---
name: write-test
description: Add focused tests for behavior, edge cases, and failure modes that the current suite does not cover well enough.
---

# write-test

## purpose

Strengthen confidence around a behavior, regression, or boundary.

## when to use

- When implementation or review reveals missing coverage
- When a bug fix or refactor needs protection
- When a new plan includes critical edge cases

## inputs

- Target behavior or failure mode
- `.ai/DOCS.md` and the API, FE, ERD, convention, or product docs that define the behavior
- Existing test layout
- Plan or review notes that describe what matters

## procedure

1. Load `.ai/DOCS.md` and the source documents for the target behavior.
2. Define the exact behavior or risk the test must cover from the plan, docs, and actual code contract.
3. For FE tests, confirm the route/screen/state expectation against `FE/docs` and current `FE/app` code. For BE tests, confirm API, ERD, response, and exception expectations against `Docs/API`, `Docs/ERD`, and backend conventions.
4. Prefer the smallest test that still exercises the real contract.
5. Cover happy path, edge case, or regression as required by the task.
6. Record important uncovered gaps, stale-doc assumptions, or contract conflicts in `.ai/LOCAL/PLANS/current-sprint.md` if they remain.

## outputs

- New or improved test coverage
- Explicit note about what is still untested if applicable

## escalation rules

- Escalate if the code is too coupled to test without structural changes.
- Escalate if a test would be low-signal compared with a higher-level verification path.

## handoff rules

- Hand off to `review` or `qa` depending on whether the risk is mostly code-level or flow-level.
