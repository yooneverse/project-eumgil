#!/usr/bin/env python3
"""PostGIS 보행 네트워크 row를 GraphHopper가 읽을 수 있는 OSM XML로 내보낸다.

이 스크립트는 GraphHopper export 경계를 담당한다.
- 표준 `road_nodes`, `road_segments`, 선택적 `segment_features`를 읽는다.
- export 전에 feature geometry 경계 기준으로 원천 segment를 분할한다.
- `ieum:*` OSM tag를 쓰기 전에 topology와 접근성 enum 값을 검증한다.
"""
import argparse
from collections import Counter, defaultdict, deque
from datetime import datetime, timezone
import json
import math
import os
import sys
import xml.etree.ElementTree as ET
from urllib.parse import parse_qs, urlparse


ROAD_NODE_COLUMNS = {
    "vertex_id": ("vertex_id", "vertexId"),
    "point": ("point",),
}

ROAD_SEGMENT_COLUMNS = {
    "edge_id": ("edge_id", "edgeId"),
    "from_node_id": ("from_node_id", "fromNodeId"),
    "to_node_id": ("to_node_id", "toNodeId"),
    "geom": ("geom",),
    "length_meter": ("length_meter", "lengthMeter"),
    "walk_access": ("walk_access", "walkAccess"),
    "avg_slope_percent": ("avg_slope_percent", "avgSlopePercent"),
    "width_meter": ("width_meter", "widthMeter"),
    "braille_block_state": ("braille_block_state", "brailleBlockState"),
    "audio_signal_state": ("audio_signal_state", "audioSignalState"),
    "width_state": ("width_state", "widthState"),
    "surface_state": ("surface_state", "surfaceState"),
    "stairs_state": ("stairs_state", "stairsState"),
    "signal_state": ("signal_state", "signalState"),
    "segment_type": ("segment_type", "segmentType"),
}

SEGMENT_FEATURE_COLUMNS = {
    "feature_id": ("feature_id", "featureId"),
    "edge_id": ("edge_id", "edgeId"),
    "feature_type": ("feature_type", "featureType"),
    "geom": ("geom",),
    "state": ("state",),
    "value_number": ("value_number", "valueNumber"),
}
POSITION_EVENT_FEATURE_TYPES = {
    "CROSSWALK",
    "AUDIO_SIGNAL",
    "BRAILLE_BLOCK",
    "STAIRS",
}

SNAKE_ROAD_NODE_COLUMNS = {aliases[0] for aliases in ROAD_NODE_COLUMNS.values()}
SNAKE_ROAD_SEGMENT_COLUMNS = {aliases[0] for aliases in ROAD_SEGMENT_COLUMNS.values()}
SNAKE_SEGMENT_FEATURE_COLUMNS = {aliases[0] for aliases in SEGMENT_FEATURE_COLUMNS.values()}


def quote_identifier(identifier):
    return '"' + identifier.replace('"', '""') + '"'


def resolve_column(available_columns, aliases, canonical_name):
    for candidate in aliases[canonical_name]:
        if candidate in available_columns:
            return quote_identifier(candidate)
    expected = ", ".join(aliases[canonical_name])
    raise ValueError(f"Missing {canonical_name} column. Expected one of: {expected}")


def resolve_optional_column(available_columns, aliases, canonical_name):
    for candidate in aliases[canonical_name]:
        if candidate in available_columns:
            return quote_identifier(candidate)
    return None


def build_road_nodes_sql(available_columns):
    vertex_id = resolve_column(available_columns, ROAD_NODE_COLUMNS, "vertex_id")
    point = resolve_column(available_columns, ROAD_NODE_COLUMNS, "point")
    return f'''
SELECT
  {vertex_id} AS vertex_id,
  ST_X({point}::geometry) AS lon,
  ST_Y({point}::geometry) AS lat
FROM road_nodes
ORDER BY {vertex_id}
'''


