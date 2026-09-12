#!/usr/bin/env bash
# Build libDigitalVibrancy.dylib (native macOS NSVisualEffectView / NSGlassEffectView bridge).
# Produces modern/dist/native/libDigitalVibrancy.dylib.
set -euo pipefail
IFS=$'\n\t'

HERE="$(cd "$(dirname "$0")" && pwd)"
# HERE = .../modern/native/vibrancy ; project root is three levels up.
ROOT="$(cd "$HERE/../../.." && pwd)"
OUT="$ROOT/modern/dist/native"

[[ "$(uname -s)" == "Darwin" ]] || {
    echo "skip: libDigitalVibrancy.dylib is macOS-only" >&2
    mkdir -p "$OUT"
    exit 0
}

command -v clang >/dev/null || { echo "clang not found" >&2; exit 1; }
command -v xcrun >/dev/null || { echo "xcrun not found" >&2; exit 1; }

SDK="$(xcrun --show-sdk-path)"
mkdir -p "$OUT"

c_red='\033[0;31m'; c_grn='\033[0;32m'; c_dim='\033[2m'; c_off='\033[0m'
log() { printf "  ${c_dim}·${c_off} %s\n" "$*"; }
ok()  { printf "${c_grn}✓${c_off} %s\n" "$*"; }

log "compiling libDigitalVibrancy.dylib (ObjC, ARC, modules)..."
clang -dynamiclib -O2 \
    -fobjc-arc -fmodules \
    -isysroot "$SDK" \
    -mmacosx-version-min=13.0 \
    -framework Cocoa \
    -Wl,-install_name,@rpath/libDigitalVibrancy.dylib \
    -o "$OUT/libDigitalVibrancy.dylib" \
    "$HERE/Vibrancy.m"

# ad-hoc sign so the dylib loads inside a hardened-runtime app on Apple Silicon.
if command -v codesign >/dev/null; then
    codesign --force --sign - "$OUT/libDigitalVibrancy.dylib" >/dev/null 2>&1 || true
fi

ok "modern/dist/native/libDigitalVibrancy.dylib built"
