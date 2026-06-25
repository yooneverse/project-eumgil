#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

source "$ROOT_DIR/scripts/make/lib/be-dev.sh"

PLACES_CSV="${PLACES_CSV:-$ROOT_DIR/.ai/LOCAL/places_erd.csv}"
PLACE_ACCESSIBILITY_FEATURES_CSV="${PLACE_ACCESSIBILITY_FEATURES_CSV:-$ROOT_DIR/.ai/LOCAL/place_accessibility_features_erd.csv}"
VALIDATE_ONLY=false
SKIP_TUNNEL="${PLACES_SKIP_TUNNEL:-false}"

usage() {
  cat <<'EOF'
Usage: scripts/db/load_places_dev.sh [--validate-only] [--skip-tunnel]

Loads facility seed CSV files into dev DB tables:
  - places
  - place_accessibility_features

Environment overrides:
  PLACES_CSV=/path/to/places_erd.csv
  PLACE_ACCESSIBILITY_FEATURES_CSV=/path/to/place_accessibility_features_erd.csv
  PLACES_SKIP_TUNNEL=true
  ENV_FILE=/path/to/.env.dev

Notes:
  - ramp and stepFree are normalized to accessibleEntrance.
  - Existing features for staged placeIds are replaced.
  - Existing places are upserted; unrelated places are not pruned.
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

validate_csv() {
  local places_csv="$1"
  local features_csv="$2"

  python3 - "$places_csv" "$features_csv" <<'PY'
import csv
import sys
from collections import Counter, defaultdict

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
FEATURE_ALIASES = {
    "ramp": "accessibleEntrance",
    "stepFree": "accessibleEntrance",
}


def fail(message):
    print(f"place CSV validation failed: {message}", file=sys.stderr)
    raise SystemExit(1)


def require_header(path, expected):
    with open(path, newline="", encoding="utf-8-sig") as file:
        reader = csv.DictReader(file)
        if reader.fieldnames != expected:
            fail(f"{path} header mismatch. expected={expected}, actual={reader.fieldnames}")


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
    f"providerPlaceIds={len(provider_place_ids)}"
)
for category, count in sorted(category_counts.items()):
    print(f"  category {category}: {count}")
for feature_type, count in sorted(raw_feature_counts.items()):
    print(f"  raw feature {feature_type}: {count}")
PY
}

