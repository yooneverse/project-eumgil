#!/usr/bin/env python3
"""
접근성 원천 CSV를 source_features에 영속 저장하고 road_segments와 공간 매칭해 segment_features와 집계 컬럼에 반영한다.

흐름은 csv매핑.md 기준이다. .ai/LOCAL의 여러 원천 CSV를 읽어 feature 단위 staging
row로 정규화하고, PostGIS transaction 안에서 ST_DWithin 매칭, ambiguous 분리,
source_features 전체 교체, segment_features 전체 교체, road_segments 접근성 컬럼 update, JSON report 생성을
한 번에 처리한다.
"""

from __future__ import annotations

import argparse
import csv
import io
import json
import os
import re
import sys
from collections import Counter
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Any
from urllib.parse import parse_qs, urlparse


FEATURE_TYPES = {
    "CROSSWALK",
    "AUDIO_SIGNAL",
    "BRAILLE_BLOCK",
    "SIGNAL",
    "SLOPE",
    "STAIRS",
    "SURFACE",
    "WALK_ACCESS",
    "WIDTH",
}
POSITION_EVENT_FEATURE_TYPES = {
    "CROSSWALK",
    "AUDIO_SIGNAL",
    "BRAILLE_BLOCK",
    "STAIRS",
}

YES_NO_UNKNOWN = {"YES", "NO", "UNKNOWN"}
WIDTH_STATES = {"ADEQUATE_150", "ADEQUATE_120", "NARROW", "UNKNOWN"}
SURFACE_STATES = {"PAVED", "UNPAVED", "UNKNOWN"}


@dataclass(frozen=True)
class SourceFeatureRow:
    source_row_id: int
    match_source_id: int
    source_file: str
    source_id: str
    csv_line_no: int
    feature_type: str | None
    geom_ewkt: str
    state: str | None
    value_number: Decimal | None
    threshold_meter: Decimal
    prefer_crosswalk: bool
    update_walk_access: bool
    geometry_kind: str


@dataclass(frozen=True)
class ParseIssue:
    source_file: str
    csv_line_no: int
    source_id: str
    reason: str


@dataclass(frozen=True)
class SourceDefinition:
    file_name: str
    threshold_meter: Decimal
    prefer_crosswalk: bool
    parser_name: str


SOURCE_DEFINITIONS = [
    SourceDefinition("인도&인도폭.csv", Decimal("10"), False, "sidewalk_width"),
    SourceDefinition("이면도로.csv", Decimal("10"), False, "shared_local_road"),
    SourceDefinition("경사도&표면타입.csv", Decimal("20"), False, "slope_surface"),
    SourceDefinition("횡단보도_신호등.csv", Decimal("20"), True, "crosswalk_signal"),
    SourceDefinition("횡단보도_음향신호기.csv", Decimal("30"), True, "audio_signal"),
    SourceDefinition("계단.csv", Decimal("2"), False, "stairs"),
    SourceDefinition("점자블록.csv", Decimal("0"), True, "braille_block"),
]

REQUIRED_HEADER_GROUPS = {
    "sidewalk_width": [("wkt", "geom", "geometryWkt")],
    "shared_local_road": [("geometryWkt", "wkt", "geom")],
    "slope_surface": [("geometryWkt", "wkt", "geom")],
    "crosswalk_signal": [("point", "geom", "geometryWkt", "wkt")],
    "audio_signal": [("point", "geom", "geometryWkt", "wkt", "lat", "latitude", "위도", "y", "Y")],
    "stairs": [("geometryWkt", "geom", "wkt")],
    "braille_block": [("geom", "point", "geometryWkt", "wkt")],
}


def position_event_feature_type_sql() -> str:
    return ", ".join(f"'{feature_type}'" for feature_type in sorted(POSITION_EVENT_FEATURE_TYPES))


def blank_to_none(value: Any) -> str | None:
    if value is None:
        return None
    text = str(value).strip()
    if not text or text.upper() in {"NULL", "NONE", "N/A", "NA"}:
        return None
    return text


def first_value(row: dict[str, str], *keys: str) -> str | None:
    for key in keys:
        value = blank_to_none(row.get(key))
        if value is not None:
            return value
    return None


def parse_decimal(value: Any) -> Decimal | None:
    text = blank_to_none(value)
    if text is None:
        return None
    text = text.replace(",", "")
    try:
        return Decimal(text)
    except InvalidOperation:
        return None


def normalize_bool_state(value: Any) -> str:
    text = blank_to_none(value)
    if text is None:
        return "UNKNOWN"
    normalized = text.strip().upper()
    if normalized in {"Y", "YES", "TRUE", "1", "있음", "유", "정상", "정상동작"}:
        return "YES"
    if normalized in {"N", "NO", "FALSE", "0", "없음", "무", "미설치"}:
        return "NO"
    return "UNKNOWN"


def derive_width_state(width_meter: Decimal | None) -> str:
    if width_meter is None:
        return "UNKNOWN"
    if width_meter >= Decimal("1.50"):
        return "ADEQUATE_150"
    if width_meter >= Decimal("1.20"):
        return "ADEQUATE_120"
    return "NARROW"


def normalize_surface_state(*values: Any) -> str:
    joined = " ".join(blank_to_none(value) or "" for value in values).strip()
    if not joined:
        return "UNKNOWN"

    upper = joined.upper()
    if any(token in upper for token in ["UNPAVED", "DIRT", "GRAVEL", "SOIL"]):
        return "UNPAVED"
    if any(token in upper for token in ["PAVED", "ASPHALT", "CONCRETE", "BLOCK"]):
        return "PAVED"

    if any(token in joined for token in ["비포장", "흙", "자갈", "마사"]):
        return "UNPAVED"
    if any(token in joined for token in ["포장", "아스팔트", "아스콘", "콘크리트", "블록", "보도블럭"]):
        return "PAVED"
    return "UNKNOWN"


def normalize_ewkt(value: Any) -> str | None:
    text = blank_to_none(value)
    if text is None:
        return None
    text = text.strip()
    if text.upper().startswith("SRID=4326;"):
        return "SRID=4326;" + text.split(";", 1)[1]
    if re.match(r"^(POINT|LINESTRING|POLYGON|MULTIPOINT|MULTILINESTRING|MULTIPOLYGON)\s*\(", text, re.I):
        return f"SRID=4326;{text}"
    return None


