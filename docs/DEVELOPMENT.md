# Development

Requirements: JDK 17+, Android SDK (compileSdk 37 via AGP shim, targetSdk 36),
ARM64 device / emulator (API 26+).

```bash
./gradlew testDebugUnitTest            # unit tests
./gradlew assembleDebug                 # → app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease -PabiFilter=arm64-v8a   # → app/build/outputs/apk/release/app-arm64-v8a-release.apk
```

Without `-PabiFilter`, the build produces all ABIs + universal APK.

## Native and bundled parts

- **PTY helper**: `native/raikapty` is a small Rust program. `native/raikapty/build.sh` builds the
  static musl binaries (aarch64, x86_64) and copies them to `app/src/main/jniLibs/*/libraikapty.so`.
  Rebuild only when `src/main.rs` changes; the binaries are committed.
- **Alpine rootfs**: `app/src/main/assets/rootfs/alpine-minirootfs-aarch64.bin` is the official
  Alpine minirootfs `.tar.gz`, renamed so the asset packager keeps it compressed. Its SHA-256 is pinned
  in `ContainerManifest.kt` (`BUNDLED_ROOTFS_SHA256`); update both together.
- **Supabase**: the roleplay catalog schema lives in `supabase/migrations/`. Apply with the Supabase
  CLI (`supabase db push`) using your own access token; never commit tokens.

## Release signing

`app/release.keystore` is **git-ignored and never committed**. Provide it locally
plus one of:

| Variable / property | Purpose |
|---|---|
| `RAIKAAI_KEYSTORE_STORE_PASSWORD` / `raikaai.keystore.storePassword` | keystore password |
| `RAIKAAI_KEYSTORE_KEY_PASSWORD` / `raikaai.keystore.keyPassword` | key password |
| `RAIKAAI_KEYSTORE_KEY_ALIAS` / `raikaai.keystore.keyAlias` | key alias |

Legacy `LITECHAT_*` / `litechat.*` names are accepted as fallback.

## CI

`.github/workflows/build-apk.yml` builds the arm64-v8a release APK on manual
dispatch (needs the secrets above). Android 37 platform is shimmed from 36 —
see comments in the workflow.

## Project layout

- `app/` — the Android application module.
- `docs/` — user-facing docs (`AGENT.md`, `LINUX.md`, `API.md`, `ARCHITECTURE.md`, `DEVELOPMENT.md`).
- `native/raikapty/` — Rust PTY helper source.
- `supabase/migrations/` — database migrations for the roleplay catalog.
- `AGENTS.md` (also linked as `CLAUDE.md`) — rules for AI coding agents and contributors.
- Other root `*.md` working notes (`PLAN.md`, …) are git-ignored. All docs are in English.
