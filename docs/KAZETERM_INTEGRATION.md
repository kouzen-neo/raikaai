# KazeTerm integration plan: Linux runtime and terminal for RaikaAI

Status: implementation plan, 2026-11-18. Companion to `docs/PORT_PI.md`, which owns the
product-level decisions. This document is source-verified against the read-only KazeTerm
checkout at `/root/Documents/kazeterm` and this repository on `feat/pi-runtime`. KazeTerm
paths below are repository-relative to `/root/Documents/kazeterm`; RaikaAI paths are
relative to this repository. Line numbers are from the state read for this document.

Scope: KazeTerm replaces the PRoot backend in
`app/src/main/java/com/raikaai/app/core/container/ContainerManager.kt` and the line-stream
terminal in `app/src/main/java/com/raikaai/app/ui/environment/EnvironmentScreen.kt`, and
hosts the Node/Pi adapter. Everything else in `docs/PORT_PI.md:44-46` stays untouched.

---

## 1. Building, packaging and publishing the KazeTerm Android library

### 1.1 What exists today (and what does not)

KazeTerm has **no AAR task**. Its Gradle project is an application module
(`android/app/build.gradle.kts:4`, `namespace = "com.kazeterm"` at `:69`). The reusable
artifact is the native library `libkazeterm.so`, produced by `CargoNdkTask`
(`android/app/build.gradle.kts:225-274`), which runs:

```
cargo ndk -t arm64-v8a --platform 30 -o <out>/generated/rust/<variant>/jniLibs \
  build -p kazeterm-android [--profile <profile>] [--features test-hooks,borrow-check]
```

Evidence: `android/app/build.gradle.kts:255-267`; the generated directory is registered as
the variant's `jniLibs` source at `:183-185`. The library name comes from
`hosts/android/Cargo.toml:10-12` (`name = "kazeterm"`, `crate-type = ["cdylib"]`), so the
output file is `libkazeterm.so` under
`android/app/build/generated/rust/<variant>/jniLibs/arm64-v8a/`.

Consequence: to consume KazeTerm as a library, someone must add a thin
`com.android.library` module on the KazeTerm side (call it `kazeterm-sdk`) that (a) takes
that `libkazeterm.so` as its `src/main/jniLibs/arm64-v8a/` input and (b) ships the Kotlin
declarations from `android/app/src/main/java/com/kazeterm/Native.kt`. That module is the
AAR. Do not try to treat the APK as a library.

### 1.2 Required toolchain (exact versions)

| Tool | Version | Evidence |
|---|---|---|
| JDK | 17 or later | `README.md:76`, `android/README.md:25-26`, `android/app/build.gradle.kts:110-113` |
| Android SDK Platform | 37 (minSdk 30, targetSdk 37) | `README.md:76`, `android/app/build.gradle.kts:71-77` |
| Android NDK | 29.0.14206865 | `README.md:76`, `android/app/build.gradle.kts:72` |
| AGP / Gradle | 9.4.1 / 9.9.1 with built-in Kotlin | `.agents/maps/android/build.md:78-80` |
| Rust | `rust-version = "1.90"`, edition 2024, target `aarch64-linux-android` | `Cargo.toml:13-17`, `README.md:83` |
| cargo-ndk | `cargo install cargo-ndk --locked` | `README.md:84` |
| Zig | 0.17.0 / 0.17.x (libghostty-vt 1.0.0 enforces it) | `README.md:77`, `android/README.md:26-28`, `.agents/maps/android/build.md:70-72` |
| Build inputs | Alpine rootfs tarball (`scripts/fetch-rootfs`) and libghostty-vt source + Zig packages (`scripts/fetch-ghostty`) | `README.md:85-86`, `android/README.md:27-30`, `android/app/build.gradle.kts:156-163` |

Zig resolution order is `android/local.properties` key `zig.path`, then `ZIG` env var,
then `zig` on `PATH` (`android/README.md:32-39`, `android/app/build.gradle.kts:10-18`).
`hosts/android/build.rs:16,55-56,99` asserts the source
`target/ghostty/libghostty-vt-1.0.0` plus its `-deps` packages, targets
`aarch64-linux-android`, and statically links `libghostty-vt.a`. `build.rs` **panics on any
architecture other than aarch64** (`.agents/maps/android/build.md:58-60`), so the library
is arm64-only.

### 1.3 Exact build commands

From the KazeTerm repository root (verbatim from `README.md:82-89`, with the library build
added):