def point_ewkt_from_lat_lng(row: dict[str, str]) -> str | None:
    lat = parse_decimal(first_value(row, "lat", "latitude", "위도", "y", "Y"))
    lng = parse_decimal(first_value(row, "lng", "lon", "longitude", "경도", "x", "X"))
    if lat is None or lng is None:
        return None
    return f"SRID=4326;POINT({lng} {lat})"


def geometry_from_row(row: dict[str, str], *keys: str) -> tuple[str | None, str]:
    for key in keys:
        ewkt = normalize_ewkt(row.get(key))
        if ewkt is not None:
            return ewkt, geometry_kind(ewkt)
    point = point_ewkt_from_lat_lng(row)
    if point is not None:
        return point, "POINT"
    return None, "UNKNOWN"


def geometry_kind(ewkt: str) -> str:
    wkt = ewkt.split(";", 1)[1] if ";" in ewkt else ewkt
    return wkt.split("(", 1)[0].strip().upper()


def source_id_from_row(row: dict[str, str], line_no: int) -> str:
    return first_value(row, "sourceId", "segmentId", "ufid", "handoffEdgeId", "id", "ID") or f"line:{line_no}"


def validate_csv_headers(definition: SourceDefinition, fieldnames: list[str] | None) -> list[ParseIssue]:
    if not fieldnames:
        return [ParseIssue(definition.file_name, 1, "", "CSV header not found")]

    present_headers = {header.strip() for header in fieldnames if header and header.strip()}
    issues: list[ParseIssue] = []
    for header_group in REQUIRED_HEADER_GROUPS[definition.parser_name]:
        if not any(header in present_headers for header in header_group):
            issues.append(
                ParseIssue(
                    definition.file_name,
                    1,
                    "",
                    "required CSV header missing: one of " + ", ".join(header_group),
                )
            )
    return issues


def width_threshold(width_meter: Decimal | None) -> Decimal:
    if width_meter is None:
        return Decimal("20")
    calculated = width_meter / Decimal("2") + Decimal("2")
    if calculated < Decimal("10"):
        return Decimal("10")
    if calculated > Decimal("20"):
        return Decimal("20")
    return calculated


class FeatureBuilder:
    def __init__(self) -> None:
        self.next_id = 1

    def match_source_id(self) -> int:
        return self.next_id

    def row(
        self,
        *,
        match_source_id: int,
        source_file: str,
        source_id: str,
        csv_line_no: int,
        feature_type: str | None,
        geom_ewkt: str,
        state: str | None,
        value_number: Decimal | None,
        threshold_meter: Decimal,
        prefer_crosswalk: bool,
        update_walk_access: bool = False,
    ) -> SourceFeatureRow:
        if feature_type is not None and feature_type not in FEATURE_TYPES:
            raise ValueError(f"unsupported feature_type: {feature_type}")
        row = SourceFeatureRow(
            source_row_id=self.next_id,
            match_source_id=match_source_id,
            source_file=source_file,
            source_id=source_id,
            csv_line_no=csv_line_no,
            feature_type=feature_type,
            geom_ewkt=geom_ewkt,
            state=state,
            value_number=value_number,
            threshold_meter=threshold_meter,
            prefer_crosswalk=prefer_crosswalk,
            update_walk_access=update_walk_access,
            geometry_kind=geometry_kind(geom_ewkt),
        )
        self.next_id += 1
        return row


def parse_sidewalk_width(
    definition: SourceDefinition,
    row: dict[str, str],
    line_no: int,
    builder: FeatureBuilder,
) -> tuple[list[SourceFeatureRow], ParseIssue | None]:
    geom, _ = geometry_from_row(row, "wkt", "geom", "geometryWkt")
    source_id = source_id_from_row(row, line_no)
    if geom is None:
        return [], ParseIssue(definition.file_name, line_no, source_id, "geometry not found")
    match_source_id = builder.match_source_id()

    features = [
        builder.row(
            match_source_id=match_source_id,
            source_file=definition.file_name,
            source_id=source_id,
            csv_line_no=line_no,
            feature_type="WALK_ACCESS",
            geom_ewkt=geom,
            state="YES",
            value_number=None,
            threshold_meter=definition.threshold_meter,
            prefer_crosswalk=definition.prefer_crosswalk,
            update_walk_access=True,
        )
    ]
    width = parse_decimal(row.get("widthMeter"))
    if width is not None:
        features.append(
            builder.row(
                match_source_id=match_source_id,
                source_file=definition.file_name,
                source_id=source_id,
                csv_line_no=line_no,
                feature_type="WIDTH",
                geom_ewkt=geom,
                state=derive_width_state(width),
                value_number=width,
                threshold_meter=definition.threshold_meter,
                prefer_crosswalk=definition.prefer_crosswalk,
            )
        )
    surface = normalize_surface_state(row.get("surfaceLabel"), row.get("surfaceCode"))
    if surface != "UNKNOWN":
        features.append(
            builder.row(
                match_source_id=match_source_id,
                source_file=definition.file_name,
                source_id=source_id,
                csv_line_no=line_no,
                feature_type="SURFACE",
                geom_ewkt=geom,
                state=surface,
                value_number=None,
                threshold_meter=definition.threshold_meter,
                prefer_crosswalk=definition.prefer_crosswalk,
            )
        )
    return features, None


def parse_shared_local_road(
    definition: SourceDefinition,
    row: dict[str, str],
    line_no: int,
    builder: FeatureBuilder,
) -> tuple[list[SourceFeatureRow], ParseIssue | None]:
    geom, _ = geometry_from_row(row, "geometryWkt", "wkt", "geom")
    source_id = source_id_from_row(row, line_no)
    if geom is None:
        return [], ParseIssue(definition.file_name, line_no, source_id, "geometry not found")
    match_source_id = builder.match_source_id()

    features = [
        builder.row(
            match_source_id=match_source_id,
            source_file=definition.file_name,
            source_id=source_id,
            csv_line_no=line_no,
            feature_type="WALK_ACCESS",
            geom_ewkt=geom,
            state="YES",
            value_number=None,
            threshold_meter=definition.threshold_meter,
            prefer_crosswalk=definition.prefer_crosswalk,
            update_walk_access=True,
        )
    ]
    slope = parse_decimal(row.get("slopePercent"))
    if slope is not None:
        features.append(
            builder.row(
                match_source_id=match_source_id,
                source_file=definition.file_name,
                source_id=source_id,
                csv_line_no=line_no,
                feature_type="SLOPE",
                geom_ewkt=geom,
                state=None,
                value_number=slope,
                threshold_meter=definition.threshold_meter,
                prefer_crosswalk=definition.prefer_crosswalk,
            )
        )
    surface = normalize_surface_state(row.get("surfaceLabel"), row.get("surfaceCode"))
    if surface != "UNKNOWN":
        features.append(
            builder.row(
                match_source_id=match_source_id,
                source_file=definition.file_name,
                source_id=source_id,
                csv_line_no=line_no,
                feature_type="SURFACE",
                geom_ewkt=geom,
                state=surface,
                value_number=None,
                threshold_meter=definition.threshold_meter,
                prefer_crosswalk=definition.prefer_crosswalk,
            )
        )
    width = parse_decimal(row.get("widthMeter"))
    if width is not None:
        features.append(
            builder.row(
                match_source_id=match_source_id,
                source_file=definition.file_name,
                source_id=source_id,
                csv_line_no=line_no,
                feature_type="WIDTH",
                geom_ewkt=geom,
                state=derive_width_state(width),
                value_number=width,
                threshold_meter=definition.threshold_meter,
                prefer_crosswalk=definition.prefer_crosswalk,
            )
        )
    return features, None


