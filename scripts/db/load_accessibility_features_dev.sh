#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
source "$ROOT_DIR/scripts/make/lib/be-dev.sh"

SOURCE_DIR="${ACCESSIBILITY_SOURCE_DIR:-$ROOT_DIR/.ai/LOCAL}"
REPORT_JSON="${ACCESSIBILITY_REPORT_JSON:-/graphhopper/import/accessibility-feature-load-report.json}"
DRY_RUN="${ACCESSIBILITY_FEATURES_DRY_RUN:-false}"

usage() {
  cat <<'EOF'
Usage: scripts/db/load_accessibility_features_dev.sh [--dry-run]

Loads accessibility source CSV files from .ai/LOCAL into dev DB:
  - segment_features replace insert
  - road_segments accessibility columns update
  - unmatched/ambiguous JSON report

Environment overrides:
  ACCESSIBILITY_SOURCE_DIR=/path/to/.ai/LOCAL
  ACCESSIBILITY_REPORT_JSON=/graphhopper/import/accessibility-feature-load-report.json
  ACCESSIBILITY_FEATURES_DRY_RUN=true
  ENV_FILE=/path/to/.env.dev
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --dry-run)
      DRY_RUN=true
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

ensure_env_file
ensure_docker_daemon
resolve_dev_graphhopper_db_url

run_args=(--profile graphhopper-build run --rm --build --entrypoint python3)
if [ "$DEV_GRAPHHOPPER_BUILD_NO_DEPS" = "true" ]; then
  run_args+=(--no-deps)
fi

loader_args=(/usr/local/bin/load-accessibility-features.py --source-dir /road-network --report-json "$REPORT_JSON" --allow-parse-issues)
if [ "$DRY_RUN" = "true" ]; then
  loader_args+=(--dry-run)
fi

echo "loading accessibility CSV into dev DB: source=$SOURCE_DIR dryRun=$DRY_RUN report=$REPORT_JSON"
DB_URL="$DEV_GRAPHHOPPER_DB_URL" \
MSYS_NO_PATHCONV=1 \
"${DEV_COMPOSE[@]}" "${run_args[@]}" \
  -v "$SOURCE_DIR:/road-network:ro" \
  graphhopper-build \
  "${loader_args[@]}"
