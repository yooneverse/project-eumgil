---
name: dashboard
description: Show a Korean checklist-based dashboard from the local plan file.
---

# dashboard

## purpose

Make local plan progress visible from the checklist in `.ai/LOCAL/PLANS/current-sprint.md`.

## when to use

- When you need an immediate status view of planned work
- Before review, QA, ship, or try
- When deciding what to do next

## inputs

- `.ai/LOCAL/PLANS/current-sprint.md`
- `.ai/LOCAL/EVALS/metrics.json`
- Optional lane: `FE` or `BE`

## procedure

1. Read checklist items from `.ai/LOCAL/PLANS/current-sprint.md`.
2. If the command is `/fe-dashboard`, run `.ai/scripts/dashboard.sh --lane FE`. If the command is `/be-dashboard`, run `.ai/scripts/dashboard.sh --lane BE`. Otherwise run `.ai/scripts/dashboard.sh`.
3. If the plan file or checklist is missing, tell the user to run the matching `/fe-plan`, `/be-plan`, or `/plan`.
4. Summarize completed, waiting, in-progress, and blocked items in Korean.
5. Recommend the next skill command using the same lane prefix when one was provided.

## outputs

- Korean checklist progress dashboard
- Next unchecked tasks
- Recommended next skill command

## escalation rules

- Escalate if the checklist is too vague to act on.
- Escalate if blocked items have no owner or next step.

## handoff rules

- Hand off to the next stage skill based on the top unresolved risk or next planned item.
