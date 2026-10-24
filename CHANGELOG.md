# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Added
- **Interactive Roleplay Hub**: Dedicated roleplay area with categorised character gallery (Honkai Star Rail, Wuthering Waves, Genshin Impact, Zenless Zone Zero, Arknights Endfield, Others), trending carousels, instant search, and community character creation.
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
