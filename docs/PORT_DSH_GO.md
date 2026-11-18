# Porting DSH Go behavior to RaikaAI

Status: research notes, not yet an implementation decision.
Source of truth for DSH Go: `/root/Documents/dsh-Go` (read-only study, 2026-11-18).

## Premise correction

DSH Go does **not** contain the DeepSeek Harness engine source. It vendors the
upstream `@deepseek-ai/dsh-*` npm tree (v0.2.2-rc.1), applies pinned patch diffs
from `runtime/patches/series` + `pins.json`, and runs `dsh web` in PRoot on
Android. The agent loop, tool schemas, permission evaluation and compaction
live in the upstream npm packages, not in this repository. Therefore:

- We cannot port "the harness" from DSH Go source. We can port its **wire
  contract, event vocabulary, orchestration state machine, and rendering
  behavior**, then implement the engine side ourselves in Kotlin/Rust.
- If we instead want the real upstream engine, the only faithful route is to
  reuse it through the same bridge contract, with its licensing/attribution.

## What is actually portable

| Layer | Portable? | DSH Go evidence |
|---|---|---|
| `/api` RPC + `/api/remote.mux` wire format | Yes | `packages/dsh_client/lib/src/{carrier,rpc,mux}.dart` |
| `$events` generation/ready/resync, durable seq, `watch()` reducer | Yes | `docs/architecture.md:329-342`, `src/transcript.dart` |
| Session/prompt/approval state machine, `commands/execute`, `/permission`, `/compact` | Yes | `src/session_api.dart:328-864` |
| Bridge `/dsh-go/v1/*` routes, guard codes, nested `{error:{code,message}}` | Yes | `runtime/bridge/dsh-go-bridge/lib/index.js:416-457`, `docs/design/bridge-contract.md` |
| Native-plugin protocol: 5 schemas, `hello/welcome`, capability intersection, effect allowlist, card key `dsh-go/view` (8 KiB) | Yes | `sdk/plugin-kit/schema/*.schema.json`, `docs/design/native-plugins-protocol.md` |
| Phone tool catalog + MCP JSON-RPC names | Yes (policy), No (mechanism) | `PhoneToolCatalog.kt:11-26`, `McpServer.kt:36-44` |
| Markdown pure transforms: math/table normalization, streaming tail repair, block split, head/tail clipping | Yes | `app/lib/ui/chat/render/{math_syntax,table_syntax,stream_repair,markdown_split,long_text}.dart` |
| `gpt_markdown` AST + cache, `re_highlight` grammars, `val_latex_flutter`, Mermaid WebView | No (rewrite or accept reduced fidelity) | `docs/design/rendering.md:20-33` |
| PRoot/direct/tawc launch, jniLibs, Shizuku/ADB, a11y executors, APK self-update | No (Android-specific) | `DshProcess.kt`, `runtime/boot`, `runtime/direct`, `phone/` |

## Rendering behavior to reproduce in Compose

Pipeline: `normalizeMath → boldTableHeaders → (streaming) repair + cursor → markdown AST`.

- Math: `$…$` → `\(…\)`, `$$…$$` → `\[…\]`; prices like `$5 and $10` and
  `5$ and 10$` must stay text; unclosed `$$` shows muted source until closed.
- Streaming repair: close dangling `**`, `*`, `~~`, backtick; half link → `(#)`;
  hold half images/HTML/marker-only lines; tables appear after the delimiter row.
- Cursor: sentinel `\uE000`, placed after the last line, on a new line after a
  closed fence / `\]`, inside the last table cell otherwise.
- Incremental: coalesce UI updates at ~32 ms; split replies into ~2000-char
  blocks; only the last block re-renders; finished code blocks never rebuild.
- Code well: language header, copy → check for 1.2 s, wrap toggle, diff colors,
  optional line numbers; highlighting disabled above 20000 chars; 56 grammars
  + alias map; exact light/dark token colors from `highlight_theme.dart`.
