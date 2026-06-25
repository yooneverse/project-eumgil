# Failure Patterns

## 2026-05-10 Route API QA - dev deployment drift

- Pattern: Official dev API smoke can fail even when current branch logic works locally if S1 dev backend is not running the branch under test.
- Signal: Entire endpoint family returns `404 C4040` while local current-branch process exposes the endpoint and returns domain statuses.
- Prevention: Before 32-case API QA, call one authenticated endpoint from the branch-specific feature surface and record backend version/deployed branch if available.

## 2026-05-13 Blue/Green QA - any-slot smoke hides active-slot failure

- Pattern: A blue/green deployment can pass health smoke when any runtime is healthy while the Redis active slot points to an unhealthy runtime.
- Signal: Smoke checks a list of blue/green/legacy health URLs but does not read `graphhopper:active-slot` and validate that exact endpoint.
- Prevention: Smoke scripts for active/standby systems must validate the routing source of truth first, then healthcheck and route-smoke the selected active endpoint.
- Applied mitigation: `scripts/deploy/prod-smoke.sh` now reads Redis `graphhopper:active-slot` and checks the exact blue/green admin health endpoint by default.

## 2026-05-13 Blue/Green QA - fallback disappears during inactive rebuild

- Pattern: Rebuilding the inactive blue/green slot can remove the previous fallback runtime for the duration of import, leaving only the active runtime serving.
- Signal: The refresh flow stops `graphhopper-$candidate_slot` and rewrites its graph-cache volume before switch, while backend fallback still names that slot as previous.
- Prevention: Keep previous serving until a temp candidate cache/runtime passes smoke, or explicitly document and alert on the single-engine availability window.
- Applied mitigation: prod refresh now imports and smokes a temporary `graphhopper-candidate` runtime first, then temporarily points previous-slot URL to candidate during the final blue/green cache publish.

## Purpose

Capture repeatable ways AI-assisted delivery can fail so planning, review, QA, and release skills can counter them.

## Starter patterns

- Shipping the first requested feature instead of the real product wedge
- Missing trust boundaries, data ownership, or failure modes in the plan
- Passing tests while real user flows still fail
- Generic UI that technically works but weakens product clarity
- Skipping rollback preparation because the change looked small
- Editing production code without updating relevant tests
- Brute-force retrying the same failing strategy instead of changing approach
- Hiding progress or risk state inside transient chat output instead of canonical artifacts
