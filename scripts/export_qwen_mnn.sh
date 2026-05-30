#!/bin/bash

set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  scripts/export_qwen_mnn.sh --model-path PATH [--dst-path PATH] [--quant-bit 4] [--quant-block 128] [--mnn-root PATH]

Run the official MNN llmexport.py tool to export a local Qwen-family model into an
MNN-LLM package directory.
EOF
}

WORKSPACE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODEL_PATH=""
DST_PATH="$WORKSPACE_ROOT/.local/exported-qwen-mnn"
QUANT_BIT="4"
QUANT_BLOCK="128"
MNN_ROOT="$WORKSPACE_ROOT/.local/mnn-source"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --model-path)
      MODEL_PATH="$2"
      shift 2
      ;;
    --dst-path)
      DST_PATH="$2"
      shift 2
      ;;
    --quant-bit)
      QUANT_BIT="$2"
      shift 2
      ;;
    --quant-block)
      QUANT_BLOCK="$2"
      shift 2
      ;;
    --mnn-root)
      MNN_ROOT="$2"
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

if [[ -z "$MODEL_PATH" ]]; then
  echo "--model-path is required." >&2
  usage >&2
  exit 1
fi

MODEL_PATH="$(cd "$MODEL_PATH" && pwd)"
DST_PATH="$(mkdir -p "$DST_PATH" && cd "$DST_PATH" && pwd)"

LLMEXPORT_PATH="$MNN_ROOT/transformers/llm/export/llmexport.py"
if [[ ! -f "$LLMEXPORT_PATH" ]]; then
  echo "llmexport.py not found: $LLMEXPORT_PATH" >&2
  echo "Run scripts/setup_mnn_arm64.sh first or pass --mnn-root PATH." >&2
  exit 1
fi

MNNCONVERT_PATH="$(find "$MNN_ROOT" -maxdepth 4 -type f -name MNNConvert 2>/dev/null | head -n 1 || true)"

if ! command -v python3 >/dev/null 2>&1; then
  echo "python3 not found. Install Python 3 first." >&2
  exit 1
fi

CMD=(
  python3
  "$LLMEXPORT_PATH"
  --path "$MODEL_PATH"
  --dst_path "$DST_PATH"
  --export mnn
  --quant_bit "$QUANT_BIT"
  --quant_block "$QUANT_BLOCK"
)

if [[ -n "$MNNCONVERT_PATH" ]]; then
  CMD+=(--mnnconvert "$MNNCONVERT_PATH")
fi

printf 'Running export:\n'
printf '  %q' "${CMD[@]}"
printf '\n'

"${CMD[@]}"

echo "Export completed: $DST_PATH"
echo "If the output contains tokenizer.txt, llm.mnn, llm.mnn.weight, llm_config.json, and config.json,"
echo "you can link it into the app with:"
echo "  scripts/link_qwen_model_dir.sh --model-dir $DST_PATH"
