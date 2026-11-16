#!/usr/bin/env bash
# Boots the production jar of one release target with its real runtime dependencies, drives a
# headless vanilla-protocol client through the gameplay checks and restarts the server.
# usage: verify-target.sh <module> [jar-dir]
#   jar-dir  flat folder holding the jar (CI passes release/); default is the module's build/libs
set -euo pipefail
# shellcheck source=common.sh
source "$(dirname "$0")/common.sh"
cd "$REPO_ROOT"

module="${1:?usage: verify-target.sh <module> [jar-dir]}"
jar="$(jar_path "$module" "$(mod_version)")"
if [ -n "${2:-}" ]; then
  jar="$2/$(basename "$jar")"
fi
exec python3 .github/scripts/smoke-test.py "$module" "$jar" \
  --player-script "$PLAYER_SCRIPT" --player-phases "$PLAYER_PHASES"
