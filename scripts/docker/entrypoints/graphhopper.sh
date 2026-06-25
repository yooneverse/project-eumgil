#!/bin/sh
set -eu

CONFIG_FILE="${GRAPHHOPPER_CONFIG_FILE:-/opt/graphhopper/config-runtime.yml}"
GRAPH_LOCATION="${GRAPHHOPPER_GRAPH_LOCATION:-/graphhopper/data}"

if [ ! -d "$GRAPH_LOCATION" ] || [ -z "$(find "$GRAPH_LOCATION" -mindepth 1 -maxdepth 1 2>/dev/null)" ]; then
  echo "GraphHopper graph-cache not found or empty: $GRAPH_LOCATION" >&2
  echo "Run the graphhopper-build job before starting GraphHopper runtime." >&2
  exit 1
fi

exec java ${JAVA_OPTS:-} \
  -Ddw.graphhopper.graph.location="$GRAPH_LOCATION" \
  -cp /opt/graphhopper/plugin/ieum-graphhopper-plugin.jar:/opt/graphhopper/graphhopper-web.jar:/opt/graphhopper/postgresql.jar \
  com.ssafy.e102.graphhopper.IeumGraphHopperApplication \
  server "$CONFIG_FILE"