```sh
rustup target add aarch64-linux-android
cargo install cargo-ndk --locked
scripts/fetch-rootfs          # target/rootfs/alpine-minirootfs-*.tar.gz + bundled apks
scripts/fetch-ghostty         # target/ghostty/libghostty-vt-1.0.0 + -deps

# Native library only, release/LTO profile (no test hooks) — the artifact RaikaAI wants:
CARGO_TARGET_DIR=target/android \
  cargo ndk -t arm64-v8a --platform 30 \
    -o target/aar-input/jniLibs \
    build -p kazeterm-android --profile android-release

# Equivalent via Gradle, if building the whole app for verification first:
cd android && ./gradlew assembleDebug && cd ..
```

Profile notes: debug uses Cargo's `release` profile plus `test-hooks,borrow-check`
(`android/app/build.gradle.kts:179`); release uses the workspace `android-release` profile
with cross-crate LTO and `codegen-units = 1` (`android/app/build.gradle.kts:180`,
`Cargo.toml:31-35`). RaikaAI should use `android-release` without the debug features.
`useLegacyPackaging = false` must be set in the consuming app so the 16 KB-aligned `LOAD`
segments map directly from the APK (`android/app/build.gradle.kts:115-119`,
`.agents/maps/android/build.md:94-96`).

### 1.4 Publish locally, consume locally, commit no binaries

1. In the new KazeTerm `kazeterm-sdk` library module, publish the AAR to the local Maven
   repository: `cd android && ./gradlew :kazeterm-sdk:publishToMavenLocal`. The AAR lands
   in `~/.m2/repository/<group>/kazeterm-sdk/<version>/` — outside both git trees. Add a
   `.gitignore` entry for that module's `build/` (standard) and never commit the `.so`,
   the AAR, the rootfs tarball or the libghostty-vt archives. KazeTerm already ignores
   `target/`, `local.properties` and fetched inputs; verify the new module follows suit.
2. In RaikaAI, add `mavenLocal()` to the repositories in `settings.gradle.kts` (or
   `dependencyResolutionManagement`) and
   `implementation("<group>:kazeterm-sdk:<version>")` to `app/build.gradle.kts`, gated so
   `ON_DEVICE` builds can be made without it if desired. Keep the version pinned in the
   version catalog.
3. For CI, replace `mavenLocal()` with an internal Maven repository or build the KazeTerm
   library in the same job from a pinned KazeTerm commit/tag. Do not add a prebuilt `.so`
   to RaikaAI's `app/src/main/jniLibs/`.