def build_road_segments_sql(available_columns):
    edge_id = resolve_column(available_columns, ROAD_SEGMENT_COLUMNS, "edge_id")
    from_node_id = resolve_column(available_columns, ROAD_SEGMENT_COLUMNS, "from_node_id")
    to_node_id = resolve_column(available_columns, ROAD_SEGMENT_COLUMNS, "to_node_id")
    geom = resolve_column(available_columns, ROAD_SEGMENT_COLUMNS, "geom")
    length_meter = resolve_column(available_columns, ROAD_SEGMENT_COLUMNS, "length_meter")
    walk_access = resolve_column(available_columns, ROAD_SEGMENT_COLUMNS, "walk_access")
    avg_slope_percent = resolve_column(available_columns, ROAD_SEGMENT_COLUMNS, "avg_slope_percent")
    width_meter = resolve_column(available_columns, ROAD_SEGMENT_COLUMNS, "width_meter")
    braille_block_state = resolve_column(available_columns, ROAD_SEGMENT_COLUMNS, "braille_block_state")
    audio_signal_state = resolve_column(available_columns, ROAD_SEGMENT_COLUMNS, "audio_signal_state")
    width_state = resolve_column(available_columns, ROAD_SEGMENT_COLUMNS, "width_state")
    surface_state = resolve_column(available_columns, ROAD_SEGMENT_COLUMNS, "surface_state")
    stairs_state = resolve_column(available_columns, ROAD_SEGMENT_COLUMNS, "stairs_state")
    signal_state = resolve_column(available_columns, ROAD_SEGMENT_COLUMNS, "signal_state")
    segment_type = resolve_column(available_columns, ROAD_SEGMENT_COLUMNS, "segment_type")
    return f'''
SELECT
  {edge_id} AS edge_id,
  {from_node_id} AS from_node_id,
  {to_node_id} AS to_node_id,
  ST_AsText({geom}::geometry) AS geom_wkt,
  COALESCE({walk_access}::text, 'UNKNOWN') AS walk_access,
  COALESCE({avg_slope_percent}, 0.0) AS avg_slope_percent,
  COALESCE({width_meter}, 0.0) AS width_meter,
  COALESCE({braille_block_state}::text, 'UNKNOWN') AS braille_block_state,
  COALESCE({audio_signal_state}::text, 'UNKNOWN') AS audio_signal_state,
  COALESCE({width_state}::text, 'UNKNOWN') AS width_state,
  COALESCE({surface_state}::text, 'UNKNOWN') AS surface_state,
  COALESCE({stairs_state}::text, 'UNKNOWN') AS stairs_state,
  COALESCE({signal_state}::text, 'UNKNOWN') AS signal_state,
  COALESCE({segment_type}::text, 'SIDE_LINE') AS segment_type
FROM road_segments
ORDER BY {edge_id}
'''


def build_segment_features_sql(available_columns):
    feature_id = resolve_column(available_columns, SEGMENT_FEATURE_COLUMNS, "feature_id")
    edge_id = resolve_column(available_columns, SEGMENT_FEATURE_COLUMNS, "edge_id")
    feature_type = resolve_column(available_columns, SEGMENT_FEATURE_COLUMNS, "feature_type")
    geom = resolve_column(available_columns, SEGMENT_FEATURE_COLUMNS, "geom")
    state = resolve_optional_column(available_columns, SEGMENT_FEATURE_COLUMNS, "state")
    value_number = resolve_optional_column(available_columns, SEGMENT_FEATURE_COLUMNS, "value_number")
    state_select = f"{state}::text" if state else "NULL::text"
    value_number_select = value_number if value_number else "NULL::numeric"
    return f'''
SELECT
  {feature_id} AS feature_id,
  {edge_id} AS edge_id,
  {feature_type}::text AS feature_type,
  ST_AsText({geom}::geometry) AS geom_wkt,
  {state_select} AS state,
  {value_number_select} AS value_number
FROM segment_features
WHERE {feature_type}::text IN ('CROSSWALK', 'AUDIO_SIGNAL', 'BRAILLE_BLOCK', 'STAIRS')
ORDER BY {edge_id}, {feature_id}
'''


DEFAULT_NODES_SQL = build_road_nodes_sql(SNAKE_ROAD_NODE_COLUMNS)
DEFAULT_SEGMENTS_SQL = build_road_segments_sql(SNAKE_ROAD_SEGMENT_COLUMNS)

# `segment_features`는 선택적 보강 입력이다. 최종 라우팅 비용은 여전히
# `road_segments` 기준이며, feature row는 OSM export 전에 분할 지점과
# 상태값 덮어쓰기만 결정한다.
DEFAULT_FEATURES_SQL = build_segment_features_sql(SNAKE_SEGMENT_FEATURE_COLUMNS)

REQUIRED_NODE_FIELDS = {"vertex_id", "lon", "lat"}
REQUIRED_SEGMENT_FIELDS = {
    "edge_id",
    "from_node_id",
    "to_node_id",
    "geom_wkt",
    "walk_access",
    "avg_slope_percent",
    "width_meter",
    "braille_block_state",
    "audio_signal_state",
    "width_state",
    "surface_state",
    "stairs_state",
    "signal_state",
    "segment_type",
}

ENUM_VALUES = {
    "walk_access": {"YES", "NO", "UNKNOWN"},
    "braille_block_state": {"YES", "NO", "UNKNOWN"},
    "audio_signal_state": {"YES", "NO", "UNKNOWN"},
    "width_state": {"ADEQUATE_150", "ADEQUATE_120", "NARROW", "UNKNOWN"},
    "surface_state": {"PAVED", "UNPAVED", "UNKNOWN"},
    "stairs_state": {"YES", "NO", "UNKNOWN"},
    "signal_state": {"YES", "NO", "UNKNOWN"},
    "segment_type": {"CROSS_WALK", "SIDE_LINE"},
}

UNKNOWN_WARNING_THRESHOLD = 0.90
ENDPOINT_TOLERANCE = 0.000001
SPLIT_FRACTION_TOLERANCE = 0.000000001
GEOMETRY_DECIMAL_PLACES = 12
SIGNED_INT_MIN = 0
SIGNED_INT_MAX = 2**31 - 1


