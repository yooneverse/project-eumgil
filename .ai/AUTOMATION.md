# Automation

## Purpose

Describe the intended harness automation pipeline without binding the repository to one vendor runtime.

## Pipeline

request or event -> classification -> doc and skill loading -> planning -> implementation -> evaluation -> risk summary -> scoring -> dashboard update

## What is automated now

The following steps run without manual intervention when hooks are wired:

- **Dangerous command guard**: `.ai/scripts/hook-pre-bash.sh` intercepts every Bash tool call via Claude Code `PreToolUse` hook (`.claude/settings.json`, generated from `.ai/.claude/settings.json`) and blocks destructive commands before execution.
- **TDD guard**: `.ai/scripts/hook-pre-edit.sh` intercepts every Edit/Write tool call via Claude Code `PreToolUse` hook and blocks production edits when no related test changes exist. `.ai/scripts/hook-post-edit.sh` remains as a post-edit audit.
- **Codex session preflight**: `.ai/.codex/hooks.json` wires a minimal `SessionStart` command that runs `.ai/scripts/codex-preflight.sh` to surface guard commands, progress state, repeated-failure warnings, and Claude review routing. It is advisory, not a replacement for tool-level blocking.
- **Code validation**: `.ai/scripts/verify.sh` runs `.ai/scripts/check-code-validation.sh` so shell syntax, structured adapter files, conflict markers, and changed code syntax are checked before the repository is considered ready.
- **Adapter sync**: `.ai/scripts/sync-adapters.sh` copies canonical skills and runtime adapter templates into `.ai/.claude/`, `.ai/.agents/`, and `.ai/.codex/`; call this explicitly after canonical skill or adapter-template changes.
- **Progress recompute**: `.ai/scripts/update-progress.sh` recomputes summary counts from items array; call after changing item statuses.
- **Metrics recompute**: `.ai/scripts/update-metrics.sh` recomputes derived retry, blocker, and health signals from canonical state.

## What requires human or agent action

- **Circuit breaker**: `.ai/scripts/check-circuit-breaker.sh` must be called with a failure signature before retrying a known-failing approach. When `.ai/scripts/dashboard.sh` surfaces a hot cluster, invoke the `learn` skill.
- **Retry logging**: Call `.ai/scripts/record-retry.sh` when an approach fails so the circuit breaker can detect accumulation. This also refreshes derived metrics.
- **Scoring**: Call `.ai/scripts/score.sh` to get a readiness snapshot. Update `.ai/LOCAL/EVALS/metrics.json` after real sprint usage.
- **Dashboard**: Call `.ai/scripts/dashboard.sh` to surface progress, blocked work, quality metrics, and retry clusters.
- **Review handoff**: Call `.ai/scripts/codex-review-brief.sh` when implementation happens in Codex and review happens in Claude.

## Static versus automated

- Static canonical assets: `.ai/PROJECT.md`, `.ai/ARCHITECTURE.md`, `.ai/WORKFLOW.md`, `.ai/GUARDS.md`, runbooks, skills
- Structured mutable assets: `.ai/LOCAL/PLANS/progress.json`, `.ai/LOCAL/EVALS/metrics.json`, `.ai/LOCAL/EVALS/retry-log.jsonl`
- Hook-automated enforcement: dangerous command guard, TDD guard (wired in root `.claude/settings.json`, generated from `.ai/.claude/settings.json`)
- Hook-automated reminders: Codex `SessionStart` guard reminder (wired in `.ai/.codex/hooks.json`)
- Verify-time enforcement: structure, adapter sync, local-only file policy, and code validation
- Generated outputs: `.ai/.claude/skills/`, `.ai/.agents/skills/`, `.ai/.claude/settings.json`, ignored root `.claude/`, ignored root `.agents/`, `.ai/.agents/README.md`, `.ai/.codex/config.toml`, `.ai/.codex/hooks.json`, `.ai/.codex/README.md`

## Self-reinforcing loop

The harness improves over time through this path:

1. A failure is logged via `.ai/scripts/record-retry.sh`.
2. Repeated failures accumulate in `retry-log.jsonl` under the same signature.
3. `.ai/scripts/dashboard.sh` detects hot clusters (>=3 occurrences in 24h) and surfaces them.
4. The agent or operator invokes the `learn` skill with the cluster context.
5. `learn` selects the right canonical destination: MEMORY, SKILL, EVAL, or ADR.
6. Future sessions inherit the compressed knowledge from the canonical artifact.

## Rule

Automation should read canonical artifacts and update `.ai/LOCAL/` for personal runtime state. Team-level learnings should be written explicitly into canonical `.ai/` files.
