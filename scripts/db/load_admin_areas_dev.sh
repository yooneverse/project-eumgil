#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

source "$ROOT_DIR/scripts/make/lib/be-dev.sh"

ADMIN_AREAS_GEOJSON="${ADMIN_AREAS_GEOJSON:-$ROOT_DIR/.ai/LOCAL/HangJeongDong_ver20260401.geojson}"
VALIDATE_ONLY=false
SKIP_TUNNEL="${ADMIN_AREAS_SKIP_TUNNEL:-false}"
ADMIN_AREAS_TMP_CSV=""

usage() {
  cat <<'EOF'
Usage: scripts/db/load_admin_areas_dev.sh [--validate-only] [--skip-tunnel]

Loads an administrative dong GeoJSON into dev DB table:
  - admin_areas

Environment overrides:
  ADMIN_AREAS_GEOJSON=/path/to/HangJeongDong_ver20260401.geojson
  ADMIN_AREAS_SKIP_TUNNEL=true
  ENV_FILE=/path/to/.env.dev

Prerequisites:
  - psql must be installed for DB load mode.
  - .env.dev must contain POSTGRES_DB, DB_USERNAME, DB_PASSWORD.
  - K14E102T.pem must exist unless --skip-tunnel is used or a tunnel is already open.
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --validate-only)
      VALIDATE_ONLY=true
      ;;
    --skip-tunnel)
      SKIP_TUNNEL=true
      ;;
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

require_psql() {
  if ! command -v psql >/dev/null 2>&1; then
    echo "psql is required. Install PostgreSQL client tools first." >&2
    exit 1
  fi
}

psql_file_literal() {
  local path="$1"
  printf "'%s'" "${path//\'/\'\'}"
}

write_admin_areas_csv() {
  local geojson_path="$1"
  local output_csv="$2"

  python3 - "$geojson_path" "$output_csv" <<'PY'
import csv
import json
import sys

geojson_path, output_csv = sys.argv[1], sys.argv[2]


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
    gu = parts[1]
    dong = " ".join(parts[2:])
    rows.append((gu, dong, "SRID=4326;" + geometry_wkt(feature["geometry"])))

if not rows:
    fail("no Busan administrative areas found")

with open(output_csv, "w", newline="", encoding="utf-8") as file:
    writer = csv.writer(file)
    writer.writerow(["gu", "dong", "geom"])
    writer.writerows(rows)

print(f"admin area validation ok: areas={len(rows)}")
PY
}

load_into_dev_db() {
  local admin_areas_csv="$1"

  ensure_env_file
  if [ "$SKIP_TUNNEL" != "true" ]; then
    ensure_dev_tunnel
  fi
  require_psql

  local db_name db_user db_password
  db_name="$(dev_db_name)"
  db_user="$(env_value DB_USERNAME)"
  db_password="$(env_value DB_PASSWORD)"
  if [ -z "$db_user" ]; then
    db_user="$(env_value POSTGRES_USER)"
  fi
  if [ -z "$db_password" ]; then
    db_password="$(env_value POSTGRES_PASSWORD)"
  fi
  if [ -z "$db_user" ] || [ -z "$db_password" ]; then
    echo "Missing DB credentials in $ENV_FILE. Expected DB_USERNAME/DB_PASSWORD or POSTGRES_USER/POSTGRES_PASSWORD." >&2
    exit 1
  fi

  local admin_areas_csv_literal
  admin_areas_csv_literal="$(psql_file_literal "$admin_areas_csv")"

  echo "loading admin areas into dev DB: 127.0.0.1:$BE_DEV_DB_LOCAL_PORT/$db_name"
  PGPASSWORD="$db_password" psql \
    -h 127.0.0.1 \
    -p "$BE_DEV_DB_LOCAL_PORT" \
    -U "$db_user" \
    -d "$db_name" \
    -v ON_ERROR_STOP=1 \
    -v admin_areas_csv="$admin_areas_csv_literal" <<'SQL'
CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE IF NOT EXISTS admin_areas (
  area_id BIGSERIAL PRIMARY KEY,
  gu VARCHAR(50) NOT NULL,
  dong VARCHAR(50) NOT NULL,
  geom geometry(Geometry, 4326) NOT NULL
);

CREATE TEMP TABLE admin_areas_staging (
  gu TEXT NOT NULL,
  dong TEXT NOT NULL,
  geom TEXT NOT NULL
);

\copy admin_areas_staging (gu, dong, geom) FROM :admin_areas_csv WITH (FORMAT csv, HEADER true)

BEGIN;
TRUNCATE TABLE admin_areas RESTART IDENTITY;
INSERT INTO admin_areas (gu, dong, geom)
SELECT gu, dong, ST_GeomFromEWKT(geom)
FROM admin_areas_staging;
CREATE INDEX IF NOT EXISTS idx_admin_areas_gu_dong ON admin_areas (gu, dong);
CREATE INDEX IF NOT EXISTS idx_admin_areas_geom ON admin_areas USING GIST (geom);
CREATE INDEX IF NOT EXISTS idx_road_segments_geom ON road_segments USING GIST (geom);
COMMIT;
SQL
}

main() {
  local geojson_path
  geojson_path="$(abs_path "$ADMIN_AREAS_GEOJSON")"
  ADMIN_AREAS_TMP_CSV="$(mktemp)"
  trap 'rm -f "$ADMIN_AREAS_TMP_CSV"' EXIT

  write_admin_areas_csv "$geojson_path" "$ADMIN_AREAS_TMP_CSV"
  if [ "$VALIDATE_ONLY" = "true" ]; then
    return
  fi
  load_into_dev_db "$ADMIN_AREAS_TMP_CSV"
}

main "$@"