def jdbc_to_dsn(jdbc_url: str) -> dict:
    if not jdbc_url.startswith("jdbc:postgresql://"):
        raise ValueError("DB_URL must use jdbc:postgresql://host:port/database")
    parsed = urlparse(jdbc_url.replace("jdbc:postgresql://", "postgresql://", 1))
    if not parsed.hostname or not parsed.path:
        raise ValueError("DB_URL must include host and database name")
    query = parse_qs(parsed.query)
    return {
        "host": parsed.hostname,
        "port": parsed.port or 5432,
        "dbname": parsed.path.lstrip("/"),
        "user": os.getenv("DB_USERNAME") or os.getenv("POSTGRES_USER"),
        "password": os.getenv("DB_PASSWORD") or os.getenv("POSTGRES_PASSWORD"),
        "sslmode": query.get("sslmode", [os.getenv("DB_SSLMODE", "prefer")])[0],
    }


def connect():
    import psycopg2

    db_url = os.getenv("DB_URL")
    if db_url:
        dsn = jdbc_to_dsn(db_url)
    else:
        dsn = {
            "host": os.getenv("PGHOST", "postgres"),
            "port": int(os.getenv("PGPORT", "5432")),
            "dbname": os.getenv("PGDATABASE", os.getenv("POSTGRES_DB", "e102")),
            "user": os.getenv("PGUSER", os.getenv("DB_USERNAME", "e102")),
            "password": os.getenv("PGPASSWORD", os.getenv("DB_PASSWORD", "e102")),
            "sslmode": os.getenv("DB_SSLMODE", "prefer"),
        }
    return psycopg2.connect(**dsn)


def tag(parent, key, value):
    if value is None:
        return
    ET.SubElement(parent, "tag", {"k": key, "v": str(value)})


def safe_osm_way_id(edge_id):
    numeric = int(edge_id)
    return numeric if numeric > 0 else abs(numeric) + 1_000_000_000


def require_signed_int32(value, *, field_name, segment_id):
    numeric = int(value)
    if numeric < SIGNED_INT_MIN or numeric > SIGNED_INT_MAX:
        raise ValueError(
            f"road_segment edge_id={segment_id} has {field_name}={numeric} outside GraphHopper 31-bit non-negative encoded value range "
            f"[{SIGNED_INT_MIN}, {SIGNED_INT_MAX}]"
        )
    return numeric


def parse_linestring_wkt(value):
    if not value:
        return []
    text = value.strip()
    if text.upper().startswith("SRID="):
        text = text.split(";", 1)[1].strip()
    upper = text.upper()
    if not upper.startswith("LINESTRING"):
        return []
    body = text[text.find("(") + 1 : text.rfind(")")]
    points = []
    for token in body.split(","):
        parts = token.strip().split()
        if len(parts) < 2:
            continue
        lon, lat = float(parts[0]), float(parts[1])
        points.append((lon, lat))
    return points


def parse_point_wkt(value):
    if not value:
        return None
    text = value.strip()
    if text.upper().startswith("SRID="):
        text = text.split(";", 1)[1].strip()
    upper = text.upper()
    if not upper.startswith("POINT"):
        return None
    body = text[text.find("(") + 1 : text.rfind(")")]
    parts = body.strip().split()
    if len(parts) < 2:
        return None
    return (float(parts[0]), float(parts[1]))


def parse_feature_geometry_wkt(value):
    """segment 위로 투영할 수 있게 feature geometry 형태를 정규화한다."""
    point = parse_point_wkt(value)
    if point:
        return "POINT", [point]
    linestring = parse_linestring_wkt(value)
    if linestring:
        return "LINESTRING", linestring
    return None, []


def is_finite_coordinate(value):
    try:
        number = float(value)
    except (TypeError, ValueError):
        return False
    return math.isfinite(number)


def is_valid_lon_lat(lon, lat):
    if not is_finite_coordinate(lon) or not is_finite_coordinate(lat):
        return False
    return -180.0 <= float(lon) <= 180.0 and -90.0 <= float(lat) <= 90.0


def same_coordinate(left, right):
    return (
        abs(float(left[0]) - float(right[0])) <= ENDPOINT_TOLERANCE
        and abs(float(left[1]) - float(right[1])) <= ENDPOINT_TOLERANCE
    )


def normalize_export_value(value, fallback):
    if value is None:
        return fallback
    text = str(value)
    if text == "":
        return fallback
    return text


def normalize_feature_type(value):
    return normalize_export_value(value, "").strip().upper()


def normalize_feature_state(value):
    state = normalize_export_value(value, "").strip().upper()
    return state or None


def parse_feature_number(value):
    if value is None or value == "":
        return None
    try:
        number = float(value)
    except (TypeError, ValueError):
        return None
    return number if math.isfinite(number) else None


def derive_width_state(width_meter):
    """원천 보도 폭을 GraphHopper 표준 width enum으로 변환한다."""
    number = parse_feature_number(width_meter)
    if number is None:
        return "UNKNOWN"
    if number >= 1.50:
        return "ADEQUATE_150"
    if number >= 1.20:
        return "ADEQUATE_120"
    return "NARROW"


def new_issue(kind, level, message, samples=None):
    issue = {"kind": kind, "level": level, "message": message}
    if samples:
        issue["samples"] = [str(sample) for sample in samples[:10]]
    return issue


def add_issue(target, kind, level, message, samples=None):
    target.append(new_issue(kind, level, message, samples))


def coordinate_distance(left, right):
    return math.hypot(float(right[0]) - float(left[0]), float(right[1]) - float(left[1]))


