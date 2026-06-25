#!/usr/bin/env bash
set -euo pipefail

MODE="${DOCKER_DISK_MAINTENANCE_MODE:-scheduled}"
if [ "${1:-}" = "--mode" ]; then
  MODE="${2:-$MODE}"
elif [ -n "${1:-}" ]; then
  MODE="$1"
fi

DRY_RUN="${DOCKER_DISK_MAINTENANCE_DRY_RUN:-false}"

case "$MODE" in
  report)
    DEFAULT_PRUNE_CONTAINERS=false
    DEFAULT_PRUNE_IMAGES=false
    DEFAULT_PRUNE_BUILDER=false
    DEFAULT_PRUNE_VOLUMES=false
    DEFAULT_BUILDER_UNTIL="24h"
    ;;
  pipeline)
    DEFAULT_PRUNE_CONTAINERS=true
    DEFAULT_PRUNE_IMAGES=true
    DEFAULT_PRUNE_BUILDER=true
    DEFAULT_PRUNE_VOLUMES=false
    DEFAULT_BUILDER_UNTIL="24h"
    ;;
  scheduled)
    DEFAULT_PRUNE_CONTAINERS=true
    DEFAULT_PRUNE_IMAGES=true
    DEFAULT_PRUNE_BUILDER=true
    DEFAULT_PRUNE_VOLUMES=false
    DEFAULT_BUILDER_UNTIL="24h"
    ;;
  aggressive)
    DEFAULT_PRUNE_CONTAINERS=true
    DEFAULT_PRUNE_IMAGES=true
    DEFAULT_PRUNE_BUILDER=true
    DEFAULT_PRUNE_VOLUMES=false
    DEFAULT_BUILDER_UNTIL="12h"
    ;;
  *)
    echo "Unknown maintenance mode: $MODE" >&2
    echo "Usage: $0 [report|pipeline|scheduled|aggressive]" >&2
    exit 2
    ;;
esac

if [ "$MODE" = "aggressive" ]; then
  DEFAULT_IMAGE_UNTIL="72h"
  DEFAULT_BUILDER_KEEP_STORAGE="4GB"
else
  DEFAULT_IMAGE_UNTIL="168h"
  DEFAULT_BUILDER_KEEP_STORAGE="8GB"
fi

PRUNE_CONTAINERS="${DOCKER_DISK_PRUNE_CONTAINERS:-$DEFAULT_PRUNE_CONTAINERS}"
PRUNE_IMAGES="${DOCKER_DISK_PRUNE_IMAGES:-$DEFAULT_PRUNE_IMAGES}"
PRUNE_IMAGES_ALL="${DOCKER_DISK_PRUNE_IMAGES_ALL:-false}"
PRUNE_BUILDER="${DOCKER_DISK_PRUNE_BUILDER:-$DEFAULT_PRUNE_BUILDER}"
PRUNE_VOLUMES="${DOCKER_DISK_PRUNE_VOLUMES:-$DEFAULT_PRUNE_VOLUMES}"
BUILDER_ALL="${DOCKER_DISK_BUILDER_ALL:-true}"
CONTAINER_UNTIL="${DOCKER_DISK_CONTAINER_UNTIL:-24h}"
IMAGE_UNTIL="${DOCKER_DISK_IMAGE_UNTIL:-$DEFAULT_IMAGE_UNTIL}"
BUILDER_UNTIL="${DOCKER_DISK_BUILDER_UNTIL:-$DEFAULT_BUILDER_UNTIL}"
BUILDER_KEEP_STORAGE="${DOCKER_DISK_BUILDER_KEEP_STORAGE:-$DEFAULT_BUILDER_KEEP_STORAGE}"
VOLUME_UNTIL="${DOCKER_DISK_VOLUME_UNTIL:-168h}"

JENKINS_WORKSPACE_DIR="${JENKINS_WORKSPACE_DIR:-}"
JENKINS_WORKSPACE_RETENTION_DAYS="${JENKINS_WORKSPACE_RETENTION_DAYS:-3}"
JENKINS_BACKUP_ARCHIVE_DIR="${JENKINS_BACKUP_ARCHIVE_DIR:-}"
JENKINS_BACKUP_RETENTION_DAYS="${JENKINS_BACKUP_RETENTION_DAYS:-14}"

run() {
  echo "+ $*"
  if [ "$DRY_RUN" != "true" ]; then
    "$@"
  fi
}

section() {
  printf '\n== %s ==\n' "$1"
}

cleanup_directory_children() {
  local label="$1"
  local dir="$2"
  local days="$3"

  if [ -z "$dir" ] || [ ! -d "$dir" ]; then
    return 0
  fi

  section "$label older than ${days}d"
  if [ "$DRY_RUN" = "true" ]; then
    find "$dir" -mindepth 1 -maxdepth 1 -mtime +"$days" -print || true
  else
    find "$dir" -mindepth 1 -maxdepth 1 -mtime +"$days" -print -exec rm -rf {} + || true
  fi
}

section "host disk before"
date -Is
df -hT / || true
docker system df || true

if [ "$PRUNE_CONTAINERS" = "true" ]; then
  section "docker container prune"
  run docker container prune --force --filter "until=$CONTAINER_UNTIL"
fi

if [ "$PRUNE_IMAGES" = "true" ]; then
  section "docker image prune"
  image_prune_args=(docker image prune --force)
  if [ "$PRUNE_IMAGES_ALL" = "true" ]; then
    image_prune_args+=(--all)
  fi
  run "${image_prune_args[@]}" --filter "until=$IMAGE_UNTIL"
fi

if [ "$PRUNE_BUILDER" = "true" ]; then
  section "docker builder prune"
  builder_prune_args=(docker builder prune --force)
  if [ "$BUILDER_ALL" = "true" ]; then
    builder_prune_args+=(--all)
  fi
  if [ -n "$BUILDER_UNTIL" ]; then
    builder_prune_args+=(--filter "until=$BUILDER_UNTIL")
  fi
  builder_storage_flag="--keep-storage"
  if docker builder prune --help 2>/dev/null | grep -q -- "--max-used-space"; then
    builder_storage_flag="--max-used-space"
  fi
  run "${builder_prune_args[@]}" "$builder_storage_flag" "$BUILDER_KEEP_STORAGE"
fi

if [ "$PRUNE_VOLUMES" = "true" ]; then
  section "docker volume prune"
  run docker volume prune --force --filter "label!=keep" --filter "until=$VOLUME_UNTIL"
fi

cleanup_directory_children "jenkins workspaces" "$JENKINS_WORKSPACE_DIR" "$JENKINS_WORKSPACE_RETENTION_DAYS"
cleanup_directory_children "jenkins backup archives" "$JENKINS_BACKUP_ARCHIVE_DIR" "$JENKINS_BACKUP_RETENTION_DAYS"

section "host disk after"
df -hT / || true
docker system df || true
