---
name: design-review
description: Review delivered UI against the intended wedge, interaction states, hierarchy, and product taste, then record design debt or risk.
---

# design-review

## purpose

Audit the live or implemented interface after build, not just the plan.

## when to use

- After a user-facing change ships or is close to release
- When the team wants a UI quality and product-taste audit

## inputs

- Implemented interface or screenshots
- `plan-design-review` notes in the sprint artifact
- `.ai/DOCS.md`, `FE/docs` route/design/component/accessibility docs, and relevant mockups
- `.ai/MEMORY/conventions.md` if visual principles exist

## procedure

1. Load `.ai/DOCS.md`, the FE screen inventory/route map, design convention, component guide, accessibility label guide, and relevant mockups.
2. Compare the delivered experience with the planned states, route expectations, hierarchy, and current FE code.
3. Look for generic UI, weak emphasis, missing states, accessibility label gaps, and copy friction.
4. Record design debt, quality risks, source-document conflicts, and recommended improvements in `.ai/LOCAL/PLANS/current-sprint.md`.

## outputs

- Design audit notes
- Missing-state list
- Product-taste and hierarchy recommendations

## escalation rules

- Escalate if the shipped UI contradicts the product wedge.
- Escalate if design quality issues are severe enough to block release.

## handoff rules

- Hand off to `start` for fixes or to `ship` if the interface is acceptable.
