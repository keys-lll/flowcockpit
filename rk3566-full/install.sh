#!/usr/bin/env bash
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../FlowCockpit_Integrated_Demo" && pwd)"
echo 'FlowCockpit FULL RK3566 install (no modules disabled)'
echo "Architecture: $(uname -m)"
if [ "$(uname -m)" != aarch64 ]; then echo 'WARNING: expected ARM64 RK3566.'; fi
sudo apt-get update
sudo apt-get install -y python3 python3-venv python3-pip build-essential pkg-config libffi-dev libssl-dev ffmpeg curl ca-certificates
python3 -m venv "$ROOT/.venv"
"$ROOT/.venv/bin/python" -m pip install --upgrade pip wheel setuptools
"$ROOT/.venv/bin/python" -m pip install -r "$ROOT/requirements.txt"
echo 'Python dependencies installed. Native ARM64 wheels (esp. faster-whisper/ctranslate2) depend on availability.'
echo 'Ollama requires a separate ARM64 installation; see README.'
echo 'Place database at FlowCockpit_Integrated_Demo/bc/data/taiwan.sqlite3; see README.'
