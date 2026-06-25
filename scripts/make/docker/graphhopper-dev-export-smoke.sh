#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
source "$ROOT_DIR/scripts/make/lib/be-dev.sh"

ensure_env_file
ensure_docker_daemon
resolve_dev_graphhopper_db_url

run_args=(--profile graphhopper-build run --rm --build --entrypoint python3)
if [ "$DEV_GRAPHHOPPER_BUILD_NO_DEPS" = "true" ]; then
  run_args+=(--no-deps)
fi

DB_URL="$DEV_GRAPHHOPPER_DB_URL" \
MSYS_NO_PATHCONV=1 \
"${DEV_COMPOSE[@]}" "${run_args[@]}" \
  graphhopper-build \
  /usr/local/bin/smoke-postgis-export.py \
    --report-json /graphhopper/import/road-network-export-smoke-report.json
