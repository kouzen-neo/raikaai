> **Historical planning note** (Indonesian). The container has since shipped; see
> `docs/ARCHITECTURE.md` for the current design.

# Prompt Implementasi — Alpine PRoot Container untuk RaikaAI (Branch `feat/alpine-proot`)

> STATUS: EXECUTED 2026-10-16 — Fase 0–4 diimplementasikan di branch ini.
> Verifikasi: `./gradlew testDebugUnitTest` + `./gradlew assembleDebug` hijau.
> APK debug arm64 dikirim ke POCO F5 via KDE Connect untuk uji manual.
> Catatan: proot binary + rootfs Alpine BELUM dipublish (manifest berisi URL
> placeholder + sha kosong → install akan gagal dengan pesan jelas sampai
> artefak ditaruh di `container-proot-v1` Release). Terminal Fase 3 berupa
> shell-in-a-box (tanpa PTY): vim/htop belum didukung.

---

## 1. Konteks & Tujuan

Tambahkan Linux userland **Alpine (via PRoot, tanpa root)** ke RaikaAI sebagai:
1. Terminal manual ala Termux di menu khusus (`Environment`: tab `Terminal | Files`).
2. Sandbox eksekusi untuk AI (`execute_in_container` + `ensure_package`, ala OpenCode).
3. File bridge via `/workspace` (bind-mount ke `filesDir/workspace`).
4. Serve localhost (preview `http://127.0.0.1:PORT` di browser HP / WebView).

Bukan Docker asli. Kernel tetap Android. Tidak ada `systemd`, Docker-in-Docker, GUI X11.

## 2. Prinsip Arsitektur (jangan dilanggar)

```
[Chat / Environment UI]
      ↓
[ContainerManager.kt] — satu-satunya pintu ke container
  API: getStatus(), install(), run(cmd, timeout), ensurePackage(name),
       serve(dir, port), kill(), wipe()
      ↓
[proot binary (downloadable, per-ABI)] --ptrace--> [filesDir/alpine/*]
      ↓ bind-mount (-b)
[filesDir/workspace ↔ /workspace]   ← SATU-SATUNYA dir read-write untuk AI
```

- Semua download **tidak di-bundle di APK**. Unduh saat pertama kali (reuse pola `LocalModelDownloader` + `ModelDownloadService`: progress, resume, sha256).
- 2 artefak: (a) `proot` dari GitHub Release sendiri per-ABI (`arm64-v8a` dulu, `x86_64` opsional emulator), (b) `alpine-minirootfs.tar.gz` dari `dl-cdn.alpinelinux.org`.
- Verifikasi `sha256` WAJIB sebelum `chmod +x`. Checksum meleset → hapus file, tampilkan error.
- Eksekusi hanya dari `filesDir` privat. Jangan tulis/exec dari `/sdcard` (diblokir SELinux + Scoped Storage).
- AI hanya boleh tulis ke `/workspace`. Sisanya read-only. Timeout default 15 dtk (run) / 120 dtk (install). Ada tombol Kill.
- Proses container hidup di Foreground Service (reuse pola `ChatServerService`) agar tidak di-kill saat pindah tab / layar mati.
- DNS: tulis `resolv.conf` (`8.8.8.8`) saat bootstrap karena Android pakai `netd`.
- Port: hanya >1024. Hindari `:8080` (dipakai OpenAI server). Default preview `8081`, `3000`.
- Kebijakan sideload-only: fitur download executable ini tidak untuk Play Store (policy dynamic code).

## 3. Scope Fase (eksekusi BERURUTAN, satu fase satu PR kecil)

### Fase 0 — Manifest + Status (tanpa binary dulu)
- Tambah `ContainerManifest`: URL proot per-ABI + URL rootfs + versi + sha256. Bisa hardcode constants dulu.
- Tambah `Settings` keys: `alpineInstalled`, `alpineVersion`, `workspaceDir`. UI: card `Linux Environment [Belum terpasang]` di Models/Settings.
- Acceptance: card muncul, status terbaca, TIDAK ada download beneran.

### Fase 1 — Download + Bootstrap
- Reuse `LocalModelDownloader` pattern: download proot → verifikasi → `chmod +x`; download rootfs → verifikasi → unpack ke `filesDir/alpine` → tulis `resolv.conf` → `apk update` smoke test (`proot ... cat /etc/alpine-release`).
- Tombol Install / Hapus / Wipe. Progress bar + error state.
- Acceptance: `alpine-release` terbaca, `ls /` jalan, wipe menghapus `alpine/` tapi mempertahankan `workspace/`.

### Fase 2 — `ContainerManager.run()` + Tool AI
- Implementasi `run(cmd, cwd=/workspace, timeout)` via `ProcessBuilder(proot, -r, alpineDir, -b, workspace:/workspace, -0, sh, -c, cmd)` → `{stdout, stderr, exitCode}` (cap output, misal 64KB).
- Daftarkan tool AI `execute_in_container(cmd)` + `ensure_package(name)`. Sambungkan ke alur approval MCP yang sudah ada (`ChatViewModel` parse `<tool_call>` → dialog `Allow once / Always / Deny` di `ChatDialogsHost`).
- Kebijakan OpenCode: auto-allow paket kecil resmi (`curl jq sqlite ffmpeg python3 git`), ask untuk `pip/npm`, paket >100MB, repo pihak ketiga; deny untuk `rm -rf /`, `--allow-untrusted`, repo sembarangan.
- `ensure_package`: `which <bin>` → kalau exit 127 baru `apk add --no-cache <pkg>`. Tampilkan status `AI installing ...` dengan log.
- Acceptance: `/run echo hi`, `python3 --version` (setelah install), output balik ke chat sebagai `role: tool`, deny memblokir `rm -rf /`.

