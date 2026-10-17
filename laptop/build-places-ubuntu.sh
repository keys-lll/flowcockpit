#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../FlowCockpit_Integrated_Demo" && pwd)"
cd "$ROOT"
"$ROOT/.venv/bin/python" -m pip install -r bc/requirements-data.txt
mkdir -p bc/data
if [[ ! -s bc/data/taiwan.osm.pbf ]]; then
  curl -fL --retry 3 --output bc/data/taiwan.osm.pbf https://download.geofabrik.de/asia/taiwan-latest.osm.pbf
fi
"$ROOT/.venv/bin/python" bc/build_places.py bc/data/taiwan.osm.pbf --source-url https://download.geofabrik.de/asia/taiwan-latest.osm.pbf
