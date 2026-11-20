#!/bin/sh
# Builds the PTY helper as static musl binaries and copies them into the
# app's jniLibs (shipped as libraikapty.so next to libproot.so).
# Needs: rustup target add aarch64-unknown-linux-musl x86_64-unknown-linux-musl
set -e
cd "$(dirname "$0")"
cargo build --release --target aarch64-unknown-linux-musl
cargo build --release --target x86_64-unknown-linux-musl
cp target/aarch64-unknown-linux-musl/release/raikapty ../../app/src/main/jniLibs/arm64-v8a/libraikapty.so
cp target/x86_64-unknown-linux-musl/release/raikapty ../../app/src/main/jniLibs/x86_64/libraikapty.so
echo "libraikapty.so updated"