4. Traceability: publish the KazeTerm commit SHA and toolchain versions alongside the AAR
   (a small `kazeterm-sdk.build.txt` in the AAR's `META-INF`), so a release can be rebuilt.
   KazeTerm's release flow (`AGENTS.md:35-46`) archives sources and R8 mappings; mirror the
   "complete corresponding source" promise for any RaikaAI release that embeds the AAR.

Verification before publishing: `nm -D libkazeterm.so | grep Java_com_kazeterm` must list
the functions in §2; `./gradlew :kazeterm-sdk:assembleRelease` must succeed with NDK 29
and Zig 0.17.x; confirm `arm64-v8a/libkazeterm.so` is uncompressed in the AAR
(`unzip -v ... | grep libkazeterm`).

---

## 2. JNI/Kotlin surface RaikaAI would call

### 2.1 Ownership, loading and error model

- Native side: `hosts/android/src/lib.rs:20` compiles only for `target_os = "android"`.
  Kotlin side: `android/app/src/main/java/com/kazeterm/Native.kt:13-17` does
  `System.loadLibrary("kazeterm")` and `init(BuildConfig.DEBUG)`.
- **JNI symbol names are hardcoded to `com_kazeterm_Native_*`** (e.g.
  `hosts/android/src/lib.rs:79,131,660,700,810,1173`). The Kotlin class must therefore stay
  `com.kazeterm.Native` with `@JvmStatic external fun` declarations; renaming the Kotlin
  package requires renaming every Rust export or adding a Java shim class. Copy
  `Native.kt` verbatim into the SDK AAR rather than rewriting it.
- Machines are held in a Rust handle table (`hosts/android/src/lib.rs:656-657`, `machine()`
  at `:1205-1208`). `start` returns a `Long` handle; every other call takes it. `stop` and
  `shutdown` remove it (`:1173-1187`, `:1191-1203`); later calls are inert or return
  `-1`/`-9` (documented at `Native.kt:8-11`).
- Most failures throw `java.io.IOException` (`hosts/android/src/lib.rs:1216-1219`);
  panics are caught by `guarded!` (`:40-60`) and turned into an `IOException` for calls
  Kotlin declares `@Throws`. Blocking calls (`read`, `write`, `waitFor`, `boot`,
  `takeListens`, `takeKills`, `takeChangedDirs`, `trimMemory`, `shutdown`) must run on
  background threads (`Native.kt:8-11`, `:140-145`, `:203-205`, `:247-257`, `:260-263`).

### 2.2 Machine lifecycle

Rust kernel types are re-exported at `core/kernel/src/lib.rs:64` (`Machine`,
`MachineOptions`, `Stdio`, `SHUTDOWN_TIMEOUT`, `run`) and `:67` (`Terminal`).

| Step | Kotlin (`Native.kt`) | JNI (`hosts/android/src/lib.rs`) | Rust kernel (`core/kernel/src/machine.rs`) |
|---|---|---|---|
| Import rootfs | `importRootfs(store, tarball)` `:29` | `Java_..._importRootfs` `:131-146` | `import_rootfs` `core/kernel/src/lib.rs:81-91` |
| Sync store | `syncRootfs(store)` `:37` | `:151-164` | `sync_store` `core/kernel/src/lib.rs:97-101` |
| Start | `start(store, tier): Long` `:137` | `:660-695` | `MachineOptions` `:48-71`, `Machine::start` `:108` |
| Boot inittab | `boot(machine)` `:145` | `:700-710` | `Machine::boot` `:182` |
| Spawn | `spawn(machine, console, program, argv, envp): Int` `:166` | `:810-840` | `Machine::spawn` `:214-225`, `Stdio` `:75-81` |
| Wait | `waitFor(machine, pid): Int` `:191` | `:948-963` | `Machine::wait` `:514` |
| Power request | `takePower(machine): Int` `:229` | `:1096-1109` | `Machine::take_power` `:733` |
| Shutdown | `shutdown(machine)` `:265` | `:1173-1187` | `Machine::shutdown` `:585`, `SHUTDOWN_TIMEOUT` = 30 s |
| Hard kill | `stop(machine)` `:269` | `:1191-1203` | `Machine::kill_all` `:710` |

`MachineOptions` fields (`machine.rs:48-71`): `tier`, `rootfs`, `memory_limit`,
`low_port_offset`, `always_map_low_ports`, `exception_trace`. The Android JNI `start`
constructs it with `tier = 1 → Tier::Dbt` else `Tier::Interpreter`, `rootfs = store`,
`memory_limit = None`, default low-port policy, and `exception_trace = true`
(`hosts/android/src/lib.rs:670-682`). RaikaAI gets the same behavior automatically;
`MachineOptions` itself is **not** exposed over JNI — only `tier` is a parameter
(`Native.kt:134-137`). If RaikaAI needs a different memory limit or port offset, add a new
JNI entry point in the SDK fork; do not expect to set it from Kotlin today.

`spawn` returns a guest pid; `console < 0` selects `Stdio::Host` (the app's stdout/stderr,
i.e. `/dev/null` + logcat in the app) and `console >= 0` attaches the console as the
controlling terminal (`hosts/android/src/lib.rs:826-832`). For captured command output use a
console; for fire-and-forget daemons use a negative console.

### 2.3 Console / terminal APIs

`Terminal` is `pub struct Terminal` at `core/kernel/src/terminal.rs:240`, re-exported from
`core/kernel/src/lib.rs:67`. Host code does not call `Terminal` directly; it drives it via
`Machine`'s console methods, exposed as:

- `openConsole(machine, rows, cols): Int` — `Native.kt:158`, JNI `:786-807`,
  `Machine::open_console` `machine.rs:392`. Returns an in-process PTY number.
- `read(machine, console, buffer): Int` — `Native.kt:173`, JNI `:845-872`,
  `Machine::read_console` `machine.rs:409`. Returns bytes, `0` on session end, `-1` if
  closed; called with a byte array from a background thread.
- `write(machine, console, buffer, offset, length): Int` — `Native.kt:177`, JNI `:877-908`,
  `Machine::write_console` `machine.rs:439`.
- `resize` / `closeConsole` — `Native.kt:180,184`, JNI `:911-942`,
  `machine.rs:462,477`. Closing sends `SIGHUP` to the session leader.
- `consoleCwd(machine, console)` — `Native.kt:198`, JNI `:969-983`, `machine.rs:496`;
  used where `EnvironmentScreen` currently keeps its own prompt/cwd.
- Diagnostics: `takeListens` (`Native.kt:207`, JNI `:989-1014`) for discovered guest
  ports, `takeKills`, `bklStall`, `processes`, `guestCpuTime`, `takeChangedDirs`,
  `trimMemory`, `setRoutes` (`Native.kt:153,207-257`; JNI `:716-740,989-1167`).

Ready-made Kotlin patterns to copy rather than reinvent:
- `android/app/src/main/java/com/kazeterm/ConsolePty.kt:13-40` shows the exact
  `openConsole` → `spawn` → `waitFor` → `closeConsole` sequence, including `argv`.
- `android/app/src/main/java/com/kazeterm/VtSession.kt:26-68` declares the `Events`
  callbacks; `:355-517` receives output/status/command events.
- `VtView.kt` / `VtRenderer.kt` / `VtGlyphs.kt` are the libghostty-vt-backed renderer.
  If RaikaAI keeps Compose, feed a Compose terminal from `Native.read`; if it wants a real
  emulator, port `VtView` + `VtSession` and keep the R8 keep rules for the `VtSession.on*`
  callbacks (`.agents/maps/android/build.md:101-105`).

### 2.4 Store / file APIs (replacing ContainerFileOps)

`core/kernel/src/lib.rs:105-159` exposes `write_file`, `read_file`, `browse` and
`browse_changing`; JNI wrappers are `Native.writeFile` / `readFile`
(`Native.kt:42,47`; JNI `:167-217`) and the `browseStat`, `browseList`, `browseOpen`,
`browseSniff`, `browseSearch`, `browseCreate`, `browseRemove`, `browseRename`,
`browseMove`, `browseOpenWrite`, `browseWritten` family (`Native.kt:60-132`; JNI
`:235-634`). Note the `top` argument on mutation calls: changes are confined to that
directory by real path (`hosts/android/src/lib.rs:407-425`). Pass `/workspace` as `top`
so the Files tab cannot escape it. Paths are encoded as document IDs (`%XX` for control
and non-UTF-8 bytes, `hosts/android/src/lib.rs:219-227`).

---

## 3. Rootfs, Node 22.19+, Pi packaging, and `/workspace`

### 3.1 Rootfs acquisition

KazeTerm's own app imports an Alpine minirootfs from APK assets on first launch, selects a
fast mirror and can replace it with a Debian download verified against a pinned sha256
(`android/README.md:9-14`; asset wiring at `android/app/build.gradle.kts:156-163,282-320`).
RaikaAI must **not** bundle that tarball in the base APK (`docs/PORT_PI.md:209-211`).
Reuse RaikaAI's existing download path instead:

1. Download the guest rootfs tarball to `filesDir` with the existing SHA-256-verified
   `downloadToFile` pattern from `ContainerManager.kt:888-933`.
2. Create an empty store directory and call `Native.importRootfs(store, tarball)`,
   then `Native.writeFile(store, ...)` to stage configuration, then
   `Native.syncRootfs(store)` before renaming it into place — this is the exact ordering
   required by `core/kernel/src/lib.rs:93-101` (a power loss between import and sync can
   leave an empty store).
3. `Native.start(store, 1)` → `Native.boot(handle)` (inittab boot) → spawn the Node
   adapter with a console.

Keep a schema marker like `ContainerManager.ROOTFS_SCHEMA` (`ContainerManager.kt:64,176-191`)
so an inode-store format change forces reinstall. KazeTerm's store is an inode store in a
host directory (`core/kernel/src/lib.rs:76-91`), not an unpacked directory tree; the
existing `unpackTarGz`/`guestResolves` code (`ContainerManager.kt:988-1145`) is obsolete
for this backend.

### 3.2 Node 22.19+ and Pi: bundle vs first-boot install

- **Recommended: first-boot install.** After the rootfs import and boot, run a one-time
  provisioning script inside the guest that installs Node 22.19+ and the Pi CLI packages.
  KazeTerm's guest uses the host network (`README.md:141-149`), so `apk add`/`npm install`
  work; pass proxy env vars explicitly through `spawn`'s `envp` (the guest sees only the
  env passed to it, `README.md:141-143`). Pin every downloaded artifact to a sha256 and
  store a provisioning marker inside the guest. This keeps `ON_DEVICE` users' APK small,
  matching `docs/PORT_PI.md:209-211`.
