#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"

docker compose --env-file "$ROOT_DIR/.env.prod" \
  -f "$ROOT_DIR/docker-compose.prod.yml" \
  up -d backend ai admin

bash "$ROOT_DIR/scripts/deploy/prod-smoke.sh"
