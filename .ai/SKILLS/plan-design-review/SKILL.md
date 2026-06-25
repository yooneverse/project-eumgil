---
name: plan-design-review
description: Force interaction states, information hierarchy, and anti-generic UI thinking into the plan before implementation begins.
---

# plan-design-review

## purpose

Prevent product plans from collapsing into generic UI or missing important user states.

## when to use

- For any user-facing flow
- When interaction states, copy, or layout hierarchy are still vague
- Before implementing a new UI or meaningful UX change

## inputs

- `.ai/LOCAL/PLANS/current-sprint.md`
- `.ai/PROJECT.md`
- `.ai/DOCS.md`
- Relevant screen specs, PRD, feature spec, and interview documents selected from `.ai/DOCS.md`
- For FE work: `FE/docs/2026-04-22_부산이음길_FE_화면_인벤토리_및_라우트_맵.md`, FE design convention, FE component guide, FE accessibility label guide, and current `FE/app` screen code
- Any design constraints already captured in memory or ADRs

## procedure

1. Load `.ai/DOCS.md` and read the relevant screen specs, PRD, feature spec, interview notes, and FE docs.
2. For FE work, compare the requested screen with the FE route map and current screen code so the plan does not invent non-existent routes or components.
3. Review the user flow and identify primary, empty, loading, error, permission-denied, offline/fallback, and success states.
4. Check the information hierarchy and whether the interface expresses the accessibility-first product goal.
5. Reject generic UI patterns that do not reinforce the 부산이음길 target users or documented accessibility needs.
6. Add design review notes and required states to `.ai/LOCAL/PLANS/current-sprint.md`.
7. Capture reusable design principles in `.ai/MEMORY/conventions.md` if they should persist.

## outputs

- Required interaction state list
- Information hierarchy critique
- Anti-generic UI guidance
- Updated sprint plan with UI expectations

## escalation rules

- Escalate if critical UX decisions require product or brand approval.
- Escalate if the requested interface conflicts with the stated wedge or target user.

## handoff rules

- Hand off to build skills once the UI states are explicit.
- Hand off to `design-review` after implementation for a live audit.
