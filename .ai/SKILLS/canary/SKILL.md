---
name: canary
description: Verify a release in the first moments after deployment by watching the most likely breakpoints and user-critical signals.
---

# canary

## purpose

Catch release issues early before they become a wider incident.

## when to use

- Immediately after deployment
- For risky or user-critical releases

## inputs

- Release context
- `.ai/DOCS.md`, infra docs, release runbook, and source docs for user-critical paths
- Monitoring or log access if available
- Rollback criteria from the runbook

## procedure

1. Load `.ai/DOCS.md`, infra docs, release runbook, and source docs for the released paths.
2. Identify the fastest indicators of release health.
3. Check the highest-risk paths first.
4. Record observed health, anomalies, doc/config mismatches, and rollback triggers in the sprint artifact or incident log.

## outputs

- Canary status
- Early warning notes
- Rollback recommendation if required

## escalation rules

- Escalate immediately if the primary flow or key health metric degrades.
- Escalate if the canary cannot be observed with enough confidence.

## handoff rules

- Hand off to rollback procedures when release health is unacceptable.
- Hand off to `try` if the canary uncovered a process gap.
