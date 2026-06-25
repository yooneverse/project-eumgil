#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "$ROOT_DIR/.ai/scripts/harness-paths.sh"
harness_ensure_local_state

required_files=(
  "AGENTS.md"
  ".gitignore"
  ".ai/README.md"
  ".ai/AGENTS.md"
  ".ai/CLAUDE.md"
  ".ai/PROJECT.md"
  ".ai/DOCS.md"
  ".ai/LANES.md"
  ".ai/ARCHITECTURE.md"
  ".ai/WORKFLOW.md"
  ".ai/ADAPTERS/README.md"
  ".ai/ADAPTERS/claude/README.md"
  ".ai/ADAPTERS/claude/settings.json"
  ".ai/ADAPTERS/agents/README.md"
  ".ai/ADAPTERS/codex/README.md"
  ".ai/ADAPTERS/codex/config.toml"
  ".ai/ADAPTERS/codex/hooks.json"
  ".ai/GUARDS.md"
  ".ai/AUTOMATION.md"
  ".ai/MEMORY/MEMORY.md"
  ".ai/MEMORY/debugging.md"
  ".ai/MEMORY/conventions.md"
  ".ai/MEMORY/incidents.md"
  ".ai/DECISIONS/ADR-template.md"
  ".ai/PLANS/roadmap.md"
  ".ai/PLANS/backlog.md"
  ".ai/PLANS/current-sprint.md"
  ".ai/PLANS/implementation-plan-template.md"
  ".ai/PLANS/progress.json"
  ".ai/EVALS/done-criteria.md"
  ".ai/EVALS/smoke-checklist.md"
  ".ai/EVALS/failure-patterns.md"
  ".ai/EVALS/scorecard.md"
  ".ai/EVALS/metrics.json"
  ".ai/EVALS/retry-log.jsonl"
  ".ai/RUNBOOKS/local-setup.md"
  ".ai/RUNBOOKS/release.md"
  ".ai/RUNBOOKS/rollback.md"
  ".ai/scripts/install-root-entrypoints.sh"
  ".ai/scripts/sync-adapters.sh"
  ".ai/scripts/verify.sh"
  ".ai/scripts/smoke.sh"
  ".ai/scripts/score.sh"
  ".ai/scripts/dashboard.sh"
  ".ai/scripts/check-tdd-guard.sh"
  ".ai/scripts/check-dangerous-command.sh"
  ".ai/scripts/check-circuit-breaker.sh"
  ".ai/scripts/check-code-validation.sh"
  ".ai/scripts/check-plan-readiness.sh"
  ".ai/scripts/docs-context.sh"
  ".ai/scripts/docs-source-report.sh"
  ".ai/scripts/codex-hook-session-start.sh"
  ".ai/scripts/codex-preflight.sh"
  ".ai/scripts/codex-review-brief.sh"
  ".ai/scripts/hook-pre-edit.sh"
  ".ai/scripts/record-retry.sh"
  ".ai/scripts/bootstrap-template.sh"
  ".ai/scripts/update-progress.sh"
  ".ai/scripts/update-metrics.sh"
  ".ai/scripts/pipeline-check.sh"
  ".ai/scripts/hook-pre-bash.sh"
  ".ai/scripts/hook-post-edit.sh"
  ".ai/scripts/harness-paths.sh"
  ".ai/.claude/settings.json"
  ".ai/.claude/README.md"
  ".ai/.agents/README.md"
  ".ai/.codex/config.toml"
  ".ai/.codex/hooks.json"
  ".ai/.codex/README.md"
  ".claude/README.md"
  ".claude/settings.json"
  ".agents/README.md"
)

required_skills=(
  "make"
  "plan-ceo-review"
  "plan-eng-review"
  "plan-design-review"
  "plan"
  "start"
  "fix-bug"
  "refactor-module"
  "write-test"
  "review"
  "investigate"
  "qa"
  "qa-only"
  "design-review"
  "benchmark"
  "security-review"
  "ship"
  "canary"
  "deploy-check"
  "document-release"
  "try"
  "learn"
  "dashboard"
)

lane_required_skills=()
for skill in "${required_skills[@]}"; do
  lane_required_skills+=("fe-$skill" "be-$skill")
done
required_skills+=("${lane_required_skills[@]}")

