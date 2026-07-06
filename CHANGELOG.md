# Changelog

All notable changes to **Fiw AntiCheat** are listed here.

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
