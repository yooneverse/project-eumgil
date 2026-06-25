---
name: try
description: Close the sprint loop by summarizing what worked, what broke, what slowed the team down, and what should change in the harness.
---

# try

## purpose

Convert a completed sprint or release into actionable process improvements.

## when to use

- At the end of a sprint, milestone, or release
- After a meaningful failure or repeated friction pattern

## inputs

- Sprint artifact
- Review, QA, release, and incident outcomes
- Scorecard and failure-pattern notes
- Source-document conflicts, stale-doc assumptions, and docs updates from the sprint

## procedure

1. Summarize the intended work versus what actually shipped.
2. Identify the best decisions, biggest misses, recurring friction, and source-of-truth problems.
3. Propose concrete updates to memory, evals, runbooks, ADRs, docs, or skills.
4. Record the 회고 summary in `.ai/LOCAL/PLANS/current-sprint.md` or archive it into the relevant durable files.

## outputs

- Sprint 회고
- Process improvements
- Candidate updates for memory, evals, skills, or ADRs

## escalation rules

- Escalate if the 회고 reveals unresolved systemic ownership or reliability issues.
- Escalate if repeated incidents are not being converted into repository policy.

## handoff rules

- Hand off to `learn` to capture the recurring patterns durably.
