#!/bin/bash

set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  scripts/build_mnn_android.sh [--mnn-root PATH] [--ndk PATH] [--jobs N] [--skip-clone]

Build the official MNN Android runtime for arm64-v8a and install headers/libs into
the MNN build directory using the upstream Android build script.
EOF
}

WORKSPACE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MNN_ROOT="${MNN_ROOT:-$WORKSPACE_ROOT/.local/mnn-source}"
ANDROID_NDK="${ANDROID_NDK:-$HOME/Library/Android/sdk/ndk/28.2.13676358}"
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}"
JOBS="${JOBS:-$(sysctl -n hw.logicalcpu 2>/dev/null || echo 4)}"
SKIP_CLONE=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --mnn-root)
      MNN_ROOT="$2"
      shift 2
      ;;
    --ndk)
      ANDROID_NDK="$2"
      shift 2
      ;;
    --jobs)
      JOBS="$2"
      shift 2
      ;;
    --skip-clone)
      SKIP_CLONE=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

if ! command -v cmake >/dev/null 2>&1; then
  SDK_CMAKE_BIN="$(find "$ANDROID_SDK_ROOT/cmake" -maxdepth 3 -type f -name cmake 2>/dev/null | sort | tail -n 1 | xargs dirname 2>/dev/null || true)"
  if [[ -n "${SDK_CMAKE_BIN:-}" && -x "$SDK_CMAKE_BIN/cmake" ]]; then
    export PATH="$SDK_CMAKE_BIN:$PATH"
  fi
fi

if ! command -v cmake >/dev/null 2>&1; then
  echo "cmake not found. Install Android SDK CMake or add cmake to PATH." >&2
  exit 1
fi

if [[ ! -d "$ANDROID_NDK" ]]; then
  echo "ANDROID_NDK not found: $ANDROID_NDK" >&2
  echo "Install Android NDK first or pass --ndk PATH." >&2
  exit 1
fi

if [[ ! -f "$MNN_ROOT/project/android/build_64.sh" ]]; then
  if [[ "$SKIP_CLONE" -eq 1 ]]; then
    echo "MNN source not found under $MNN_ROOT and --skip-clone was set." >&2
    exit 1
  fi
  mkdir -p "$(dirname "$MNN_ROOT")"
  rm -rf "$MNN_ROOT"
  git clone --depth=1 https://github.com/alibaba/MNN.git "$MNN_ROOT"
fi

BUILD_DIR="$MNN_ROOT/project/android/build_64"
mkdir -p "$BUILD_DIR"

DEFAULT_FLAGS="-DMNN_LOW_MEMORY=true -DMNN_CPU_WEIGHT_DEQUANT_GEMM=true -DMNN_BUILD_LLM=true -DMNN_SUPPORT_TRANSFORMER_FUSE=true -DMNN_ARM82=true -DMNN_USE_LOGCAT=true -DMNN_OPENCL=true -DLLM_SUPPORT_VISION=true -DMNN_BUILD_OPENCV=true -DMNN_IMGCODECS=true -DLLM_SUPPORT_AUDIO=true -DMNN_BUILD_AUDIO=true -DMNN_BUILD_DIFFUSION=ON -DMNN_SEP_BUILD=OFF -DMNN_BUILD_TEST=OFF -DMNN_BUILD_BENCHMARK=OFF -DCMAKE_SHARED_LINKER_FLAGS='-Wl,-z,max-page-size=16384' -DCMAKE_INSTALL_PREFIX=."

pushd "$BUILD_DIR" >/dev/null
export ANDROID_NDK
"../build_64.sh" "$DEFAULT_FLAGS"
make -j"$JOBS"
make install
popd >/dev/null

HEADER_PATH="$BUILD_DIR/include/MNN/Interpreter.hpp"
HEADER_ROOT="$BUILD_DIR/include"
if [[ ! -f "$HEADER_PATH" && -f "$MNN_ROOT/include/MNN/Interpreter.hpp" ]]; then
  HEADER_PATH="$MNN_ROOT/include/MNN/Interpreter.hpp"
  HEADER_ROOT="$MNN_ROOT/include"
fi

LIB_PATH="$BUILD_DIR/lib/libMNN.so"
if [[ ! -f "$LIB_PATH" && -f "$BUILD_DIR/libMNN.so" ]]; then
  LIB_PATH="$BUILD_DIR/libMNN.so"
fi

if [[ ! -f "$HEADER_PATH" ]]; then
  echo "MNN header not found after build: $HEADER_PATH" >&2
  exit 1
fi

if [[ ! -f "$LIB_PATH" ]]; then
  echo "libMNN.so not found after build under $BUILD_DIR" >&2
  exit 1
fi

echo "MNN build completed."
echo "MNN source root: $MNN_ROOT"
echo "Header root: $HEADER_ROOT"
echo "Runtime library: $LIB_PATH"