def linestring_lengths(coords):
    cumulative = [0.0]
    for index in range(1, len(coords)):
        cumulative.append(cumulative[-1] + coordinate_distance(coords[index - 1], coords[index]))
    return cumulative


def point_at_fraction(coords, fraction):
    """segment linestring의 0..1 위치에 해당하는 좌표를 보간한다."""
    if not coords:
        return None
    fraction = max(0.0, min(1.0, float(fraction)))
    cumulative = linestring_lengths(coords)
    total = cumulative[-1]
    if total == 0.0:
        return coords[0]
    target = total * fraction
    for index in range(1, len(coords)):
        start_distance = cumulative[index - 1]
        end_distance = cumulative[index]
        if target <= end_distance or index == len(coords) - 1:
            segment_length = end_distance - start_distance
            if segment_length == 0.0:
                return coords[index]
            ratio = (target - start_distance) / segment_length
            start = coords[index - 1]
            end = coords[index]
            return (
                start[0] + (end[0] - start[0]) * ratio,
                start[1] + (end[1] - start[1]) * ratio,
            )
    return coords[-1]


def project_point_fraction(coords, point):
    """feature 좌표를 segment 위에 투영하고 0..1 위치값을 반환한다.

    Feature geometry는 원천 segment vertex와 정확히 맞지 않는 점 또는 짧은 선일 수 있다.
    투영을 사용하면 좌표가 완전히 일치하지 않아도 segment 위 가장 가까운 위치에서
    export 분할을 수행할 수 있다.
    """
    cumulative = linestring_lengths(coords)
    total = cumulative[-1] if cumulative else 0.0
    if total == 0.0:
        return 0.0

    best_distance = None
    best_along = 0.0
    px, py = float(point[0]), float(point[1])
    for index in range(1, len(coords)):
        start = coords[index - 1]
        end = coords[index]
        sx, sy = float(start[0]), float(start[1])
        ex, ey = float(end[0]), float(end[1])
        dx, dy = ex - sx, ey - sy
        length_sq = dx * dx + dy * dy
        if length_sq == 0.0:
            continue
        ratio = ((px - sx) * dx + (py - sy) * dy) / length_sq
        ratio = max(0.0, min(1.0, ratio))
        projected = (sx + dx * ratio, sy + dy * ratio)
        distance = coordinate_distance(projected, point)
        along = cumulative[index - 1] + math.sqrt(length_sq) * ratio
        if best_distance is None or distance < best_distance:
            best_distance = distance
            best_along = along
    return best_along / total


def linestring_between_fractions(coords, start_fraction, end_fraction):
    """두 분할 위치 사이의 child segment geometry를 만든다."""
    if end_fraction <= start_fraction:
        return []
    result = [point_at_fraction(coords, start_fraction)]
    cumulative = linestring_lengths(coords)
    total = cumulative[-1] if cumulative else 0.0
    if total > 0.0:
        start_distance = total * start_fraction
        end_distance = total * end_fraction
        for index in range(1, len(coords) - 1):
            if start_distance + SPLIT_FRACTION_TOLERANCE < cumulative[index] < end_distance - SPLIT_FRACTION_TOLERANCE:
                result.append(coords[index])
    result.append(point_at_fraction(coords, end_fraction))
    return result


def format_linestring_wkt(coords):
    formatted = []
    for lon, lat in coords:
        formatted.append(f"{float(lon):.{GEOMETRY_DECIMAL_PLACES}f} {float(lat):.{GEOMETRY_DECIMAL_PLACES}f}")
    return f'LINESTRING({", ".join(formatted)})'


def same_export_coordinate(left, right):
    return (
        round(float(left[0]), GEOMETRY_DECIMAL_PLACES)
        == round(float(right[0]), GEOMETRY_DECIMAL_PLACES)
        and round(float(left[1]), GEOMETRY_DECIMAL_PLACES)
        == round(float(right[1]), GEOMETRY_DECIMAL_PLACES)
    )


def is_valid_export_linestring(coords):
    if len(coords) < 2:
        return False
    exported = parse_linestring_wkt(format_linestring_wkt(coords))
    return (
        len(exported) >= 2
        and exported[0] != exported[-1]
        and all(is_valid_lon_lat(lon, lat) for lon, lat in exported)
    )


def normalize_split_fractions(coords, split_fractions):
    """Merge split boundaries that would create collapsed exported geometry."""
    ordered = sorted(split_fractions)
    normalized = []
    for fraction in ordered:
        point = point_at_fraction(coords, fraction)
        if point is None:
            continue
        if not normalized:
            normalized.append(fraction)
            continue
        previous_point = point_at_fraction(coords, normalized[-1])
        child_coords = linestring_between_fractions(coords, normalized[-1], fraction)
        if same_export_coordinate(previous_point, point) or not is_valid_export_linestring(child_coords):
            if abs(fraction - 1.0) <= SPLIT_FRACTION_TOLERANCE:
                normalized[-1] = 1.0
            continue
        normalized.append(fraction)
    return normalized


