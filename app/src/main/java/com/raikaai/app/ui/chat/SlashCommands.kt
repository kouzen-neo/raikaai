package com.raikaai.app.ui.chat

/** Where a command is offered. */
enum class SlashScope { CHAT, ROLEPLAY, BOTH }

/** Menu grouping, also used to order the list and the /help output. */
enum class SlashGroup(val label: String) {
    SESSION("Chat"),
    ROLEPLAY("Roleplay"),
    TOOLS("Tools"),
    LINUX("Linux"),
}

/**
 * A slash command.
 *
 * @property promptTemplate text placed in the composer when picked from the menu.
 * @property runsImmediately picking it from the menu executes it right away.
 * @property requiresLinux only offered when the Linux environment is installed.
 */
data class SlashCommand(
    val command: String,
    val description: String,
    val promptTemplate: String,
    val group: SlashGroup,
    val scope: SlashScope = SlashScope.BOTH,
    val runsImmediately: Boolean = false,
    val requiresLinux: Boolean = false,
    val usage: String = command,
)

/**
 * Built-in slash commands: only the ones worth a shortcut. Writing
 * templates are gone (just ask), and skills/MCP live in the Agent menu.
 * Older spellings (/yolo, /daemons, /stop-serve, /fix-dns, /skills) still
 * work when typed but are not listed.
 */
val DEFAULT_SLASH_COMMANDS: List<SlashCommand> =
    listOf(
        // ── Chat ──
        SlashCommand("/new", "Start a fresh chat", "/new", SlashGroup.SESSION, runsImmediately = true),
        SlashCommand("/btw", "Quick side question; not saved to the chat", "/btw ", SlashGroup.SESSION, usage = "/btw <question>"),
        SlashCommand("/retry", "Regenerate the last reply", "/retry", SlashGroup.SESSION, runsImmediately = true),
        SlashCommand("/compact", "Summarize earlier turns to free context", "/compact ", SlashGroup.SESSION, usage = "/compact [focus]"),
        SlashCommand("/model", "Switch the model", "/model", SlashGroup.SESSION, runsImmediately = true),
        SlashCommand(
            "/remember", "Save a fact about you to Raika's memory", "/remember ", SlashGroup.SESSION,
            scope = SlashScope.CHAT, usage = "/remember <fact>",
        ),
        SlashCommand("/help", "List commands", "/help", SlashGroup.SESSION, runsImmediately = true),

        // ── Roleplay ──
        SlashCommand("/continue", "Let the character keep going", "/continue", SlashGroup.ROLEPLAY, scope = SlashScope.ROLEPLAY, runsImmediately = true),
        SlashCommand("/ooc", "Talk to the narrator, out of character", "/ooc ", SlashGroup.ROLEPLAY, scope = SlashScope.ROLEPLAY, usage = "/ooc <note>"),
        SlashCommand("/scene", "Direct what happens next", "/scene ", SlashGroup.ROLEPLAY, scope = SlashScope.ROLEPLAY, usage = "/scene <what happens>"),

        // ── Tools ──
        SlashCommand("/search", "Answer with fresh web results", "/search ", SlashGroup.TOOLS, scope = SlashScope.CHAT, usage = "/search <query>"),
        SlashCommand("/image", "Generate an image", "/image ", SlashGroup.TOOLS, usage = "/image <prompt>"),
        SlashCommand("/remind", "Schedule a notification", "/remind in 10 minutes to ", SlashGroup.TOOLS, scope = SlashScope.CHAT, usage = "/remind in <n> minutes to <note>"),

        // ── Linux ──
        SlashCommand("/run", "Run a shell command in /workspace", "/run ", SlashGroup.LINUX, scope = SlashScope.CHAT, requiresLinux = true, usage = "/run <command>"),
        SlashCommand("/serve", "Preview /workspace in the browser", "/serve", SlashGroup.LINUX, scope = SlashScope.CHAT, requiresLinux = true, runsImmediately = true, usage = "/serve [port]"),
        SlashCommand(
            "/permission", "Ask before commands, or run them automatically", "/permission", SlashGroup.LINUX,
            scope = SlashScope.CHAT, requiresLinux = true, runsImmediately = true, usage = "/permission [ask | auto | full]",
        ),
        SlashCommand("/env", "Install or reset the Linux environment", "/env ", SlashGroup.LINUX, scope = SlashScope.CHAT, usage = "/env install | wipe"),
    )

/** Commands offered in the current context, ordered by group. */
fun availableSlashCommands(
    isRoleplay: Boolean,
    linuxInstalled: Boolean,
): List<SlashCommand> =
    DEFAULT_SLASH_COMMANDS
        .filter { cmd ->
            val scopeOk =
                when (cmd.scope) {
                    SlashScope.BOTH -> true
                    SlashScope.CHAT -> !isRoleplay
                    SlashScope.ROLEPLAY -> isRoleplay
                }
            scopeOk && (!cmd.requiresLinux || linuxInstalled)
        }.sortedBy { it.group.ordinal }

/** Filters [commands] by what the user typed after "/". */
fun matchSlashCommands(
    commands: List<SlashCommand>,
    input: String,
): List<SlashCommand> {
    if (!input.startsWith("/")) return emptyList()
    val q = input.removePrefix("/").substringBefore(' ').trim()
    // Once an argument is being typed, the menu has done its job.
    if (input.contains(' ')) return emptyList()
    if (q.isEmpty()) return commands
    val starts = commands.filter { it.command.removePrefix("/").startsWith(q, ignoreCase = true) }
    val contains =
        commands.filter {
            it !in starts && (it.command.contains(q, ignoreCase = true) || it.description.contains(q, ignoreCase = true))
        }
    return starts + contains
}

/** Markdown help text for the current context. */
fun slashHelpText(commands: List<SlashCommand>): String =
    buildString {
        append("**Commands**\n")
        commands.groupBy { it.group }.forEach { (group, cmds) ->
            append("\n**${group.label}**\n")
            cmds.forEach { append("- `${it.usage}` — ${it.description}\n") }
        }
    }.trimEnd()
