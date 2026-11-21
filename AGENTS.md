# AGENTS.md

Instructions for AI coding agents (Claude Code, Codex, opencode, pi, …) and humans working on
RaikaAI. Read this before changing anything.

## Project

RaikaAI is an Android app (Kotlin, Jetpack Compose, Material 3, minSdk 26) for on-device and
remote LLM chat, with an agent (Raika) and a bundled Alpine Linux environment. A small Rust PTY
helper lives in `native/raikapty`. Start with:

- `README.md` for what the app does
- `docs/ARCHITECTURE.md` for code layout and key flows
- `docs/AGENT.md` and `docs/LINUX.md` for agent and Linux behaviour
- `docs/DEVELOPMENT.md` for build, signing and native parts

## Language

- All code, comments, UI strings, commit messages and docs are in **English**.
- Talk to the user in their language; keep the repository in English.

## Workflow

1. Read the relevant code before editing. Prefer small, focused changes over rewrites.
2. After every change, compile:
   `./gradlew :app:compileDebugKotlin`
3. Before a release build, run the tests:
   `./gradlew :app:testDebugUnitTest`
   Add or update unit tests for logic you change (parsers, policies, prompt building, terminal
   emulation). Tests live in `app/src/test`.
4. Release build (arm64 phones):
   `./gradlew assembleRelease -PabiFilter=arm64-v8a`
   Output: `app/build/outputs/apk/release/app-arm64-v8a-release.apk`.
5. **Every build that goes to a device bumps the version**: `versionCode` +1 and the patch of
   `versionName` in `app/build.gradle.kts`, plus a `CHANGELOG.md` entry (Keep a Changelog style:
   Added / Changed / Fixed, written for users). The patch number runs 0–9: after `x.y.9` comes
   `x.(y+1).0` (0.1.9 → 0.2.0), never `x.y.10`.
6. Install on a real arm64 phone to verify. Emulators and Waydroid are x86_64 and cannot run the
   bundled arm64 rootfs; use them only for UI screenshots.

Shell tip: pass Gradle flags inline. Storing them in a variable (`$G`) breaks under zsh, which
does not word-split, and Gradle then prints its help instead of building.

## Commits

- Commit or push only when asked.
- Conventional-style subjects: `feat(scope): …`, `fix(scope): …`, `docs: …`, `release: vX.Y.Z - …`.
  Explain the why in the body.
- **No `Co-Authored-By` lines or AI attribution** in commits or PRs.
- Never commit secrets: `local.properties`, keystores, API keys, Supabase access tokens or
  service keys. The Supabase **publishable** key in `RolePlayConstants.kt` is public by design.

## Code conventions

- Disk and network I/O off the main thread. Compose state hoisted; no work in composition.
- Gson-parsed models go through their `normalize()` helper (Gson bypasses Kotlin defaults).
- UI: use `ScreenHeader`, outlined icons, rounded cards (20 dp), and bottom sheets for actions,
  like the existing screens. Wide controls (segmented buttons) go under a settings row's text
  (`bottomContent`), never in the trailing slot.
- Generation must keep running across screen and chat switches (`ChatLane`); never stop it in
  `onDispose`.

## Agent and context rules

- On-device (LiteRT) models have small context windows. Anything added to the system prompt or
  tool results needs a compact path for `modelSource != REMOTE` (hard caps, one-line lists).
- Roleplay keeps the character card first; do not inject tool catalogs into roleplay prompts.
- Every command or tool call that changes state goes through the permission gate
  (Ask / Auto / Full access). MCP calls ask in Ask and Auto modes (with a per-tool
  "Always" option) and run automatically in Full access.
- Never let the model's claims stand in for tool output: results shown in chat come from real
  runs.

## Linux environment

- Alpine (musl) under PRoot, no root, no systemd. Use `apk`, not apt.
- Shell redirections (`2>&1`, `>&2`) are not background jobs; only a real `&` detaches.
- `/workspace` (projects), `/root/.raika` (agent config and chats), `/sdcard` (phone storage,
  opt-in) are bind mounts; keep them working when touching `ContainerManager.baseArgs()`.
- Updating the bundled rootfs means updating `BUNDLED_ROOTFS_SHA256` in `ContainerManifest.kt`.

## Database (Supabase)

- Schema changes are SQL files in `supabase/migrations/`, applied with the Supabase CLI.
- Keep row-level security on. Public clients may only read approved characters and submit
  through `submit_character`.

## Before you finish

- Compiles, tests pass, version bumped and changelog updated if a build was made.
- Docs updated when behaviour changes (`README.md`, `docs/`).
- Tell the user plainly what changed, what was verified, and what was not.
