#!/usr/bin/env bash
set -euo pipefail

S1_SSH_USER="${S1_SSH_USER:-ubuntu}"
S1_SSH_HOST="${S1_SSH_HOST:-s1.internal.example.com}"
S1_SSH_KEY="${S1_SSH_KEY:-K14E102T.pem}"
LOCAL_PORT="${PORTAINER_LOCAL_PORT:-19000}"
REMOTE_HOST="${PORTAINER_REMOTE_HOST:-127.0.0.1}"
REMOTE_PORT="${PORTAINER_REMOTE_PORT:-19000}"

if [[ ! -f "$S1_SSH_KEY" ]]; then
  echo "S1 SSH key not found: $S1_SSH_KEY" >&2
  echo "Set S1_SSH_KEY=/path/to/key.pem or place K14E102T.pem in the repository root." >&2
  exit 1
fi

echo "Opening Portainer tunnel: http://localhost:${LOCAL_PORT}"
echo "Press Ctrl+C to close the tunnel."

exec ssh \
  -i "$S1_SSH_KEY" \
  -N \
  -L "${LOCAL_PORT}:${REMOTE_HOST}:${REMOTE_PORT}" \
  -o ExitOnForwardFailure=yes \
  -o ServerAliveInterval=30 \
  -o ServerAliveCountMax=3 \
  "${S1_SSH_USER}@${S1_SSH_HOST}"