load_into_dev_db() {
  local places_csv="$1"
  local features_csv="$2"

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

  local places_csv_literal features_csv_literal
  places_csv_literal="$(psql_file_literal "$places_csv")"
  features_csv_literal="$(psql_file_literal "$features_csv")"

  echo "loading places CSV into dev DB: 127.0.0.1:$BE_DEV_DB_LOCAL_PORT/$db_name"
  PGPASSWORD="$db_password" psql \
    -h 127.0.0.1 \
    -p "$BE_DEV_DB_LOCAL_PORT" \
    -U "$db_user" \
    -d "$db_name" \
    -v ON_ERROR_STOP=1 <<SQL
CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE IF NOT EXISTS places (
  place_id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
  name varchar(255) NOT NULL,
  category varchar(50) NOT NULL,
  address varchar(255),
  "point" geometry(Point, 4326) NOT NULL,
  provider_place_id varchar(100),
  created_at timestamp NOT NULL DEFAULT now(),
  updated_at timestamp NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS place_accessibility_features (
  id integer GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
  place_id bigint NOT NULL REFERENCES places(place_id),
  feature_type varchar(50) NOT NULL,
  is_available boolean NOT NULL,
  CONSTRAINT uk_place_accessibility_features_place_type UNIQUE (place_id, feature_type)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_places_provider_place_id
ON places (provider_place_id)
WHERE provider_place_id IS NOT NULL;

CREATE TEMP TABLE staging_places (
  place_id text,
  name text,
  category text,
  address text,
  "point" text,
  provider_place_id text
);

CREATE TEMP TABLE staging_place_accessibility_features (
  id text,
  place_id text,
  feature_type text,
  is_available text
);

\copy staging_places FROM ${places_csv_literal} WITH (FORMAT csv, HEADER true)
\copy staging_place_accessibility_features FROM ${features_csv_literal} WITH (FORMAT csv, HEADER true)

BEGIN;

INSERT INTO places (
  place_id,
  name,
  category,
  address,
  "point",
  provider_place_id,
  created_at,
  updated_at
)
SELECT
  place_id::bigint,
  name,
  category,
  NULLIF(address, ''),
  ST_GeomFromText("point", 4326)::geometry(Point, 4326),
  NULLIF(provider_place_id, ''),
  now(),
  now()
FROM staging_places
ON CONFLICT (place_id) DO UPDATE SET
  name = EXCLUDED.name,
  category = EXCLUDED.category,
  address = EXCLUDED.address,
  "point" = EXCLUDED."point",
  provider_place_id = EXCLUDED.provider_place_id,
  updated_at = now();

DELETE FROM place_accessibility_features feature
USING staging_places place
WHERE feature.place_id = place.place_id::bigint;

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
INSERT INTO place_accessibility_features (
  place_id,
  feature_type,
  is_available
)
SELECT
  place_id,
  feature_type,
  is_available
FROM normalized_features
ORDER BY place_id, feature_type;

SELECT setval(pg_get_serial_sequence('places', 'place_id'), COALESCE((SELECT MAX(place_id) FROM places), 1), true);
SELECT setval(pg_get_serial_sequence('place_accessibility_features', 'id'), COALESCE((SELECT MAX(id) FROM place_accessibility_features), 1), true);

DO \$validate_loaded\$
DECLARE
  staged_place_count bigint;
  loaded_place_count bigint;
  staged_raw_feature_count bigint;
  normalized_feature_count bigint;
  loaded_feature_count bigint;
  orphan_feature_count bigint;
BEGIN
  SELECT COUNT(*) INTO staged_place_count FROM staging_places;
  SELECT COUNT(*) INTO loaded_place_count
  FROM places
  WHERE place_id IN (SELECT place_id::bigint FROM staging_places);

  IF staged_place_count <> loaded_place_count THEN
    RAISE EXCEPTION 'places count mismatch: staging %, loaded %', staged_place_count, loaded_place_count;
  END IF;

  SELECT COUNT(*) INTO staged_raw_feature_count FROM staging_place_accessibility_features;
  SELECT COUNT(*) INTO normalized_feature_count
  FROM (
    SELECT
      place_id::bigint,
      CASE
        WHEN feature_type IN ('ramp', 'stepFree') THEN 'accessibleEntrance'
        ELSE feature_type
      END AS feature_type
    FROM staging_place_accessibility_features
    GROUP BY 1, 2
  ) normalized;
  SELECT COUNT(*) INTO loaded_feature_count
  FROM place_accessibility_features
  WHERE place_id IN (SELECT place_id::bigint FROM staging_places);

  IF normalized_feature_count <> loaded_feature_count THEN
    RAISE EXCEPTION 'place_accessibility_features count mismatch: normalized %, loaded %', normalized_feature_count, loaded_feature_count;
  END IF;

  SELECT COUNT(*) INTO orphan_feature_count
  FROM place_accessibility_features feature
  LEFT JOIN places place ON place.place_id = feature.place_id
  WHERE place.place_id IS NULL;

  IF orphan_feature_count <> 0 THEN
    RAISE EXCEPTION 'place_accessibility_features orphan count: %', orphan_feature_count;
  END IF;

  RAISE NOTICE 'places load ok: places %, raw features %, normalized features %',
    loaded_place_count, staged_raw_feature_count, loaded_feature_count;
END
\$validate_loaded\$;

COMMIT;

SELECT 'places' AS table_name, COUNT(*) AS row_count FROM places
UNION ALL
SELECT 'place_accessibility_features' AS table_name, COUNT(*) AS row_count FROM place_accessibility_features
ORDER BY table_name;

SELECT category, COUNT(*) AS row_count
FROM places
GROUP BY category
ORDER BY category;

SELECT feature_type, COUNT(*) AS row_count
FROM place_accessibility_features
GROUP BY feature_type
ORDER BY feature_type;
SQL
}

main() {
  PLACES_CSV="$(abs_path "$PLACES_CSV")"
  PLACE_ACCESSIBILITY_FEATURES_CSV="$(abs_path "$PLACE_ACCESSIBILITY_FEATURES_CSV")"

  echo "places csv: $PLACES_CSV"
  echo "features csv: $PLACE_ACCESSIBILITY_FEATURES_CSV"
  validate_csv "$PLACES_CSV" "$PLACE_ACCESSIBILITY_FEATURES_CSV"

  if [ "$VALIDATE_ONLY" = "true" ]; then
    exit 0
  fi

  load_into_dev_db "$PLACES_CSV" "$PLACE_ACCESSIBILITY_FEATURES_CSV"
}

main "$@"