- **Alternative: bundle into the rootfs image.** Pre-build an Alpine rootfs tarball that
  already contains Node and Pi and download it on first enable. Offline and reproducible,
  but a much larger download and a second rootfs artifact to license and maintain. Choose
  this only if first-boot install proves unreliable on captive portals.
- Alpine's packaged Node.js may be older than 22.19; if so, install the official arm64
  Node 22.19+ tarball into `/opt/node` in the guest and add it to `PATH` for the adapter
  process, rather than relying on `apk add nodejs` alone. Confirm the exact published Pi
  package names at port time against `docs/PORT_PI.md:161-167` (the upstream set is
  `pi-ai`, `pi-schema`, `pi-agent-core`, `pi-coding-agent`, `pi-mcp` v1.5.2); do not hardcode
  a guessed npm name.
- Pi skills are reintroduced later as `/workspace/.pi/skills/<slug>/SKILL.md`
  (`docs/PORT_PI.md:88-89`), i.e. as guest files, not Kotlin prompt text.

### 3.3 Where `/workspace` lives

With PRoot, `/workspace` is a host bind mount of `filesDir/workspace`
(`ContainerManager.kt:37,148,378-388`). With KazeTerm there is no bind mount: `/workspace`
is an ordinary directory **inside the inode store**. Practical rules:

