#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "$ROOT_DIR/.ai/scripts/harness-paths.sh"
harness_ensure_local_state

DOC_CONTEXT_DIR="$AI_LOCAL_DIR/DOCS"
DOC_CONTEXT_FILE="$DOC_CONTEXT_DIR/context-exclusions.json"
mkdir -p "$DOC_CONTEXT_DIR"

command="${1:-status}"
if [[ $# -gt 0 ]]; then
  shift
fi

python3 - "$ROOT_DIR" "$DOC_CONTEXT_FILE" "$command" "$@" <<'PY'
import hashlib
import json
import sys
from datetime import datetime, timezone
from pathlib import Path

root = Path(sys.argv[1]).resolve()
state_path = Path(sys.argv[2])
command = sys.argv[3]
args = sys.argv[4:]


def usage(exit_code=0):
    print("Usage:")
    print("  ./.ai/scripts/docs-context.sh status")
    print("  ./.ai/scripts/docs-context.sh stale <doc-path> [reason]")
    print("  ./.ai/scripts/docs-context.sh clear <doc-path>")
    print("  ./.ai/scripts/docs-context.sh list-active")
    print("  ./.ai/scripts/docs-context.sh is-active <doc-path>")
    print("  ./.ai/scripts/docs-context.sh prune")
    raise SystemExit(exit_code)


def load_state():
    if not state_path.exists():
        return {"version": 1, "excluded_docs": []}
    try:
        data = json.loads(state_path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        print(f"docs-context: invalid state file: {state_path}: {exc}", file=sys.stderr)
        raise SystemExit(2)
    data.setdefault("version", 1)
    data.setdefault("excluded_docs", [])
    return data


def save_state(data):
    state_path.parent.mkdir(parents=True, exist_ok=True)
    state_path.write_text(
        json.dumps(data, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def now_iso():
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def normalize(raw):
    if not raw:
        print("docs-context: missing document path", file=sys.stderr)
        raise SystemExit(2)
    path = Path(raw)
    if not path.is_absolute():
        path = root / path
    try:
        resolved = path.resolve()
        rel = resolved.relative_to(root)
    except ValueError:
        print(f"docs-context: path is outside repository: {raw}", file=sys.stderr)
        raise SystemExit(2)
    return resolved, rel.as_posix()


def fingerprint(path):
    if not path.exists() or not path.is_file():
        return None
    digest = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1024 * 1024), b""):
            digest.update(chunk)
    stat = path.stat()
    return {
        "sha256": digest.hexdigest(),
        "size": stat.st_size,
        "mtime_ns": stat.st_mtime_ns,
    }


def entry_state(entry):
    path = root / entry["path"]
    current = fingerprint(path)
    if current is None:
        return "missing"
    expected = entry.get("fingerprint", {})
    if current.get("sha256") == expected.get("sha256"):
        return "active"
    return "expired"


def active_entries(data):
    return [entry for entry in data.get("excluded_docs", []) if entry_state(entry) == "active"]


def print_status(data, active_only=False):
    entries = data.get("excluded_docs", [])
    if not entries:
        print("docs-context: no stale document exclusions recorded")
        return
    shown = 0
    for entry in entries:
        status = entry_state(entry)
        if active_only and status != "active":
            continue
        shown += 1
        reason = entry.get("reason") or "no reason recorded"
        print(f"{status.upper():7} {entry['path']}")
        print(f"        reason: {reason}")
        print(f"        recorded: {entry.get('recorded_at', 'unknown')}")
    if shown == 0:
        print("docs-context: no active stale document exclusions")


def mark_stale(raw_path, reason):
    path, rel = normalize(raw_path)
    fp = fingerprint(path)
    if fp is None:
        print(f"docs-context: document not found: {rel}", file=sys.stderr)
        raise SystemExit(2)
    data = load_state()
    data["excluded_docs"] = [entry for entry in data["excluded_docs"] if entry.get("path") != rel]
    data["excluded_docs"].append(
        {
            "path": rel,
            "reason": reason.strip() or "marked stale for this local context",
            "fingerprint": fp,
            "recorded_at": now_iso(),
        }
    )
    save_state(data)
    print(f"docs-context: marked stale until edited: {rel}")


def clear(raw_path):
    _, rel = normalize(raw_path)
    data = load_state()
    before = len(data["excluded_docs"])
    data["excluded_docs"] = [entry for entry in data["excluded_docs"] if entry.get("path") != rel]
    save_state(data)
    if len(data["excluded_docs"]) == before:
        print(f"docs-context: no exclusion found for {rel}")
    else:
        print(f"docs-context: cleared exclusion for {rel}")


def prune():
    data = load_state()
    before = len(data["excluded_docs"])
    data["excluded_docs"] = active_entries(data)
    save_state(data)
    print(f"docs-context: pruned {before - len(data['excluded_docs'])} expired or missing exclusion(s)")


if command in {"help", "-h", "--help"}:
    usage(0)

data = load_state()

if command == "status":
    print_status(data)
elif command in {"stale", "exclude"}:
    if not args:
        usage(2)
    mark_stale(args[0], " ".join(args[1:]))
elif command in {"clear", "include"}:
    if len(args) != 1:
        usage(2)
    clear(args[0])
elif command == "list-active":
    for entry in active_entries(data):
        print(entry["path"])
elif command == "is-active":
    if len(args) != 1:
        usage(2)
    _, rel = normalize(args[0])
    active = {entry["path"] for entry in active_entries(data)}
    raise SystemExit(0 if rel in active else 1)
elif command == "prune":
    prune()
else:
    usage(2)
PY
