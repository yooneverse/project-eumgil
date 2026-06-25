#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

ENV_FILE="${ENV_FILE:-"$ROOT_DIR/.env.dev"}"
IMAGE_NAME="${BACKEND_DEV_IMAGE:-e102-backend:dev}"
CONTAINER_NAME="${BACKEND_DEV_CONTAINER:-e102-backend-be-dev}"
BE_DEV_TUNNEL_AUTO="${BE_DEV_TUNNEL_AUTO:-true}"
BE_DEV_SSH_HOST="${BE_DEV_SSH_HOST:-ubuntu@s1.internal.example.com}"
BE_DEV_SSH_KEY="${BE_DEV_SSH_KEY:-$ROOT_DIR/K14E102T.pem}"
BE_DEV_DB_LOCAL_PORT="${BE_DEV_DB_LOCAL_PORT:-15432}"
BE_DEV_REDIS_LOCAL_PORT="${BE_DEV_REDIS_LOCAL_PORT:-16379}"
BE_DEV_MINIO_LOCAL_PORT="${BE_DEV_MINIO_LOCAL_PORT:-19000}"
BE_DEV_GRAPHHOPPER_LOCAL_PORT="${BE_DEV_GRAPHHOPPER_LOCAL_PORT:-18989}"
BE_DEV_GRAPHHOPPER_REMOTE_PORT="${BE_DEV_GRAPHHOPPER_REMOTE_PORT:-8998}"
BE_DEV_TUNNEL_PID_FILE="${BE_DEV_TUNNEL_PID_FILE:-$ROOT_DIR/.tmp/be-dev-tunnel.pid}"
DEV_GRAPHHOPPER_DB_URL=""
DEV_GRAPHHOPPER_BUILD_NO_DEPS="true"

server_port() {
  if [ -f "$ENV_FILE" ]; then
    awk -F= '/^SERVER_PORT=/{print $2}' "$ENV_FILE" \
      | tail -n 1 \
      | sed 's/\r$//' \
      | tr -d '"' \
      | tr -d "'"
  fi
}

env_value() {
  local key="$1"
  if [ -f "$ENV_FILE" ]; then
    awk -F= -v key="$key" '$1 == key {print substr($0, length(key) + 2)}' "$ENV_FILE" \
      | tail -n 1 \
      | sed 's/\r$//' \
      | sed 's/^"//; s/"$//' \
      | sed "s/^'//; s/'$//"
  fi
}

ensure_env_file() {
  if [ ! -f "$ENV_FILE" ]; then
    echo "Missing env file: $ENV_FILE" >&2
    exit 1
  fi
}

ensure_docker_daemon() {
  if ! docker info >/dev/null 2>&1; then
    echo "Docker daemon is not reachable. Start Docker Desktop, then run make be-dev-up again." >&2
    exit 1
  fi
}

backend_dev_port() {
  local port
  port="$(server_port)"
  echo "${port:-8080}"
}

dev_db_name() {
  local db_name
  db_name="$(env_value POSTGRES_DB)"
  echo "${db_name:-e102}"
}

resolve_dev_graphhopper_db_url() {
  local configured_db_url
  configured_db_url="$(env_value DB_URL)"

  if [ "$BE_DEV_TUNNEL_AUTO" = "true" ] && [ -f "$BE_DEV_SSH_KEY" ]; then
    ensure_dev_tunnel
    DEV_GRAPHHOPPER_DB_URL="jdbc:postgresql://host.docker.internal:$BE_DEV_DB_LOCAL_PORT/$(dev_db_name)"
    DEV_GRAPHHOPPER_BUILD_NO_DEPS="true"
    return
  fi

  if [ -n "$configured_db_url" ]; then
    if [ "$BE_DEV_TUNNEL_AUTO" = "true" ]; then
      echo "dev SSH key is unavailable; using configured DB_URL for GraphHopper build." >&2
    fi
    DEV_GRAPHHOPPER_DB_URL="$configured_db_url"
    DEV_GRAPHHOPPER_BUILD_NO_DEPS="false"
    return
  fi

  ensure_dev_tunnel
  DEV_GRAPHHOPPER_DB_URL="jdbc:postgresql://host.docker.internal:$BE_DEV_DB_LOCAL_PORT/$(dev_db_name)"
  DEV_GRAPHHOPPER_BUILD_NO_DEPS="true"
}

