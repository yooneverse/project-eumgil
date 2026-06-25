---
name: document-release
description: Update release-facing docs so the repository tells the truth about what just shipped and how to operate it.
---

# document-release

## purpose

Keep documentation aligned with shipped behavior and operational expectations.

## when to use

- After a behavior, interface, setup, or operational path changed
- As part of the release stage

## inputs

- Release-ready change summary
- `.ai/DOCS.md` and the source documents touched by the shipped behavior
- Relevant runbooks, README, and sprint artifact

## procedure

1. Load `.ai/DOCS.md` and identify the source documents that should tell the truth about the shipped behavior.
2. Identify which docs became stale because of the change.
3. Update the relevant `Docs/`, `FE/docs`, runbooks, README sections, or memory entries.
4. Record what was updated, what remains stale, and any owner needed in `.ai/LOCAL/PLANS/current-sprint.md`.

## outputs

- Updated release-facing documentation
- Documentation delta summary

## escalation rules

- Escalate if the change is ready to ship but operational docs remain too unclear.
- Escalate if documentation needs product or legal review outside the repository.

## handoff rules

- Hand off to `ship` once docs match the release.
- Hand off to `learn` if the documentation gap reveals a recurring pattern.
