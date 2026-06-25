#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

ENV_FILE="${ENV_FILE:-"$ROOT_DIR/.env.prod"}"
source "$ROOT_DIR/scripts/make/lib/prod-db-tunnel.sh"

SERVER_PORT="${SERVER_PORT:-8080}"
AI_PORT="${AI_PORT:-5000}"
ADMIN_PORT="${ADMIN_PORT:-3001}"
GRAPHHOPPER_ADMIN_PORT="${GRAPHHOPPER_ADMIN_PORT:-8990}"
GRAPHHOPPER_BLUE_ADMIN_PORT="${GRAPHHOPPER_BLUE_ADMIN_PORT:-18990}"
GRAPHHOPPER_GREEN_ADMIN_PORT="${GRAPHHOPPER_GREEN_ADMIN_PORT:-18992}"
GRAPHHOPPER_ACTIVE_SLOT_KEY="${GRAPHHOPPER_ACTIVE_SLOT_KEY:-graphhopper:active-slot}"
ALLOW_GRAPHHOPPER_SMOKE_WITHOUT_ACTIVE_SLOT="${ALLOW_GRAPHHOPPER_SMOKE_WITHOUT_ACTIVE_SLOT:-false}"
DEPLOY_GRAPHHOPPER="${DEPLOY_GRAPHHOPPER:-true}"
SMOKE_ADMIN="${SMOKE_ADMIN:-true}"
SMOKE_RETRIES="${SMOKE_RETRIES:-24}"
SMOKE_DELAY_SECONDS="${SMOKE_DELAY_SECONDS:-5}"

redis_env_args() {
  local password
  password="$(env_value REDIS_PASSWORD)"
  if [ -n "$password" ]; then
    printf '%s\n' "-e" "REDISCLI_AUTH=$password"
  fi
}

redis_cli() {
  local host port ssl
  host="$(env_value REDIS_HOST)"
  port="$(env_value REDIS_PORT)"
  ssl="$(env_value REDIS_SSL)"
  port="${port:-6379}"
  ssl="${ssl:-true}"
  if [ -z "$host" ]; then
    echo "REDIS_HOST must be set in $ENV_FILE for active GraphHopper smoke" >&2
    return 1
  fi

  local args=(docker run --rm --network host)
  while IFS= read -r item; do
    args+=("$item")
  done < <(redis_env_args)
  args+=(redis:7.2-alpine redis-cli --raw --no-auth-warning -h "$host" -p "$port")
  if [ "$ssl" = "true" ]; then
    args+=(--tls)
  fi
  args+=("$@")
  "${args[@]}"
}

redis_get() {
  redis_cli GET "$1" | tr -d '\r'
}

wait_for_url() {
  local url="$1"
  local name="$2"
  local attempt

  for attempt in $(seq 1 "$SMOKE_RETRIES"); do
    if curl -fsS "$url" >/dev/null; then
      return 0
    fi
    sleep "$SMOKE_DELAY_SECONDS"
  done

  echo "${name} smoke check failed: ${url}" >&2
  return 1
}

wait_for_any_url() {
  local name="$1"
  shift
  local attempt
  local url

  for attempt in $(seq 1 "$SMOKE_RETRIES"); do
    for url in "$@"; do
      if curl -fsS "$url" >/dev/null; then
        return 0
      fi
    done
    sleep "$SMOKE_DELAY_SECONDS"
  done

  echo "${name} smoke check failed: $*" >&2
  return 1
}

graphhopper_active_health_url() {
  case "$1" in
    blue) echo "http://127.0.0.1:${GRAPHHOPPER_BLUE_ADMIN_PORT}/healthcheck" ;;
    green) echo "http://127.0.0.1:${GRAPHHOPPER_GREEN_ADMIN_PORT}/healthcheck" ;;
    *) return 1 ;;
  esac
}

wait_for_active_graphhopper_slot() {
  local active_slot
  active_slot="$(redis_get "$GRAPHHOPPER_ACTIVE_SLOT_KEY" 2>/dev/null || true)"
  if [ "$active_slot" != "blue" ] && [ "$active_slot" != "green" ]; then
    if [ "$ALLOW_GRAPHHOPPER_SMOKE_WITHOUT_ACTIVE_SLOT" = "true" ]; then
      echo "GraphHopper active slot is not initialized. Falling back to any-slot smoke." >&2
      wait_for_any_url "GraphHopper" \
        "http://127.0.0.1:${GRAPHHOPPER_BLUE_ADMIN_PORT}/healthcheck" \
        "http://127.0.0.1:${GRAPHHOPPER_GREEN_ADMIN_PORT}/healthcheck" \
        "http://127.0.0.1:${GRAPHHOPPER_ADMIN_PORT}/healthcheck"
      return
    fi
    echo "GraphHopper active slot smoke failed: invalid Redis value for ${GRAPHHOPPER_ACTIVE_SLOT_KEY}: ${active_slot:-<empty>}" >&2
    return 1
  fi

  wait_for_url "$(graphhopper_active_health_url "$active_slot")" "GraphHopper active slot ${active_slot}"
}

wait_for_response() {
  local method="$1"
  local url="$2"
  local request_body="$3"

  if [ "$method" = "POST" ]; then
    curl -sS -o /tmp/prod-smoke-response.json -w "%{http_code}" \
      -H "Content-Type: application/json" \
      -d "$request_body" \
      "$url" || true
    return
  fi

  curl -sS -o /tmp/prod-smoke-response.json -w "%{http_code}" "$url" || true
}

wait_for_status_with_body() {
  local method="$1"
  local url="$2"
  local name="$3"
  local expected_status="$4"
  local request_body="$5"
  shift 5

  local attempt
  local status=""
  local pattern
  local matched

  for attempt in $(seq 1 "$SMOKE_RETRIES"); do
    status="$(wait_for_response "$method" "$url" "$request_body")"
    if [ "$status" = "$expected_status" ]; then
      matched="true"
      for pattern in "$@"; do
        if ! grep -Eq "$pattern" /tmp/prod-smoke-response.json; then
          matched="false"
          break
        fi
      done
      if [ "$matched" = "true" ]; then
        return 0
      fi
    fi
    sleep "$SMOKE_DELAY_SECONDS"
  done

  echo "${name} smoke check failed: ${url} (expected ${expected_status}, got ${status})" >&2
  if [ -f /tmp/prod-smoke-response.json ]; then
    cat /tmp/prod-smoke-response.json >&2
    echo >&2
  fi
  return 1
}

wait_for_status_with_body "GET" "http://127.0.0.1:${AI_PORT}/health" "AI health" "200" "" \
  '"providers"[[:space:]]*:' \
  '"POST /voice/analyze"'
wait_for_status_with_body "POST" "http://127.0.0.1:${AI_PORT}/voice/analyze" "AI voice analyze" "400" '{}' \
  '"status"[[:space:]]*:[[:space:]]*"C4000"' \
  '"message"[[:space:]]*:' \
  '"data"[[:space:]]*:[[:space:]]*null'
wait_for_url "http://127.0.0.1:${SERVER_PORT}/v3/api-docs" "Backend"
if [ "$SMOKE_ADMIN" = "true" ]; then
  wait_for_status_with_body "GET" "http://127.0.0.1:${ADMIN_PORT}/health" "ADMIN health" "200" "" \
    'ok'
  wait_for_status_with_body "GET" "http://127.0.0.1:${ADMIN_PORT}/" "ADMIN web" "200" "" \
    '<div id="root"></div>'
fi

if [ "$DEPLOY_GRAPHHOPPER" = "true" ]; then
  wait_for_active_graphhopper_slot
fi

echo "prod smoke test passed"
