# Workflow

## Sprint loop

The default loop is:

Think -> Plan -> Build -> Review -> Test -> Ship -> Reflect

The automation-oriented execution shape is:

request or event -> classification -> doc and skill loading -> planning -> implementation -> evaluation -> risk summary -> scoring -> dashboard update

The harness control shape is:

intent -> orchestrator selects stage -> agent loads skill -> guarded tool use -> deterministic validation -> review agent or reviewer -> QA signal -> shared learning update when needed

## Team contract

This file is the shared team workflow contract, not a personal daily checklist. Team members may skip or compress stages for small tasks, but the handoff expectations stay the same: plan enough to implement safely, review against the plan, validate the change, and record durable lessons when they affect future work.

## Docs-first contract

`.ai/DOCS.md` defines how project documents are selected, weighted, and temporarily excluded. Every stage must load `.ai/DOCS.md`, check the current docs source report or local document exclusions, and then read the relevant PRD, planning, API, ARD, infrastructure, FE, or convention documents before making project-specific claims. If shared `Docs/` and lane-specific documents disagree, follow the source-of-truth order in `.ai/DOCS.md` and record the conflict in the plan instead of silently blending both.

## Lane contract

Use `.ai/LANES.md` to separate FE and BE work inside the monorepo. Prefer `/fe-*` commands for frontend-only work and `/be-*` commands for backend-only work. Use the unprefixed command only when the request is intentionally cross-functional or the lane is not yet known. Lane-specific plans must record `## Work Lane`, source documents, and `## Cross-Lane Handoff`.

## Stage contracts

### Think

- Primary skills: `make`
- Goal: turn vague intent into a sharper problem definition, wedge, user, and non-goals
- Main outputs: updated framing in `.ai/LOCAL/PLANS/current-sprint.md` and relevant backlog or roadmap adjustments
- Required docs: product planning, PRD, 기능명세서, 화면명세서, interview notes from `.ai/DOCS.md`
- Lane split: use `/fe-make` for FE framing and `/be-make` for BE framing
- Handoff: planning stages inherit the clarified problem instead of the original loose request

### Plan

- Primary skills: `plan-ceo-review`, `plan-eng-review`, `plan-design-review`, `plan`
- Goal: challenge scope, architecture, interaction quality, failure modes, trust boundaries, and test strategy before implementation
- Main outputs: reusable plan sections in `.ai/LOCAL/PLANS/current-sprint.md` or a linked implementation plan artifact, explicit execution units, test and validation matrix, risk register, optional ADR drafts, backlog or roadmap deltas
- Required docs: PRD, API, ARD/ERD, PoC, infra, and conventions from `.ai/DOCS.md`
- Lane split: use `/fe-plan` or `/be-plan` unless the work intentionally spans both sides
- BE requirement: backend planning must run the Backend Design Analyzer and Failure Scenario Generator from `.ai/PLANS/implementation-plan-template.md`, including source of truth, duplicate request behavior, retry safety, transaction boundaries, concurrency consistency, bottlenecks, and test strategy
- Handoff: build, review, and QA consume these artifacts directly

### Build

- Primary skills: `start`, `fix-bug`, `refactor-module`, `write-test`, `investigate`
- Goal: execute against an approved plan with clear boundaries and evidence
- Main outputs: code changes, tests, and implementation notes recorded in sprint artifacts when behavior or scope changed
- Required docs: API contracts, ERD, backend conventions, infra docs, or UI specs relevant to the touched files
- Lane split: use `/fe-start`, `/fe-fix-bug`, `/fe-refactor-module`, `/fe-write-test`, `/fe-investigate` for FE; use `/be-start`, `/be-fix-bug`, `/be-refactor-module`, `/be-write-test`, `/be-investigate` for BE
- Pre-implementation gate: before code edits, record predicted changed files, why each file changes, expected side effects, test cases, rollback trigger, implementation order, and self-review focus
- Handoff: review inherits the approved plan, not just the diff

### Review

- Primary skills: `review`, `design-review`, `security-review`
- Goal: inspect correctness, maintainability, product integrity, and risk
- Main outputs: findings, resolved risks, open questions, and review notes linked from `.ai/LOCAL/PLANS/current-sprint.md`
- Required docs: the same source docs used for planning plus changed-code conventions
- Lane split: use `/fe-review`, `/fe-design-review`, `/fe-security-review` for FE surfaces; use `/be-review`, `/be-design-review`, `/be-security-review` for BE/API/admin surfaces
- Handoff: QA and release should consume unresolved risks explicitly

