package com.raikaai.app.ui.sampler

import com.raikaai.app.ui.component.ScreenHeader
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raikaai.app.ui.chat.ChatViewModel
import java.util.Locale

/**
 * Generation Parameters Screen merging Sampler Tuning and Instruct Prompt Templates.
 * Controls Context Allocation, One-Tap Sampler Presets, Sliders, Thinking Toggles, and Instruct Formats.
 */
@Composable
fun SamplerScreen(
    viewModel: ChatViewModel,
    onOpenDrawer: () -> Unit = {},
) {
    val settings by viewModel.settings.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }

    val templateFormats =
        listOf(
            "auto" to "Auto-Detect (Based on Model Name)",
            "chatml" to "ChatML (<|im_start|> / <|im_end|>)",
            "gemma" to "Gemma 2 (<start_of_turn> / <end_of_turn>)",
            "llama3" to "Llama 3 (<|start_header_id|> / <|eot_id|>)",
            "raw" to "Raw Text (Plain Prompt)",
        )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(top = 10.dp),
        ) {
            // ── 0. Top Header ──
            ScreenHeader(
                title = "Parameters",
                subtitle = "Sampling, context and prompt format",
                onOpenDrawer = onOpenDrawer,
            )

            Spacer(Modifier.height(8.dp))

            // ── Secondary Tabs Row ──
            SecondaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.background,
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Sampling", fontWeight = FontWeight.SemiBold)
                        }
                    },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Prompt Template", fontWeight = FontWeight.SemiBold)
                        }
                    },
                )
            }

            // ── Tab Content ──
            if (selectedTab == 0) {
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(top = 12.dp, start = 16.dp, end = 16.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // ── 1. Context Allocation Card ──
                    item(key = "context_allocation") {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                val currentTokens = settings.contextWindowTokens
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Context Allocation",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        val ramHint =
                                            when {
                                                currentTokens <= 2048 -> "Fast inference · ~150-300MB KV RAM"
                                                currentTokens <= 8192 -> "Standard & Long · ~600MB-1.2GB KV RAM"
                                                currentTokens <= 16384 -> "High context · ~2.4GB KV RAM"
                                                currentTokens <= 32768 -> "Ultra 32K Context · ~4.8GB KV RAM (Local limit)"
                                                currentTokens <= 131072 -> "Extended Cloud / Remote Window (128K)"
                                                else -> "Ultra Large Context Window (${formatContextTokens(currentTokens)})"
                                            }
                                        Text(
                                            ramHint,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    ) {
                                        Text(
                                            text = "${formatContextTokens(currentTokens)} ($currentTokens)",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        )
                                    }
                                }

                                Spacer(Modifier.height(8.dp))

                                // Continuous slider up to 1M (1,000,000) tokens
                                Slider(
                                    value = tokensToSliderPos(currentTokens),
                                    onValueChange = { pos ->
                                        val newTokens = sliderPosToTokens(pos)
                                        viewModel.setContextWindowTokens(newTokens)
                                    },
                                    valueRange = 0f..1f,
                                    modifier = Modifier.fillMaxWidth(),
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text("1K", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("8K", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("32K", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("128K", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("1M", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                Spacer(Modifier.height(14.dp))

                                // Row 1: 1K, 2K, 4K
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    listOf(
                                        Pair(1024, "1K Fast"),
                                        Pair(2048, "2K Standard"),
                                        Pair(4096, "4K Extended"),
                                    ).forEach { (tokens, label) ->
                                        val isSelected = currentTokens == tokens
                                        Surface(
                                            onClick = { viewModel.setContextWindowTokens(tokens) },
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isSelected) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(vertical = 10.dp),
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.height(8.dp))

                                // Row 2: 8K, 16K, 32K
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    listOf(
                                        Pair(8192, "8K Long"),
                                        Pair(16384, "16K Deep"),
                                        Pair(32768, "32K Max"),
                                    ).forEach { (tokens, label) ->
                                        val isSelected = currentTokens == tokens
                                        Surface(
                                            onClick = { viewModel.setContextWindowTokens(tokens) },
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isSelected) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(vertical = 10.dp),
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── 2. Sampler Preset Cards ──
                    item(key = "sampler_presets") {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("One-Tap Sampler Presets", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Quickly switch generation balance depending on your use case.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    SamplerPresetItem(
                                        label = "Creative",
                                        sub = "T: 0.9 · P: 0.95",
                                        selected = settings.temperature >= 0.85f && settings.topP >= 0.9f,
                                        onClick = {
                                            viewModel.setTemperature(0.9f)
                                            viewModel.setTopP(0.95f)
                                            viewModel.setTopK(50)
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                    SamplerPresetItem(
                                        label = "Balanced",
                                        sub = "T: 0.7 · P: 0.85",
                                        selected = settings.temperature in 0.65f..0.75f && settings.topP in 0.8f..0.9f,
                                        onClick = {
                                            viewModel.setTemperature(0.7f)
                                            viewModel.setTopP(0.85f)
                                            viewModel.setTopK(40)
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                    SamplerPresetItem(
                                        label = "Precise",
                                        sub = "T: 0.2 · P: 0.50",
                                        selected = settings.temperature <= 0.3f && settings.topP <= 0.6f,
                                        onClick = {
                                            viewModel.setTemperature(0.2f)
                                            viewModel.setTopP(0.5f)
                                            viewModel.setTopK(20)
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }

                    // ── 3. Thinking & Reasoning Mode Toggle ──
                    item(key = "thinking_mode") {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Reasoning / Thinking Mode", style = MaterialTheme.typography.titleMedium)
                                    Text("Renders <thought> scratchpad blocks inside expandable reasoning accordions.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(Modifier.width(12.dp))
                                Switch(
                                    checked = settings.enableThinking,
                                    onCheckedChange = { viewModel.setEnableThinking(it) },
                                )
                            }
                        }
                    }

                    // ── 4. Detailed Sampler Sliders ──
                    item(key = "sampler_sliders") {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Text("Fine-Tuning Sliders", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                                SamplerSliderField(
                                    title = "Temperature",
                                    valueDisplay = String.format(Locale.US, "%.2f", settings.temperature),
                                    description = "Higher values increase randomness and creativity; lower values make output focused and deterministic.",
                                    value = settings.temperature,
                                    range = 0.0f..2.0f,
                                    steps = 40,
                                    onValueChange = { viewModel.setTemperature(it) },
                                )

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                                SamplerSliderField(
                                    title = "Top-P (Nucleus Sampling)",
                                    valueDisplay = String.format(Locale.US, "%.2f", settings.topP),
                                    description = "Cumulative probability cutoff. Filters out low-confidence tokens.",
                                    value = settings.topP,
                                    range = 0.0f..1.0f,
                                    steps = 20,
                                    onValueChange = { viewModel.setTopP(it) },
                                )

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                                SamplerSliderField(
                                    title = "Top-K Sampling",
                                    valueDisplay = "${settings.topK}",
                                    description = "Restricts sampling to the top K highest probability tokens.",
                                    value = settings.topK.toFloat(),
                                    range = 1f..100f,
                                    steps = 99,
                                    onValueChange = { viewModel.setTopK(it.toInt()) },
                                )
                            }
                        }
                    }

                    // ── 5. Reset to Recommended ──
                    item(key = "reset_btn") {
                        OutlinedButton(
                            onClick = { viewModel.resetParametersToRecommended() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Reset Sampler to Recommended Defaults")
                        }
                    }
                }
            } else {
                // ── Tab 1: Prompt Template Content ──
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(top = 12.dp, start = 16.dp, end = 16.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // ── 1. Active Instruct Template Selector ──
                    item(key = "template_selector") {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "Prompt Template Format",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Special token wrapping used to structure system instructions and conversation turns.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(12.dp))

                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    templateFormats.forEach { (format, label) ->
                                        val isSelected = settings.promptTemplateFormat == format
                                        Surface(
                                            onClick = { viewModel.setPromptTemplateFormat(format) },
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isSelected) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                            ) {
                                                Text(
                                                    text = label,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                                if (isSelected) {
                                                    Surface(
                                                        color = MaterialTheme.colorScheme.primary,
                                                        shape = RoundedCornerShape(50),
                                                    ) {
                                                        Text(
                                                            "Active",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onPrimary,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── 2. Format Schema Preview Card ──
                    item(key = "format_preview") {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "Special Token Schema Preview",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(Modifier.height(8.dp))

                                val previewCode =
                                    when (settings.promptTemplateFormat) {
                                        "chatml" -> "<|im_start|>system\n{{system_prompt}}<|im_end|>\n<|im_start|>user\n{{user_message}}<|im_end|>\n<|im_start|>assistant\n"
                                        "gemma" -> "<start_of_turn>user\n{{system_prompt}}\n\n{{user_message}}<end_of_turn>\n<start_of_turn>model\n"
                                        "llama3" -> "<|start_header_id|>system<|end_header_id|>\n\n{{system_prompt}}<|eot_id|><|start_header_id|>user<|end_header_id|>\n\n{{user_message}}<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n\n"
                                        "raw" -> "System: {{system_prompt}}\nUser: {{user_message}}\nAssistant: "
                                        else -> "// Auto-detects based on loaded model filename (e.g. Gemma, SmolLM, Qwen)"
                                    }

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = previewCode,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(12.dp),
                                    )
                                }
                            }
                        }
                    }

                    // ── 3. Reset Button ──
                    item(key = "reset_template_btn") {
                        OutlinedButton(
                            onClick = { viewModel.setPromptTemplateFormat("auto") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Reset Formatting to Auto-Detect")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SamplerPresetItem(
    label: String,
    sub: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = sub,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SamplerSliderField(
    title: String,
    valueDisplay: String,
    description: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChange: (Float) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Text(
                    text = valueDisplay,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
        )
    }
}

private fun formatContextTokens(tokens: Int): String {
    return when {
        tokens >= 1_000_000 -> {
            val m = tokens / 1_000_000.0
            if (m == m.toLong().toDouble()) "${m.toLong()}M" else String.format(Locale.US, "%.1fM", m)
        }
        tokens >= 1024 -> "${tokens / 1024}K"
        else -> "$tokens"
    }
}

private fun tokensToSliderPos(tokens: Int): Float {
    val minLog = 6.931471805599453 // ln(1024.0)
    val maxLog = 13.815510557964274 // ln(1000000.0)
    val clamped = tokens.coerceIn(1024, 1_000_000).toDouble()
    return ((kotlin.math.ln(clamped) - minLog) / (maxLog - minLog)).toFloat().coerceIn(0f, 1f)
}

private fun sliderPosToTokens(pos: Float): Int {
    if (pos <= 0f) return 1024
    if (pos >= 1f) return 1_000_000
    val minLog = 6.931471805599453
    val maxLog = 13.815510557964274
    val raw = kotlin.math.exp(minLog + pos * (maxLog - minLog)).toInt()
    return when {
        raw <= 8192 -> (raw / 512) * 512
        raw <= 32768 -> (raw / 1024) * 1024
        raw <= 131072 -> (raw / 4096) * 4096
        raw <= 524288 -> (raw / 16384) * 16384
        else -> minOf(1_000_000, (raw / 32768) * 32768)
    }.coerceIn(1024, 1_000_000)
}
