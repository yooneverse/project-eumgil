# Guards

## Purpose

Define harness-level safeguards that should run before risky automation proceeds.

## TDD guard

- If production implementation files change, check whether meaningful related tests changed too.
- Default policy in this template: block production edits before execution when no related test work exists yet, then warn again after edits if needed.
- Exceptions should be explicit and rare.
- Entry point: `.ai/scripts/check-tdd-guard.sh`

## Dangerous command guard

- Block obviously destructive commands before execution whenever possible.
- Default deny examples: `rm -rf`, `git reset --hard`, force push, destructive bulk deletes, irreversible environment mutations.
- Keep the rule set short and conservative.
- Entry point: `.ai/scripts/check-dangerous-command.sh`

## Circuit breaker

- If the same or effectively equivalent failure repeats several times in a short window, stop retrying and require a strategy change.
- Default threshold: 3 equivalent failures in 30 minutes.
- Record retries in `.ai/LOCAL/EVALS/retry-log.jsonl`.
- Entry point: `.ai/scripts/check-circuit-breaker.sh`

## Code validation guard

- Validate newly added or changed code before structural verification passes.
- Always check shell script syntax for harness scripts.
- Check JSON and TOML syntax for canonical and generated adapter artifacts.
- Compile changed Python files and run JavaScript syntax checks when the relevant runtime exists.
- Run optional linters such as `shellcheck` when available, but do not make the template unusable on machines without those tools.
- Entry point: `.ai/scripts/check-code-validation.sh`

## Local-only host files

- Local host files may enforce user-specific permissions or locks.
- Verification must fail if local-only files are tracked or not ignored.
- Verification must not fail solely because an ignored local-only file exists.
- Current local-only file: `.ai/.claude/settings.local.json`

## Operating rule

Guards should remain deterministic, fast, and easy to wire into host-specific hooks later. They are enforcement helpers, not the canonical source of policy by themselves. Policy remains documented in `.ai/`.
