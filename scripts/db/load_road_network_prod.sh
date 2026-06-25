#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
ENV_FILE="${ENV_FILE:-"$ROOT_DIR/.env.prod"}"
source "$ROOT_DIR/scripts/make/lib/prod-db-tunnel.sh"

NODES_CSV="${ROAD_NODES_CSV:-$ROOT_DIR/.ai/LOCAL/nodes.csv}"
SEGMENTS_CSV="${ROAD_SEGMENTS_CSV:-$ROOT_DIR/.ai/LOCAL/segments.csv}"
CSV_MOUNT_DIR=""
CONTAINER_NODES_CSV=""
CONTAINER_SEGMENTS_CSV=""
PROD_COMPOSE=(docker compose --env-file "$ENV_FILE" -f "$ROOT_DIR/docker-compose.prod.yml")

usage() {
  cat <<'EOF'
Usage: scripts/db/load_road_network_prod.sh

Loads .ai/LOCAL/nodes.csv and .ai/LOCAL/segments.csv into prod DB tables:
  - road_nodes
  - road_segments

Environment overrides:
  ROAD_NODES_CSV=/path/to/nodes.csv
  ROAD_SEGMENTS_CSV=/path/to/segments.csv
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

prepare_csv_mount() {
  local nodes_csv="$1"
  local segments_csv="$2"
  local nodes_dir segments_dir
  nodes_dir="$(dirname "$nodes_csv")"
  segments_dir="$(dirname "$segments_csv")"

  if [ "$nodes_dir" != "$segments_dir" ]; then
    echo "ROAD_NODES_CSV and ROAD_SEGMENTS_CSV must be in the same directory for prod Docker load." >&2
    exit 1
  fi

  CSV_MOUNT_DIR="$nodes_dir"
  CONTAINER_NODES_CSV="/road-network/$(basename "$nodes_csv")"
  CONTAINER_SEGMENTS_CSV="/road-network/$(basename "$segments_csv")"
}

csv_mount_arg() {
  if command -v cygpath >/dev/null 2>&1; then
    echo "$(cygpath -w "$CSV_MOUNT_DIR"):/road-network:ro"
    return
  fi

  echo "$CSV_MOUNT_DIR:/road-network:ro"
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
    - "$CONTAINER_NODES_CSV" "$CONTAINER_SEGMENTS_CSV" <<'PY'
import csv
import os
import sys
from collections import Counter
from decimal import Decimal, InvalidOperation

import psycopg2

nodes_path, segments_path = sys.argv[1], sys.argv[2]

NODE_HEADER = ["vertexId", "sourceNodeKey", "point"]
SEGMENT_HEADER = [
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

STAGING_NODE_COLUMNS = ["vertex_id", "source_node_key", "point"]
STAGING_SEGMENT_COLUMNS = [
    "edge_id",
    "from_node_id",
    "to_node_id",
    "geom",
    "length_meter",
    "walk_access",
    "avg_slope_percent",
    "width_meter",
    "braille_block_state",
    "audio_signal_state",
    "slope_state",
    "width_state",
    "surface_state",
    "stairs_state",
    "signal_state",
    "segment_type",
]

ALLOWED = {
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

SEGMENT_TYPE_ALIASES = {
    "SIDE_WALK": "CROSS_WALK",
    "TRANSITION_CONNECTOR": "SIDE_LINE",
}


def fail(message: str) -> None:
    print(f"CSV validation failed: {message}", file=sys.stderr)
    raise SystemExit(1)


def require_header(path: str, expected: list[str]) -> None:
    with open(path, newline="", encoding="utf-8-sig") as file:
        reader = csv.DictReader(file)
        if reader.fieldnames != expected:
            fail(f"{path} header mismatch. expected={expected}, actual={reader.fieldnames}")


def validate_csv() -> tuple[int, int]:
    require_header(nodes_path, NODE_HEADER)
    require_header(segments_path, SEGMENT_HEADER)

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
    enum_counts = {key: Counter() for key in ALLOWED}
    segment_type_alias_counts = Counter()
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
            for key, candidates in ALLOWED.items():
                value = row[key]
                if not value:
                    fail(f"{segments_path}:{line_no} blank enum value: {key}")
                normalized_value = SEGMENT_TYPE_ALIASES.get(value, value) if key == "segmentType" else value
                if normalized_value not in candidates:
                    fail(f"{segments_path}:{line_no} invalid {key}: {value}")
                enum_counts[key][normalized_value] += 1
                if key == "segmentType" and value in SEGMENT_TYPE_ALIASES:
                    segment_type_alias_counts[f"{value}->{SEGMENT_TYPE_ALIASES[value]}"] += 1

    print(f"CSV validation ok: nodes={node_rows}, segments={segment_rows}", flush=True)
    for key in sorted(enum_counts):
        values = ", ".join(f"{name}={count}" for name, count in sorted(enum_counts[key].items()))
        print(f"  {key}: {values}", flush=True)
    for alias, count in sorted(segment_type_alias_counts.items()):
        print(f"  segmentType normalized: {alias} rows={count}", flush=True)
    return node_rows, segment_rows


def connect():
    url = os.environ["DB_URL"].replace("jdbc:postgresql://", "")
    host_port, db_name = url.split("/", 1)
    host, port = host_port.split(":", 1)
    return psycopg2.connect(
        host=host,
        port=port,
        dbname=db_name,
        user=os.environ["DB_USERNAME"],
        password=os.environ["DB_PASSWORD"],
        sslmode=os.environ.get("DB_SSLMODE", "require"),
    )


def copy_csv(cursor, table: str, columns: list[str], path: str) -> None:
    quoted_columns = ", ".join(f'"{column}"' for column in columns)
    sql = f'COPY {table} ({quoted_columns}) FROM STDIN WITH (FORMAT csv, HEADER true)'
    with open(path, "r", encoding="utf-8-sig", newline="") as file:
        cursor.copy_expert(sql, file)


def load_csv() -> None:
    with connect() as conn:
        conn.autocommit = False
        with conn.cursor() as cursor:
            cursor.execute("CREATE EXTENSION IF NOT EXISTS postgis")
            cursor.execute(
                """
                CREATE TABLE IF NOT EXISTS road_nodes (
                  vertex_id bigint PRIMARY KEY,
                  source_node_key varchar(100) NOT NULL UNIQUE,
                  "point" geometry(Point, 4326) NOT NULL
                )
                """
            )
            cursor.execute(
                """
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
                )
                """
            )
            cursor.execute(
                """
                CREATE TABLE IF NOT EXISTS segment_features (
                  feature_id bigint PRIMARY KEY,
                  edge_id bigint NOT NULL,
                  feature_type varchar(50) NOT NULL,
                  "geom" geometry(Geometry, 4326) NOT NULL,
                  state varchar(50),
                  value_number numeric(10, 2)
                )
                """
            )
            cursor.execute("CREATE INDEX IF NOT EXISTS segment_features_edge_id_idx ON segment_features (edge_id)")
            # CSV header format stays camelCase for upstream compatibility.
            # Temp/staging columns and final tables use snake_case consistently.
            cursor.execute('CREATE TEMP TABLE staging_road_nodes (vertex_id text, source_node_key text, "point" text)')
            cursor.execute(
                """
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
                )
                """
            )
            copy_csv(cursor, "staging_road_nodes", STAGING_NODE_COLUMNS, nodes_path)
            copy_csv(cursor, "staging_road_segments", STAGING_SEGMENT_COLUMNS, segments_path)
            cursor.execute("CREATE INDEX staging_road_nodes_vertex_id_idx ON staging_road_nodes (vertex_id)")
            cursor.execute("CREATE INDEX staging_road_segments_from_node_id_idx ON staging_road_segments (from_node_id)")
            cursor.execute("CREATE INDEX staging_road_segments_to_node_id_idx ON staging_road_segments (to_node_id)")
            cursor.execute("ANALYZE staging_road_nodes")
            cursor.execute("ANALYZE staging_road_segments")
            cursor.execute(
                """
                DO $validate_staging$
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
                $validate_staging$;
                """
            )
            cursor.execute("TRUNCATE TABLE segment_features, road_segments, road_nodes")
            cursor.execute(
                """
                INSERT INTO road_nodes (vertex_id, source_node_key, "point")
                SELECT
                  vertex_id::bigint,
                  source_node_key,
                  ST_GeomFromEWKT("point")::geometry(Point, 4326)
                FROM staging_road_nodes
                """
            )
            cursor.execute(
                """
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
                FROM staging_road_segments
                """
            )
            cursor.execute("CREATE SEQUENCE IF NOT EXISTS road_nodes_vertex_id_seq")
            cursor.execute(
                "SELECT setval('road_nodes_vertex_id_seq', COALESCE((SELECT MAX(vertex_id) FROM road_nodes), 0) + 1, false)"
            )
            cursor.execute(
                "ALTER TABLE road_nodes ALTER COLUMN vertex_id SET DEFAULT nextval('road_nodes_vertex_id_seq')"
            )
            cursor.execute("CREATE SEQUENCE IF NOT EXISTS road_segments_edge_id_seq")
            cursor.execute(
                "SELECT setval('road_segments_edge_id_seq', COALESCE((SELECT MAX(edge_id) FROM road_segments), 0) + 1, false)"
            )
            cursor.execute(
                "ALTER TABLE road_segments ALTER COLUMN edge_id SET DEFAULT nextval('road_segments_edge_id_seq')"
            )
            cursor.execute("CREATE SEQUENCE IF NOT EXISTS segment_features_feature_id_seq")
            cursor.execute(
                "SELECT setval('segment_features_feature_id_seq', COALESCE((SELECT MAX(feature_id) FROM segment_features), 0) + 1, false)"
            )
            cursor.execute(
                "ALTER TABLE segment_features ALTER COLUMN feature_id SET DEFAULT nextval('segment_features_feature_id_seq')"
            )
            cursor.execute(
                """
                DO $validate_loaded$
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
                $validate_loaded$;
                """
            )
            cursor.execute(
                """
                SELECT 'road_nodes' AS table_name, COUNT(*) AS row_count FROM road_nodes
                UNION ALL
                SELECT 'road_segments' AS table_name, COUNT(*) AS row_count FROM road_segments
                ORDER BY table_name
                """
            )
            for table_name, row_count in cursor.fetchall():
                print(f"{table_name}: rows={row_count}", flush=True)
            cursor.execute('SELECT segment_type, COUNT(*) FROM road_segments GROUP BY segment_type ORDER BY segment_type')
            for segment_type, row_count in cursor.fetchall():
                print(f"segmentType {segment_type}: rows={row_count}", flush=True)


validate_csv()
load_csv()
PY
}

main() {
  NODES_CSV="$(abs_path "$NODES_CSV")"
  SEGMENTS_CSV="$(abs_path "$SEGMENTS_CSV")"
  prepare_csv_mount "$NODES_CSV" "$SEGMENTS_CSV"

  echo "nodes csv: $NODES_CSV"
  echo "segments csv: $SEGMENTS_CSV"
  echo "loading road network CSV into prod DB"
  load_into_prod_db
}

main "$@"
