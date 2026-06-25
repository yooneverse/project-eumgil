# Done Criteria

Work is done only when:

- The stage-appropriate `.ai/` artifacts were updated.
- Planning-stage work includes execution units, measurable done criteria, test and validation matrix, risk register, and explicit open questions.
- Backend planning includes the Backend Design Analyzer, Failure Scenario Generator, Implementation Harness Preflight, Backend Verification Harness, and Metrics Explainer sections.
- Structured progress and metrics artifacts match the latest known state.
- Risks and open questions are explicit.
- Required checks ran or the blocker is recorded.
- Newly added or changed code passed deterministic validation, or an explicit blocker explains why it could not run.
- Backend implementation is not ready unless compile, unit, integration, API, migration, security, performance, and log/metric checks are passed, blocked, or marked not applicable with evidence.
- Backend features include measured or explicitly blocked values for average response time, p95 response time, maximum TPS, error rate, DB query count, external API call count, and cache hit rate.
- Local-only host files are ignored and untracked rather than removed just to satisfy verification.
- Review and QA outcomes were fed forward to the next stage.
- Release readiness is not inferred from passing tests alone.
- Retrospective learnings are captured when the work exposed a recurring pattern.
