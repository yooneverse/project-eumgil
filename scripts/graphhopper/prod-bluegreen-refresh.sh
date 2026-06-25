#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

source "$ROOT_DIR/scripts/make/lib/prod-db-tunnel.sh"

ENV_FILE="${ENV_FILE:-"$ROOT_DIR/.env.prod"}"
PROD_COMPOSE=(docker compose --env-file "$ENV_FILE" -f "$ROOT_DIR/docker-compose.prod.yml")
LOCK_FILE="${GRAPHHOPPER_REFRESH_LOCK_FILE:-$ROOT_DIR/.deploy-state/graphhopper-refresh.lock}"
REPORT_DIR="${GRAPHHOPPER_REFRESH_REPORT_DIR:-$ROOT_DIR/runtime/graphhopper/refresh}"
BUILD_ID="${GRAPHHOPPER_REFRESH_BUILD_ID:-$(date -u +"%Y%m%dT%H%M%SZ")}"
ACTIVE_SLOT_KEY="${GRAPHHOPPER_ACTIVE_SLOT_KEY:-graphhopper:active-slot}"
PREVIOUS_SLOT_KEY="${GRAPHHOPPER_PREVIOUS_SLOT_KEY:-graphhopper:previous-slot}"
ACTIVE_BUILD_ID_KEY="${GRAPHHOPPER_ACTIVE_BUILD_ID_KEY:-graphhopper:active-build-id}"
BLUE_URL_KEY="${GRAPHHOPPER_BLUE_URL_KEY:-graphhopper:blue:url}"
GREEN_URL_KEY="${GRAPHHOPPER_GREEN_URL_KEY:-graphhopper:green:url}"
BLUE_URL="${GRAPHHOPPER_BLUE_URL:-http://graphhopper-blue:8989}"
GREEN_URL="${GRAPHHOPPER_GREEN_URL:-http://graphhopper-green:8989}"
CANDIDATE_URL="${GRAPHHOPPER_CANDIDATE_URL:-http://graphhopper-candidate:8989}"
CANDIDATE_GRAPH_LOCATION="${GRAPHHOPPER_CANDIDATE_GRAPH_LOCATION:-/graphhopper/candidate-data}"
BOOTSTRAP_SLOT="${GRAPHHOPPER_BOOTSTRAP_SLOT:-blue}"
DRAIN_SECONDS="${GRAPHHOPPER_OLD_SLOT_DRAIN_SECONDS:-60}"
SMOKE_TIMEOUT_SECONDS="${GRAPHHOPPER_PROFILE_SMOKE_TIMEOUT_SECONDS:-10}"
BACKEND_SMOKE_URL="${GRAPHHOPPER_BACKEND_SMOKE_URL:-}"
BACKEND_SMOKE_REQUIRED="${GRAPHHOPPER_BACKEND_SMOKE_REQUIRED:-true}"
REPORT_FILE="$REPORT_DIR/$BUILD_ID.json"

status="RUNNING"
active_slot=""
previous_slot=""
candidate_slot=""
switched="false"
error_message=""
warning_message=""
publish_fallback_armed="false"
publish_target_validated="false"
target_cache_snapshot_available="false"
original_previous_slot=""

json_escape() {
  printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'
}

write_report() {
  mkdir -p "$REPORT_DIR"
  cat >"$REPORT_FILE" <<EOF
{
  "buildId": "$(json_escape "$BUILD_ID")",
  "status": "$(json_escape "$status")",
  "activeSlotBefore": "$(json_escape "$active_slot")",
  "candidateSlot": "$(json_escape "$candidate_slot")",
  "previousSlotBefore": "$(json_escape "$previous_slot")",
  "switched": "$(json_escape "$switched")",
  "reportGeneratedAt": "$(date -u +"%Y-%m-%dT%H:%M:%SZ")",
  "warningMessage": "$(json_escape "$warning_message")",
  "errorMessage": "$(json_escape "$error_message")"
}
EOF
}

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
    echo "REDIS_HOST must be set in $ENV_FILE" >&2
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

redis_mset() {
  redis_cli MSET "$@" >/dev/null
}

