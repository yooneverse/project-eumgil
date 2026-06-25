#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
ENV_FILE="${ENV_FILE:-"$ROOT_DIR/.env.prod"}"
source "$ROOT_DIR/scripts/make/lib/prod-db-tunnel.sh"

SOURCE_DIR="${ACCESSIBILITY_SOURCE_DIR:-$ROOT_DIR/.ai/LOCAL}"
REPORT_JSON="${ACCESSIBILITY_REPORT_JSON:-/graphhopper/import/accessibility-feature-load-prod-report.json}"
PROD_APPLY="${ACCESSIBILITY_FEATURES_PROD_APPLY:-false}"
PROD_COMPOSE=(docker compose --env-file "$ENV_FILE" -f "$ROOT_DIR/docker-compose.prod.yml")

usage() {
  cat <<'EOF'
Usage: scripts/db/load_accessibility_features_prod.sh [--apply]

Runs accessibility source CSV geom matching against prod DB.
Default mode is dry-run and writes only the report. Use --apply or
ACCESSIBILITY_FEATURES_PROD_APPLY=true after reviewing the dry-run report.

Environment overrides:
  ACCESSIBILITY_SOURCE_DIR=/path/to/.ai/LOCAL
  ACCESSIBILITY_REPORT_JSON=/graphhopper/import/accessibility-feature-load-prod-report.json
  ACCESSIBILITY_FEATURES_PROD_APPLY=true
  PROD_DB_SSH_KEY=/path/to/key.pem
  ENV_FILE=/path/to/.env.prod
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --apply)
      PROD_APPLY=true
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
  shift
done

if [ ! -d "$SOURCE_DIR" ]; then
  echo "Missing accessibility source dir: $SOURCE_DIR" >&2
  exit 1
fi

resolve_prod_db_url
"${PROD_COMPOSE[@]}" --profile graphhopper-build build graphhopper-build

loader_args=(/usr/local/bin/load-accessibility-features.py --source-dir /road-network --report-json "$REPORT_JSON" --allow-parse-issues)
if [ "$PROD_APPLY" != "true" ]; then
  loader_args+=(--dry-run)
fi

echo "loading accessibility CSV into prod DB: source=$SOURCE_DIR apply=$PROD_APPLY report=$REPORT_JSON"
DB_URL="$PROD_DB_URL" \
MSYS_NO_PATHCONV=1 \
"${PROD_COMPOSE[@]}" --profile graphhopper-build run --rm --no-deps --build --entrypoint python3 \
  -v "$SOURCE_DIR:/road-network:ro" \
  graphhopper-build \
  "${loader_args[@]}"
