#!/usr/bin/env python3
"""모든 GraphHopper 접근성 profile을 확인하는 runtime smoke다.

이 스크립트는 DB 기반 routeable road segment 후보를 고르고 각 profile로
GraphHopper `/route` API를 호출한다. import된 graph, custom encoded value,
custom model 파일이 함께 최소 하나의 경로를 만들 수 있는지 확인하고,
path details로 hard policy 위반이 없는지도 검증한다.
"""
import argparse
import importlib.util
import json
import os
import time
from datetime import datetime, timezone
from urllib.parse import urlencode
from urllib.request import Request, urlopen
from urllib.error import HTTPError, URLError


EXPORTER_PATH = "/usr/local/bin/export-postgis-to-osm.py"
DEFAULT_PROFILES = [
    "pedestrian_safe",
    "pedestrian_fast",
    "visual_safe",
    "visual_fast",
    "wheelchair_manual_safe",
    "wheelchair_manual_fast",
    "wheelchair_auto_safe",
    "wheelchair_auto_fast",
]
POLICY_DETAILS = [
    "walk_access",
    "stairs_state",
    "avg_slope_percent",
    "width_state",
    "surface_state",
    "signal_state",
    "segment_type",
]


def load_exporter():
    """GraphHopper 컨테이너 안에서 exporter의 DB 연결 설정을 재사용한다."""
    spec = importlib.util.spec_from_file_location("export_postgis_to_osm", EXPORTER_PATH)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def fetch_candidates(conn, limit):
    """smoke 경로 endpoint로 쓸 길고 routeable한 정상 segment 후보를 찾는다."""
    sql = """
SELECT
  edge_id,
  walk_access::text AS walk_access,
  stairs_state::text AS stairs_state,
  COALESCE(length_meter, ST_Length("geom"::geography)) AS length_meter,
  ST_X(ST_StartPoint("geom"::geometry)) AS from_lon,
  ST_Y(ST_StartPoint("geom"::geometry)) AS from_lat,
  ST_X(ST_EndPoint("geom"::geometry)) AS to_lon,
  ST_Y(ST_EndPoint("geom"::geometry)) AS to_lat
FROM road_segments
WHERE COALESCE(walk_access::text, 'UNKNOWN') <> 'NO'
  AND "geom" IS NOT NULL
  AND NOT ST_IsEmpty("geom"::geometry)
  AND ST_NPoints("geom"::geometry) >= 2
  AND NOT (
    ST_X(ST_StartPoint("geom"::geometry)) = ST_X(ST_EndPoint("geom"::geometry))
    AND ST_Y(ST_StartPoint("geom"::geometry)) = ST_Y(ST_EndPoint("geom"::geometry))
  )
ORDER BY COALESCE(length_meter, ST_Length("geom"::geography)) DESC
LIMIT %s
"""
    with conn.cursor() as cur:
        cur.execute(sql, [limit])
        columns = [desc[0] for desc in cur.description]
        return [dict(zip(columns, row)) for row in cur.fetchall()]


def request_json(url, timeout):
    request = Request(url, headers={"Accept": "application/json"})
    with urlopen(request, timeout=timeout) as response:
        body = response.read().decode("utf-8")
        return response.status, json.loads(body)


def route_url(base_url, candidate, profile, details=None):
    """후보 segment endpoint를 사용해 GraphHopper route URL을 만든다."""
    query_items = [
        ("profile", profile),
        ("point", f'{candidate["from_lat"]},{candidate["from_lon"]}'),
        ("point", f'{candidate["to_lat"]},{candidate["to_lon"]}'),
        ("points_encoded", "false"),
        ("locale", "ko-KR"),
    ]
    for detail in details or []:
        query_items.append(("details", detail))
    query = urlencode(query_items)
    return f"{base_url.rstrip('/')}/route?{query}"


def path_detail_values(path, detail_name):
    """GraphHopper path details 배열에서 value 목록만 추출한다."""
    details = path.get("details") or {}
    values = []
    for row in details.get(detail_name) or []:
        if len(row) >= 3:
            values.append(str(row[2]))
    return values


def summarize_policy_details(path):
    """report에 남길 접근성 detail 값 분포를 만든다."""
    summary = {}
    for detail in POLICY_DETAILS:
        values = path_detail_values(path, detail)
        if not values:
            continue
        counts = {}
        for value in values:
            counts[value] = counts.get(value, 0) + 1
        summary[detail] = counts
    return summary


def validate_policy_details(profile, path, require_details=True):
    """runtime route가 custom encoded value와 hard policy를 실제로 반영하는지 검증한다."""
    violations = []
    details = path.get("details") or {}

    if require_details:
        missing = [detail for detail in POLICY_DETAILS if detail not in details]
        if missing:
            violations.append({
                "kind": "missing_policy_detail",
                "details": missing,
            })

    walk_access_values = set(path_detail_values(path, "walk_access"))
    if "NO" in walk_access_values:
        violations.append({
            "kind": "blocked_walk_access_used",
            "detail": "walk_access",
            "blockedValue": "NO",
        })

    if profile.startswith("wheelchair_"):
        stairs_values = set(path_detail_values(path, "stairs_state"))
        if "YES" in stairs_values:
            violations.append({
                "kind": "wheelchair_stairs_used",
                "detail": "stairs_state",
                "blockedValue": "YES",
            })

    return violations


