#!/bin/zsh
set -euo pipefail

LABEL="com.memomind.agent-bridge"
BRIDGE_DIR="$(cd "$(dirname "$0")/.." && pwd)"
LAUNCH_AGENTS_DIR="$HOME/Library/LaunchAgents"
LOG_DIR="$HOME/Library/Logs/MemoMind"
PLIST_PATH="$LAUNCH_AGENTS_DIR/$LABEL.plist"

mkdir -p "$LAUNCH_AGENTS_DIR" "$LOG_DIR"

cat > "$PLIST_PATH" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key>
  <string>$LABEL</string>
  <key>ProgramArguments</key>
  <array>
    <string>/bin/zsh</string>
    <string>-lc</string>
    <string>cd "$BRIDGE_DIR" &amp;&amp; /usr/bin/env python3 -u -m memomind_agent_bridge</string>
  </array>
  <key>WorkingDirectory</key>
  <string>$BRIDGE_DIR</string>
  <key>RunAtLoad</key>
  <true/>
  <key>KeepAlive</key>
  <true/>
  <key>StandardOutPath</key>
  <string>$LOG_DIR/bridge.stdout.log</string>
  <key>StandardErrorPath</key>
  <string>$LOG_DIR/bridge.stderr.log</string>
  <key>ProcessType</key>
  <string>Background</string>
</dict>
</plist>
PLIST

launchctl unload "$PLIST_PATH" >/dev/null 2>&1 || true
launchctl load -w "$PLIST_PATH"

echo "Installed LaunchAgent: $LABEL"
echo "Plist: $PLIST_PATH"
echo "Logs: $LOG_DIR"