- Create `/workspace` during staging with `Native.writeFile` (or in the guest init script)
  before `syncRootfs`.
- All Kotlin file operations become `Native.browse*` calls with `store` and `top =
  "/workspace"`, rather than `java.io.File` on `filesDir/workspace`
  (`ContainerManager.kt:228-252`, `ContainerFileOps`). `EnvironmentScreen.FilesTab`
  (`:769-965`) currently uses `File` APIs and must be rewritten against those JNI calls
  (import via `browseCreate`/`browseOpenWrite`, export via `browseOpen`/`readFile`).
- The guest's Pi adapter therefore sees the same `/workspace` path the Kotlin tools see;
  there is no host/guest path translation to maintain.
- Keep the `isWithinRoot` guard (`EnvironmentScreen.kt:989-994`) conceptually in the JNI
  `top` confinement; do not rely on UI-only checks for safety.

### 3.4 Pi adapter port and token mapping to the guest

RaikaAI already persists the adapter settings: `runtimeMode`, `piAdapterHost` (default
`127.0.0.1`), `piAdapterPort` (default `8787`, clamped 1024–65535), and
`piAdapterToken` read from encrypted preferences
(`SettingsRepository.kt:123-129,556-559`). `ChatViewModel.kt:238-247` builds
`ContainerRuntime(host, port, token)`; `ToolRuntime.kt:120-146` defines
`adapterBaseUrl = "http://$host:$port"` and currently returns "unavailable" for all
container tools until the guest adapter lands.

Mapping:

- The Node adapter binds `127.0.0.1:$piAdapterPort` **inside the guest**. Because guest
  networking uses the host network (`README.md:141`), Kotlin's existing
  `adapterBaseUrl` works unchanged. Port 8787 is above 1024, so the low-port remap
  (`machine.rs:58-61`, default offset 10000) does not apply; do not choose a port below
  1024 for the adapter.
- Pass the token to the guest **as an environment variable via `spawn`'s `envp`**
  (`Native.kt:166`, JNI `:810-839`) or as a mode-0600 config file written with
  `Native.writeFile` (`Native.kt:42`). Do **not** put it in `argv` (visible in guest
  `/proc`) or in `adapterBaseUrl` (`ToolRuntime.kt:130-131`). Kotlin keeps the token only
  in encrypted prefs (`SettingsRepository.kt:129,559`).
- On network changes, call `Native.setRoutes(machine, routes)` (`Native.kt:153`, JNI
  `:716-740`), which replaces PRoot's periodic `resolv.conf` rewrite
  (`ContainerManager.kt:363-368,1024-1042`).
- The adapter exposes an OpenAI-compatible endpoint to Pi and translates Pi RPC JSONL
  events, per `docs/PORT_PI.md:68-72,113-127`. Pi's `baseUrl` points at RaikaAI's local
  Ktor `/v1`; the tool-calling gap in `/v1` (`docs/PORT_PI.md:113-127`) is handled in the
  adapter (option 2) so LiteRT and the Ktor server stay untouched.

---

## 4. Migration from `ContainerManager` (PRoot) to the KazeTerm backend

### 4.1 API surface to preserve

Callers use the following `ContainerManager` members (grep-verified):

- `ChatViewModel`: `install`, `wipe`, `isInstalled`, `installState`, `servedPort`,
  `previewUrl`, `run` (6 sites), `runStreaming`, `execProot`-backed helpers,
  `startDaemon` (2), `stopDaemon` (2), `listDaemons` (2), `serve`, `stopServe`,
  `markPreviewPort`, `probeListeningPort`, `refreshResolvConf`, `writeFile`, `readFile`,
  `editFile`.
- `EnvironmentScreen`: `installState`, `install`, `cancelInstall`, `isInstalled`,
  `isOutdated`, `diagnostics`, `servedPort`, `workspaceDir`, `workspaceFiles`.

Implement a `KazeTermBackend` exposing the same names and `StateFlow`s, selected by
`runtimeMode = KAZETERM_PI` (`SettingsRepository.kt:123`, `RuntimeMode.kt:21`).
Suggested mapping:

