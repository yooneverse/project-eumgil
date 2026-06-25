#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

DEPLOY_STATE_DIR="${DEPLOY_STATE_DIR:-.deploy-state}"
DEPLOY_GRAPHHOPPER="${DEPLOY_GRAPHHOPPER:-true}"
BUILD_GRAPHHOPPER="${BUILD_GRAPHHOPPER:-false}"
APP_IMAGE_TAG="${APP_IMAGE_TAG:-$(git rev-parse --short=12 HEAD 2>/dev/null || date +%Y%m%d%H%M%S)}"
GRAPHHOPPER_IMAGE_TAG="${GRAPHHOPPER_IMAGE_TAG:-$APP_IMAGE_TAG}"

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
require_env_value CORS_ALLOWED_ORIGINS
require_env_value DB_URL
require_env_value DB_USERNAME
require_env_value DB_PASSWORD
require_env_value REDIS_HOST
require_env_value S3_BUCKET
require_env_value S3_ACCESS_KEY
require_env_value S3_SECRET_KEY
require_env_value GMS_KEY
require_env_value KAKAO_REST_API_KEY
require_env_value ODSAY_API_KEY
require_env_value BUSAN_BIMS_SERVICE_KEY_DECODING
require_env_value VITE_BACKEND_API_URL
require_env_value VITE_ADMIN_KAKAO_JAVASCRIPT_KEY
require_env_value VITE_ADMIN_NAVER_CLIENT_ID
require_env_value VITE_ADMIN_GOOGLE_CLIENT_ID
require_env_value VITE_KAKAO_MAP_KEY

mkdir -p "$DEPLOY_STATE_DIR"
if [ -f "$DEPLOY_STATE_DIR/current-app-image" ]; then
  cp "$DEPLOY_STATE_DIR/current-app-image" "$DEPLOY_STATE_DIR/previous-app-image"
fi
if { [ "$BUILD_GRAPHHOPPER" = "true" ] || [ "$DEPLOY_GRAPHHOPPER" = "true" ]; } && [ -f "$DEPLOY_STATE_DIR/current-graphhopper-image" ]; then
  cp "$DEPLOY_STATE_DIR/current-graphhopper-image" "$DEPLOY_STATE_DIR/previous-graphhopper-image"
fi

export APP_IMAGE_TAG GRAPHHOPPER_IMAGE_TAG

docker compose --env-file .env.prod -f docker-compose.prod.yml config --quiet

docker compose --env-file .env.prod -f docker-compose.prod.yml build backend ai admin
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d backend ai admin

if [ "$DEPLOY_GRAPHHOPPER" = "true" ]; then
  docker compose --env-file .env.prod -f docker-compose.prod.yml --profile graphhopper build graphhopper-blue graphhopper-green
fi

if [ "$BUILD_GRAPHHOPPER" = "true" ]; then
  GRAPHHOPPER_REFRESH_BUILD_ID="${GRAPHHOPPER_REFRESH_BUILD_ID:-deploy-$APP_IMAGE_TAG}" \
    bash "$ROOT_DIR/scripts/graphhopper/prod-bluegreen-refresh.sh"
elif [ "$DEPLOY_GRAPHHOPPER" = "true" ]; then
  docker compose --env-file .env.prod -f docker-compose.prod.yml --profile graphhopper up -d --no-recreate graphhopper-blue graphhopper-green
fi

bash "$ROOT_DIR/scripts/deploy/prod-smoke.sh"

echo "$APP_IMAGE_TAG" > "$DEPLOY_STATE_DIR/current-app-image"
if [ "$BUILD_GRAPHHOPPER" = "true" ] || [ "$DEPLOY_GRAPHHOPPER" = "true" ]; then
  echo "$GRAPHHOPPER_IMAGE_TAG" > "$DEPLOY_STATE_DIR/current-graphhopper-image"
fi

if [ "${DOCKER_DISK_MAINTENANCE_AFTER_DEPLOY:-true}" = "true" ] && [ -f "$ROOT_DIR/scripts/maintenance/docker-disk-maintenance.sh" ]; then
  DOCKER_DISK_MAINTENANCE_MODE="${DOCKER_DISK_MAINTENANCE_MODE:-pipeline}" \
    bash "$ROOT_DIR/scripts/maintenance/docker-disk-maintenance.sh" || \
    echo "Docker disk maintenance failed after deploy; continuing because deploy already passed." >&2
fi