def feature_fraction_range(segment_coords, feature):
    """feature geometry가 영향을 주는 segment 위치 범위로 변환한다."""
    geometry_type, feature_coords = parse_feature_geometry_wkt(feature.get("geom_wkt"))
    if not feature_coords:
        return None
    fractions = [project_point_fraction(segment_coords, point) for point in feature_coords]
    start = max(0.0, min(fractions))
    end = min(1.0, max(fractions))
    return (start, end, geometry_type)


def ranges_overlap(left_start, left_end, right_start, right_end):
    """child segment 구간에 feature 상태값을 적용해야 하는지 판단한다."""
    if right_start == right_end:
        if right_start == 1.0:
            return abs(left_end - 1.0) <= SPLIT_FRACTION_TOLERANCE
        return left_start - SPLIT_FRACTION_TOLERANCE <= right_start < left_end - SPLIT_FRACTION_TOLERANCE
    return max(left_start, right_start) < min(left_end, right_end) - SPLIT_FRACTION_TOLERANCE


def apply_feature_state(segment, feature):
    """하나의 segment feature를 child segment의 최종 export 상태값에 반영한다."""
    feature_type = normalize_feature_type(feature.get("feature_type"))
    state = normalize_feature_state(feature.get("state"))

    if feature_type == "CROSSWALK":
        segment["segment_type"] = "CROSS_WALK"
        if state in ENUM_VALUES["signal_state"]:
            segment["signal_state"] = state
        return
    if feature_type == "AUDIO_SIGNAL":
        segment["audio_signal_state"] = state if state in ENUM_VALUES["audio_signal_state"] else "YES"
        return
    if feature_type == "BRAILLE_BLOCK":
        segment["braille_block_state"] = state if state in ENUM_VALUES["braille_block_state"] else "YES"
        return
    if feature_type == "STAIRS":
        segment["stairs_state"] = state if state in ENUM_VALUES["stairs_state"] else "YES"
        return


def apply_segment_features_to_export(nodes, segments, features):
    """feature geometry 기준으로 원천 segment를 분할하고 export 가능한 row를 반환한다.

    모든 분할 경계에 synthetic node를 만들어 기존 topology validator가 생성된
    child segment endpoint까지 검증할 수 있게 한다. Synthetic edge id는 DB가
    소유한 edge id와 충돌하지 않도록 음수로 둔다.
    """
    if not features:
        return nodes, segments

    features_by_edge = defaultdict(list)
    for feature in features:
        if normalize_feature_type(feature.get("feature_type")) not in POSITION_EVENT_FEATURE_TYPES:
            continue
        features_by_edge[str(feature.get("edge_id"))].append(feature)

    output_nodes = [dict(node) for node in nodes]
    output_segments = []
    existing_node_ids = [int(node["vertex_id"]) for node in output_nodes if str(node.get("vertex_id", "")).lstrip("-").isdigit()]
    existing_edge_ids = [int(segment["edge_id"]) for segment in segments if str(segment.get("edge_id", "")).lstrip("-").isdigit()]
    next_synthetic_node_id = (max(existing_node_ids) + 1) if existing_node_ids else 1
    next_synthetic_edge_id = (min(existing_edge_ids) - 1) if existing_edge_ids else -1

    for segment in segments:
        segment_features = features_by_edge.get(str(segment.get("edge_id")), [])
        coords = parse_linestring_wkt(segment.get("geom_wkt"))
        if len(coords) < 2 or not segment_features:
            patched_segment = dict(segment)
            for feature in segment_features:
                feature_range = feature_fraction_range(coords, feature) if coords else None
                if feature_range and ranges_overlap(0.0, 1.0, feature_range[0], feature_range[1]):
                    apply_feature_state(patched_segment, feature)
            output_segments.append(patched_segment)
            continue

        feature_ranges = []
        split_fractions = {0.0, 1.0}
        for feature in segment_features:
            feature_range = feature_fraction_range(coords, feature)
            if not feature_range:
                continue
            start_fraction, end_fraction, _ = feature_range
            feature_ranges.append((feature, start_fraction, end_fraction))
            for fraction in (start_fraction, end_fraction):
                if SPLIT_FRACTION_TOLERANCE < fraction < 1.0 - SPLIT_FRACTION_TOLERANCE:
                    split_fractions.add(round(fraction, 12))

        # 모든 feature가 segment 전체를 덮으면 topology 분할은 필요 없고,
        # 접근성 상태값 덮어쓰기만 적용한다.
        ordered_fractions = normalize_split_fractions(coords, split_fractions)
        if len(ordered_fractions) <= 2:
            patched_segment = dict(segment)
            for feature, start_fraction, end_fraction in feature_ranges:
                if ranges_overlap(0.0, 1.0, start_fraction, end_fraction):
                    apply_feature_state(patched_segment, feature)
            output_segments.append(patched_segment)
            continue

        boundary_nodes = {0.0: segment["from_node_id"], 1.0: segment["to_node_id"]}
        for fraction in ordered_fractions[1:-1]:
            point = point_at_fraction(coords, fraction)
            synthetic_node = {
                "vertex_id": next_synthetic_node_id,
                "lon": point[0],
                "lat": point[1],
            }
            output_nodes.append(synthetic_node)
            boundary_nodes[fraction] = next_synthetic_node_id
            next_synthetic_node_id += 1

        for index in range(1, len(ordered_fractions)):
            # 인접한 두 분할 위치는 각각 독립 접근성 상태를 가진
            # 최종 GraphHopper way 하나가 된다.
            start_fraction = ordered_fractions[index - 1]
            end_fraction = ordered_fractions[index]
            child_coords = linestring_between_fractions(coords, start_fraction, end_fraction)
            if not is_valid_export_linestring(child_coords):
                if output_segments and output_segments[-1].get("source_edge_id") == segment.get("edge_id"):
                    for feature, feature_start, feature_end in feature_ranges:
                        if ranges_overlap(start_fraction, end_fraction, feature_start, feature_end):
                            apply_feature_state(output_segments[-1], feature)
                continue
            child_geom_wkt = format_linestring_wkt(child_coords)
            child = dict(segment)
            child["edge_id"] = next_synthetic_edge_id
            child["source_edge_id"] = segment["edge_id"]
            child["from_node_id"] = boundary_nodes[start_fraction]
            child["to_node_id"] = boundary_nodes[end_fraction]
            child["geom_wkt"] = child_geom_wkt
            next_synthetic_edge_id -= 1
            for feature, feature_start, feature_end in feature_ranges:
                if ranges_overlap(start_fraction, end_fraction, feature_start, feature_end):
                    apply_feature_state(child, feature)
            output_segments.append(child)

    return output_nodes, output_segments


