#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
source "$ROOT_DIR/scripts/make/lib/be-dev.sh"

ensure_env_file
ensure_docker_daemon
resolve_dev_graphhopper_db_url

app_port="$(env_value GRAPHHOPPER_PORT)"
admin_port="$(env_value GRAPHHOPPER_ADMIN_PORT)"
app_port="${app_port:-8998}"
admin_port="${admin_port:-8999}"

DB_URL="$DEV_GRAPHHOPPER_DB_URL" \
"${DEV_COMPOSE[@]}" up -d --build graphhopper

echo "waiting for dev GraphHopper healthcheck: http://127.0.0.1:$admin_port/healthcheck"
ready=0
for _ in $(seq 1 180); do
  if curl -fsS "http://127.0.0.1:$admin_port/healthcheck" >/dev/null 2>&1; then
    ready=1
    break
  fi
  sleep 1
done

if [ "$ready" -ne 1 ]; then
  echo "dev GraphHopper runtime did not become healthy on admin port $admin_port" >&2
  exit 1
fi

run_args=(--profile graphhopper-build run --rm --build --entrypoint python3)
if [ "$DEV_GRAPHHOPPER_BUILD_NO_DEPS" = "true" ]; then
  run_args+=(--no-deps)
fi

DB_URL="$DEV_GRAPHHOPPER_DB_URL" \
GRAPHHOPPER_PROFILE_SMOKE_BASE_URL="http://host.docker.internal:$app_port" \
MSYS_NO_PATHCONV=1 \
"${DEV_COMPOSE[@]}" "${run_args[@]}" \
  graphhopper-build \
  /usr/local/bin/smoke-graphhopper-profiles.py \
    --report-json /graphhopper/import/road-network-profile-smoke-report.json
