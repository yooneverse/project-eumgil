#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
source "$ROOT_DIR/scripts/make/lib/be-dev.sh"

graphhopper_admin_port() {
  local port
  port="$(env_value GRAPHHOPPER_ADMIN_PORT)"
  echo "${port:-8990}"
}

graphhopper_cache_ready() {
  "${DEV_COMPOSE[@]}" --profile graphhopper-build run --rm --no-deps --build --entrypoint sh \
    graphhopper-build \
    -c 'test -d "${GRAPHHOPPER_GRAPH_LOCATION:-/graphhopper/data}" && test -n "$(find "${GRAPHHOPPER_GRAPH_LOCATION:-/graphhopper/data}" -mindepth 1 -maxdepth 1 2>/dev/null)"'
}

wait_for_graphhopper_healthcheck() {
  local admin_port="$1"
  local url="http://127.0.0.1:$admin_port/healthcheck"

  echo "waiting for dev GraphHopper healthcheck: $url"
  for _ in $(seq 1 60); do
    if curl -fsS "$url" >/dev/null 2>&1; then
      echo "dev GraphHopper is healthy: $url"
      return 0
    fi
    sleep 3
  done

  echo "dev GraphHopper did not become healthy: $url" >&2
  "${DEV_COMPOSE[@]}" logs --tail=120 graphhopper >&2 || true
  return 1
}

ensure_env_file
ensure_docker_daemon

if graphhopper_cache_ready; then
  echo "dev GraphHopper graph-cache is already present."
else
  echo "dev GraphHopper graph-cache is missing or empty. Running graphhopper-dev-build first."
  "$ROOT_DIR/scripts/make/docker/graphhopper-dev-build.sh"
fi

"${DEV_COMPOSE[@]}" up -d --build graphhopper
wait_for_graphhopper_healthcheck "$(graphhopper_admin_port)"
