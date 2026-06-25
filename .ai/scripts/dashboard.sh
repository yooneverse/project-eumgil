#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "$ROOT_DIR/.ai/scripts/harness-paths.sh"
harness_ensure_local_state
"$ROOT_DIR/.ai/scripts/update-progress.sh" >/dev/null
"$ROOT_DIR/.ai/scripts/update-metrics.sh" >/dev/null

LANE=""
if [[ "${1:-}" == "--lane" ]]; then
  LANE="${2:-}"
fi

python3 - <<'PY' "$ROOT_DIR" "$AI_PLAN_DIR" "$AI_EVAL_DIR" "$LANE"
import json
import re
import sys
from collections import Counter
from datetime import datetime, timedelta, timezone
from pathlib import Path

root = Path(sys.argv[1])
plan_dir = Path(sys.argv[2])
eval_dir = Path(sys.argv[3])
requested_lane = sys.argv[4].upper()
plan_path = plan_dir / "current-sprint.md"
progress_path = plan_dir / "progress.json"
metrics_path = eval_dir / "metrics.json"
retry_path = eval_dir / "retry-log.jsonl"


def strip_md(text):
    text = re.sub(r"`([^`]+)`", r"\1", text)
    text = re.sub(r"\*\*([^*]+)\*\*", r"\1", text)
    text = re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", text)
    return text.strip()


def cut(text, width=58):
    text = strip_md(text)
    return text if len(text) <= width else text[: width - 1] + "…"


def bar(done, total, width=18):
    if total <= 0:
        return "░" * width
    filled = round(width * done / total)
    return "█" * filled + "░" * (width - filled)


def print_box(title, lines):
    width = 72
    print("┌" + "─" * (width - 2) + "┐")
    print("│ " + title.ljust(width - 4) + " │")
    print("├" + "─" * (width - 2) + "┤")
    for line in lines:
        print("│ " + line[: width - 4].ljust(width - 4) + " │")
    print("└" + "─" * (width - 2) + "┘")


def plan_lane(text):
    match = re.search(r"(?im)^\s*[-*]?\s*Lane:\s*(FE|BE|Cross-functional)\s*$", text)
    if match:
        return match.group(1).upper()
    match = re.search(r"(?im)^##\s*Work Lane\s*$([\s\S]*?)(?=^##\s|\Z)", text)
    if not match:
        return ""
    section = match.group(1).upper()
    if "FE" in section and "BE" not in section:
        return "FE"
    if "BE" in section and "FE" not in section:
        return "BE"
    return ""


def item_lane(label):
    clean = strip_md(label).strip()
    if re.match(r"^\[FE\]|\bFE\s*[:/-]", clean, re.I):
        return "FE"
    if re.match(r"^\[BE\]|\bBE\s*[:/-]", clean, re.I):
        return "BE"
    return ""


def parse_checklist(path, lane=""):
    if not path.exists():
        return []
    plan_text = path.read_text(encoding="utf-8")
    current_lane = plan_lane(plan_text)
    items = []
    pattern = re.compile(r"^\s*(?:[-*+]|\d+[.)])\s+\[([ xX])\]\s+(.+?)\s*$")
    for line in plan_text.splitlines():
        match = pattern.match(line)
        if not match:
            continue
        checked = match.group(1).lower() == "x"
        label = match.group(2)
        tagged_lane = item_lane(label)
        if lane:
            if tagged_lane and tagged_lane != lane:
                continue
            if not tagged_lane and current_lane and current_lane not in {lane, "CROSS-FUNCTIONAL"}:
                continue
        lower = label.lower()
        if "blocked" in lower or "막힘" in label or "차단" in label:
            status = "막힘"
        elif checked:
            status = "완료"
        elif "진행" in label or "in progress" in lower:
            status = "진행"
        else:
            status = "대기"
        items.append({"label": label, "checked": checked, "status": status})
    return items


def retry_hot_clusters(path):
    clusters = Counter()
    window_start = datetime.now(timezone.utc) - timedelta(hours=24)
    if not path.exists():
        return {}
    for line in path.read_text(encoding="utf-8").splitlines():
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
                clusters[sig] += 1
        except Exception:
            continue
    return {sig: cnt for sig, cnt in clusters.items() if cnt >= 3}


prefix = f"/{requested_lane.lower()}-" if requested_lane in {"FE", "BE"} else "/"
dashboard_title = f"부산이음길 {requested_lane} 작업 대시보드" if requested_lane in {"FE", "BE"} else "부산이음길 작업 대시보드"
items = parse_checklist(plan_path, requested_lane)
if not plan_path.exists():
    print_box(
        dashboard_title,
        [
            "계획 파일이 없습니다.",
            f"먼저 {prefix}plan 으로 실행 가능한 계획을 세워주세요.",
            f"예상 위치: {plan_path.relative_to(root)}",
        ],
    )
    raise SystemExit(0)

if not items:
    print_box(
        dashboard_title,
        [
            "계획 체크리스트가 없습니다.",
            f"먼저 {prefix}plan 으로 체크 가능한 작업 목록을 만들어주세요.",
            f"계획 파일: {plan_path.relative_to(root)}",
        ],
    )
    raise SystemExit(0)

done = sum(1 for item in items if item["checked"])
blocked = sum(1 for item in items if item["status"] == "막힘")
active = sum(1 for item in items if item["status"] == "진행")
waiting = len(items) - done - blocked - active
percent = round(done / len(items) * 100)
hot_clusters = retry_hot_clusters(retry_path)

metrics = {}
if metrics_path.exists():
    try:
        metrics = json.loads(metrics_path.read_text(encoding="utf-8"))
    except Exception:
        metrics = {}

progress = {}
if progress_path.exists():
    try:
        progress = json.loads(progress_path.read_text(encoding="utf-8"))
    except Exception:
        progress = {}

title = progress.get("sprint_name") or plan_path.stem
lines = [
    f"계획: {title}",
    f"진행률  {bar(done, len(items))}  {percent}%  ({done}/{len(items)} 완료)",
    f"상태    진행 {active} · 대기 {waiting} · 막힘 {blocked}",
    f"리스크  반복 실패 {len(hot_clusters)} · 하네스 상태 {metrics.get('harness_health_score', '확인 전')}",
]
print_box(dashboard_title, lines)

print("")
print("다음 작업")
open_items = [item for item in items if not item["checked"]]
for index, item in enumerate(open_items[:6], start=1):
    marker = "!" if item["status"] == "막힘" else "›"
    print(f"{marker} {index}. [{item['status']}] {cut(item['label'])}")
if not open_items:
    print(f"✓ 모든 체크리스트가 완료되었습니다. {prefix}review 또는 {prefix}qa 로 검증을 진행하세요.")

if hot_clusters:
    print("")
    print("주의")
    for sig, cnt in sorted(hot_clusters.items(), key=lambda x: -x[1]):
        print(f"! 반복 실패 {cnt}회: {cut(sig)}")
    print("! 같은 접근을 반복하기 전에 /learn 또는 /investigate 로 원인을 정리하세요.")

print("")
print("추천 명령")
if not open_items:
    print(f"- {prefix}review 현재 변경사항을 검토해줘")
    print(f"- {prefix}qa 실제 사용자 흐름 기준으로 검증해줘")
elif blocked:
    print(f"- {prefix}investigate 막힌 원인을 증거 기반으로 분석해줘")
    print(f"- {prefix}plan 현재 계획을 다시 실행 가능하게 쪼개줘")
else:
    print(f"- {prefix}start 다음 체크리스트 항목을 구현해줘")
    print(f"- {prefix}fix-bug 실패한 항목을 재현하고 수정해줘")
PY
