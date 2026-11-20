<div align="center">
  <img src="docs/assets/app_icon.png" width="100" alt="RaikaAI Logo" />
  <h1>RaikaAI</h1>

  <p>
    <a href="LICENSE"><img src="https://img.shields.io/badge/License-AGPLv3-blue.svg?style=for-the-badge&logo=gnu&logoColor=white" alt="License: AGPLv3"></a>
    <a href="https://github.com/kouzen-neo/raikaai/releases"><img src="https://img.shields.io/github/v/release/kouzen-neo/raikaai?style=for-the-badge&color=teal" alt="Latest Release"></a>
    <a href="https://developer.android.com"><img src="https://img.shields.io/badge/Android-API%2026%2B-3DDC84.svg?style=for-the-badge&logo=android&logoColor=white" alt="Android SDK"></a>
    <a href="https://ai.google.dev/edge/litert"><img src="https://img.shields.io/badge/Google_LiteRT-GPU_Accelerated-4285F4.svg?style=for-the-badge&logo=google&logoColor=white" alt="LiteRT"></a>
    <a href="https://ktor.io/"><img src="https://img.shields.io/badge/Ktor-CIO_Server-7F52FF.svg?style=for-the-badge&logo=ktor&logoColor=white" alt="Ktor"></a>
  </p>

  <p>
    <a href="README.md"><img src="https://img.shields.io/badge/EN-0078D4.svg?style=for-the-badge" alt="English"></a>
    <a href="README.id.md"><img src="https://img.shields.io/badge/ID-6e7681.svg?style=for-the-badge" alt="Bahasa Indonesia"></a>
  </p>
</div>

RaikaAI is an open-source Android app for chatting with large language models entirely on-device, powered by **Google AI Edge LiteRT-LM**. Supported models include Gemma, SmolLM2, Llama 3.2, and Qwen. Inference runs on the phone GPU (Qualcomm Adreno / ARM Mali via OpenCL) with CPU fallback.

It also includes a built-in **OpenAI-compatible HTTP server (`/v1`)** based on **Ktor CIO**, so tools on the same Wi-Fi network — VS Code extensions (Continue), OpenWebUI, SillyTavern, or plain scripts — can send requests to the phone.

<div align="center">
  <table>
    <tr>
      <td align="center" width="20%"><b>Chat&nbsp;Screen</b></td>
      <td align="center" width="20%"><b>Models&nbsp;Hub</b></td>
      <td align="center" width="20%"><b>Characters</b></td>
      <td align="center" width="20%"><b>Skills&nbsp;&amp;&nbsp;Tools</b></td>
      <td align="center" width="20%"><b>Chat&nbsp;History</b></td>
    </tr>
    <tr>
      <td align="center" valign="top"><img src="docs/screenshots/chat.png" width="220" height="489" alt="Chat Screen"></td>
      <td align="center" valign="top"><img src="docs/screenshots/models.png" width="220" height="489" alt="Models Hub"></td>
      <td align="center" valign="top"><img src="docs/screenshots/characters.png" width="220" height="489" alt="Characters"></td>
      <td align="center" valign="top"><img src="docs/screenshots/skills.png" width="220" height="489" alt="Agent"></td>
      <td align="center" valign="top"><img src="docs/screenshots/history.png" width="220" height="489" alt="Chat History"></td>
    </tr>
  </table>
</div>

---

## Features

