<div align="center">
  <img src="docs/assets/app_icon.png" width="100" alt="RaikaAI Logo" />
  <h1>RaikaAI</h1>

  <p>
    <a href="LICENSE"><img src="https://img.shields.io/badge/Lisensi-GPLv3-blue.svg?style=for-the-badge&logo=gnu&logoColor=white" alt="Lisensi: GPLv3"></a>
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
      <td align="center" width="25%"><b>Layar&nbsp;Obrolan</b></td>
      <td align="center" width="25%"><b>Hub&nbsp;Model</b></td>
      <td align="center" width="25%"><b>Karakter</b></td>
      <td align="center" width="25%"><b>Riwayat&nbsp;Chat</b></td>
    </tr>
    <tr>
      <td align="center" valign="top"><img src="docs/screenshots/chat.png" width="220" height="489" alt="Layar Obrolan"></td>
      <td align="center" valign="top"><img src="docs/screenshots/models.png" width="220" height="489" alt="Hub Model"></td>
      <td align="center" valign="top"><img src="docs/screenshots/characters.png" width="220" height="489" alt="Karakter"></td>
      <td align="center" valign="top"><img src="docs/screenshots/history.png" width="220" height="489" alt="Riwayat Chat"></td>
    </tr>
  </table>
</div>

---

## Fitur

- **Obrolan on-device**: respon streaming token per token, bisa dihentikan kapan saja, pengaturan temperature / Top-K / Top-P, indikator pemakaian konteks, blok kode markdown yang dapat dilipat (default 10 baris dengan toggle expand), text-to-speech, dan riwayat chat tersimpan.
- **Linux Environment (Alpine PRoot, Tanpa Root)**: sistem Linux userland mandiri di dalam HP dengan direktori `/workspace` permanen. Dilengkapi virtual keypad ala Termux (`ESC`, `TAB`, `CTRL`, `ALT`, `SHIFT`, panah), pelengkap TAB otomatis, dan riwayat perintah.
- **Terminal Multi-Tab Gaya Chrome**: jalankan beberapa sesi terminal sekaligus (`Term 1`, `Term 2`, tombol `+`) tanpa saling mengunci (*non-blocking*). Server (Vite/Node) bisa berjalan di tab 1 sambil mengetik perintah lain di tab 2, lengkap dengan live streaming output log, tombol stop cepat (`✕`), dan persistensi state saat berpindah menu.
- **Autonomous Coding Agent Loop**: agen pemrograman mandiri ala OpenCode (hingga 30 ronde) dengan tool manipulasi file lokal (`read_file`, `write_file`, `edit_file`), kelanjutan otomatis saat limit tercapai, eksekusi server latar belakang (`&`), serta kontrol izin langsung dari chat (`/permission`).
- **Peringkas Konteks Gaya OpenCode (`/compact`)**: merangkum percakapan panjang menjadi briefing terstruktur tanpa kehilangan konteks kerja penting sekaligus menghemat token context window.
- **Server kompatibel OpenAI**: `POST /v1/chat/completions` (streaming SSE atau JSON tunggal), `POST /v1/completions`, `GET /v1/models`, `GET /health`. Autentikasi bearer-token opsional dan dukungan CORS.
- **Manajemen model**: katalog bawaan berisi model yang sudah diuji; cari dan unduh bobot `.litertlm` dari Hugging Face (repo gated didukung via access token) dengan jeda dan lanjut; atau impor file model dari penyimpanan HP (SAF).
- **Karakter**: buat persona kustom berisi avatar, system prompt, sapaan, dan contoh dialog. Bisa ganti persona di tengah percakapan. Impor/ekspor sebagai JSON.
- **Chat dokumen (RAG)**: lampirkan file PDF, TXT, MD, CSV, atau kode sumber. Jawaban mencantumkan kutipan yang dirujuk (`[Doc1]`, `[Doc2]`).
- **Klien Model Context Protocol (MCP)**: hubungkan server MCP untuk menambah tool bagi asisten. Setiap eksekusi tool selalu meminta persetujuan pengguna dulu.
- **Vision & audio**: kirim gambar ke model yang mendukung vision, lampirkan klip audio, dan input suara via speech-to-text.
- **Mode perbandingan**: jalankan satu prompt di dua model sekaligus (lokal/lokal atau lokal/remote).
- **Benchmark**: ukur time-to-first-token (TTFT), token per detik, dan perbandingan GPU vs CPU di HP sendiri.
- **Pencarian web & ringkasan tautan**: pencarian web opsional dengan sumber kutipan, dan ringkasan artikel dalam sekali ketuk.
- **Tampilan**: tema Material 3 dengan pilihan warna aksen dan mode hitam untuk layar OLED.

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

Proyek ini dilisensikan di bawah [GNU General Public License v3.0 (GPLv3)](LICENSE).
