---
name: plan-eng-review
description: Force architecture, data flow, failure modes, trust boundaries, and test strategy into the open before implementation.
---

# plan-eng-review

## purpose

Turn product intent or an existing implementation plan into an execution-ready artifact that build, review, and QA can follow without re-inventing missing details.

## when to use

- After the product wedge and scope are understood
- Before large implementation work
- When a change touches architecture, external systems, state, or security boundaries

## inputs

- Existing project documents such as PRD, ERD, blueprint, and prior implementation notes
- Feature requirements, domain description, expected traffic, data structures, and external dependency assumptions when backend behavior is involved
- `.ai/DOCS.md`
- Relevant `Docs/API/`, `Docs/ERD/`, `Docs/PoC/`, `Docs/인프라/`, `Docs/컨벤션/`, and `Docs/skills/backend/` documents
- `.ai/LOCAL/PLANS/current-sprint.md`
- `.ai/PLANS/implementation-plan-template.md`
- `.ai/ARCHITECTURE.md`
- `.ai/EVALS/failure-patterns.md`
- Relevant ADRs or incidents if they exist

## procedure

1. Load `.ai/DOCS.md`, then read the relevant API, ERD, PoC, infra, backend convention, and planning docs.
2. Read the existing plan and supporting docs, then identify what is still vague, oversized, internally inconsistent, or missing.
3. Map the proposed flow: trigger, data movement, state changes, storage boundaries, external dependencies, and trust boundaries.
4. For backend work, run the Backend Design Analyzer from `.ai/PLANS/implementation-plan-template.md`: capture requirements, domain, expected traffic, data structures, external dependencies, state flow, API candidates, data mutation points, transaction boundaries, failure points, expected bottlenecks, and test strategy.
5. For backend work, answer the required consistency questions explicitly: source of truth, duplicate request behavior, retry safety, transaction boundaries, and concurrent request data integrity.
6. Generate concrete failure scenarios before implementation. Cover duplicate requests, partial cache/DB success, committed DB work followed by response-time crash, expiry or business-time changes during processing, and multi-instance concurrency when relevant.
7. Cross-check API paths, request/response fields, error codes, tables, entities, transaction rules, indexes, idempotency keys, and package rules against `Docs/`.
8. Break the work into small execution units with explicit dependencies, changed surfaces, build steps, review focus, QA path, and measurable done criteria.
9. Convert missing tests and validation into an explicit test and validation matrix instead of leaving them as loose suggestions. Backend plans must include compile, unit, integration, API, migration, security, performance, and log/metric validation or a reason each item is not applicable.
10. Add a Metrics Explainer target table for backend features with expected measurement method for average latency, p95 latency, maximum TPS, error rate, DB query count, external API calls, and cache hit rate.
11. Convert every risk into one of three buckets: mitigated now, execution task, or true open question that requires outside confirmation.
12. Update `.ai/LOCAL/PLANS/current-sprint.md` or a linked plan artifact under `.ai/LOCAL/PLANS/` using the implementation plan template so build, review, and QA can consume it directly.
13. Update `.ai/ARCHITECTURE.md` or draft an ADR when the system shape, source of truth, transaction boundary, or trust boundary changed materially.
14. Run `.ai/scripts/check-plan-readiness.sh` on the updated plan artifact and iterate until it passes or until a repeated blocked failure must be escalated through the circuit breaker path.

## outputs

- Revised implementation plan artifact
- Data flow and failure mode summary
- Backend Design Analyzer section when backend behavior is involved
- Failure Scenario Generator table when backend behavior is involved
- Trust boundary notes
- Test and validation matrix
- Metrics Explainer target table for backend features
- Review and QA handoff sections
- Explicit risk register and open question list

## escalation rules

- Escalate only when the architecture depends on unvalidated external assumptions, when a product or source-of-truth decision is genuinely missing, or when repeated plan rewrites still fail readiness and the circuit breaker opens.
- Do not stop at listing tests, validations, or mitigations when they can be added to the plan artifact immediately.

## handoff rules

- Hand off to `plan-design-review` if UX states still need clarification.
- Hand off to build skills only after the execution units, test matrix, and risk register are explicit enough to execute.
- Hand off to `review` and `qa` with the updated plan artifact as the canonical brief, not with an isolated chat summary.
