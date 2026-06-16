# Fiw AntiCheat

**Professional mod verification, modpack enforcement, and admin investigation tools for Minecraft servers.**

Fiw AntiCheat helps server owners deal with unwanted client mods without guessing what players are running. On join, the server challenges the client companion mod, records the reported mod list, checks it against configurable rules, and gives admins readable tools for enforcement, rollout, and investigation.

This is built for real server administration: blacklist known unfair mods, enforce an official modpack with a whitelist, monitor detections before kicking, and keep per-player mod profiles so staff can see what changed over time.

---

## Why Fiw AntiCheat?

Most anti-cheat setups focus only on movement or combat behavior. Fiw AntiCheat focuses on **client mod visibility and modpack integrity**:

- Detect common unfair mod categories such as cheat clients, x-ray, fullbright, freecam, autoclickers, and schematic printers
- Enforce an official modpack list for private SMPs, events, staff servers, and modded communities
- Keep readable player mod profiles with noisy loader/API entries separated from real installed mods
- Let admins roll out rules safely with monitor-only mode before kicking players
- Work across Fabric, Forge, and NeoForge targets with one shared rule engine

Fiw AntiCheat is an admin enforcement tool and mod verification layer. It is not magic and does not claim to be cheat-proof: a malicious custom client can lie. It is designed to make honest clients enforceable, catch common unwanted mods, and give staff practical visibility.

---

## Supported Targets

| Loader | Minecraft | Status |
|---|---|---|
| **Fabric** | **1.21.1** | Active |
| **NeoForge** | **1.21.1** | Active |
| **Fabric** | **1.21.11** | Active |
| **NeoForge** | **1.21.11** | Active |
| **Fabric** | **1.20.1** | Active |
| **MinecraftForge** | **1.20.1** | Active |

NeoForge does not publish a real Minecraft 1.20.1 artifact on the current NeoForge Maven, so the 1.20.1 Forge-family build targets MinecraftForge 47.x.

---

## Features

- **Blacklist mode** - allow normal mods while blocking selected unfair categories
- **Whitelist mode** - enforce a captured official modpack list
- **Built-in signature database** - detects known unfair mods by id and stable markers such as packages or mixin configs
- **Admin profiles** - view each player's current mods, platform/loader entries, and recent changes
- **Readable grouping** - Fabric, Forge, NeoForge, API, and loader noise is separated from player-installed mods
- **Monitor-only rollout** - log and alert staff without kicking while tuning your rules
- **Staff alerts** - online operators can see detections as they happen
- **Bypass list** - exempt trusted accounts or test users by name or UUID
- **Floodgate/Geyser support** - optional Bedrock-player exemption when Floodgate is present
- **Hot reload** - reload config and signatures without restarting
- **Server snapshots** - capture the server or an online player as the official whitelist baseline

---

## Commands

All commands require permission level 4.

| Command | Does |
|---|---|
| `/fiwmods reload` | Reload config and bundled signatures |
| `/fiwmods snapshot server` | Capture the server's loaded mods into the whitelist |
| `/fiwmods snapshot player <name>` | Capture an online player's reported mods into the whitelist |
| `/fiwmods profile <name>` | Show grouped current mods and recent profile history |

---

## Configuration

The config is generated at:

```text
config/fiw-mods-api/config.json
```

The internal config folder keeps the original id for compatibility, even though the public mod name is Fiw AntiCheat.

Main options:

| Option | Purpose |
|---|---|
| `mode` | `blacklist` or `whitelist` |
| `detection.preset` | `strict`, `balanced`, `lenient`, or `custom` |
| `detection.monitor_only` | Log detections without kicking |
| `detection.allow_overrides` | Allow specific signatures/categories even if preset blocks them |
| `detection.banned_mods` | Add exact mod ids to block |
| `exemptions.bypass_players` | Bypass specific names or UUIDs |
| `whitelist.official_mods` | Official modpack snapshot used in whitelist mode |

---

## Requirements

| Target | Requirements |
|---|---|
| Fabric 1.20.1 | Fabric Loader, Fabric API, Java 17 |
| MinecraftForge 1.20.1 | Forge 47.x, Java 17 |
| Fabric 1.21.1 | Fabric Loader, Fabric API, Java 21 |
| NeoForge 1.21.1 | NeoForge 21.1.x, Java 21 |
| Fabric 1.21.11 | Fabric Loader 0.19.2+, Fabric API 0.141.4+1.21.11, Java 21 |
| NeoForge 1.21.11 | NeoForge 21.11.x, Java 21 |

Install Fiw AntiCheat on the server and on player clients. The client companion is what reports the mod list to the server.

---

## Best Uses

- Private SMP modpack enforcement
- Event servers where everyone must use the same mod list
- Staff visibility into suspicious or changing client setups
- Blocking common unfair utility mods without locking down harmless QoL mods
- Gradual anti-cheat rollout with monitor-only logging before enforcement

---

## Documentation

Full English documentation ships with the mod in `DOCS_EN.md`: the join
protocol, complete config reference, both modes, the signature database,
commands, per-player profiles, and troubleshooting.

---

## License

Fiw AntiCheat is released under the [Fiw AntiCheat License (Attribution,
Non-Commercial)](LICENSE). You are free to use, modify, fork, and redistribute
it — including modified builds — as long as you give clear, visible credit to the
original creator (**Fi3w0**) with a link back and keep existing notices intact.
You **may not sell it** or any fork without written permission (running it on a
server, even a monetized one, is fine). Fi3w0 retains authorship of the original
work.

Contact: Discord `fi3w0`.

Contact  me: @Fi3w0 on Github/Discord

---

*Made by Fi3w0 — built for a private SMP, shared with everyone.*
