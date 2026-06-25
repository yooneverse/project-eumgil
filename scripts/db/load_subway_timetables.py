#!/usr/bin/env python3
"""
Generate, validate, and load ODsay subway timetable CSV artifacts.

Runtime route APIs do not call ODsay schedule endpoints. This script keeps the
flow explicit: raw ODsay JSON -> normalized CSV -> validation -> PostgreSQL COPY.
"""

from __future__ import annotations

import argparse
import csv
import json
import os
import re
import sys
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any
from urllib.parse import urlparse


STATION_CSV_NAME = "subway_stations.csv"
TIMETABLE_CSV_NAME = "subway_timetables.csv"
STATION_CSV_HEADER = ["odsay_station_id", "station_name", "line_name", "point"]
TIMETABLE_CSV_HEADER = [
    "odsay_station_id",
    "service_day_type",
    "way_code",
    "departure_time_text",
    "departure_second_of_day",
    "end_station_name",
]
SERVICE_DAY_TYPES = {"WEEKDAY", "SATURDAY", "HOLIDAY"}
SCHEDULE_KEYS = {
    "weekdaySchedule": "WEEKDAY",
    "saturdaySchedule": "SATURDAY",
    "holidaySchedule": "HOLIDAY",
}
DIRECTION_KEYS = {
    "up": "1",
    "down": "2",
}
POINT_RE = re.compile(r"^(?:SRID=4326;)?POINT\((-?\d+(?:\.\d+)?) (-?\d+(?:\.\d+)?)\)$", re.I)


@dataclass(frozen=True)
class SubwayStationCsvRow:
    odsay_station_id: str
    station_name: str
    line_name: str
    point: str


@dataclass(frozen=True)
class SubwayTimetableCsvRow:
    odsay_station_id: str
    service_day_type: str
    way_code: str
    departure_time_text: str
    departure_second_of_day: str
    end_station_name: str


def blank_to_none(value: Any) -> str | None:
    if value is None:
        return None
    text = str(value).strip()
    return text or None


def rows_from_schedule_payload(payload: dict[str, Any]) -> tuple[list[SubwayStationCsvRow], list[SubwayTimetableCsvRow]]:
    result = payload.get("result") or {}
    odsay_station_id = required_text(result, "stationID")
    station_name = required_text(result, "stationName")
    line_name = required_text(result, "laneName")
    point = point_from_result(result)

    stations = [
        SubwayStationCsvRow(
            odsay_station_id=odsay_station_id,
            station_name=station_name,
            line_name=line_name,
            point=point,
        )
    ]
    timetables: list[SubwayTimetableCsvRow] = []

    for schedule_key, service_day_type in SCHEDULE_KEYS.items():
        schedule = result.get(schedule_key) or {}
        for direction_key, way_code in DIRECTION_KEYS.items():
            for item in iter_schedule_items(schedule.get(direction_key)):
                departure_time_text = required_text(item, "departureTime")
                end_station_name = required_text(item, "endStationName")
                timetables.append(
                    SubwayTimetableCsvRow(
                        odsay_station_id=odsay_station_id,
                        service_day_type=service_day_type,
                        way_code=way_code,
                        departure_time_text=departure_time_text,
                        departure_second_of_day=str(departure_second_of_day(departure_time_text)),
                        end_station_name=end_station_name,
                    )
                )

    return stations, timetables


def required_text(row: dict[str, Any], key: str) -> str:
    text = blank_to_none(row.get(key))
    if text is None:
        raise ValueError(f"missing required ODsay field: {key}")
    return text


def point_from_result(result: dict[str, Any]) -> str:
    longitude = blank_to_none(result.get("x"))
    latitude = blank_to_none(result.get("y"))
    if longitude is None or latitude is None:
        return ""
    return f"SRID=4326;POINT({longitude} {latitude})"


def iter_schedule_items(node: Any):
    if node is None:
        return
    if isinstance(node, list):
        for item in node:
            yield from iter_schedule_items(item)
        return
    if not isinstance(node, dict):
        return
    if "departureTime" in node:
        yield node
        return
    for nested_key in ("time", "station", "items", "list"):
        if nested_key in node:
            yield from iter_schedule_items(node[nested_key])


