# Port plan: Pi harness + KazeTerm runtime for RaikaAI

Status: research/decision note, 2026-11-18. Supersedes `PORT_DSH_GO.md` as the
chosen direction. Sources: `/root/Documents/pi`, `/root/Documents/raikaai`,
`/root/Documents/kazeterm`, `/root/Documents/dsh-Go` (all read-only study).

## Decision

- **Agent harness:** upstream Pi (MIT). Port/bundle its agent loop, tools,
  skills, MCP client, compaction, and RPC surface.
- **Linux runtime:** KazeTerm `Machine`/JNI replaces RaikaAI's PRoot
  `ContainerManager` and the line-based terminal.
- **Keep:** RaikaAI's LLM engine, Ktor server, model manager, chat/branching,
  RAG, characters, vision/audio/TTS, theme, benchmark, compare mode.
- **Do not lose:** web search/fetch, scheduling, subagents, permission gates.
  Pi does not ship these, so they become first-party Pi extensions or Kotlin
  services. DSH Go is used as a behavior reference, not as a code donor.

## RaikaAI feature inventory (source-verified)

| Feature | Implementation | Notes |
|---|---|---|
| On-device chat + streaming + stop + sampling | `core/engine/LiteRtEngineManager.kt:262,507` | AAR `litertlm-android:0.16.0`; no app-owned JNI |
| Thinking channel, multimodal, GPU/CPU fallback | `LiteRtEngineManager.kt:519-640,672-758` | `<think>` wrapping in engine |
| Ktor `/v1` server | `core/server/OpenAiServer.kt:316-534`, `SseProtocol.kt`, `ChatServerService.kt` | bearer + Origin guard + CORS |
| Model manager (HF, gated, Range resume, SAF) | `localai/LocalAiModel.kt`, `HuggingFaceModelResolver.kt`, `ModelDownloadService.kt:284-366,569`, `LocalModelManager.kt:185` | "pause" is cancel only |
| Chat tree, branching, history | `data/ChatRepository.kt:55-56,181-190,444-528` | flat nodes + branchActive |
| RAG (BM25, chunk 1100/150, top-K 3) | `rag/RagManager.kt:36-60,300-333` | `[DocN]` labels; no embeddings |
| Characters + cloud roleplay hub | `ui/chat/PersonaStore.kt`, `ui/characters/CharactersScreen.kt`, `data/RoleplayRepository.kt:47-125` | Supabase + Safebooru undocumented in README |
| Vision/audio/STT/TTS | `core/audio/*`, `ui/chat/SpeechRecognizerHelper.kt`, `TtsHelper.kt`, `ImageDecode.kt` | Android framework APIs |
| Compare / benchmark | `ChatViewModel.kt:4047-4110`, `ui/chat/BenchmarkRunner.kt:135-189` | local/local or local/remote |
| Theme (MaterialKolor, OLED black) | `ui/theme/Theme.kt:16-38` | — |
| MarkdownText (custom Compose) | `ui/component/MarkdownText.kt:82-183,513-670` | code block collapse >10 lines, no syntax highlighting, no nested lists |
| PRoot Linux + multi-tab terminal | `core/container/ContainerManager.kt:346-435,579-663`, `ui/environment/EnvironmentScreen.kt:520-745` | ProcessBuilder of `libproot.so`; no PTY |
| Agent loop | `ui/chat/ChatViewModel.kt:3508-3639` | `MAX_CONTAINER_ROUNDS=30`, **remote path only**; local path is one round (`:1701-1862`) |
| Tools | `ChatViewModel.kt:1952-2222`, `web/WebSearchManager.kt:55-139`, `web/UrlSummarizer.kt:48-105` | regex/Gson parsing, no function-calling registry |
| Skills | `skills/SkillsManager.kt:50-91,254-273`, `ToolCallParser.kt` | prompt text only; no hooks or execution |
| MCP | `mcp/McpClient.kt:33-45,131-205`, `McpManager.kt:95-146` | Streamable HTTP only; local-source only |
| `/compact` | `ChatViewModel.kt:2853-3008` | keeps last 4 turns verbatim, structured briefing |
| Approval | `core/container/ContainerManifest.kt:101,343-366`, `ChatViewModel.kt:4144-4307`, `ChatDialogsHost.kt:352-468` | ALLOW/ASK/DENY; MCP never auto-approved |

