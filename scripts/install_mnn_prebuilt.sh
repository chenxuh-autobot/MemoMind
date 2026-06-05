#!/bin/bash

set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  scripts/install_mnn_prebuilt.sh [--build-dir PATH] [--project-root PATH]

Copy arm64-v8a MNN headers and libMNN.so from an upstream Android build output into
this project's ai/mnn/src/main/cpp/third_party/mnn directory.
EOF
}

WORKSPACE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD_DIR="${BUILD_DIR:-$WORKSPACE_ROOT/.local/mnn-source/project/android/build_64}"
PROJECT_ROOT="${PROJECT_ROOT:-$WORKSPACE_ROOT}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --build-dir)
      BUILD_DIR="$2"
      shift 2
      ;;
    --project-root)
      PROJECT_ROOT="$2"
      shift 2
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

SOURCE_INCLUDE_DIR="$BUILD_DIR/include"
SOURCE_LIB_PATH="$BUILD_DIR/lib/libMNN.so"
SOURCE_ROOT="$(cd "$BUILD_DIR/../../.." && pwd)"
if [[ ! -f "$SOURCE_LIB_PATH" && -f "$BUILD_DIR/libMNN.so" ]]; then
  SOURCE_LIB_PATH="$BUILD_DIR/libMNN.so"
fi

if [[ ! -f "$SOURCE_INCLUDE_DIR/MNN/Interpreter.hpp" && -f "$SOURCE_ROOT/include/MNN/Interpreter.hpp" ]]; then
  SOURCE_INCLUDE_DIR="$SOURCE_ROOT/include"
fi

if [[ ! -f "$SOURCE_INCLUDE_DIR/MNN/Interpreter.hpp" ]]; then
  echo "MNN headers not found under $SOURCE_INCLUDE_DIR" >&2
  exit 1
fi

if [[ ! -f "$SOURCE_LIB_PATH" ]]; then
  echo "libMNN.so not found under $BUILD_DIR" >&2
  exit 1
fi

TARGET_ROOT="$PROJECT_ROOT/ai/mnn/src/main/cpp/third_party/mnn"
TARGET_INCLUDE_DIR="$TARGET_ROOT/include"
TARGET_LIB_DIR="$TARGET_ROOT/lib/arm64-v8a"
SOURCE_LLM_INCLUDE_DIR="$SOURCE_ROOT/transformers/llm/engine/include/llm"
SOURCE_PROFILE_PATH="$BUILD_DIR/mnn_build_profile.json"
TARGET_PROFILE_PATH="$TARGET_ROOT/mnn_build_profile.json"
APP_ASSET_PROFILE_DIR="$PROJECT_ROOT/app/src/main/assets/mnn"
APP_ASSET_PROFILE_PATH="$APP_ASSET_PROFILE_DIR/mnn_build_profile.json"

mkdir -p "$TARGET_INCLUDE_DIR" "$TARGET_LIB_DIR"
rm -rf "$TARGET_INCLUDE_DIR/MNN"
cp -R "$SOURCE_INCLUDE_DIR/MNN" "$TARGET_INCLUDE_DIR/"
if [[ -d "$SOURCE_LLM_INCLUDE_DIR" ]]; then
  rm -rf "$TARGET_INCLUDE_DIR/llm"
  cp -R "$SOURCE_LLM_INCLUDE_DIR" "$TARGET_INCLUDE_DIR/"
fi
cp "$SOURCE_LIB_PATH" "$TARGET_LIB_DIR/libMNN.so"
mkdir -p "$APP_ASSET_PROFILE_DIR"
if [[ -f "$SOURCE_PROFILE_PATH" ]]; then
  cp "$SOURCE_PROFILE_PATH" "$TARGET_PROFILE_PATH"
  cp "$SOURCE_PROFILE_PATH" "$APP_ASSET_PROFILE_PATH"
fi

echo "Installed MNN prebuilt into project."
echo "Target include: $TARGET_INCLUDE_DIR/MNN"
if [[ -d "$TARGET_INCLUDE_DIR/llm" ]]; then
  echo "Target llm api: $TARGET_INCLUDE_DIR/llm"
fi
echo "Target runtime: $TARGET_LIB_DIR/libMNN.so"
if [[ -f "$TARGET_PROFILE_PATH" ]]; then
  echo "Target build profile: $TARGET_PROFILE_PATH"
fi
