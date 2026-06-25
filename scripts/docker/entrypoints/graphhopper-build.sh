#!/bin/sh
set -eu

CONFIG_FILE="${GRAPHHOPPER_BUILD_CONFIG_FILE:-/opt/graphhopper/config-build.yml}"
OSM_IMPORT_FILE="${GRAPHHOPPER_OSM_IMPORT_FILE:-/graphhopper/import/road-network.osm}"
PBF_IMPORT_FILE="${GRAPHHOPPER_PBF_IMPORT_FILE:-${GRAPHHOPPER_IMPORT_FILE:-/graphhopper/import/road-network.osm.pbf}}"
VALIDATION_REPORT_FILE="${GRAPHHOPPER_VALIDATION_REPORT_FILE:-/graphhopper/import/road-network-validation-report.json}"
PBF_REPORT_FILE="${GRAPHHOPPER_PBF_REPORT_FILE:-/graphhopper/import/road-network-pbf-report.json}"
BUILD_LOCATION="${GRAPHHOPPER_BUILD_LOCATION:-/graphhopper/build-cache}"
GRAPH_LOCATION="${GRAPHHOPPER_GRAPH_LOCATION:-/graphhopper/data}"
IMPORT_TIMEOUT_SECONDS="${GRAPHHOPPER_IMPORT_TIMEOUT_SECONDS:-1800}"
OSMIUM_BIN="${OSMIUM_BIN:-osmium}"
CACHE_FINGERPRINT="${GRAPHHOPPER_CACHE_FINGERPRINT:-}"
CACHE_FINGERPRINT_FILE="${GRAPHHOPPER_CACHE_FINGERPRINT_FILE:-$GRAPH_LOCATION/.ieum-graphhopper-cache-fingerprint}"
CACHE_TIMESTAMP_FILE="${GRAPHHOPPER_CACHE_TIMESTAMP_FILE:-$GRAPH_LOCATION/.ieum-graphhopper-cache-built-at}"

write_pbf_report() {
  python3 - "$PBF_REPORT_FILE" "$OSM_IMPORT_FILE" "$PBF_IMPORT_FILE" "$OSMIUM_BIN" <<'PY'
from datetime import datetime, timezone
import json
import os
import sys

report_path, osm_path, pbf_path, osmium_bin = sys.argv[1:5]
report = {
    "generatedAt": datetime.now(timezone.utc).isoformat(),
    "status": "PASS",
    "osmiumBin": osmium_bin,
    "input": {
        "path": osm_path,
        "bytes": os.path.getsize(osm_path),
    },
    "output": {
        "path": pbf_path,
        "bytes": os.path.getsize(pbf_path),
    },
}
os.makedirs(os.path.dirname(os.path.abspath(report_path)), exist_ok=True)
with open(report_path, "w", encoding="utf-8") as report_file:
    json.dump(report, report_file, ensure_ascii=False, indent=2)
    report_file.write("\n")
PY
}

mkdir -p "$(dirname "$OSM_IMPORT_FILE")" "$(dirname "$PBF_IMPORT_FILE")" "$GRAPH_LOCATION"
mkdir -p "$(dirname "$VALIDATION_REPORT_FILE")"
mkdir -p "$(dirname "$PBF_REPORT_FILE")"
mkdir -p "$BUILD_LOCATION"
find "$BUILD_LOCATION" -mindepth 1 -maxdepth 1 -exec rm -rf {} +

echo "Exporting PostgreSQL road network to OSM XML: $OSM_IMPORT_FILE"
python3 /usr/local/bin/export-postgis-to-osm.py \
  --output "$OSM_IMPORT_FILE" \
  --report-json "$VALIDATION_REPORT_FILE"

if [ ! -s "$OSM_IMPORT_FILE" ]; then
  echo "GraphHopper OSM import file is empty: $OSM_IMPORT_FILE" >&2
  exit 1
fi

if ! command -v "$OSMIUM_BIN" >/dev/null 2>&1; then
  echo "osmium is required to build GraphHopper PBF input. Set OSMIUM_BIN or install osmium-tool." >&2
  exit 1
fi

echo "Converting OSM XML to PBF: $PBF_IMPORT_FILE"
"$OSMIUM_BIN" cat "$OSM_IMPORT_FILE" -o "$PBF_IMPORT_FILE" --overwrite

if [ ! -s "$PBF_IMPORT_FILE" ]; then
  echo "GraphHopper PBF import file is empty: $PBF_IMPORT_FILE" >&2
  exit 1
fi
write_pbf_report

echo "Building GraphHopper graph-cache at: $BUILD_LOCATION"
set +e
timeout "$IMPORT_TIMEOUT_SECONDS" java ${JAVA_OPTS:-} \
  -Ddw.graphhopper.datareader.file="$PBF_IMPORT_FILE" \
  -Ddw.graphhopper.graph.location="$BUILD_LOCATION" \
  -cp /opt/graphhopper/plugin/ieum-graphhopper-plugin.jar:/opt/graphhopper/graphhopper-web.jar:/opt/graphhopper/postgresql.jar \
  com.ssafy.e102.graphhopper.IeumGraphHopperApplication \
  server "$CONFIG_FILE" &
java_pid="$!"

ready=0
for _ in $(seq 1 "$IMPORT_TIMEOUT_SECONDS"); do
  if curl -fsS http://127.0.0.1:8990/healthcheck >/dev/null 2>&1; then
    ready=1
    break
  fi
  if ! kill -0 "$java_pid" >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

kill "$java_pid" >/dev/null 2>&1 || true
wait "$java_pid" >/dev/null 2>&1
java_status="$?"
set -e

if [ "$ready" -ne 1 ] && [ "$java_status" -ne 124 ]; then
  echo "GraphHopper import process did not become healthy. status=$java_status" >&2
  exit 1
fi

if [ ! -d "$BUILD_LOCATION" ] || [ -z "$(find "$BUILD_LOCATION" -mindepth 1 -maxdepth 1 2>/dev/null)" ]; then
  echo "GraphHopper graph-cache build produced no files: $BUILD_LOCATION" >&2
  exit 1
fi

echo "Publishing graph-cache to runtime location: $GRAPH_LOCATION"
PREVIOUS_LOCATION="${GRAPHHOPPER_PREVIOUS_GRAPH_LOCATION:-/graphhopper/previous-cache}"
mkdir -p "$PREVIOUS_LOCATION"
find "$PREVIOUS_LOCATION" -mindepth 1 -maxdepth 1 -exec rm -rf {} +
if [ -d "$GRAPH_LOCATION" ] && [ -n "$(find "$GRAPH_LOCATION" -mindepth 1 -maxdepth 1 2>/dev/null)" ]; then
  cp -a "$GRAPH_LOCATION"/. "$PREVIOUS_LOCATION"/
fi
find "$GRAPH_LOCATION" -mindepth 1 -maxdepth 1 -exec rm -rf {} +
cp -a "$BUILD_LOCATION"/. "$GRAPH_LOCATION"/

if [ -n "$CACHE_FINGERPRINT" ]; then
  printf '%s\n' "$CACHE_FINGERPRINT" > "$CACHE_FINGERPRINT_FILE"
fi
date -u +"%Y-%m-%dT%H:%M:%SZ" > "$CACHE_TIMESTAMP_FILE"

echo "GraphHopper graph-cache build completed."