- **On-device chat**: token-by-token streaming, stop anytime, adjustable sampling, context usage indicator, collapsible code blocks, text-to-speech, branching edits, and saved history. Replies keep generating when you switch screens or chats.
- **Raika agent**: the default persona is Raika AI, configured like a modern agent harness: `SOUL.md` (identity), `AGENTS.md` (standing instructions) and `MEMORY.md` (facts saved with the `remember` tool), editable in **Agent → Memory**. On-device models get a compact version so the small context is not flooded.
- **Agent hub**: Plugins, Skills, MCP and Memory in one place. MCP works with local and remote models; servers can be a URL or a command that runs inside Alpine (e.g. `npx -y @upstash/context7-mcp`), and Raika can install them itself after you approve. Every MCP call asks first.
- **Linux environment (Alpine PRoot, no root)**: Alpine ships inside the APK, so it installs offline. Persistent `/workspace`, optional phone storage at `/sdcard`, Raika's home at `/root/.raika` (its config files plus every chat as Markdown in `chats/`). Permission levels: Ask, Auto, Full access.
- **Real terminal**: a Rust PTY helper and a full VT emulator run full-screen apps (opencode, vim, htop) with touch-as-mouse, pinch zoom, copy, bracketed paste and multiple tabs.
- **Coding agent loop**: multi-round tool use (`read_file`, `write_file`, `edit_file`, shell) with servers detached automatically and a live "working" indicator. Tool cards show Quiet, Normal or Full detail (Settings).
- **Chat controls**: queue messages while a reply is running (or Send now), `/btw` side questions that stay out of the chat, `/compact` context compaction, `/permission`, an "Add to chat" sheet (camera, photos, files, knowledge, web search).
- **Remote providers**: presets for OpenAI, Anthropic, Gemini, OpenRouter, Groq, DeepSeek, Mistral, xAI and Ollama, plus any OpenAI-compatible endpoint. The model list follows the selected provider; the context window is detected from the provider or known model families.
- **OpenAI-compatible server**: `POST /v1/chat/completions` (SSE or JSON), `POST /v1/completions`, `GET /v1/models`, `GET /health`, optional bearer-token auth and CORS.
- **Model manager**: tested catalog; download `.litertlm` weights from Hugging Face (gated repos via token) with pause/resume, or import from storage.
- **Characters**: one hub for the online roleplay catalog (Discover), your own characters (Mine) and Create. Publish to the public Community category after an explicit warning. Roleplay chats appear in the sidebar's Recent list.
- **Document chat (RAG)**: attach PDF, TXT, MD, CSV or code; answers cite excerpts (`[Doc1]`).
- **Vision & audio**: images for vision models, audio clips, and live speech-to-text dictation.
- **Compare, benchmark, web search**: two models side by side, TTFT and tokens/s on your device, web lookup with cited sources.
- **Appearance**: Material 3 with accent colors and an OLED black mode.

---

## Download & Installation

Get the latest signed APK from **[GitHub Releases](https://github.com/kouzen-neo/raikaai/releases/latest)**.

| File | Requirements |
|---|---|
| `RaikaAI-*-arm64-v8a.apk` | 64-bit phone (`arm64-v8a`), Android 8.0+ (API 26). 32-bit devices (`armeabi-v7a`) are not supported because LiteRT-LM provides no 32-bit native library. |

---

## Architecture & Inference Flow

```text
[ User Prompt / Image / Audio / Documents ]
                     │
                     ▼
       [ Context Assembler & RAG ]
                     │
                     ▼
  [ LiteRT-LM Engine (OpenCL GPU / CPU) ]  ◄──►  [ Embedded Ktor Server (/v1) ]
                     │                                         ▲
                     ▼                                         │
     [ Streaming Response & Thinking ]             [ VS Code / OpenWebUI / LAN ]
                     │
                     ▼
    [ Branching Tree Chat / Saved Chats ]
```

- For full architecture breakdown and data flows, see **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)**.
- For embedded server endpoints, authentication, and examples, see **[docs/API.md](docs/API.md)**.
- For build steps, unit tests, and signing setup, see **[docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)**.

---

## Quick Build Guide

Requirements: JDK 17+, Android SDK (compileSdk 37, targetSdk 36), and an ARM64 Android device (API 26+).

```bash
# Run Unit Tests
./gradlew testDebugUnitTest

# Build Debug APK
./gradlew assembleDebug

# Build Optimized Release APK (arm64-v8a)
./gradlew assembleRelease -PabiFilter=arm64-v8a
```

*Output: `app/build/outputs/apk/release/app-arm64-v8a-release.apk`*

---

## Privacy & Security

- **Local inference**: with on-device models, prompts, attachments, and responses stay on the phone. No tracking or third-party telemetry.
- **Encrypted secret storage**: remote API keys and Hugging Face tokens are stored encrypted via Android Keystore (AES-GCM).
- **LAN server protection**: the embedded server rejects cross-origin browser requests (`403 Forbidden`) unless an API bearer token is configured.

---

## Open-Source Acknowledgments

RaikaAI is built with these open-source projects:

- [Google AI Edge LiteRT](https://ai.google.dev/edge/litert) for on-device LLM inference.
- [PRoot](https://proot-me.github.io/) for user-space chroot and virtualization without root.
- [Alpine Linux](https://alpinelinux.org/) for the lightweight on-device sandbox environment.
- [Termux](https://termux.dev/) for terminal virtual keypad patterns and Android userland tooling inspiration.
- [Ktor](https://ktor.io/) for the embedded HTTP/CIO server.
- [Jetpack Compose](https://developer.android.com/compose) for the Android UI.
- [MaterialKolor](https://github.com/jordond/MaterialKolor) for Material 3 color theming.
- [OkHttp](https://square.github.io/okhttp/) & [Gson](https://github.com/google/gson) for downloads and serialization.

---

## License

This project is licensed under the [GNU Affero General Public License v3.0 (AGPLv3)](LICENSE).
