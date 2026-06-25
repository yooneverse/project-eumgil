#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

"$ROOT_DIR/.ai/scripts/update-progress.sh"
"$ROOT_DIR/.ai/scripts/update-metrics.sh"
"$ROOT_DIR/.ai/scripts/sync-adapters.sh"
"$ROOT_DIR/.ai/scripts/verify.sh"
"$ROOT_DIR/.ai/scripts/score.sh"
"$ROOT_DIR/.ai/scripts/dashboard.sh"