def departure_second_of_day(text: str) -> int:
    parts = text.split(":")
    if len(parts) not in {2, 3}:
        raise ValueError(f"invalid departureTime: {text}")
    hour = int(parts[0])
    minute = int(parts[1])
    second = int(parts[2]) if len(parts) == 3 else 0
    if minute < 0 or minute > 59 or second < 0 or second > 59:
        raise ValueError(f"invalid departureTime: {text}")
    return hour * 3600 + minute * 60 + second


def rows_from_raw_dir(raw_dir: Path) -> tuple[list[SubwayStationCsvRow], list[SubwayTimetableCsvRow]]:
    station_by_id: dict[str, SubwayStationCsvRow] = {}
    timetable_rows: set[SubwayTimetableCsvRow] = set()

    for path in sorted(raw_dir.glob("*.json")):
        with path.open(encoding="utf-8") as file:
            payload = json.load(file)
        stations, timetables = rows_from_schedule_payload(payload)
        for station in stations:
            station_by_id.setdefault(station.odsay_station_id, station)
        timetable_rows.update(timetables)

    return list(station_by_id.values()), sorted(
        timetable_rows,
        key=lambda row: (
            row.odsay_station_id,
            row.service_day_type,
            row.way_code,
            int(row.departure_second_of_day),
            row.end_station_name,
        ),
    )


def write_csvs(
    stations: list[SubwayStationCsvRow],
    timetables: list[SubwayTimetableCsvRow],
    csv_dir: Path,
) -> None:
    csv_dir.mkdir(parents=True, exist_ok=True)
    write_csv(csv_dir / STATION_CSV_NAME, STATION_CSV_HEADER, stations)
    write_csv(csv_dir / TIMETABLE_CSV_NAME, TIMETABLE_CSV_HEADER, timetables)


def write_csv(path: Path, header: list[str], rows: list[Any]) -> None:
    with path.open("w", newline="", encoding="utf-8") as file:
        writer = csv.DictWriter(file, fieldnames=header)
        writer.writeheader()
        for row in rows:
            writer.writerow(asdict(row))


def validate_csv_dir(csv_dir: Path) -> list[str]:
    issues: list[str] = []
    station_rows = read_csv(csv_dir / STATION_CSV_NAME, STATION_CSV_HEADER, issues)
    timetable_rows = read_csv(csv_dir / TIMETABLE_CSV_NAME, TIMETABLE_CSV_HEADER, issues)
    if not station_rows:
        issues.append(f"{STATION_CSV_NAME} has no rows")
    if not timetable_rows:
        issues.append(f"{TIMETABLE_CSV_NAME} has no rows")
    station_ids = validate_station_rows(station_rows, issues)
    validate_timetable_rows(timetable_rows, station_ids, issues)
    return issues


def read_csv(path: Path, expected_header: list[str], issues: list[str]) -> list[dict[str, str]]:
    if not path.exists():
        issues.append(f"missing CSV file: {path.name}")
        return []
    with path.open(newline="", encoding="utf-8") as file:
        reader = csv.DictReader(file)
        if reader.fieldnames != expected_header:
            issues.append(f"{path.name}: invalid header: {reader.fieldnames}")
            return []
        return list(reader)


def validate_station_rows(rows: list[dict[str, str]], issues: list[str]) -> set[str]:
    station_ids: set[str] = set()
    duplicate_ids: set[str] = set()
    for index, row in enumerate(rows, start=2):
        odsay_station_id = row["odsay_station_id"].strip()
        if not odsay_station_id:
            issues.append(f"row {index}: odsay_station_id is required")
        elif odsay_station_id in station_ids:
            duplicate_ids.add(odsay_station_id)
        else:
            station_ids.add(odsay_station_id)

        if not row["station_name"].strip():
            issues.append(f"row {index}: station_name is required")
        if not row["line_name"].strip():
            issues.append(f"row {index}: line_name is required")
        point = row["point"].strip()
        if point and not is_valid_point(point):
            issues.append(f"row {index}: point must be SRID=4326 POINT longitude latitude")

    for duplicate_id in sorted(duplicate_ids):
        issues.append(f"duplicate subway_stations.odsay_station_id: {duplicate_id}")
    return station_ids


