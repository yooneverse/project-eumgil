#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

# be-dev helpers read .env.dev and prepare the SSH tunnel.
# This script runs psql COPY over that tunnel to load road network CSV files.
source "$ROOT_DIR/scripts/make/lib/be-dev.sh"

NODES_CSV="${ROAD_NODES_CSV:-$ROOT_DIR/.ai/LOCAL/nodes.csv}"
SEGMENTS_CSV="${ROAD_SEGMENTS_CSV:-$ROOT_DIR/.ai/LOCAL/segments.csv}"
VALIDATE_ONLY=false
SKIP_TUNNEL="${ROAD_NETWORK_SKIP_TUNNEL:-false}"

usage() {
  cat <<'EOF'
Usage: scripts/db/load_road_network_dev.sh [--validate-only] [--skip-tunnel]

Loads .ai/LOCAL/nodes.csv and .ai/LOCAL/segments.csv into dev DB tables:
  - road_nodes
  - road_segments

Environment overrides:
  ROAD_NODES_CSV=/path/to/nodes.csv
  ROAD_SEGMENTS_CSV=/path/to/segments.csv
  ROAD_NETWORK_SKIP_TUNNEL=true
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

validate_csv() {
  local nodes_csv="$1"
  local segments_csv="$2"

  # Validate CSV structure and enum candidates before opening a DB transaction.
  # If this fails, no dev DB state is changed.
  python3 - "$nodes_csv" "$segments_csv" <<'PY'
import csv
import sys
from collections import Counter
from decimal import Decimal, InvalidOperation

nodes_path, segments_path = sys.argv[1], sys.argv[2]

node_header = ["vertexId", "sourceNodeKey", "point"]
segment_header = [
    "edgeId",
    "fromNodeId",
    "toNodeId",
    "geom",
    "lengthMeter",
    "walkAccess",
    "avgSlopePercent",
    "widthMeter",
    "brailleBlockState",
    "audioSignalState",
    "slopeState",
    "widthState",
    "surfaceState",
    "stairsState",
    "signalState",
    "segmentType",
]

allowed = {
    "walkAccess": {"YES", "NO", "UNKNOWN"},
    "brailleBlockState": {"YES", "NO", "UNKNOWN"},
    "audioSignalState": {"YES", "NO", "UNKNOWN"},
    "slopeState": {"FLAT", "MODERATE", "STEEP", "RISK", "UNKNOWN"},
    "widthState": {"ADEQUATE_150", "ADEQUATE_120", "NARROW", "UNKNOWN"},
    "surfaceState": {"PAVED", "UNPAVED", "UNKNOWN"},
    "stairsState": {"YES", "NO", "UNKNOWN"},
    "signalState": {"YES", "NO", "UNKNOWN"},
    "segmentType": {"CROSS_WALK", "SIDE_LINE"},
}

segment_type_aliases = {
    "SIDE_WALK": "CROSS_WALK",
    "TRANSITION_CONNECTOR": "SIDE_LINE",
}


def fail(message):
    print(f"CSV validation failed: {message}", file=sys.stderr)
    raise SystemExit(1)


def require_header(path, expected):
    with open(path, newline="", encoding="utf-8-sig") as file:
        reader = csv.DictReader(file)
        if reader.fieldnames != expected:
            fail(f"{path} header mismatch. expected={expected}, actual={reader.fieldnames}")
        return reader.fieldnames


require_header(nodes_path, node_header)
require_header(segments_path, segment_header)

node_ids = set()
source_keys = set()
node_rows = 0
with open(nodes_path, newline="", encoding="utf-8-sig") as file:
    for line_no, row in enumerate(csv.DictReader(file), start=2):
        node_rows += 1
        if not row["vertexId"] or not row["sourceNodeKey"] or not row["point"]:
            fail(f"{nodes_path}:{line_no} contains blank required value")
        try:
            vertex_id = int(row["vertexId"])
        except ValueError:
            fail(f"{nodes_path}:{line_no} vertexId is not a number: {row['vertexId']}")
        if vertex_id in node_ids:
            fail(f"{nodes_path}:{line_no} duplicate vertexId: {vertex_id}")
        node_ids.add(vertex_id)
        if row["sourceNodeKey"] in source_keys:
            fail(f"{nodes_path}:{line_no} duplicate sourceNodeKey: {row['sourceNodeKey']}")
        if len(row["sourceNodeKey"]) > 100:
            fail(f"{nodes_path}:{line_no} sourceNodeKey exceeds 100 chars")
        source_keys.add(row["sourceNodeKey"])
        if not row["point"].startswith("SRID=4326;POINT(") or not row["point"].endswith(")"):
            fail(f"{nodes_path}:{line_no} invalid point EWKT: {row['point'][:120]}")

edge_ids = set()
segment_rows = 0
enum_counts = {key: Counter() for key in allowed}
with open(segments_path, newline="", encoding="utf-8-sig") as file:
    for line_no, row in enumerate(csv.DictReader(file), start=2):
        segment_rows += 1
        for key in ["edgeId", "fromNodeId", "toNodeId", "geom", "lengthMeter"]:
            if not row[key]:
                fail(f"{segments_path}:{line_no} blank required value: {key}")
        try:
            edge_id = int(row["edgeId"])
            from_node_id = int(row["fromNodeId"])
            to_node_id = int(row["toNodeId"])
        except ValueError as exc:
            fail(f"{segments_path}:{line_no} invalid numeric id: {exc}")
        if edge_id in edge_ids:
            fail(f"{segments_path}:{line_no} duplicate edgeId: {edge_id}")
        edge_ids.add(edge_id)
        if from_node_id not in node_ids or to_node_id not in node_ids:
            fail(f"{segments_path}:{line_no} node reference not found: {from_node_id}->{to_node_id}")
        if not row["geom"].startswith("SRID=4326;LINESTRING(") or not row["geom"].endswith(")"):
            fail(f"{segments_path}:{line_no} invalid linestring EWKT: {row['geom'][:120]}")
        for key in ["lengthMeter", "avgSlopePercent", "widthMeter"]:
            if row[key]:
                try:
                    Decimal(row[key])
                except InvalidOperation:
                    fail(f"{segments_path}:{line_no} invalid decimal {key}: {row[key]}")
        for key, candidates in allowed.items():
            value = row[key]
            if not value:
                fail(f"{segments_path}:{line_no} blank enum value: {key}")
            normalized_value = segment_type_aliases.get(value, value) if key == "segmentType" else value
            if normalized_value not in candidates:
                fail(f"{segments_path}:{line_no} invalid {key}: {value}")
            enum_counts[key][normalized_value] += 1

print(f"CSV validation ok: nodes={node_rows}, segments={segment_rows}")
for key in sorted(enum_counts):
    values = ", ".join(f"{name}={count}" for name, count in sorted(enum_counts[key].items()))
    print(f"  {key}: {values}")
PY
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

load_into_dev_db() {
  local nodes_csv="$1"
  local segments_csv="$2"

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

  local nodes_csv_literal segments_csv_literal
  nodes_csv_literal="$(psql_file_literal "$nodes_csv")"
  segments_csv_literal="$(psql_file_literal "$segments_csv")"

  echo "loading road network CSV into dev DB: 127.0.0.1:$BE_DEV_DB_LOCAL_PORT/$db_name"
  PGPASSWORD="$db_password" psql \
    -h 127.0.0.1 \
    -p "$BE_DEV_DB_LOCAL_PORT" \
    -U "$db_user" \
    -d "$db_name" \
    -v ON_ERROR_STOP=1 <<SQL
CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE IF NOT EXISTS road_nodes (
  vertex_id bigint PRIMARY KEY,
  source_node_key varchar(100) NOT NULL UNIQUE,
  "point" geometry(Point, 4326) NOT NULL
);

CREATE TABLE IF NOT EXISTS road_segments (
  edge_id bigint PRIMARY KEY,
  from_node_id bigint NOT NULL,
  to_node_id bigint NOT NULL,
  "geom" geometry(LineString, 4326) NOT NULL,
  length_meter numeric(10, 2) NOT NULL,
  walk_access varchar(30) NOT NULL DEFAULT 'UNKNOWN',
  avg_slope_percent numeric(6, 2),
  width_meter numeric(6, 2),
  braille_block_state varchar(30) NOT NULL DEFAULT 'UNKNOWN',
  audio_signal_state varchar(30) NOT NULL DEFAULT 'UNKNOWN',
  width_state varchar(30) NOT NULL DEFAULT 'UNKNOWN',
  surface_state varchar(30) NOT NULL DEFAULT 'UNKNOWN',
  stairs_state varchar(30) NOT NULL DEFAULT 'UNKNOWN',
  signal_state varchar(30) NOT NULL DEFAULT 'UNKNOWN',
  segment_type varchar(30) NOT NULL DEFAULT 'SIDE_LINE'
);

CREATE TABLE IF NOT EXISTS segment_features (
  feature_id bigint PRIMARY KEY,
  edge_id bigint NOT NULL,
  feature_type varchar(50) NOT NULL,
  "geom" geometry(Geometry, 4326) NOT NULL,
  state varchar(50),
  value_number numeric(10, 2)
);

CREATE INDEX IF NOT EXISTS segment_features_edge_id_idx ON segment_features (edge_id);

-- CSV header format stays camelCase for upstream compatibility.
-- Temp/staging columns and final tables use snake_case consistently.
CREATE TEMP TABLE staging_road_nodes (
  vertex_id text,
  source_node_key text,
  "point" text
);

CREATE TEMP TABLE staging_road_segments (
  edge_id text,
  from_node_id text,
  to_node_id text,
  "geom" text,
  length_meter text,
  walk_access text,
  avg_slope_percent text,
  width_meter text,
  braille_block_state text,
  audio_signal_state text,
  slope_state text,
  width_state text,
  surface_state text,
  stairs_state text,
  signal_state text,
  segment_type text
);

\copy staging_road_nodes FROM ${nodes_csv_literal} WITH (FORMAT csv, HEADER true)
\copy staging_road_segments FROM ${segments_csv_literal} WITH (FORMAT csv, HEADER true)

CREATE INDEX staging_road_nodes_vertex_id_idx ON staging_road_nodes (vertex_id);
CREATE INDEX staging_road_segments_from_node_id_idx ON staging_road_segments (from_node_id);
CREATE INDEX staging_road_segments_to_node_id_idx ON staging_road_segments (to_node_id);
ANALYZE staging_road_nodes;
ANALYZE staging_road_segments;

BEGIN;

DO \$validate_staging\$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM staging_road_segments s
    LEFT JOIN staging_road_nodes nf ON nf.vertex_id = s.from_node_id
    LEFT JOIN staging_road_nodes nt ON nt.vertex_id = s.to_node_id
    WHERE nf.vertex_id IS NULL OR nt.vertex_id IS NULL
  ) THEN
    RAISE EXCEPTION 'staging_road_segments contains orphan node references';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM staging_road_segments
    WHERE segment_type NOT IN ('CROSS_WALK', 'SIDE_LINE', 'SIDE_WALK', 'TRANSITION_CONNECTOR')
  ) THEN
    RAISE EXCEPTION 'staging_road_segments contains invalid segmentType';
  END IF;
END
\$validate_staging\$;

TRUNCATE TABLE segment_features, road_segments, road_nodes;

INSERT INTO road_nodes (
  vertex_id,
  source_node_key,
  "point"
)
SELECT
  vertex_id::bigint,
  source_node_key,
  ST_GeomFromEWKT("point")::geometry(Point, 4326)
FROM staging_road_nodes;

INSERT INTO road_segments (
  edge_id,
  from_node_id,
  to_node_id,
  "geom",
  length_meter,
  walk_access,
  avg_slope_percent,
  width_meter,
  braille_block_state,
  audio_signal_state,
  width_state,
  surface_state,
  stairs_state,
  signal_state,
  segment_type
)
SELECT
  edge_id::bigint,
  from_node_id::bigint,
  to_node_id::bigint,
  ST_GeomFromEWKT("geom")::geometry(LineString, 4326),
  length_meter::numeric(10, 2),
  walk_access,
  NULLIF(avg_slope_percent, '')::numeric(6, 2),
  NULLIF(width_meter, '')::numeric(6, 2),
  braille_block_state,
  audio_signal_state,
  width_state,
  surface_state,
  stairs_state,
  signal_state,
  CASE segment_type
    WHEN 'SIDE_WALK' THEN 'CROSS_WALK'
    WHEN 'TRANSITION_CONNECTOR' THEN 'SIDE_LINE'
    ELSE segment_type
  END
FROM staging_road_segments;

CREATE SEQUENCE IF NOT EXISTS road_nodes_vertex_id_seq;
SELECT setval('road_nodes_vertex_id_seq', COALESCE((SELECT MAX(vertex_id) FROM road_nodes), 0) + 1, false);
ALTER TABLE road_nodes ALTER COLUMN vertex_id SET DEFAULT nextval('road_nodes_vertex_id_seq');

CREATE SEQUENCE IF NOT EXISTS road_segments_edge_id_seq;
SELECT setval('road_segments_edge_id_seq', COALESCE((SELECT MAX(edge_id) FROM road_segments), 0) + 1, false);
ALTER TABLE road_segments ALTER COLUMN edge_id SET DEFAULT nextval('road_segments_edge_id_seq');

CREATE SEQUENCE IF NOT EXISTS segment_features_feature_id_seq;
SELECT setval('segment_features_feature_id_seq', COALESCE((SELECT MAX(feature_id) FROM segment_features), 0) + 1, false);
ALTER TABLE segment_features ALTER COLUMN feature_id SET DEFAULT nextval('segment_features_feature_id_seq');

DO \$validate_loaded\$
DECLARE
  staging_node_count bigint;
  staging_segment_count bigint;
  loaded_node_count bigint;
  loaded_segment_count bigint;
  orphan_count bigint;
  invalid_point_count bigint;
  invalid_segment_count bigint;
BEGIN
  SELECT COUNT(*) INTO staging_node_count FROM staging_road_nodes;
  SELECT COUNT(*) INTO staging_segment_count FROM staging_road_segments;
  SELECT COUNT(*) INTO loaded_node_count FROM road_nodes;
  SELECT COUNT(*) INTO loaded_segment_count FROM road_segments;

  IF staging_node_count <> loaded_node_count THEN
    RAISE EXCEPTION 'road_nodes count mismatch: staging %, loaded %', staging_node_count, loaded_node_count;
  END IF;
  IF staging_segment_count <> loaded_segment_count THEN
    RAISE EXCEPTION 'road_segments count mismatch: staging %, loaded %', staging_segment_count, loaded_segment_count;
  END IF;

  SELECT COUNT(*)
  INTO orphan_count
  FROM road_segments s
  LEFT JOIN road_nodes nf ON nf.vertex_id = s.from_node_id
  LEFT JOIN road_nodes nt ON nt.vertex_id = s.to_node_id
  WHERE nf.vertex_id IS NULL OR nt.vertex_id IS NULL;

  SELECT COUNT(*) INTO invalid_point_count FROM road_nodes WHERE NOT ST_IsValid("point");
  SELECT COUNT(*) INTO invalid_segment_count FROM road_segments WHERE NOT ST_IsValid("geom");

  IF orphan_count <> 0 THEN
    RAISE EXCEPTION 'road_segments orphan reference count: %', orphan_count;
  END IF;
  IF invalid_point_count <> 0 THEN
    RAISE EXCEPTION 'invalid road_nodes point count: %', invalid_point_count;
  END IF;
  IF invalid_segment_count <> 0 THEN
    RAISE EXCEPTION 'invalid road_segments geom count: %', invalid_segment_count;
  END IF;

  RAISE NOTICE 'road network load ok: nodes %, segments %', loaded_node_count, loaded_segment_count;
END
\$validate_loaded\$;

COMMIT;

SELECT 'road_nodes' AS table_name, COUNT(*) AS row_count FROM road_nodes
UNION ALL
SELECT 'road_segments' AS table_name, COUNT(*) AS row_count FROM road_segments
ORDER BY table_name;

SELECT segment_type, COUNT(*) AS row_count
FROM road_segments
GROUP BY segment_type
ORDER BY segment_type;
SQL
}

main() {
  NODES_CSV="$(abs_path "$NODES_CSV")"
  SEGMENTS_CSV="$(abs_path "$SEGMENTS_CSV")"

  echo "nodes csv: $NODES_CSV"
  echo "segments csv: $SEGMENTS_CSV"
  validate_csv "$NODES_CSV" "$SEGMENTS_CSV"

  if [ "$VALIDATE_ONLY" = "true" ]; then
    exit 0
  fi

  load_into_dev_db "$NODES_CSV" "$SEGMENTS_CSV"
}

main "$@"
