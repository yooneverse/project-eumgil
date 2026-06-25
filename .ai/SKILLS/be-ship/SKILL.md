---
name: be-ship
description: Run ship for backend work only. Use the BE lane source of truth, update BE docs, and record FE dependencies as cross-lane handoff instead of mixing scope.
---

# be-ship

## purpose

Run the `ship` workflow in the BE lane: verify lane-specific release readiness, docs, and accepted risks.

Base skill intent: Verify readiness gates, confirm docs and tests are aligned, and prepare the change to land safely.

## when to use

- When the user invokes `/be-ship`.
- When the work belongs to the BE side of the monorepo.
- When documentation, implementation, review, QA, release, or learning should stay scoped to BE ownership.

## inputs

- `.ai/LANES.md`
- `.ai/DOCS.md`
- `.ai/SKILLS/ship/SKILL.md` for the base stage workflow
- `Docs/API`, `Docs/ERD`, `Docs/skills/backend`, `Docs/컨벤션`, `Docs/인프라`, and actual `BE` code
- `.ai/LOCAL/PLANS/current-sprint.md` when a plan or handoff exists

## procedure

1. Read `.ai/LANES.md` and apply the BE lane contract before reading implementation files.
2. Read `.ai/DOCS.md`, then select only the source documents relevant to the BE request.
3. Read `.ai/SKILLS/ship/SKILL.md` and apply its procedure inside the BE lane boundary.
4. Treat `BE` and `Docs/API`, `Docs/ERD`, `Docs/skills/backend`, or `Docs/인프라` as the primary editable surfaces for this command.
5. Do not edit `FE/app` implementation files from this command. If the opposite lane must change, record a `## Cross-Lane Handoff` item and recommend `/fe-plan or /fe-start`.
6. For documentation work, update the BE source documents first and record any stale or conflicting shared docs in the plan.
7. Keep `.ai/LOCAL/PLANS/current-sprint.md` explicit about `## Work Lane`, source documents, checklist items, document conflicts, and cross-lane handoff.
8. When recommending a next command, prefer the same prefix: `/be-*`.

## outputs

- BE-scoped result for `ship`
- Updated BE implementation, tests, documents, review notes, or release notes as appropriate
- `## Work Lane` and `## Cross-Lane Handoff` entries when the sprint plan is touched
- Clear recommendation for the next `/be-*` command

## escalation rules

- Escalate if the request cannot be completed without changing FE implementation files.
- Escalate if BE source documents conflict with current code and the decision would change product or API behavior.
- Escalate if the user intended a cross-functional change; switch to the unprefixed base skill or split into FE and BE handoffs.

## handoff rules

- Hand off to `/fe-*` only through an explicit `## Cross-Lane Handoff` entry.
- Hand off to the unprefixed `ship` skill when the work is intentionally cross-functional.
- Keep lane-specific follow-ups on `/be-*` commands.
