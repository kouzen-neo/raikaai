# Rootfs strategy

Status: implementation note for the `feat/pi-runtime` branch. This document
records how the Linux environment obtains and verifies its rootfs, and what
happens when the optional build inputs are absent. Source references:
`scripts/fetch-rootfs`, `app/build.gradle.kts`, `ContainerManifest.kt`,
`ContainerManager.kt` and `EnvironmentScreen.kt`. No new dependencies are
introduced: downloads use the platform `HttpURLConnection`/OkHttp already in
the project, JSON is `org.json`, and work runs in existing coroutines.

## 1. Pinned artifacts

| Artifact | URL | sha256 | Bytes |
|---|---|---|---|
| Alpine minirootfs 3.21.0 (aarch64) | `https://dl-cdn.alpinelinux.org/alpine/v3.21/releases/aarch64/alpine-minirootfs-3.21.0-aarch64.tar.gz` | `f31202c4070c4ef7de9e157e1bd01cb4da3a2150035d74ea5372c5e86f1efac1` | 3,851,686 |
| Debian 13 (`trixie`, debuerreotype OCI rootfs) | `https://raw.githubusercontent.com/debuerreotype/docker-debian-artifacts/e36653f9967231373aeba7cff2102fd004ac4f4a/trixie/oci/blobs/rootfs.tar.gz` | `dfb68c024d5cdf4872bb7f0be8d63874aa6d0b7a0050f1b06169573e34ea543a` | 49,776,179 |

Both digests were computed from the actual upstream files and match the values
used by KazeTerm for Debian. KazeTerm pins a newer Alpine (3.24.3, sha256
`4d3f80afaebd447c5591a63a73bde380a149fe8b2d85488cafabb2ff97551f50`), but
RaikaAI's manifest and unit tests pin 3.21, so this repository keeps 3.21 and
uses its own verified digest. Change the constants in `ContainerManifest.kt`
together with `scripts/fetch-rootfs` when upgrading Alpine.

Debian mirrors (same list as KazeTerm, used for package sources after install;
they are not download sources for the pinned tarball):

- `deb.debian.org`
- `mirrors.tuna.tsinghua.edu.cn`
- `mirrors.ustc.edu.cn`
- `mirrors.aliyun.com`

Alpine mirrors: `https://dl-cdn.alpinelinux.org/alpine` (official),
`https://mirrors.tuna.tsinghua.edu.cn/alpine`,
`https://mirrors.ustc.edu.cn/alpine`, `https://mirrors.aliyun.com/alpine`.
A mirror probe is not implemented in this change; the official source is used.

## 2. Build-time bundle (optional)

```sh
scripts/fetch-rootfs                 # -> target/rootfs/alpine-minirootfs-3.21.0-aarch64.tar.gz
./gradlew assembleDebug              # or the release/CI build command
```

`scripts/fetch-rootfs` is POSIX `sh`: it writes to `<name>.part`, retries with
`curl --retry 3`, compares sha256 with `sha256sum` (or `shasum -a 256`), and
renames only on a match. It prints the path of each optional package first and
the tarball path last, so shell callers can take the last line. GPG is not
used: the digest is pinned, and a mismatch deletes the partial file and fails.

Optional apks from the same branch's `main/aarch64` repository may be listed in
the script's `packages` variable as `<file.apk> <sha256>`. The list is empty by
default; a package that cannot be downloaded or does not match its digest is
skipped with a warning and never breaks the fetch.

The `rootfsAsset` Gradle task copies the newest matching tarball from
`target/rootfs` to the generated asset `container/rootfs.tgz`. The `.tgz` name
is deliberate: AAPT handles a `.tar.gz` asset differently and breaks
`AssetManager.open()`. The task is wired through `androidComponents` →
`variant.sources.assets.addGeneratedSourceDirectory`, the same idiom used for
generated jniLibs/asset directories. `scripts/fetch-rootfs` output is not
committed (the repository ignores `build/`; do not add `target/` binaries).

## 3. Runtime acquisition

- **Alpine (default, unchanged API):** `ContainerManager.install()` first
  checks for the bundled `container/rootfs.tgz` asset. When present it is
  copied to the cache and re-verified against `ContainerManifest.rootfsSha256`
  before `unpackTarGz`. When absent, the pinned URL is downloaded with the
  existing verified downloader (`downloadToFile`) and unpacked. Both paths
  write a `.installed` marker with the layout schema so a format change forces
  a reinstall. PRoot command construction and bind mounts are untouched.
- **Debian 13 (opt-in):** `ContainerManager.installDebian()` calls
  `downloadDebian`, which uses `HttpURLConnection` to fetch the pinned tarball
  into `cacheDir/debian13-rootfs.tar.gz.part`, streaming a SHA-256 digest and
  progress callbacks. It rejects a wrong length or digest, deletes the partial
  file, and only then renames and unpacks into `filesDir/debian13` with the
  existing tar.gz extractor. Coroutine cancellation is checked between reads.
- The Linux Environment screen offers a two-choice selector (Alpine bundled /
  Debian download). Alpine remains selected by default. The existing
  `Downloading`/`Extracting`/`Error` install states provide progress and error
  text for both choices; switching choice cancels any running install.

## 4. Fallback matrix

| Situation | Result |
|---|---|
| No `target/rootfs` at build time | `rootfsAsset` emits no asset; the build succeeds. `ON_DEVICE` users are unaffected. |
| Bundled asset present and digest matches | Alpine installs offline; no network needed. |
| Bundled asset present but digest differs | Install fails with a clear error; re-run `scripts/fetch-rootfs` and rebuild. |
| No bundled asset, network available | Alpine is downloaded from the pinned URL and verified before unpack. |
| No bundled asset, download fails or digest differs | `InstallState.Error`; the partial file is deleted and a retry can be attempted. |
| Debian selected | ~50 MB download with pinned length + sha256; verified before unpack; failure leaves no installed rootfs. |
| Debian already unpacked | Reported by `isDebianInstalled()`; the selector keeps its state. |

## 5. Known limits

- The active PRoot runtime still uses `filesDir/alpine`; `installDebian` only
  prepares `filesDir/debian13` because changing the PRoot command construction
  or bind mounts is out of scope here. Selecting the Debian rootfs for a live
  session belongs to the KazeTerm backend migration (`docs/KAZETERM_INTEGRATION.md`
  section 3). The UI states this explicitly.
- No GPG signature check is performed; trust is anchored in the pinned sha256,
  which is compared on every acquisition path.
- `settingsRepo` keeps the existing `alpineInstalled`/`alpineVersion` flags;
  Debian installation does not change them.
- Optional apks are not consumed by the current PRoot installer, so the script
  leaves that list empty rather than shipping stale pins.
