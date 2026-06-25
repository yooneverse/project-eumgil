#!/usr/bin/env bash
# ============================================================
# test-git-jira.sh — Git/Jira 자동화 회귀 테스트
# ============================================================
#
# 확인하는 것:
#   - 브랜치명 규칙
#   - commit hook 동작
#   - make init 설치 결과
#   - Windows / PATH bash 환경 호환성
#
# 사용법:
#   make test-git-jira
#   bash scripts/test/test-git-jira.sh
#
# ============================================================

set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
LIB_PATH="$ROOT_DIR/scripts/init/jira-utils.sh"
HOOK_TEMPLATE_PATH="$ROOT_DIR/scripts/init/prepare-commit-msg"

fail() {
  printf 'FAIL: %s\n' "$1" >&2
  exit 1
}

assert_eq() {
  local expected="$1"
  local actual="$2"
  local label="$3"

  if [[ "$expected" != "$actual" ]]; then
    printf 'ASSERTION FAILED: %s\n' "$label" >&2
    printf '  expected: %s\n' "$expected" >&2
    printf '  actual:   %s\n' "$actual" >&2
    exit 1
  fi
}

setup_repo() {
  local repo_dir="$1"

  # 각 테스트는 임시 저장소에서 따로 돈다.
  git init -q "$repo_dir"
  git -C "$repo_dir" config user.name "Codex Test"
  git -C "$repo_dir" config user.email "codex@example.com"
  git -C "$repo_dir" config jira.prefix "S14P31E102"
  git -C "$repo_dir" commit --allow-empty -m "chore: init" >/dev/null
}

test_branch_name_expansion() {
  [[ -f "$LIB_PATH" ]] || fail "missing library: $LIB_PATH"
  # shellcheck source=/dev/null
  source "$LIB_PATH"

  local actual
  actual="$(jira_expand_branch_name "S14P31E102" "be/feat/login-32")"

  assert_eq "be/feat/login-S14P31E102-32" "$actual" "branch name should embed jira prefix before numeric suffix"
}

# 이미 정규화된 브랜치명은 손대지 않아야 한다.
test_branch_name_accepts_normalized_input() {
  [[ -f "$LIB_PATH" ]] || fail "missing library: $LIB_PATH"
  # shellcheck source=/dev/null
  source "$LIB_PATH"

  local actual
  actual="$(jira_expand_branch_name "S14P31E102" "be/feat/login-S14P31E102-32")"

  assert_eq "be/feat/login-S14P31E102-32" "$actual" "already-normalized branch names should remain unchanged"
}

# ticket 번호가 끝에 없으면 규칙 위반으로 본다.
test_branch_name_rejects_non_terminal_ticket() {
  [[ -f "$LIB_PATH" ]] || fail "missing library: $LIB_PATH"
  # shellcheck source=/dev/null
  source "$LIB_PATH"

  if jira_expand_branch_name "S14P31E102" "be/feat/login-32-ryuwon" >/dev/null 2>&1; then
    fail "branch names with non-terminal ticket suffix should be rejected"
  fi
}

# Jira prefix가 이미 있어도 형태가 틀리면 거부해야 한다.
test_branch_name_rejects_malformed_prefixed_input() {
  [[ -f "$LIB_PATH" ]] || fail "missing library: $LIB_PATH"
  # shellcheck source=/dev/null
  source "$LIB_PATH"

  if jira_expand_branch_name "S14P31E102" "be/feat/login-S14P31E102-32-ryuwon" >/dev/null 2>&1; then
    fail "malformed branch names that already contain jira prefix should still be rejected"
  fi
}

# [#12] 축약 표기가 Jira key로 잘 풀리는지 본다.
test_commit_placeholder_conversion() {
  [[ -x "$HOOK_TEMPLATE_PATH" ]] || fail "missing executable hook template: $HOOK_TEMPLATE_PATH"

  local tmp_dir
  tmp_dir="$(mktemp -d)"

  setup_repo "$tmp_dir/repo"
  mkdir -p "$tmp_dir/repo/scripts/init"
  cp "$LIB_PATH" "$tmp_dir/repo/scripts/init/jira-utils.sh"
  cp "$HOOK_TEMPLATE_PATH" "$tmp_dir/repo/.git/hooks/prepare-commit-msg"
  chmod +x "$tmp_dir/repo/.git/hooks/prepare-commit-msg"

  local message_file="$tmp_dir/message.txt"
  printf 'feat[#12, #34]: login flow\n' >"$message_file"

  (
    cd "$tmp_dir/repo"
    ./.git/hooks/prepare-commit-msg "$message_file" "message"
  )

  local actual
  actual="$(cat "$message_file")"
  assert_eq "feat[S14P31E102-12, S14P31E102-34]: login flow" "$actual" "placeholder jira refs should expand inside brackets"
  rm -rf "$tmp_dir"
}

