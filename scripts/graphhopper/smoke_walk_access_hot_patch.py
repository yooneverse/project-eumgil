#!/usr/bin/env python3
"""Verify GraphHopper walk_access overlay reload changes routing immediately.

This smoke test runs the same origin/destination route twice:
1. Before override: the route must include the target `db_edge_id`.
2. After override + reload: the route must either avoid that `db_edge_id` or become unroutable.

The script restores the previous `routing_segment_overrides` row state unless
`--skip-restore` is used.
"""

from __future__ import annotations

import argparse
import json
import os
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any
from urllib.error import HTTPError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

import psycopg2


NO_ROUTE_MARKERS = (
    "connectionnotfoundexception",
    "connection between locations not found",
)


@dataclass
class HttpRequestError(Exception):
    status_code: int
    body: str
    payload: dict[str, Any]

    def __str__(self) -> str:
        return f"http {self.status_code}: {self.body or self.payload}"


def parse_json_body(body: str) -> dict[str, Any]:
    if not body:
        return {}
    try:
        parsed = json.loads(body)
    except json.JSONDecodeError:
        return {}
    return parsed if isinstance(parsed, dict) else {}


def request_json(method: str, url: str, payload: dict[str, Any] | None, timeout: int) -> tuple[int, dict[str, Any]]:
    body = None if payload is None else json.dumps(payload).encode("utf-8")
    request = Request(
        url,
        data=body,
        method=method,
        headers={
            "Accept": "application/json",
            "Content-Type": "application/json",
        },
    )
    try:
        with urlopen(request, timeout=timeout) as response:
            response_body = response.read().decode("utf-8")
            return response.status, json.loads(response_body) if response_body else {}
    except HTTPError as error:
        response_body = error.read().decode("utf-8", errors="replace")
        raise HttpRequestError(error.code, response_body, parse_json_body(response_body)) from error


def route_url(base_url: str, profile: str, from_lat: float, from_lng: float, to_lat: float, to_lng: float) -> str:
    query = urlencode(
        [
            ("profile", profile),
            ("point", f"{from_lat},{from_lng}"),
            ("point", f"{to_lat},{to_lng}"),
            ("points_encoded", "false"),
            ("locale", "ko-KR"),
            ("details", "db_edge_id"),
            ("details", "walk_access"),
        ]
    )
    return f"{base_url.rstrip('/')}/route?{query}"


def reload_url(base_url: str) -> str:
    return f"{base_url.rstrip('/')}/ieum/admin/overrides/reload"


def extract_paths(payload: dict[str, Any]) -> list[dict[str, Any]]:
    return payload.get("paths") or []


def detail_values(path: dict[str, Any], detail_name: str) -> list[str]:
    details = path.get("details") or {}
    rows = details.get(detail_name) or []
    return [str(row[2]) for row in rows if isinstance(row, list) and len(row) >= 3]


def is_no_route_error(error: HttpRequestError) -> bool:
    normalized_body = error.body.lower()
    if any(marker in normalized_body for marker in NO_ROUTE_MARKERS):
        return True
    payload_dump = json.dumps(error.payload).lower() if error.payload else ""
    return any(marker in payload_dump for marker in NO_ROUTE_MARKERS)


def write_report(report_json: str | None, report: dict[str, Any]) -> None:
    if not report_json:
        return
    report_path = os.path.abspath(report_json)
    os.makedirs(os.path.dirname(report_path), exist_ok=True)
    with open(report_path, "w", encoding="utf-8") as file:
        json.dump(report, file, ensure_ascii=False, indent=2)
        file.write("\n")


def fail(message: str, report_json: str | None, report: dict[str, Any]) -> int:
    report["status"] = "FAIL"
    report["message"] = message
    write_report(report_json, report)
    print(message)
    return 1


