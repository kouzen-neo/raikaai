<div align="center">
  <img src="docs/assets/app_icon.png" width="100" alt="RaikaAI Logo" />
  <h1>RaikaAI</h1>

  <p>
    <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPLv3-blue.svg?style=for-the-badge&logo=gnu&logoColor=white" alt="License: GPLv3"></a>
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
      <td align="center" width="25%"><b>Chat&nbsp;Screen</b></td>
      <td align="center" width="25%"><b>Models&nbsp;Hub</b></td>
      <td align="center" width="25%"><b>Characters</b></td>
      <td align="center" width="25%"><b>Chat&nbsp;History</b></td>
    </tr>
    <tr>
      <td align="center" valign="top"><img src="docs/screenshots/chat.png" width="220" height="489" alt="Chat Screen"></td>
      <td align="center" valign="top"><img src="docs/screenshots/models.png" width="220" height="489" alt="Models Hub"></td>
      <td align="center" valign="top"><img src="docs/screenshots/characters.png" width="220" height="489" alt="Characters"></td>
      <td align="center" valign="top"><img src="docs/screenshots/history.png" width="220" height="489" alt="Chat History"></td>
    </tr>
  </table>
</div>

---

## Features

- **On-device chat**: token-by-token streaming, stop generation anytime, adjustable temperature / Top-K / Top-P, context usage indicator, collapsible markdown code blocks (10 lines default with expand toggle), text-to-speech, and saved chat history.
- **Linux Environment (Alpine PRoot, No Root)**: embedded on-device Linux userland with a persistent `/workspace` folder. Includes a Termux-style virtual keypad (`ESC`, `TAB`, `CTRL`, `ALT`, `SHIFT`, arrows), TAB auto-completion, and command history.
- **Chrome-Style Multi-Tab Terminal**: run multiple concurrent terminal sessions (`Term 1`, `Term 2`, `+`) without blocking threads. Run servers (Vite/Node) in one tab while executing shell commands in another, with live streaming logs, instant stop button (`✕`), and ViewModel state persistence.
- **Autonomous Coding Agent Loop**: multi-round OpenCode-style coding agent (up to 30 rounds) with dedicated file tools (`read_file`, `write_file`, `edit_file`), length auto-continuation, background server execution (`&`), and in-chat permission management (`/permission`).
- **OpenCode-Style Context Compaction (`/compact`)**: summarizes multi-turn conversation into a structured briefing, freeing context window tokens without losing work state.
- **OpenAI-compatible server**: `POST /v1/chat/completions` (SSE streaming or single JSON), `POST /v1/completions`, `GET /v1/models`, `GET /health`. Optional bearer-token auth and CORS support.
- **Model manager**: built-in catalog of tested mobile models; search and download `.litertlm` weights from Hugging Face (gated repos supported via access token) with pause and resume; or import model files from device storage (SAF).
- **Characters**: create custom personas with avatar, system prompt, greeting, and example dialogues. Switch persona mid-conversation. Import/export as JSON.
- **Document chat (RAG)**: attach PDF, TXT, MD, CSV, or source code files. Answers cite the referenced excerpts (`[Doc1]`, `[Doc2]`).
- **Model Context Protocol (MCP) client**: connect MCP servers to expose additional tools to the assistant. Tool execution always asks for user approval first.
- **Vision & audio**: send images to vision-capable models, attach audio clips, and dictate input via speech-to-text.
- **Compare mode**: run one prompt on two models side by side (local/local or local/remote).
- **Benchmark**: measure time-to-first-token (TTFT), tokens per second, and GPU vs CPU performance on your own device.
- **Web search & link summary**: optional web lookup with cited sources, plus one-tap summarization of article links.
- **Appearance**: Material 3 theme with selectable accent colors and a black mode for OLED screens.

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

This project is licensed under the [GNU General Public License v3.0 (GPLv3)](LICENSE).
