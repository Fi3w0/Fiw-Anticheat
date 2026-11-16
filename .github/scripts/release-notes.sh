#!/usr/bin/env bash
# Writes the CHANGELOG.md section of one version to a file. The heading must look like
# `## [X.Y.Z] - Title`; a version without a section fails instead of publishing empty notes.
# usage: release-notes.sh [version] [output-file]
set -euo pipefail
# shellcheck source=common.sh
source "$(dirname "$0")/common.sh"
cd "$REPO_ROOT"

version="${1:-$(mod_version)}"
output="${2:-RELEASE_NOTES.md}"
awk -v ver="$version" '
  /^## \[/ { if (found) exit; if (index($0, "[" ver "]")) found = 1 }
  found { print }
' CHANGELOG.md > "$output"
if [ ! -s "$output" ]; then
  echo "::error file=CHANGELOG.md::No '## [$version] - ...' section found" >&2
  exit 1
fi
