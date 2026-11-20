package com.raikaai.app.core.server

import com.raikaai.app.data.ChatConstants

/**
 * Converts an OpenAI-style `messages[]` array into a model-compliant formatted prompt string.
 * Supports automatic template detection as well as manual format overrides (Gemma, ChatML, Llama 3, Raw).
 */
object PromptBuilder {
    private val THINK_BLOCK =
        Regex("<(?:think|thought|thinking)(?:\\s+[^>]*)?>[\\s\\S]*?</(?:think|thought|thinking)>", RegexOption.IGNORE_CASE)
    private val THINK_UNCLOSED =
        Regex("<(?:think|thought|thinking)(?:\\s+[^>]*)?>[\\s\\S]*", RegexOption.IGNORE_CASE)

    data class ChatMessage(
        val role: String = "",
        val content: String = "",
    )

    /**
     * Builds a formatted prompt string from message history based on the chosen format template.
     */
    fun build(
        messages: List<ChatMessage>,
        systemPrompt: String,
        templateFormat: String = "auto",
        modelPath: String = "",
        enableThinking: Boolean = true,
        contextWindow: Int = 4096,
    ): String {
        // Per OpenAI semantics the system message comes first; when several
        // system messages are present, the first one wins.
        var system =
            messages.firstOrNull { it.role.equals(ChatConstants.ROLE_SYSTEM, ignoreCase = true) }?.content?.trim()
                .takeUnless { it.isNullOrBlank() } ?: systemPrompt.trim()

        if (!enableThinking) {
            val noThinkDirective = "Do NOT think or reason inside <think>...</think> tags. Provide the final response directly."
            system = if (system.isNotBlank()) "$system\n$noThinkDirective" else noThinkDirective
        }

        val rawDialogue =
            messages.filterNot {
                it.role.equals(ChatConstants.ROLE_SYSTEM, ignoreCase = true) ||
                    it.role.equals(ChatConstants.ROLE_COMPACTION, ignoreCase = true)
            }
        val dialogue = rawDialogue.map { msg ->
            var content = msg.content
            if (msg.role.equals(ChatConstants.ROLE_ASSISTANT, ignoreCase = true)) {
                // Previous turns' reasoning is never fed back (thinking models
                // such as Qwen3 / Gemma / DeepSeek expect it dropped), even with
                // thinking enabled: it only burns the small on-device context
                // window. Covers attributes, <thinking>, and unclosed blocks.
                content = content
                    .replace(THINK_BLOCK, "")
                    .replace(THINK_UNCLOSED, "")
                    .trim()
            }
            if (msg.role.equals(ChatConstants.ROLE_USER, ignoreCase = true)) {
                // Strip a leading /search or /web command prefix (case-insensitive,
                // only when followed by whitespace or end-of-input).
                val clean = content.replaceFirst(Regex("^/(search|web)(?=\\s|$)", RegexOption.IGNORE_CASE), "").trim()
                if (clean.isNotBlank()) {
                    content = clean
                }
            }
            msg.copy(content = content)
        }

        // Drop oldest turns until we fit the context window budget, reserving headroom for generation.
        val reservedOutput = minOf(1024, maxOf(384, (contextWindow * 0.25f).toInt()))
        val budgetTokens = maxOf(256, contextWindow - reservedOutput)
        val kept = ArrayDeque(dialogue)
        while (kept.size > 1 && estimateTotalPromptTokens(listOfNotNull(system), kept) > budgetTokens) {
            kept.removeFirst()
        }

        // Safeguard on-device engine: if the solitary remaining turn exceeds budgetTokens,
        // cap its content to avoid overflowing the native LiteRT KV-cache sequence capacity.
        if (kept.isNotEmpty() && estimateTotalPromptTokens(listOfNotNull(system), kept) > budgetTokens) {
            val single = kept.removeFirst()
            val sysTokens = estimateTokens(system)
            val availableTokens = maxOf(128, budgetTokens - sysTokens)
            val maxChars = availableTokens * 4
            val trimmedContent = if (single.content.length > maxChars) {
                val fileMarker = single.content.indexOf("\n\n[Attached file \"")
                if (fileMarker in 1 until maxChars) {
                    val promptPart = single.content.substring(0, fileMarker)
                    val docPart = single.content.substring(fileMarker).take(maxChars - fileMarker)
                    "$promptPart$docPart\n\n[... truncated to fit ${contextWindow} token window ...]"
                } else {
                    single.content.take(maxChars) + "\n\n[... truncated to fit ${contextWindow} token window ...]"
                }
            } else {
                single.content
            }
            kept.addFirst(single.copy(content = trimmedContent))
        }

        val effectiveFormat = resolveTemplateFormat(templateFormat, modelPath)

        return when (effectiveFormat) {
            "gemma" -> buildGemmaPrompt(system, kept, enableThinking)
            "chatml" -> buildChatMLPrompt(system, kept, enableThinking)
            "llama3" -> buildLlama3Prompt(system, kept, enableThinking)
            else -> buildRawPrompt(system, kept, enableThinking)
        }
    }

