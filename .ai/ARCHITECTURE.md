# Architecture

## Canonical layers

- `.ai/` is the methodology and project-operations layer.
- `.ai/SKILLS/` is the canonical workflow layer.
- `.ai/ADAPTERS/` is the canonical runtime-adapter template layer.
- `.ai/scripts/` is the canonical deterministic-automation layer.
- `.ai/README.md`, `.ai/AGENTS.md`, and `.ai/CLAUDE.md` are the canonical host-facing documentation layer.
- `.ai/LOCAL/PLANS/progress.json` is the local structured progress layer.
- `.ai/LOCAL/EVALS/metrics.json` plus retry logs are the local measurable state layer.
- `.ai/LOCAL/DOCS/context-exclusions.json` is the local stale-document exclusion layer.
- `.ai/GUARDS.md` and `.ai/AUTOMATION.md` define harness control behavior.
- `.ai/.claude/skills/` is the Claude adapter layer generated from canonical skills.
- `.ai/.agents/skills/` is the Codex adapter layer generated from canonical skills.
- `.ai/.codex/` holds generated repo-local Codex configuration placeholders.
- Root `.claude/skills` and `.claude/settings.json` are ignored Claude discovery/runtime shims that point at `.ai/.claude/`.
- Root `.agents/skills` is an ignored Codex discovery shim that points at `.ai/.agents/skills`.

## Design intent

This template now separates the portable harness core from repo-root entrypoints.

- The portable unit is `.ai/`.
- A repository can adopt the harness by copying only `.ai/`.
- Root `AGENTS.md` is the unified install surface for Codex and Claude, not the design source.
- Generated adapters remain rebuildable views over canonical `.ai/` sources.

## Harness control model

The harness is a control system for AI-assisted delivery, not a prompt collection. It is built from three cooperating parts:

- **Agents**: role-scoped workers with explicit responsibilities, tool access, and permission boundaries.
- **Skills**: reusable operating manuals that describe when to act, what inputs to load, what procedure to follow, and what durable output to leave behind.
- **Orchestrator**: the stage loop, `.ai/scripts/`, and the human or agent operator that route work through planning, implementation, review, QA, release, and learning.

The operating philosophy is: when an agent repeats a mistake, strengthen the system boundary instead of only rewriting the prompt. Examples include blocking dangerous commands in code, denying direct access to sensitive files or systems, forcing plan readiness checks before build, and routing Codex implementation through a separate review handoff.

## Data flow

1. A request or event is classified against the current sprint and workflow.
2. The orchestrator chooses the relevant stage and loads the matching skill.
3. Humans or agents update canonical docs and canonical skills under `.ai/`.
4. Planning and implementation update local narrative plan artifacts plus `.ai/LOCAL/PLANS/progress.json`.
5. Document selection checks `.ai/DOCS.md` plus local stale-document exclusions before source documents are used as contracts.
6. Guards and validation scripts under `.ai/scripts/` check command risk, TDD discipline, plan readiness, code syntax, and adapter sync before work is treated as ready.
7. Evaluation and release update `.ai/LOCAL/EVALS/metrics.json` and related logs.
8. Shared learning updates go into `.ai/MEMORY/`, `.ai/SKILLS/`, `.ai/EVALS/`, or `.ai/DECISIONS/`.
9. `.ai/scripts/sync-adapters.sh` copies canonical skills and runtime adapter templates into `.ai/.claude/`, `.ai/.agents/`, and `.ai/.codex/`.
10. `.ai/scripts/install-root-entrypoints.sh` can install root `AGENTS.md` and an optional `README.md` pointer for hosts that require root discovery.
11. Claude guidance is referenced from root `AGENTS.md` and uses the ignored root `.claude/skills` and `.claude/settings.json` shims, which are generated from `.ai/.claude/`.
12. Codex uses root `AGENTS.md` and the ignored root `.agents/skills` discovery shim, which is generated from `.ai/.agents/skills`.
13. `.ai/scripts/dashboard.sh` turns progress, metrics, and harness state into a visible status summary.

## Trust boundaries

- Canonical policy belongs in `.ai/`.
- Root entrypoint files are installable host-discovery shims over canonical docs.
- Generated adapters are rebuildable runtime views, not design sources.
- Root `.claude/` is local generated discovery/runtime state for Claude and must stay ignored.
- Root `.agents/` is local generated discovery state for Codex and must stay ignored.
- Local-only host files such as `.ai/.claude/settings.local.json` and all `.ai/LOCAL/` runtime state may exist for a developer, but they must stay ignored and untracked.
- Stale-document exclusions are local runtime state. They can suppress a document for one working context, but they do not change the shared project source of truth until the document itself is updated.
- Verification should fail on tracked policy drift, invalid structure, unsynced adapters, or code validation failures. It should not fail merely because an ignored local host file exists.

## Change policy

- Change canonical skill behavior only under `.ai/SKILLS/`.
- Change canonical host instructions in `.ai/AGENTS.md` and `.ai/CLAUDE.md`.
- Change harness command behavior only under `.ai/scripts/`.
- Regenerate adapters after canonical changes.
