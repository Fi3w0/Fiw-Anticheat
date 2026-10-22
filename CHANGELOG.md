# Changelog

All notable changes to **Fiw AntiCheat** are listed here.

## [2.1.2] - Security fixes for the join check

Security and bug-fix update. The network protocol is unchanged.

> **⚠ Upgrade note:** use the same Fiw AntiCheat version on the server and on players' clients.

### Fixed
- A verification reply with the wrong nonce now disconnects the player. Before, it unfroze them and skipped the mod check, and the join timeout no longer applied. Replies that arrive after `timeout_seconds` are rejected with the timeout message instead of being accepted.
- `/fiwmods snapshot player <name>` is refused while that player is still being verified on join. Before, it replaced the join check, so the player was unfrozen without a mod check and their reported mods became the whitelist.
- An unanswered `/fiwmods snapshot player` request no longer kicks the player with the timeout message (e.g. a Bedrock or bypassed player without the companion mod).
- Resource-pack updates from a client are limited to one every 5 seconds per player, so a client spamming them can't cause constant profile writes on the server.
- Mod ids, versions and resource-pack names reported by clients have line breaks, control characters and `§` colour codes removed, so they can't add fake lines to the server log or fake text in `/fiwmods profile`.

### Notes
- Thanks to Mickynuts ([@araminimichael](https://github.com/araminimichael)) for privately reporting the wrong-nonce issue.

## [2.1.1] - Safer saves, stricter package matching, exemption list fixes

Maintenance and bug-fix update. If something breaks or behaves differently than before, please report it on the [issue tracker](https://github.com/Fi3w0/Fiw-Anticheat/issues).

> **⚠ Upgrade note:** use the same Fiw AntiCheat version on the server and on players' clients.

### Changed
- Config and player profiles are saved to a temporary file first and then swapped in, so a failed or interrupted save keeps the previous file. Storage that can't swap files atomically falls back to a normal replace.

### Fixed
- Bundled signature package rules no longer match unrelated longer names (a rule for `com.foo` matched `com.foobar`); they match the exact package or a sub-package, case-insensitively on every server locale.
- `/fiwmods exempt list` hides expired grants right away, labels expiry times as UTC on every loader, and its active count only includes unexpired, permanent and legacy bypass entries.

### Documentation
- English and Spanish guides now match: bundled signatures, what `/fiwmods reload` re-reads, preset-tier fields, which data is collected and stored, and why there is no 1.21.8 build. Existing `bypass_players` entries keep working alongside tiers.

## [2.1.0] - Tiered per-player exemptions

### Added

- Added `/fiwmods exempt add <player> <tier> [hours] [preset]`, `/fiwmods exempt
  remove <player>`, and `/fiwmods exempt list` commands for granting, revoking,
  and reviewing per-player exemption tiers.
- Added six exemption tiers, replacing the old all-or-nothing bypass list:
  - `bypass` - skips scanning entirely and always allows the player to join
    (the original bypass behavior).
  - `silent` - still scanned and profiled, but never kicked and never alerts
    staff; only a quiet console log line records what they're running.
  - `monitor` - still scanned, profiled, and alerts staff, but never kicked.
  - `quiet_kick` - normal detection and kick behavior, but the staff chat
    alert is suppressed.
  - `preset:<strict|balanced|lenient|custom>` - evaluates that one player
    against a pinned preset instead of the server's global preset.
  - `force_block` - always kicked on join, before any scan, for repeat
    offenders regardless of what mod list they report.
- Added optional expiry (`[hours]`) on any granted exemption; expired grants
  are purged automatically the next time that player is evaluated.
- Added `exemptions.escalation_rules` config: admin-defined rules that
  auto-apply a tier (e.g. `force_block` for 4 hours) once a player racks up a
  configured number of detections within a time window. An escalation never
  overrides a tier an admin already granted.
- Added `exemptions.player_overrides` config map backing the new tiers
  (`exemptions.bypass_players` is unchanged and still works for permanent
  full bypasses).

### Changed

- The legacy `exemptions.bypass_players` list is still honored as a permanent
  `bypass` grant; new grants from `/fiwmods exempt add` are written to
  `exemptions.player_overrides` instead.
- Floodgate/Geyser auto-exemption now resolves to the `bypass` tier internally
  (same behavior as before, just expressed through the new tier system).

## [2.0.2] - Resource pack auditing

### Added

- Added companion-client reporting for active and inactive resource packs.
- Added resource pack profile history, including added, removed, enabled,
  disabled, and fingerprint-updated events.
- Added `/fiwmods profile <name>` output sections for active and inactive
  resource packs.
- Added `resource_packs` config for log-only auditing by default, with optional
  banned pack name/id and SHA-256 fingerprint checks.
- Added live resource-pack update reporting after join so active/inactive/delete
  changes during a session are recorded when the companion client observes them.

### Changed

- Join verification responses now include resource pack data alongside the mod
  list and nonce.
- Existing config files are not reset on upgrade; missing `resource_packs`
  values are filled with defaults in memory and appear in newly generated
  configs or future saves.

## [2.0.1] - Expanded cheat signature database

### Changed

- Expanded bundled signatures for common hacked clients and aliases, including
  Wurst, Meteor/addons, LiquidBounce, BleachHack, MatHax, ThunderHack, Aoba,
  ForgeHax, Sigma, Jigsaw, Huzuni, Wolfram, Flux, Vape, SalHack, KAMI/Lambda,
  Future/RusherHack-style clients, and mod-detection bypass tools.
- Expanded x-ray detection aliases for Advanced XRay, XRay Ultimate, seed
  cracker/mapper tools, cave finder names, and common x-ray jar naming variants.
- Expanded fullbright/gamma detection aliases for Gamma Utils, true/dynamic
  fullbright, resource gamma utilities, boosted brightness, and night-vision
  utility naming variants.
- Expanded freecam detection aliases for Freecam, Easy Freecam, FreeCamMC,
  Aether Freecam, FPV Freecam, Free Camera, FToFreecam, and Fair Freecam.
- Expanded autoclicker, schematic printer, and damage-indicator aliases.

### Fixed

- Added `flyhack` / `fly-hack` to cheat-client signatures after the NeoForge
  1.21.11 smoke test showed the client mod list was received but that alias was
  not yet blocked.

## [2.0.0] - Initial public multi-loader release

Fiw AntiCheat is a professional mod verification and admin enforcement tool for Minecraft servers. It helps admins detect unwanted client mods, enforce official modpacks, and inspect readable player mod profiles.

### Added

- Multi-loader support for:
  - Fabric 1.20.1
  - MinecraftForge 1.20.1
  - Fabric 1.21.1
  - NeoForge 1.21.1
  - Fabric 1.21.11
  - NeoForge 1.21.11
- Whitelist mode for enforcing a captured official modpack list.
- Client challenge/response verification with nonce validation.
- Client mod reporting with ids, versions, fingerprints, and stable code markers.
- Bundled signatures for common unfair categories including cheat clients, x-ray, fullbright, freecam, autoclickers, and schematic printers.
- Admin commands:
  - `/fiwmods reload`
  - `/fiwmods snapshot server`
  - `/fiwmods snapshot player <name>`
  - `/fiwmods profile <name>`
- Per-player profile storage with current mods and recent change history.
- Readable profile grouping that separates real player-installed mods from Fabric, Forge, NeoForge, loader, API, and platform entries.
- Colored in-game profile output for easier admin scanning.
- Monitor-only mode for logging detections without kicking.
- Staff alerts for online operators.
- Bypass list for trusted users or test accounts.
- Optional Floodgate/Geyser Bedrock-player exemption by reflection.
- Mod icon metadata for Fabric, Forge, and NeoForge mod menus and launchers.

### Notes

- The public mod name is now **Fiw AntiCheat**.
- The project uses the Fiw AntiCheat License (Attribution, Non-Commercial): use,
  modify, fork, and redistribution are allowed as long as clear credit is given
  to the original creator (Fi3w0) and existing notices are kept intact; selling
  the mod or any fork requires written permission.
- Internal ids and config paths remain compatible with the original `fiw-mods-api` / `fiw_mods_api` identifiers.
- NeoForge does not publish a real Minecraft 1.20.1 artifact on the current NeoForge Maven, so the 1.20.1 Forge-family build targets MinecraftForge 47.x.

### Requirements

- Fabric 1.20.1: Fabric Loader, Fabric API, Java 17
- MinecraftForge 1.20.1: Forge 47.x, Java 17
- Fabric 1.21.1: Fabric Loader, Fabric API, Java 21
- NeoForge 1.21.1: NeoForge 21.1.x, Java 21
- Fabric 1.21.11: Fabric Loader 0.19.2+, Fabric API 0.141.4+1.21.11, Java 21
- NeoForge 1.21.11: NeoForge 21.11.x, Java 21
- Install on both server and client for mod reporting.
