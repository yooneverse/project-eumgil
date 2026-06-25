---
name: plan-ceo-review
description: Challenge scope, sharpen the product wedge, and decide whether the team is building too little, too much, or the wrong thing.
---

# plan-ceo-review

## purpose

Pressure-test the plan from a product and scope perspective before code starts.

## when to use

- After `make`
- When a feature is likely under-scoped, over-scoped, or strategically weak
- When a team needs an opinionated product tradeoff before implementation

## inputs

- Current sprint framing from `.ai/LOCAL/PLANS/current-sprint.md`
- `.ai/DOCS.md` and relevant PRD, planning, feature, screen, interview, and FE docs
- Relevant roadmap and backlog context
- `.ai/PROJECT.md`

## procedure

1. Load `.ai/DOCS.md` and the product source documents for the requested scope.
2. Read the current framing and identify the proposed wedge.
3. Ask whether the wedge is strong enough to matter for the target user.
4. Challenge scope with three lenses: stronger wedge, simpler first version, and dangerous distractions.
5. Record source-document conflicts, stale assumptions, and the recommended scope position in `.ai/LOCAL/PLANS/current-sprint.md`.
6. Push deferred but relevant work into `.ai/PLANS/backlog.md` or `.ai/PLANS/roadmap.md`.

## outputs

- Scope recommendation
- Product wedge critique
- Explicit non-goals and deferred opportunities
- Updated sprint plan with product rationale

## escalation rules

- Escalate if the team cannot agree on the primary user or success metric.
- Escalate if the plan depends on a business decision outside the repository.

## handoff rules

- Hand off to `plan-eng-review` once scope is acceptable.
- Hand off back to `make` if the problem definition itself is still weak.
