#!/usr/bin/env bash
set -euo pipefail
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ROOT="$REPO_ROOT/FlowCockpit_Integrated_Demo"
cd "$ROOT"
# The repository-level .env is the documented default; keep support for a
# deployment-specific .env beside the integrated services. Existing shell
# variables take precedence, and the integrated file takes precedence over root.
set -a
if [[ -f "$REPO_ROOT/.env" ]]; then source "$REPO_ROOT/.env"; fi
if [[ -f "$ROOT/.env" ]]; then source "$ROOT/.env"; fi
set +a
export OLLAMA_MODEL="${OLLAMA_MODEL:-llama3.2:1b}"
exec "$ROOT/.venv/bin/python" "$ROOT/run_system.py" --host 0.0.0.0 "$@"