| PRoot primitive | KazeTerm replacement |
|---|---|
| `run` / `execProot` (`ContainerManager.kt:346-435`) | `openConsole` → `spawn` → `read` loop → `waitFor` → `closeConsole` (§2.3) |
| `runStreaming` (`:442-519`) | same console read pumped into `onLine`; `TermKind` mapping stays |
| `startDaemon`/`stopDaemon` (`:590-758`) | `spawn` with negative console (`Stdio::Host`) + `waitFor`; use `takeListens` for port discovery instead of the `parseAnnouncedPort`/socket probe (`:710-721`) |
| `serve` (`:779-820`) | guest `httpd` via spawn + console, or `takeListens`-driven preview URL |
| `writeFile`/`editFile`/`readFile` (`:236-252`) | `Native.browse*` / `writeFile` / `readFile` with `top = /workspace` (§3.3) |
| `refreshResolvConf` (`:1024-1042`) | `Native.setRoutes` (§3.4) |
| `install`/`wipe`/schema (`:261-338`) | tarball import + sync + marker (§3.1) |

`TerminalService` (`android/app/src/main/java/com/kazeterm/TerminalService.kt`) and
`ConsolePty.kt:13-40` are the reference implementations for owning a `Machine` in a
foreground service and mapping console output to UI; RaikaAI can adapt them instead of
writing the JNI lifecycle from scratch.

### 4.2 Keep as fallback

- Keep `LEGACY_PROOT` as a `RuntimeMode` value (`docs/PORT_PI.md:200`, `RuntimeMode.kt`)
  and keep `ContainerManager.kt`, `ContainerManifest.kt`, `ContainerFileOps` and the
  bundled PRoot binaries until the device test matrix in §4.3 is green on real hardware.
- Default stays `ON_DEVICE` (`SettingsRepository.kt:123`) — no guest, no Pi, no terminal.
  Keep `EnvironmentScreen` and container slash commands hidden there
  (`docs/PORT_PI.md:206-208`).
- Do not delete PRoot deployment code or the GPL attribution burden until the flag is
  removed (`docs/PORT_PI.md:157-159`).
- Add the R8 keep rules for `com.kazeterm.Native` methods and any `VtSession.on*`
  callbacks if RaikaAI enables minification; KazeTerm's own rules are
  `android/app/proguard-rules.pro` (`.agents/maps/android/build.md:101-105`).

### 4.3 Device tests required

- **arm64 device mandatory.** KazeTerm builds only `arm64-v8a` and `build.rs` rejects other
  architectures (`README.md:11-12`, `.agents/maps/android/build.md:58-60,70-72`). An x86_64
  emulator cannot run this backend; `ON_DEVICE` and JVM unit tests still run on any host.
- **4K-page and 16K-page system images.** KazeTerm's own instrumentation suite is required
  to run on both (`android/README.md:54-56`); the APK stores native libs uncompressed with
  16 KB-aligned segments (`android/app/build.gradle.kts:115-119`,
  `.agents/maps/android/build.md:94-96`). Run the equivalent RaikaAI smoke test on both:
  import rootfs → boot → spawn Node → `pi --mode rpc` → tool round-trip → shutdown.
- Command: `./gradlew connectedDebugAndroidTest` on each image; network-dependent cases
  need `-Pandroid.testInstrumentationRunnerArguments.network=true`
  (`android/README.md:55-57`). If an emulator uses `-http-proxy`, follow the proxy caveat
  in `android/README.md:57-64` (use `adb shell settings put global http_proxy ...` and
  clear with `... :0`).
- Runtime/kernel regression suite (Linux host, not device):
  `scripts/dev cargo test --workspace` and the LTP/difftest flows in `README.md:95-203`.
  Run these whenever the KazeTerm pin changes.
- Acceptance for removing the PRoot fallback: `ON_DEVICE` unit tests plus the full
  `KAZETERM_PI` device matrix on 4K and 16K images pass for two consecutive builds.

---

## 5. Legacy RaikaAI skills removal checklist

Decision source: `docs/PORT_PI.md:74-89`. Grep results below are from this repository on
`feat/pi-runtime` (`grep -rn` over `app/src` plus repo-level paths).

### 5.1 Files to delete

