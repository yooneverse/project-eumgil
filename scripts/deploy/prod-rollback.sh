#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

DEPLOY_STATE_DIR="${DEPLOY_STATE_DIR:-.deploy-state}"
DEPLOY_GRAPHHOPPER="${DEPLOY_GRAPHHOPPER:-true}"

export DEPLOY_GRAPHHOPPER

require_env_value() {
  local key="$1"
  local raw

  raw="$(grep -E "^${key}=" .env.prod | tail -n1 || true)"
  if [ -z "$raw" ] || [ "${raw#*=}" = "" ]; then
    echo "${key} must be set in .env.prod" >&2
    exit 1
  fi
}

require_env_value JWT_SECRET

if [ ! -f "$DEPLOY_STATE_DIR/previous-app-image" ]; then
  echo "No previous app image tag recorded in $DEPLOY_STATE_DIR/previous-app-image" >&2
  exit 1
fi

export APP_IMAGE_TAG="$(cat "$DEPLOY_STATE_DIR/previous-app-image")"
admin_image="s14p31e102-prod-admin:${APP_IMAGE_TAG}"
smoke_admin="true"

if [ -f "$DEPLOY_STATE_DIR/previous-graphhopper-image" ]; then
  export GRAPHHOPPER_IMAGE_TAG="$(cat "$DEPLOY_STATE_DIR/previous-graphhopper-image")"
fi

docker compose --env-file .env.prod -f docker-compose.prod.yml up -d backend ai
if docker image inspect "$admin_image" >/dev/null 2>&1; then
  docker compose --env-file .env.prod -f docker-compose.prod.yml up -d admin
else
  echo "No previous admin image found for ${APP_IMAGE_TAG}. Stopping admin during rollback."
  docker compose --env-file .env.prod -f docker-compose.prod.yml stop admin >/dev/null 2>&1 || true
  smoke_admin="false"
fi

if [ "$DEPLOY_GRAPHHOPPER" = "true" ]; then
  docker compose --env-file .env.prod -f docker-compose.prod.yml --profile graphhopper up -d --no-recreate graphhopper-blue graphhopper-green
fi

SMOKE_ADMIN="$smoke_admin" bash "$ROOT_DIR/scripts/deploy/prod-smoke.sh"

cp "$DEPLOY_STATE_DIR/previous-app-image" "$DEPLOY_STATE_DIR/current-app-image"
if [ -f "$DEPLOY_STATE_DIR/previous-graphhopper-image" ]; then
  cp "$DEPLOY_STATE_DIR/previous-graphhopper-image" "$DEPLOY_STATE_DIR/current-graphhopper-image"
fi
