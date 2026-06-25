#!/usr/bin/env bash
# ============================================================
# jira-utils.sh — Git/Jira 자동화 공용 함수
# ============================================================
#
# 포함 기능:
#   - Jira prefix 조회
#   - 브랜치명 정규화
#   - 브랜치명에서 Jira key 추출
#   - [#31] 형태 축약표기 확장
#   - commit subject에 Jira key 주입
#
# ============================================================

set -euo pipefail

# 현재 Git 저장소 루트를 구한다.
jira_get_repo_root() {
  git rev-parse --show-toplevel 2>/dev/null
}

# ── Jira prefix 조회 ───────────────────────────────────────
# Jira prefix는 git config를 먼저 보고, 없으면 폴더명으로 대신한다.
jira_get_prefix() {
  local configured_prefix=""
  configured_prefix="$(git config --get jira.prefix 2>/dev/null || true)"

  if [[ -n "$configured_prefix" ]]; then
    printf '%s\n' "$configured_prefix"
    return 0
  fi

  local repo_root=""
  repo_root="$(jira_get_repo_root 2>/dev/null || true)"

  if [[ -n "$repo_root" ]]; then
    basename "$repo_root"
    return 0
  fi

  return 1
}

# ── 브랜치명 정규화 ───────────────────────────────────────
# 브랜치 규칙은 두 가지만 허용한다.
# - 축약형: be/feat/login-32
# - 정규형: be/feat/login-S14P31E102-32
# ticket 번호가 끝에 없으면 규칙 위반으로 본다.
jira_expand_branch_name() {
  local jira_prefix="$1"
  local branch_name="$2"
  local normalized_regex="^(.+)-(${jira_prefix})-([0-9]+)$"
  local shorthand_regex='^(.+)-([0-9]+)$'

  if [[ "$branch_name" =~ $normalized_regex ]]; then
    printf '%s\n' "$branch_name"
    return 0
  fi

  # Jira prefix가 이미 들어 있는데 형태가 틀리면
  # 조용히 고치기보다 실패시키는 편이 덜 헷갈린다.
  if [[ "$branch_name" == *"$jira_prefix"* ]]; then
    return 1
  fi

  if [[ "$branch_name" =~ $shorthand_regex ]]; then
    printf '%s-%s-%s\n' "${BASH_REMATCH[1]}" "$jira_prefix" "${BASH_REMATCH[2]}"
    return 0
  fi

  return 1
}

# ── 현재 브랜치에서 Jira key 추출 ─────────────────────────
# 현재 브랜치명에서 Jira key만 뽑아낸다.
jira_find_branch_key() {
  local jira_prefix="$1"
  local branch_name="${2:-}"

  if [[ -z "$branch_name" ]]; then
    branch_name="$(git symbolic-ref --quiet --short HEAD 2>/dev/null || true)"
  fi

  if [[ "$branch_name" =~ (${jira_prefix}-[0-9]+) ]]; then
    printf '%s\n' "${BASH_REMATCH[1]}"
    return 0
  fi

  return 1
}

# ── [#31] 형태 축약표기 확장 ──────────────────────────────
# [#12, #13] 같은 축약 표기를 Jira key 목록으로 펼친다.
jira_expand_bracket_hash_refs() {
  local jira_prefix="$1"
  local text="$2"
  local result=""
  local remainder="$text"

  while [[ "$remainder" =~ ^([^\[]*)\[([^][]*)\](.*)$ ]]; do
    local before="${BASH_REMATCH[1]}"
    local inside="${BASH_REMATCH[2]}"
    remainder="${BASH_REMATCH[3]}"

    if [[ "$inside" == *"#"* ]]; then
      local expanded_inside="$inside"

      while [[ "$expanded_inside" =~ ^([^#]*)#([0-9]+)(.*)$ ]]; do
        expanded_inside="${BASH_REMATCH[1]}${jira_prefix}-${BASH_REMATCH[2]}${BASH_REMATCH[3]}"
      done

      result+="${before}[${expanded_inside}]"
    else
      result+="${before}[${inside}]"
    fi
  done

  result+="$remainder"
  printf '%s\n' "$result"
}

# ── commit subject에 Jira key 삽입 ────────────────────────
# Conventional Commit 첫 줄에 Jira key를 자연스럽게 끼워 넣는다.
jira_inject_key_into_subject() {
  local jira_key="$1"
  local subject_line="$2"
  local conventional_commit_regex='^(([^[:space:]]+[[:space:]]+)?)([[:alpha:]][[:alnum:]-]*(\([^)]+\))?!?):[[:space:]]*(.*)$'

  if [[ -z "$subject_line" ]]; then
    printf '[%s]\n' "$jira_key"
    return 0
  fi

  if [[ "$subject_line" == *"[$jira_key]"* ]]; then
    printf '%s\n' "$subject_line"
    return 0
  fi

  if [[ "$subject_line" == fixup!\ * || "$subject_line" == squash!\ * ]]; then
    printf '%s\n' "$subject_line"
    return 0
  fi

  if [[ "$subject_line" =~ $conventional_commit_regex ]]; then
    printf '%s%s[%s]: %s\n' "${BASH_REMATCH[1]}" "${BASH_REMATCH[3]}" "$jira_key" "${BASH_REMATCH[5]}"
    return 0
  fi

  printf '[%s] %s\n' "$jira_key" "$subject_line"
}
