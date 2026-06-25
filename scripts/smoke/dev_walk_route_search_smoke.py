#!/usr/bin/env python3
"""
dev backend에 POST /routes/search/walk 실요청을 보내 응답 shape를 검증한다.

전제:
- make be-dev-up 또는 동일한 dev backend가 localhost SERVER_PORT에 떠 있어야 한다.
- dev DB/Redis/GraphHopper 터널이 열려 있어야 한다.
- .env.dev의 JWT_SECRET/JWT_ISSUER/DB 접속 값을 사용한다.
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
SMOKE_SOCIAL_PROVIDER_USER_ID = "route-search-smoke-S14P31E102-468"


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
    secret = base64.b64decode(env_value(env, "JWT_SECRET"))
    signature = hmac.new(secret, signing_input.encode(), hashlib.sha256).digest()
    return signing_input + "." + base64url(signature)


def base64url(value: bytes) -> str:
    return base64.urlsafe_b64encode(value).rstrip(b"=").decode()


def request_walk_route(env: dict[str, str], token: str) -> dict[str, Any]:
    port = env_value(env, "SERVER_PORT", "8080")
    url = env_value(env, "WALK_ROUTE_SMOKE_URL", f"http://127.0.0.1:{port}/routes/search/walk")
    payload = {
        "startPoint": {"lat": 35.1200, "lng": 128.9360},
        "endPoint": {"lat": 35.1315, "lng": 128.8823},
    }
    request = urllib.request.Request(
        url,
        data=json.dumps(payload).encode(),
        headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            body = json.loads(response.read().decode())
            body["_httpStatus"] = response.status
            body["_url"] = url
            return body
    except urllib.error.HTTPError as error:
        body = json.loads(error.read().decode())
        body["_httpStatus"] = error.code
        body["_url"] = url
        return body


def validate_response(body: dict[str, Any]) -> None:
    if body.get("_httpStatus") != 200:
        raise SystemExit(f"walk route smoke failed: http={body.get('_httpStatus')} body={body}")
    if body.get("status") != "S2000":
        raise SystemExit(f"unexpected response status: {body.get('status')}")
    data = body.get("data") or {}
    routes = data.get("routes") or []
    if not data.get("searchId") or not routes:
        raise SystemExit(f"missing searchId/routes: {body}")
    options = {route.get("routeOption") for route in routes}
    if not {"SAFE", "SHORTEST"}.issubset(options):
        raise SystemExit(f"SAFE/SHORTEST candidates missing: {options}")
    required_route_fields = {
        "routeId",
        "transportMode",
        "routeOption",
        "distanceMeter",
        "durationSecond",
        "estimatedTimeMinute",
        "geometry",
        "legs",
    }
    for route in routes:
        missing = required_route_fields - set(route)
        if missing:
            raise SystemExit(f"route fields missing: {missing}")
        if not route["legs"] or not route["legs"][0].get("guidanceEvents"):
            raise SystemExit(f"walk leg guidanceEvents missing: {route.get('routeId')}")


def main() -> None:
    parser = argparse.ArgumentParser(description="Smoke POST /routes/search/walk against dev backend.")
    parser.add_argument("--json", action="store_true", help="Print full response JSON.")
    args = parser.parse_args()

    env = load_env(ENV_FILE)
    user_id = ensure_smoke_user(env)
    token = create_access_token(env, user_id)
    body = request_walk_route(env, token)
    validate_response(body)
    summary = {
        "httpStatus": body["_httpStatus"],
        "url": body["_url"],
        "searchId": body["data"]["searchId"],
        "routeCount": len(body["data"]["routes"]),
        "routeOptions": [route["routeOption"] for route in body["data"]["routes"]],
        "guidanceEventCounts": [len(route["legs"][0]["guidanceEvents"]) for route in body["data"]["routes"]],
    }
    print(json.dumps(body if args.json else summary, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
