#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../FlowCockpit_Integrated_Demo" && pwd)"
cd "$ROOT"
if [[ -f .env ]]; then
  set -a
  source .env
  set +a
fi
export OLLAMA_MODEL="${OLLAMA_MODEL:-llama3.2:1b}"
exec "$ROOT/.venv/bin/python" "$ROOT/run_system.py" --host 0.0.0.0 "$@"
