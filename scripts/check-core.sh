#!/usr/bin/env bash
# Verifies a core library directory (<dir>/<abi>/libyuhaiin.so): every ABI must
# export the C ABI and JNI entry points, and ELF segments must be 16 KB aligned.
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
dir=${1:-$root/yuhaiin/core}
ndk=${ANDROID_NDK_HOME:?ANDROID_NDK_HOME is required}
tools="$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin"
for abi in arm64-v8a x86_64; do
  so="$dir/$abi/libyuhaiin.so"
  [ -f "$so" ] || { echo "missing $so" >&2; exit 1; }
  # Capture first: grep -q closing the pipe early would fail under pipefail.
  symbols=$("$tools/llvm-nm" -D --defined-only "$so")
  for sym in yh_call yh_set_host JNI_OnLoad; do
    grep -q " T $sym\$" <<<"$symbols" || { echo "$so: $sym is not exported" >&2; exit 1; }
  done
  dynamic=$("$tools/llvm-readelf" -d "$so")
  grep -q 'SONAME.*libyuhaiin.so' <<<"$dynamic" || { echo "$so: wrong SONAME" >&2; exit 1; }
  "$tools/llvm-readelf" -lW "$so" | awk '/LOAD/ { if ($NF != "0x4000") { bad=1 } } END { exit bad }' \
    || { echo "$so: LOAD segments are not 16 KB aligned" >&2; exit 1; }
done
echo "core library OK: $dir"
