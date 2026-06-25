# Implementation Plan Template

Use this when `plan-eng-review` or `plan` needs a build-ready artifact instead of loose notes.

## Source Documents

- PRD:
- ERD:
- Blueprint:
- Existing implementation plan:
- Excluded stale docs:

## Work Lane

- Lane: FE | BE | Cross-functional
- Scope boundary:
- Explicit non-goals:
- Cross-lane dependency summary:

## Problem List

- What is still vague, too large, or internally inconsistent?
- Which gaps can be closed now without waiting for a product decision?
- Which gaps are true external blockers?

## Architecture And Data Flow

- Trigger and entrypoint
- State changes
- Storage reads and writes
- External services and failure boundaries
- Auth and trust boundaries

## Backend Design Analyzer

Use this section for BE or cross-functional work that changes server behavior.

### Inputs

- Feature requirements:
- Domain description:
- Expected traffic:
- Data structures:
- External dependencies:

### Outputs

- State flow:
- API candidates:
- Data mutation points:
- Transaction boundaries:
- Failure points:
- Expected bottlenecks:
- Test strategy:

### Required Questions

- Where is this feature's source of truth?
- What happens when duplicate requests arrive?
- Can a failed operation be retried safely?
- Where are the transaction boundaries?
- Can concurrent requests break data consistency?

## Failure Scenario Generator

List concrete failure scenarios before implementation. Include at least duplicate input, partial persistence, response-time crash, time-window expiry, and multi-instance concurrency when relevant.

| Scenario | Expected behavior | Mitigation or test |
| --- | --- | --- |
| Duplicate request, rapid repeated click, or client retry |  |  |
| Cache succeeds but DB fails, or DB succeeds but cache fails |  |  |
| DB commit succeeds but the server dies before responding |  |  |
| Expiry or business time window changes during processing |  |  |
| Multiple server instances process the same logical request |  |  |

## Execution Units

For each unit record:

1. Name and goal
2. Inputs and dependencies
3. Files or surfaces likely to change
4. Build steps
5. Review focus
6. QA or validation path
7. Done criteria

## Implementation Harness Preflight

Before editing implementation code, record:

1. Files expected to change
2. Why each file changes
3. Expected side effects
4. Cases that must be tested
5. Rollback trigger or rollback path

Implementation order:

1. Predict changed files
2. Analyze impact scope
3. Write implementation order
4. Write test plan
5. Modify code
6. Perform self review

## Cross-Lane Handoff

- FE -> BE requests:
- BE -> FE requests:
- Contract questions:
- Owner or next action:

## Test And Validation Matrix

- Unit or module tests
- Contract tests
- Integration or ETL checks
- Security checks
- QA scenarios
- Benchmark or latency checks
- Release gating checks

## Backend Verification Harness

For BE work, mark each item as pass, fail, blocked, or not applicable and include the command or evidence.

| Check | Status | Command or evidence |
| --- | --- | --- |
| Compile success |  |  |
| Unit tests |  | `./gradlew clean test` |
| Integration tests |  |  |
| API response validation |  |  |
| DB migration validation |  |  |
| Security validation |  |  |
| Performance validation |  |  |
| Log and metric validation |  |  |
| Spring Boot package check |  | `./gradlew bootJar` |
| Full Gradle check |  | `./gradlew check` |

For cross-functional work that includes FE, also record `npm run lint`, `npm run typecheck`, `npm run test`, and `npm run build` when those commands exist.

## Metrics Explainer

Every implemented BE feature should end with a measured or explicitly blocked metrics table. Use `N/A` only when the metric truly does not apply, and use `Blocked: <reason>` when measurement could not be completed.

| Item | Measurement |
| --- | --- |
| Average response time |  |
| p95 response time |  |
| Maximum TPS |  |
| Error rate |  |
| DB query count |  |
| External API call count |  |
| Cache hit rate |  |

## Risk Register

For each risk record:

- Risk statement
- Why it matters
- What can be mitigated now
- What remains open
- Owner or next action

## Review Handoff

- What reviewers should inspect first
- What changed versus the previous plan
- What is intentionally deferred

## QA Handoff

- Primary user flows
- Degraded mode or failure scenarios
- Required fixtures or environments
- Expected pass or fail signals

## Open Questions

- Leave only questions that truly require external confirmation or unavailable evidence.
- Convert everything else into execution units, tests, or risks.
