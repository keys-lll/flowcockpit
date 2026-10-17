#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../FlowCockpit_Integrated_Demo" && pwd)"
sudo apt-get update
sudo apt-get install -y python3 python3-venv python3-pip ffmpeg build-essential curl ca-certificates
python3 -m venv "$ROOT/.venv"
"$ROOT/.venv/bin/python" -m pip install --upgrade pip
"$ROOT/.venv/bin/python" -m pip install -r "$ROOT/requirements.txt"
echo "Python dependencies installed. Install Ollama separately and pull llama3.2:1b."