redis_del() {
  redis_cli DEL "$@" >/dev/null
}

slot_url_key() {
  case "$1" in
    blue) echo "$BLUE_URL_KEY" ;;
    green) echo "$GREEN_URL_KEY" ;;
    *) return 1 ;;
  esac
}

slot_runtime_url() {
  case "$1" in
    blue) echo "$BLUE_URL" ;;
    green) echo "$GREEN_URL" ;;
    *) return 1 ;;
  esac
}

slot_admin_port() {
  case "$1" in
    blue)
      local port
      port="$(env_value GRAPHHOPPER_BLUE_ADMIN_PORT)"
      echo "${port:-18990}"
      ;;
    green)
      local port
      port="$(env_value GRAPHHOPPER_GREEN_ADMIN_PORT)"
      echo "${port:-18992}"
      ;;
    *) return 1 ;;
  esac
}

candidate_admin_port() {
  local port
  port="$(env_value GRAPHHOPPER_CANDIDATE_ADMIN_PORT)"
  echo "${port:-18994}"
}

slot_graph_location() {
  case "$1" in
    blue) echo "/graphhopper/blue-data" ;;
    green) echo "/graphhopper/green-data" ;;
    *) return 1 ;;
  esac
}

other_slot() {
  case "$1" in
    blue) echo "green" ;;
    green) echo "blue" ;;
    *) echo "$BOOTSTRAP_SLOT" ;;
  esac
}

is_slot() {
  [ "$1" = "blue" ] || [ "$1" = "green" ]
}

wait_for_temp_candidate_health() {
  local admin_port
  admin_port="$(candidate_admin_port)"
  local url="http://127.0.0.1:$admin_port/healthcheck"
  echo "waiting for temporary candidate GraphHopper healthcheck: $url"
  for _ in $(seq 1 120); do
    if curl -fsS "$url" >/dev/null 2>&1; then
      return 0
    fi
    sleep 3
  done
  "${PROD_COMPOSE[@]}" --profile graphhopper-candidate logs --tail=160 graphhopper-candidate >&2 || true
  return 1
}

wait_for_candidate_slot_health() {
  local candidate="$1"
  local admin_port
  admin_port="$(slot_admin_port "$candidate")"
  local url="http://127.0.0.1:$admin_port/healthcheck"
  echo "waiting for candidate slot GraphHopper healthcheck: $url"
  for _ in $(seq 1 120); do
    if curl -fsS "$url" >/dev/null 2>&1; then
      return 0
    fi
    sleep 3
  done
  "${PROD_COMPOSE[@]}" --profile graphhopper logs --tail=160 "graphhopper-$candidate_slot" >&2 || true
  return 1
}

run_profile_smoke() {
  local base_url="$1"
  local label="$2"
  echo "running GraphHopper profile smoke for $label: $base_url"
  DB_URL="$PROD_DB_URL" \
  GRAPHHOPPER_PROFILE_SMOKE_BASE_URL="$base_url" \
  GRAPHHOPPER_PROFILE_SMOKE_TIMEOUT_SECONDS="$SMOKE_TIMEOUT_SECONDS" \
  "${PROD_COMPOSE[@]}" --profile graphhopper-build run --rm --no-deps --entrypoint python3 \
    -e DB_URL="$PROD_DB_URL" \
    -e GRAPHHOPPER_PROFILE_SMOKE_BASE_URL="$base_url" \
    -e GRAPHHOPPER_PROFILE_SMOKE_TIMEOUT_SECONDS="$SMOKE_TIMEOUT_SECONDS" \
    graphhopper-build \
    /usr/local/bin/smoke-graphhopper-profiles.py \
      --report-json "/graphhopper/import/profile-smoke-$BUILD_ID-$label.json"
}

wait_for_slot_health() {
  local slot="$1"
  local attempts="${2:-20}"
  local delay_seconds="${3:-3}"
  local admin_port
  admin_port="$(slot_admin_port "$slot")"
  local url="http://127.0.0.1:$admin_port/healthcheck"
  for _ in $(seq 1 "$attempts"); do
    if curl -fsS "$url" >/dev/null 2>&1; then
      return 0
    fi
    if [ "$delay_seconds" -gt 0 ]; then
      sleep "$delay_seconds"
    fi
  done
  return 1
}