- Tool output: head 60 lines / tail 140 lines / max 16000 chars, "N lines
  hidden" + "View all"; tool row is 32 dp, collapsed unless it has a native card.
- Approval card: Decline / Allow once / Allow for chat, command well max 148 dp.
- Lists: text column at 1.375 em, right-aligned numbers; `<details>` closed by
  default; GitHub alert cards with five localized titles; tables max column 260.
- There is **no** "10 lines + expand" code-block rule in DSH Go; that belongs to
  RaikaAI's current README and should be kept only if we want it.

## Harness orchestration to reproduce in Kotlin/Rust

1. Transport: `POST /api/<endpoint>` with `{type:"client-request",rpcId,method,payload:{args}}`;
   response `{type:"server-response",rpcId,result:{ok,value|error}}`. Business
   errors are HTTP 200 with `ok:false`; only transport refusals use 4xx.
2. Mux: WebSocket `/api/remote.mux`; first frame `ready {clientId,host.home}`;
   frames `open/item/end/cancel` up and `item/end/error` down; no cross-socket
   replay; pending approvals/questions redelivered by `eventId`; backoff 0.5–10 s.
3. Events: `tool/call`, `tool/result`, `assistant/message`, `assistant-stream`,
   `turn/start|end`, `approval/request`, `user-questions/request`. Keep a
   reducer from events to chat state, with revision-jump resync.
4. Approvals: engine is the gate; answers are `allowed-once | rejected | next`
   via `$events/result`. Auto-approve is a client answering policy only.
   Phone/tool dangerous actions additionally need a native `SafetyGate`-like
   policy that is not configurable from the model.
5. Session deletion: capability handshake first (`supported:true`), validate
   before dispose, ordered cascade, codes `409/404/503/500`.
6. Plugin protocol: 5 JSON schemas; `hello/welcome`; declared ∩ negotiated
   capabilities; effect allowlist with 3 s tap-bound authority; token bucket
   8/4-per-second/4-concurrent; 5 violations → degraded.

## Suggested Kotlin/Rust module split for RaikaAI

- `dshproto` (Rust, serde) — schemas, envelopes, event types, error taxonomy,
  plugin view tree and effects. Shared by client and local service.
- `dshclient` (Kotlin) — carrier/RPC/mux, `watch()` reducer, session prompts,
  approval answering, reconnect/resync. Mirrors `packages/dsh_client`.
- `agent` (Rust or Kotlin) — portable agent loop state machine: prompt → turn →
  tool call → approval → tool result → continuation; compaction and permission
  presets as commands. This is a **reimplementation**, not a port.
- `tools` (Kotlin) — filesystem/workspace/shell adapters for RaikaAI's PRoot or
  KazeTerm runtime; plugin effect executors; native safety policy.
- `markdown` (Kotlin/Compose) — pure transforms + Compose renderers; accept
  reduced fidelity for LaTeX and Mermaid in v1.

## Phasing

1. Write golden tests from the DSH Go contracts (RPC envelope, mux frames,
   event reducer, plugin schemas, streaming repair, long-text clipping).
2. Implement `dshproto` + `dshclient`, point them at a stub bridge; replay
   captured DSH Go event sequences as fixtures (no need to run its engine).
3. Implement `markdown` with the checklist above; visual parity on a few
   representative replies (code, table, math, tool card, approval).
4. Implement `agent` + `tools`; start with RaikaAI's existing agent loop and
   only adopt DSH vocabulary where it adds a missing capability.
5. Only then consider replacing RaikaAI's runtime with KazeTerm's engine.

## Licensing

DSH Go: AGPL-3.0; `sdk/plugin-kit` and `examples` MIT; name, logo, Deepy and
鲸娘 are excluded from the open-source grant (`ASSETS.md`). Its README asks
authors of derivative releases to contact the author first and credit the main
repository. Keep notices and do not reuse the brand/mascots.