def parse_slope_surface(
    definition: SourceDefinition,
    row: dict[str, str],
    line_no: int,
    builder: FeatureBuilder,
) -> tuple[list[SourceFeatureRow], ParseIssue | None]:
    geom, _ = geometry_from_row(row, "geometryWkt", "wkt", "geom")
    source_id = source_id_from_row(row, line_no)
    if geom is None:
        return [], ParseIssue(definition.file_name, line_no, source_id, "geometry not found")

    width = parse_decimal(row.get("widthMeter"))
    threshold = width_threshold(width)
    match_source_id = builder.match_source_id()
    features: list[SourceFeatureRow] = []
    slope = parse_decimal(row.get("slopeMean"))
    if slope is not None:
        features.append(
            builder.row(
                match_source_id=match_source_id,
                source_file=definition.file_name,
                source_id=source_id,
                csv_line_no=line_no,
                feature_type="SLOPE",
                geom_ewkt=geom,
                state=None,
                value_number=slope,
                threshold_meter=threshold,
                prefer_crosswalk=definition.prefer_crosswalk,
            )
        )
    surface = normalize_surface_state(row.get("surfaceType"), row.get("surfaceLabel"), row.get("surfaceCode"))
    if surface != "UNKNOWN":
        features.append(
            builder.row(
                match_source_id=match_source_id,
                source_file=definition.file_name,
                source_id=source_id,
                csv_line_no=line_no,
                feature_type="SURFACE",
                geom_ewkt=geom,
                state=surface,
                value_number=None,
                threshold_meter=threshold,
                prefer_crosswalk=definition.prefer_crosswalk,
            )
        )
    if width is not None:
        features.append(
            builder.row(
                match_source_id=match_source_id,
                source_file=definition.file_name,
                source_id=source_id,
                csv_line_no=line_no,
                feature_type="WIDTH",
                geom_ewkt=geom,
                state=derive_width_state(width),
                value_number=width,
                threshold_meter=threshold,
                prefer_crosswalk=definition.prefer_crosswalk,
            )
        )
    if not features:
        return [], ParseIssue(definition.file_name, line_no, source_id, "no usable slope/surface/width value")
    return features, None


def parse_crosswalk_signal(
    definition: SourceDefinition,
    row: dict[str, str],
    line_no: int,
    builder: FeatureBuilder,
) -> tuple[list[SourceFeatureRow], ParseIssue | None]:
    geom, _ = geometry_from_row(row, "point", "geom", "geometryWkt", "wkt")
    source_id = source_id_from_row(row, line_no)
    if geom is None:
        return [], ParseIssue(definition.file_name, line_no, source_id, "geometry not found")
    match_source_id = builder.match_source_id()
    return [
        builder.row(
            match_source_id=match_source_id,
            source_file=definition.file_name,
            source_id=source_id,
            csv_line_no=line_no,
            feature_type="CROSSWALK",
            geom_ewkt=geom,
            state="YES",
            value_number=None,
            threshold_meter=definition.threshold_meter,
            prefer_crosswalk=definition.prefer_crosswalk,
        ),
        builder.row(
            match_source_id=match_source_id,
            source_file=definition.file_name,
            source_id=source_id,
            csv_line_no=line_no,
            feature_type="SIGNAL",
            geom_ewkt=geom,
            state="YES",
            value_number=None,
            threshold_meter=definition.threshold_meter,
            prefer_crosswalk=definition.prefer_crosswalk,
        ),
    ], None


def parse_audio_signal(
    definition: SourceDefinition,
    row: dict[str, str],
    line_no: int,
    builder: FeatureBuilder,
) -> tuple[list[SourceFeatureRow], ParseIssue | None]:
    geom, _ = geometry_from_row(row, "point", "geom", "geometryWkt", "wkt")
    source_id = source_id_from_row(row, line_no)
    if geom is None:
        return [], ParseIssue(definition.file_name, line_no, source_id, "geometry not found")
    state = normalize_bool_state(first_value(row, "audioSignalState", "state", "stat"))
    if state != "YES":
        return [], ParseIssue(definition.file_name, line_no, source_id, "audio signal state is not YES")
    match_source_id = builder.match_source_id()
    return [
        builder.row(
            match_source_id=match_source_id,
            source_file=definition.file_name,
            source_id=source_id,
            csv_line_no=line_no,
            feature_type="AUDIO_SIGNAL",
            geom_ewkt=geom,
            state="YES",
            value_number=None,
            threshold_meter=definition.threshold_meter,
            prefer_crosswalk=definition.prefer_crosswalk,
        )
    ], None


def parse_stairs(
    definition: SourceDefinition,
    row: dict[str, str],
    line_no: int,
    builder: FeatureBuilder,
) -> tuple[list[SourceFeatureRow], ParseIssue | None]:
    geom, _ = geometry_from_row(row, "geometryWkt", "geom", "wkt")
    source_id = source_id_from_row(row, line_no)
    if geom is None:
        return [], ParseIssue(definition.file_name, line_no, source_id, "geometry not found")
    state = normalize_bool_state(first_value(row, "stairsState", "state"))
    if state != "YES":
        return [], ParseIssue(definition.file_name, line_no, source_id, "stairs state is not YES")
    match_source_id = builder.match_source_id()
    return [
        builder.row(
            match_source_id=match_source_id,
            source_file=definition.file_name,
            source_id=source_id,
            csv_line_no=line_no,
            feature_type="STAIRS",
            geom_ewkt=geom,
            state="YES",
            value_number=None,
            threshold_meter=definition.threshold_meter,
            prefer_crosswalk=definition.prefer_crosswalk,
        )
    ], None


