# The Linux environment

RaikaAI ships Alpine Linux (aarch64 minirootfs) inside the APK and runs it with PRoot, so no
root is needed. It installs on first launch without a download.

## Folders

| Path in Linux | What it is |
|---|---|
| `/workspace` | Your project folder. Kept across resets unless you choose to delete it. |
| `/root/.raika` | Raika's home: `SOUL.md`, `AGENTS.md`, `MEMORY.md`, and `chats/` (every chat as Markdown; roleplay in `chats/roleplay/`). Kept across resets. |
| `/sdcard` | Your phone storage, when **Settings → Terminal → Phone storage in Linux** is on. Android asks for "All files access" first. |
| `/workspace/chat_uploads` | Files you attach in chat. |

## Terminal

- A real pseudo-terminal (Rust helper `libraikapty`) with a VT emulator: full-screen programs
  such as `opencode`, `vim`, `htop` and `less` work.
- Tabs, pinch to zoom, tap and swipe act as mouse clicks and scrolling, long press to drag.
- Copy output and paste from the clipboard (⋮ menu and the paste button).
- The extra key row has ESC, TAB, CTRL, ALT, SHIFT and arrows.

## Good to know

- **It is Alpine, not Debian.** Use `apk add <package>` (e.g. `apk add nodejs npm python3 py3-pip git`).
- **musl, not glibc.** Prebuilt installers made for glibc (many `curl | bash` scripts) do not
  run. Prefer `apk` packages or `npx -y <tool>`.
- **The internet works.** `ping` and `ip` fail under PRoot because raw sockets are not
  allowed; that is not an outage. Test with `curl -sI https://example.com`.
- **No services.** There is no systemd. Start servers in the background (`&`); the app detaches
  them and shows a preview link when a port opens.
- Commands from the agent time out after 30 to 120 seconds; long builds belong in the terminal.

## Reset

**Settings → Terminal → Reset Linux environment** reinstalls a fresh Alpine. Installed
packages are removed; `/workspace` is kept unless you tick the box, and `/root/.raika` is
always kept.
