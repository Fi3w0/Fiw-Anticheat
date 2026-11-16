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
# The headless client cannot join every Minecraft version yet (see NO_CLIENT_MINECRAFT in
# targets.sh). Those targets still boot the production jar twice and run the console checks.
minecraft=""
for target in "${TARGETS[@]}"; do
  read -r name _ version <<< "$target"
  if [ "$name" = "$module" ]; then minecraft="$version"; fi
done
if [[ " ${NO_CLIENT_MINECRAFT:-} " == *" $minecraft "* ]]; then
  echo "::notice::$module: no test client for Minecraft $minecraft yet; running the boot and console checks only"
  exec python3 .github/scripts/smoke-test.py "$module" "$jar" --suite-file .github/boot/smoke.json
fi
exec python3 .github/scripts/smoke-test.py "$module" "$jar" \
  --player-script "$PLAYER_SCRIPT" --player-phases "$PLAYER_PHASES"
