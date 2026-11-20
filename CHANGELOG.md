# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [0.5.0] - 2026-11-21

### Fixed
- Opening Characters from the sidebar after a normal chat showed an empty "Character not found" page. It now opens the Characters hub.

## [0.4.9] - 2026-11-21

### Added
- Translate button on character replies and greetings. It translates with the model you are using (remote or on-device), keeps *actions*, dialogue and names intact, and a second tap shows the original. Only the display changes; the chat stays in its original language. Pick the language in Settings → Chat Behavior → Translate to (Bahasa Indonesia by default, any language or your phone's language), and optionally translate greetings automatically.
- App updates in Settings: checks GitHub for a new version, downloads it with progress (resumes after a dropped connection), checks it against the release checksum and opens the installer. Chats, characters and Linux files are kept.
- Publish a local character: the upload icon on a Local character card opens Publish with its name, description and greeting filled in. Pick a public image with Search image, since the local photo stays on your phone.
- Settings → Remote models → Reply timeout (2, 5, 10 or 30 minutes, default 5). Long replies from slow providers or proxies are no longer cut off after 2 minutes.

### Changed
- Characters open from the saved list right away and refresh in the background, so new and updated characters appear without clearing data (thanks Ilyas).
- Local form labels are clearer: "Save Persona" is now "Save to this phone", "System Instructions" is "Character prompt", and the edit form uses the same names.
- Community characters were rewritten: each has an accurate description, a consistent prompt (who they are, personality, speech, relationship with you) and an English greeting that fits them. Several that showed the wrong character were fixed.
- New Community submissions get the same reply-length rules as the catalog (thanks Ilyas).

## [0.4.8] - 2026-11-21

### Changed
- Characters tabs have matching icons: Publish uses an upload icon and Local a phone icon.

## [0.4.7] - 2026-11-21

### Changed
- Characters labels say what happens: the bottom "Create" is now "Publish" (shared with the Community after a review), and Mine's "Create & Import" tab is "Local", where personas stay on this phone. The Local card explains it is never shared.

## [0.4.6] - 2026-11-21

### Fixed
- Models that write tool calls in their own native markup instead of the app's `<tool_call>` format showed it as raw text while nothing ran (for example DeepSeek's `<｜DSML｜invoke …>`, so a `git clone` never started). Remote replies are now translated to normal tool calls for the popular families: DeepSeek (V3 and V3.2+), Qwen3-Coder and Llama `<function=…>`, Llama 3.x `<|python_tag|>` and bare JSON replies, Mistral `[TOOL_CALLS]`, GLM `<arg_key>`, Kimi K2, gpt-oss and Claude-style `<invoke>`. Normal replies, code and the app's own format are untouched.

## [0.4.5] - 2026-11-20

### Added
- Chat style (Settings → Chat Behavior): Auto, Classic or Bubbles. Bubbles show the character's replies in rounded bubbles next to their photo (the app logo for Raika), your messages in a tinted bubble, and a Stop pill beside the working indicator. Auto uses bubbles in roleplay and keeps the flat layout for the agent, where tool cards, code and tables need the width.
- Characters → Discover: Trending cards are larger, with the full photo, name, description and a "Say hi" pill that opens the chat.
- Trending cards load the original artwork instead of the thumbnail: the sample or original file from the same post, falling back to the thumbnail when one is missing.

### Changed
- Character cards in Discover use rounded corners that match the rest of the app.

## [0.4.4] - 2026-11-20

### Added
- Tap the context meter above the composer to see where the context goes: messages, summary, system prompt, memory files, skills, MCP tools, Linux tools, image tool, knowledge and web results, and the free space, each with tokens and a share of the window, plus a "Compact now" button.
- Models → Remote: a usage card per model with tokens in and out and request count. Every remote request is counted (agent rounds, `/btw`, compaction too), using the provider's own numbers when it reports them and marking estimates with ~.

### Changed
- The context meter counts the system prompt and tool catalogs, not only the messages.
- Cost is estimated from the provider's published prices (OpenRouter) or a price you enter per 1M tokens by tapping a model. Unpriced models show "Set price" instead of a misleading $0.

## [0.4.3] - 2026-11-20

### Fixed
- "Send now" no longer makes the message disappear for a few seconds: it stays in the queue as "Sending…" until it is in the chat, and the handover waits at most 1.5 seconds.
- Stopping a remote reply aborts the request at once, even while it is still connecting or waiting for the first token.

## [0.4.2] - 2026-11-20

### Fixed
- "Send now" no longer ends with "Remote model returned an empty response" and a dropped message. It now works like an interrupt in coding agents: the running reply is stopped and fully unwound before the queued message is sent, what it wrote and ran so far is kept and marked "interrupted", and the model is told the previous reply was interrupted so it continues with full context.

## [0.4.1] - 2026-11-20

### Changed
- Full access no longer asks before MCP tool calls; Ask and Auto still do. The MCP prompt has an "Always" button that trusts that tool from then on.
- Raika is told real MCP package names (Python servers such as the official fetch run with `uvx`) and gets a hint when an npm package does not exist.

### Fixed
- Access prompts (Linux command, MCP tool, phone storage) no longer close and deny when you tap outside them or press back; choose Allow, Always or Deny.

