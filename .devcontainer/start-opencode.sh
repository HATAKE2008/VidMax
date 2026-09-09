#!/usr/bin/env bash
set -e
# Mobile theke control korar jonno: Codespace terminal e ei script chalao
#   bash .devcontainer/start-opencode.sh
# Tarpor PORTS tab e 4096 -> Visibility Private -> Globe icon / Forwarded URL mobile browser e kholo
export PATH="$HOME/.opencode/bin:$PATH"
if ! command -v opencode >/dev/null 2>&1; then
  echo "opencode not found, installing..."
  curl -fsSL https://opencode.ai/install | bash
  export PATH="$HOME/.opencode/bin:$PATH"
fi
echo "Starting opencode web on 0.0.0.0:4096 ..."
exec opencode web --hostname 0.0.0.0 --port 4096