ensure_slot_runtime() {
  local slot="$1"
  if ! is_slot "$slot"; then
    return 1
  fi

  if wait_for_slot_health "$slot" 1 0; then
    return 0
  fi

  echo "GraphHopper slot $slot is not healthy. Starting runtime."
  "${PROD_COMPOSE[@]}" --profile graphhopper up -d --no-recreate "graphhopper-$slot" >/dev/null
  if wait_for_slot_health "$slot" 20 3; then
    return 0
  fi

  echo "GraphHopper slot $slot is still unhealthy after start. Restarting once."
  "${PROD_COMPOSE[@]}" --profile graphhopper restart "graphhopper-$slot" >/dev/null 2>&1 || true
  if wait_for_slot_health "$slot" 20 3; then
    return 0
  fi

  "${PROD_COMPOSE[@]}" --profile graphhopper logs --tail=160 "graphhopper-$slot" >&2 || true
  return 1
}

arm_publish_fallback() {
  if [ -z "$active_slot" ]; then
    return 0
  fi
  local candidate_url_key
  candidate_url_key="$(slot_url_key "$candidate_slot")"
  original_previous_slot="$previous_slot"
  echo "arming temporary previous fallback for $candidate_slot through graphhopper-candidate"
  redis_mset \
    "$PREVIOUS_SLOT_KEY" "$candidate_slot" \
    "$candidate_url_key" "$CANDIDATE_URL"
  publish_fallback_armed="true"
}

restore_publish_fallback() {
  if [ "$publish_fallback_armed" != "true" ]; then
    return 0
  fi
  local candidate_url_key candidate_runtime_url
  candidate_url_key="$(slot_url_key "$candidate_slot")"
  candidate_runtime_url="$(slot_runtime_url "$candidate_slot")"
  echo "restoring temporary previous fallback for $candidate_slot" >&2
  redis_mset "$candidate_url_key" "$candidate_runtime_url"
  if is_slot "$original_previous_slot"; then
    redis_mset "$PREVIOUS_SLOT_KEY" "$original_previous_slot"
  else
    redis_del "$PREVIOUS_SLOT_KEY"
  fi
  publish_fallback_armed="false"
}

publish_candidate_cache_to_slot() {
  local slot="$1"
  local target_graph_location
  target_graph_location="$(slot_graph_location "$slot")"
  echo "publishing temporary candidate cache to $slot slot: $target_graph_location"
  DB_URL="$PROD_DB_URL" \
  "${PROD_COMPOSE[@]}" --profile graphhopper-build run --rm --no-deps --entrypoint sh \
    -e DB_URL="$PROD_DB_URL" \
    -e CANDIDATE_GRAPH_LOCATION="$CANDIDATE_GRAPH_LOCATION" \
    -e TARGET_GRAPH_LOCATION="$target_graph_location" \
    graphhopper-build \
    -ceu '
      test -d "$CANDIDATE_GRAPH_LOCATION"
      test -n "$(find "$CANDIDATE_GRAPH_LOCATION" -mindepth 1 -maxdepth 1 2>/dev/null)"
      mkdir -p "$TARGET_GRAPH_LOCATION"
      find "$TARGET_GRAPH_LOCATION" -mindepth 1 -maxdepth 1 -exec rm -rf {} +
      cp -a "$CANDIDATE_GRAPH_LOCATION"/. "$TARGET_GRAPH_LOCATION"/
    '
}

snapshot_target_slot_cache() {
  local slot="$1"
  local target_graph_location
  target_graph_location="$(slot_graph_location "$slot")"
  echo "snapshotting current $slot slot cache before publish"
  DB_URL="$PROD_DB_URL" \
  "${PROD_COMPOSE[@]}" --profile graphhopper-build run --rm --no-deps --entrypoint sh \
    -e DB_URL="$PROD_DB_URL" \
    -e TARGET_GRAPH_LOCATION="$target_graph_location" \
    graphhopper-build \
    -ceu '
      previous_graph_location="/graphhopper/previous-cache"
      test -d "$TARGET_GRAPH_LOCATION"
      test -n "$(find "$TARGET_GRAPH_LOCATION" -mindepth 1 -maxdepth 1 2>/dev/null)"
      mkdir -p "$previous_graph_location"
      find "$previous_graph_location" -mindepth 1 -maxdepth 1 -exec rm -rf {} +
      cp -a "$TARGET_GRAPH_LOCATION"/. "$previous_graph_location"/
    '
}

