# Claude Adapter

Generated from `.ai/ADAPTERS/claude/` and `.ai/SKILLS/`.

## Purpose

- Provide Claude Code with repo-shared hooks and skill layout.
- Keep hooks minimal and tied only to actively used safeguards.
- `sync-adapters.sh` also creates ignored root `.claude/skills` and `.claude/settings.json` shims because Claude Code discovers repository skills and settings from the root `.claude` path.

## Generated files

- `.ai/.claude/settings.json`
- `.ai/.claude/skills/`
- `.claude/settings.json` ignored shim
- `.claude/skills/` ignored shim

## Non-generated file

- `.ai/.claude/settings.local.json` and any root `.claude/settings.local.json` are intentionally local-only and must not be committed.
