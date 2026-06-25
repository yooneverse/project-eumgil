---
name: refactor-module
description: Improve module structure, boundaries, or readability while preserving behavior and making architectural intent clearer.
---

# refactor-module

## purpose

Make a code area easier to maintain without changing product behavior.

## when to use

- When a module is too large, repetitive, or hard to reason about
- When architecture drift is slowing feature or bug work

## inputs

- Target module or boundary
- `.ai/ARCHITECTURE.md`
- `.ai/DOCS.md` and relevant FE, BE, API, ERD, infra, or convention docs
- Relevant tests and current failure patterns

## procedure

1. Load `.ai/DOCS.md`, then identify the behavior, contracts, and conventions that must be preserved.
2. State what pain the refactor is addressing.
3. Identify the safe behavioral boundary that must not change.
4. Before mutating shell state, run `.ai/scripts/check-dangerous-command.sh "<command>"`. Before editing implementation files, run `.ai/scripts/check-tdd-guard.sh --mode pre <candidate paths>`.
5. Refactor incrementally with tests guarding expected behavior.
6. If the same refactor path fails repeatedly, run `.ai/scripts/record-retry.sh <signature>` and `.ai/scripts/check-circuit-breaker.sh <signature>` before retrying again.
7. Update architecture notes if the resulting boundaries become clearer or different.
8. Record document conflicts, stale assumptions, or reusable patterns in `.ai/LOCAL/PLANS/current-sprint.md` or `.ai/MEMORY/conventions.md`.

## outputs

- Refactored module
- Preserved or improved tests
- Updated architecture notes when relevant

## escalation rules

- Escalate if the refactor requires behavior changes disguised as cleanup.
- Escalate if test coverage is too weak to preserve confidence.

## handoff rules

- Hand off to `review` for maintainability and regression risk inspection.