def count_components(segments):
    graph = defaultdict(set)
    routeable_edges = []
    for segment in segments:
        if normalize_export_value(segment.get("walk_access"), "UNKNOWN") == "NO":
            continue
        try:
            from_node = int(segment["from_node_id"])
            to_node = int(segment["to_node_id"])
        except (KeyError, TypeError, ValueError):
            continue
        graph[from_node].add(to_node)
        graph[to_node].add(from_node)
        routeable_edges.append((from_node, to_node))

    seen = set()
    sizes = []
    for node_id in graph:
        if node_id in seen:
            continue
        queue = deque([node_id])
        seen.add(node_id)
        size = 0
        while queue:
            current = queue.popleft()
            size += 1
            for neighbor in graph[current]:
                if neighbor not in seen:
                    seen.add(neighbor)
                    queue.append(neighbor)
        sizes.append(size)
    return sorted(sizes, reverse=True), len(routeable_edges)


def validate_graph(nodes, segments, output=None):
    blockers = []
    warnings = []
    node_count = len(nodes)
    segment_count = len(segments)

    node_fields = set(nodes[0].keys()) if nodes else set()
    segment_fields = set(segments[0].keys()) if segments else set()
    missing_node_fields = sorted(REQUIRED_NODE_FIELDS - node_fields)
    missing_segment_fields = sorted(REQUIRED_SEGMENT_FIELDS - segment_fields)
    if missing_node_fields:
        add_issue(blockers, "missing_node_fields", "blocker", "road_nodes export is missing required fields", missing_node_fields)
    if missing_segment_fields:
        add_issue(
            blockers,
            "missing_segment_fields",
            "blocker",
            "road_segments export is missing required fields",
            missing_segment_fields,
        )

    vertex_counter = Counter()
    node_lookup = {}
    invalid_nodes = []
    for node in nodes:
        try:
            vertex_id = int(node["vertex_id"])
        except (KeyError, TypeError, ValueError):
            invalid_nodes.append(node.get("vertex_id"))
            continue
        vertex_counter[vertex_id] += 1
        if not is_valid_lon_lat(node.get("lon"), node.get("lat")):
            invalid_nodes.append(vertex_id)
            continue
        node_lookup[vertex_id] = (float(node["lon"]), float(node["lat"]))

    duplicate_vertices = [vertex_id for vertex_id, count in vertex_counter.items() if count > 1]
    if duplicate_vertices:
        add_issue(blockers, "duplicate_vertex_id", "blocker", "duplicate road_nodes vertex_id values found", duplicate_vertices)
    if invalid_nodes:
        add_issue(blockers, "invalid_node_geometry", "blocker", "node coordinates are missing or outside lon/lat bounds", invalid_nodes)

    edge_counter = Counter()
    dangling_edges = []
    invalid_geometry_edges = []
    endpoint_mismatch_edges = []
    self_loop_edges = []
    enum_violations = defaultdict(list)
    invalid_db_edge_id_ranges = []
    routeable_edges = 0
    unknown_counts = Counter()
    enum_counts = {field: Counter() for field in ENUM_VALUES}

    for segment in segments:
        edge_id = segment.get("edge_id")
        edge_counter[edge_id] += 1
        try:
            from_node_id = int(segment["from_node_id"])
            to_node_id = int(segment["to_node_id"])
        except (KeyError, TypeError, ValueError):
            dangling_edges.append(edge_id)
            continue

        if from_node_id == to_node_id:
            self_loop_edges.append(edge_id)
        if from_node_id not in node_lookup or to_node_id not in node_lookup:
            dangling_edges.append(edge_id)
        try:
            require_signed_int32(segment.get("source_edge_id", edge_id), field_name="db_edge_id", segment_id=edge_id)
        except (TypeError, ValueError):
            invalid_db_edge_id_ranges.append(edge_id)

        coords = parse_linestring_wkt(segment.get("geom_wkt"))
        if len(coords) < 2 or coords[0] == coords[-1] or not all(is_valid_lon_lat(lon, lat) for lon, lat in coords):
            invalid_geometry_edges.append(edge_id)
        elif from_node_id in node_lookup and to_node_id in node_lookup:
            if not same_coordinate(coords[0], node_lookup[from_node_id]) or not same_coordinate(coords[-1], node_lookup[to_node_id]):
                endpoint_mismatch_edges.append(edge_id)

        for field, allowed_values in ENUM_VALUES.items():
            fallback = "SIDE_LINE" if field == "segment_type" else "UNKNOWN"
            value = normalize_export_value(segment.get(field), fallback)
            enum_counts[field][value] += 1
            if value == "UNKNOWN":
                unknown_counts[field] += 1
            if value not in allowed_values:
                enum_violations[field].append(edge_id)

        if normalize_export_value(segment.get("walk_access"), "UNKNOWN") != "NO":
            routeable_edges += 1

    duplicate_edges = [edge_id for edge_id, count in edge_counter.items() if count > 1]
    if duplicate_edges:
        add_issue(blockers, "duplicate_edge_id", "blocker", "duplicate road_segments edge_id values found", duplicate_edges)
    if dangling_edges:
        add_issue(blockers, "missing_node_reference", "blocker", "road_segments reference missing road_nodes", dangling_edges)
    if invalid_geometry_edges:
        add_issue(blockers, "invalid_segment_geometry", "blocker", "segment geometry must be LINESTRING with at least two valid points", invalid_geometry_edges)
    if endpoint_mismatch_edges:
        add_issue(blockers, "endpoint_mismatch", "blocker", "segment endpoints do not match from/to node coordinates", endpoint_mismatch_edges)
    if self_loop_edges:
        add_issue(blockers, "self_loop", "blocker", "segment from_node_id and to_node_id must differ", self_loop_edges)
    if invalid_db_edge_id_ranges:
        add_issue(
            blockers,
            "db_edge_id_out_of_range",
            "blocker",
            "db_edge_id/source_edge_id must fit the GraphHopper 31-bit non-negative encoded value range",
            invalid_db_edge_id_ranges,
        )
    for field, edge_ids in enum_violations.items():
        add_issue(blockers, "enum_violation", "blocker", f"{field} contains values outside the GraphHopper contract", edge_ids)
    if routeable_edges == 0:
        add_issue(blockers, "no_routeable_edge", "blocker", "at least one segment must have walk_access other than NO")

    component_sizes, routeable_edge_count = count_components(segments)
    if len(component_sizes) > 1:
        add_issue(
            warnings,
            "small_component",
            "warning",
            "routeable road network has disconnected components",
            component_sizes[1:6],
        )

    for field, count in unknown_counts.items():
        ratio = count / segment_count if segment_count else 0
        if ratio >= UNKNOWN_WARNING_THRESHOLD:
            add_issue(
                warnings,
                "high_unknown_ratio",
                "warning",
                f"{field} UNKNOWN ratio is {ratio:.2%}",
                [f"{count}/{segment_count}"],
            )

    return {
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "status": "PASS" if not blockers else "FAIL",
        "output": output,
        "summary": {
            "nodeCount": node_count,
            "segmentCount": segment_count,
            "routeableEdgeCount": routeable_edges,
            "routeableComponentCount": len(component_sizes),
            "largestRouteableComponentNodeCount": component_sizes[0] if component_sizes else 0,
            "routeableEdgeCountForComponentScan": routeable_edge_count,
            "blockerCount": len(blockers),
            "warningCount": len(warnings),
        },
        "enumCounts": {field: dict(counter) for field, counter in enum_counts.items()},
        "blockers": blockers,
        "warnings": warnings,
    }


