#!/usr/bin/env bash
# Works out what the Release workflow should build: the tag when it already exists, otherwise
# the checked-out commit. Fails early when the requested version is not the project version.
# Reads REQUESTED (a tag or version, for example v1.2.0 or 1.2.0) and writes `ref` to
# GITHUB_OUTPUT.
set -euo pipefail
# shellcheck source=common.sh
source "$(dirname "$0")/common.sh"
cd "$REPO_ROOT"

requested="${REQUESTED:?REQUESTED is required}"
requested="${requested#refs/tags/}"
version="${requested#v}"
tag="v$version"

if git ls-remote --exit-code --tags origin "refs/tags/$tag" >/dev/null 2>&1; then
  git fetch --quiet --depth=1 origin "refs/tags/$tag"
  git checkout --quiet --detach FETCH_HEAD
  ref="$tag"
else
  echo "$tag does not exist yet; building the checked-out commit"
  ref="$(git rev-parse HEAD)"
fi

actual="$(mod_version)"
if [ "$actual" != "$version" ]; then
  echo "::error::gradle.properties $VERSION_KEY is '$actual', but '$version' was requested" >&2
  exit 1
fi
echo "ref=$ref" >> "${GITHUB_OUTPUT:-/dev/stdout}"
