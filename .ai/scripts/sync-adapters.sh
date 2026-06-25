#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "$ROOT_DIR/.ai/scripts/harness-paths.sh"
SOURCE_DIR="$ROOT_DIR/.ai/SKILLS"
ADAPTERS_DIR="$ROOT_DIR/.ai/ADAPTERS"
CLAUDE_DIR="$AI_CLAUDE_HOME/skills"
AGENTS_DIR="$AI_AGENTS_HOME/skills"
CODEX_DISCOVERY_HOME="$ROOT_DIR/.agents"
CLAUDE_DISCOVERY_HOME="$ROOT_DIR/.claude"

if [[ ! -d "$SOURCE_DIR" ]]; then
  echo "missing canonical skills directory: $SOURCE_DIR" >&2
  exit 1
fi

if [[ ! -d "$ADAPTERS_DIR" ]]; then
  echo "missing canonical adapters directory: $ADAPTERS_DIR" >&2
  exit 1
fi

SKILL_DIRS=()
while IFS= read -r skill_dir; do
  SKILL_DIRS+=("$skill_dir")
done < <(find "$SOURCE_DIR" -mindepth 1 -maxdepth 1 -type d | sort)

sync_target() {
  local target_dir="$1"
  mkdir -p "$target_dir"

  while IFS= read -r existing_dir; do
    rm -rf "$existing_dir"
  done < <(find "$target_dir" -mindepth 1 -maxdepth 1 -type d | sort)

  local skill_dir
  local skill_name
  for skill_dir in "${SKILL_DIRS[@]}"; do
    skill_name="$(basename "$skill_dir")"
    mkdir -p "$target_dir/$skill_name"
    cp -R "$skill_dir/." "$target_dir/$skill_name/"
  done
}

link_or_copy() {
  local source_path="$1"
  local target_path="$2"

  if [[ -e "$target_path" && ! -L "$target_path" ]]; then
    backup="$target_path.backup.$(date +%Y%m%d%H%M%S)"
    mv "$target_path" "$backup"
    echo "moved existing $(basename "$target_path") to $backup"
  fi

  rm -f "$target_path"
  if ! ln -s "$source_path" "$target_path" 2>/dev/null; then
    if [[ -d "$source_path" ]]; then
      mkdir -p "$target_path"
      sync_target "$target_path"
    else
      cp "$source_path" "$target_path"
    fi
  fi
}

sync_target "$CLAUDE_DIR"
sync_target "$AGENTS_DIR"

mkdir -p "$AI_CLAUDE_HOME" "$AI_AGENTS_HOME" "$AI_CODEX_HOME"
cp "$ADAPTERS_DIR/claude/settings.json" "$AI_CLAUDE_HOME/settings.json"
cp "$ADAPTERS_DIR/claude/README.md" "$AI_CLAUDE_HOME/README.md"
cp "$ADAPTERS_DIR/agents/README.md" "$AI_AGENTS_HOME/README.md"
cp "$ADAPTERS_DIR/codex/config.toml" "$AI_CODEX_HOME/config.toml"
cp "$ADAPTERS_DIR/codex/hooks.json" "$AI_CODEX_HOME/hooks.json"
cp "$ADAPTERS_DIR/codex/README.md" "$AI_CODEX_HOME/README.md"

mkdir -p "$CLAUDE_DISCOVERY_HOME"
cp "$ADAPTERS_DIR/claude/README.md" "$CLAUDE_DISCOVERY_HOME/README.md"
link_or_copy "../.ai/.claude/skills" "$CLAUDE_DISCOVERY_HOME/skills"
link_or_copy "../.ai/.claude/settings.json" "$CLAUDE_DISCOVERY_HOME/settings.json"

mkdir -p "$CODEX_DISCOVERY_HOME"
cp "$ADAPTERS_DIR/agents/README.md" "$CODEX_DISCOVERY_HOME/README.md"
link_or_copy "../.ai/.agents/skills" "$CODEX_DISCOVERY_HOME/skills"

echo "synced ${#SKILL_DIRS[@]} canonical skills and runtime adapters from .ai/"
echo "installed ignored root .claude/skills and .claude/settings.json shim for Claude"
echo "installed ignored root .agents/skills discovery shim for Codex slash commands"
echo "note: root AGENTS.md is installed separately via .ai/scripts/install-root-entrypoints.sh"