def write_json_report(path, report):
    if not path:
        return
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    with open(path, "w", encoding="utf-8") as report_file:
        json.dump(report, report_file, ensure_ascii=False, indent=2)
        report_file.write("\n")


def write_osm(nodes, segments, output):
    # exporter는 PostGIS camelCase 컬럼과 GraphHopper custom parser 사이의 경계다.
    # tag 이름은 Graphhopper_pipeline.md의 `ieum:*` 계약과 맞춰야 한다.
    root = ET.Element("osm", {"version": "0.6", "generator": "e102-postgis-graphhopper-export"})
    node_id_map = {int(node["vertex_id"]): index + 1 for index, node in enumerate(nodes)}
    next_synthetic_node_id = len(node_id_map) + 1

    for node in nodes:
        osm_node = ET.SubElement(
            root,
            "node",
            {
                "id": str(node_id_map[int(node["vertex_id"])]),
                "lat": f'{float(node["lat"]):.{GEOMETRY_DECIMAL_PLACES}f}',
                "lon": f'{float(node["lon"]):.{GEOMETRY_DECIMAL_PLACES}f}',
            },
        )
        tag(osm_node, "ieum:vertex_id", node["vertex_id"])

    segment_refs = []
    for segment in segments:
        refs = [node_id_map[int(segment["from_node_id"])]]
        coords = parse_linestring_wkt(segment.get("geom_wkt"))
        if len(coords) < 2:
            raise ValueError(f'road_segment edge_id={segment["edge_id"]} has invalid LINESTRING geometry')
        for lon, lat in coords[1:-1]:
            synthetic_id = next_synthetic_node_id
            next_synthetic_node_id += 1
            ET.SubElement(
                root,
                "node",
                {
                    "id": str(synthetic_id),
                    "lat": f"{lat:.{GEOMETRY_DECIMAL_PLACES}f}",
                    "lon": f"{lon:.{GEOMETRY_DECIMAL_PLACES}f}",
                },
            )
            refs.append(synthetic_id)
        refs.append(node_id_map[int(segment["to_node_id"])])
        segment_refs.append((segment, refs))

    for segment, refs in segment_refs:
        way = ET.SubElement(root, "way", {"id": str(safe_osm_way_id(segment["edge_id"]))})
        for ref in refs:
            ET.SubElement(way, "nd", {"ref": str(ref)})
        tag(way, "highway", "footway")
        tag(way, "foot", "yes")
        tag(way, "oneway", "no")
        tag(way, "ieum:edge_id", segment["edge_id"])
        tag(
            way,
            "ieum:db_edge_id",
            require_signed_int32(
                segment.get("source_edge_id", segment["edge_id"]),
                field_name="db_edge_id",
                segment_id=segment["edge_id"],
            ),
        )
        tag(way, "ieum:walk_access", normalize_export_value(segment.get("walk_access"), "UNKNOWN"))
        tag(way, "ieum:avg_slope_percent", normalize_export_value(segment.get("avg_slope_percent"), "0.0"))
        tag(way, "ieum:width_meter", normalize_export_value(segment.get("width_meter"), "0.0"))
        tag(way, "ieum:braille_block_state", normalize_export_value(segment.get("braille_block_state"), "UNKNOWN"))
        tag(way, "ieum:audio_signal_state", normalize_export_value(segment.get("audio_signal_state"), "UNKNOWN"))
        tag(way, "ieum:width_state", normalize_export_value(segment.get("width_state"), "UNKNOWN"))
        tag(way, "ieum:surface_state", normalize_export_value(segment.get("surface_state"), "UNKNOWN"))
        tag(way, "ieum:stairs_state", normalize_export_value(segment.get("stairs_state"), "UNKNOWN"))
        tag(way, "ieum:signal_state", normalize_export_value(segment.get("signal_state"), "UNKNOWN"))
        tag(way, "ieum:segment_type", normalize_export_value(segment.get("segment_type"), "SIDE_LINE"))

    tree = ET.ElementTree(root)
    ET.indent(tree, space="  ")
    tree.write(output, encoding="utf-8", xml_declaration=True)


