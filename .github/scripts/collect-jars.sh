#!/usr/bin/env bash
# Copies every release jar (see targets.sh) into release/.
set -euo pipefail

VERSION="${VERSION:?VERSION is required}"
source "$(dirname "$0")/targets.sh"

mkdir -p release
for target in "${TARGETS[@]}"; do
  read -r module _ _ <<< "$target"
  jar="$(jar_path "$module" "$VERSION")"
  if [ ! -f "$jar" ]; then
    echo "::error file=$jar::Missing release jar" >&2
    exit 1
  fi
  cp -v "$jar" release/
done
test "$(find release -name '*.jar' | wc -l)" -eq "${#TARGETS[@]}"
ls -la release