## Disposition

### Keep unchanged
LLM engine, Ktor server + auth/CORS, model manager, chat tree, RAG, characters,
vision/audio/TTS, theme, compare, benchmark, HF downloader.

### Replace
| Old | New | Reason |
|---|---|---|
| `ContainerManager` (PRoot, ProcessBuilder) | KazeTerm `Machine` + JNI | real ARM64 engine, soft-MMU, persistent inode store, PTY console |
| `EnvironmentScreen` line-stream terminal | KazeTerm `VtView`/libghostty-vt (or Compose shell fed by Machine PTY) | current terminal is not a real PTY |
| `ChatViewModel` agent loop + `ToolCallParser` regex | Pi `packages/agent` + `packages/coding-agent` loop | real tool calling, steering/follow-up, sessions, compaction |
| RaikaAI skills (static prompt text) | delete now; Pi skills reintroduced later | clean break, no legacy JSON migration |
| RaikaAI Kotlin MCP client | `@earendil-works/pi-mcp` or keep both with distinct prefixes | avoid double-registering servers; Pi names tools `mcp__<server>__<tool>` |
| `MarkdownText` internals | DSH Go rendering behaviors reimplemented in Compose | stream repair, block cache, tool cards, approval card |
| `InstallPolicy` + dialog as sole gate | Pi permission-gate extension + native Kotlin `SafetyGate` | Pi has no permission system |

### Build new (Pi does not provide)
- **Web search / fetch** as a Pi extension wrapping existing `WebSearchManager`
  (Google News RSS / DDG Lite / Wikipedia / Jina Reader) and `UrlSummarizer`.
  Keep numeric citations `[1]`, not `[DocN]` (those are local RAG).
- **Scheduling / cron** tool. No upstream equivalent.
- **Subagents** — port `packages/coding-agent/examples/extensions/subagent/`.
- **Plan mode** — port `examples/extensions/plan-mode/` (optional).
- **Permission/safety** — port `examples/extensions/permission-gate.ts` and
  `protected-paths.ts`; native hard rules must stay in Kotlin, not in the model.
- **Node adapter in the KazeTerm guest** exposing HTTP/WS to Kotlin, because Pi
  RPC is JSONL over stdin/stdout (`docs/rpc.md:3,50-56`). Translate Pi events to
  `assistant-stream`, `tool/call`, `approval/request`, `turn/end`.
- **LLM wiring** — point Pi's `baseUrl` at RaikaAI's local Ktor `/v1`
  (`pi/docs/custom-provider.md:30`, `models.md:54`).

### Skills: remove legacy, rebuild on Pi
Decision (updated): delete the RaikaAI skill subsystem entirely and replace it
later with Pi skills. Remove `SkillsManager.kt`, `Skill.kt`, the Skills UI,
`custom_skills.json`, `preset_toggles.json`, the 4 built-ins,
`getActivePromptInstructions()` (:254-273) and the skill block in
`withRagSystem` (:801-818). No skill-specific DataStore keys were found in
`SettingsRepository.kt`, so no preference migration is needed for skills.

Two consequences:
- Web search was partly gated by a "web researcher" skill (`ChatViewModel:805-807`).
  Make `enableWebSearch` the sole persistent gate; keep `/search` and `/web`.
- Do not delete `ToolCallParser.kt` with the skills: `parseGenericCall` is used
  by the agent loop (`ChatViewModel:3545`). Delete it only when Pi replaces the loop.

Pi skills will be reintroduced as `/workspace/.pi/skills/<slug>/SKILL.md` in the
KazeTerm guest (Agent Skills spec, `pi/docs/skills.md:9-39,41-53`).

### Settings
Keep unchanged: LLM sampling, Ktor server (port/bindAll/authToken/systemPrompt/
contextWindow), HF token in `EncryptedSharedPreferences`, RAG, compare,
benchmark, theme/accent/OLED, model catalog, roleplay/Supabase settings.