    private fun resolveTemplateFormat(format: String, modelPath: String): String {
        if (!format.equals("auto", ignoreCase = true) && format.isNotBlank()) {
            return format.lowercase()
        }
        val lowerPath = modelPath.lowercase()
        return when {
            lowerPath.contains("gemma") -> "gemma"
            lowerPath.contains("llama") -> "llama3"
            lowerPath.contains("smol") || lowerPath.contains("qwen") || lowerPath.contains("chatml") -> "chatml"
            else -> "raw"
        }
    }

    private fun buildGemmaPrompt(system: String, dialogue: ArrayDeque<ChatMessage>, enableThinking: Boolean): String {
        val sb = StringBuilder()
        if (system.isNotBlank()) {
            sb.append("<start_of_turn>user\n[System Instructions]\n").append(system).append("\n<end_of_turn>\n")
            sb.append("<start_of_turn>model\nUnderstood. I will follow these instructions.\n<end_of_turn>\n")
        }
        if (dialogue.isEmpty()) {
            sb.append("<start_of_turn>user\nHello\n<end_of_turn>\n<start_of_turn>model\n")
            if (!enableThinking) sb.append("<think>\n</think>\n")
            return sb.toString()
        }
        for (msg in dialogue) {
            val role = if (msg.role.equals(ChatConstants.ROLE_ASSISTANT, ignoreCase = true)) "model" else "user"
            sb.append("<start_of_turn>").append(role).append("\n").append(msg.content.trim()).append("<end_of_turn>\n")
        }
        sb.append("<start_of_turn>model\n")
        if (!enableThinking) sb.append("<think>\n</think>\n")
        return sb.toString()
    }

    private fun buildChatMLPrompt(system: String, dialogue: ArrayDeque<ChatMessage>, enableThinking: Boolean): String {
        val sb = StringBuilder()
        if (system.isNotBlank()) {
            sb.append("<|im_start|>system\n").append(system).append("<|im_end|>\n")
        }
        if (dialogue.isEmpty()) {
            sb.append("<|im_start|>user\nHello\n<|im_end|>\n<|im_start|>assistant\n")
            if (!enableThinking) sb.append("<think>\n</think>\n")
            return sb.toString()
        }
        for (msg in dialogue) {
            val role = if (msg.role.equals(ChatConstants.ROLE_ASSISTANT, ignoreCase = true)) "assistant" else "user"
            sb.append("<|im_start|>").append(role).append("\n").append(msg.content.trim()).append("<|im_end|>\n")
        }
        sb.append("<|im_start|>assistant\n")
        if (!enableThinking) sb.append("<think>\n</think>\n")
        return sb.toString()
    }

    private fun buildLlama3Prompt(system: String, dialogue: ArrayDeque<ChatMessage>, enableThinking: Boolean): String {
        val sb = StringBuilder()
        sb.append("<|begin_of_text|>")
        if (system.isNotBlank()) {
            sb.append("<|start_header_id|>system<|end_header_id|>\n\n").append(system).append("<|eot_id|>")
        }
        if (dialogue.isEmpty()) {
            sb.append("<|start_header_id|>user<|end_header_id|>\n\nHello<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n\n")
            if (!enableThinking) sb.append("<think>\n</think>\n")
            return sb.toString()
        }
        for (msg in dialogue) {
            val role = if (msg.role.equals(ChatConstants.ROLE_ASSISTANT, ignoreCase = true)) "assistant" else "user"
            sb.append("<|start_header_id|>").append(role).append("<|end_header_id|>\n\n")
                .append(msg.content.trim()).append("<|eot_id|>")
        }
        sb.append("<|start_header_id|>assistant<|end_header_id|>\n\n")
        if (!enableThinking) sb.append("<think>\n</think>\n")
        return sb.toString()
    }

    private fun buildRawPrompt(system: String, dialogue: ArrayDeque<ChatMessage>, enableThinking: Boolean): String {
        val sb = StringBuilder()
        if (system.isNotBlank()) {
            sb.append("[System]\n").append(system).append("\n\n")
        }
        if (dialogue.isEmpty()) {
            sb.append("User: \nAssistant:")
            if (!enableThinking) sb.append(" <think>\n</think>\n")
            return sb.toString()
        }
        for ((index, msg) in dialogue.withIndex()) {
            val speaker = if (msg.role.equals(ChatConstants.ROLE_ASSISTANT, ignoreCase = true)) "Assistant" else "User"
            if (index == dialogue.lastIndex) {
                sb.append("$speaker: ").append(msg.content.trim()).append("\nAssistant:")
                if (!enableThinking) sb.append(" <think>\n</think>\n")
            } else {
                sb.append("$speaker: ").append(msg.content.trim()).append("\n\n")
            }
        }
        return sb.toString()
    }

    private fun estimateTokens(text: String): Int {
        if (text.isBlank()) return 0
        return maxOf(1, Math.round(text.length / 3.2).toInt())
    }

    private fun estimateTotalPromptTokens(
        extras: List<String>,
        dialogue: ArrayDeque<ChatMessage>,
    ): Int {
        var count = 0
        for (extra in extras) {
            count += estimateTokens(extra)
        }
        for (msg in dialogue) {
            count += estimateTokens(msg.content) + 6
        }
        return count
    }
}
