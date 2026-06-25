#!/usr/bin/env bash
set -euo pipefail

ENV_FILE="${ENV_FILE:-./.env}"
SONAR_CONTAINER="${SONAR_CONTAINER:-e102-sonarqube}"
SONAR_DB_CONTAINER="${SONAR_DB_CONTAINER:-e102-sonarqube-db}"
SONAR_URL="${SONAR_URL:-http://localhost:9000}"
SONAR_PUBLIC_URL="${SONAR_PUBLIC_URL:-https://sonarqube.busaneumgil.com}"
GITLAB_BASE_URL="${GITLAB_BASE_URL:-https://git.example.com}"
GITLAB_ALLOWED_GROUPS="${GITLAB_ALLOWED_GROUPS:-}"
DOCKER_BIN="${DOCKER_BIN:-sudo docker}"
SONAR_RESTART_AFTER_DB_SETTING="${SONAR_RESTART_AFTER_DB_SETTING:-true}"

if [ -f "$ENV_FILE" ]; then
  set -a
  # shellcheck disable=SC1090
  . "$ENV_FILE"
  set +a
fi

required_vars=(
  SONAR_ADMIN_USER
  SONAR_ADMIN_PASSWORD
  SONAR_POSTGRES_PASSWORD
  SONAR_GITLAB_OAUTH_CLIENT_ID
  SONAR_GITLAB_OAUTH_CLIENT_SECRET
)

for var_name in "${required_vars[@]}"; do
  if [ -z "${!var_name:-}" ]; then
    echo "Missing required env: ${var_name}" >&2
    exit 1
  fi
done

curl_sonar() {
  # DOCKER_BIN은 "docker" 또는 "sudo docker" 형태를 지원한다.
  read -r -a docker_cmd <<< "$DOCKER_BIN"
  "${docker_cmd[@]}" exec "$SONAR_CONTAINER" curl -fsS \
    -u "${SONAR_ADMIN_USER}:${SONAR_ADMIN_PASSWORD}" \
    "$@"
}

set_setting() {
  local key="$1"
  local value="$2"

  curl_sonar -X POST "${SONAR_URL}/api/settings/set" \
    --data-urlencode "key=${key}" \
    --data-urlencode "value=${value}" >/dev/null
}

set_multivalue_setting() {
  local key="$1"
  local value="$2"

  curl_sonar -X POST "${SONAR_URL}/api/settings/set" \
    --data-urlencode "key=${key}" \
    --data-urlencode "values=${value}" >/dev/null
}

set_db_setting() {
  local key="$1"
  local value="$2"
  local uuid
  local created_at

  uuid="$(python3 - <<'PY'
import uuid
print(uuid.uuid4())
PY
)"
  created_at="$(python3 - <<'PY'
import time
print(int(time.time() * 1000))
PY
)"

  read -r -a docker_cmd <<< "$DOCKER_BIN"
  "${docker_cmd[@]}" exec -i -e "PGPASSWORD=${SONAR_POSTGRES_PASSWORD}" "$SONAR_DB_CONTAINER" \
    psql -U sonar -d sonarqube -v ON_ERROR_STOP=1 >/dev/null <<SQL
INSERT INTO properties (uuid, prop_key, is_empty, text_value, clob_value, created_at, entity_uuid, user_uuid)
VALUES ('${uuid}', '${key}', false, '${value}', null, ${created_at}, null, null)
ON CONFLICT (prop_key, entity_uuid, user_uuid) DO UPDATE SET
  is_empty = EXCLUDED.is_empty,
  text_value = EXCLUDED.text_value,
  clob_value = EXCLUDED.clob_value;
SQL
}

set_setting "sonar.auth.gitlab.enabled" "true"
set_setting "sonar.auth.gitlab.applicationId.secured" "$SONAR_GITLAB_OAUTH_CLIENT_ID"
set_setting "sonar.auth.gitlab.secret.secured" "$SONAR_GITLAB_OAUTH_CLIENT_SECRET"
set_setting "sonar.auth.gitlab.allowUsersToSignUp" "true"
set_setting "sonar.auth.gitlab.groupsSync" "true"
if [ -n "$GITLAB_ALLOWED_GROUPS" ]; then
  set_multivalue_setting "sonar.auth.gitlab.allowedGroups" "$GITLAB_ALLOWED_GROUPS"
else
  curl_sonar -X POST "${SONAR_URL}/api/settings/reset" \
    --data-urlencode "keys=sonar.auth.gitlab.allowedGroups" >/dev/null
fi
set_setting "sonar.core.serverBaseURL" "$SONAR_PUBLIC_URL"

# SonarQube 10.7 blocks sonar.auth.gitlab.url through the legacy settings API.
# Keep this single setting in sync through the local SonarQube PostgreSQL store.
set_db_setting "sonar.auth.gitlab.url" "$GITLAB_BASE_URL"

if [ "$SONAR_RESTART_AFTER_DB_SETTING" = "true" ]; then
  read -r -a docker_cmd <<< "$DOCKER_BIN"
  "${docker_cmd[@]}" restart "$SONAR_CONTAINER" >/dev/null

  for _ in $(seq 1 60); do
    if curl_sonar "${SONAR_URL}/api/system/status" 2>/dev/null | grep -q '"status":"UP"'; then
      break
    fi
    sleep 5
  done
fi

curl_sonar "${SONAR_URL}/api/settings/values?keys=sonar.auth.gitlab.enabled,sonar.auth.gitlab.url,sonar.auth.gitlab.allowUsersToSignUp,sonar.auth.gitlab.groupsSync,sonar.auth.gitlab.allowedGroups"
echo
echo "SonarQube GitLab OAuth settings applied."
