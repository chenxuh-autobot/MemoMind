#!/bin/zsh
set -euo pipefail

LABEL="com.memomind.agent-bridge"
PLIST_PATH="$HOME/Library/LaunchAgents/$LABEL.plist"

if [[ -f "$PLIST_PATH" ]]; then
  launchctl unload "$PLIST_PATH" >/dev/null 2>&1 || true
  rm -f "$PLIST_PATH"
  echo "Removed LaunchAgent: $LABEL"
else
  echo "LaunchAgent not installed: $LABEL"
fi
