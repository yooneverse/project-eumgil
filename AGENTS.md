# AGENTS.md

Root AI host entrypoint for this repository.

Canonical Codex instructions live in `.ai/AGENTS.md`.
Canonical Claude instructions live in `.ai/CLAUDE.md`.

The root `CLAUDE.md` entrypoint is intentionally folded into this file so the repository has one root instruction file.

## Setup

- If this repository adopted the harness by copying only `.ai/`, run `./.ai/scripts/install-root-entrypoints.sh`.
- Codex sessions should read and follow `.ai/AGENTS.md` before implementation work.
- Claude sessions should read and follow `.ai/CLAUDE.md` before major changes.
- Run `./.ai/scripts/sync-adapters.sh` after canonical skill changes; generated adapters live under `.ai/.agents`, `.ai/.claude`, and `.ai/.codex`.

## Discovery

- Codex uses this `AGENTS.md` plus the ignored root `.agents/skills` discovery shim.
- Claude uses this `AGENTS.md` as the root instruction entrypoint plus the ignored root `.claude/skills` and `.claude/settings.json` shims.
- Do not recreate a root `CLAUDE.md`; keep Claude-specific canonical guidance in `.ai/CLAUDE.md`.
