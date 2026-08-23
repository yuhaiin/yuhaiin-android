#!/usr/bin/env bash

set -euo pipefail

android_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
rust_root="${YUHAIIN_RUST_ROOT:-${android_root}/../../yuhaiin-rust}"
ndk_root="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-${ANDROID_NDK:-}}}"
android_api="${ANDROID_API:-35}"
output_root="${RUST_ANDROID_JNI_LIBS_DIR:-${android_root}/app/build/generated/rustJniLibs}"

if [[ ! -f "${rust_root}/Cargo.toml" ]]; then
  echo "Rust checkout not found: ${rust_root}" >&2
  echo "Set YUHAIIN_RUST_ROOT to /Volumes/PSSD/Documents/yuhaiin-rust." >&2
  exit 1
fi

if [[ -z "${ndk_root}" || ! -d "${ndk_root}" ]]; then
  echo "Android NDK not found; set ANDROID_NDK_HOME." >&2
  exit 1
fi

ndk_prebuilt_root="${ndk_root}/toolchains/llvm/prebuilt"
case "$(uname -s)-$(uname -m)" in
  Linux-x86_64)
    host_candidates=(linux-x86_64)
    ;;
  Darwin-arm64)
    # Some SDK installations only contain the x86_64 NDK toolchain and rely
    # on Rosetta when the host process is arm64.
    host_candidates=(darwin-arm64 darwin-x86_64)
    ;;
  Darwin-x86_64)
    host_candidates=(darwin-x86_64 darwin-arm64)
    ;;
  *)
    echo "Unsupported Android NDK host: $(uname -s)-$(uname -m)" >&2
    exit 1
    ;;
esac

host_tag=""
for candidate in "${host_candidates[@]}"; do
  if [[ -d "${ndk_prebuilt_root}/${candidate}" ]]; then
    host_tag="${candidate}"
    break
  fi
done
if [[ -z "${host_tag}" ]]; then
  echo "No compatible Android NDK host toolchain found under ${ndk_prebuilt_root}." >&2
  exit 1
fi

ndk_bin="${ndk_root}/toolchains/llvm/prebuilt/${host_tag}/bin"
target_dir="${RUST_CARGO_TARGET_DIR:-${rust_root}/target}"

build_abi() {
  local target="$1"
  local abi="$2"
  local clang="${ndk_bin}/${target}${android_api}-clang"
  local ar="${ndk_bin}/llvm-ar"
  local target_key="$(printf '%s' "${target}" | tr '[:lower:]-' '[:upper:]_')"

  [[ -x "${clang}" ]] || {
    echo "Android linker not found: ${clang}" >&2
    exit 1
  }
  [[ -x "${ar}" ]] || {
    echo "Android llvm-ar not found: ${ar}" >&2
    exit 1
  }

  rustup target add "${target}"
  env \
    "CC_${target//-/_}=${clang}" \
    "AR_${target//-/_}=${ar}" \
    "CC_${target_key}=${clang}" \
    "AR_${target_key}=${ar}" \
    "CARGO_TARGET_${target_key}_LINKER=${clang}" \
    cargo build \
      --manifest-path "${rust_root}/Cargo.toml" \
      --target-dir "${target_dir}" \
      --target "${target}" \
      --release \
      --locked \
      -p yuhaiin-android

  mkdir -p "${output_root}/${abi}"
  cp "${target_dir}/${target}/release/libyuhaiin_android.so" \
    "${output_root}/${abi}/libyuhaiin_android.so"
}

build_abi aarch64-linux-android arm64-v8a
build_abi x86_64-linux-android x86_64

echo "Rust Android JNI libraries written to ${output_root}"
