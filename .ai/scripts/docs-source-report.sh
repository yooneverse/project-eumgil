#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "$ROOT_DIR/.ai/scripts/harness-paths.sh"
harness_ensure_local_state

python3 - <<'PY' "$ROOT_DIR" "$AI_LOCAL_DIR/DOCS/context-exclusions.json"
import hashlib
import json
import re
import sys
from pathlib import Path

root = Path(sys.argv[1])
context_path = Path(sys.argv[2])

groups = {
    "제품/PRD": ["Docs/PRD/*.md", "Docs/기획/*기획서*.md", "Docs/기획/*기능명세서*.md"],
    "화면/FE": ["FE/docs/*.md", "FE/docs/todo/*.md", "FE/docs/sprint_backlog/*.md", "Docs/기획/*화면명세서*.md"],
    "API": ["Docs/API/**/*.md"],
    "ERD/데이터": ["Docs/ERD/*.md", "Docs/PoC/*.md"],
    "인프라": ["Docs/인프라/*.md"],
    "컨벤션": ["Docs/컨벤션/*.md", "Docs/skills/**/*.md", "FE/docs/*컨벤션*.md"],
}

date_pattern = re.compile(r"(\d{4}-\d{2}-\d{2})")


def fingerprint(path: Path):
    if not path.exists() or not path.is_file():
        return None
    digest = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def load_active_exclusions():
    if not context_path.exists():
        return {}
    try:
        data = json.loads(context_path.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return {}
    active = {}
    for entry in data.get("excluded_docs", []):
        rel = entry.get("path", "")
        expected = entry.get("fingerprint", {}).get("sha256")
        if not rel or not expected:
            continue
        path = root / rel
        if fingerprint(path) == expected:
            active[rel] = entry.get("reason", "")
    return active


def doc_date(path: Path):
    match = date_pattern.search(path.name)
    return match.group(1) if match else "0000-00-00"


active_exclusions = load_active_exclusions()

print("Docs Source Report")
print("")
for label, patterns in groups.items():
    files = []
    for pattern in patterns:
        files.extend(root.glob(pattern))
    files = sorted(
        {p for p in files if p.is_file() and p.relative_to(root).as_posix() not in active_exclusions},
        key=lambda p: (doc_date(p), str(p)),
        reverse=True,
    )

    print(label)
    if not files:
        print("- 문서 없음")
        print("")
        continue

    for path in files[:5]:
        rel = path.relative_to(root)
        print(f"- {doc_date(path)}  {rel}")
    if len(files) > 5:
        print(f"- 외 {len(files) - 5}개")
    print("")

if active_exclusions:
    print("이번 로컬 컨텍스트에서 제외된 문서")
    for rel, reason in sorted(active_exclusions.items()):
        suffix = f" — {reason}" if reason else ""
        print(f"- {rel}{suffix}")
    print("")

print("사용 규칙")
print("- /plan은 위 목록에서 작업 영역에 맞는 최신 상세 문서를 먼저 고른다.")
print("- FE 작업은 FE/docs와 실제 FE/app 코드를 Docs/기획보다 우선한다.")
print("- 위 제외 목록의 문서는 수정되거나 clear 되기 전까지 source of truth로 사용하지 않는다.")
print("- 문서끼리 충돌하면 계획 파일에 '문서 충돌'과 임시 기준을 남긴다.")
PY
