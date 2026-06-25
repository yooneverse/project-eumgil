# Local Setup

## Goal

Document the minimal steps a contributor needs to run the project and its harness locally.

## Template checklist

- Install the project runtime and package manager.
- Install or enable the preferred AI hosts.
- If the repository adopted the harness by copying only `.ai/`, run `./.ai/scripts/install-root-entrypoints.sh`.
- Review root `AGENTS.md`, plus `.ai/AGENTS.md` for Codex and `.ai/CLAUDE.md` for Claude.
- Run `./.ai/scripts/sync-adapters.sh`.
- Run `./.ai/scripts/verify.sh`.
- If Git hooks should run locally, run `git config core.hooksPath .ai/git-hooks`; the Lefthook config lives at `.ai/lefthook.yml`.

## Command slot

Replace the project-specific environment bootstrap command below after adoption:

`TODO(project): add the real local setup command sequence`