port_open() {
  local port="$1"
  (echo >"/dev/tcp/127.0.0.1/$port") >/dev/null 2>&1
}

tracked_tunnel_running() {
  if [ ! -f "$BE_DEV_TUNNEL_PID_FILE" ]; then
    return 1
  fi

  local pid
  pid="$(cat "$BE_DEV_TUNNEL_PID_FILE" 2>/dev/null || true)"
  if [ -z "$pid" ]; then
    return 1
  fi

  kill -0 "$pid" >/dev/null 2>&1
}

ensure_dev_tunnel() {
  if [ "$BE_DEV_TUNNEL_AUTO" != "true" ]; then
    return
  fi

  if [ ! -f "$BE_DEV_SSH_KEY" ]; then
    echo "Missing SSH key for dev tunnel: $BE_DEV_SSH_KEY" >&2
    echo "Set BE_DEV_TUNNEL_AUTO=false if you opened tunnels manually." >&2
    exit 1
  fi
  local ssh_args=()
  if ! port_open "$BE_DEV_DB_LOCAL_PORT"; then
    ssh_args+=(-L "127.0.0.1:$BE_DEV_DB_LOCAL_PORT:127.0.0.1:5432")
  fi
  if ! port_open "$BE_DEV_REDIS_LOCAL_PORT"; then
    ssh_args+=(-L "127.0.0.1:$BE_DEV_REDIS_LOCAL_PORT:127.0.0.1:6379")
  fi
  if ! port_open "$BE_DEV_MINIO_LOCAL_PORT"; then
    ssh_args+=(-L "127.0.0.1:$BE_DEV_MINIO_LOCAL_PORT:127.0.0.1:9000")
  fi
  if ! port_open "$BE_DEV_GRAPHHOPPER_LOCAL_PORT"; then
    ssh_args+=(-L "127.0.0.1:$BE_DEV_GRAPHHOPPER_LOCAL_PORT:127.0.0.1:$BE_DEV_GRAPHHOPPER_REMOTE_PORT")
  fi

  if [ "${#ssh_args[@]}" -eq 0 ]; then
    echo "dev tunnel already reachable on local ports: $BE_DEV_DB_LOCAL_PORT, $BE_DEV_REDIS_LOCAL_PORT, $BE_DEV_MINIO_LOCAL_PORT, $BE_DEV_GRAPHHOPPER_LOCAL_PORT"
    return
  fi

  local ssh_key_for_tunnel="$BE_DEV_SSH_KEY"
  local temp_ssh_key=""
  temp_ssh_key="$(mktemp)"
  cp "$BE_DEV_SSH_KEY" "$temp_ssh_key"
  chmod 600 "$temp_ssh_key"
  ssh_key_for_tunnel="$temp_ssh_key"

  echo "opening dev tunnel: local $BE_DEV_DB_LOCAL_PORT->5432, $BE_DEV_REDIS_LOCAL_PORT->6379, $BE_DEV_MINIO_LOCAL_PORT->9000, $BE_DEV_GRAPHHOPPER_LOCAL_PORT->$BE_DEV_GRAPHHOPPER_REMOTE_PORT"
  mkdir -p "$(dirname "$BE_DEV_TUNNEL_PID_FILE")"
  ssh -nN \
    -i "$ssh_key_for_tunnel" \
    -o StrictHostKeyChecking=accept-new \
    -o ExitOnForwardFailure=yes \
    -o ServerAliveInterval=30 \
    -o ServerAliveCountMax=3 \
    "${ssh_args[@]}" \
    "$BE_DEV_SSH_HOST" &
  local tunnel_pid=$!
  echo "$tunnel_pid" >"$BE_DEV_TUNNEL_PID_FILE"

  local port
  for port in "$BE_DEV_DB_LOCAL_PORT" "$BE_DEV_REDIS_LOCAL_PORT" "$BE_DEV_MINIO_LOCAL_PORT" "$BE_DEV_GRAPHHOPPER_LOCAL_PORT"; do
    local opened=false
    for _ in 1 2 3 4 5 6 7 8 9 10; do
      if ! kill -0 "$tunnel_pid" >/dev/null 2>&1; then
        rm -f "$BE_DEV_TUNNEL_PID_FILE"
        rm -f "$temp_ssh_key"
        echo "Failed to start dev tunnel process" >&2
        exit 1
      fi
      if port_open "$port"; then
        opened=true
        break
      fi
      sleep 1
    done
    if [ "$opened" != "true" ]; then
      kill "$tunnel_pid" >/dev/null 2>&1 || true
      rm -f "$BE_DEV_TUNNEL_PID_FILE"
      rm -f "$temp_ssh_key"
      echo "Failed to open dev tunnel on local port: $port" >&2
      exit 1
    fi
  done
  rm -f "$temp_ssh_key"
}