required_sections=(
  "## purpose"
  "## when to use"
  "## inputs"
  "## procedure"
  "## outputs"
  "## escalation rules"
  "## handoff rules"
)

for file in "${required_files[@]}"; do
  if [[ ! -f "$ROOT_DIR/$file" ]]; then
    echo "missing required file: $file" >&2
    exit 1
  fi
done

required_executables=(
  ".ai/scripts/install-root-entrypoints.sh"
  ".ai/scripts/sync-adapters.sh"
  ".ai/scripts/verify.sh"
  ".ai/scripts/smoke.sh"
  ".ai/scripts/score.sh"
  ".ai/scripts/dashboard.sh"
  ".ai/scripts/check-tdd-guard.sh"
  ".ai/scripts/check-dangerous-command.sh"
  ".ai/scripts/check-circuit-breaker.sh"
  ".ai/scripts/check-code-validation.sh"
  ".ai/scripts/check-plan-readiness.sh"
  ".ai/scripts/docs-context.sh"
  ".ai/scripts/docs-source-report.sh"
  ".ai/scripts/codex-hook-session-start.sh"
  ".ai/scripts/codex-preflight.sh"
  ".ai/scripts/codex-review-brief.sh"
  ".ai/scripts/hook-pre-edit.sh"
  ".ai/scripts/record-retry.sh"
  ".ai/scripts/bootstrap-template.sh"
  ".ai/scripts/update-progress.sh"
  ".ai/scripts/update-metrics.sh"
  ".ai/scripts/pipeline-check.sh"
  ".ai/scripts/hook-pre-bash.sh"
  ".ai/scripts/hook-post-edit.sh"
)

for file in "${required_executables[@]}"; do
  if [[ ! -x "$ROOT_DIR/$file" ]]; then
    echo "required script is not executable: $file" >&2
    exit 1
  fi
done

for skill in "${required_skills[@]}"; do
  canonical_skill="$ROOT_DIR/.ai/SKILLS/$skill/SKILL.md"
  claude_skill="$AI_CLAUDE_HOME/skills/$skill/SKILL.md"
  agents_skill="$AI_AGENTS_HOME/skills/$skill/SKILL.md"
  claude_discovery_skill="$ROOT_DIR/.claude/skills/$skill/SKILL.md"
  codex_discovery_skill="$ROOT_DIR/.agents/skills/$skill/SKILL.md"

  if [[ ! -f "$canonical_skill" ]]; then
    echo "missing canonical skill: .ai/SKILLS/$skill/SKILL.md" >&2
    exit 1
  fi

  if ! grep -q '^name:' "$canonical_skill"; then
    echo "missing skill name metadata: $canonical_skill" >&2
    exit 1
  fi

  if ! grep -q '^description:' "$canonical_skill"; then
    echo "missing skill description metadata: $canonical_skill" >&2
    exit 1
  fi

  for section in "${required_sections[@]}"; do
    if ! grep -q "^${section}$" "$canonical_skill"; then
      echo "missing section '$section' in $canonical_skill" >&2
      exit 1
    fi
  done

  if [[ ! -f "$claude_skill" ]]; then
    echo "missing Claude adapter skill: $claude_skill" >&2
    exit 1
  fi

  if [[ ! -f "$agents_skill" ]]; then
    echo "missing Codex adapter skill: $agents_skill" >&2
    exit 1
  fi

  if [[ ! -f "$claude_discovery_skill" ]]; then
    echo "missing root Claude discovery skill: $claude_discovery_skill" >&2
    echo "run .ai/scripts/sync-adapters.sh to recreate the ignored .claude/skills shim" >&2
    exit 1
  fi

  if [[ ! -f "$codex_discovery_skill" ]]; then
    echo "missing root Codex discovery skill: $codex_discovery_skill" >&2
    echo "run .ai/scripts/sync-adapters.sh to recreate the ignored .agents/skills shim" >&2
    exit 1
  fi

  if ! diff -qr "$ROOT_DIR/.ai/SKILLS/$skill" "$AI_CLAUDE_HOME/skills/$skill" >/dev/null; then
    echo "Claude adapter is out of sync for skill: $skill" >&2
    exit 1
  fi

  if ! diff -qr "$ROOT_DIR/.ai/SKILLS/$skill" "$ROOT_DIR/.claude/skills/$skill" >/dev/null; then
    echo "Root Claude discovery shim is out of sync for skill: $skill" >&2
    exit 1
  fi

  if ! diff -qr "$ROOT_DIR/.ai/SKILLS/$skill" "$AI_AGENTS_HOME/skills/$skill" >/dev/null; then
    echo "Codex adapter is out of sync for skill: $skill" >&2
    exit 1
  fi

  if ! diff -qr "$ROOT_DIR/.ai/SKILLS/$skill" "$ROOT_DIR/.agents/skills/$skill" >/dev/null; then
    echo "Root Codex discovery shim is out of sync for skill: $skill" >&2
    exit 1
  fi