## [0.4.0] - 2026-11-20

### Changed
- Community characters are reviewed before they appear: publishing goes through `submit_character` (rate-limited to 5 per minute and 30 per hour, input validated) and new characters stay hidden until approved. The publish warning says so, and errors such as the rate limit are shown.

### Fixed
- Agent → Memory text no longer sits under the navigation bar.

### Project
- README rewritten around what RaikaAI is (chat, agent, Linux) with new screenshots; all docs are in English. The Indonesian README and an old planning note were removed.
- New docs: `docs/AGENT.md` (config files, tools, MCP, permissions, slash commands) and `docs/LINUX.md` (folders, terminal, Alpine tips, reset).
- `AGENTS.md` (also `CLAUDE.md`) describes the workflow for contributors and AI coding agents.
- `SECURITY.md` (private vulnerability reporting), `CONTRIBUTING.md`, `PRIVACY.md`, issue forms and a pull request template.
- Acknowledgments list the projects actually used; `.gitattributes` counts only Kotlin and Rust in GitHub's language stats.

## [0.3.9] - 2026-11-20

### Changed
- MCP tab: one card per server with an icon (Linux command or URL), a status line (Connected · N tools, Starting in Linux…, Not connected + Try again), the command shown in monospace, a "Show N tools" list with test buttons, and Reconnect/Edit/Remove in a ⋮ menu. The list no longer touches the tabs.

### Fixed
- MCP tools were lost after restarting the app until "Discover tools" was tapped: enabled servers now connect on app start and when the MCP tab opens.

## [0.3.8] - 2026-11-20

### Added
- Working indicator under the last message while Raika is busy: spinner, what it is doing (Thinking, Running command, Using a tool, Writing) and elapsed seconds, also between agent steps.

### Fixed
- A dark band behind the composer (around the server pill) covered messages when scrolling; the list now scrolls behind the floating composer.

## [0.3.7] - 2026-11-20

### Added
- Settings → Terminal → Phone storage in Linux: mounts your phone storage at /sdcard so Raika can work in phone folders (or /workspace). A dialog explains the "All files access" permission before Android asks for it.
- Settings → Terminal → Permission: Ask, Auto, or Full access (nothing blocked; destructive commands ask once). `/permission full` too.

### Changed
- Settings rows with a three-way choice put the buttons under the text, full width (the title no longer wraps one letter per line).
- On-device (LiteRT): a much shorter Linux tool prompt and tool results capped at 1500 characters, so the small context is not flooded.

## [0.3.6] - 2026-11-20

### Changed
- Edit message is a bottom sheet: a large focused text box, a note that sending makes a new branch, and two equal actions (New chat, Send).

## [0.3.5] - 2026-11-20

### Added
- Message queue: text sent while a reply is being written waits above the composer and goes out when the reply ends; "Send now" stops the reply and sends it first. `/btw` still answers at once.
- Settings → Terminal → Tool details: Quiet (one line), Normal (tap to open) or Full (output and file contents open).
- Alpine notes in the agent prompt: musl not glibc, `apk` not apt, internet works, `ping`/`ip` fail under proot (not an outage), prefer apk packages over `curl | bash` installers.

### Fixed
- Commands with redirections (`2>&1`, `>&2`, `&>file`) were treated as background jobs and started as daemons, so `curl`, `ping` and `git clone` looked stuck and their output was cut.
- Context window detection reads `context_length`-style fields from the provider's model list and falls back to known model families (Gemini, Claude, GPT, DeepSeek, Grok, Mistral, Llama, Qwen) instead of warning.
- Picking a slash command only fills the composer; it no longer sends immediately.

## [0.3.4] - 2026-11-20

### Added
- `/btw <question>`: a quick side question answered in a card above the composer; it is not saved to the chat or sent to the model later. On-device (LiteRT) gets a short context and waits for a running reply.
- `/remember <fact>` saves to Raika's memory.
- Stdio MCP servers that run inside the Alpine environment (e.g. `npx -y @upstash/context7-mcp`); Raika can install them with `add_mcp_server` after you approve.
- Raika's home in Linux: `/root/.raika` holds SOUL.md, AGENTS.md, MEMORY.md and `chats/` (every chat as Markdown, roleplay in `chats/roleplay`), so the agent can read and grep past chats.
- "Add to chat" sheet behind the + button: Camera, Photos, Files, Knowledge, Web search and Linux command permission.

### Changed
- Voice input: the recognizer was killed right after starting (a delayed cleanup ran after the new session began) and silence opened the fallback dialog. Speech now streams into the composer while you talk.
- Model picker always lists the models of the selected remote provider (refreshed on open and on provider switch; stale lists are dropped).
- Terminal always uses the PTY; the pipe mode and its switches are gone.
- Slash commands trimmed to the essentials, without icons. `/permission ask | auto` replaces `/yolo` (old spellings still work).
- MCP tab: no global switch; servers show where they run and whether Raika installed them; add dialog supports a Linux command or a URL.
- Characters hub: Discover · Mine · Create. Roleplay chats are in the sidebar's Recent list; your profile is behind the header button.

## [0.3.3] - 2026-11-20

