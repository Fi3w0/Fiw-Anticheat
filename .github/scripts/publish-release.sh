#!/usr/bin/env bash
# Publishes the verified jars in release/: every Modrinth version first (ones that already
# exist are skipped, so a failed run can simply be repeated), then the GitHub release and tag.
# usage: publish-release.sh auto|recover
#   auto     after a successful Build on main; does nothing when the version is already tagged
#   recover  manual or tag-driven (re)publish of the checked-out version
# Needs GH_TOKEN and MODRINTH_TOKEN. RELEASE_SHA is the commit to tag (default: HEAD).
set -euo pipefail
# shellcheck source=common.sh
source "$(dirname "$0")/common.sh"
cd "$REPO_ROOT"

mode="${1:?usage: publish-release.sh auto|recover}"
case "$mode" in
  auto|recover) ;;
  *) echo "usage: publish-release.sh auto|recover" >&2; exit 2 ;;
esac

VERSION="$(mod_version)"
if [ -z "$VERSION" ]; then
  echo "::error::$VERSION_KEY not found in gradle.properties" >&2
  exit 1
fi
TAG="v$VERSION"

tag_exists=false
if git ls-remote --exit-code --tags origin "refs/tags/$TAG" >/dev/null 2>&1; then
  tag_exists=true
fi
if [ "$mode" = auto ] && [ "$tag_exists" = true ]; then
  echo "$TAG already exists; nothing to publish. Run the Release workflow to republish it."
  exit 0
fi

: "${GH_TOKEN:?GH_TOKEN is required}"
: "${MODRINTH_TOKEN:?MODRINTH_TOKEN is required to publish $TAG}"

# The artifact is a flat folder; put each jar back where targets.sh expects it.
found="$(find release -maxdepth 1 -name '*.jar' | wc -l | tr -d ' ')"
if [ "$found" -ne "${#TARGETS[@]}" ]; then
  echo "::error::Expected ${#TARGETS[@]} jars in release/, found $found" >&2
  exit 1
fi
for target in "${TARGETS[@]}"; do
  read -r module _ _ <<< "$target"
  jar="$(jar_path "$module" "$VERSION")"
  mkdir -p "$(dirname "$jar")"
  cp "release/$(basename "$jar")" "$jar"
done

bash .github/scripts/release-notes.sh "$VERSION" RELEASE_NOTES.md
VERSION="$VERSION" bash .github/scripts/publish-modrinth.sh

channel=()
if [ "$RELEASE_TYPE" != release ]; then
  channel=(--prerelease)
fi
if gh release view "$TAG" >/dev/null 2>&1; then
  echo "GitHub release $TAG already exists; replacing its jars"
  gh release upload "$TAG" release/*.jar --clobber
else
  placement=(--target "${RELEASE_SHA:-$(git rev-parse HEAD)}")
  if [ "$tag_exists" = true ]; then
    placement=(--verify-tag)
  fi
  gh release create "$TAG" release/*.jar "${placement[@]}" \
    --title "$DISPLAY_NAME $VERSION" --notes-file RELEASE_NOTES.md ${channel[@]+"${channel[@]}"}
fi
