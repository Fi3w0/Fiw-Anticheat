#!/usr/bin/env bash
# Shared helpers for the release scripts and workflows. Everything specific to this mod
# (project id, targets, version property, player test) lives in targets.sh.
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# shellcheck source=targets.sh
source "$REPO_ROOT/.github/scripts/targets.sh"

# The mod version, read from the one gradle.properties key named by VERSION_KEY.
mod_version() {
  awk -F= -v key="$VERSION_KEY" '$1 == key { gsub(/[ \t\r]/, "", $2); print $2; exit }' \
    "$REPO_ROOT/gradle.properties"
}

# Every release module as a JSON array, for the workflow test matrix.
target_modules_json() {
  local target module
  for target in "${TARGETS[@]}"; do
    read -r module _ _ <<< "$target"
    printf '%s\n' "$module"
  done | jq -R . | jq -sc .
}
