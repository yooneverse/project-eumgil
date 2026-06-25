---
name: qa
description: Test real user flows, document what breaks, and produce a bug and risk report that reflects actual product usage.
---

# qa

## purpose

Validate that the implemented change works in the way a user experiences it, not only in isolated tests.

## when to use

- After review for any user-impacting change
- Before shipping
- When the team needs a realistic flow-level confidence check

## inputs

- Current change and plan context
- `.ai/DOCS.md`
- Relevant PRD, feature spec, screen spec, API contract, ERD, and infra docs selected from `.ai/DOCS.md`
- Any test and validation matrix produced by planning
- `.ai/EVALS/smoke-checklist.md`
- Review findings and open risks

## procedure

1. Load `.ai/DOCS.md` and read the user flow, acceptance, API, data, FE, and runtime assumptions relevant to the change.
2. For FE changes, validate against FE screen inventory, route map, design convention, accessibility labels, and available mockups.
3. Start from the plan artifact's test and validation matrix instead of inventing QA scope from scratch.
4. For BE changes, execute or explicitly block each Backend Verification Harness item from the plan: compile, unit tests, integration tests, API response validation, DB migration validation, security validation, performance validation, and log/metric validation.
5. For Spring Boot backends, prefer `./gradlew clean test`, `./gradlew check`, and `./gradlew bootJar` when those commands exist. For cross-functional work that includes FE, also run or explicitly block `npm run lint`, `npm run typecheck`, `npm run test`, and `npm run build` when those commands exist.
6. Exercise the planned failure scenarios, especially duplicate requests, partial persistence, response-time crash assumptions, expiry during processing, and multi-instance concurrency where credible locally or in the target environment.
7. Execute the flows and note failures, confusing states, API contract mismatches, data issues, stale-document assumptions, and hidden operational risks.
8. Produce a bug and risk report in `.ai/LOCAL/PLANS/current-sprint.md`.
9. Update `.ai/EVALS/scorecard.md` if the test outcome changes release readiness.
10. Feed repeatable gaps into `.ai/EVALS/failure-patterns.md` or memory files.

## outputs

- Flow-based QA report
- Bug list
- Risk list
- Updated readiness notes
- Backend verification status table when backend behavior changed
- Failure scenario execution notes when backend behavior changed

## escalation rules

- Escalate if the release depends on flows that cannot be tested credibly.
- Escalate if high-severity bugs remain unresolved.

## handoff rules

- Hand off to `ship` only after critical findings are addressed or explicitly deferred.
- Hand off to `learn` or `try` if QA exposed a recurring failure pattern.