Migrate or retire:
- `mcpEnabled` / `mcpServersJson` / `mcp_token_*` (`SettingsRepository.kt:89-91,259-260,664-741`)
  → Pi `~/.pi/agent/mcp.json` or `.pi/mcp.json` in the workspace; keep old keys
  read-only during transition to avoid losing server definitions.
- `containerYolo` / `containerAlwaysAllowJson` (`:118-120,278-279,540-541`)
  → retire when the native `SafetyGate` and Pi permission gate land.
- `enableWebSearch` (`:275`) stays, now the sole web-search gate.

Add: `runtimeMode` (legacy PRoot vs KazeTerm+Pi), `piRpcPort`/`piRpcToken`,
`piProviderBaseUrl` (default to the local Ktor `/v1`), `nodeInstallMode`
(bundled rootfs vs first-boot install), and approval mode for the new gate.

### LiteRT LM
Keep it. It becomes the inference backend behind RaikaAI's Ktor `/v1`; Pi is
configured with `baseUrl` pointing there (`pi/docs/custom-provider.md:30`).
Do not replace LiteRT with Pi.

Critical gap: `ChatCompletionRequest` has no `tools` field and the response has
no `tool_calls` (`OpenAiServer.kt:732-740`); `LiteRtEngineManager.kt` has no
tool/function-calling support at all. Pi relies on native tool calling, so it
cannot drive tools through `/v1` as-is. Options:
1. Add tool-calling to `/v1` and emulate it with prompt serialization +
   response parsing if LiteRT has no constrained decoding.
2. Put the shim in the Node adapter: expose an OpenAI-compatible endpoint to
   Pi, inject tool schemas into the system prompt, parse tool calls from the
   text response, and return proper `tool_calls` to Pi. This keeps LiteRT and
   the Ktor server untouched and confines the parser to one testable module.
3. Route Pi to a remote provider that supports tool calling (loses on-device).

Recommendation: option 2 for the first milestone; swap to option 1 if/when the
engine gains native function calling. Keep the existing `<think>` streaming and
thinking-fallback behavior, which Pi does not need to know about.

### Known bug: thinking display (fix during migration)
Symptom: only one thinking block is shown; a later thinking phase makes previous
text disappear and the thinking card expands/collapses repeatedly.

Root cause (source-verified):
- Thinking is encoded as `<think>...</think>` inside the message string
  (`LiteRtEngineManager.kt:300-341`, `RemoteAiClient.kt:511-524`). A single turn
  can emit several such blocks.
- `extractThinking()` joins every block into one `combinedThoughts`
  (`MarkdownText.kt:503-505`) and the UI renders exactly one `ThinkingCard`
  (`MarkdownText.kt:102-106`), so multiple phases collapse into one card.
- `ThinkingCard` uses `remember(isComplete) { mutableStateOf(!isComplete) }`
  (`MarkdownText.kt:196`); `isComplete` flips false→true at each `</think>` and
  back on the next `<think>`, resetting the expanded state (flicker).
- The thinking-only fallback **wipes the whole assistant message**
  (`ChatViewModel.kt:1683-1686`: `content = ""`) before retrying, which deletes
  already-streamed text and thinking. The regex itself does not drop text
  (verified by simulating it against multi-block strings).

Fix direction: stop encoding thinking in the content string. Add structured
`thinkingBlocks`/`reasoning` to the chat message (DSH Go does this with
`AssistantChatItem.reasoning` and a separate row, `chat_rows.dart:500-509`),
emit `thinking_delta` / `text_delta` from both engines, render a list of blocks
with stable keys, and keep pass-1 thinking on fallback instead of clearing the
message. Pi's event stream is already structured, so this aligns with the
harness migration. A smaller interim fix: do not wipe on fallback, render a list
instead of one combined card, and stop keying expansion on `isComplete`.

### Drop
PRoot binaries and GPL attribution burden, regex tool-call parser, stale
SearXNG claim, the "one-round local agent" behavior, stale comments (3 vs 30).

## Pi upstream inventory (MIT, smallest coherent set)
- `pi-ai` + `pi-schema`, `pi-agent-core` (`agent.ts:194-263`, `agent-loop.ts:172-330`),
  `pi-coding-agent` (8 built-in tools: `read, bash, powershell, edit, write,
  grep, find, ls`; default enabled: `read, bash, edit, write`), `pi-mcp` v1.5.2.
