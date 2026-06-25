#!/usr/bin/env bash
set -euo pipefail

ADMIN_DOMAIN="${ADMIN_DOMAIN:-admin.busaneumgil.com}"
ADMIN_PORT="${ADMIN_PORT:-3001}"
ISSUE_CERT="${ISSUE_CERT:-true}"
CERTBOT_EMAIL="${CERTBOT_EMAIL:-}"
SITE_FILE="${SITE_FILE:-/etc/nginx/sites-available/eumgil-admin}"
ENABLED_FILE="${ENABLED_FILE:-/etc/nginx/sites-enabled/eumgil-admin}"

tmp_file="$(mktemp)"
trap 'rm -f "$tmp_file"' EXIT

cat >"$tmp_file" <<NGINX
server {
  listen 80;
  server_name ${ADMIN_DOMAIN};

  location / {
    proxy_pass http://127.0.0.1:${ADMIN_PORT};
    proxy_http_version 1.1;
    proxy_set_header Host \$host;
    proxy_set_header X-Real-IP \$remote_addr;
    proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto \$scheme;
  }
}
NGINX

sudo install -m 0644 "$tmp_file" "$SITE_FILE"
sudo ln -sf "$SITE_FILE" "$ENABLED_FILE"
sudo nginx -t
sudo systemctl reload nginx

scheme="http"

if [ "$ISSUE_CERT" = "true" ]; then
  certbot_args=(--nginx -d "$ADMIN_DOMAIN" --redirect --non-interactive --agree-tos)
  if [ -n "$CERTBOT_EMAIL" ]; then
    certbot_args+=(--email "$CERTBOT_EMAIL")
  else
    certbot_args+=(--register-unsafely-without-email)
  fi
  sudo certbot "${certbot_args[@]}"
  sudo nginx -t
  sudo systemctl reload nginx
  scheme="https"
fi

echo "admin ingress ready: ${scheme}://${ADMIN_DOMAIN} -> 127.0.0.1:${ADMIN_PORT}"