def fetch_dicts(conn, sql):
    with conn.cursor() as cur:
        cur.execute(sql)
        columns = [desc[0] for desc in cur.description]
        return [dict(zip(columns, row)) for row in cur.fetchall()]


def table_exists(conn, table_name):
    with conn.cursor() as cur:
        cur.execute("SELECT to_regclass(%s)", (f"public.{table_name}",))
        return cur.fetchone()[0] is not None


def fetch_table_columns(conn, table_name):
    with conn.cursor() as cur:
        cur.execute(
            """
            SELECT column_name
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = %s
            """,
            (table_name,),
        )
        return {row[0] for row in cur.fetchall()}


def fetch_segment_features(conn):
    features_sql = os.getenv("GRAPHHOPPER_SEGMENT_FEATURES_SQL")
    if features_sql:
        return fetch_dicts(conn, features_sql)
    if not table_exists(conn, "segment_features"):
        return []
    return fetch_dicts(conn, build_segment_features_sql(fetch_table_columns(conn, "segment_features")))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", required=True)
    parser.add_argument("--report-json")
    args = parser.parse_args()

    nodes_sql_override = os.getenv("GRAPHHOPPER_ROAD_NODES_SQL")
    segments_sql_override = os.getenv("GRAPHHOPPER_ROAD_SEGMENTS_SQL")

    with connect() as conn:
        nodes_sql = nodes_sql_override or build_road_nodes_sql(fetch_table_columns(conn, "road_nodes"))
        segments_sql = segments_sql_override or build_road_segments_sql(fetch_table_columns(conn, "road_segments"))
        nodes = fetch_dicts(conn, nodes_sql)
        segments = fetch_dicts(conn, segments_sql)
        features = fetch_segment_features(conn)

    if not nodes:
        print("No road_nodes rows found for GraphHopper export.", file=sys.stderr)
        return 1
    if not segments:
        print("No road_segments rows found for GraphHopper export.", file=sys.stderr)
        return 1

    nodes, segments = apply_segment_features_to_export(nodes, segments, features)
    report = validate_graph(nodes, segments, args.output)
    write_json_report(args.report_json, report)
    if report["blockers"]:
        sample = "; ".join(f'{item["kind"]}: {item["message"]}' for item in report["blockers"][:5])
        print(f"GraphHopper export validation failed. {sample}", file=sys.stderr)
        return 1

    os.makedirs(os.path.dirname(os.path.abspath(args.output)), exist_ok=True)
    write_osm(nodes, segments, args.output)
    print(
        f'Exported {len(nodes)} nodes and {len(segments)} segments to {args.output}. '
        f'validation_status={report["status"]}'
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
