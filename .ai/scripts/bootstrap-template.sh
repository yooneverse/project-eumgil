#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "$ROOT_DIR/.ai/scripts/harness-paths.sh"
PROJECT_NAME="${1:-$(basename "$ROOT_DIR")}"
harness_ensure_local_state

replace_text() {
  local file="$1"
  local search="$2"
  local replace="$3"
  perl -0pi -e "s/\Q${search}\E/${replace}/g" "$file"
}

replace_text "$ROOT_DIR/.ai/PROJECT.md" "Template Project" "$PROJECT_NAME"
replace_text "$AI_PLAN_DIR/current-sprint.md" "Adopt the template into a real repository while preserving the end-to-end loop." "Establish the initial AI harness workflow for ${PROJECT_NAME}."
replace_text "$AI_PLAN_DIR/progress.json" "template-adoption" "${PROJECT_NAME}-adoption"

"$ROOT_DIR/.ai/scripts/install-root-entrypoints.sh"
"$ROOT_DIR/.ai/scripts/sync-adapters.sh"

cat <<EOF2
bootstrap: initialized first-pass project identity for ${PROJECT_NAME}

Initial setup:
- run ./.ai/scripts/install-root-entrypoints.sh --write-readme if you want the harness pointer in the root README.md
- customize project commands in .ai/scripts/smoke.sh
- update local runtime state under .ai/LOCAL/PLANS/ and .ai/LOCAL/EVALS/
- replace TODO(project) command slots in .ai/RUNBOOKS/
- adjust .ai/ADAPTERS/codex/hooks.json if you want repo-local Codex hook behavior, then rerun .ai/scripts/sync-adapters.sh
- use .ai/scripts/check-plan-readiness.sh after refining project plans into execution-ready artifacts
- run .ai/scripts/verify.sh
EOF2
