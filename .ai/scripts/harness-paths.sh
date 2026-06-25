#!/usr/bin/env bash

if [[ -z "${ROOT_DIR:-}" ]]; then
  ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
fi

AI_DIR="${AI_DIR:-$ROOT_DIR/.ai}"
AI_LOCAL_DIR="${AI_LOCAL_DIR:-$AI_DIR/LOCAL}"
AI_PLAN_DIR="${AI_PLAN_DIR:-$AI_LOCAL_DIR/PLANS}"
AI_EVAL_DIR="${AI_EVAL_DIR:-$AI_LOCAL_DIR/EVALS}"
AI_CLAUDE_HOME="${AI_CLAUDE_HOME:-$AI_DIR/.claude}"
AI_AGENTS_HOME="${AI_AGENTS_HOME:-$AI_DIR/.agents}"
AI_CODEX_HOME="${AI_CODEX_HOME:-$AI_DIR/.codex}"
AI_CANONICAL_PLAN_DIR="${AI_CANONICAL_PLAN_DIR:-$AI_DIR/PLANS}"
AI_CANONICAL_EVAL_DIR="${AI_CANONICAL_EVAL_DIR:-$AI_DIR/EVALS}"

harness_ensure_local_state() {
  mkdir -p "$AI_PLAN_DIR" "$AI_EVAL_DIR"

  local file
  for file in roadmap.md backlog.md current-sprint.md implementation-plan-template.md progress.json; do
    if [[ ! -f "$AI_PLAN_DIR/$file" && -f "$AI_CANONICAL_PLAN_DIR/$file" ]]; then
      cp "$AI_CANONICAL_PLAN_DIR/$file" "$AI_PLAN_DIR/$file"
    fi
  done

  for file in metrics.json retry-log.jsonl; do
    if [[ ! -f "$AI_EVAL_DIR/$file" && -f "$AI_CANONICAL_EVAL_DIR/$file" ]]; then
      cp "$AI_CANONICAL_EVAL_DIR/$file" "$AI_EVAL_DIR/$file"
    fi
  done
}
