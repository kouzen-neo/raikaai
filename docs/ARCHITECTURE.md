# Architecture

Package: `com.raikaai.app` · Kotlin + Jetpack Compose (Material 3) · minSdk 26.

```
app/src/main/java/com/raikaai/app/
├── MainActivity.kt / App.kt
├── core/
│   ├── engine/LiteRtEngineManager.kt   # JNI bridge, load/unload, telemetry, thinking-channel wrap
│   ├── remote/RemoteAiClient.kt        # SSE streaming, reasoning_content, image generations
│   ├── audio/                          # recorder (WAV) + player helpers
│   └── server/
│       ├── OpenAiServer.kt             # Ktor CIO routes, auth + browser-origin guard, CORS
│       ├── ChatServerService.kt         # foreground service + WakeLock
│       ├── PromptBuilder.kt            # Gemma/ChatML/Llama3 templates, thinking toggle
│       └── SseProtocol.kt              # SSE chunk formatting
├── localai/                            # HF resolver, catalog, downloader, SAF import
├── data/                               # ChatRepository (tree), SettingsRepository (DataStore +
│                                       # EncryptedSharedPreferences), ChatConstants, SamplingDefaults, AppLimits
├── runtime/                            # RuntimeMode, ToolRuntime, PiAgentRuntime
├── core/tool/                          # ToolCallParser shared by agent turns
├── mcp/ / rag/ / reminder/ / attachments/ / web/
└── ui/
    ├── chat/                           # ChatScreen + ChatTopBar + ChatDialogsHost + ChatOverlayState,
    │                                   # ChatComponents (bubbles), ChatViewModel (orchestration),
    │                                   # PersonaStore, BenchmarkRunner, ServerControls, AttachmentPipeline
    ├── characters/ / models/ / server/ / mcp/ / logs/ / history/ / settings/ / sampler/
    ├── component/                      # MarkdownText (thinking cards, code blocks), badges, sheets
    └── theme/                          # MaterialKolor dynamic theming + OLED black
```

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
- **Secrets**: API/HF/server tokens live in EncryptedSharedPreferences (plaintext-DataStore
  migration runs eagerly once at startup, never inside flow collectors).

## Conventions

- English-only code & comments (functional non-English regex/stopwords excepted).
- No behavior-change refactors without tests green: `compileDebugKotlin`,
  `testDebugUnitTest`, `assembleRelease -PabiFilter=arm64-v8a`.
- Gson-parsed models must go through `normalize()` (see `PersonaPreset`,
  `McpServerConfig`, `Conversation`).
- Disk/network I/O off the main thread; bitmaps downsampled off-composition.