def open_connection():
    jdbc_url = first_non_blank(
        os.getenv("DB_URL"),
        os.getenv("SPRING_DATASOURCE_URL"),
    )
    if jdbc_url:
        normalized = jdbc_url.replace("jdbc:postgresql://", "")
        host_port, db_name = normalized.split("/", 1)
        host, port = host_port.split(":", 1)
        return psycopg2.connect(
            host=host,
            port=port,
            dbname=db_name,
            user=first_non_blank(os.getenv("DB_USERNAME"), os.getenv("POSTGRES_USER")),
            password=first_non_blank(os.getenv("DB_PASSWORD"), os.getenv("POSTGRES_PASSWORD")),
            sslmode=os.getenv("DB_SSLMODE", os.getenv("PGSSLMODE", "prefer")),
        )

    return psycopg2.connect(
        host=first_non_blank(os.getenv("PGHOST"), "postgres"),
        port=first_non_blank(os.getenv("PGPORT"), "5432"),
        dbname=first_non_blank(os.getenv("PGDATABASE"), os.getenv("POSTGRES_DB"), "e102"),
        user=first_non_blank(os.getenv("DB_USERNAME"), os.getenv("POSTGRES_USER")),
        password=first_non_blank(os.getenv("DB_PASSWORD"), os.getenv("POSTGRES_PASSWORD")),
        sslmode=os.getenv("DB_SSLMODE", os.getenv("PGSSLMODE", "prefer")),
    )


def first_non_blank(*candidates: str | None) -> str | None:
    for candidate in candidates:
        if candidate and candidate.strip():
            return candidate
    return None


def fetch_previous_override(edge_id: int) -> dict[str, str | None] | None:
    with open_connection() as connection:
        with connection.cursor() as cursor:
            cursor.execute(
                """
                select walk_access, stairs_state, width_state, braille_block_state
                from routing_segment_overrides
                where edge_id = %s
                """,
                (edge_id,),
            )
            row = cursor.fetchone()
            if row is None:
                return None
            return {
                "walk_access": row[0],
                "stairs_state": row[1],
                "width_state": row[2],
                "braille_block_state": row[3],
            }


def apply_override(edge_id: int, walk_access: str) -> None:
    with open_connection() as connection:
        with connection.cursor() as cursor:
            cursor.execute(
                """
                insert into routing_segment_overrides(edge_id, walk_access)
                values (%s, %s)
                on conflict (edge_id)
                do update set walk_access = excluded.walk_access
                """,
                (edge_id, walk_access),
            )
        connection.commit()


def clear_override(edge_id: int) -> None:
    with open_connection() as connection:
        with connection.cursor() as cursor:
            cursor.execute("delete from routing_segment_overrides where edge_id = %s", (edge_id,))
        connection.commit()


