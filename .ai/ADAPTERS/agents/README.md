# Codex Skills Adapter

Generated from `.ai/SKILLS/`.

## Purpose

- Expose repository skills to Codex through `.ai/.agents/skills/`.
- Keep `.ai/.agents/` focused on skill discovery while canonical instructions live under `.ai/`.
- `sync-adapters.sh` also creates an ignored root `.agents/skills` shim because Codex discovers repository skills from the root `.agents` path.

## Note

The harness keeps generated Codex adapter output under `.ai/.agents/` and exposes it through an ignored root `.agents/skills` discovery shim. Install root `AGENTS.md` with `.ai/scripts/install-root-entrypoints.sh` when the repository adopted the harness by copying only `.ai/`.
