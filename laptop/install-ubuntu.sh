#!/usr/bin/env bash
set -euo pipefail

# Ubuntu 26.04 may ship a newer system Python than the AI dependencies support.
# Use a separate managed CPython 3.12 without changing /usr/bin/python3.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../FlowCockpit_Integrated_Demo" && pwd)"
sudo apt-get update
sudo apt-get install -y python3 python3-venv pipx ffmpeg build-essential curl ca-certificates

UV="$HOME/.local/bin/uv"
if [[ ! -x "$UV" ]]; then
  pipx install uv
fi
if [[ ! -x "$UV" ]]; then
  echo "uv not found at $UV; check pipx installation." >&2
  exit 1
fi

"$UV" python install 3.12
if [[ -f "$ROOT/.venv/pyvenv.cfg" ]]; then
  if ! "$ROOT/.venv/bin/python" -c 'import sys; assert sys.version_info[:2] == (3, 12)'; then
    echo "Existing .venv is not Python 3.12. Rename or remove it before retrying." >&2
    exit 1
  fi
else
  "$UV" venv --python 3.12 "$ROOT/.venv"
fi
"$UV" pip install --python "$ROOT/.venv/bin/python" -r "$ROOT/requirements.txt"
echo "Ubuntu laptop Python 3.12 environment ready."
echo "Install Ollama separately and run: ollama pull llama3.2:1b"
