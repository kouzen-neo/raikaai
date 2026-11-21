package com.raikaai.app.skills

import com.raikaai.app.util.orFallback

data class Skill(
    val id: String,
    val name: String,
    val description: String,
    val instructions: String,
    val iconCategory: String = "code", // "code", "terminal", "write", "search", "math", "palette", "sparkles"
    val isEnabled: Boolean = false,
    val isBuiltIn: Boolean = false,
    val category: String = "General", // "Coding", "Writing", "Reasoning", "Analysis", "General"
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Gson bypasses the Kotlin constructor: fields missing from old/corrupt JSON
 * stay JVM-null even when declared non-null, which later crashes string
 * operations (e.g. instructions.isNotBlank()). Always pass Gson-parsed
 * skills through here before storing or using them.
 */
@Suppress("SENSELESS_COMPARISON")
internal fun Skill.normalize(): Skill {
    return Skill(
        id = (id as String?).orFallback("skill_" + System.currentTimeMillis()),
        name = (name as String?).orFallback("Custom Skill"),
        description = (description as String?).orEmpty(),
        instructions = (instructions as String?).orEmpty(),
        iconCategory = (iconCategory as String?).orFallback("code"),
        isEnabled = (isEnabled as Boolean?) ?: false,
        isBuiltIn = (isBuiltIn as Boolean?) ?: false,
        category = (category as String?).orFallback("General"),
        createdAt = (createdAt as Long?)?.takeIf { it > 0 } ?: System.currentTimeMillis(),
    )
}

private fun escapeYamlString(value: String): String =
    value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", "")

/** The skill as a SKILL.md document (YAML frontmatter + instructions). */
fun Skill.toMarkdown(): String =
    buildString {
        appendLine("---")
        appendLine("name: \"${escapeYamlString(name)}\"")
        appendLine("description: \"${escapeYamlString(description)}\"")
        appendLine("category: \"${escapeYamlString(category)}\"")
        appendLine("icon: \"${escapeYamlString(iconCategory)}\"")
        appendLine("---")
        appendLine()
        appendLine(instructions.trim())
    }
