# Architecture

Package: `com.raikaai.app` · Kotlin + Jetpack Compose (Material 3) · minSdk 26.

```
app/src/main/java/com/raikaai/app/
├── MainActivity.kt / App.kt
├── core/
│   ├── agent/                          # AgentConfig (SOUL/AGENTS/MEMORY.md), ChatArchive (chats as Markdown)
│   ├── container/                      # ContainerManager (proot, PTY runs, daemons, bundled rootfs), policy
│   ├── engine/LiteRtEngineManager.kt   # JNI bridge, load/unload, telemetry, thinking-channel wrap
│   ├── remote/                         # RemoteAiClient (SSE), RemoteProviders (presets, auth headers)
│   ├── work/BackgroundWorkService.kt   # foreground service + wakelock while replying or running commands
│   ├── audio/                          # recorder (WAV) + player helpers
│   └── server/
│       ├── OpenAiServer.kt             # Ktor CIO routes, auth + browser-origin guard, CORS
│       ├── ChatServerService.kt         # foreground service + WakeLock
│       ├── PromptBuilder.kt            # Gemma/ChatML/Llama3 templates, thinking toggle
│       └── SseProtocol.kt              # SSE chunk formatting
├── localai/                            # HF resolver, catalog, downloader, SAF import
├── data/                               # ChatRepository (tree), SettingsRepository (DataStore +
│                                       # EncryptedSharedPreferences), ChatConstants, SamplingDefaults, AppLimits
├── skills/ / mcp/ / rag/ / reminder/ / attachments/ / web/
└── ui/
    ├── chat/                           # ChatScreen + ChatTopBar + ChatDialogsHost + ChatOverlayState,
    │                                   # ChatComponents (bubbles), ChatViewModel (orchestration),
    │                                   # PersonaStore, BenchmarkRunner, ServerControls, AttachmentPipeline
    ├── agent/                          # Agent hub: Plugins · Skills · MCP · Memory
    ├── environment/                    # Terminal: AnsiScreen, VtGrid (VT emulator), TerminalGridCanvas
    ├── roleplay/                       # Characters hub (Discover · Mine · Create), roleplay chat
    ├── characters/ / models/ / server/ / skills/ / mcp/ / logs/ / history/ / settings/ / sampler/
    ├── component/                      # MarkdownText (thinking cards, code blocks), badges, sheets
    └── theme/                          # MaterialKolor dynamic theming + OLED black
```

Outside the app module: `native/raikapty/` (Rust PTY helper shipped as `libraikapty.so`) and
`supabase/migrations/` (roleplay catalog schema).

## Key flows

- **Local send** (`ChatViewModel.sendMessage`): attachments → prompt → `runGenerationPipeline`
  (`GenerationCtx`: context-fit → RAG → template → `engine.streamResponse` → collect)
  → thinking-fallback retry (once, no-thinking) → tool-call check → persist/prune.
- **Regenerate / edit-regen**: same shared pipeline (`streamWithThinkingFallback` / single pass).
- **Remote send**: OpenAI-compatible SSE via `RemoteAiClient.streamChat`; `reasoning_content`
  is wrapped into `<think>` for the Thought Process card; blank streams are dropped with an error.
- **Thinking**: LiteRT thinking channel is wrapped as `<think>…</think>` by the engine
  (auto-closed); with thinking off, prompts carry a no-think directive + pre-closed tag.
- **Branching**: flat `nodes` + `branchActive` map; `persist()` merges O(n) and prunes
  unreachable nodes (active path + siblings only).
- **Chat lanes**: each conversation has a `ChatLane`; a generation keeps its lane (via a
  ThreadLocal coroutine context element) when the user switches chats, so replies never stop
  on navigation. Messages sent during a reply are queued per chat.
- **Agent prompt**: `withRagSystem` adds the AgentConfig block (SOUL/AGENTS/MEMORY + capabilities),
  skills, MCP tools and Linux tools. On-device models get compact, capped versions; roleplay keeps
  the character card first and adds only a short memory.
- **Agent loop**: local and remote loops share `dispatchContainerTool`, which also runs Raika's own
  tools (`remember`, `add_mcp_server`) and MCP tools, each behind the approval gate.
- **Linux**: proot binds `/workspace`, `/root/.raika` (app `files/agent`) and, when enabled with
  all-files access, phone storage at `/sdcard`. The terminal always runs on a PTY; the alternate
  screen is handed to `VtGrid`. Stdio MCP servers are long-lived proot processes.
- **Secrets**: API/HF/server tokens live in EncryptedSharedPreferences (plaintext-DataStore
  migration runs eagerly once at startup, never inside flow collectors).

## Conventions

- English-only code & comments (functional non-English regex/stopwords excepted).
- No behavior-change refactors without tests green: `compileDebugKotlin`,
  `testDebugUnitTest`, `assembleRelease -PabiFilter=arm64-v8a`.
- Gson-parsed models must go through `normalize()` (see `PersonaPreset`, `Skill`,
  `McpServerConfig`, `Conversation`).
- Disk/network I/O off the main thread; bitmaps downsampled off-composition.
