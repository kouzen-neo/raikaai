<div align="center">
  <img src="docs/assets/app_icon.png" width="100" alt="RaikaAI Logo" />
  <h1>RaikaAI</h1>

  <p>
    <a href="LICENSE"><img src="https://img.shields.io/badge/Lisensi-AGPLv3-blue.svg?style=for-the-badge&logo=gnu&logoColor=white" alt="Lisensi: AGPLv3"></a>
    <a href="https://github.com/kouzen-neo/raikaai/releases"><img src="https://img.shields.io/github/v/release/kouzen-neo/raikaai?style=for-the-badge&color=teal" alt="Rilis Terbaru"></a>
    <a href="https://developer.android.com"><img src="https://img.shields.io/badge/Android-API%2026%2B-3DDC84.svg?style=for-the-badge&logo=android&logoColor=white" alt="Android SDK"></a>
    <a href="https://ai.google.dev/edge/litert"><img src="https://img.shields.io/badge/Google_LiteRT-Akselerasi_GPU-4285F4.svg?style=for-the-badge&logo=google&logoColor=white" alt="LiteRT"></a>
    <a href="https://ktor.io/"><img src="https://img.shields.io/badge/Ktor-Server_CIO-7F52FF.svg?style=for-the-badge&logo=ktor&logoColor=white" alt="Ktor"></a>
  </p>

  <p>
    <a href="README.md"><img src="https://img.shields.io/badge/EN-6e7681.svg?style=for-the-badge" alt="English"></a>
    <a href="README.id.md"><img src="https://img.shields.io/badge/ID-0078D4.svg?style=for-the-badge" alt="Bahasa Indonesia"></a>
  </p>
</div>

RaikaAI adalah aplikasi Android open-source untuk mengobrol dengan model bahasa besar yang berjalan sepenuhnya di perangkat, ditenagai oleh **Google AI Edge LiteRT-LM**. Model yang didukung antara lain Gemma, SmolLM2, Llama 3.2, dan Qwen. Inferensi berjalan di GPU HP (Qualcomm Adreno / ARM Mali via OpenCL) dengan fallback CPU.

Aplikasi ini juga menyertakan **server HTTP kompatibel OpenAI (`/v1`)** berbasis **Ktor CIO**, sehingga aplikasi di jaringan Wi-Fi yang sama — ekstensi VS Code (Continue), OpenWebUI, SillyTavern, atau skrip biasa — bisa mengirim request ke HP.

<div align="center">
  <table>
    <tr>
      <td align="center" width="20%"><b>Layar&nbsp;Obrolan</b></td>
      <td align="center" width="20%"><b>Hub&nbsp;Model</b></td>
      <td align="center" width="20%"><b>Karakter</b></td>
      <td align="center" width="20%"><b>Skills&nbsp;&amp;&nbsp;Tools</b></td>
      <td align="center" width="20%"><b>Riwayat&nbsp;Chat</b></td>
    </tr>
    <tr>
      <td align="center" valign="top"><img src="docs/screenshots/chat.png" width="220" height="489" alt="Layar Obrolan"></td>
      <td align="center" valign="top"><img src="docs/screenshots/models.png" width="220" height="489" alt="Hub Model"></td>
      <td align="center" valign="top"><img src="docs/screenshots/characters.png" width="220" height="489" alt="Karakter"></td>
      <td align="center" valign="top"><img src="docs/screenshots/skills.png" width="220" height="489" alt="Skills & Tools"></td>
      <td align="center" valign="top"><img src="docs/screenshots/history.png" width="220" height="489" alt="Riwayat Chat"></td>
    </tr>
  </table>
</div>

---

## Fitur

