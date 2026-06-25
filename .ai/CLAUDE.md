# CLAUDE.md

Use this repository as a structured AI harness, not as a loose prompt sandbox.

## Canonical sources

- Read `.ai/PROJECT.md`, `.ai/ARCHITECTURE.md`, and `.ai/WORKFLOW.md` before major changes.
- Read `.ai/LANES.md` for lane-specific work and prefer `/fe-*` or `/be-*` skills instead of ambiguous unprefixed commands.
- Read `.ai/DOCS.md` before choosing project source documents and respect active local stale-document exclusions from `.ai/scripts/docs-context.sh status`.
- Treat `.ai/` as canonical.
- Treat `.ai/.claude/skills/` and root `.claude/skills` as generated adapter output.
- Treat `.ai/.claude/settings.json` and root `.claude/settings.json` as generated from `.ai/ADAPTERS/claude/settings.json`.
- Use `.ai/LOCAL/PLANS/progress.json` and `.ai/LOCAL/EVALS/metrics.json` when summarizing local status.
- If the repository only copied `.ai/`, run `.ai/scripts/install-root-entrypoints.sh` so Claude guidance is included in root `AGENTS.md`.

## Skills

- Claude-compatible skills live under root `.claude/skills`, which is an ignored shim over `.ai/.claude/skills`.
- Update `.ai/SKILLS/` first, then run `.ai/scripts/sync-adapters.sh`.
- Prefer the stage-appropriate skill instead of improvising a new workflow mid-task.
- Prefer FE/BE-prefixed skills when the request belongs to one side of the monorepo.
- Use the `dashboard` skill or `.ai/scripts/dashboard.sh` for visible status.

## Workflow discipline

- Move work through Think -> Plan -> Build -> Review -> Test -> Ship -> Reflect.
- Write durable artifacts back to `.ai/` when the work changes scope, architecture, risk, release readiness, or learnings.
- If a team-level result should survive the session, store it in `.ai/MEMORY/`, `.ai/EVALS/`, `.ai/PLANS/`, or `.ai/DECISIONS/`; keep personal sprint execution notes under `.ai/LOCAL/`.

## Expected checks

- Run `.ai/scripts/verify.sh` after structural changes.
- Run `.ai/scripts/sync-adapters.sh` after canonical skill changes.
- Run `.ai/scripts/update-progress.sh` after changing item statuses in `.ai/LOCAL/PLANS/progress.json`.
- Run `.ai/scripts/update-metrics.sh` after retry or blocker state changes.
- Use `.ai/scripts/smoke.sh` after project-specific commands are configured.
- Use the guard scripts before enabling runtime hooks for command execution or repeated retries.
