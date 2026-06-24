#!/bin/zsh
set -euo pipefail

LABEL="com.memomind.agent-bridge"
BRIDGE_DIR="$(cd "$(dirname "$0")/.." && pwd)"
LOG_DIR="$HOME/Library/Logs/MemoMind"

echo "== launchctl =="
launchctl print "gui/$(id -u)/$LABEL" 2>/dev/null || echo "LaunchAgent not loaded"
echo
echo "== doctor =="
(
  cd "$BRIDGE_DIR"
  /usr/bin/env python3 -m memomind_agent_bridge doctor || true
)
echo
echo "== recent bridge tasks =="
(
  cd "$BRIDGE_DIR"
  /usr/bin/env python3 -m memomind_agent_bridge status || true
)
echo
echo "== recent stdout =="
tail -n 40 "$LOG_DIR/bridge.stdout.log" 2>/dev/null || echo "No stdout log yet"
echo
echo "== recent stderr =="
tail -n 40 "$LOG_DIR/bridge.stderr.log" 2>/dev/null || echo "No stderr log yet"