def parse_braille_block(
    definition: SourceDefinition,
    row: dict[str, str],
    line_no: int,
    builder: FeatureBuilder,
) -> tuple[list[SourceFeatureRow], ParseIssue | None]:
    geom, _ = geometry_from_row(row, "geom", "point", "geometryWkt", "wkt")
    source_id = source_id_from_row(row, line_no)
    if geom is None:
        return [], ParseIssue(definition.file_name, line_no, source_id, "geometry not found")
    state = normalize_bool_state(first_value(row, "brailleBlockState", "state"))
    if state not in {"YES", "NO"}:
        return [], ParseIssue(definition.file_name, line_no, source_id, "braille block state is not YES/NO")
    match_source_id = builder.match_source_id()
    return [
        builder.row(
            match_source_id=match_source_id,
            source_file=definition.file_name,
            source_id=source_id,
            csv_line_no=line_no,
            feature_type="BRAILLE_BLOCK",
            geom_ewkt=geom,
            state=state,
            value_number=None,
            threshold_meter=definition.threshold_meter,
            prefer_crosswalk=definition.prefer_crosswalk,
        )
    ], None


PARSERS = {
    "sidewalk_width": parse_sidewalk_width,
    "shared_local_road": parse_shared_local_road,
    "slope_surface": parse_slope_surface,
    "crosswalk_signal": parse_crosswalk_signal,
    "audio_signal": parse_audio_signal,
    "stairs": parse_stairs,
    "braille_block": parse_braille_block,
}


def parse_source_features(source_dir: Path, require_files: bool = True) -> tuple[list[SourceFeatureRow], list[ParseIssue], dict[str, Any]]:
    builder = FeatureBuilder()
    features: list[SourceFeatureRow] = []
    issues: list[ParseIssue] = []
    source_counts: dict[str, int] = {}

    for definition in SOURCE_DEFINITIONS:
        path = source_dir / definition.file_name
        if not path.exists():
            issue = ParseIssue(definition.file_name, 0, "", "source file not found")
            issues.append(issue)
            if require_files:
                continue
            source_counts[definition.file_name] = 0
            continue

        parser = PARSERS[definition.parser_name]
        row_count = 0
        with path.open(newline="", encoding="utf-8-sig") as file:
            reader = csv.DictReader(file)
            header_issues = validate_csv_headers(definition, reader.fieldnames)
            if header_issues:
                issues.extend(header_issues)
                source_counts[definition.file_name] = 0
                continue
            for line_no, row in enumerate(reader, start=2):
                row_count += 1
                parsed_rows, issue = parser(definition, row, line_no, builder)
                features.extend(parsed_rows)
                if issue is not None:
                    issues.append(issue)
        source_counts[definition.file_name] = row_count

    feature_counts = Counter(feature.feature_type for feature in features)
    report = {
        "sourceRows": source_counts,
        "parsedFeatureRows": len(features),
        "parsedFeatureTypeCounts": dict(sorted(feature_counts.items())),
        "parseIssueCount": len(issues),
        "parseIssueSamples": [asdict(issue) for issue in issues[:30]],
        "excludedFiles": ["지하철_엘리베이터.csv"],
    }
    return features, issues, report


def jdbc_to_dsn() -> dict[str, Any]:
    db_url = os.environ.get("DB_URL") or os.environ.get("SPRING_DATASOURCE_URL")
    if not db_url:
        raise RuntimeError("DB_URL or SPRING_DATASOURCE_URL is required")
    if db_url.startswith("jdbc:postgresql://"):
        db_url = "postgresql://" + db_url[len("jdbc:postgresql://") :]
    parsed = urlparse(db_url)
    params: dict[str, Any] = {
        "host": parsed.hostname,
        "port": parsed.port or 5432,
        "dbname": parsed.path.lstrip("/"),
        "user": os.environ.get("DB_USERNAME") or os.environ.get("POSTGRES_USER") or parsed.username,
        "password": os.environ.get("DB_PASSWORD") or os.environ.get("POSTGRES_PASSWORD") or parsed.password,
    }
    query = parse_qs(parsed.query)
    sslmode = os.environ.get("DB_SSLMODE") or (query.get("sslmode") or [None])[0]
    if sslmode:
        params["sslmode"] = sslmode
    missing = [key for key in ["host", "dbname", "user", "password"] if not params.get(key)]
    if missing:
        raise RuntimeError(f"missing DB connection fields: {', '.join(missing)}")
    return params


def connect():
    import psycopg2

    return psycopg2.connect(**jdbc_to_dsn())


def ensure_schema(cursor) -> None:
    cursor.execute(
        """
        CREATE EXTENSION IF NOT EXISTS postgis;

        CREATE TABLE IF NOT EXISTS source_features (
          source_feature_id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
          feature_type varchar(50) NOT NULL,
          "geom" geometry(Geometry, 4326) NOT NULL,
          state varchar(50),
          value_number numeric(10, 2),
          source_file varchar(255) NOT NULL
        );

        CREATE TABLE IF NOT EXISTS segment_features (
          feature_id bigint PRIMARY KEY,
          edge_id bigint NOT NULL REFERENCES road_segments(edge_id),
          feature_type varchar(50) NOT NULL,
          "geom" geometry(Geometry, 4326) NOT NULL,
          state varchar(50),
          value_number numeric(10, 2)
        );

        CREATE INDEX IF NOT EXISTS road_segments_geom_gix
          ON road_segments USING GIST ("geom");
        CREATE INDEX IF NOT EXISTS idx_source_features_geom
          ON source_features USING GIST ("geom");
        CREATE INDEX IF NOT EXISTS idx_source_features_type_file
          ON source_features(feature_type, source_file);
        CREATE INDEX IF NOT EXISTS segment_features_edge_id_idx
          ON segment_features(edge_id);
        CREATE INDEX IF NOT EXISTS segment_features_geom_gix
          ON segment_features USING GIST ("geom");

        CREATE SEQUENCE IF NOT EXISTS segment_features_feature_id_seq;
        ALTER TABLE segment_features
          ALTER COLUMN feature_id SET DEFAULT nextval('segment_features_feature_id_seq');
        """
    )


