#!/usr/bin/env bash
# Builds libyuhaiin.so from a local checkout of github.com/yuhaiin/yuhaiin into
# yuhaiin/core/<abi>/, where Gradle picks it up. Usage:
#   scripts/build-core.sh [path/to/yuhaiin]    (default: ../yuhaiin)
# Needs the Go toolchain and ANDROID_NDK_HOME (NDK r28+ recommended).
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
core=${1:-$root/../yuhaiin}
: "${ANDROID_NDK_HOME:?ANDROID_NDK_HOME must point to the Android NDK}"
make -C "$core" yuhaiin_android_so ANDROID_DIST="$root/yuhaiin/core"
"$root/scripts/check-core.sh" "$root/yuhaiin/core"
