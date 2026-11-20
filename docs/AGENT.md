# The Raika agent

Raika is the default persona. It is an agent: it can use tools, remember things about you,
and follow your standing instructions. Character personas replace Raika's personality but
keep the same app.

## Config files

Raika is configured with three Markdown files, edited in **Agent → Memory** or from the
terminal at `/root/.raika/`.

| File | Purpose | Sent to the model |
|---|---|---|
| `SOUL.md` | Who Raika is: tone, values, habits. | When the Raika AI persona is active. |
| `AGENTS.md` | Your standing instructions ("answer in Indonesian", "prefer TypeScript"). | Every chat. |
| `MEMORY.md` | Facts about you. Raika adds lines with the `remember` tool; you can edit or clear it. | Every chat; roleplay gets a short version. |

`/workspace/AGENTS.md`, if present, is added as project instructions when Raika works in
the Linux environment.

### Context budget

On-device (LiteRT) models have small context windows, so they get a compact version: each
file is capped (a few hundred characters), the tool list is one line, at most 6 MCP tools
are listed, and tool output is cut to 1500 characters. Remote models get the full files.

Tap the context meter above the composer to see the breakdown: messages, summary, system
prompt, memory files, skills, MCP and Linux tool catalogs, retrieved knowledge, and free space.
**Models → Remote** shows token usage per model and a cost estimate (from the provider's
prices or a price you enter).

## Tools

| Tool | What it does |
|---|---|
| `execute_in_container` | Run a shell command in Alpine (cwd `/workspace`). Servers are detached automatically. |
| `read_file`, `write_file`, `edit_file` | Read with line numbers, write whole files, exact replacements. |
| `ensure_package` | Install packages with `apk`. |
| `list_daemons`, `stop_daemon` | Manage background servers. |
| `remember` | Append a fact to `MEMORY.md`. |
| `add_mcp_server` | Connect an MCP server (after your approval). |
| `web_search`, `generate_image` | When enabled in Agent → Plugins. |
| MCP tools | Any tool from a connected MCP server. |

Models that write tool calls in their own native format (DeepSeek, Qwen, Llama, Mistral, GLM, Kimi,
gpt-oss, Claude-style) are translated to the app's `<tool_call>` form as the reply arrives
(`core/remote/NativeToolCalls.kt`), so open-weight models work too.

The agent loop runs tool after tool until the task is done. Tool calls show up as cards in
the chat; **Settings → Terminal → Tool details** sets how much they show (Quiet, Normal,
Full).

## MCP

MCP servers come in two kinds:

- **Linux command** (stdio): runs inside Alpine, for example `npx -y @upstash/context7-mcp`.
  Install the runtime first if needed (`apk add nodejs npm`). The first start can take a
  minute while `npx` downloads the package.
- **URL** (Streamable HTTP): a remote endpoint, with an optional bearer token stored encrypted.

Add them in **Agent → MCP** with **+**, or ask Raika ("install the context7 MCP"). Enabled
servers connect when the app starts. MCP tool calls ask for approval (Allow, Always for that
tool, or Deny) unless the permission level is Full access.

## Permissions

Set in **Settings → Terminal → Permission** or with `/permission`.

| Level | Behaviour |
|---|---|
| **Ask** | Raika asks before every command that changes something. |
| **Auto** | Commands run without asking; destructive ones are blocked. |
| **Full access** | Commands and MCP tools run without asking; destructive commands still ask once. |

`/permission allow <command>` always allows one specific command. Approval prompts only close
through their buttons; tapping outside or pressing back does not deny them.

## Slash commands

| Command | Use |
|---|---|
| `/new` | Start a fresh chat |
| `/btw <question>` | Side question, answered above the composer and not saved to the chat |
| `/retry` | Regenerate the last reply |
| `/compact [focus]` | Summarize earlier turns to free context |
| `/model` | Switch the model |
| `/remember <fact>` | Save a fact to memory |
| `/search <query>`, `/image <prompt>`, `/remind in <n> minutes to <note>` | Tools |
| `/run <command>`, `/serve [port]`, `/env install \| wipe` | Linux |
| `/permission ask \| auto \| full` | Permission level |
| `/continue`, `/ooc <note>`, `/scene <direction>` | Roleplay only |
| `/help` | List commands |

Picking a command from the menu only fills the composer; you send it.

## Roleplay

In roleplay the character card leads the prompt. Raika's tool catalog and Linux tools are
left out, and only a short memory about you is added, so the scene stays in character.