### Test

- Primary skills: `qa`, `qa-only`, `benchmark`
- Goal: verify real user flows, failure cases, and performance expectations
- Main outputs: bug and risk reports, smoke-check references, scorecard updates, regression notes
- Required docs: user flows, acceptance criteria, API contracts, and deployment/runtime assumptions from `.ai/DOCS.md`
- Lane split: use `/fe-qa`, `/fe-qa-only`, `/fe-benchmark` for FE flows; use `/be-qa`, `/be-qa-only`, `/be-benchmark` for API/server flows
- BE requirement: record compile, unit, integration, API response, DB migration, security, performance, and log/metric verification, preferring `./gradlew clean test`, `./gradlew check`, and `./gradlew bootJar` for Spring Boot projects when present
- Metrics requirement: backend benchmark or QA evidence must explain the feature with measurements or explicit blockers for average latency, p95 latency, maximum TPS, error rate, DB query count, external API calls, and cache hit rate
- Handoff: ship consumes readiness status rather than assuming tests passed means production-ready

### Ship

- Primary skills: `ship`, `canary`, `deploy-check`, `document-release`
- Goal: verify readiness gates, release safely, and keep release docs aligned
- Main outputs: release checklist status, deployment verification, rollback readiness, release notes
- Lane split: use `/fe-ship`, `/fe-document-release`, `/fe-canary`, `/fe-deploy-check` for frontend releases; use `/be-ship`, `/be-document-release`, `/be-canary`, `/be-deploy-check` for backend releases
- BE gate: do not mark backend work ready when verification or metric rows are missing without an explicit blocker or not-applicable reason
- Handoff: try consumes what actually happened, not what was intended

### Reflect

- Primary skills: `try`, `learn`, `dashboard`
- Goal: capture what changed in the team or project system so the next sprint is better
- Main outputs: memory updates, evaluation updates, ADR follow-ups, skill improvements, recurring pattern capture
- Lane split: use `/fe-try`, `/fe-learn`, `/fe-dashboard` or `/be-try`, `/be-learn`, `/be-dashboard` for lane-specific retrospectives and status
- Handoff: future work starts with a better repository memory state

## Artifact movement

- Shared plan templates and examples live in `.ai/PLANS/`.
- Docs source map lives in `.ai/DOCS.md`.
- FE/BE lane contract lives in `.ai/LANES.md`.
- Local stale-document exclusions live in `.ai/LOCAL/DOCS/context-exclusions.json` and are managed by `.ai/scripts/docs-context.sh`.
- Personal sprint plans and structured task state live in `.ai/LOCAL/PLANS/`.
- Shared quality gates and recurring failure knowledge live in `.ai/EVALS/`.
- Personal quality and readiness metrics live in `.ai/LOCAL/EVALS/metrics.json`.
- Reusable operational and debugging memory lives in `.ai/MEMORY/`.
- Structural decisions live in `.ai/DECISIONS/`.
- Runbooks describe deterministic setup, release, and rollback behavior under `.ai/RUNBOOKS/`.
- Guard policy lives in `.ai/GUARDS.md`.

## Guard rails

- Production edits should pass through the TDD guard before automation is allowed to treat them as ready.
- Shell execution should pass through the dangerous command guard before automation executes high-risk commands.
- Repeated equivalent failures should pass through the circuit breaker before more retries are attempted.
- Newly added or changed code should pass deterministic validation before `verify` treats the harness as ready.
- Local-only host files should be ignored and untracked, but their mere presence should not block repository verification.

## Learning paths

- One-off issue: leave local unless it is severe.
- Repeated issue: record it in memory.
- Repeated procedure: update or add a skill.
- Repeated completion ambiguity: tighten evals or workflow.
- Architecture-level tradeoff: write an ADR.

## Operating rule

If a stage creates output that another stage will need later, store it in `.ai/` instead of leaving it in transient chat context.

Planning is not done when it only lists gaps. Planning is done when executable tasks, measurable done criteria, validation work, and unresolved external blockers are separated into durable artifacts.
