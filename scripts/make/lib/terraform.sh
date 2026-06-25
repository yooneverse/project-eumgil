#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
TERRAFORM_BOOTSTRAP_DIR="$ROOT_DIR/INF/terraform/bootstrap"
TERRAFORM_PROD_DIR="$ROOT_DIR/INF/terraform/envs/prod"
TERRAFORM_ENV_FILE="$ROOT_DIR/.env.terraform"
TERRAFORM_BIN="${TERRAFORM_BIN:-terraform}"

load_terraform_env() {
  if [ -f "$TERRAFORM_ENV_FILE" ]; then
    while IFS= read -r line || [ -n "$line" ]; do
      line="${line#"${line%%[![:space:]]*}"}"
      line="${line%"${line##*[![:space:]]}"}"

      if [ -z "$line" ] || [[ "$line" == \#* ]] || [[ "$line" != *=* ]]; then
        continue
      fi

      key="${line%%=*}"
      value="${line#*=}"
      key="${key%"${key##*[![:space:]]}"}"
      value="${value#"${value%%[![:space:]]*}"}"
      value="${value%"${value##*[![:space:]]}"}"

      if [[ "$key" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
        export "$key=$value"
      fi
    done < "$TERRAFORM_ENV_FILE"
  fi
}

require_terraform() {
  if ! command -v "$TERRAFORM_BIN" >/dev/null 2>&1; then
    echo "terraform command not found. Install Terraform first or set TERRAFORM_BIN." >&2
    exit 1
  fi
}

terraform_prod() {
  require_terraform
  load_terraform_env
  cd "$TERRAFORM_PROD_DIR"
  "$TERRAFORM_BIN" "$@"
}

terraform_bootstrap() {
  require_terraform
  load_terraform_env
  cd "$TERRAFORM_BOOTSTRAP_DIR"
  "$TERRAFORM_BIN" "$@"
}
