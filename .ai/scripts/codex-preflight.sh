#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "$ROOT_DIR/.ai/scripts/harness-paths.sh"
harness_ensure_local_state
"$ROOT_DIR/.ai/scripts/update-progress.sh" >/dev/null
"$ROOT_DIR/.ai/scripts/update-metrics.sh" >/dev/null

python3 - <<'PY' "$ROOT_DIR" "$AI_PLAN_DIR" "$AI_EVAL_DIR"
import json
import sys
from collections import Counter
from datetime import datetime, timedelta, timezone
from pathlib import Path

root = Path(sys.argv[1])
plan_dir = Path(sys.argv[2])
eval_dir = Path(sys.argv[3])
progress = json.loads((plan_dir / "progress.json").read_text(encoding="utf-8"))
metrics = json.loads((eval_dir / "metrics.json").read_text(encoding="utf-8"))
project_md = (root / ".ai" / "PROJECT.md").read_text(encoding="utf-8")
smoke_sh = (root / ".ai" / "scripts" / "smoke.sh").read_text(encoding="utf-8")
retry_log = eval_dir / "retry-log.jsonl"

items = progress.get("items", [])
summary = progress.get("summary", {})
blocked = [item for item in items if item.get("status") == "blocked"]
in_progress = [item for item in items if item.get("status") == "in_progress"]

hot_clusters = Counter()
window_start = datetime.now(timezone.utc) - timedelta(hours=24)
if retry_log.exists():
    for line in retry_log.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        try:
            item = json.loads(line)
            sig = item.get("signature", "")
            ts = item.get("timestamp", "")
            note = item.get("note", "")
            if not sig or sig == "placeholder" or note:
                continue
            dt = datetime.fromisoformat(ts.replace("Z", "+00:00"))
            if dt >= window_start:
                hot_clusters[sig] += 1
        except Exception:
            continue

warnings = []
if "Template Project" in project_md:
    warnings.append("project identity is still default")
if "TODO(project)" in smoke_sh:
    warnings.append("smoke command is still placeholder")
if blocked:
    warnings.append(f"{len(blocked)} blocked work item(s)")
if hot_clusters:
    clusters = [sig for sig, count in hot_clusters.items() if count >= 3]
    if clusters:
        warnings.append(f"{len(clusters)} repeated failure cluster(s) need strategy change")
if metrics.get("release_readiness_confidence") is None:
    warnings.append("release readiness confidence is not recorded yet")

print("Codex Preflight")
print("")
print("Status")
print(f"- Sprint: {progress.get('sprint_name', 'unknown')}")
print(f"- Planned: {summary.get('planned', 0)}")
print(f"- Completed: {summary.get('completed', 0)}")
print(f"- In progress: {len(in_progress)}")
print(f"- Blocked: {len(blocked)}")
print(f"- Harness health: {metrics.get('harness_health_score')}")

print("")
print("Guard Commands")
print('- Before shell mutations: .ai/scripts/check-dangerous-command.sh "<command>"')
print("- Before implementation edits: .ai/scripts/check-tdd-guard.sh --mode pre <paths>")
print("- After failed attempts: .ai/scripts/record-retry.sh <signature>")
print("- Before another repeated attempt: .ai/scripts/check-circuit-breaker.sh <signature>")

print("")
print("Review Route")
print("- Install root entrypoints with .ai/scripts/install-root-entrypoints.sh if AGENTS.md is missing")
print("- Implement in Codex with AGENTS.md and .ai/.agents/skills/")
print("- Hand off review with .ai/scripts/codex-review-brief.sh and then use Claude review flow")

if warnings:
    print("")
    print("Warnings")
    for warning in warnings:
        print(f"- {warning}")
PY
