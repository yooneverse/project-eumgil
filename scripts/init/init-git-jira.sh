#!/usr/bin/env bash
# ============================================================
# init-git-jira.sh — Git/Jira 자동화 로컬 설치 스크립트
# ============================================================
#
# 하는 일:
#   1. Jira prefix를 정함
#   2. .git/hooks/prepare-commit-msg 설치
#   3. git br alias 설정
#   4. 로컬 git config 설정
#
# 사용법:
#   make init
#   make init JIRA_PREFIX=S14P31E102
#
# ============================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
HOOK_TEMPLATE_PATH="$SCRIPT_DIR/prepare-commit-msg"
HOOK_TARGET_PATH="$REPO_ROOT/.git/hooks/prepare-commit-msg"

main() {
  local jira_prefix="${1:-}"

  # ── Jira prefix 결정 ─────────────────────────────────────
  # 우선순위:
  #   1. make init JIRA_PREFIX=...
  #   2. origin 저장소명
  #   3. 현재 폴더명
  if [[ -z "$jira_prefix" ]]; then
    local remote_url=""
    remote_url="$(git -C "$REPO_ROOT" remote get-url origin 2>/dev/null || true)"

    if [[ "$remote_url" =~ /([^/]+)\.git$ ]]; then
      jira_prefix="${BASH_REMATCH[1]}"
    fi
  fi

  if [[ -z "$jira_prefix" ]]; then
    jira_prefix="$(basename "$REPO_ROOT")"
  fi

  # ── 실행 권한 정리 ───────────────────────────────────────
  chmod +x \
    "$REPO_ROOT/scripts/init/prepare-commit-msg" \
    "$REPO_ROOT/scripts/init/git-br.sh" \
    "$REPO_ROOT/scripts/init/init-git-jira.sh" \
    "$REPO_ROOT/scripts/init/jira-utils.sh" \
    "$REPO_ROOT/scripts/test/test-git-jira.sh"

  # ── Hook 설치 ───────────────────────────────────────────
  # Git이 실제로 읽는 .git/hooks 위치에 템플릿을 복사한다.
  mkdir -p "$REPO_ROOT/.git/hooks"
  cp "$HOOK_TEMPLATE_PATH" "$HOOK_TARGET_PATH"
  chmod +x "$HOOK_TARGET_PATH"

  # ── 로컬 Git 설정 ────────────────────────────────────────
  # 전역 설정은 건드리지 않고, 현재 저장소에만 필요한 값만 넣는다.
  git -C "$REPO_ROOT" config --local jira.prefix "$jira_prefix"
  git -C "$REPO_ROOT" config --local core.hooksPath .git/hooks
  git -C "$REPO_ROOT" config --local alias.br '!f() { repo=$(git rev-parse --show-toplevel); "$repo/scripts/init/git-br.sh" "$@"; }; f'

  # ── 안내 메시지 ─────────────────────────────────────────
  printf 'Initialized git jira automation with prefix %s\n' "$jira_prefix"
  printf 'Use: git br be/feat/login-32\n'
  printf 'Installed hook: %s\n' "$HOOK_TARGET_PATH"
  printf '세팅 완료\n'
}

main "$@"