def copy_staging_rows(cursor, rows: list[SourceFeatureRow]) -> None:
    cursor.execute(
        """
        CREATE TEMP TABLE accessibility_feature_source_raw (
          source_row_id bigint PRIMARY KEY,
          match_source_id bigint NOT NULL,
          source_file text NOT NULL,
          source_id text NOT NULL,
          csv_line_no integer NOT NULL,
          feature_type text,
          geom_ewkt text NOT NULL,
          state text,
          value_number numeric(10, 2),
          threshold_meter numeric(8, 2) NOT NULL,
          prefer_crosswalk boolean NOT NULL,
          update_walk_access boolean NOT NULL,
          geometry_kind text NOT NULL
        ) ON COMMIT DROP;
        """
    )
    buffer = io.StringIO()
    writer = csv.writer(buffer, lineterminator="\n")
    for row in rows:
        writer.writerow(
            [
                row.source_row_id,
                row.match_source_id,
                row.source_file,
                row.source_id,
                row.csv_line_no,
                row.feature_type or "",
                row.geom_ewkt,
                row.state or "",
                "" if row.value_number is None else str(row.value_number),
                str(row.threshold_meter),
                "true" if row.prefer_crosswalk else "false",
                "true" if row.update_walk_access else "false",
                row.geometry_kind,
            ]
        )
    buffer.seek(0)
    cursor.copy_expert(
        """
        COPY accessibility_feature_source_raw (
          source_row_id, match_source_id, source_file, source_id, csv_line_no, feature_type,
          geom_ewkt, state, value_number, threshold_meter, prefer_crosswalk,
          update_walk_access, geometry_kind
        ) FROM STDIN WITH (FORMAT csv)
        """,
        buffer,
    )
    cursor.execute(
        """
        CREATE TEMP TABLE accessibility_feature_source AS
        SELECT
          source_row_id,
          match_source_id,
          source_file,
          source_id,
          csv_line_no,
          NULLIF(feature_type, '') AS feature_type,
          ST_SetSRID(ST_GeomFromEWKT(geom_ewkt), 4326)::geometry(Geometry, 4326) AS geom,
          NULLIF(state, '') AS state,
          value_number,
          threshold_meter,
          prefer_crosswalk,
          update_walk_access,
          geometry_kind
        FROM accessibility_feature_source_raw;

        CREATE INDEX accessibility_feature_source_geom_gix
          ON accessibility_feature_source USING GIST (geom);
        CREATE INDEX accessibility_feature_source_match_source_idx
          ON accessibility_feature_source(match_source_id, source_row_id);
        """
    )


def replace_source_features(cursor, dry_run: bool) -> int:
    cursor.execute("SELECT count(*) FROM accessibility_feature_source")
    source_feature_count = int(cursor.fetchone()[0])
    if dry_run:
        return source_feature_count

    cursor.execute(
        """
        TRUNCATE TABLE source_features RESTART IDENTITY;

        INSERT INTO source_features (feature_type, "geom", state, value_number, source_file)
        SELECT
          feature_type,
          geom,
          state,
          value_number,
          source_file
        FROM accessibility_feature_source
        WHERE feature_type IS NOT NULL
          AND ST_IsValid(geom)
        ORDER BY source_row_id;
        """
    )
    return source_feature_count