### Changed
- Characters → Mine: no empty gap under the header; search and filters show only when there are more than 5 characters.
- Raika AI uses the app icon (character list, right drawer, editor) and shows its description instead of the old system prompt.
- Editing Raika AI opens SOUL.md directly instead of the generic character editor.
- Character editor: one Avatar row (photo or icon), shorter labels, and roleplay details and sampler folded under "Roleplay details & sampler…".
- Chat top bar: the character switcher uses the same icon as Characters in the sidebar.

## [0.3.2] - 2026-11-20

### Added
- Agent hub: Plugins, Skills, MCP and Memory in one sidebar item ("Agent"); the separate MCP entry is gone.
- Raika's own config: SOUL.md (identity), AGENTS.md (standing instructions) and MEMORY.md (facts about the user), editable in Agent → Memory and added to the system prompt.
- `remember` tool: Raika saves lasting facts to MEMORY.md, shown as a "Remembered" card.
- `add_mcp_server` tool: Raika can connect a Streamable HTTP MCP server after the user approves.
- Community category for user-made roleplay characters, with a public warning and a confirmation checkbox before publishing.

### Changed
- MCP tools now work with remote models too, inside the same agent loop as the Linux tools, with approval per call.
- On-device (LiteRT) models get a compact agent prompt: capped SOUL/AGENTS/MEMORY, one-line capabilities, at most 6 MCP tools, so the small context is not flooded.
- Characters and Roleplay are one "Characters" hub: Discover · Mine · History · Create · Profile.
- The default persona is "Raika AI" instead of "General"; its identity comes from SOUL.md.
- Roleplay keeps the character card first and adds only a short user memory, no tool catalogs.
- Bundled Alpine rootfs is stored as .bin so the APK packager keeps it compressed and the checksum matches.
- Supabase: categories can no longer be created publicly, functions use a fixed search_path, slugs are consistent, and search/trending/category lists have indexes (`supabase/migrations/20261120123500_roleplay_cleanup.sql`).

## [0.3.1] - 2026-11-20

### Added
- **Alpine ships with the app**: the Alpine 3.21 minirootfs (3.8 MB, checked against the official SHA-256) is bundled in the APK, so the Linux environment installs offline on first launch without an Install tap. Reset and reinstall use the bundled copy too; other ABIs still download it.

### Changed
- **One look across all screens**: every drawer screen (Models, Parameters, Skills & Plugins, MCP, Local API, All chats, Settings, Logs, About, Characters, Roleplay) uses the same header (menu button, title, one-line subtitle, actions on the right) at the same position. Roleplay no longer uses a separate app bar.
- **Outlined icons everywhere**: filled icons were replaced with their outlined versions; filled ones remain only for status badges, the primary send/stop/play actions and the pin marker.

## [0.3.0] - 2026-11-20

### Fixed
- **Replies keep generating when you leave the chat**: Opening another screen (for example to turn a plugin on or off) or switching to another chat no longer stops the reply. Each reply runs in its own chat's lane: it keeps streaming, runs its tools and is saved to the chat it belongs to, and opening that chat again shows it live. Stop, clearing the chat or deleting it still ends the reply.

## [0.2.9] - 2026-11-20

### Added
- **Touch as a mouse in full-screen apps (PTY mode)**: for programs that enable mouse reporting (opencode, htop, vim with `mouse=a`), a tap clicks (tabs, buttons, dialogs), a swipe scrolls with the wheel, and a long press then drag selects. SGR and legacy encodings are supported.
- **Copy**: text a program copies with OSC 52 (opencode copies its selection this way) goes to the Android clipboard; lines in the normal terminal view can be selected with a long press; "Copy output" in the ⋮ menu copies the whole screen.
- **Paste** button in the terminal top bar while a program runs, since the keyboard's clipboard is hidden in the key-capture field. Uses bracketed paste when the program supports it, so multi-line text is not run line by line.
- **Reset Linux environment** (Settings → Terminal): stops running commands and servers, deletes the rootfs and reinstalls a fresh Alpine. /workspace is kept unless you tick "Also delete my files in /workspace".

## [0.2.8] - 2026-11-20

