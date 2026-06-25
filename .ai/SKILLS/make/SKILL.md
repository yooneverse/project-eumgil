---
name: make
description: Turn a vague idea into a sharper problem definition, wedge, target user, and non-goals before planning starts.
---

# make

## purpose

Convert a loose product request into a reusable framing artifact that planning skills can pressure-test.

## when to use

- At the start of a new feature, product bet, workflow, or repo-level initiative
- When the request is still framed as a solution instead of a problem
- When the team needs a clearer wedge before architecture or UI discussion

## inputs

- The raw request, idea, or opportunity
- `.ai/PROJECT.md`
- `.ai/DOCS.md`
- `.ai/WORKFLOW.md`
- Relevant product, PRD, planning, screen-spec, and interview documents selected from `.ai/DOCS.md`
- Current backlog or sprint context from `.ai/LOCAL/PLANS/`

## procedure

1. Load `.ai/DOCS.md` and read the relevant planning, PRD, screen-spec, and interview documents before restating the request.
2. Restate the request in plain language and separate problem, user, and proposed solution.
3. Challenge ambiguity until the target user, pain, trigger moment, and narrowest useful wedge are explicit.
4. Identify what is out of scope for this sprint and tie scope decisions back to the source documents.
5. Update `.ai/LOCAL/PLANS/current-sprint.md` with a durable problem framing section and source document links.
6. Move any deferred work into `.ai/PLANS/backlog.md` if it matters later.

## outputs

- Sharpened problem statement
- Clear user and wedge
- Non-goals
- Updated sprint framing artifact

## escalation rules

- Escalate if the request depends on unclear business goals, owner decisions, or conflicting target users.
- Escalate if the problem cannot be stated without assuming an implementation.

## handoff rules

- Hand off to `plan-ceo-review` when scope or product ambition needs pressure.
- Hand off to `plan-eng-review` once the problem is concrete enough to design a buildable solution.
