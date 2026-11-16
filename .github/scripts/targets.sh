# shellcheck shell=bash disable=SC2034
# Everything the release scripts and workflows need to know about this mod. Sourced, never run.
# The scripts and workflows themselves are identical in every Fiw mod repository.

# Modrinth project id or slug. Publishing refuses to run while this is empty.
MODRINTH_PROJECT="BUXFUwPg"
# release, beta or alpha. Anything but release is also a GitHub pre-release.
RELEASE_TYPE="release"
# Name used in release and version titles.
DISPLAY_NAME="Fiw AntiCheat"
# gradle.properties key that holds the mod version (the single version source).
VERSION_KEY="mod_version"
# Kotlin on Modrinth: none, fabric-only (Fabric Language Kotlin on Fabric only) or external
# (also Kotlin for Forge on Forge/NeoForge).
KOTLIN_RUNTIME="none"
# true declares LuckPerms as an optional Modrinth dependency.
OPTIONAL_LUCKPERMS="false"

# Headless client that joins with and without the companion handshake (see verify-target.sh).
PLAYER_SCRIPT=".github/player-tests/anticheat.cjs"
PLAYER_PHASES="first,restart"

# Every released jar, newest Minecraft first: "<module> <modrinth loader> <minecraft version>".
TARGETS=(
  "fabric-26.1.2 fabric 26.1.2"
  "neoforge-26.1.2 neoforge 26.1.2"
  "fabric-1.21.11 fabric 1.21.11"
  "neoforge-1.21.11 neoforge 1.21.11"
  "fabric-1.21.8 fabric 1.21.8"
  "neoforge-1.21.8 neoforge 1.21.8"
  "fabric-1.21.1 fabric 1.21.1"
  "neoforge-1.21.1 neoforge 1.21.1"
  "fabric-1.20.1 fabric 1.20.1"
  "forge-1.20.1 forge 1.20.1"
)

# Path of the built jar of <module> for mod version <version>.
jar_path() {
  echo "$1/build/libs/fiw-anticheat-$1-$2.jar"
}
