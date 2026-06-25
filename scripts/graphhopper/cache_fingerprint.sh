#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${1:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"

cd "$ROOT_DIR"

inputs=(
  "INF/graphhopper/Dockerfile"
  "INF/graphhopper/config-build.yml"
  "INF/graphhopper/config-runtime.yml"
  "INF/graphhopper/custom_models"
  "INF/graphhopper/plugin/src"
  "scripts/graphhopper/export_postgis_to_osm.py"
)

hash_lines=()
for input in "${inputs[@]}"; do
  if [ -d "$input" ]; then
    while IFS= read -r file; do
      hash_lines+=("$(sha256sum "$file")")
    done < <(find "$input" -type f | LC_ALL=C sort)
  elif [ -f "$input" ]; then
    hash_lines+=("$(sha256sum "$input")")
  else
    echo "missing GraphHopper fingerprint input: $input" >&2
    exit 1
  fi
done

printf '%s\n' "${hash_lines[@]}" | sha256sum | awk '{print $1}'
