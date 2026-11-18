# Pi adapter (KazeTerm guest)

A self-contained Node.js 22+ service that runs inside the KazeTerm guest. It
has two jobs:

1. **Pi RPC wrapper** — spawn `pi --mode rpc --no-session`, correlate commands
   by `id`, stream Pi events, translate them into a small host event vocabulary,
   and relay abort.
2. **OpenAI-compatible tool-calling shim** — expose `/v1/chat/completions` as
   Pi's `baseUrl`, forwarding to RaikaAI's Ktor `/v1`, which has no `tools`
   field and no `tool_calls` in responses.

Only Node built-in modules are used (no npm dependencies).

## Data flow

```
Kotlin host --HTTP--> this adapter --JSONL child stdio--> Pi agent
                                    <--events------------
Pi (baseUrl = this adapter) --HTTP /v1/chat/completions--> tool shim
    --HTTP (no tools)--> RaikaAI Ktor /v1 --> LiteRT
```

The tool schemas are injected into the system prompt; the assistant's
`{"tool_calls":[...]}` answer (bare or in a fenced ```json block) is parsed
back out of the text, stripped from the visible content, and returned as a
proper OpenAI `message.tool_calls` array with generated `call_...` ids and
`finish_reason: "tool_calls"`. Both JSON and SSE streaming responses work.

## Environment variables

| Variable | Default | Meaning |
|---|---|---|
| `PI_BIN` | `pi` | Pi executable. May include leading args or be a full path; the fixed `--mode rpc --no-session` args are appended. |
| `UPSTREAM_BASE_URL` | `http://127.0.0.1:8080/v1` | Base URL of RaikaAI's OpenAI-compatible `/v1` (the tool-less upstream). |
| `ADAPTER_TOKEN` | empty | When set, every endpoint except `/health` requires `Authorization: Bearer <token>`. Empty disables auth for local development. |
| `UPSTREAM_TOKEN` | empty | Bearer token for RaikaAI's `/v1`. The caller's adapter token is never relayed upstream. |
| `PORT` | `8787` | HTTP listen port. |
| `HOST` | `127.0.0.1` | HTTP listen address. |

## Endpoints

| Method | Path | Description |
|---|---|---|
| `GET` | `/health` | `{ "ok": true }` |
| `GET` | `/events` | JSON array snapshot of translated events (`assistant-stream`, `tool/call`, `approval/request`, `turn/end`, `settled`). |
| `POST` | `/v1/chat/completions` | OpenAI-compatible chat endpoint with `tools` and `stream` support. Proxies to `UPSTREAM_BASE_URL` through the tool shim. |
| `POST` | `/v1/abort` | Sends Pi's RPC `abort`; responds `{ "ok": true, "aborted": true }`. |
| `POST` | `/v1/agent/prompt` | Optional bridge: send `{ "message": "..." }` to Pi and wait for `agent_settled`. Deltas appear on `/events`. |

Pi RPC framing splits stdout on LF only and strips an optional CR. It never
uses `node:readline`, so U+2028/U+2029 inside JSON strings stay intact. stderr
is logged as diagnostics and is never parsed as protocol data. The command
listener is installed before any prompt is sent, and prompts wait on
`agent_settled` (not `agent_end`) for completion.

## Run

```sh
UPSTREAM_BASE_URL=http://127.0.0.1:8080/v1 PI_BIN=pi node tools/pi-adapter/src/index.js
```

## Tests

No real Pi installation or upstream server is needed. Tests use a fake Pi
child process (`test/fake-pi.js`) and a fake upstream server
(`test/fake-upstream.js`).

```sh
node --test tools/pi-adapter/test/*.test.js
```

The suite covers LF framing with an embedded U+2028, tool-schema injection and
`tool_calls` parse round-trips (JSON and SSE), event translation, and abort.
