#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "$ROOT_DIR/.ai/scripts/harness-paths.sh"
harness_ensure_local_state
TARGET="${1:-$AI_PLAN_DIR/current-sprint.md}"

if [[ ! -f "$TARGET" ]]; then
  echo "plan-readiness: missing target file: $TARGET" >&2
  exit 1
fi

required_sections=(
  "## Source Documents"
  "## Work Lane"
  "## Problem List"
  "## Architecture And Data Flow"
  "## Execution Units"
  "## Cross-Lane Handoff"
  "## Test And Validation Matrix"
  "## Risk Register"
  "## Review Handoff"
  "## QA Handoff"
  "## Open Questions"
)

backend_required_sections=(
  "## Backend Design Analyzer"
  "## Failure Scenario Generator"
  "## Implementation Harness Preflight"
  "## Backend Verification Harness"
  "## Metrics Explainer"
)

missing=0
for section in "${required_sections[@]}"; do
  if ! grep -q "^${section}$" "$TARGET"; then
    echo "plan-readiness: missing section '$section' in $TARGET" >&2
    missing=1
  fi
done

if grep -Eq '^- Lane:[[:space:]]*BE([[:space:]]*$|[[:space:]])' "$TARGET"; then
  for section in "${backend_required_sections[@]}"; do
    if ! grep -q "^${section}$" "$TARGET"; then
      echo "plan-readiness: missing backend section '$section' in $TARGET" >&2
      missing=1
    fi
  done
fi

if [[ "$missing" -ne 0 ]]; then
  exit 2
fi

echo "plan-readiness: ok"