### Fase 3 — Menu Khusus: Terminal manual + Tab Files
- Drawer baru `Linux Env` dengan 2 tab:
  - `Terminal`: emulator (reuse lib `terminal-emulator` Termux) + PTY + tombol `Esc/Tab/Ctrl/|/~`. Session persisten, shared dengan AI (model `tmux`-like: manual dan AI attach ke session sama).
  - `Files:/workspace`: list via `File(filesDir/workspace)`, auto-refresh tiap `run()` selesai. Aksi: Preview (txt/md/csv/gambar), Share (`FileProvider`), Save ke Download (MediaStore `Download/RaikaAI/`), `Kirim ke Chat` (jadi attachment AI), `Import` (SAF picker → copy ke `workspace/`).
- Acceptance: ketik manual di Terminal muncul di Files; file dari AI (`ffmpeg out.mp4`) langsung muncul; Share/Save berhasil di Android 13+.

### Fase 4 — Serve localhost + Preview
- API `serve(dir=/workspace, port)` + chip di chat `[Preview 127.0.0.1:PORT]` → buka Custom Tab / browser luar (bukan WebView dulu). `usesCleartextTraffic` untuk http lokal.
- Default bind `127.0.0.1`. Opsi bind `0.0.0.0` + tampilkan IP LAN (`NetworkUtils`) hanya sebagai opt-in.
- Acceptance: `python3 -m http.server 8081` di container → `http://127.0.0.1:8081` kebuka di Chrome HP; kill serve menghentikan port.

## 4. Non-Goals (fase ini JANGAN dikerjakan)

- Build `proot` NDK dari source (cukup download binary; build source fase terpisah).
- `x86_64`/emulator, `Termux:API` (kamera/lokasi), GUI X11, `systemd`, Docker-in-Docker.
- WebView in-app preview, multi-session terminal, quota disk canggih.
- Masuk Play Store.

## 5. Batasan Teknis yang Harus Ditangani

- `ptrace()` bisa diblokir/diperlambat di ROM ketat (Knox/MIUI) → tampilkan warning + fallback error jelas, jangan hang.
- `musl` ≠ `glibc` → urutan fallback install: `apk` → `pip` → binary statik. `ping`/raw socket akan gagal (tanpa `CAP_NET_RAW`) → dokumentasikan.
- Cap stdout (64KB), timeout, single-flight `run()` per session (antre, jangan konkuren buta).
- Semua string user-facing Bahasa Indonesia (ikuti konvensi app), log teknis Inggris.

## 6. File yang Boleh Disentuh (jangan lebar-lebar)

- BARU: `core/container/ContainerManager.kt`, `core/container/ContainerManifest.kt`, `core/container/ProotBootstrap.kt`, UI `ui/environment/EnvironmentScreen.kt` (+ `TerminalTab`, `FilesTab`).
- EDIT minimal: `SettingsRepository` (keys baru), drawer nav (`RaikaAIApp`), Chat tool registry (`ChatViewModel` + `SlashCommands`: `/run`, `/term`, `/install`), `AndroidManifest` (service + `usesCleartextTraffic` hanya jika Fase 4).
- JANGAN ubah: engine LiteRT, `PromptBuilder` compaction, flow branch chat.

## 7. Definisi Selesai per Fase (DoD)

- `testDebugUnitTest` hijau untuk setiap util baru (manifest parse, sha256 check, policy allow/ask/deny, port validation).
- Tidak ada string Indonesia yang tercampur di log teknis; tidak ada hardcoded URL tanpa constant.
- Setiap fase diakhiri APK debug yang bisa diuji di POCO F5 via KDE Connect sebelum lanjut fase berikut.

## 8. Perintah Eksekusi Nanti (contoh, JANGAN dijalankan sekarang)

```bash
# Fase 0 saja dulu:
./gradlew testDebugUnitTest
./gradlew assembleDebug
kdeconnect-cli --device 0b53e7a3ff7348e39f76261497e740b6 --share app/build/outputs/apk/debug/app-debug.apk
```

---

## Keputusan yang Masih Butuh Jawaban User

1. Host `proot` binary: Release GitHub sendiri vs file lokal? (Rekomendasi: Release GitHub per-ABI.)
2. Tab Files: cukup Share/Save, atau sekalian `Kirim ke Chat`? (Rekomendasi: sekalian, murah.)
3. Preview serve: browser luar dulu atau WebView in-app? (Rekomendasi: browser luar.)
4. Auto-allow install paket kecil (<20MB) default nyala atau semua harus tanya dulu? (Rekomendasi: nyala untuk allowlist resmi.)
