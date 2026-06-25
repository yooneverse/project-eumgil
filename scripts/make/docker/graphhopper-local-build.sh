#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
source "$ROOT_DIR/scripts/make/lib/common.sh"

"${LOCAL_COMPOSE[@]}" --profile graphhopper-build run --rm graphhopper-build