backend_dev_config() {
  ensure_env_file
  local port
  port="$(backend_dev_port)"
  local db_name
  db_name="$(dev_db_name)"
  echo "backend dev image: $IMAGE_NAME"
  echo "backend dev container: $CONTAINER_NAME"
  echo "env file: $ENV_FILE"
  echo "port: $port -> 8080"
  echo "dev tunnel auto: $BE_DEV_TUNNEL_AUTO"
  echo "dev tunnel ssh host: $BE_DEV_SSH_HOST"
  echo "dev DB URL override: jdbc:postgresql://host.docker.internal:$BE_DEV_DB_LOCAL_PORT/$db_name"
  echo "dev Redis override: host.docker.internal:$BE_DEV_REDIS_LOCAL_PORT"
  echo "dev MinIO override: http://host.docker.internal:$BE_DEV_MINIO_LOCAL_PORT"
  echo "dev GraphHopper override: http://host.docker.internal:$BE_DEV_GRAPHHOPPER_LOCAL_PORT"
  echo "dev tunnel pid file: $BE_DEV_TUNNEL_PID_FILE"
}

backend_dev_up() {
  ensure_env_file
  ensure_docker_daemon
  ensure_dev_tunnel
  local port
  port="$(backend_dev_port)"
  local db_name
  db_name="$(dev_db_name)"
  docker build -t "$IMAGE_NAME" "$ROOT_DIR/BE"
  docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true
  docker run -d \
    --name "$CONTAINER_NAME" \
    --add-host host.docker.internal:host-gateway \
    --env-file "$ENV_FILE" \
    -e SPRING_PROFILES_ACTIVE=dev \
    -e SERVER_PORT=8080 \
    -e "DB_URL=jdbc:postgresql://host.docker.internal:$BE_DEV_DB_LOCAL_PORT/$db_name" \
    -e REDIS_HOST=host.docker.internal \
    -e "REDIS_PORT=$BE_DEV_REDIS_LOCAL_PORT" \
    -e "S3_ENDPOINT=http://host.docker.internal:$BE_DEV_MINIO_LOCAL_PORT" \
    -e "S3_BUCKET=$(env_value MINIO_BUCKET)" \
    -e "S3_ACCESS_KEY=$(env_value MINIO_ROOT_USER)" \
    -e "S3_SECRET_KEY=$(env_value MINIO_ROOT_PASSWORD)" \
    -e "S3_REGION=$(env_value MINIO_REGION)" \
    -e "GRAPHHOPPER_BASE_URL=http://host.docker.internal:$BE_DEV_GRAPHHOPPER_LOCAL_PORT" \
    -p "$port:8080" \
    "$IMAGE_NAME"
  echo "backend dev container started. Press Ctrl+C to stop following logs."
  set +e
  docker logs -f "$CONTAINER_NAME"
  local log_status=$?
  set -e
  if [ "$log_status" -ne 0 ] && [ "$log_status" -ne 130 ]; then
    return "$log_status"
  fi
}

backend_dev_down() {
  docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true
  if tracked_tunnel_running; then
    local pid
    pid="$(cat "$BE_DEV_TUNNEL_PID_FILE")"
    kill "$pid" >/dev/null 2>&1 || true
    rm -f "$BE_DEV_TUNNEL_PID_FILE"
    echo "closed tracked dev tunnel: $pid"
  else
    rm -f "$BE_DEV_TUNNEL_PID_FILE"
  fi
}

backend_dev_logs() {
  docker logs -f "$CONTAINER_NAME"
}