def validate_timetable_rows(rows: list[dict[str, str]], station_ids: set[str], issues: list[str]) -> None:
    lookup_keys: set[str] = set()
    duplicate_lookup_keys: set[str] = set()
    for index, row in enumerate(rows, start=2):
        odsay_station_id = row["odsay_station_id"].strip()
        service_day_type = row["service_day_type"].strip()
        way_code = row["way_code"].strip()
        departure_time_text = row["departure_time_text"].strip()
        departure_second = row["departure_second_of_day"].strip()
        end_station_name = row["end_station_name"].strip()

        if odsay_station_id not in station_ids:
            issues.append(f"row {index}: odsay_station_id not found in subway_stations: {odsay_station_id}")
        if service_day_type not in SERVICE_DAY_TYPES:
            issues.append(f"row {index}: invalid service_day_type: {service_day_type}")
        if way_code not in {"1", "2"}:
            issues.append(f"row {index}: invalid way_code: {way_code}")
        if not departure_time_text:
            issues.append(f"row {index}: departure_time_text is required")
        try:
            int(departure_second)
        except ValueError:
            issues.append(f"row {index}: departure_second_of_day must be an integer")
        if not end_station_name:
            issues.append(f"row {index}: end_station_name is required")

        lookup_key = "|".join(
            [odsay_station_id, service_day_type, way_code, departure_second, end_station_name]
        )
        if all([odsay_station_id, service_day_type, way_code, departure_second, end_station_name]):
            if lookup_key in lookup_keys:
                duplicate_lookup_keys.add(lookup_key)
            else:
                lookup_keys.add(lookup_key)

    for duplicate_key in sorted(duplicate_lookup_keys):
        issues.append(f"duplicate subway_timetables lookup key: {duplicate_key}")


def is_valid_point(value: str) -> bool:
    match = POINT_RE.match(value.strip())
    if not match:
        return False
    longitude = float(match.group(1))
    latitude = float(match.group(2))
    return -180 <= longitude <= 180 and -90 <= latitude <= 90


def copy_csv_to_db(csv_dir: Path, truncate: bool) -> None:
    import psycopg2

    with psycopg2.connect(**jdbc_to_dsn()) as connection:
        with connection.cursor() as cursor:
            if truncate:
                cursor.execute("TRUNCATE TABLE subway_timetables, subway_stations RESTART IDENTITY")
            copy_table(
                cursor,
                "subway_stations",
                STATION_CSV_HEADER,
                csv_dir / STATION_CSV_NAME,
            )
            copy_table(
                cursor,
                "subway_timetables",
                TIMETABLE_CSV_HEADER,
                csv_dir / TIMETABLE_CSV_NAME,
            )


def copy_table(cursor, table_name: str, columns: list[str], path: Path) -> None:
    columns_sql = ", ".join(columns)
    sql = f"COPY {table_name} ({columns_sql}) FROM STDIN WITH (FORMAT CSV, HEADER TRUE, NULL '')"
    with path.open("r", encoding="utf-8") as file:
        cursor.copy_expert(sql, file)


def jdbc_to_dsn() -> dict[str, Any]:
    db_url = os.environ.get("DB_URL")
    if not db_url:
        raise RuntimeError("DB_URL is required for COPY")
    if db_url.startswith("jdbc:postgresql://"):
        db_url = "postgresql://" + db_url[len("jdbc:postgresql://") :]
    parsed = urlparse(db_url)
    query = dict(part.split("=", 1) for part in parsed.query.split("&") if "=" in part)
    return {
        "host": parsed.hostname,
        "port": parsed.port or 5432,
        "dbname": parsed.path.lstrip("/"),
        "user": os.environ.get("DB_USERNAME") or os.environ.get("POSTGRES_USER") or parsed.username,
        "password": os.environ.get("DB_PASSWORD") or os.environ.get("POSTGRES_PASSWORD") or parsed.password,
        "sslmode": query.get("sslmode") or os.environ.get("DB_SSLMODE", "prefer"),
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--raw-dir", type=Path, help="Directory containing ODsay searchSubwaySchedule JSON files")
    parser.add_argument("--csv-dir", type=Path, required=True, help="Directory for normalized CSV artifacts")
    parser.add_argument("--copy", action="store_true", help="COPY validated CSV into DB")
    parser.add_argument("--no-truncate", action="store_true", help="Do not truncate tables before COPY")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if args.raw_dir is not None:
        stations, timetables = rows_from_raw_dir(args.raw_dir)
        write_csvs(stations, timetables, args.csv_dir)

    issues = validate_csv_dir(args.csv_dir)
    if issues:
        for issue in issues:
            print(issue, file=sys.stderr)
        return 1

    if args.copy:
        copy_csv_to_db(args.csv_dir, truncate=not args.no_truncate)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
