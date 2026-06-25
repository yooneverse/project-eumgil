---
name: plan
description: Run a full planning pass by consolidating problem framing, product scope review, engineering review, and design review into one reusable sprint artifact.
---

# plan

## purpose

Produce a complete plan packet without skipping the Think or Plan stages.

## when to use

- When a new task needs a full reviewed plan
- When the team wants one command-shaped planning entrypoint
- When downstream build and QA need structured artifacts immediately

## inputs

- Raw task request
- `.ai/PROJECT.md`
- `.ai/DOCS.md`
- `.ai/ARCHITECTURE.md`
- `.ai/WORKFLOW.md`
- Relevant PRD, planning, API, ARD/ERD, PoC, infra, and convention docs selected from `.ai/DOCS.md`

## procedure

1. Load `.ai/DOCS.md` and select the source documents required by the requested domain.
2. Run `.ai/scripts/docs-source-report.sh` to identify the newest candidate source documents.
3. Decide the work lane: FE, BE, AI/data, infra, docs, or cross-functional.
4. For FE work, inspect `FE/docs` and relevant `FE/app` route/screen/contract/ViewModel code before using older `Docs/기획` screen specs.
5. Run the intent of `make` using the selected product and planning docs.
6. Run the intent of `plan-ceo-review`.
7. Run the intent of `plan-eng-review` using API, ARD/ERD, PoC, infra, and convention docs where relevant.
8. Run the intent of `plan-design-review` when the work is user-facing.
9. Consolidate the approved result in `.ai/LOCAL/PLANS/current-sprint.md` or a linked plan artifact under `.ai/LOCAL/PLANS/`.
10. Include source document links, document freshness/conflict notes, checklist items, success criteria, validation plan, and open questions.
11. Run `.ai/scripts/check-plan-readiness.sh` and close plan gaps that do not require external confirmation.

## outputs

- Single reviewed sprint plan
- Explicit scope, architecture, risk, and UX expectations
- Ready-to-build artifact with review and QA handoff sections
- Source document list and checklist suitable for `/dashboard`
- Work lane and document freshness/conflict notes

## escalation rules

- Escalate if the request is too ambiguous to survive a combined planning pass.
- Escalate if the plan reveals unresolved product ownership or technical feasibility questions.

## handoff rules

- Hand off to `start`, `fix-bug`, or another build skill with the consolidated plan as the brief.
