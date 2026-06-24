#!/bin/zsh
set -euo pipefail

BRIDGE_DIR="$(cd "$(dirname "$0")/.." && pwd)"

cd "$BRIDGE_DIR"

/usr/bin/env python3 - <<'PY'
from __future__ import annotations

import os
import subprocess
import time
from pathlib import Path

from memomind_agent_bridge.config import BridgeConfig

root = Path.cwd()
config = BridgeConfig.load(root)
codex_home = config.ensure_codex_bridge_home()
output_path = Path("/tmp/memomind_codex_cli_healthcheck.txt")

command = [
    config.resolved_codex_command() or config.codex_command,
    "exec",
    "--ignore-user-config",
    "--ignore-rules",
    "--disable",
    "apps",
    "--disable",
    "plugins",
    "--disable",
    "browser_use",
    "--disable",
    "in_app_browser",
    "--disable",
    "computer_use",
    "--sandbox",
    "read-only",
    "--skip-git-repo-check",
    "--ephemeral",
    "--output-last-message",
    str(output_path),
    "请只回复一行：Bridge smoke test ok",
]

env = os.environ.copy()
env["CODEX_HOME"] = str(codex_home)

print("MemoMind Codex CLI healthcheck")
print(f"command={command[0]}")
print(f"codex_home={codex_home}")

start = time.time()
process = subprocess.Popen(
    command,
    cwd="/Users/chenxuhang/MemoMind",
    stdout=subprocess.PIPE,
    stderr=subprocess.PIPE,
    text=True,
    env=env,
)
try:
    stdout, stderr = process.communicate(timeout=45)
    elapsed = round(time.time() - start, 1)
    print(f"status=completed elapsed={elapsed}s returncode={process.returncode}")
    print("stderr_tail:")
    for line in (stderr or "").splitlines()[-20:]:
        print(line)
    if output_path.exists():
        print("result:")
        print(output_path.read_text(encoding="utf-8").strip())
    else:
        print("result: <missing output file>")
except subprocess.TimeoutExpired:
    process.kill()
    stdout, stderr = process.communicate()
    elapsed = round(time.time() - start, 1)
    print(f"status=timeout elapsed={elapsed}s")
    print("stderr_tail:")
    for line in (stderr or "").splitlines()[-20:]:
        print(line)
    if output_path.exists():
        print("partial_result:")
        print(output_path.read_text(encoding="utf-8").strip())
    else:
        print("partial_result: <missing output file>")
finally:
    output_path.unlink(missing_ok=True)
PY
