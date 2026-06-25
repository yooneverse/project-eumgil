# Adapters

This directory is the canonical source for generated runtime-specific adapter files.

## Rule

- Keep runtime-independent methodology in the rest of `.ai/`.
- Keep only minimal runtime-specific layout or hook templates here.
- Generate `.ai/.claude/`, `.ai/.agents/`, and `.ai/.codex/` from this directory plus `.ai/SKILLS/`.
- Do not hand-edit generated adapter outputs unless debugging the generator.
