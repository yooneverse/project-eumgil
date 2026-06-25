---
name: fe-start
description: Run start for frontend work only. Use the FE lane source of truth, update FE docs, and record BE dependencies as cross-lane handoff instead of mixing scope.
---

# fe-start

## purpose

Run the `start` workflow in the FE lane: implement the approved plan without crossing into the opposite lane.

Base skill intent: Build an approved feature against the recorded plan instead of improvising from the latest prompt.

## when to use

- When the user invokes `/fe-start`.
- When the work belongs to the FE side of the monorepo.
- When documentation, implementation, review, QA, release, or learning should stay scoped to FE ownership.

## inputs

- `.ai/LANES.md`
- `.ai/DOCS.md`
- `.ai/SKILLS/start/SKILL.md` for the base stage workflow
- `FE/docs`, actual `FE/app` code, FE mockups, and only the API documents needed for client contracts
- `.ai/LOCAL/PLANS/current-sprint.md` when a plan or handoff exists

## procedure

1. Read `.ai/LANES.md` and apply the FE lane contract before reading implementation files.
2. Read `.ai/DOCS.md`, then select only the source documents relevant to the FE request.
3. Read `.ai/SKILLS/start/SKILL.md` and apply its procedure inside the FE lane boundary.
4. Treat `FE/app` and `FE/docs` as the primary editable surfaces for this command.
5. Do not edit `BE` implementation files from this command. If the opposite lane must change, record a `## Cross-Lane Handoff` item and recommend `/be-plan or /be-start`.
6. For documentation work, update the FE source documents first and record any stale or conflicting shared docs in the plan.
7. Keep `.ai/LOCAL/PLANS/current-sprint.md` explicit about `## Work Lane`, source documents, checklist items, document conflicts, and cross-lane handoff.
8. When recommending a next command, prefer the same prefix: `/fe-*`.

## outputs

- FE-scoped result for `start`
- Updated FE implementation, tests, documents, review notes, or release notes as appropriate
- `## Work Lane` and `## Cross-Lane Handoff` entries when the sprint plan is touched
- Clear recommendation for the next `/fe-*` command

## escalation rules

- Escalate if the request cannot be completed without changing BE implementation files.
- Escalate if FE source documents conflict with current code and the decision would change product or API behavior.
- Escalate if the user intended a cross-functional change; switch to the unprefixed base skill or split into FE and BE handoffs.

## handoff rules

- Hand off to `/be-*` only through an explicit `## Cross-Lane Handoff` entry.
- Hand off to the unprefixed `start` skill when the work is intentionally cross-functional.
- Keep lane-specific follow-ups on `/fe-*` commands.