def build_matching_tables(cursor) -> None:
    # 거리 계산용 EPSG:5179 geometry를 temp table에 만들고 GiST index를 걸어 대량 join 비용을 낮춘다.
    # 점자블록 CSV는 횡단보도 segment LineString에 점자블록 여부를 보강한 입력이다.
    # 거리 buffer 없이 같은 CROSS_WALK geometry에만 붙인다.
    cursor.execute(
        """
        CREATE TEMP TABLE accessibility_match_source_projected AS
        SELECT
          match_source_id,
          source_file,
          source_id,
          csv_line_no,
          feature_type,
          geom,
          threshold_meter,
          prefer_crosswalk,
          geometry_kind,
          feature_type = 'BRAILLE_BLOCK' AS has_braille_block,
          ST_Transform(geom, 5179) AS geom_5179
        FROM accessibility_feature_source
        WHERE source_row_id = match_source_id;

        CREATE INDEX accessibility_match_source_projected_geom_gix
          ON accessibility_match_source_projected USING GIST (geom_5179);

        CREATE TEMP TABLE road_segments_projected AS
        SELECT
          edge_id,
          segment_type,
          "geom",
          ST_Transform("geom", 5179) AS geom_5179
        FROM road_segments
        WHERE "geom" IS NOT NULL
          AND ST_IsValid("geom");

        CREATE INDEX road_segments_projected_geom_gix
          ON road_segments_projected USING GIST (geom_5179);

        CREATE TEMP TABLE accessibility_feature_candidates AS
        SELECT
          f.*,
          s.edge_id,
          ST_Distance(f.geom_5179, s.geom_5179) AS match_distance_meter,
          CASE
            WHEN f.prefer_crosswalk AND s.segment_type = 'CROSS_WALK' THEN 0
            WHEN f.prefer_crosswalk THEN 1
            ELSE 0
          END AS preference_rank,
          CASE
            WHEN GeometryType(f.geom) IN ('LINESTRING', 'MULTILINESTRING')
              THEN ST_Length(ST_Intersection(
                ST_Buffer(s.geom_5179, f.threshold_meter),
                f.geom_5179
              ))
            ELSE 0
          END AS overlap_meter
        FROM accessibility_match_source_projected f
        JOIN road_segments_projected s
          ON ST_DWithin(f.geom_5179, s.geom_5179, f.threshold_meter)
        WHERE NOT (
          f.has_braille_block
          AND f.geometry_kind IN ('LINESTRING', 'MULTILINESTRING')
        )
        UNION ALL
        SELECT
          f.*,
          s.edge_id,
          0::double precision AS match_distance_meter,
          0 AS preference_rank,
          ST_Length(ST_Intersection(f.geom_5179, s.geom_5179)) AS overlap_meter
        FROM accessibility_match_source_projected f
        JOIN road_segments_projected s
          ON s.segment_type = 'CROSS_WALK'
         AND ST_Equals(f.geom, s."geom")
        WHERE f.has_braille_block
          AND f.geometry_kind IN ('LINESTRING', 'MULTILINESTRING');

        CREATE TEMP TABLE accessibility_feature_ranked AS
        SELECT
          *,
          row_number() OVER (
            PARTITION BY match_source_id
            ORDER BY preference_rank ASC, overlap_meter DESC, match_distance_meter ASC, edge_id ASC
          ) AS rn,
          lead(preference_rank) OVER (
            PARTITION BY match_source_id
            ORDER BY preference_rank ASC, overlap_meter DESC, match_distance_meter ASC, edge_id ASC
          ) AS second_preference_rank,
          lead(overlap_meter) OVER (
            PARTITION BY match_source_id
            ORDER BY preference_rank ASC, overlap_meter DESC, match_distance_meter ASC, edge_id ASC
          ) AS second_overlap_meter,
          lead(match_distance_meter) OVER (
            PARTITION BY match_source_id
            ORDER BY preference_rank ASC, overlap_meter DESC, match_distance_meter ASC, edge_id ASC
          ) AS second_match_distance_meter
        FROM accessibility_feature_candidates;

        CREATE TEMP TABLE accessibility_feature_ambiguous AS
        SELECT *
        FROM accessibility_feature_ranked
        WHERE rn = 1
          AND geometry_kind NOT IN ('LINESTRING', 'MULTILINESTRING')
          AND second_preference_rank IS NOT NULL
          AND second_preference_rank = preference_rank
          AND ABS(second_match_distance_meter - match_distance_meter) <= 1
          AND ABS(second_overlap_meter - overlap_meter) <= 1;

        CREATE TEMP TABLE accessibility_match_edges AS
        SELECT
          match_source_id,
          source_file,
          source_id,
          csv_line_no,
          feature_type,
          geom,
          threshold_meter,
          prefer_crosswalk,
          geometry_kind,
          geom_5179,
          edge_id,
          match_distance_meter,
          preference_rank,
          overlap_meter
        FROM accessibility_feature_candidates
        WHERE geometry_kind IN ('LINESTRING', 'MULTILINESTRING')
          AND overlap_meter > 0
        UNION ALL
        SELECT
          r.match_source_id,
          r.source_file,
          r.source_id,
          r.csv_line_no,
          r.feature_type,
          r.geom,
          r.threshold_meter,
          r.prefer_crosswalk,
          r.geometry_kind,
          r.geom_5179,
          r.edge_id,
          r.match_distance_meter,
          r.preference_rank,
          r.overlap_meter
        FROM accessibility_feature_ranked r
        LEFT JOIN accessibility_feature_ambiguous a
          ON a.match_source_id = r.match_source_id
        WHERE r.rn = 1
          AND r.geometry_kind NOT IN ('LINESTRING', 'MULTILINESTRING')
          AND a.match_source_id IS NULL;

        CREATE TEMP TABLE accessibility_feature_matches AS
        SELECT
          source.source_row_id,
          source.source_file,
          source.source_id,
          source.csv_line_no,
          source.feature_type,
          source.geom,
          source.state,
          source.value_number,
          source.threshold_meter,
          source.prefer_crosswalk,
          source.update_walk_access,
          source.geometry_kind,
          edge.geom_5179,
          edge.edge_id,
          edge.match_distance_meter,
          edge.preference_rank,
          edge.overlap_meter
        FROM accessibility_match_edges edge
        JOIN accessibility_match_source_projected match_source
          ON match_source.match_source_id = edge.match_source_id
        JOIN accessibility_feature_source source
          ON source.source_file = match_source.source_file
         AND source.source_id = match_source.source_id
         AND source.csv_line_no = match_source.csv_line_no
         AND source.threshold_meter = match_source.threshold_meter
         AND source.prefer_crosswalk = match_source.prefer_crosswalk
         AND source.geometry_kind = match_source.geometry_kind
        WHERE source.feature_type IS NOT NULL;
        """
    )


def collect_samples(cursor) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    cursor.execute(
        """
        SELECT
          f.source_file,
          f.source_id,
          f.csv_line_no,
          f.feature_type,
          f.threshold_meter,
          'outside_threshold_or_invalid_geometry' AS reason
        FROM accessibility_feature_source f
        LEFT JOIN accessibility_feature_matches c
          ON c.source_row_id = f.source_row_id
        WHERE c.source_row_id IS NULL
        ORDER BY f.source_file, f.csv_line_no, f.feature_type NULLS FIRST
        LIMIT 50;
        """
    )
    unmatched = [dict(row) for row in cursor.fetchall()]

    cursor.execute(
        """
        SELECT
          source_file,
          source_id,
          csv_line_no,
          feature_type,
          edge_id AS first_edge_id,
          round(match_distance_meter::numeric, 3) AS first_distance_meter,
          round(second_match_distance_meter::numeric, 3) AS second_distance_meter,
          'ambiguous_within_1m' AS reason
        FROM accessibility_feature_ambiguous
        ORDER BY source_file, csv_line_no, feature_type NULLS FIRST
        LIMIT 50;
        """
    )
    ambiguous = [dict(row) for row in cursor.fetchall()]
    return unmatched, ambiguous


