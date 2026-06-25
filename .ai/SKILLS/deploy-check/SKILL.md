---
name: deploy-check
description: Confirm deployment prerequisites, environment assumptions, and rollback readiness before pushing a release over the line.
---

# deploy-check

## purpose

Prevent last-mile deployment surprises.

## when to use

- Right before deployment
- When infrastructure, migrations, or environment assumptions are involved

## inputs

- `.ai/RUNBOOKS/release.md`
- `.ai/RUNBOOKS/rollback.md`
- `.ai/DOCS.md`, `Docs/인프라/`, and current deployment configuration
- Current sprint artifact

## procedure

1. Load `.ai/DOCS.md`, infra docs, release/rollback runbooks, and actual deployment configuration.
2. Verify the target environment, dependencies, and access assumptions.
3. Check whether rollout, migration, or flag dependencies are explicit.
4. Confirm rollback steps are current.
5. Record deploy status, infra-doc conflicts, and accepted operational assumptions in `.ai/LOCAL/PLANS/current-sprint.md`.

## outputs

- Deployment readiness note
- List of environment assumptions
- Rollback readiness confirmation or gap

## escalation rules

- Escalate if the deployment path depends on undocumented manual steps.
- Escalate if rollback depends on missing backups, flags, or procedures.

## handoff rules

- Hand off to `ship` or real deployment execution only after readiness is confirmed.
