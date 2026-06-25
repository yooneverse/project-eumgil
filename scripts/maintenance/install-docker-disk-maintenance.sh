#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SCRIPT_SOURCE="$ROOT_DIR/scripts/maintenance/docker-disk-maintenance.sh"
SCRIPT_TARGET="${SCRIPT_TARGET:-/usr/local/sbin/e102-docker-disk-maintenance.sh}"
ENV_FILE="${ENV_FILE:-/etc/e102-docker-disk-maintenance.env}"
CRON_FILE="${CRON_FILE:-/etc/cron.d/e102-docker-disk-maintenance}"
LOGROTATE_FILE="${LOGROTATE_FILE:-/etc/logrotate.d/e102-docker-disk-maintenance}"
LOG_FILE="${LOG_FILE:-/var/log/e102-docker-disk-maintenance.log}"
CRON_SCHEDULE="${CRON_SCHEDULE:-17 */3 * * *}"
CONFIGURE_DOCKER_LOG_ROTATION="${CONFIGURE_DOCKER_LOG_ROTATION:-true}"
DOCKER_LOG_MAX_SIZE="${DOCKER_LOG_MAX_SIZE:-50m}"
DOCKER_LOG_MAX_FILE="${DOCKER_LOG_MAX_FILE:-3}"
RESTART_DOCKER_DAEMON="${RESTART_DOCKER_DAEMON:-false}"

if [ "$(id -u)" -ne 0 ]; then
  echo "Run as root. Example: sudo bash $0" >&2
  exit 1
fi

if [ ! -f "$SCRIPT_SOURCE" ]; then
  echo "Maintenance script not found: $SCRIPT_SOURCE" >&2
  exit 1
fi

install -m 0755 "$SCRIPT_SOURCE" "$SCRIPT_TARGET"

if [ ! -f "$ENV_FILE" ]; then
  cat >"$ENV_FILE" <<'EOF'
DOCKER_DISK_MAINTENANCE_MODE=scheduled
DOCKER_DISK_PRUNE_CONTAINERS=true
DOCKER_DISK_PRUNE_IMAGES=true
DOCKER_DISK_PRUNE_IMAGES_ALL=false
DOCKER_DISK_PRUNE_BUILDER=true
DOCKER_DISK_PRUNE_VOLUMES=false
DOCKER_DISK_BUILDER_ALL=true
DOCKER_DISK_CONTAINER_UNTIL=24h
DOCKER_DISK_IMAGE_UNTIL=168h
DOCKER_DISK_BUILDER_UNTIL=24h
DOCKER_DISK_BUILDER_KEEP_STORAGE=8GB
JENKINS_WORKSPACE_DIR=/var/lib/docker/volumes/e102-jenkins_jenkins-home/_data/workspace
JENKINS_WORKSPACE_RETENTION_DAYS=3
JENKINS_BACKUP_ARCHIVE_DIR=/home/ubuntu/e102/jenkins-backups/archives
JENKINS_BACKUP_RETENTION_DAYS=14
EOF
  chmod 0644 "$ENV_FILE"
fi

cat >"$CRON_FILE" <<EOF
SHELL=/bin/bash
PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
$CRON_SCHEDULE root set -a; [ -f "$ENV_FILE" ] && . "$ENV_FILE"; set +a; "$SCRIPT_TARGET" >> "$LOG_FILE" 2>&1
EOF
chmod 0644 "$CRON_FILE"

cat >"$LOGROTATE_FILE" <<EOF
$LOG_FILE {
  daily
  rotate 7
  compress
  missingok
  notifempty
  copytruncate
}
EOF
chmod 0644 "$LOGROTATE_FILE"

if [ "$CONFIGURE_DOCKER_LOG_ROTATION" = "true" ]; then
  python3 - "$DOCKER_LOG_MAX_SIZE" "$DOCKER_LOG_MAX_FILE" <<'PY'
import json
import os
import sys

path = "/etc/docker/daemon.json"
max_size, max_file = sys.argv[1], sys.argv[2]

if os.path.exists(path):
    with open(path, "r", encoding="utf-8") as f:
        raw = f.read().strip()
    data = json.loads(raw) if raw else {}
else:
    data = {}

data.setdefault("log-driver", "json-file")
log_opts = data.setdefault("log-opts", {})
log_opts.setdefault("max-size", max_size)
log_opts.setdefault("max-file", max_file)

tmp = path + ".tmp"
with open(tmp, "w", encoding="utf-8") as f:
    json.dump(data, f, indent=2, sort_keys=True)
    f.write("\n")
os.replace(tmp, path)
PY
  chmod 0644 /etc/docker/daemon.json
fi

if [ "$RESTART_DOCKER_DAEMON" = "true" ]; then
  systemctl restart docker
else
  echo "Docker daemon log rotation config was written. Restart Docker during a maintenance window to apply it to new containers."
fi

echo "Docker disk maintenance installed: $CRON_FILE -> $SCRIPT_TARGET"
