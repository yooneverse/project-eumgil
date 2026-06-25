---
name: qa-only
description: Run the same real-flow validation as QA but stop at reporting instead of changing code.
---

# qa-only

## purpose

Provide an independent QA report without mixing testing and implementation in the same pass.

## when to use

- When a pure test report is preferred
- When code changes are blocked or should be separated from validation

## inputs

- Current change and release candidate context
- `.ai/DOCS.md` and the source documents that define the user flow or contract
- `.ai/EVALS/smoke-checklist.md`
- Relevant review notes

## procedure

1. Load `.ai/DOCS.md` and the relevant FE, API, product, ERD, or infra docs.
2. Select the highest-value flows for the release based on the docs, plan, and actual changed code.
3. Execute them and collect evidence.
4. Report bugs, inconsistencies, stale-doc assumptions, and risk areas in `.ai/LOCAL/PLANS/current-sprint.md`.
5. Update score or readiness notes if the report changes release confidence.

## outputs

- QA report without code changes
- Bug list and risk list

## escalation rules

- Escalate if high-severity issues are found and no owner is assigned.
- Escalate if the release cannot be judged without missing credentials, environments, or fixtures.

## handoff rules

- Hand off to `fix-bug`, `start`, or `ship` depending on the report outcome.