- Built-in tools are `core/tools/*`; extensions loader `src/extensions/*`.
- Compaction `core/compaction/*`; skills `core/skills.ts:168,277,414-486`.
- Subagent/plan-mode/permission-gate are **examples**, not built-ins.

## Architecture

```
RaikaAI (Compose UI + LiteRT + Ktor /v1)
  │ HTTP/WS 127.0.0.1 + token
  ▼
Node adapter (guest, KazeTerm)
  │ child_process JSONL (Pi RPC)
  ▼
Pi agent + extensions (web/fetch, MCP, skills, subagent, schedule, permission)
  │ baseUrl → RaikaAI /v1
  ▼
KazeTerm engine + kernel + /workspace in inode store
```

## Runtime modes (must remain two products in one app)

RaikaAI already works without any Linux container: chat, LiteRT inference,
Ktor `/v1`, model manager, RAG, characters, web search (`WebSearchManager`),
link summary (`UrlSummarizer`), HTTP MCP (`McpManager`), image generation,
compare and benchmark are all Kotlin/network features, not container features
(`docs/ARCHITECTURE.md:5-28`, `ChatViewModel:1756-1861,2321-2327`). Keep this as
the default mode. Do not make KazeTerm or Pi mandatory.

`runtimeMode` values:
- `ON_DEVICE` (default): no guest, no Pi, no terminal. Chat goes straight through
  `LiteRtEngineManager`/`RemoteAiClient`. Tools available: `web_search`, RAG,
  `generate_image`, HTTP MCP. Keep the existing one-round local tool handling.
- `KAZETERM_PI` (opt-in): install KazeTerm rootfs + Node 22.19+ + Pi on demand.
  Pi runs in the guest, calls RaikaAI's local `/v1` via `baseUrl`, and owns the
  agent loop; file/shell/daemon tools become available.
- `LEGACY_PROOT` (transition only, then deleted): current `ContainerManager`.

Implementation rules:
- Introduce a `ToolRuntime`/`AgentRuntime` interface with `DirectRuntime` (no
  container) and `ContainerRuntime` (KazeTerm+Pi) implementations. The mode
  selects which loop and which tool set the UI exposes.
- Keep `EnvironmentScreen` and container slash commands (`/run`, `/env`,
  `/daemons`, `/killdaemon`) hidden when `runtimeMode = ON_DEVICE`; keep
  `enableWebSearch`, MCP, RAG and server tabs visible in both modes.
- Do not bundle the KazeTerm rootfs and Node in the base APK. Install them on
  first enable, the way RaikaAI already downloads the Alpine rootfs, so
  `ON_DEVICE` users keep the small APK.
- In both modes Pi is optional: the Ktor `/v1` server stays the single LLM
  backend, so switching modes never changes model loading or sampling.
- Tests: `ON_DEVICE` is pure JVM/unit-testable; `KAZETERM_PI` needs a real
  arm64 device. Keep both in CI where practical.

## Phasing
1. Golden tests: Pi RPC records + RaikaAI tool semantics (web search, approval,
   compaction) + DSH Go rendering checklist from `PORT_DSH_GO.md`.
2. Boot KazeTerm guest with Node 22.19+ and Pi; verify `pi --mode rpc` JSONL.
3. Node adapter + Kotlin client; no engine replacement yet.
4. Move agent loop from `ChatViewModel` to Pi; keep RaikaAI UI/LLM intact.
5. Port web search, MCP, skills, subagent, permission gate as Pi extensions.
6. Replace PRoot with KazeTerm `Machine`; keep the old container behind a flag
   until device tests pass.

## Open questions
- Where to run Node: bundled in rootfs (offline, larger APK) vs installed on
  first boot (network, smaller APK).
- Whether to keep RaikaAI MCP client in parallel with `pi-mcp` (name collisions
  `mcp__*` and duplicate server connections).
- minSdk: RaikaAI is 26; KazeTerm's API floor must be checked before embedding.
- AGPL (KazeTerm) + GPLv3 (RaikaAI) + MIT (Pi) combined licensing at release.
