#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
ENV_FILE="${ENV_FILE:-"$ROOT_DIR/.env.prod"}"
source "$ROOT_DIR/scripts/make/lib/prod-db-tunnel.sh"

PLACES_CSV="${PLACES_CSV:-$ROOT_DIR/.ai/LOCAL/places_erd.csv}"
PLACE_ACCESSIBILITY_FEATURES_CSV="${PLACE_ACCESSIBILITY_FEATURES_CSV:-$ROOT_DIR/.ai/LOCAL/place_accessibility_features_erd.csv}"
CSV_MOUNT_DIR=""
CONTAINER_PLACES_CSV=""
CONTAINER_FEATURES_CSV=""
PROD_COMPOSE=(docker compose --env-file "$ENV_FILE" -f "$ROOT_DIR/docker-compose.prod.yml")

usage() {
  cat <<'EOF'
Usage: scripts/db/load_places_prod.sh

Loads facility seed CSV files into prod DB tables:
  - places
  - place_accessibility_features

Environment overrides:
  PLACES_CSV=/path/to/places_erd.csv
  PLACE_ACCESSIBILITY_FEATURES_CSV=/path/to/place_accessibility_features_erd.csv
  PROD_GRAPHHOPPER_SSH_KEY=/path/to/busan-eumgil-S2.pem
  PROD_DB_TUNNEL_AUTO=false
  ENV_FILE=/path/to/.env.prod

Notes:
  - ramp and stepFree are normalized to accessibleEntrance.
  - Existing features for staged placeIds are replaced.
  - Existing places are upserted; unrelated places are not pruned.
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

prepare_csv_mount() {
  local places_csv="$1"
  local features_csv="$2"
  local places_dir features_dir
  places_dir="$(dirname "$places_csv")"
  features_dir="$(dirname "$features_csv")"

  if [ "$places_dir" != "$features_dir" ]; then
    echo "PLACES_CSV and PLACE_ACCESSIBILITY_FEATURES_CSV must be in the same directory for prod Docker load." >&2
    exit 1
  fi

  CSV_MOUNT_DIR="$places_dir"
  CONTAINER_PLACES_CSV="/places/$(basename "$places_csv")"
  CONTAINER_FEATURES_CSV="/places/$(basename "$features_csv")"
}

csv_mount_arg() {
  if command -v cygpath >/dev/null 2>&1; then
    echo "$(cygpath -w "$CSV_MOUNT_DIR"):/places:ro"
    return
  fi

  echo "$CSV_MOUNT_DIR:/places:ro"
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
  volume_arg="$(csv_mount_arg)"
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
    - "$CONTAINER_PLACES_CSV" "$CONTAINER_FEATURES_CSV" <<'PY'
import csv
import os
import sys
from collections import Counter, defaultdict

import psycopg2

places_path, features_path = sys.argv[1], sys.argv[2]

PLACE_HEADER = ["placeId", "name", "category", "address", "point", "providerPlaceId"]
FEATURE_HEADER = ["id", "placeId", "featureType", "isAvailable"]
ALLOWED_CATEGORIES = {
    "FOOD_CAFE",
    "TOURIST_SPOT",
    "ACCOMMODATION",
    "HEALTHCARE",
    "WELFARE",
    "PUBLIC_OFFICE",
    "ETC",
}
ALLOWED_FEATURES = {
    "accessibleEntrance",
    "elevator",
    "accessibleToilet",
    "accessibleParking",
    "chargingStation",
    "accessibleRoom",
    "guidanceFacility",
}
FEATURE_ALIASES = {"ramp": "accessibleEntrance", "stepFree": "accessibleEntrance"}


def fail(message):
    print(f"place CSV validation failed: {message}", file=sys.stderr)
    raise SystemExit(1)


def require_header(path, expected):
    with open(path, newline="", encoding="utf-8-sig") as file:
        reader = csv.DictReader(file)
        if reader.fieldnames != expected:
            fail(f"{path} header mismatch. expected={expected}, actual={reader.fieldnames}")


def validate_csv():
    require_header(places_path, PLACE_HEADER)
    require_header(features_path, FEATURE_HEADER)
    place_ids = set()
    provider_place_ids = set()
    category_counts = Counter()
    with open(places_path, newline="", encoding="utf-8-sig") as file:
        for line_no, row in enumerate(csv.DictReader(file), start=2):
            try:
                place_id = int(row["placeId"])
            except ValueError:
                fail(f"{places_path}:{line_no} invalid placeId: {row['placeId']}")
            if place_id in place_ids:
                fail(f"{places_path}:{line_no} duplicate placeId: {place_id}")
            place_ids.add(place_id)
            if not row["name"].strip():
                fail(f"{places_path}:{line_no} blank name")
            if row["category"] not in ALLOWED_CATEGORIES:
                fail(f"{places_path}:{line_no} invalid category: {row['category']}")
            if not row["point"].startswith("POINT(") or not row["point"].endswith(")"):
                fail(f"{places_path}:{line_no} invalid point WKT: {row['point'][:80]}")
            provider_place_id = row["providerPlaceId"].strip()
            if provider_place_id:
                if provider_place_id in provider_place_ids:
                    fail(f"{places_path}:{line_no} duplicate providerPlaceId: {provider_place_id}")
                provider_place_ids.add(provider_place_id)
            category_counts[row["category"]] += 1

    feature_ids = set()
    raw_feature_counts = Counter()
    normalized_features = defaultdict(dict)
    with open(features_path, newline="", encoding="utf-8-sig") as file:
        for line_no, row in enumerate(csv.DictReader(file), start=2):
            try:
                feature_id = int(row["id"])
                place_id = int(row["placeId"])
            except ValueError as exc:
                fail(f"{features_path}:{line_no} invalid numeric id: {exc}")
            if feature_id in feature_ids:
                fail(f"{features_path}:{line_no} duplicate id: {feature_id}")
            feature_ids.add(feature_id)
            if place_id not in place_ids:
                fail(f"{features_path}:{line_no} orphan placeId: {place_id}")
            raw_feature_type = row["featureType"]
            feature_type = FEATURE_ALIASES.get(raw_feature_type, raw_feature_type)
            if feature_type not in ALLOWED_FEATURES:
                fail(f"{features_path}:{line_no} invalid featureType: {raw_feature_type}")
            is_available_raw = row["isAvailable"].lower()
            if is_available_raw not in {"true", "false"}:
                fail(f"{features_path}:{line_no} invalid isAvailable: {row['isAvailable']}")
            raw_feature_counts[raw_feature_type] += 1
            normalized_features[place_id][feature_type] = (
                normalized_features[place_id].get(feature_type, False) or is_available_raw == "true"
            )

    places_without_feature = place_ids - set(normalized_features)
    if places_without_feature:
        fail(f"places without accessibility features: {len(places_without_feature)}")
    normalized_row_count = sum(len(features) for features in normalized_features.values())
    print(
        "place CSV validation ok: "
        f"places={len(place_ids)}, rawFeatures={len(feature_ids)}, normalizedFeatures={normalized_row_count}, "
        f"providerPlaceIds={len(provider_place_ids)}",
        flush=True,
    )


validate_csv()
db_url = os.environ.get("DB_URL")
if not db_url:
    fail("DB_URL is required")

with psycopg2.connect(db_url) as connection:
    with connection.cursor() as cursor:
        cursor.execute("CREATE EXTENSION IF NOT EXISTS postgis")
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS places (
              place_id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
              name varchar(255) NOT NULL,
              category varchar(50) NOT NULL,
              address varchar(255),
              point geometry(Point, 4326) NOT NULL,
              provider_place_id varchar(100),
              created_at timestamp NOT NULL DEFAULT now(),
              updated_at timestamp NOT NULL DEFAULT now()
            )
        """)
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS place_accessibility_features (
              id integer GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
              place_id bigint NOT NULL REFERENCES places(place_id),
              feature_type varchar(50) NOT NULL,
              is_available boolean NOT NULL,
              CONSTRAINT uk_place_accessibility_features_place_type UNIQUE (place_id, feature_type)
            )
        """)
        cursor.execute("""
            CREATE UNIQUE INDEX IF NOT EXISTS uk_places_provider_place_id
            ON places (provider_place_id)
            WHERE provider_place_id IS NOT NULL
        """)
        cursor.execute("""
            CREATE TEMP TABLE staging_places (
              place_id text,
              name text,
              category text,
              address text,
              point text,
              provider_place_id text
            ) ON COMMIT DROP
        """)
        cursor.execute("""
            CREATE TEMP TABLE staging_place_accessibility_features (
              id text,
              place_id text,
              feature_type text,
              is_available text
            ) ON COMMIT DROP
        """)
        with open(places_path, encoding="utf-8-sig") as file:
            cursor.copy_expert(
                "COPY staging_places FROM STDIN WITH (FORMAT csv, HEADER true)",
                file,
            )
        with open(features_path, encoding="utf-8-sig") as file:
            cursor.copy_expert(
                "COPY staging_place_accessibility_features FROM STDIN WITH (FORMAT csv, HEADER true)",
                file,
            )
        cursor.execute("""
            INSERT INTO places (
              place_id, name, category, address, point, provider_place_id, created_at, updated_at
            )
            SELECT
              place_id::bigint,
              name,
              category,
              NULLIF(address, ''),
              ST_GeomFromText(point, 4326)::geometry(Point, 4326),
              NULLIF(provider_place_id, ''),
              now(),
              now()
            FROM staging_places
            ON CONFLICT (place_id) DO UPDATE SET
              name = EXCLUDED.name,
              category = EXCLUDED.category,
              address = EXCLUDED.address,
              point = EXCLUDED.point,
              provider_place_id = EXCLUDED.provider_place_id,
              updated_at = now()
        """)
        cursor.execute("""
            DELETE FROM place_accessibility_features feature
            USING staging_places place
            WHERE feature.place_id = place.place_id::bigint
        """)
        cursor.execute("""
            WITH normalized_features AS (
              SELECT
                place_id::bigint AS place_id,
                CASE
                  WHEN feature_type IN ('ramp', 'stepFree') THEN 'accessibleEntrance'
                  ELSE feature_type
                END AS feature_type,
                bool_or(lower(is_available) = 'true') AS is_available
              FROM staging_place_accessibility_features
              GROUP BY place_id::bigint,
                CASE
                  WHEN feature_type IN ('ramp', 'stepFree') THEN 'accessibleEntrance'
                  ELSE feature_type
                END
            )
            INSERT INTO place_accessibility_features (place_id, feature_type, is_available)
            SELECT place_id, feature_type, is_available
            FROM normalized_features
            ORDER BY place_id, feature_type
        """)
        cursor.execute("SELECT setval(pg_get_serial_sequence('places', 'place_id'), COALESCE((SELECT MAX(place_id) FROM places), 1), true)")
        cursor.execute("SELECT setval(pg_get_serial_sequence('place_accessibility_features', 'id'), COALESCE((SELECT MAX(id) FROM place_accessibility_features), 1), true)")
        cursor.execute("SELECT COUNT(*) FROM places WHERE place_id IN (SELECT place_id::bigint FROM staging_places)")
        place_count = cursor.fetchone()[0]
        cursor.execute("SELECT COUNT(*) FROM place_accessibility_features WHERE place_id IN (SELECT place_id::bigint FROM staging_places)")
        feature_count = cursor.fetchone()[0]
        print(f"places load complete: places={place_count}, normalizedFeatures={feature_count}", flush=True)
PY
}

main() {
  local places_csv features_csv
  places_csv="$(abs_path "$PLACES_CSV")"
  features_csv="$(abs_path "$PLACE_ACCESSIBILITY_FEATURES_CSV")"
  prepare_csv_mount "$places_csv" "$features_csv"
  load_into_prod_db
}

main "$@"
