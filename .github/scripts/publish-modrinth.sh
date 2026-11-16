#!/usr/bin/env bash
# Publishes every release jar (see targets.sh) to Modrinth, skipping ones that already
# exist, then syncs the project description from MODRINTH.md.
set -euo pipefail

API_BASE="${API_BASE:-https://api.modrinth.com/v2}"
VERSION="${VERSION:?VERSION is required}"
CHANGELOG_FILE="${CHANGELOG_FILE:-RELEASE_NOTES.md}"
DESCRIPTION_FILE="${DESCRIPTION_FILE:-MODRINTH.md}"
MODRINTH_TOKEN="${MODRINTH_TOKEN:?MODRINTH_TOKEN is required}"

# Provides project metadata, the release channel and the target matrix.
source "$(dirname "$0")/targets.sh"
case "$RELEASE_TYPE" in
  release|beta|alpha) ;;
  *) echo "::error::RELEASE_TYPE must be release, beta or alpha (got '$RELEASE_TYPE')" >&2; exit 1 ;;
esac
if [ -z "$MODRINTH_PROJECT" ]; then
  echo "::error::MODRINTH_PROJECT is not set in .github/scripts/targets.sh" >&2
  exit 1
fi

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required" >&2
  exit 1
fi
if [ ! -s "$CHANGELOG_FILE" ]; then
  echo "$CHANGELOG_FILE does not exist or is empty" >&2
  exit 1
fi

tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT
auth_header="Authorization: $MODRINTH_TOKEN"

request() {
  local response_file="$1"
  shift
  local http_code
  http_code="$(curl -sS -o "$response_file" -w '%{http_code}' "$@")"
  if [ "$http_code" -ge 400 ]; then
    echo "::error::Modrinth request failed with HTTP $http_code: $(cat "$response_file")" >&2
    return 1
  fi
}

# Modrinth wants base62 ids (not slugs) in version payloads; dependencies are looked up by slug.
project_id() {
  local response_file="$tmp_dir/project-$1.json"
  request "$response_file" -H "$auth_header" "$API_BASE/project/$1"
  jq -er '.id' "$response_file"
}

PROJECT_ID="$(project_id "$MODRINTH_PROJECT")"
FABRIC_API_ID="$(project_id fabric-api)"
FABRIC_KOTLIN_ID="$(project_id fabric-language-kotlin)"
KOTLIN_FOR_FORGE_ID="$(project_id kotlin-for-forge)"
LUCKPERMS_ID="$(project_id luckperms)"
echo "Publishing to Modrinth project $MODRINTH_PROJECT ($PROJECT_ID)"

versions_file="$tmp_dir/project-versions.json"
request "$versions_file" -H "$auth_header" "$API_BASE/project/$PROJECT_ID/version"

modrinth_version_exists() {
  jq -e \
    --arg version_number "$VERSION" \
    --arg game_version "$1" \
    --arg loader "$2" \
    '.[] | select(
      .version_number == $version_number
      and (.game_versions | index($game_version))
      and (.loaders | index($loader))
    )' "$versions_file" >/dev/null
}

dependencies_for() {
  jq -n --arg loader "$1" --arg runtime "$KOTLIN_RUNTIME" --arg optional_lp "$OPTIONAL_LUCKPERMS" \
    --arg api "$FABRIC_API_ID" --arg kotlin "$FABRIC_KOTLIN_ID" \
    --arg kff "$KOTLIN_FOR_FORGE_ID" --arg lp "$LUCKPERMS_ID" '[
      if $loader == "fabric" then {project_id:$api, dependency_type:"required"} else empty end,
      if $loader == "fabric" and $runtime != "none" then {project_id:$kotlin, dependency_type:"required"} else empty end,
      if $loader != "fabric" and $runtime == "external" then {project_id:$kff, dependency_type:"required"} else empty end,
      if $optional_lp == "true" then {project_id:$lp, dependency_type:"optional"} else empty end
    ]'
}

loader_label() {
  case "$1" in
    fabric) echo "Fabric" ;;
    neoforge) echo "NeoForge" ;;
    forge) echo "Forge" ;;
    *) echo "$1" ;;
  esac
}

for target in "${TARGETS[@]}"; do
  read -r module loader game_version <<< "$target"
  jar="$(jar_path "$module" "$VERSION")"
  label="$(loader_label "$loader") $game_version"
  if [ ! -f "$jar" ]; then
    echo "::error file=$jar::Missing jar for Modrinth publish" >&2
    exit 1
  fi
  if modrinth_version_exists "$game_version" "$loader"; then
    echo "Modrinth $VERSION ($label) already exists; skipping"
    continue
  fi

  data_file="$tmp_dir/$module.json"
  jq -n \
    --rawfile changelog "$CHANGELOG_FILE" \
    --arg name "$DISPLAY_NAME $VERSION ($label)" \
    --arg version_number "$VERSION" \
    --arg project_id "$PROJECT_ID" \
    --arg game_version "$game_version" \
    --arg loader "$loader" \
    --arg version_type "$RELEASE_TYPE" \
    --argjson dependencies "$(dependencies_for "$loader")" \
    '{
      name: $name,
      version_number: $version_number,
      changelog: $changelog,
      dependencies: $dependencies,
      game_versions: [$game_version],
      version_type: $version_type,
      loaders: [$loader],
      featured: true,
      status: "listed",
      requested_status: "listed",
      project_id: $project_id,
      file_parts: ["file"],
      primary_file: "file"
    }' > "$data_file"

  echo "Publishing $VERSION ($label, $RELEASE_TYPE)"
  request "$tmp_dir/$module-response.json" -X POST "$API_BASE/version" \
    -H "$auth_header" \
    -F "data=@$data_file;type=application/json" \
    -F "file=@$jar"
done

if [ -s "$DESCRIPTION_FILE" ]; then
  description_payload="$tmp_dir/project-description.json"
  jq -n --rawfile body "$DESCRIPTION_FILE" '{body: $body}' > "$description_payload"
  echo "Syncing Modrinth description from $DESCRIPTION_FILE"
  request "$tmp_dir/project-description-response.json" -X PATCH "$API_BASE/project/$PROJECT_ID" \
    -H "$auth_header" \
    -H "Content-Type: application/json" \
    --data-binary "@$description_payload"
fi