# 브랜치에서 읽은 Jira key가 commit subject에 자동으로 들어가는지 본다.
test_commit_injects_branch_ticket() {
  [[ -x "$HOOK_TEMPLATE_PATH" ]] || fail "missing executable hook template: $HOOK_TEMPLATE_PATH"

  local tmp_dir
  tmp_dir="$(mktemp -d)"

  setup_repo "$tmp_dir/repo"
  mkdir -p "$tmp_dir/repo/scripts/init"
  cp "$LIB_PATH" "$tmp_dir/repo/scripts/init/jira-utils.sh"
  cp "$HOOK_TEMPLATE_PATH" "$tmp_dir/repo/.git/hooks/prepare-commit-msg"
  chmod +x "$tmp_dir/repo/.git/hooks/prepare-commit-msg"
  git -C "$tmp_dir/repo" switch -c "be/feat/login-S14P31E102-32" >/dev/null 2>&1

  local message_file="$tmp_dir/message.txt"
  printf 'feat: login flow\n' >"$message_file"

  (
    cd "$tmp_dir/repo"
    ./.git/hooks/prepare-commit-msg "$message_file" "message"
  )

  local actual
  actual="$(cat "$message_file")"
  assert_eq "feat[S14P31E102-32]: login flow" "$actual" "branch jira key should be injected into conventional commit subject"
  rm -rf "$tmp_dir"
}

# gitmoji를 앞에 붙여도 Jira key가 같은 위치에 들어가야 한다.
test_commit_injects_branch_ticket_with_gitmoji() {
  [[ -x "$HOOK_TEMPLATE_PATH" ]] || fail "missing executable hook template: $HOOK_TEMPLATE_PATH"

  local tmp_dir
  tmp_dir="$(mktemp -d)"

  setup_repo "$tmp_dir/repo"
  mkdir -p "$tmp_dir/repo/scripts/init"
  cp "$LIB_PATH" "$tmp_dir/repo/scripts/init/jira-utils.sh"
  cp "$HOOK_TEMPLATE_PATH" "$tmp_dir/repo/.git/hooks/prepare-commit-msg"
  chmod +x "$tmp_dir/repo/.git/hooks/prepare-commit-msg"
  git -C "$tmp_dir/repo" switch -c "be/feat/login-S14P31E102-32" >/dev/null 2>&1

  local message_file="$tmp_dir/message.txt"
  printf '✨ feat: login flow\n' >"$message_file"

  (
    cd "$tmp_dir/repo"
    ./.git/hooks/prepare-commit-msg "$message_file" "message"
  )

  local actual
  actual="$(cat "$message_file")"
  assert_eq "✨ feat[S14P31E102-32]: login flow" "$actual" "branch jira key should be injected even when gitmoji is present"
  rm -rf "$tmp_dir"
}

# 타입을 대문자로 써도 같은 규칙으로 Jira key가 들어가야 한다.
test_commit_injects_branch_ticket_with_capitalized_type() {
  [[ -x "$HOOK_TEMPLATE_PATH" ]] || fail "missing executable hook template: $HOOK_TEMPLATE_PATH"

  local tmp_dir
  tmp_dir="$(mktemp -d)"

  setup_repo "$tmp_dir/repo"
  mkdir -p "$tmp_dir/repo/scripts/init"
  cp "$LIB_PATH" "$tmp_dir/repo/scripts/init/jira-utils.sh"
  cp "$HOOK_TEMPLATE_PATH" "$tmp_dir/repo/.git/hooks/prepare-commit-msg"
  chmod +x "$tmp_dir/repo/.git/hooks/prepare-commit-msg"
  git -C "$tmp_dir/repo" switch -c "be/feat/login-S14P31E102-32" >/dev/null 2>&1

  local message_file="$tmp_dir/message.txt"
  printf '✨ Feat: login flow\n' >"$message_file"

  (
    cd "$tmp_dir/repo"
    ./.git/hooks/prepare-commit-msg "$message_file" "message"
  )

  local actual
  actual="$(cat "$message_file")"
  assert_eq "✨ Feat[S14P31E102-32]: login flow" "$actual" "branch jira key should be injected when the type starts with uppercase"
  rm -rf "$tmp_dir"
}