done

"$ROOT_DIR/.ai/scripts/check-code-validation.sh"

if ! python3 - <<'PY' "$AI_PLAN_DIR/progress.json" "$AI_EVAL_DIR/metrics.json" "$ROOT_DIR/.ai/ADAPTERS/claude/settings.json" "$ROOT_DIR/.ai/ADAPTERS/codex/hooks.json" "$AI_CLAUDE_HOME/settings.json" "$ROOT_DIR/.claude/settings.json" "$AI_CODEX_HOME/hooks.json"
import json
import sys

for path in sys.argv[1:]:
    with open(path, "r", encoding="utf-8") as fh:
        json.load(fh)
PY
then
  echo "one or more json artifacts are invalid" >&2
  exit 1
fi

if git -C "$ROOT_DIR" ls-files --error-unmatch .ai/.claude/settings.local.json >/dev/null 2>&1; then
  echo ".ai/.claude/settings.local.json must remain local-only and must not be tracked" >&2
  exit 1
fi

if ! grep -q '^\.ai/LOCAL/$' "$ROOT_DIR/.gitignore"; then
  echo ".gitignore should ignore .ai/LOCAL/" >&2
  exit 1
fi

if ! grep -q '^\.agents/$' "$ROOT_DIR/.gitignore"; then
  echo ".gitignore should ignore .agents/" >&2
  exit 1
fi

if [[ -e "$ROOT_DIR/.agents" ]] && ! git -C "$ROOT_DIR" check-ignore -q .agents/; then
  echo ".agents exists but is not ignored by git" >&2
  exit 1
fi

if ! grep -q '^\.claude/$' "$ROOT_DIR/.gitignore"; then
  echo ".gitignore should ignore .claude/" >&2
  exit 1
fi

if [[ -e "$ROOT_DIR/.claude" ]] && ! git -C "$ROOT_DIR" check-ignore -q .claude/; then
  echo ".claude exists but is not ignored by git" >&2
  exit 1
fi

if ! grep -q '^\.ai/\.claude/settings\.local\.json$' "$ROOT_DIR/.gitignore"; then
  echo ".gitignore should ignore .ai/.claude/settings.local.json" >&2
  exit 1
fi

if [[ -f "$AI_CLAUDE_HOME/settings.local.json" ]] && ! git -C "$ROOT_DIR" check-ignore -q .ai/.claude/settings.local.json; then
  echo ".ai/.claude/settings.local.json exists but is not ignored by git" >&2
  exit 1
fi

if ! diff -q "$ROOT_DIR/.ai/ADAPTERS/claude/settings.json" "$AI_CLAUDE_HOME/settings.json" >/dev/null; then
  echo "Claude settings adapter is out of sync" >&2
  exit 1
fi

if ! diff -q "$ROOT_DIR/.ai/ADAPTERS/claude/settings.json" "$ROOT_DIR/.claude/settings.json" >/dev/null; then
  echo "Root Claude settings shim is out of sync" >&2
  exit 1
fi

if ! diff -q "$ROOT_DIR/.ai/ADAPTERS/codex/config.toml" "$AI_CODEX_HOME/config.toml" >/dev/null; then
  echo "Codex config adapter is out of sync" >&2
  exit 1
fi

if ! diff -q "$ROOT_DIR/.ai/ADAPTERS/codex/hooks.json" "$AI_CODEX_HOME/hooks.json" >/dev/null; then
  echo "Codex hooks adapter is out of sync" >&2
  exit 1
fi

echo "verify: repository structure and adapter sync look good"
