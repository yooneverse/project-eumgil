# Codex Runtime Adapter

Generated from `.ai/ADAPTERS/codex/`.

## Purpose

- Keep repo-local Codex configuration explicit but minimal.
- Keep runtime-specific behavior subordinate to canonical `.ai/` assets.
- Use only the smallest hook layer needed for active Codex implementation work.

## Generated files

- `.ai/.codex/config.toml`
- `.ai/.codex/hooks.json`

## Rule

Root `AGENTS.md` plus `.ai/.agents/skills/` are the primary Codex-facing surfaces. `.ai/.codex/hooks.json` only triggers a minimal session-start preflight that surfaces canonical `.ai/scripts/` guard commands, dashboard state, and Claude review routing. Tool-level blocking still lives in the guard scripts and canonical host instructions unless a stable Codex hook contract proves otherwise.
