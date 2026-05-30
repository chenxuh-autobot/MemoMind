#!/bin/bash

set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  scripts/setup_mnn_arm64.sh [--mnn-root PATH] [--ndk PATH] [--jobs N] [--skip-build] [--verify]

Build MNN for Android arm64-v8a, copy the prebuilt files into this project, and
optionally verify the Android build.
EOF
}

WORKSPACE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MNN_ROOT="${MNN_ROOT:-$WORKSPACE_ROOT/.local/mnn-source}"
ANDROID_NDK="${ANDROID_NDK:-$HOME/Library/Android/sdk/ndk/28.2.13676358}"
JOBS="${JOBS:-$(sysctl -n hw.logicalcpu 2>/dev/null || echo 4)}"
SKIP_BUILD=0
VERIFY=0

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
    --skip-build)
      SKIP_BUILD=1
      shift
      ;;
    --verify)
      VERIFY=1
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

if [[ "$SKIP_BUILD" -eq 0 ]]; then
  "$WORKSPACE_ROOT/scripts/build_mnn_android.sh" \
    --mnn-root "$MNN_ROOT" \
    --ndk "$ANDROID_NDK" \
    --jobs "$JOBS"
fi

"$WORKSPACE_ROOT/scripts/install_mnn_prebuilt.sh" \
  --build-dir "$MNN_ROOT/project/android/build_64" \
  --project-root "$WORKSPACE_ROOT"

if [[ "$VERIFY" -eq 1 ]]; then
  pushd "$WORKSPACE_ROOT" >/dev/null
  ./gradlew :app:assembleDebug
  popd >/dev/null
fi

echo "MNN arm64 setup is ready."