restore_target_slot_cache() {
  local slot="$1"
  if [ "$target_cache_snapshot_available" != "true" ]; then
    return 1
  fi
  local target_graph_location
  target_graph_location="$(slot_graph_location "$slot")"
  echo "restoring previous $slot slot cache after failed publish" >&2
  "${PROD_COMPOSE[@]}" --profile graphhopper stop "graphhopper-$slot" >/dev/null 2>&1 || true
  DB_URL="$PROD_DB_URL" \
  "${PROD_COMPOSE[@]}" --profile graphhopper-build run --rm --no-deps --entrypoint sh \
    -e DB_URL="$PROD_DB_URL" \
    -e TARGET_GRAPH_LOCATION="$target_graph_location" \
    graphhopper-build \
    -ceu '
      previous_graph_location="/graphhopper/previous-cache"
      test -d "$previous_graph_location"
      test -n "$(find "$previous_graph_location" -mindepth 1 -maxdepth 1 2>/dev/null)"
      mkdir -p "$TARGET_GRAPH_LOCATION"
      find "$TARGET_GRAPH_LOCATION" -mindepth 1 -maxdepth 1 -exec rm -rf {} +
      cp -a "$previous_graph_location"/. "$TARGET_GRAPH_LOCATION"/
    '
  "${PROD_COMPOSE[@]}" --profile graphhopper up -d --build "graphhopper-$slot"
  wait_for_candidate_slot_health "$slot"
}

verify_active_slot_after_switch() {
  local redis_active
  redis_active="$(redis_get "$ACTIVE_SLOT_KEY")"
  if [ "$redis_active" != "$candidate_slot" ]; then
    echo "Redis active slot verification failed. expected=$candidate_slot actual=$redis_active" >&2
    return 1
  fi
  wait_for_slot_health "$redis_active" 20 3
}

cleanup_temp_candidate_runtime() {
  "${PROD_COMPOSE[@]}" --profile graphhopper-candidate stop graphhopper-candidate >/dev/null 2>&1 || true
}

rollback_switch() {
  if [ "$switched" != "true" ] || ! is_slot "$active_slot"; then
    return 0
  fi
  echo "rolling back GraphHopper active slot to $active_slot" >&2
  if ! redis_mset \
    "$BLUE_URL_KEY" "$BLUE_URL" \
    "$GREEN_URL_KEY" "$GREEN_URL" \
    "$ACTIVE_SLOT_KEY" "$active_slot" \
    "$PREVIOUS_SLOT_KEY" "$candidate_slot" \
    "$ACTIVE_BUILD_ID_KEY" "rollback-$BUILD_ID"; then
    echo "Redis rollback write failed" >&2
    return 1
  fi
  local restored_active
  restored_active="$(redis_get "$ACTIVE_SLOT_KEY" || true)"
  if [ "$restored_active" != "$active_slot" ]; then
    echo "Redis rollback verification failed. expected=$active_slot actual=$restored_active" >&2
    return 1
  fi
}

default_backend_smoke_url() {
  local server_port
  server_port="$(env_value SERVER_PORT)"
  server_port="${server_port:-8080}"
  echo "http://127.0.0.1:$server_port/health/graphhopper"
}

