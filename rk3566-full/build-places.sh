#!/usr/bin/env bash
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../FlowCockpit_Integrated_Demo" && pwd)"
mkdir -p "$ROOT/bc/data"
"$ROOT/.venv/bin/python" -m pip install -r "$ROOT/bc/requirements-data.txt"
if [[ ! -f "$ROOT/bc/data/taiwan.osm.pbf" ]]; then
  curl -fL --retry 3 -o "$ROOT/bc/data/taiwan.osm.pbf" https://download.geofabrik.de/asia/taiwan-latest.osm.pbf
fi
"$ROOT/.venv/bin/python" "$ROOT/bc/build_places.py" "$ROOT/bc/data/taiwan.osm.pbf" --source-url https://download.geofabrik.de/asia/taiwan-latest.osm.pbf
