# Embedded OpenAI-Compatible API

When the local server is running (sidebar → Local API → toggle), RaikaAI hosts an
OpenAI-compatible HTTP endpoint on your Wi-Fi network for VS Code Continue,
OpenWebUI, SillyTavern, and other third-party apps. Base URL:
`http://<phone-lan-ip>:<port>/v1`.

## Endpoints

| Method & path | Description |
|---|---|
| `POST /v1/chat/completions` | Chat completions; `stream: true` → SSE, `false` → single JSON |
| `POST /v1/completions` | Raw text completion (IDE extensions) |
| `GET /v1/models`, `GET /v1/models/{model}` | Active model metadata |
| `GET /health` | Health check (`ok`) |

## Auth & browser safety

- Optional bearer token (`API Key` field). When set, `Authorization: Bearer <token>`
  is required (constant-time comparison).
- Browser cross-origin requests (`Origin` header) are rejected with `403`
  unless an API token is configured — any website could otherwise drive your
  phone's AI. Non-browser clients (curl, Python, desktop apps) are unaffected.
- `Access-Control-Allow-Origin: *` is sent so configured web apps can connect.

## Example

```bash
curl -N http://192.168.1.10:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{"model": "raikaai",
       "messages": [{"role": "user", "content": "Hello!"}]}'
```
