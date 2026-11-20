# Bundled Linux container helpers

The proot engine ships as native libs (`app/src/main/jniLibs/<abi>/lib*.so`)
so the package manager extracts them with exec permission — executing
binaries from `filesDir` is denied (EACCES) on HyperOS / newer Android.
This requires `android:extractNativeLibs="true"`. Total ~350 KB per ABI.

Only the Alpine rootfs (~3 MB) is downloaded at install time.

## proot (user-mode chroot / bind-mount engine, no root required)

- Version: 5.1.107.96 (aarch64 + x86_64, Android NDK builds)
- Source: Termux package repository
  - https://packages.termux.dev/apt/termux-main/pool/main/p/proot/proot_5.1.107.96_aarch64.deb
  - https://packages.termux.dev/apt/termux-main/pool/main/p/proot/proot_5.1.107.96_x86_64.deb
- Upstream: https://github.com/termux/proot (patches for Android),
  original project https://proot-me.github.io/
- License: GNU GPL v2 or later (runs as a separate process; it does not
  link into RaikaAI code)

## libtalloc (proot dependency)

- Version: 2.5.0
- Source: https://packages.termux.dev/apt/termux-main/pool/main/libt/libtalloc/
- Upstream: https://talloc.samba.org/
- License: GNU LGPL v3 (shared library, dynamically linked at runtime)

## libandroid-shmem (proot dependency, SysV shm shim for Android)

- Version: 0.7
- Source: https://packages.termux.dev/apt/termux-main/pool/main/liba/libandroid-shmem/
- Upstream packaging: https://github.com/termux/termux-packages (see package
  page for license text)

## Reproducing

1. Download the three `.deb` files above for the wanted arch.
2. `ar x package.deb` then `tar -xf data.tar.xz`.
3. Rename into jniLibs flat names:
   - `usr/bin/proot` → `libproot.so` (then patchelf, see below)
   - `usr/lib/libtalloc.so.2` → `libtalloc_prt.so`
   - `usr/lib/libandroid-shmem.so` → `libshmem_prt.so`
   - `usr/libexec/proot/loader` → `libproot_loader.so`
   - `usr/libexec/proot/loader32` → `libproot_loader32.so`
4. Repoint the engine at its siblings (the Termux RUNPATH does not exist here):
   `patchelf --replace-needed libtalloc.so.2 libtalloc_prt.so libproot.so`
   `patchelf --replace-needed libandroid-shmem.so libshmem_prt.so libproot.so`
   At runtime the app additionally sets `LD_LIBRARY_PATH=<nativeLibDir>`
   and `PROOT_LOADER(_32)=<nativeLibDir>/libproot_loader*.so`.
5. Update the pinned SHA-256 hashes in
   `app/src/main/java/com/raikaai/app/core/container/ContainerManifest.kt`.

## Alpine Linux minirootfs (bundled)

`assets/rootfs/alpine-minirootfs-aarch64.bin` is the unmodified official Alpine Linux 3.21.0
minirootfs for aarch64 (https://alpinelinux.org/downloads/), renamed from `.tar.gz`. Alpine
packages carry their own licenses (mostly GPL-2.0 / MIT / BSD); see `/usr/share/licenses` inside
the environment. SHA-256 is pinned in `ContainerManifest.kt`.

## raikapty (PTY helper)

`jniLibs/*/libraikapty.so` is built from `native/raikapty` in this repository (AGPL-3.0, same as
RaikaAI) with the Rust `libc` crate (MIT/Apache-2.0).