# make init가 alias, hook, jira.prefix를 제대로 심는지 본다.
test_make_init_installs_local_git_settings() {
  local tmp_dir
  tmp_dir="$(mktemp -d)"
  local repo_dir="$tmp_dir/S14P31E102"

  mkdir -p "$repo_dir/scripts/init" "$repo_dir/scripts/test"
  cp "$ROOT_DIR/Makefile" "$repo_dir/Makefile"
  cp "$ROOT_DIR/scripts/init/git-br.sh" "$repo_dir/scripts/init/git-br.sh"
  cp "$ROOT_DIR/scripts/init/init-git-jira.sh" "$repo_dir/scripts/init/init-git-jira.sh"
  cp "$ROOT_DIR/scripts/init/jira-utils.sh" "$repo_dir/scripts/init/jira-utils.sh"
  cp "$ROOT_DIR/scripts/init/prepare-commit-msg" "$repo_dir/scripts/init/prepare-commit-msg"
  cp "$ROOT_DIR/scripts/test/test-git-jira.sh" "$repo_dir/scripts/test/test-git-jira.sh"

  setup_repo "$repo_dir"

  (
    cd "$repo_dir"
    make init JIRA_PREFIX=S14P31E102 >/dev/null
  )

  assert_eq "S14P31E102" "$(git -C "$repo_dir" config --local --get jira.prefix)" "make init should persist jira prefix"
  assert_eq ".git/hooks" "$(git -C "$repo_dir" config --local --get core.hooksPath)" "make init should point git hooks to .git/hooks"
  assert_eq "!f() { repo=\$(git rev-parse --show-toplevel); \"\$repo/scripts/init/git-br.sh\" \"\$@\"; }; f" "$(git -C "$repo_dir" config --local --get alias.br)" "make init should install git br alias"
  [[ -x "$repo_dir/.git/hooks/prepare-commit-msg" ]] || fail "make init should create an executable hook in .git/hooks"

  (
    cd "$repo_dir"
    git br be/feat/login-32 >/dev/null 2>&1
  )

  assert_eq "be/feat/login-S14P31E102-32" "$(git -C "$repo_dir" symbolic-ref --short HEAD)" "git br should create and switch to the jira-expanded branch"
  rm -rf "$tmp_dir"
}

# bash를 PATH에서 찾는 환경에서도 돌아야 한다.
test_make_init_with_path_bash() {
  local tmp_dir
  tmp_dir="$(mktemp -d)"
  local repo_dir="$tmp_dir/S14P31E102"

  mkdir -p "$repo_dir/scripts/init" "$repo_dir/scripts/test"
  cp "$ROOT_DIR/Makefile" "$repo_dir/Makefile"
  cp "$ROOT_DIR/scripts/init/git-br.sh" "$repo_dir/scripts/init/git-br.sh"
  cp "$ROOT_DIR/scripts/init/init-git-jira.sh" "$repo_dir/scripts/init/init-git-jira.sh"
  cp "$ROOT_DIR/scripts/init/jira-utils.sh" "$repo_dir/scripts/init/jira-utils.sh"
  cp "$ROOT_DIR/scripts/init/prepare-commit-msg" "$repo_dir/scripts/init/prepare-commit-msg"
  cp "$ROOT_DIR/scripts/test/test-git-jira.sh" "$repo_dir/scripts/test/test-git-jira.sh"

  setup_repo "$repo_dir"

  (
    cd "$repo_dir"
    make init JIRA_PREFIX=S14P31E102 BASH=bash >/dev/null
    git br be/feat/cross-platform-77 >/dev/null 2>&1
  )

  assert_eq "be/feat/cross-platform-S14P31E102-77" "$(git -C "$repo_dir" symbolic-ref --short HEAD)" "make init should also work when bash is resolved from PATH"
  rm -rf "$tmp_dir"
}

main() {
  test_branch_name_expansion
  test_branch_name_accepts_normalized_input
  test_branch_name_rejects_non_terminal_ticket
  test_branch_name_rejects_malformed_prefixed_input
  test_commit_placeholder_conversion
  test_commit_injects_branch_ticket
  test_commit_injects_branch_ticket_with_gitmoji
  test_commit_injects_branch_ticket_with_capitalized_type
  test_make_init_installs_local_git_settings
  test_make_init_with_path_bash
  printf 'PASS: git jira automation tests\n'
}

main "$@"