on_error() {
  local line="$1"
  set +e
  error_message="GraphHopper blue/green refresh failed at line $line"
  local keep_temp_candidate_fallback="false"
  if [ "$publish_fallback_armed" = "true" ] && [ "$publish_target_validated" != "true" ]; then
    if restore_target_slot_cache "$candidate_slot"; then
      if ! restore_publish_fallback; then
        keep_temp_candidate_fallback="true"
        warning_message="$warning_message; failed to restore temporary publish fallback"
        echo "keeping temporary candidate GraphHopper runtime alive because Redis fallback restore failed" >&2
      fi
    else
      keep_temp_candidate_fallback="true"
      warning_message="$warning_message; temporary candidate fallback left armed because target slot restore failed"
      echo "keeping temporary candidate GraphHopper runtime alive as previous fallback" >&2
    fi
  else
    restore_publish_fallback || warning_message="$warning_message; failed to restore temporary publish fallback"
  fi
  if [ "$keep_temp_candidate_fallback" != "true" ]; then
    cleanup_temp_candidate_runtime
  fi
  status="FAILED"
  if [ "$switched" = "true" ]; then
    if rollback_switch; then
      status="ROLLED_BACK"
    else
      status="ROLLBACK_FAILED"
      error_message="$error_message; rollback verification failed"
    fi
  fi
  write_report
  echo "$error_message" >&2
  echo "report: $REPORT_FILE" >&2
  exit 1
}

trap 'on_error "$LINENO"' ERR

mkdir -p "$(dirname "$LOCK_FILE")" "$REPORT_DIR"
exec 9>"$LOCK_FILE"
if ! flock -n 9; then
  status="SKIPPED_LOCKED"
  error_message="another GraphHopper refresh is already running"
  write_report
  echo "$error_message"
  exit 0
fi

echo "resolving prod DB URL for GraphHopper export"
resolve_prod_db_url

active_slot="$(redis_get "$ACTIVE_SLOT_KEY")"
previous_slot="$(redis_get "$PREVIOUS_SLOT_KEY")"
if ! is_slot "$active_slot"; then
  echo "GraphHopper active slot is not initialized. bootstrap slot=$BOOTSTRAP_SLOT"
  active_slot=""
  candidate_slot="$BOOTSTRAP_SLOT"
else
  if ! ensure_slot_runtime "$active_slot"; then
    warning_message="active slot self-heal failed: $active_slot"
    if is_slot "$previous_slot" && [ "$previous_slot" != "$active_slot" ] && ensure_slot_runtime "$previous_slot"; then
      echo "GraphHopper active slot $active_slot is unhealthy. Failing over active slot to previous $previous_slot before refresh."
      redis_mset \
        "$BLUE_URL_KEY" "$BLUE_URL" \
        "$GREEN_URL_KEY" "$GREEN_URL" \
        "$ACTIVE_SLOT_KEY" "$previous_slot" \
        "$PREVIOUS_SLOT_KEY" "$active_slot" \
        "$ACTIVE_BUILD_ID_KEY" "self-heal-$BUILD_ID"
      active_slot="$previous_slot"
      previous_slot="$(other_slot "$active_slot")"
      warning_message="$warning_message; failed over to previous slot before refresh"
    else
      warning_message="$warning_message; previous slot unavailable, candidate rebuild will continue"
      echo "Active GraphHopper slot is unavailable and previous slot is not healthy. Continuing candidate rebuild." >&2
    fi
  fi
  candidate_slot="$(other_slot "$active_slot")"
fi

if ! is_slot "$candidate_slot"; then
  echo "candidate slot must be blue or green, got: $candidate_slot" >&2
  exit 1
fi

echo "GraphHopper refresh buildId=$BUILD_ID active=${active_slot:-none} candidate=$candidate_slot"

graphhopper_cache_fingerprint="$(bash "$ROOT_DIR/scripts/graphhopper/cache_fingerprint.sh" "$ROOT_DIR")"
candidate_graph_location="$(slot_graph_location "$candidate_slot")"

echo "stopping temporary candidate runtime before cache rebuild"
cleanup_temp_candidate_runtime

echo "building temporary candidate graph-cache at $CANDIDATE_GRAPH_LOCATION"
"${PROD_COMPOSE[@]}" --profile graphhopper-build build graphhopper-build
DB_URL="$PROD_DB_URL" \
"${PROD_COMPOSE[@]}" --profile graphhopper-build run --rm \
  -e DB_URL="$PROD_DB_URL" \
  -e GRAPHHOPPER_GRAPH_LOCATION="$CANDIDATE_GRAPH_LOCATION" \
  -e GRAPHHOPPER_CACHE_FINGERPRINT="$graphhopper_cache_fingerprint" \
  graphhopper-build

