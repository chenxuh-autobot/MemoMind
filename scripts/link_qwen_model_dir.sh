#!/bin/bash

set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  scripts/link_qwen_model_dir.sh --model-dir PATH

Validate a Qwen3-VL-2B MNN-LLM export directory for MemoMind's single-model setup.
EOF
}

WORKSPACE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODEL_DIR=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --model-dir)
      MODEL_DIR="$2"
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

if [[ -z "$MODEL_DIR" ]]; then
  echo "--model-dir is required." >&2
  usage >&2
  exit 1
fi

MODEL_DIR="$(cd "$MODEL_DIR" && pwd)"
if [[ ! -d "$MODEL_DIR" ]]; then
  echo "Model directory not found: $MODEL_DIR" >&2
  exit 1
fi

REQUIRED_FILES=(
  "tokenizer.txt"
  "llm.mnn"
  "llm.mnn.weight"
  "llm_config.json"
  "config.json"
  "visual.mnn"
  "visual.mnn.weight"
)

for file in "${REQUIRED_FILES[@]}"; do
  if [[ ! -f "$MODEL_DIR/$file" ]]; then
    echo "Missing required file: $MODEL_DIR/$file" >&2
    exit 1
  fi
done

echo "Model directory looks complete for qwen-vl-2b-instruct-mnn:"
for file in "${REQUIRED_FILES[@]}"; do
  echo "  - $file"
done
echo
echo "MemoMind now uses a single-model setup."
echo "Next step: place or sync these files into the app model directory used by qwen-vl-2b-instruct-mnn,"
echo "then run: source ~/.zshrc && ./gradlew :app:assembleDebug"
