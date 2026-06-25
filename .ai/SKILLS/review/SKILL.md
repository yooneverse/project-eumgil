---
name: review
description: Review changes for correctness, maintainability, edge cases, missing tests, and production risk before the code moves forward.
---

# review

## purpose

Inspect the branch like a strong human reviewer would, with emphasis on what happy-path implementation tends to miss.

## when to use

- After implementation or bug fixing
- Before QA or shipping
- When a risky change needs a hard correctness pass

## inputs

- Current diff or changed files
- Optional runtime-generated review handoff such as `.ai/scripts/codex-review-brief.sh`
- `.ai/DOCS.md`
- `.ai/LOCAL/PLANS/current-sprint.md`
- Any linked implementation plan artifact under `.ai/LOCAL/PLANS/`
- Relevant `Docs/` source documents for the touched product, API, data, infra, or convention area
- Relevant tests, architecture notes, and incidents

## procedure

1. Load `.ai/DOCS.md` and read the relevant source documents for the touched area.
2. For FE changes, compare the diff against FE route map, FE code convention, design convention, component guide, accessibility labels, and current route/screen code.
3. Review the intended scope and compare it with the actual change.
4. Check whether the implementation matches PRD, API, ERD, FE/BE convention, and infra contracts.
5. Look for correctness issues, maintainability problems, missing tests, stale-document assumptions, and hidden risks.
6. Cross-check any runtime handoff summary against the actual diff instead of trusting it blindly.
7. Record findings and open questions in the sprint artifact.
8. Make sure unresolved items are visible to QA and release stages.

## outputs

- Review findings
- Risk summary
- Missing-test or maintainability notes

## escalation rules

- Escalate if the implementation diverged materially from the approved plan.
- Escalate if unresolved correctness risks remain high.

## handoff rules

- Hand off to `qa` when the change affects real flows.
- Hand off to `ship` only after high-risk findings are resolved or explicitly accepted.
