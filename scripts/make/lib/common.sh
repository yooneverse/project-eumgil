#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
cd "$ROOT_DIR"

LOCAL_COMPOSE=(docker compose --env-file .env.local -f docker-compose.local.yml)
DEV_COMPOSE=(docker compose --env-file .env.dev -f docker-compose.dev.yml)
