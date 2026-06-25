#!/usr/bin/env bash
set -euo pipefail

EXPECTED_FINGERPRINT="${1:?expected fingerprint is required}"
CACHE_VOLUME="${2:?docker volume name is required}"
FINGERPRINT_FILE="${GRAPHHOPPER_CACHE_FINGERPRINT_FILE:-.ieum-graphhopper-cache-fingerprint}"
TIMESTAMP_FILE="${GRAPHHOPPER_CACHE_TIMESTAMP_FILE:-.ieum-graphhopper-cache-built-at}"

docker volume inspect "$CACHE_VOLUME" >/dev/null 2>&1

docker run --rm \
  -e EXPECTED_FINGERPRINT="$EXPECTED_FINGERPRINT" \
  -e FINGERPRINT_FILE="$FINGERPRINT_FILE" \
  -e TIMESTAMP_FILE="$TIMESTAMP_FILE" \
  -v "$CACHE_VOLUME:/graphhopper/data" \
  alpine:3.20 sh -ceu '
    [ -s "/graphhopper/data/$FINGERPRINT_FILE" ]
    [ "$(cat "/graphhopper/data/$FINGERPRINT_FILE")" = "$EXPECTED_FINGERPRINT" ]
    test -n "$(find /graphhopper/data -mindepth 1 -maxdepth 1 \
      ! -name "$FINGERPRINT_FILE" \
      ! -name "$TIMESTAMP_FILE" \
      2>/dev/null)"
  '