def restore_override(edge_id: int, previous_override: dict[str, str | None] | None) -> None:
    if previous_override is None:
        clear_override(edge_id)
    else:
        with open_connection() as connection:
            with connection.cursor() as cursor:
                cursor.execute(
                    """
                    insert into routing_segment_overrides(
                      edge_id, walk_access, stairs_state, width_state, braille_block_state
                    )
                    values (%s, %s, %s, %s, %s)
                    on conflict (edge_id)
                    do update set
                      walk_access = excluded.walk_access,
                      stairs_state = excluded.stairs_state,
                      width_state = excluded.width_state,
                      braille_block_state = excluded.braille_block_state
                    """,
                    (
                        edge_id,
                        previous_override["walk_access"],
                        previous_override["stairs_state"],
                        previous_override["width_state"],
                        previous_override["braille_block_state"],
                    ),
                )
            connection.commit()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default=os.getenv("GRAPHHOPPER_OVERLAY_SMOKE_BASE_URL", "http://graphhopper:8989"))
    parser.add_argument("--profile", default=os.getenv("GRAPHHOPPER_OVERLAY_SMOKE_PROFILE", "wheelchair_manual_safe"))
    parser.add_argument("--edge-id", type=int, required=True)
    parser.add_argument("--from-lat", type=float, required=True)
    parser.add_argument("--from-lng", type=float, required=True)
    parser.add_argument("--to-lat", type=float, required=True)
    parser.add_argument("--to-lng", type=float, required=True)
    parser.add_argument("--override-walk-access", default=os.getenv("GRAPHHOPPER_OVERLAY_SMOKE_WALK_ACCESS", "NO"))
    parser.add_argument("--timeout-seconds", type=int, default=int(os.getenv("GRAPHHOPPER_OVERLAY_SMOKE_TIMEOUT_SECONDS", "10")))
    parser.add_argument("--report-json", default=os.getenv("GRAPHHOPPER_OVERLAY_SMOKE_REPORT_FILE"))
    parser.add_argument("--skip-restore", action="store_true")
    args = parser.parse_args()

    report: dict[str, Any] = {
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "baseUrl": args.base_url,
        "profile": args.profile,
        "edgeId": args.edge_id,
        "route": {
            "from": {"lat": args.from_lat, "lng": args.from_lng},
            "to": {"lat": args.to_lat, "lng": args.to_lng},
        },
    }

    before_url = route_url(args.base_url, args.profile, args.from_lat, args.from_lng, args.to_lat, args.to_lng)
    try:
        before_status, before_payload = request_json("GET", before_url, None, args.timeout_seconds)
    except Exception as error:
        return fail(f"overlay smoke failed before route request: {error}", args.report_json, report)

    before_paths = extract_paths(before_payload)
    report["before"] = {"httpStatus": before_status, "pathCount": len(before_paths)}
    if before_status != 200 or not before_paths:
        return fail("overlay smoke failed: before route has no paths", args.report_json, report)

    before_edge_values = detail_values(before_paths[0], "db_edge_id")
    report["before"]["dbEdgeIds"] = before_edge_values
    if str(args.edge_id) not in before_edge_values:
        return fail(
            "overlay smoke failed: before route does not include the target db_edge_id; choose an OD that traverses the edge",
            args.report_json,
            report,
        )

    previous_override = fetch_previous_override(args.edge_id)
    report["before"]["previousOverride"] = previous_override

    return_code = 1
    failure_message: str | None = None
    restore_error: str | None = None
    try:
        apply_override(args.edge_id, args.override_walk_access)

        try:
            reload_status, reload_payload = request_json("POST", reload_url(args.base_url), {}, args.timeout_seconds)
            report["reload"] = {
                "httpStatus": reload_status,
                "response": reload_payload,
            }
            if reload_status != 200:
                failure_message = "overlay smoke failed: reload endpoint did not return 200"
            else:
                after_url = route_url(args.base_url, args.profile, args.from_lat, args.from_lng, args.to_lat, args.to_lng)
                after_status, after_payload = request_json("GET", after_url, None, args.timeout_seconds)
                after_paths = extract_paths(after_payload)
                report["after"] = {"httpStatus": after_status, "pathCount": len(after_paths)}

                if after_status != 200:
                    failure_message = "overlay smoke failed: after route endpoint did not return 200"
                elif not after_paths:
                    report["status"] = "PASS"
                    report["message"] = "overlay smoke ok: route became unroutable after blocking target edge"
                    return_code = 0
                else:
                    after_edge_values = detail_values(after_paths[0], "db_edge_id")
                    report["after"]["dbEdgeIds"] = after_edge_values
                    if str(args.edge_id) in after_edge_values:
                        failure_message = "overlay smoke failed: target db_edge_id is still present after reload"
                    else:
                        report["status"] = "PASS"
                        report["message"] = "overlay smoke ok: target db_edge_id disappeared from the route after reload"
                        return_code = 0
        except HttpRequestError as error:
            if "reload" not in report:
                report["reload"] = {
                    "httpStatus": error.status_code,
                    "errorBody": error.body,
                    "errorPayload": error.payload,
                }
                failure_message = f"overlay smoke failed during reload request: {error}"
            else:
                report["after"] = {
                    "httpStatus": error.status_code,
                    "pathCount": 0,
                    "errorBody": error.body,
                    "errorPayload": error.payload,
                }
                if is_no_route_error(error):
                    report["status"] = "PASS"
                    report["message"] = "overlay smoke ok: route became unroutable after blocking target edge"
                    return_code = 0
                else:
                    failure_message = f"overlay smoke failed after route request: {error}"
        except Exception as error:
            if "reload" not in report:
                failure_message = f"overlay smoke failed during reload request: {error}"
            else:
                failure_message = f"overlay smoke failed after route request: {error}"
    finally:
        if not args.skip_restore:
            try:
                restore_override(args.edge_id, previous_override)
                restore_status, restore_payload = request_json("POST", reload_url(args.base_url), {}, args.timeout_seconds)
                report["restore"] = {
                    "previousOverride": previous_override,
                    "httpStatus": restore_status,
                    "response": restore_payload,
                }
                if restore_status != 200:
                    restore_error = "reload endpoint did not return 200 while restoring"
            except Exception as error:
                restore_error = str(error)
                report["restoreError"] = restore_error

    if restore_error is not None:
        report["status"] = "FAIL"
        report["message"] = f"overlay smoke failed while restoring previous override={previous_override}: {restore_error}"
    elif failure_message is not None:
        report["status"] = "FAIL"
        report["message"] = failure_message

    write_report(args.report_json, report)
    if report.get("message"):
        print(report["message"])
    return 0 if report.get("status") == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
