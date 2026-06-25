---
name: start
description: Build an approved feature against the recorded plan instead of improvising from the latest prompt.
---

# start

## purpose

Execute planned feature work while keeping implementation tied to durable artifacts.

## when to use

- After the relevant planning reviews are complete
- When the work is a net-new feature or capability

## inputs

- Approved plan in `.ai/LOCAL/PLANS/current-sprint.md`
- `.ai/DOCS.md`
- `.ai/ARCHITECTURE.md`
- Relevant `Docs/API/`, `Docs/ERD/`, `Docs/skills/backend/`, `Docs/컨벤션/`, or UI/infra docs selected from `.ai/DOCS.md`
- Relevant tests and runbooks

## procedure

1. Load `.ai/DOCS.md` and read the source docs referenced by the approved plan.
2. Restate the approved feature scope, non-goals, source documents, and document freshness/conflict decisions.
3. For FE work, inspect the relevant `FE/docs` files and current `FE/app` route/screen/contract/ViewModel code before editing.
4. For BE work, inspect the relevant `Docs/API`, `Docs/ERD`, `Docs/skills/backend`, and current `BE` code before editing.
5. Before editing code, write the Implementation Harness Preflight into the sprint artifact or implementation notes: predicted changed files, reason for each file, expected side effects, cases to test, and rollback trigger or path.
6. Follow the implementation order strictly: predict changed files, analyze impact scope, write implementation order, write test plan, modify code, then self-review.
7. For BE work, confirm that the plan already contains source-of-truth, idempotency or duplicate request handling, retry safety, transaction boundaries, concurrency consistency, failure scenarios, and metrics targets. If any are missing, update the plan before code changes.
8. Before mutating shell state, run `.ai/scripts/check-dangerous-command.sh "<command>"`. Before editing implementation files, run `.ai/scripts/check-tdd-guard.sh --mode pre <candidate paths>`.
9. Implement the smallest coherent slice that satisfies the plan and `Docs/` contracts.
10. Add or update tests as the feature is built.
11. Self-review the diff against the approved plan, failure scenarios, transaction boundaries, and expected side effects before handing off.
12. If the same implementation attempt fails repeatedly, run `.ai/scripts/record-retry.sh <signature>` and `.ai/scripts/check-circuit-breaker.sh <signature>` before retrying again.
13. Record any material plan deviation or document conflict in `.ai/LOCAL/PLANS/current-sprint.md`.
14. Update architecture or runbooks if the change alters system behavior.

## outputs

- Feature implementation
- Tests for intended behavior
- Updated sprint artifact if the build revealed meaningful changes
- Implementation Harness Preflight notes for changed files, side effects, tests, and rollback
- Self-review notes tied to the plan, failure scenarios, and validation expectations

## escalation rules

- Escalate if implementation requires changing the approved wedge, trust boundary, or release plan.
- Escalate if missing infrastructure or unclear ownership blocks progress.

## handoff rules

- Hand off to `review` and then `qa` once the implementation is coherent.
