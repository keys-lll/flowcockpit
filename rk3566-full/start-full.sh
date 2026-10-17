#!/usr/bin/env bash
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../FlowCockpit_Integrated_Demo" && pwd)"
cd "$ROOT"
if [[ -f .env ]]; then set -a; source .env; set +a; fi
exec "$ROOT/.venv/bin/python" "$ROOT/run_system.py" --host 0.0.0.0 "$@"
