#!/usr/bin/env bash
# ============================================================
# git-br.sh — Jira ticket 포함 브랜치 생성 스크립트
# ============================================================
#
# 사용 예시:
#   git br be/feat/login-31
#   → be/feat/login-S14P31E102-31
#
# 규칙:
#   브랜치명은 반드시 마지막이 -숫자 형태여야 함
#
# 비허용 예시:
#   be/feat/login-31-ryuwon
#   be/feat/login-S14P31E102-31-ryuwon
#
# ============================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=/dev/null
source "$SCRIPT_DIR/jira-utils.sh"

usage() {
  cat <<'EOF' >&2
usage: git br <branch-name> [start-point]

example:
  git br be/feat/login-32
  -> be/feat/login-S14P31E102-32
EOF
}

main() {
  local jira_prefix=""
  jira_prefix="$(jira_get_prefix)"

  # ── 인자 ────────────────────────────────────────────────
  # 첫 번째 인자는 브랜치명, 나머지는 start-point로 넘긴다.
  local input_branch="${1:-}"
  if [[ -z "$input_branch" ]]; then
    usage
    exit 1
  fi
  shift || true

  local expanded_branch=""
  # ── 브랜치명 변환 ───────────────────────────────────────
  # 규칙이 애매하게 풀리면 더 헷갈리기 쉬워서, 틀린 입력은 바로 막는다.
  if ! expanded_branch="$(jira_expand_branch_name "$jira_prefix" "$input_branch")"; then
    printf 'error: branch name must end with -<ticket-number>. received: %s\n' "$input_branch" >&2
    exit 1
  fi

  # ── 브랜치 생성 ─────────────────────────────────────────
  git switch -c "$expanded_branch" "$@"
}

main "$@"
