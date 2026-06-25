---
name: security-review
description: Review changes for trust boundaries, auth, secrets handling, data exposure, and exploit-shaped failure modes before release.
---

# security-review

## purpose

Make security and trust boundary review a normal stage artifact instead of a late surprise.

## when to use

- For changes touching auth, permissions, secrets, user data, or external integrations
- Before shipping risk-sensitive functionality

## inputs

- Current change and plan
- `.ai/ARCHITECTURE.md`
- `.ai/DOCS.md` and relevant API, ERD, infra, external integration, auth, or config docs
- Incident history if relevant

## procedure

1. Load `.ai/DOCS.md` and the source documents that define user data, external integrations, infrastructure, auth, and API contracts.
2. Identify the trust boundaries and sensitive assets involved.
3. Review auth, authorization, secret handling, logging, and data movement risks.
4. Check whether the implementation contradicts API, ERD, infra, or config/external integration docs.
5. Record findings, mitigations, stale-doc assumptions, and accepted risks in `.ai/LOCAL/PLANS/current-sprint.md`.
6. Update runbooks or memory if the review changes operational practice.

## outputs

- Security findings
- Mitigation list
- Updated trust-boundary notes when needed

## escalation rules

- Escalate immediately on confirmed data exposure or privilege escalation risk.
- Escalate if the release depends on a control that does not exist yet.

## handoff rules

- Hand off to `ship` only after critical security findings are resolved or explicitly accepted.
