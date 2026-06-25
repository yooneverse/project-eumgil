# AGENTS.md

## Mission

Use this repository as a disciplined AI harness template for real software delivery. Favor durable project artifacts over chat-only conclusions.

## Hard rules

- `.ai/` is the canonical source of truth.
- `.ai/ADAPTERS/` is the canonical source for generated runtime adapter files.
- `.ai/scripts/` is the canonical source for harness commands.
- Use `.ai/LANES.md` to keep FE and BE work separated. Prefer `/fe-*` for frontend-only work and `/be-*` for backend-only work.
- Do not edit `.ai/.claude/skills/` or `.ai/.agents/skills/` by hand unless you are debugging the sync process.
- Do not commit `.ai/.claude/settings.local.json` or anything under `.ai/LOCAL/`.
- For Codex implementation sessions, treat root `AGENTS.md` and `.ai/.agents/skills/` as the primary enforcement surface after installing the root entrypoints.
- When a skill changes, update `.ai/SKILLS/` first and then run `.ai/scripts/sync-adapters.sh`.
- Keep markdown additive, parse-friendly, and easy to diff.
- Prefer explicit assumptions, failure modes, and acceptance criteria over vague guidance.
- Store reusable team learnings in `.ai/MEMORY/`, `.ai/EVALS/`, or `.ai/DECISIONS/` instead of leaving them implicit in a thread.
- Treat `.ai/LOCAL/PLANS/progress.json` and `.ai/LOCAL/EVALS/metrics.json` as local machine-readable sources for progress and quality state.
- Run the guard scripts before wiring automation that can edit code, execute shell commands, or retry repeatedly.

## Repository usage expectations

- Start new work by reading `.ai/PROJECT.md`, `.ai/ARCHITECTURE.md`, and `.ai/WORKFLOW.md`.
- For lane-specific work, read `.ai/LANES.md` and use the matching `/fe-*` or `/be-*` skill.
- Before choosing project source documents, follow `.ai/DOCS.md` and respect active local stale-document exclusions from `.ai/scripts/docs-context.sh status`.
- If the repository only copied `.ai/`, run `.ai/scripts/install-root-entrypoints.sh` before implementation work so Codex can discover root instructions.
- Use the sprint loop: Think -> Plan -> Build -> Review -> Test -> Ship -> Reflect.
- Planning outputs should be durable enough that build and QA can consume them without re-interpreting the original request.
- If a change alters architecture, document the delta in `.ai/ARCHITECTURE.md` or add an ADR.
- If a failure repeats, capture it in `.ai/MEMORY/` or `.ai/EVALS/failure-patterns.md`.
- If a procedure repeats, consider promoting it into a skill.
- If completion ambiguity repeats, tighten `.ai/EVALS/` or `.ai/WORKFLOW.md`.

## Required checks

- Run `.ai/scripts/sync-adapters.sh` after changing any canonical skill.
- Run `.ai/scripts/verify.sh` before finalizing structural or documentation changes.
- Run `.ai/scripts/update-progress.sh` after changing item statuses in `.ai/LOCAL/PLANS/progress.json` to keep summary counts in sync.
- Run `.ai/scripts/update-metrics.sh` after retry, blocker, or readiness state changes.
- At the start of a Codex implementation session, run `.ai/scripts/codex-preflight.sh` if the host hook did not show it automatically.
- After `plan-eng-review` or `plan`, run `.ai/scripts/check-plan-readiness.sh` on the updated plan artifact and keep refining until it passes or an external blocker is explicitly recorded.
- Before a mutating shell command, run `.ai/scripts/check-dangerous-command.sh "<command>"`.
- Before editing production implementation files, run `.ai/scripts/check-tdd-guard.sh --mode pre <candidate paths>`.
- After a failed attempt that may repeat, run `.ai/scripts/record-retry.sh <signature>`. Before the next repeated attempt, run `.ai/scripts/check-circuit-breaker.sh <signature>`.
- Run `.ai/scripts/smoke.sh` once project-specific smoke commands are customized.
- Run `.ai/scripts/dashboard.sh` when you need a visible summary of progress, risk, and harness health.
- When Codex implementation is ready for Claude review, run `.ai/scripts/codex-review-brief.sh` and hand off that summary with the diff.
- Update relevant runbooks when build, release, rollback, or local setup expectations change.

## Done criteria

A change is not done until all of the following are true:

- The relevant `.ai/` artifacts were updated.
- Adapter skills were regenerated if canonical skills changed.
- The requested stage handoff is documented.
- Required checks passed or an explicit blocker is recorded.
- `.ai/EVALS/done-criteria.md` still matches the current workflow.
- Structured progress and metrics artifacts still reflect reality.

## Skill usage

- Use canonical skills in `.ai/SKILLS/` as the design source.
- Use adapter skills only as host-specific entrypoints.
- Prefer extending an existing skill when the workflow belongs to an existing stage.
- Add a new skill only when the task shape is recurring and meaningfully distinct.
- Dashboard behavior should read canonical artifacts instead of inventing parallel state.

## Skill extension checklist

1. Define the stage and handoff.
2. Define required inputs and durable outputs.
3. Add or update the canonical skill under `.ai/SKILLS/`.
4. Sync adapters.
5. Verify the repository.

## Frontend document enforcement

- For frontend work, treat `FE/docs/2026-04-22_부산이음길_FE_디자인_컨벤션.md` as a required source document, not an optional style reference.
- If FE route structure, current implementation code, mockups, and design convention disagree, separate `current implementation fact` from `target visual contract` instead of silently promoting the code as the answer.
- A frontend task is not done if route flow is correct but the design convention's tokens, CTA hierarchy, tab shell, state surfaces, or component composition rules are still violated.