def insert_and_update(cursor, dry_run: bool) -> tuple[int, int]:
    cursor.execute(
        f"""
        SELECT count(*)
        FROM (
          SELECT DISTINCT ON (edge_id, feature_type, COALESCE(state, ''))
            edge_id,
            feature_type,
            state
          FROM accessibility_feature_matches
          WHERE feature_type IN ({position_event_feature_type_sql()})
          ORDER BY edge_id, feature_type, COALESCE(state, ''), source_row_id, match_distance_meter
        ) deduped_segment_features
        """
    )
    insert_count = int(cursor.fetchone()[0])
    cursor.execute("SELECT count(DISTINCT edge_id) FROM accessibility_feature_matches")
    update_candidate_count = int(cursor.fetchone()[0])

    if dry_run:
        return insert_count, update_candidate_count

    cursor.execute(
        f"""
        TRUNCATE TABLE segment_features;

        INSERT INTO segment_features (feature_id, edge_id, feature_type, "geom", state, value_number)
        SELECT
          row_number() OVER (ORDER BY edge_id, feature_type, COALESCE(state, ''), source_row_id)::bigint AS feature_id,
          edge_id,
          feature_type,
          geom,
          state,
          value_number
        FROM (
          SELECT DISTINCT ON (edge_id, feature_type, COALESCE(state, ''))
            source_row_id,
            edge_id,
            feature_type,
            geom,
            state,
            value_number,
            match_distance_meter
          FROM accessibility_feature_matches
          WHERE feature_type IN ({position_event_feature_type_sql()})
          ORDER BY edge_id, feature_type, COALESCE(state, ''), source_row_id, match_distance_meter
        ) deduped_segment_features
        ORDER BY edge_id, feature_type, COALESCE(state, ''), source_row_id;

        CREATE TEMP TABLE accessibility_edge_updates AS
        SELECT
          edge_id,
          bool_or(update_walk_access OR (feature_type = 'WALK_ACCESS' AND state = 'YES')) AS walk_access_yes,
          bool_or(feature_type = 'CROSSWALK' AND state = 'YES') AS crosswalk_yes,
          bool_or(feature_type = 'SIGNAL' AND state = 'YES') AS signal_yes,
          bool_or(feature_type = 'AUDIO_SIGNAL' AND state = 'YES') AS audio_signal_yes,
          bool_or(feature_type = 'STAIRS' AND state = 'YES') AS stairs_yes,
          CASE
            WHEN bool_or(feature_type = 'BRAILLE_BLOCK' AND state = 'YES') THEN 'YES'
            WHEN bool_or(feature_type = 'BRAILLE_BLOCK' AND state = 'NO') THEN 'NO'
            ELSE NULL
          END AS braille_block_state,
          avg(value_number) FILTER (WHERE feature_type = 'SLOPE' AND value_number IS NOT NULL) AS avg_slope_percent,
          percentile_cont(0.5) WITHIN GROUP (ORDER BY value_number)
            FILTER (WHERE feature_type = 'WIDTH' AND value_number IS NOT NULL) AS width_meter,
          bool_or(feature_type = 'SURFACE' AND state = 'UNPAVED') AS has_unpaved,
          bool_or(feature_type = 'SURFACE' AND state = 'PAVED') AS has_paved
        FROM accessibility_feature_matches
        GROUP BY edge_id;

        UPDATE road_segments s
        SET
          walk_access = CASE
            WHEN u.walk_access_yes AND s.walk_access <> 'NO' THEN 'YES'
            ELSE s.walk_access
          END,
          avg_slope_percent = COALESCE(round(u.avg_slope_percent::numeric, 2), s.avg_slope_percent),
          width_meter = COALESCE(round(u.width_meter::numeric, 2), s.width_meter),
          braille_block_state = COALESCE(u.braille_block_state, s.braille_block_state),
          audio_signal_state = CASE
            WHEN u.audio_signal_yes THEN 'YES'
            ELSE s.audio_signal_state
          END,
          width_state = CASE
            WHEN u.width_meter IS NULL THEN s.width_state
            WHEN u.width_meter >= 1.50 THEN 'ADEQUATE_150'
            WHEN u.width_meter >= 1.20 THEN 'ADEQUATE_120'
            ELSE 'NARROW'
          END,
          surface_state = CASE
            WHEN u.has_unpaved THEN 'UNPAVED'
            WHEN u.has_paved THEN 'PAVED'
            ELSE s.surface_state
          END,
          stairs_state = CASE
            WHEN u.stairs_yes THEN 'YES'
            ELSE s.stairs_state
          END,
          signal_state = CASE
            WHEN u.signal_yes THEN 'YES'
            ELSE s.signal_state
          END,
          segment_type = CASE
            WHEN u.crosswalk_yes THEN 'CROSS_WALK'
            ELSE s.segment_type
          END
        FROM accessibility_edge_updates u
        WHERE s.edge_id = u.edge_id;

        SELECT setval(
          'segment_features_feature_id_seq',
          COALESCE((SELECT MAX(feature_id) FROM segment_features), 0) + 1,
          false
        );
        """
    )
    return insert_count, update_candidate_count


def post_load_checks(cursor, dry_run: bool) -> dict[str, int]:
    if dry_run:
        cursor.execute("SELECT count(*) FROM accessibility_feature_source WHERE NOT ST_IsValid(geom)")
        invalid_geom = int(cursor.fetchone()[0])
        cursor.execute(
            f"""
            SELECT count(*)
            FROM accessibility_feature_matches
            WHERE feature_type IS NOT NULL
              AND feature_type NOT IN ({position_event_feature_type_sql()}, 'SIGNAL', 'SLOPE', 'SURFACE', 'WALK_ACCESS', 'WIDTH')
            """
        )
        unsupported_feature_rows = int(cursor.fetchone()[0])
        cursor.execute("SELECT count(*) FROM accessibility_feature_source WHERE feature_type = 'SLOPE' AND state IS NOT NULL")
        slope_state_rows = int(cursor.fetchone()[0])
        cursor.execute(
            """
            SELECT count(*)
            FROM accessibility_feature_source
            WHERE feature_type = 'WIDTH'
              AND (state IS NULL OR value_number IS NULL)
            """
        )
        invalid_width_rows = int(cursor.fetchone()[0])
        return {
            "duplicateSegmentFeatureIds": 0,
            "invalidSourceGeometry": invalid_geom,
            "invalidSegmentFeatureGeometry": 0,
            "nonPositionSegmentFeatureRows": 0,
            "orphanSegmentFeatureEdges": 0,
            "slopeRowsWithState": slope_state_rows,
            "widthRowsMissingStateOrValue": invalid_width_rows,
            "unsupportedMatchedFeatureRows": unsupported_feature_rows,
        }

    cursor.execute(
        """
        SELECT count(*)
        FROM (
          SELECT feature_id
          FROM segment_features
          GROUP BY feature_id
          HAVING count(*) > 1
        ) duplicate_ids
        """
    )
    duplicate_ids = int(cursor.fetchone()[0])
    cursor.execute("SELECT count(*) FROM segment_features WHERE NOT ST_IsValid(\"geom\")")
    invalid_geom = int(cursor.fetchone()[0])
    cursor.execute(
        f"""
        SELECT count(*)
        FROM segment_features
        WHERE feature_type NOT IN ({position_event_feature_type_sql()})
        """
    )
    non_position_rows = int(cursor.fetchone()[0])
    cursor.execute(
        """
        SELECT count(*)
        FROM segment_features f
        LEFT JOIN road_segments s
          ON s.edge_id = f.edge_id
        WHERE s.edge_id IS NULL
        """
    )
    orphan_edges = int(cursor.fetchone()[0])
    cursor.execute("SELECT count(*) FROM source_features WHERE NOT ST_IsValid(\"geom\")")
    invalid_source_geom = int(cursor.fetchone()[0])
    cursor.execute("SELECT count(*) FROM source_features WHERE feature_type = 'SLOPE' AND state IS NOT NULL")
    slope_state_rows = int(cursor.fetchone()[0])
    cursor.execute(
        """
        SELECT count(*)
        FROM source_features
        WHERE feature_type = 'WIDTH'
          AND (state IS NULL OR value_number IS NULL)
        """
    )
    invalid_width_rows = int(cursor.fetchone()[0])
    if duplicate_ids or invalid_geom or non_position_rows or orphan_edges or invalid_source_geom or slope_state_rows or invalid_width_rows:
        raise RuntimeError(
            "post load validation failed: "
            f"duplicateSegmentFeatureIds={duplicate_ids}, "
            f"invalidSourceGeometry={invalid_source_geom}, "
            f"invalidSegmentFeatureGeometry={invalid_geom}, "
            f"nonPositionSegmentFeatureRows={non_position_rows}, "
            f"orphanSegmentFeatureEdges={orphan_edges}, "
            f"slopeRowsWithState={slope_state_rows}, "
            f"widthRowsMissingStateOrValue={invalid_width_rows}"
        )
    return {
        "duplicateSegmentFeatureIds": duplicate_ids,
        "invalidSourceGeometry": invalid_source_geom,
        "invalidSegmentFeatureGeometry": invalid_geom,
        "nonPositionSegmentFeatureRows": non_position_rows,
        "orphanSegmentFeatureEdges": orphan_edges,
        "slopeRowsWithState": slope_state_rows,
        "widthRowsMissingStateOrValue": invalid_width_rows,
    }


