#!/bin/bash

set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  scripts/link_qwen_model_dir.sh --model-dir PATH [--gradle-properties PATH]

Validate an exported MNN-LLM model directory and write file:// URLs plus sha256 values
into gradle.properties for qwen-local-1_5b-text.
EOF
}

WORKSPACE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODEL_DIR=""
GRADLE_PROPERTIES_PATH="$WORKSPACE_ROOT/gradle.properties"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --model-dir)
      MODEL_DIR="$2"
      shift 2
      ;;
    --gradle-properties)
      GRADLE_PROPERTIES_PATH="$2"
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
)

for file in "${REQUIRED_FILES[@]}"; do
  if [[ ! -f "$MODEL_DIR/$file" ]]; then
    echo "Missing required file: $MODEL_DIR/$file" >&2
    exit 1
  fi
done

sha256_of() {
  shasum -a 256 "$1" | awk '{print $1}'
}

update_property() {
  local key="$1"
  local value="$2"
  perl -0pi -e "s#^\\Q${key}\\E=.*\$#${key}=${value}#m" "$GRADLE_PROPERTIES_PATH"
}

cp "$GRADLE_PROPERTIES_PATH" "$GRADLE_PROPERTIES_PATH.bak"

update_property "model.qwen_local_1_5b_text.tokenizer_txt_url" "file://$MODEL_DIR/tokenizer.txt"
update_property "model.qwen_local_1_5b_text.tokenizer_txt_sha256" "$(sha256_of "$MODEL_DIR/tokenizer.txt")"
update_property "model.qwen_local_1_5b_text.llm_mnn_url" "file://$MODEL_DIR/llm.mnn"
update_property "model.qwen_local_1_5b_text.llm_mnn_sha256" "$(sha256_of "$MODEL_DIR/llm.mnn")"
update_property "model.qwen_local_1_5b_text.llm_weight_url" "file://$MODEL_DIR/llm.mnn.weight"
update_property "model.qwen_local_1_5b_text.llm_weight_sha256" "$(sha256_of "$MODEL_DIR/llm.mnn.weight")"
update_property "model.qwen_local_1_5b_text.llm_config_url" "file://$MODEL_DIR/llm_config.json"
update_property "model.qwen_local_1_5b_text.llm_config_sha256" "$(sha256_of "$MODEL_DIR/llm_config.json")"
update_property "model.qwen_local_1_5b_text.config_url" "file://$MODEL_DIR/config.json"
update_property "model.qwen_local_1_5b_text.config_sha256" "$(sha256_of "$MODEL_DIR/config.json")"

echo "Linked model directory into $GRADLE_PROPERTIES_PATH"
echo "Backup written to $GRADLE_PROPERTIES_PATH.bak"
echo "Next step: source ~/.zshrc && ./gradlew :app:assembleDebug"