- **Chat on-device**: streaming token per token, bisa dihentikan, pengaturan sampling, indikator konteks, blok kode yang bisa dilipat, text-to-speech, edit bercabang, dan riwayat tersimpan. Balasan tetap berjalan saat pindah layar atau pindah chat.
- **Agent Raika**: persona default adalah Raika AI, dikonfigurasi seperti harness agent modern: `SOUL.md` (identitas), `AGENTS.md` (instruksi tetap), dan `MEMORY.md` (fakta yang disimpan lewat tool `remember`), bisa diedit di **Agent → Memory**. Model on-device mendapat versi ringkas agar konteksnya tidak penuh.
- **Hub Agent**: Plugins, Skills, MCP, dan Memory di satu tempat. MCP jalan untuk model lokal dan remote; server bisa berupa URL atau perintah yang berjalan di Alpine (mis. `npx -y @upstash/context7-mcp`), dan Raika bisa memasangnya sendiri setelah kamu setujui. Setiap panggilan MCP minta izin dulu.
- **Linux environment (Alpine PRoot, tanpa root)**: Alpine dibundel di APK sehingga terpasang offline. `/workspace` permanen, storage HP opsional di `/sdcard`, folder Raika di `/root/.raika` (file konfigurasi plus semua chat sebagai Markdown di `chats/`). Tingkat izin: Ask, Auto, Full access.
- **Terminal sungguhan**: helper PTY berbasis Rust dan emulator VT lengkap untuk aplikasi layar penuh (opencode, vim, htop), sentuhan sebagai mouse, pinch zoom, salin, paste, dan multi-tab.
- **Loop agent coding**: pemakaian tool beberapa ronde (`read_file`, `write_file`, `edit_file`, shell), server otomatis berjalan di latar, dan indikator "sedang bekerja". Detail kartu tool bisa Quiet, Normal, atau Full (Pengaturan).
- **Kontrol chat**: antrekan pesan saat balasan berjalan (atau Send now), `/btw` untuk pertanyaan sampingan yang tidak masuk chat, `/compact`, `/permission`, dan sheet "Add to chat" (kamera, foto, file, knowledge, pencarian web).
- **Provider remote**: preset OpenAI, Anthropic, Gemini, OpenRouter, Groq, DeepSeek, Mistral, xAI, dan Ollama, plus endpoint OpenAI-compatible apa pun. Daftar model mengikuti provider yang dipilih; context window dideteksi dari provider atau keluarga model.
- **Server kompatibel OpenAI**: `POST /v1/chat/completions` (SSE atau JSON), `POST /v1/completions`, `GET /v1/models`, `GET /health`, autentikasi bearer opsional dan CORS.
- **Manajemen model**: katalog teruji; unduh bobot `.litertlm` dari Hugging Face (repo gated via token) dengan jeda/lanjut, atau impor dari penyimpanan.
- **Characters**: satu hub untuk katalog roleplay online (Discover), karakter milikmu (Mine), dan Create. Publikasi ke kategori publik Community setelah peringatan yang jelas. Chat roleplay muncul di daftar Recent di sidebar.
- **Chat dokumen (RAG)**: lampirkan PDF, TXT, MD, CSV, atau kode; jawaban mengutip potongannya (`[Doc1]`).
- **Vision & audio**: gambar untuk model vision, klip audio, dan dikte suara langsung.
- **Bandingkan, benchmark, pencarian web**: dua model berdampingan, TTFT dan token/detik di HP sendiri, pencarian web dengan sumber.
- **Tampilan**: Material 3 dengan warna aksen dan mode hitam OLED.

---

## Download & Panduan Instalasi

Unduh APK rilis terbaru dari **[GitHub Releases](https://github.com/kouzen-neo/raikaai/releases/latest)**.

| Berkas | Syarat |
|---|---|
| `RaikaAI-*-arm64-v8a.apk` | HP 64-bit (`arm64-v8a`), Android 8.0+ (API 26). Perangkat 32-bit (`armeabi-v7a`) tidak didukung karena LiteRT-LM tidak menyediakan pustaka native 32-bit. |

---

## Alur Inferensi & Arsitektur

```text
[ Input Pengguna / Gambar / Audio / Dokumen ]
                     │
                     ▼
       [ Context Assembler & RAG ]
                     │
                     ▼
  [ LiteRT-LM Engine (GPU OpenCL / CPU) ]  ◄──►  [ Embedded Ktor Server (/v1) ]
                     │                                         ▲
                     ▼                                         │
     [ Streaming Respon & Thinking ]                [ VS Code / OpenWebUI / LAN ]
                     │
                     ▼
    [ Branching Tree Chat / Riwayat Obrolan ]
```

- Untuk detail arsitektur dan modul kode, baca **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)**.
- Untuk spesifikasi endpoint server, autentikasi, dan contoh request, baca **[docs/API.md](docs/API.md)**.
- Untuk panduan kompilasi, pengujian unit, dan signing key, baca **[docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)**.

---

## Panduan Build Mandiri

Kebutuhan: JDK 17+, Android SDK (compileSdk 37, targetSdk 36), dan perangkat fisik Android ARM64 (API 26+).

```bash
# Jalankan Unit Tests
./gradlew testDebugUnitTest

# Kompilasi Debug APK
./gradlew assembleDebug

# Kompilasi Release APK (arm64-v8a)
./gradlew assembleRelease -PabiFilter=arm64-v8a
```

*Output berkas: `app/build/outputs/apk/release/app-arm64-v8a-release.apk`*

---

## Privasi & Keamanan

- **Inferensi lokal**: dengan model on-device, prompt, lampiran, dan respon tetap di HP. Tanpa pelacakan atau telemetri pihak ketiga.
- **Kredensial terenkripsi**: API key remote dan token Hugging Face disimpan terenkripsi via Android Keystore (AES-GCM).
- **Proteksi server LAN**: server menolak request browser lintas-origin (`403 Forbidden`) kecuali bearer token API sudah dikonfigurasi.

---

## Ucapan Terima Kasih (Open-Source)

RaikaAI dibangun dengan proyek open-source berikut:

- [Google AI Edge LiteRT](https://ai.google.dev/edge/litert) untuk inferensi LLM on-device.
- [PRoot](https://proot-me.github.io/) untuk virtualisasi chroot tingkat user-space tanpa root.
- [Alpine Linux](https://alpinelinux.org/) untuk distribusi rootfs sandbox ringan di perangkat.
- [Termux](https://termux.dev/) untuk inspirasi implementasi virtual keypad terminal dan userland tooling di Android.
- [Ktor](https://ktor.io/) untuk server HTTP/CIO bawaan.
- [Jetpack Compose](https://developer.android.com/compose) untuk UI Android.
- [MaterialKolor](https://github.com/jordond/MaterialKolor) untuk theming warna Material 3.
- [OkHttp](https://square.github.io/okhttp/) & [Gson](https://github.com/google/gson) untuk unduhan dan serialisasi.

---

## Lisensi

Proyek ini dilisensikan di bawah [GNU Affero General Public License v3.0 (AGPLv3)](LICENSE).
