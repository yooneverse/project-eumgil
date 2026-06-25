#!/usr/bin/env python3
"""
dev backend에 transit search -> select -> transit-refresh 실요청을 보내 응답 shape를 검증한다.

전제:
- dev backend가 localhost SERVER_PORT에 떠 있어야 한다.
- dev DB/Redis/GraphHopper 터널이 열려 있어야 한다.
- .env.dev와 환경변수의 JWT_SECRET/JWT_ISSUER/DB 접속 값을 사용한다.
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import hmac
import json
import os
import re
import subprocess
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any


ROOT_DIR = Path(__file__).resolve().parents[2]
ENV_FILE = ROOT_DIR / ".env.dev"
SMOKE_SOCIAL_PROVIDER = "KAKAO"
SMOKE_SOCIAL_PROVIDER_USER_ID = "route-transit-refresh-smoke-S14P31E102-501"
ARRIVAL_STATUSES = {
    "REALTIME_AVAILABLE",
    "NO_CURRENT_ARRIVAL",
    "ARRIVAL_UNKNOWN",
    "SCHEDULE_BASED",
}


def load_env(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    if not path.exists():
        raise SystemExit(f"missing env file: {path}")
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key] = value.strip().strip('"').strip("'")
    return values


def env_value(env: dict[str, str], key: str, default: str = "") -> str:
    return os.environ.get(key) or env.get(key) or default


def ensure_smoke_user(env: dict[str, str]) -> str:
    db_name = env_value(env, "POSTGRES_DB", "e102")
    db_user = env_value(env, "DB_USERNAME") or env_value(env, "POSTGRES_USER", "e102")
    db_password = env_value(env, "DB_PASSWORD") or env_value(env, "POSTGRES_PASSWORD", "e102")
    db_host = env_value(env, "BE_DEV_DB_HOST", "127.0.0.1")
    db_port = env_value(env, "BE_DEV_DB_LOCAL_PORT", "15432")
    sql = f"""
    INSERT INTO users (
      user_id,
      social_provider,
      social_provider_user_id,
      selected_primary_user_type,
      selected_mobility_subtype,
      created_at,
      updated_at
    )
    VALUES (
      gen_random_uuid(),
      '{SMOKE_SOCIAL_PROVIDER}',
      '{SMOKE_SOCIAL_PROVIDER_USER_ID}',
      'LOW_VISION',
      NULL,
      now(),
      now()
    )
    ON CONFLICT (social_provider, social_provider_user_id)
    DO UPDATE SET
      selected_primary_user_type = 'LOW_VISION',
      selected_mobility_subtype = NULL,
      updated_at = now()
    RETURNING user_id;
    """
    process_env = os.environ.copy()
    process_env["PGPASSWORD"] = db_password
    result = subprocess.run(
        [
            "psql",
            "-h",
            db_host,
            "-p",
            db_port,
            "-U",
            db_user,
            "-d",
            db_name,
            "-At",
            "-c",
            sql,
        ],
        cwd=ROOT_DIR,
        env=process_env,
        check=True,
        capture_output=True,
        text=True,
    )
    uuid_pattern = re.compile(r"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    user_id = next(
        (line.strip() for line in result.stdout.splitlines() if uuid_pattern.match(line.strip())),
        "",
    )
    if not user_id:
        raise SystemExit("failed to prepare smoke user")
    return user_id


def create_access_token(env: dict[str, str], user_id: str) -> str:
    secret_value = env_value(env, "JWT_SECRET")
    if not secret_value:
        raise SystemExit("JWT_SECRET is required for smoke token generation")
    now = int(time.time())
    header = {"alg": "HS256", "typ": "JWT"}
    payload = {
        "iss": env_value(env, "JWT_ISSUER", "e102-dev"),
        "sub": user_id,
        "tokenType": "ACCESS",
        "iat": now,
        "exp": now + 15 * 60,
    }
    signing_input = ".".join(
        [
            base64url(json.dumps(header, separators=(",", ":")).encode()),
            base64url(json.dumps(payload, separators=(",", ":")).encode()),
        ]
    )
    secret = base64.b64decode(secret_value)
    signature = hmac.new(secret, signing_input.encode(), hashlib.sha256).digest()
    return signing_input + "." + base64url(signature)


def base64url(value: bytes) -> str:
    return base64.urlsafe_b64encode(value).rstrip(b"=").decode()


def request_json(env: dict[str, str], token: str, method: str, path: str, payload: dict[str, Any] | None) -> dict[str, Any]:
    port = env_value(env, "SERVER_PORT", "8080")
    base_url = env_value(env, "ROUTE_SMOKE_BASE_URL", f"http://127.0.0.1:{port}")
    data = None if payload is None else json.dumps(payload).encode()
    request = urllib.request.Request(
        base_url + path,
        data=data,
        headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json",
        },
        method=method,
    )
    try:
        with urllib.request.urlopen(request, timeout=45) as response:
            body = json.loads(response.read().decode())
            body["_httpStatus"] = response.status
            body["_path"] = path
            return body
    except urllib.error.HTTPError as error:
        body = json.loads(error.read().decode())
        body["_httpStatus"] = error.code
        body["_path"] = path
        return body


def assert_response(body: dict[str, Any], http_status: int, status: str) -> None:
    if body.get("_httpStatus") != http_status or body.get("status") != status:
        raise SystemExit(f"unexpected response: expected={http_status}/{status} body={body}")


def write_result(args: argparse.Namespace, result: dict[str, Any]) -> None:
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def find_refresh_target(routes: list[dict[str, Any]]) -> tuple[dict[str, Any], dict[str, Any]]:
    for route in routes:
        for leg in route.get("legs") or []:
            if leg.get("type") in {"BUS", "SUBWAY"}:
                return route, leg
    raise SystemExit("missing BUS/SUBWAY leg in transit search response")


def validate_refresh(refresh: dict[str, Any]) -> None:
    assert_response(refresh, 200, "S2000")
    data = refresh.get("data") or {}
    if data.get("type") not in {"BUS", "SUBWAY"}:
        raise SystemExit(f"unexpected refresh type: {data}")
    if data.get("arrivalStatus") not in ARRIVAL_STATUSES:
        raise SystemExit(f"unexpected arrivalStatus: {data}")
    transits = data.get("transits")
    if not isinstance(transits, list):
        raise SystemExit(f"transits must be array: {data}")
    if data["arrivalStatus"] in {"REALTIME_AVAILABLE", "SCHEDULE_BASED"}:
        if not transits:
            raise SystemExit(f"arrivalStatus requires transits: {data}")
        for transit in transits:
            if not transit.get("routeNo") or transit.get("remainingMinute") is None:
                raise SystemExit(f"invalid transit item: {transit}")


def main() -> None:
    parser = argparse.ArgumentParser(description="Smoke transit search/select/transit-refresh against dev backend.")
    parser.add_argument("--json", action="store_true", help="Print full response JSON.")
    parser.add_argument("--output", type=Path, help="Write full smoke JSON to the given path.")
    parser.add_argument("--start-lat", type=float, default=35.1200)
    parser.add_argument("--start-lng", type=float, default=128.9360)
    parser.add_argument("--end-lat", type=float, default=35.1315)
    parser.add_argument("--end-lng", type=float, default=128.8823)
    args = parser.parse_args()

    env = load_env(ENV_FILE)
    user_id = ensure_smoke_user(env)
    token = create_access_token(env, user_id)
    search_payload = {
        "startPoint": {"lat": args.start_lat, "lng": args.start_lng},
        "endPoint": {"lat": args.end_lat, "lng": args.end_lng},
    }
    search = request_json(env, token, "POST", "/routes/search/transit", search_payload)
    if search.get("_httpStatus") != 200 or search.get("status") != "S2000":
        result = {
            "summary": {
                "success": False,
                "failedStep": "transitSearch",
                "userId": user_id,
                "searchHttpStatus": search.get("_httpStatus"),
                "searchStatus": search.get("status"),
                "coordinates": search_payload,
            },
            "responses": {
                "search": search,
            },
        }
        write_result(args, result)
        raise SystemExit(f"unexpected response: expected=200/S2000 body={search}")
    routes = search.get("data", {}).get("routes") or []
    if not search.get("data", {}).get("searchId") or not routes:
        raise SystemExit(f"missing searchId/routes: {search}")

    route, leg = find_refresh_target(routes)
    search_id = search["data"]["searchId"]
    route_id = route["routeId"]
    leg_sequence = leg["sequence"]
    select = request_json(env, token, "POST", f"/routes/{route_id}/select", {"searchId": search_id})
    assert_response(select, 200, "S2000")
    if select.get("data") is not None:
        raise SystemExit(f"select data must be null: {select}")

    refresh = request_json(env, token, "POST", f"/routes/{route_id}/transit-refresh", {"legSequence": leg_sequence})
    validate_refresh(refresh)
    summary = {
        "userId": user_id,
        "searchId": search_id,
        "routeId": route_id,
        "legSequence": leg_sequence,
        "legType": leg.get("type"),
        "routeCount": len(routes),
        "searchHttpStatus": search["_httpStatus"],
        "selectHttpStatus": select["_httpStatus"],
        "refreshHttpStatus": refresh["_httpStatus"],
        "arrivalStatus": refresh["data"]["arrivalStatus"],
        "transitCount": len(refresh["data"]["transits"]),
        "coordinates": search_payload,
    }
    result = {
        "summary": summary,
        "responses": {
            "search": search,
            "select": select,
            "refresh": refresh,
        },
    }
    write_result(args, result)
    print(json.dumps(result if args.json else summary, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
