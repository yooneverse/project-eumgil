#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

OPS_DIR="${OPS_DIR:-/home/ubuntu/e102/ops}"
JENKINS_DIR="${JENKINS_DIR:-/home/ubuntu/e102/jenkins}"
DEV_STACK_NETWORK="${DEV_STACK_NETWORK:-s14p31e102-dev_default}"

MONITORING_COMPOSE=(docker compose --env-file "$OPS_DIR/.env" -f "$OPS_DIR/docker-compose.yml")
OPS_SERVICES=(
  portainer
  grafana
  prometheus
  loki
  promtail
  blackbox-exporter
  node-exporter
  cadvisor
  redis-exporter
)

sync_tree() {
  local source_dir="$1"
  local target_dir="$2"

  mkdir -p "$target_dir"
  rsync -a --delete "$source_dir/" "$target_dir/"
}

ensure_dev_stack_network() {
  if docker network inspect "$DEV_STACK_NETWORK" >/dev/null 2>&1; then
    return 0
  fi

  echo "dev stack network '$DEV_STACK_NETWORK' is missing. Creating bootstrap network once."
  docker network create \
    --label com.docker.compose.project=s14p31e102-dev \
    --label com.docker.compose.network=default \
    "$DEV_STACK_NETWORK" >/dev/null
}

proxy_restart_required="false"
grafana_restart_required="false"
prometheus_restart_required="false"
blackbox_restart_required="false"
promtail_restart_required="false"

mkdir -p "$OPS_DIR" "$JENKINS_DIR"

if [ ! -d "$OPS_DIR/grafana" ] || ! diff -qr "$ROOT_DIR/INF/monitoring/s1/grafana" "$OPS_DIR/grafana" >/dev/null 2>&1; then
  grafana_restart_required="true"
fi

if [ ! -f "$OPS_DIR/prometheus/prometheus.yml" ] || ! cmp -s "$ROOT_DIR/INF/monitoring/s1/prometheus/prometheus.yml" "$OPS_DIR/prometheus/prometheus.yml"; then
  prometheus_restart_required="true"
fi

if [ ! -f "$OPS_DIR/blackbox/blackbox.yml" ] || ! cmp -s "$ROOT_DIR/INF/monitoring/s1/blackbox/blackbox.yml" "$OPS_DIR/blackbox/blackbox.yml"; then
  blackbox_restart_required="true"
fi

if [ ! -f "$OPS_DIR/promtail/config.yml" ] || ! cmp -s "$ROOT_DIR/INF/monitoring/s1/promtail/config.yml" "$OPS_DIR/promtail/config.yml"; then
  promtail_restart_required="true"
fi

sync_tree "$ROOT_DIR/INF/monitoring/s1/grafana" "$OPS_DIR/grafana"
sync_tree "$ROOT_DIR/INF/monitoring/s1/prometheus" "$OPS_DIR/prometheus"
sync_tree "$ROOT_DIR/INF/monitoring/s1/promtail" "$OPS_DIR/promtail"
sync_tree "$ROOT_DIR/INF/monitoring/s1/loki" "$OPS_DIR/loki"
sync_tree "$ROOT_DIR/INF/monitoring/s1/blackbox" "$OPS_DIR/blackbox"
install -m 644 "$ROOT_DIR/INF/monitoring/s1/docker-compose.yml" "$OPS_DIR/docker-compose.yml"

if [ ! -f "$JENKINS_DIR/nginx.conf" ] || ! cmp -s "$ROOT_DIR/INF/jenkins/s1/nginx.conf" "$JENKINS_DIR/nginx.conf"; then
  install -m 644 "$ROOT_DIR/INF/jenkins/s1/nginx.conf" "$JENKINS_DIR/nginx.conf"
  proxy_restart_required="true"
fi

docker network create e102-ops >/dev/null 2>&1 || true
ensure_dev_stack_network

"${MONITORING_COMPOSE[@]}" config --quiet
"${MONITORING_COMPOSE[@]}" up -d "${OPS_SERVICES[@]}"

if [ "$proxy_restart_required" = "true" ]; then
  docker restart e102-jenkins-proxy >/dev/null
fi

if [ "$grafana_restart_required" = "true" ]; then
  docker restart e102-grafana >/dev/null
fi

if [ "$prometheus_restart_required" = "true" ]; then
  docker restart e102-prometheus >/dev/null
fi

if [ "$blackbox_restart_required" = "true" ]; then
  docker restart e102-blackbox-exporter >/dev/null
fi

if [ "$promtail_restart_required" = "true" ]; then
  docker restart e102-promtail >/dev/null
fi

echo "S1 monitoring sync complete."
