#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
ENV_FILE="${ENV_FILE:-"$ROOT_DIR/.env.prod"}"
source "$ROOT_DIR/scripts/make/lib/prod-db-tunnel.sh"

ADMIN_AREAS_GEOJSON="${ADMIN_AREAS_GEOJSON:-$ROOT_DIR/.ai/LOCAL/HangJeongDong_ver20260401.geojson}"
GEOJSON_MOUNT_DIR=""
CONTAINER_ADMIN_AREAS_GEOJSON=""
PROD_COMPOSE=(docker compose --env-file "$ENV_FILE" -f "$ROOT_DIR/docker-compose.prod.yml")

usage() {
  cat <<'EOF'
Usage: scripts/db/load_admin_areas_prod.sh

Loads an administrative dong GeoJSON into prod DB table:
  - admin_areas

Environment overrides:
  ADMIN_AREAS_GEOJSON=/path/to/HangJeongDong_ver20260401.geojson
  PROD_GRAPHHOPPER_SSH_KEY=/path/to/busan-eumgil-S2.pem
  PROD_DB_TUNNEL_AUTO=false
  ENV_FILE=/path/to/.env.prod
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
  shift
done

abs_path() {
  local path="$1"
  if [ ! -f "$path" ]; then
    echo "Missing file: $path" >&2
    exit 1
  fi
  echo "$(cd "$(dirname "$path")" && pwd)/$(basename "$path")"
}

prepare_geojson_mount() {
  local geojson_path="$1"
  GEOJSON_MOUNT_DIR="$(dirname "$geojson_path")"
  CONTAINER_ADMIN_AREAS_GEOJSON="/admin-areas/$(basename "$geojson_path")"
}

geojson_mount_arg() {
  if command -v cygpath >/dev/null 2>&1; then
    echo "$(cygpath -w "$GEOJSON_MOUNT_DIR"):/admin-areas:ro"
    return
  fi

  echo "$GEOJSON_MOUNT_DIR:/admin-areas:ro"
}

docker_host_path() {
  local path="$1"
  if command -v cygpath >/dev/null 2>&1; then
    cygpath -w "$path"
    return
  fi

  echo "$path"
}

load_into_prod_db() {
  local volume_arg
  volume_arg="$(geojson_mount_arg)"
  local env_file_arg
  env_file_arg="$(docker_host_path "$ENV_FILE")"

  "$ROOT_DIR/scripts/make/docker/prod-schema-update.sh"
  resolve_prod_db_url
  "${PROD_COMPOSE[@]}" --profile graphhopper-build build graphhopper-build

  MSYS_NO_PATHCONV=1 \
  docker run --rm -i \
    --env-file "$env_file_arg" \
    -e "DB_URL=$PROD_DB_URL" \
    -v "$volume_arg" \
    --entrypoint python3 \
    "s14p31e102-prod-graphhopper:${GRAPHHOPPER_IMAGE_TAG:-latest}" \
    - "$CONTAINER_ADMIN_AREAS_GEOJSON" <<'PY'
import csv
import io
import json
import os
import sys

import psycopg2

geojson_path = sys.argv[1]


def fail(message):
    print(f"admin area validation failed: {message}", file=sys.stderr)
    raise SystemExit(1)


def close_ring(ring):
    points = [(float(point[0]), float(point[1])) for point in ring]
    if len(points) < 4:
        fail("polygon ring must contain at least 4 points")
    if points[0] != points[-1]:
        points.append(points[0])
    return points


def format_point(point):
    return f"{point[0]:.8f} {point[1]:.8f}"


def polygon_wkt(coordinates):
    rings = []
    for ring in coordinates:
        rings.append("(" + ", ".join(format_point(point) for point in close_ring(ring)) + ")")
    return "POLYGON(" + ", ".join(rings) + ")"


def geometry_wkt(geometry):
    geom_type = geometry.get("type")
    coordinates = geometry.get("coordinates")
    if geom_type == "Polygon":
        return polygon_wkt(coordinates)
    if geom_type == "MultiPolygon":
        polygons = []
        for polygon in coordinates:
            rings = []
            for ring in polygon:
                rings.append("(" + ", ".join(format_point(point) for point in close_ring(ring)) + ")")
            polygons.append("(" + ", ".join(rings) + ")")
        return "MULTIPOLYGON(" + ", ".join(polygons) + ")"
    fail(f"unsupported geometry type: {geom_type}")


with open(geojson_path, encoding="utf-8") as file:
    data = json.load(file)

rows = []
for feature in data.get("features", []):
    properties = feature.get("properties", {})
    adm_nm = str(properties.get("adm_nm", ""))
    parts = adm_nm.split()
    if len(parts) < 3 or parts[0] != "부산광역시":
        continue
    rows.append((parts[1], " ".join(parts[2:]), "SRID=4326;" + geometry_wkt(feature["geometry"])))

if not rows:
    fail("no Busan administrative areas found")

csv_buffer = io.StringIO()
writer = csv.writer(csv_buffer)
writer.writerow(["gu", "dong", "geom"])
writer.writerows(rows)
csv_buffer.seek(0)

db_url = os.environ.get("DB_URL")
if not db_url:
    fail("DB_URL is required")

print(f"loading admin areas into prod DB: areas={len(rows)}", flush=True)
with psycopg2.connect(db_url) as connection:
    with connection.cursor() as cursor:
        cursor.execute("CREATE EXTENSION IF NOT EXISTS postgis")
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS admin_areas (
              area_id BIGSERIAL PRIMARY KEY,
              gu VARCHAR(50) NOT NULL,
              dong VARCHAR(50) NOT NULL,
              geom geometry(Geometry, 4326) NOT NULL
            )
        """)
        cursor.execute("""
            CREATE TEMP TABLE admin_areas_staging (
              gu TEXT NOT NULL,
              dong TEXT NOT NULL,
              geom TEXT NOT NULL
            ) ON COMMIT DROP
        """)
        cursor.copy_expert(
            "COPY admin_areas_staging (gu, dong, geom) FROM STDIN WITH (FORMAT csv, HEADER true)",
            csv_buffer,
        )
        cursor.execute("TRUNCATE TABLE admin_areas RESTART IDENTITY")
        cursor.execute("""
            INSERT INTO admin_areas (gu, dong, geom)
            SELECT gu, dong, ST_GeomFromEWKT(geom)
            FROM admin_areas_staging
        """)
        cursor.execute("CREATE INDEX IF NOT EXISTS idx_admin_areas_gu_dong ON admin_areas (gu, dong)")
        cursor.execute("CREATE INDEX IF NOT EXISTS idx_admin_areas_geom ON admin_areas USING GIST (geom)")
        cursor.execute("CREATE INDEX IF NOT EXISTS idx_road_segments_geom ON road_segments USING GIST (geom)")
print("admin area load complete", flush=True)
PY
}

main() {
  local geojson_path
  geojson_path="$(abs_path "$ADMIN_AREAS_GEOJSON")"
  prepare_geojson_mount "$geojson_path"
  load_into_prod_db
}

main "$@"