def count_by(cursor, table_name: str, column_name: str) -> dict[str, int]:
    cursor.execute(
        f"""
        SELECT COALESCE({column_name}::text, 'NULL') AS key, count(*) AS count
        FROM {table_name}
        GROUP BY 1
        ORDER BY 1
        """
    )
    return {str(key): int(count) for key, count in cursor.fetchall()}


def run_load(args: argparse.Namespace) -> dict[str, Any]:
    source_dir = Path(args.source_dir).expanduser().resolve()
    rows, parse_issues, parse_report = parse_source_features(source_dir, require_files=not args.allow_missing_files)
    if not rows:
        raise RuntimeError("no source feature rows were parsed")
    missing = [issue for issue in parse_issues if issue.reason == "source file not found"]
    if missing and not args.allow_missing_files:
        names = ", ".join(issue.source_file for issue in missing)
        raise RuntimeError(f"missing required source CSV files: {names}")
    if parse_issues and not args.allow_parse_issues:
        samples = "; ".join(f"{issue.source_file}:{issue.csv_line_no} {issue.reason}" for issue in parse_issues[:5])
        raise RuntimeError(f"source CSV parse issues found: {samples}")

    conn = connect()
    try:
        cursor = conn.cursor()
        try:
            from psycopg2.extras import DictCursor

            cursor.close()
            cursor = conn.cursor(cursor_factory=DictCursor)
        except Exception:
            cursor = conn.cursor()

        ensure_schema(cursor)
        copy_staging_rows(cursor, rows)
        source_feature_count = replace_source_features(cursor, args.dry_run)
        build_matching_tables(cursor)
        unmatched_samples, ambiguous_samples = collect_samples(cursor)

        cursor.execute("SELECT count(*) FROM accessibility_feature_source")
        staged_count = int(cursor.fetchone()[0])
        cursor.execute("SELECT count(*) FROM accessibility_feature_candidates")
        candidate_count = int(cursor.fetchone()[0])
        cursor.execute(
            """
            SELECT count(*)
            FROM accessibility_feature_source f
            WHERE NOT EXISTS (
              SELECT 1
              FROM accessibility_feature_matches c
              WHERE c.source_row_id = f.source_row_id
            )
            """
        )
        unmatched_count = int(cursor.fetchone()[0])
        cursor.execute("SELECT count(*) FROM accessibility_feature_matches")
        matched_count = int(cursor.fetchone()[0])
        cursor.execute("SELECT count(*) FROM accessibility_feature_ambiguous")
        ambiguous_count = int(cursor.fetchone()[0])
        inserted_count, updated_edge_count = insert_and_update(cursor, args.dry_run)
        validation = post_load_checks(cursor, args.dry_run)

        report = {
            "generatedAt": datetime.now(timezone.utc).isoformat(),
            "sourceDir": str(source_dir),
            "dryRun": args.dry_run,
            **parse_report,
            "stagedRows": staged_count,
            "candidateRows": candidate_count,
            "matchedRows": matched_count,
            "unmatchedRows": unmatched_count,
            "ambiguousRows": ambiguous_count,
            "replacedSourceFeatures": source_feature_count,
            "insertedSegmentFeatures": inserted_count,
            "updatedRoadSegments": updated_edge_count,
            "sourceFeatureTypeCounts": count_by(cursor, "accessibility_feature_source", "feature_type")
            if args.dry_run
            else count_by(cursor, "source_features", "feature_type"),
            "sourceFeatureFileCounts": count_by(cursor, "accessibility_feature_source", "source_file")
            if args.dry_run
            else count_by(cursor, "source_features", "source_file"),
            "matchedFeatureTypeCounts": count_by(cursor, "accessibility_feature_matches", "feature_type"),
            "postLoadChecks": validation,
            "unmatchedSamples": unmatched_samples,
            "ambiguousSamples": ambiguous_samples,
        }
        write_report(Path(args.report_json), report)
        if args.dry_run:
            conn.rollback()
        else:
            conn.commit()
        return report
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


def write_report(path: Path, report: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as file:
        json.dump(report, file, ensure_ascii=False, indent=2, default=str)
        file.write("\n")


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Load accessibility source CSV features into road_segments and segment_features.")
    parser.add_argument("--source-dir", default=os.environ.get("ACCESSIBILITY_SOURCE_DIR", ".ai/LOCAL"))
    parser.add_argument(
        "--report-json",
        default=os.environ.get("ACCESSIBILITY_REPORT_JSON", ".tmp/accessibility-feature-load-report.json"),
    )
    parser.add_argument("--dry-run", action="store_true", help="Run matching and report generation, then rollback DB writes.")
    parser.add_argument("--allow-missing-files", action="store_true", help="Skip missing source CSV files.")
    parser.add_argument("--allow-parse-issues", action="store_true", help="Continue when rows are skipped during parse.")
    return parser.parse_args(argv)


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    report = run_load(args)
    print(
        "accessibility feature load "
        f"dryRun={report['dryRun']} "
        f"staged={report['stagedRows']} "
        f"matched={report['matchedRows']} "
        f"segmentFeatures={report['insertedSegmentFeatures']} "
        f"updatedSegments={report['updatedRoadSegments']} "
        f"unmatched={report['unmatchedRows']} "
        f"ambiguous={report['ambiguousRows']} "
        f"report={args.report_json}",
        flush=True,
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