### Fixed
- **Terminal stuck after a program exits (PTY mode)**: Programs that leave helpers behind (opencode's background server) kept proot, and so the session, alive after the command ended, and typed keys went nowhere. The command wrapper now marks when it is done and the leftover processes are stopped, so the prompt comes back.
- **Black frame around full-screen apps**: The terminal's margin stayed pure black while programs paint their own background. The margin now takes the program's edge color and the grid is centered.

## [0.2.7] - 2026-11-20

### Fixed
- **Replies and tool loops stalling in the background**: Android freezes background apps and cuts their network (even resolving `localhost` failed: "Unable to resolve host"), so switching apps mid-reply slowed generation to a crawl and broke tool rounds. While a reply generates or a terminal command runs, a foreground service with a quiet "RaikaAI · Writing a reply…" notification and a partial wake lock keeps the app running; it stops as soon as the work is done.

## [0.2.6] - 2026-11-20

### Fixed
- **Faded text in full-screen apps after the keyboard opens**: The terminal canvas reused cached text layouts that kept the color they were first drawn with, so text a program faded during a resize (opencode's footer) stayed almost invisible. Colors are now applied at draw time.
- **Missing `/etc/machine-id` in the Linux environment**: The Alpine rootfs had none; each install now gets its own random machine id (read by tools such as dbus and telemetry libraries).

## [0.2.5] - 2026-11-20

### Added
- **PTY mode for the terminal** (Settings → Terminal, or the terminal ⋮ menu): commands run on a real pseudo-terminal, so full-screen and interactive programs work (opencode, vim, htop, prompts that ask for input). Keys go straight to the running program, including ESC, TAB, arrows and the sticky CTRL/ALT/SHIFT keys; the size follows the screen and pinch zoom (programs get SIGWINCH). Off by default: the lighter pipe mode stays available.
- **`native/raikapty`**: a small Rust helper (static musl, about 340 KB) shipped as `libraikapty.so`. It opens the PTY on the host, runs proot on it, and relays keys, output and resizes; no packages are needed in the guest. Rebuild with `native/raikapty/build.sh`.
- **Full-screen terminal emulator** (`VtGrid`): alternate screen, absolute cursor addressing, scroll regions, insert/delete lines and characters, deferred wrap, background-color erase, truecolor, DEC line drawing, wide characters, and replies to terminal queries (cursor position, device attributes, window size, colors). It is drawn cell by cell on a canvas, and block and box-drawing characters are drawn as shapes, so logos and borders line up.
- "Stop program" in the terminal ⋮ menu.

### Fixed
- Capability queries (DCS/APC strings) are no longer printed as text in the terminal.

## [0.2.4] - 2026-11-20

### Changed
- **Dev-server bar**: The "server running" bar above the composer now matches the tool cards (neutral surface, thin outline, status dot, monospace URL) and can be folded with × into a small `:port` pill so it no longer covers the chat. Tapping the pill unfolds it; a server on a new URL unfolds it automatically.

## [0.2.3] - 2026-11-20

### Fixed
- **Empty replies after edit/regenerate (Remote)**: Earlier replies were sent back to the provider with their `<think>` reasoning. Once a reply that was only a thinking block was in the history, the model answered with thinking alone, so edits, regenerations and new messages all came back empty. History now carries only the visible text of earlier replies and skips replies left blank.
- **History for the reply being generated**: The in-progress reply is passed through untouched, so splitting tool results or merging messages can no longer make the history drop the wrong message.

## [0.2.2] - 2026-11-20

### Fixed
- **Fake "[ran …]" lines**: 0.1.10 sent earlier tool results back to the model as `[ran `cmd`: exit 0]` notes; models copied that line as text and then invented the output without running anything. History now carries the real `<tool_call>` followed by a `[CONTAINER TOOL RESULT]` message with the actual (truncated) output, and leftover fake lines are removed from history and hidden in the chat.

## [0.2.1] - 2026-11-20

### Added
- **Remote provider presets**: OpenAI, Anthropic, Google Gemini, OpenRouter, Groq, DeepSeek, Mistral, xAI and Ollama (LAN), plus "OpenAI-compatible" for any other server. Base URLs are filled in, each provider keeps its own API key, and "Get a key" opens the provider's key page. Existing setups are detected from their saved URL.

### Changed
- **Models screen**: Tabs are now "Local" and "Remote"; the remote tab shows a provider grid instead of a bare URL field.
- **Sidebar**: Reorganized by priority: Chat, Roleplay, Characters; then Tools (Models, Parameters, Skills & Plugins, Terminal, MCP, Local API); then recent chats with "All chats". Settings, Logs and About moved to a footer, and the hidden "More" section is gone.
- **Consistent names**: Screen titles match the sidebar (Parameters, Local API, MCP, Logs, All chats, Terminal).

### Fixed
- **Replies that stop mid-sentence**: A stream that ends without `[DONE]` or a finish reason is marked "incomplete", and replies that stop early (incomplete, provider limit, thinking only) show a clear notice with a full-size Retry button. A thinking-only reply is retried once automatically.
- **Squeezed Retry button**: Long stats no longer push the message action buttons; stats ellipsize and all actions keep one size.
- **Gemini and other versioned base URLs**: URLs such as `/v1beta/openai` no longer get an extra `/v1` appended.
- Hosted providers are no longer probed with Ollama's `/api/show` (which also sent the API key there).

## [0.2.0] - 2026-11-20

### Added
- **Terminal screen emulation**: The Linux terminal interprets carriage returns, cursor moves, erase sequences and 16/256/truecolor SGR colors, so progress bars, `clear` and tools like fastfetch (logo with info beside it) render like a real terminal, in color.
- **Pinch to zoom** in the terminal (font size is remembered). The column count follows the screen width and font size, and programs get `COLUMNS`/`TERM` to format for it.
- **Real prompt and `cd`**: The prompt shows `root@raika:/path#` and the working directory persists between commands.

### Fixed
- **Raw escape codes in chat tool results**: Output such as `[90m→[0m` from colored programs is rendered through the terminal screen and shown as clean text, for the chat card and for the model.
- **Models faking tool results**: Earlier replies' rendered tool blocks were sent back to the model, which then imitated them (multi-line commands in backticks with invented output). They are now replaced by short notes such as ``[ran `ls`: exit 0]``.

## [0.1.9] - 2026-11-20

### Added
- **Pi-style agent loop on-device**: Local models can now chain Linux tools (look -> edit -> run -> verify) for up to 8 rounds per turn instead of one, with duplicate commands skipped.
- **Multi-edit**: `edit_file` accepts `edits: [{oldText, newText}, ...]`, applied against the original file (each match unique, no overlaps, all or nothing).
- **Project instructions**: `/workspace/AGENTS.md` is loaded into the sandbox prompt automatically.
- **SKILL.md viewer**: Tap a skill to read its Markdown (rendered or source), toggle, edit, export or delete it.
- **Tappable citations**: `[1]` in answers opens the matching source.

### Changed
- **Skills & Plugins**: The hub is split into Plugins (Web Search, Documents, Chat Memory, Code Artifacts, Auto-Compact, Thinking, Vision) and Skills (Markdown instructions), with shorter descriptions and distinct Material You icon tiles instead of the same sparkle everywhere. The skill editor has an icon picker.
- **Web search answers**: Top result pages are read and the most relevant passages kept, and the model is told to answer directly with inline citations. The "Searched the web · N sources" card now sits above the answer.
- **Sandbox tool prompt**: Rewritten pi-style (one line per tool, short rules, working directory).
- **Tool output**: Results sent to the model keep the END of the output, where errors are.

### Fixed
- **Commands hanging on large output**: The sandbox stopped reading output after 64 KB, so chatty commands (npm install, apk add, builds) blocked until the timeout killed them. Output is now always drained.

## [0.1.8] - 2026-11-20

### Fixed
- **HTML preview always failed**: The sandbox filter blocked the preview's own `data:` document (no host), so every preview showed `ERR_HTTP_RESPONSE_CODE_FAILURE`. In-document schemes (`data:`, `about:`, `blob:`) are now allowed. Foreign network hosts stay blocked.
- **"Running …" line left behind**: The status line was removed by a regex that failed on commands containing `'`, `&`, `;`, `|` or `/`. Running commands now show a pending tool card that is replaced exactly by the result.
- **Commands repeated in agent loops**: Remote models received the whole rendered message (status lines and result blocks) as their own history and imitated it. Only the model's raw text of each round is sent now, and identical tool calls are replaced one at a time.
- `_italic_` markdown is rendered.

## [0.1.7] - 2026-11-20

### Changed
- **Terminal prompt follows the output**: The shell prompt is now the last line of the output stream (like a real terminal) instead of being pinned to the bottom of the screen. It scrolls with the output and stays visible when the keyboard opens.

## [0.1.6] - 2026-11-20

### Added
- **Interleaved thinking**: Every `<think>` block now renders where the model produced it (think -> answer -> tool -> think -> answer), instead of all thoughts being merged into one card at the top.
- **Compact tool cards**: Tool calls and Linux environment results render as one-line cards (icon, command, status) with collapsible, height-capped output. Older saved chats are converted too.
- **Context-aware slash commands**: A new harness-style command set (`/new`, `/retry`, `/compact`, `/model`, `/skills`, `/help`, `/search`, `/image`, `/remind`, writing templates, Linux commands) shown as a vertical list with descriptions. Roleplay chats get `/continue`, `/ooc`, `/scene` and hide developer commands; Linux commands appear only once the environment is installed.
- **New bundled skills**: Concise, Bahasa Indonesia, Coding, Step by step, Tutor, Linux agent, plus roleplay-only Immersive roleplay and Slow-burn story. Toggle with `/skills <name>`.

### Changed
- **Linux environment screen**: Full-screen black terminal with numbered session tabs in the top bar, a Terminal/Files toggle and an overflow menu, and a two-row extra-keys grid (`SYM` page for shell symbols, hold-to-repeat arrows). Layout inspired by Appine.
- **Markdown rendering**: Consecutive lines form one paragraph, nested lists are indented, `####`-level headings, horizontal rules, task lists, `~~strikethrough~~`, `***bold italic***` and `1)` lists are supported, with better spacing.
- **Roleplay prompt**: Roleplay chats no longer receive code-output rules, tool catalogs or non-roleplay skills, which saves context and keeps replies in character.
- **Previous reasoning is dropped from history**: Earlier turns' `<think>` blocks are no longer sent back to the model, even with thinking enabled.
- **License**: RaikaAI is now licensed under AGPL-3.0.

### Fixed
- **Engine use-after-close race**: A generation fetched the LiteRT engine in one critical section and used it in another, so a concurrent request (e.g. a `/v1` client) could reload and close the engine in between. Engine acquisition and generation now happen under the same lock.
- **Model reload on every message after GPU fallback**: After a GPU -> CPU fallback the active backend ("CPU (Fallback)") never matched the requested "GPU", forcing a full model reload (and a fresh failed GPU attempt) for every message. The requested backend is now tracked separately.
- **Model reload thrash from `max_tokens`**: Any change in the token budget (e.g. `/v1` clients sending different `max_tokens`) reloaded the multi-GB model. The engine now only reloads when a larger budget is needed.
- **Repeated reloads for unsupported modalities**: Image/audio requests on a model whose vision/audio encoder failed to initialize no longer trigger a pointless reload each time.
- **Workspace path check**: File tools used a bare string-prefix check, which also accepted sibling folders such as `workspace2/`. The check now uses a path-segment boundary (same fix for the rootfs tar extractor).
- **SSRF guard gaps**: Blocked addresses now also include `100.64.0.0/10` (carrier-grade NAT, used by Tailscale), `0.0.0.0/8`, and IPv6 unique-local `fc00::/7`.

### Security
- Removed the hardcoded release keystore password fallback from `app/build.gradle.kts`; local builds read it from the gitignored `local.properties` (CI keeps using secrets).

## [0.1.5] - 2026-10-24

### Added
- **Interactive Roleplay Hub**: Dedicated roleplay area with categorised character gallery (Honkai Star Rail, Wuthering Waves, Genshin Impact, Zenless Zone Zero, Arknights Endfield, Others), trending carousels, instant search, and community character creation (contributed by @ilyaSuperGlue in #1).
- **Supabase Cloud Character Sync**: Centralized community character catalog powered by Supabase PostgreSQL backend with secure Row Level Security (RLS) policies and RPC view counting.
- **Safebooru Image Integration**: Built-in anime and game image tag search engine for one-tap character avatar selection during character creation.
- **Right Sidebar Quick Character Switcher**: Dedicated right-hand drawer sheet with avatar previews and search filter for instant character switching. Automatically resumes the latest conversation with the selected character or starts a fresh session.
- **Roleplay Profile & Persona Persistence**: Isolated roleplay user persona (Name, Gender, Description) and `{{user}}` dynamic greeting variable substitution that automatically carries over when roleplay characters are selected in main chat.
- **Character JSON Export & Copy**: Full Character Card v2 schema compatibility with one-tap JSON copying to clipboard for both custom and built-in personas.
- **Supabase Keep-Alive Automation**: Scheduled GitHub Actions workflow pinging backend endpoints to prevent inactivity auto-pause on free-tier infrastructure.

### Changed
- **Isolated Roleplay Storage**: Roleplay conversations are stored in an independent database partition (`roleplay_chats`), preventing roleplay sessions from cluttering the primary drawer history.
- **Streamlined Built-in Presets**: Simplified built-in personas down to General as the primary default, allowing full preview, prompt editing, and JSON export.
- **Coil 3 Image Pipeline**: Added Coil 3 Compose image loader supporting HTTP(S), local files, and content URIs with automatic disk/memory caching and crossfade animations.

### Fixed & Optimized
- **Instant Roleplay Gallery Loading**: Converted raw multi-megabyte Safebooru PNG downloads to lightweight thumbnail CDN endpoints (~14 KB per image), reducing category load times from 20+ seconds to instant.
- **Pagination Loop Mitigation**: Replaced greedy recomposition triggers with scroll-driven `derivedStateOf` threshold detection for smooth on-demand pagination.
- **Chat History Leakage**: Resolved issue where roleplay chats exited via back navigation would erroneously persist into the general history repository.
- **Null Safety in Remote AI Reasoning**: Fixed `RemoteAiClient` JSON parser where `optString` transformed metadata null tokens into the literal string `"null"`.

### Contributors
- Special thanks to @ilyaSuperGlue for contributing the Roleplay feature in #1!

## [0.1.4] - 2026-10-16

### Added
- **Chrome-Style Multi-Tab Terminal Sessions**: Horizontal tab bar (`Term 1`, `Term 2`, `+` button) allowing multiple simultaneous terminal sessions in Linux Environment. Tabs execute concurrently without thread blocking, enabling background servers (like Vite/Node) in one tab while executing shell commands in another.
- **Terminal State Persistency**: Terminal transcript, command history, and draft inputs survive tab switching and app navigation via ViewModel lifecycle.
- **Real-Time Streaming Output**: Asynchronous streaming stdout/stderr in PRoot terminal with live output rendering, eliminating frozen UI and 60s hard timeouts for long-running processes.
- **Dedicated File Manipulation Tools**: Container agent tools `write_file`, `edit_file`, and `read_file` replace fragile shell echo/cat scripts with robust local filesystem operations.
- **Collapsible Code Blocks**: Markdown code blocks default to 10 lines with line counter badges and one-tap Expand/Collapse controls.
- **Quick Stop Action**: Red instant-stop action button (`✕`) alongside command prompt for terminating active running foreground processes without keyboard modifiers.
- **Remote Stream Resilience**: Exponential backoff auto-retry (up to 3x) for remote AI inference streams on socket timeouts, network drops, and HTTP 429/500-504 errors.

### Fixed
- **Multi-Round Thinking & Action Block Leakage**: Extracted and rendered all multi-turn `<think>`, `<thought>`, `<thinking>`, and `<action>` tags into structured UI cards without raw XML tag leakage in chat bubbles, TTS, or history summaries.
- **Streaming Tool Call Escapes**: Formatted streaming tool calls into stylized code block cards in real-time, preventing escaped JSON string leakage.
- **Agent Loop Premature Halts**: Expanded autonomous coding loop limit from 10 to 30 rounds with automatic length continuation when token limits are reached (`finish_reason == "length"`).
- **Server Status Hallucination**: Strict agent prompt guards preventing LLMs from claiming dev servers are running without verified container daemon startup.
- **Background Operator Redirection**: Terminal commands with `&` or background syntax automatically route to detached container daemons (`startDaemon`) to prevent execution hangs.

## [0.1.3] - 2026-10-16

### Added
- **On-Device Alpine Linux Sandbox (PRoot, No Root)**: Embedded Alpine userland with a persistent `/workspace` folder shared directly between chat attachments (`/workspace/chat_uploads/`), manual terminal, and AI tool calls. PRoot engine and dependencies ship directly inside the APK (`jniLibs`); only the minimal ~3 MB Alpine rootfs downloads from upstream.
- **Termux-Style Terminal**: Shell-in-a-box terminal with a compact 3-row virtual keypad (`ESC`, `TAB`, `CTRL`, `ALT`, `SHIFT`, arrows, `PGUP`, `PGDN`, etc.), sticky modifiers, TAB auto-completion for files and directories, command history navigation (`↑`/`↓`), and 11sp monospace font.
- **Workspace File Manager**: Full folder navigation inside `/workspace`, recursive directory deletion, file previews, SAF import, and direct export/share to system Downloads or chat.
- **Autonomous Agent Loop & Background Daemons**: 6-round OpenCode-style execution loop with automatic repeat-command deduplication and explicit daemon tracking (`run_in_background`), allowing non-blocking web servers and background processes with `/daemons` and `/killdaemon`.
- **Dynamic DNS Syncing**: Automatic synchronization of Android active network DNS configuration into container `/etc/resolv.conf` to avoid dead DNS states across Wi-Fi and cellular switches. Added `/fix-dns` and `/nettest` diagnostic tools.
- **In-Chat Sandbox Permissions**: Quick permission management via `/permission` command (`yolo`, `allow`, `forget`, `clear`, status) and persistent "Always Allow" action in approval dialogs without leaving chat.
- **Bundled Curl Compatibility Shim**: Built-in `/usr/local/bin/curl` wrapper around busybox `wget` with coaching hints to support standard curl commands out-of-the-box.

### Fixed
- **Slash Command Cursor Alignment**: Resolved issue where tapping slash command chips (`/permission`, `/run`, `/compact`) placed the cursor in the middle or beginning of the text field instead of at the end.
- **Package Installation Syscall Error**: Configured `PROOT_NO_SECCOMP=1` to prevent bogus `EACCES` errors during `apk add` on modern Android kernels.
- **Thinking-Only Model Responses**: Preserved thinking-only model outputs instead of deleting bubbles and raising empty response errors.
- **Compaction History Preservation**: Retained the last 4 verbatim conversation turns during `/compact` with validated structured headings.
- **UI Decrapification**: Removed all pictographic emojis from system messages, status badges, and command outputs.
- **Models Hub Cleanup**: Removed redundant Linux Environment card from Models Hub in favor of the dedicated `/env` screen and `/permission` commands.

## [0.1.2] - 2026-10-15

### Added
- **OpenCode-Style Context Compaction (`/compact`)**: Summarizes multi-turn history into a structured briefing (`Objective`, `Requirements`, `Decisions`, `Work State`) rendered as divider-style cards that don't inflate context; live token counter drops immediately after compacting.
- **Logarithmic Context Slider (1K – 1M Tokens)**: Continuous context allocation slider in Generation Parameters with quick preset buttons; local and remote settings stay independent.
- **Claude-Style Attachment Cards**: Document attachments render as compact file chips with metadata and a text inspector modal instead of dumping raw text into chat bubbles.
- **Remote Model Caching**: Detected remote models persist to DataStore and are available instantly on restart with background refresh.

### Changed
- **Model Quick Switch Menu**: Pure model switcher — removed Pin, Compare, and Hub buttons; shows all on-device and remote models with a correct active indicator per mode.
- **Slash Command Suggestions**: Compact single-line popup without descriptions.
- **Drawer Navigation**: Generation Parameters moved above History.
- **Localization**: Slash commands, compaction output, and container strings standardized to English.

### Fixed
- **Model Switcher Active Indicator**: Local model no longer stays checked while in remote mode.
- **Attachment Context Retention**: Document payloads survive message edit and regeneration.

## [0.1.1] - 2026-10-15

### Added
- **Branch in New Chat (`CallSplit`)**: Fork and duplicate active conversation history up to any selected turn into a brand-new chat session directly from Assistant action buttons, multi-branch navigation row (`BranchNavRow`), or User edit dialog.
- **On-Device PDF Text Extraction**: Pure Kotlin PDF content stream parser extracting text from `/FlateDecode` compressed and uncompressed streams without third-party dependencies, allowing text-only on-device LLMs to read and summarize PDF attachments.
- **Real-Time Context Bar**: Live token usage counter and percentage bar updates smoothly during streaming generation via reactive Compose snapshot states.

### Fixed
- **PDF Attachment Loss & Fallback Prompt Bug**: Resolved issue where attaching PDF files failed silently due to non-seekable ContentProvider file descriptors in Android's `PdfRenderer`, causing the attachment to vanish and triggering the generic fallback prompt. PDF files are now staged to seekable app storage and always retained in the message.
- **LiteRT Generation Stopping Mid-Turn**: Fixed generation cutting off prematurely on long turns by actively tracking `activeTokenBudget`, enforcing dynamic output headroom reservation (384–1024 tokens) in `PromptBuilder`, and reloading engine configurations on context budget shifts.
- **Context Bar Inactivity During Inference**: Fixed context bar progress and token count freezing during token streaming due to wall-clock throttling and non-reactive variables.

---

## [0.1.0] - 2026-10-14

### Initial Release — RaikaAI (On-Device AI Engine & Local LLM Server)

This is the first official open-source release of **RaikaAI**, a standalone Android on-device LLM workstation and OpenAI-compatible local inference server powered by Google AI Edge LiteRT-LM.

---

### Features & Capabilities

#### On-Device LiteRT-LM Inference Engine
- **Hardware Acceleration**: GPU acceleration via OpenCL (Qualcomm Adreno & ARM Mali) with automatic CPU fallback (Arm NEON / XNNPack).
- **Supported Models**: Native execution of `.litertlm` compiled weights including Gemma 2B / 3 / 4, SmolLM 135M / 360M, Llama 3.2 1B / 3B, Qwen 2.5, DeepSeek R1 Distill, and user-compiled custom models.
- **Real-Time Streaming**: Token-by-token generation streaming with sub-millisecond latency.
- **Generation Controls**: Instant prompt cancellation via `Conversation.cancelProcess()`, adjustable temperature, Top-K, Top-P, and max output tokens.
- **Context Window Indicator**: Visual token usage meter tracking conversation capacity against model context limit.
- **Speed Benchmark Tool**: Built-in benchmark suite reporting Time-To-First-Token (TTFT ms), generation speed (tok/s), latency, and GPU vs. CPU comparison.

#### Embedded OpenAI-Compatible HTTP/CIO Server
- **Ktor Embedded Server**: Runs directly on device in an Android Foreground Service with sticky notification and CPU wake-lock.
- **API Endpoints**:
  - `POST /v1/chat/completions` (Server-Sent Events streaming `stream: true` and single JSON response).
  - `POST /v1/completions` (raw text completion for IDE extensions).
  - `GET /v1/models` and `GET /v1/models/{model}` (active model metadata).
  - `GET /health` (status check).
- **Universal Client Compatibility**: Works out of the box with VS Code (Continue, Roo Code), OpenWebUI, SillyTavern, Obsidian Local LLM plugins, LangChain, curl, and Python `openai` SDK.
- **Network & Security**: Automatic LAN / Wi-Fi IP detection (`http://<ip>:8080/v1`), global CORS preflight support, and configurable API Bearer token protection.

#### Model Hub & Storage Manager
- **Verified Preset Catalog**: One-click download for optimized mobile models (SmolLM, Gemma, Llama).
- **Hugging Face Hub Integration**: In-app search for `.litertlm` repositories with support for gated models via Hugging Face Access Tokens.
- **Resumeable Downloader**: Multi-part chunked HTTP downloader with pause, resume, progress bar, and storage space checks.
- **SAF Importer**: Import external `.litertlm` models directly from SD card, Downloads folder, or USB OTG via Android Storage Access Framework.
- **Disk Management**: Visual storage allocation breakdown with one-tap model deletion.

#### Characters & Persona System
- **Custom AI Characters**: Create and configure unlimited personas with custom avatar icon, name, description, and system prompt.
- **TopBar Carousel**: Instant persona switching during active conversation without resetting context.
- **DataStore Persistence**: Fully persistent personas saved across app restarts.

#### Agentic Skills & Web Tools
- **Skills Hub**: Extensible agent plugin architecture with capability discovery.
- **Live Web Search**: Tool calling integration for real-time web querying via SearXNG and DuckDuckGo.
- **Structured Tool Execution**: Agent loop with JSON schema function execution and synthesis.

#### Multimodal & Attachment Processing
- **Image Support**: Vision model inputs via camera capture and gallery selection (`image/*`).
- **Document Ingestion**: Read and parse text files (`.txt`, `.md`, `.json`, `.csv`, `.log`).
- **PDF Extraction**: Native on-device PDF text rendering and extraction via Android `PdfRenderer`.
- **Audio Support**: Attachment pipeline handling audio formats (`audio/*`).

#### Material You Interface & Usability
- **Modern Jetpack Compose UI**: Built strictly with Material 3 Expressive components and fluid animations.
- **Dynamic Theming**: Palette harmonization powered by `MaterialKolor` with dynamic accent color picker.
- **Pure Black OLED Mode**: Deep contrast theme optimized for OLED/AMOLED battery saving.
- **Rich Markdown Viewer**: Code syntax formatting with one-click code copy feedback.
- **Offline TTS**: On-device Text-to-Speech playback using Android `TextToSpeech` engine.
- **Chat History**: Full session management with persistent JSON storage in app-private directory.

---

### Security & Privacy
- **100% Local Processing**: In on-device mode, zero telemetry, prompts, or model weights are ever sent to external cloud servers.
- **Encrypted Credentials**: API keys and Hugging Face tokens are secured at rest using Android Keystore-backed AES-GCM encryption.
- **Copyleft Licensing**: Fully licensed under the **GNU General Public License v3.0 (GPLv3)**.