def smoke_profile(base_url, candidate, profile, timeout, require_policy_details=True):
    """`/route` 요청 하나를 실행하고 HTTP/runtime 실패를 report row로 변환한다."""
    url = route_url(base_url, candidate, profile, POLICY_DETAILS)
    started = time.monotonic()
    try:
        status, payload = request_json(url, timeout)
    except HTTPError as error:
        body = error.read().decode("utf-8", errors="replace")
        return {
            "profile": profile,
            "status": "FAIL",
            "httpStatus": error.code,
            "error": body[:1000],
            "url": url,
        }
    except (TimeoutError, URLError) as error:
        return {
            "profile": profile,
            "status": "FAIL",
            "error": str(error),
            "url": url,
        }

    elapsed_ms = round((time.monotonic() - started) * 1000)
    paths = payload.get("paths") or []
    if status != 200 or not paths:
        return {
            "profile": profile,
            "status": "FAIL",
            "httpStatus": status,
            "elapsedMs": elapsed_ms,
            "error": "route response has no paths",
            "url": url,
        }

    path = paths[0]
    policy_violations = validate_policy_details(profile, path, require_policy_details)
    policy_details = summarize_policy_details(path)
    if policy_violations:
        return {
            "profile": profile,
            "status": "FAIL",
            "httpStatus": status,
            "elapsedMs": elapsed_ms,
            "error": "route violates GraphHopper accessibility policy smoke checks",
            "policyViolations": policy_violations,
            "policyDetails": policy_details,
            "url": url,
        }

    return {
        "profile": profile,
        "status": "PASS",
        "httpStatus": status,
        "elapsedMs": elapsed_ms,
        "distanceMeter": round(float(path.get("distance", 0.0)), 3),
        "timeMs": int(path.get("time", 0)),
        "policyDetails": policy_details,
        "url": url,
    }


def write_report(path, report):
    if not path:
        return
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    with open(path, "w", encoding="utf-8") as report_file:
        json.dump(report, report_file, ensure_ascii=False, indent=2, default=str)
        report_file.write("\n")


def profile_list(raw):
    if not raw:
        return DEFAULT_PROFILES
    return [item.strip() for item in raw.split(",") if item.strip()]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default=os.getenv("GRAPHHOPPER_PROFILE_SMOKE_BASE_URL", "http://graphhopper:8989"))
    parser.add_argument("--candidate-limit", type=int, default=int(os.getenv("GRAPHHOPPER_PROFILE_SMOKE_CANDIDATE_LIMIT", "30")))
    parser.add_argument("--timeout-seconds", type=int, default=int(os.getenv("GRAPHHOPPER_PROFILE_SMOKE_TIMEOUT_SECONDS", "10")))
    parser.add_argument("--profiles", default=os.getenv("GRAPHHOPPER_PROFILE_SMOKE_PROFILES", ",".join(DEFAULT_PROFILES)))
    parser.add_argument("--report-json", default=os.getenv("GRAPHHOPPER_PROFILE_SMOKE_REPORT_FILE"))
    parser.add_argument(
        "--skip-policy-details",
        action="store_true",
        default=os.getenv("GRAPHHOPPER_PROFILE_SMOKE_SKIP_POLICY_DETAILS", "false").lower() == "true",
        help="path details 기반 접근성 hard policy 검증을 건너뛴다.",
    )
    args = parser.parse_args()

    exporter = load_exporter()
    profiles = profile_list(args.profiles)
    with exporter.connect() as conn:
        candidates = fetch_candidates(conn, args.candidate_limit)

    if not candidates:
        report = {
            "generatedAt": datetime.now(timezone.utc).isoformat(),
            "status": "FAIL",
            "error": "no routeable road_segments candidate found",
            "profiles": profiles,
        }
        write_report(args.report_json, report)
        print("GraphHopper profile smoke failed: no routeable candidate")
        return 1

    attempts = []
    selected = None
    for candidate in candidates:
        # 개별 segment는 profile별 custom model 규칙 적용 후에도 고립되거나
        # unroutable할 수 있으므로 여러 후보를 순서대로 시도한다.
        results = [
            smoke_profile(
                args.base_url,
                candidate,
                profile,
                args.timeout_seconds,
                require_policy_details=not args.skip_policy_details,
            )
            for profile in profiles
        ]
        attempt = {
            "candidate": candidate,
            "results": results,
        }
        attempts.append(attempt)
        if all(result["status"] == "PASS" for result in results):
            selected = attempt
            break

    report = {
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "status": "PASS" if selected else "FAIL",
        "baseUrl": args.base_url,
        "profileCount": len(profiles),
        "candidateLimit": args.candidate_limit,
        "selectedCandidate": selected["candidate"] if selected else None,
        "results": selected["results"] if selected else attempts[-1]["results"],
        "attempts": attempts,
    }
    write_report(args.report_json, report)

    if not selected:
        print(f"GraphHopper profile smoke failed: profiles={','.join(profiles)}")
        return 1

    print(
        "GraphHopper profile smoke ok: "
        f"profiles={len(profiles)}, edgeId={selected['candidate']['edge_id']}, "
        f"lengthMeter={round(float(selected['candidate']['length_meter']), 3)}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