| # | Path | Evidence / reason |
|---|---|---|
| 1 | `app/src/main/java/com/raikaai/app/skills/Skill.kt` | data class + `normalize()` (`:5,24-40`) |
| 2 | `app/src/main/java/com/raikaai/app/skills/SkillsManager.kt` | singleton, 4 built-ins (`:50-91`), JSON persistence (`:95-98`) |
| 3 | `app/src/main/java/com/raikaai/app/ui/skills/SkillsScreen.kt` | Skills destination UI |
| 4 | `app/src/main/java/com/raikaai/app/ui/skills/SkillsSheet.kt` | has no callers (grep: only its own declaration at `:77`) |
| 5 | `app/src/main/java/com/raikaai/app/ui/skills/SkillImportReviewDialog.kt` | only referenced by files 3–4 |
| 6 | `app/src/test/java/com/raikaai/app/skills/SkillNormalizeAuditTest.kt` | tests `Skill.normalize()` |
| 7 | `docs/screenshots/skills.png` | remove together with README image rows |

**Do not delete `app/src/main/java/com/raikaai/app/skills/ToolCallParser.kt` yet.** Its
`parseGenericCall` is used by the current agent loop (`ChatViewModel.kt:1743,3210,3501,3549,3575,3618,3661`) and
`GenericToolCall` is a parameter type at `:2260,2357`; `docs/PORT_PI.md:85-86` says to keep
it until Pi replaces the loop. Because it lives in the `skills` package, **move it** to a
neutral package (e.g. `com.raikaai.app.core.tool.ToolCallParser.kt`) and update the fully
qualified references in §5.3, or keep the otherwise-empty `skills` package solely for it.

### 5.2 Call sites to remove or rewrite

- `app/src/main/java/com/raikaai/app/ui/chat/ChatViewModel.kt`
  - `:214` remove `private val skillsManager get() = ...SkillsManager.getInstance(app)`.
  - `:217` remove `val skills: StateFlow<List<Skill>> get() = skillsManager.skills`
    (only consumers are the deleted Skills UI files).
  - `:817-820` remove the `activeSkills = skillsManager.getActivePromptInstructions()`
    block appended to `system` in `withRagSystem`.
  - `:834` remove `isWebSkillActive` (note: it checks id `builtin_web_researcher`, which is
    **not** in `SkillsManager.BUILTIN_SKILLS` at `SkillsManager.kt:50-91` — a stale id).
  - `:839` rewrite: `shouldSearch = settings.value.enableWebSearch || isExplicitSearch ||
    hasSearchKeyword`. `enableWebSearch` becomes the sole persistent gate
    (`docs/PORT_PI.md:82-84`; `SettingsRepository.kt:110`).
  - `:1741,1743,2260,2357,3210,3501,3549,3575,3618,3661` retarget `ToolCallParser`
    references after the package move (§5.1). Keep the call logic itself.
- `app/src/main/java/com/raikaai/app/ui/RaikaAIApp.kt`
  - `:97` remove `const val SKILLS = "skills"`.
  - `:227` remove `DrawerMenuItem(NavRoute.SKILLS, "Skills", Icons.Outlined.AutoAwesome)`.
  - `:352-357` remove the `composable(NavRoute.SKILLS) { SkillsScreen(...) }` block.
- `app/proguard-rules.pro:18` remove `-keep class com.raikaai.app.skills.** { *; }`;
  replace with a narrow keep for the relocated `ToolCallParser` if R8 needs it.
- `app/src/main/java/com/raikaai/app/ui/chat/PersonaStore.kt:168-170` rewrite the comment
  "mirrors the skill import caps" — the caps it refers to disappear with `SkillsManager`;
  keep the actual `AppLimits.PERSONA_IMPORT_MAX_CHARS` guard.
- `app/src/main/java/com/raikaai/app/data/SettingsRepository.kt:109` is only a section
  comment "Context & Capability Skills" above unrelated keys (`autoCompress`,
  `captureArtifacts`, `enableVision`, `enableWebSearch` at `:110-113`); rename the comment,
  keep the keys. No skill-specific DataStore key exists — no preference migration needed
  (`docs/PORT_PI.md:79-80`).
- Tests: `app/src/test/java/com/raikaai/app/ToolCallParserTest.kt:3` and
  `app/src/test/java/com/raikaai/app/mcp/McpParserTest.kt:3` update the `import` to the
  new `ToolCallParser` package; keep both suites.

### 5.3 On-device data and generated files

Delete at migration time (all created by `SkillsManager.kt:95-98,128,156,176`):
`filesDir/skills/` (directory), `filesDir/skills/custom_skills.json`,
`filesDir/skills/custom_skills.json.bak.*`, `filesDir/skills/preset_toggles.json`,
`filesDir/skills/.builtin_skills_default_off_v1`, and
`filesDir/skills/.custom_skills_default_off_v2`. No DataStore migration required.

### 5.4 Docs and leftover references

- `README.md:36` and `README.id.md:36` remove the `docs/screenshots/skills.png` table cell;
  `README.md:55` / `README.id.md:55` remove or rewrite the "Skills & MCP" feature bullet.