echo "starting temporary candidate runtime graphhopper-candidate"
"${PROD_COMPOSE[@]}" --profile graphhopper-candidate up -d --build graphhopper-candidate
wait_for_temp_candidate_health
run_profile_smoke "$CANDIDATE_URL" "temp-candidate"

arm_publish_fallback

if snapshot_target_slot_cache "$candidate_slot"; then
  target_cache_snapshot_available="true"
else
  warning_message="$warning_message; target slot snapshot unavailable before publish"
  echo "target slot snapshot unavailable before publish; temporary candidate fallback will remain if publish fails" >&2
fi

echo "stopping target slot runtime graphhopper-$candidate_slot for final cache publish"
"${PROD_COMPOSE[@]}" --profile graphhopper stop "graphhopper-$candidate_slot" >/dev/null 2>&1 || true
publish_candidate_cache_to_slot "$candidate_slot"

echo "starting target slot runtime graphhopper-$candidate_slot"
"${PROD_COMPOSE[@]}" --profile graphhopper up -d --build "graphhopper-$candidate_slot"
wait_for_candidate_slot_health "$candidate_slot"
run_profile_smoke "http://graphhopper-$candidate_slot:8989" "$candidate_slot"
publish_target_validated="true"

echo "switching active GraphHopper slot to $candidate_slot"
if [ -n "$active_slot" ]; then
  redis_mset \
    "$BLUE_URL_KEY" "$BLUE_URL" \
    "$GREEN_URL_KEY" "$GREEN_URL" \
    "$PREVIOUS_SLOT_KEY" "$active_slot" \
    "$ACTIVE_SLOT_KEY" "$candidate_slot" \
    "$ACTIVE_BUILD_ID_KEY" "$BUILD_ID"
else
  redis_mset \
    "$BLUE_URL_KEY" "$BLUE_URL" \
    "$GREEN_URL_KEY" "$GREEN_URL" \
    "$ACTIVE_SLOT_KEY" "$candidate_slot" \
    "$ACTIVE_BUILD_ID_KEY" "$BUILD_ID"
fi
switched="true"
publish_fallback_armed="false"
verify_active_slot_after_switch

if [ -z "$BACKEND_SMOKE_URL" ] && [ "$BACKEND_SMOKE_REQUIRED" = "true" ]; then
  BACKEND_SMOKE_URL="$(default_backend_smoke_url)"
  echo "GRAPHHOPPER_BACKEND_SMOKE_URL is not set. Using default backend post-switch smoke: $BACKEND_SMOKE_URL"
fi
if [ -z "$BACKEND_SMOKE_URL" ]; then
  echo "GRAPHHOPPER_BACKEND_SMOKE_URL is not set. Skipping backend post-switch smoke."
else
  echo "running backend post-switch smoke: $BACKEND_SMOKE_URL"
  curl -fsS "$BACKEND_SMOKE_URL" >/dev/null
fi

if [ -n "$active_slot" ]; then
  echo "keeping previous slot $active_slot alive for drain window: ${DRAIN_SECONDS}s"
  ensure_slot_runtime "$active_slot" || warning_message="$warning_message; previous slot self-heal failed after switch: $active_slot"
  sleep "$DRAIN_SECONDS"
fi

cleanup_temp_candidate_runtime
status="SUCCESS"
write_report

if [ "${DOCKER_DISK_MAINTENANCE_AFTER_GRAPHHOPPER_REFRESH:-true}" = "true" ] && [ -f "$ROOT_DIR/scripts/maintenance/docker-disk-maintenance.sh" ]; then
  DOCKER_DISK_MAINTENANCE_MODE="${DOCKER_DISK_MAINTENANCE_MODE:-pipeline}" \
    bash "$ROOT_DIR/scripts/maintenance/docker-disk-maintenance.sh" || \
    echo "Docker disk maintenance failed after GraphHopper refresh; refresh already passed." >&2
fi

echo "GraphHopper blue/green refresh completed. active=$candidate_slot report=$REPORT_FILE"