- `docs/ARCHITECTURE.md:20,25` update the directory trees; `:50` remove `Skill` from the
  `normalize()` example list.
- `.gitignore:12-13` (`web_search_skill.json`, `web_search_skill.md`) can be removed with
  the subsystem.
- `docs/PORT_PI.md:74-89` is the decision record — keep it as historical rationale.

### 5.5 Verification order

1. Move `ToolCallParser` and update all 10+ fully qualified call sites and 2 test imports.
2. Remove the `ChatViewModel` prompt/skill references; verify `withRagSystem` still builds
   the system prompt and that `enableWebSearch` alone controls search.
3. Remove the UI files, navigation entries and ProGuard rule.
4. Delete the Kotlin data/manager files and the audit test; delete runtime data in the
   migration path.
5. `./gradlew test` (JVM) and `./gradlew assembleDebug lintDebug`; confirm zero remaining
   `SkillsManager` / `com.raikaai.app.skills.Skill` references with a repo-wide grep.

---

## 6. Risks and open questions

- **Licensing.** KazeTerm is AGPL-3.0-or-later (`Cargo.toml:17`, `README.md:244`) with a
  Fuchsia BSD-style license for `core/kernel/src/tty.rs` (`README.md:246-247`), plus
  libghostty-vt, uucode, simdutf, Highway, Wuffs, JetBrains Mono (OFL) and Alpine package
  notices bundled under `android/app/src/main/assets/notices/`
  (`README.md:249-252`, `.agents/maps/android/build.md:107-133`). RaikaAI is GPLv3 and Pi
  is MIT (`docs/PORT_PI.md:233`). Shipping the AAR means distributing a derivative work:
  get legal review of AGPL+GPLv3 compatibility, keep the bundled notices and source offer
  in Settings, and do **not** drop the required notices when stripping the KazeTerm APK
  down to a library. `docs/PORT_PI.md:157-159` also removes PRoot's GPL attribution burden
  only if PRoot is actually removed.
- **minSdk mismatch.** KazeTerm requires API 30 (`android/app/build.gradle.kts:76`,
  `README.md:11`); RaikaAI is API 26 (`app/build.gradle.kts:26`, `docs/PORT_PI.md:232`).
  Decide whether to raise RaikaAI's minSdk to 30 or make the AAR dependency and
  `KAZETERM_PI` mode conditional on `Build.VERSION.SDK_INT >= 30`. The latter avoids
  manifest-merger minSdk failures but needs a runtime capability check.
- **Native library name collisions / JNI symbols.** `libkazeterm.so` must not collide with
  RaikaAI's existing native libraries (LiteRT AAR, PRoot's `libproot*.so`, any Vulkan/NDK
  libs). Verify with `readelf -d`/`nm -D` that JNI exports are unique and that the Kotlin
  class remains `com.kazeterm.Native` (§2.1); otherwise the JVM will resolve the wrong
  `Native` class or fail `UnsatisfiedLinkError`.
- **Build size.** Two costs: the native library with statically linked libghostty-vt
  (`.agents/maps/android/build.md:58-72`), and the rootfs + Node + Pi download. Keep both
  out of the base APK (`docs/PORT_PI.md:209-211`); measure the `android-release` AAR and
  the first-boot download before choosing bundle vs install (§3.2). R8 mapping must be
  archived for any shipped build (`.agents/maps/android/build.md:101-105`).
- **Open: AAR ownership.** Who owns the `kazeterm-sdk` module — KazeTerm (recommended, so
  JNI/Kotlin stay in sync) or RaikaAI (a local fork that copies the `.so`)? The latter
  risks symbol drift.
- **Open: memory limit.** `MachineOptions.memory_limit` is hardcoded to `None` in the JNI
  `start` (`hosts/android/src/lib.rs:677`); on low-RAM devices RaikaAI may want to expose
  it, which requires a new JNI entry point.
- **Open: inittab boot work.** `Native.boot` runs `/etc/inittab` sysinit/wait commands
  (`.agents/maps/kernel/boot.md:69-76`) and blocks; the exact provisioning sequence for
  Node/Pi must be timed so first-boot setup does not show a dead terminal.
- **Open: MCP parallel operation.** Keeping RaikaAI's Kotlin MCP client alongside Pi's
  `mcp__*` tools can double-register servers (`docs/PORT_PI.md:96-99,230-231`).
- **Open: tool calling in `/v1`.** The critical gap at `docs/PORT_PI.md:113-127` remains
  until the Node adapter shim lands; `ContainerRuntime.call` is a stub
  (`ToolRuntime.kt:137-146`).
