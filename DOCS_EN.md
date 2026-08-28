# Fiw AntiCheat — Full Documentation (English)

A server-side **mod verification and enforcement** tool for Minecraft. On join,
the server challenges the client; a client running the companion mod reports its
loaded mods (id, version, SHA-256 jar fingerprint, and stable code markers); the
client also reports active and inactive resource packs for staff auditing. The
server evaluates that report and either lets the player in or disconnects them.

> **Honest threat model — read this first.**
> This is a *modpack/mod enforcer*, not a cryptographically unbreakable
> anti-cheat. The report is **client-reported** and the companion mod is trusted.
> A determined attacker who decompiles and edits the companion mod can lie about
> their mod or resource-pack list — and **no client-side system can fully prevent
> that**, because the client holds every secret involved. What this mod *does*
> reliably defeat is the casual tier: jar renames, mod-id swaps, version bumps,
> impersonating an allowed mod, replaying packets, "just don't install it", and
> common public x-ray/fullbright packs left installed under obvious names. For a hardened
> setup, pair it with a controlled launcher (signed packs) and a **server-side**
> behavioural anti-cheat for injection/DLL-style cheats.

---

## 1. Supported targets

The mod is one shared engine plus a thin adapter per loader/version. Pick the
**one jar** that matches your server's loader and Minecraft version.

| Minecraft | Loader | Java | Jar |
|---|---|---:|---|
| 1.20.1 | Fabric | 17 | `fiw-anticheat-fabric-1.20.1-<version>.jar` |
| 1.20.1 | MinecraftForge 47.x | 17 | `fiw-anticheat-forge-1.20.1-<version>.jar` |
| 1.21.1 | Fabric | 21 | `fiw-anticheat-fabric-1.21.1-<version>.jar` |
| 1.21.1 | NeoForge | 21 | `fiw-anticheat-neoforge-1.21.1-<version>.jar` |
| 1.21.11 | Fabric | 21 | `fiw-anticheat-fabric-1.21.11-<version>.jar` |
| 1.21.11 | NeoForge | 21 | `fiw-anticheat-neoforge-1.21.11-<version>.jar` |

NeoForge does not publish a Minecraft **1.20.1** build on its current Maven
(earliest is 1.20.2), so the 1.20.1 Forge-family target uses **MinecraftForge
47.x** instead.

---

## 2. Requirements

- Every **Java** player must have the matching companion jar (ship it inside your
  modpack / launcher so players don't install it manually).
- Loader dependencies:
  - Fabric → Fabric Loader + Fabric API.
  - Forge 1.20.1 → MinecraftForge 47.x.
  - NeoForge → the NeoForge version matching the target.
- **Bedrock players via Geyser/Floodgate are exempt automatically** (they can't
  run Java mods), so they are never kicked.

---

## 3. How it works (protocol)

All work happens **once per join**, never per tick.

1. **Join → freeze + challenge.** The server freezes the player (movement and
   interactions are blocked, they're snapped back if they move) and sends a
   32-byte random **nonce**.
2. **Client report.** The companion mod enumerates every loaded mod and replies
   with, per mod:
   - `id` — loader mod id
   - `version` — version string from mod metadata
   - `fingerprint` — **SHA-256 of the mod's jar file**
   - `markers` — stable identity signals: root packages + declared mixin config
     names (these survive a jar rename *and* an internal mod-id rename)
   It also reports resource packs:
   - `id` — pack id, usually `file/<filename>` for local packs
   - `name` — readable pack name/filename for staff output
   - `fingerprint` — SHA-256 of the zip or directory content when available
   - `active` — whether the pack is currently enabled
   - plus the **nonce echoed back**
3. **Server evaluation** (see §6). The result is PASS or KICK.
   - PASS → unfreeze.
   - KICK → disconnect with a generic message; the **real** reason/mod is logged
     to console and (optionally) sent to online staff.
   - No reply within `timeout_seconds` → kicked with the timeout message.
4. **Exemptions** (Floodgate Bedrock + bypass list) skip the whole process.

After the join report, the companion client periodically re-scans resource packs
while online. If the active/inactive pack set changes, it sends a pack-only
update so the profile can record added, enabled, disabled, removed, and changed
pack events during the same session.

The per-join nonce is **anti-replay / session-bound** (an off-session third
party can't forge a valid response and old responses can't be replayed). It is
**not** a content signature — the companion mod, which every player has, can
always echo its own nonce. That's the irreducible client-trust limit.

---

## 4. Installation

1. Drop the matching jar in the server's `mods/` folder.
2. Ship the same jar to clients (inside your modpack/launcher).
3. Start the server once — the config is generated at:
   ```
   config/fiw-mods-api/config.json
   ```
   (The internal id `fiw-mods-api` is kept for backwards-compat; the public name
   is Fiw AntiCheat.)
4. Edit the config (§5), then reload in-game with `/fiwmods reload` (OP level 4).

Existing config files are not reset on upgrade. Missing fields use defaults in
memory; new fields appear in freshly generated configs or the next time an
existing command saves the config.

---

## 5. Configuration reference

Full default config:

```jsonc
{
  "mode": "blacklist",
  "timeout_seconds": 10,
  "kick_message": "You are using a mod not allowed on this server.",
  "timeout_message": "Mod verification timed out. Please rejoin.",
  "detection": {
    "preset": "balanced",
    "monitor_only": false,
    "alert_staff": true,
    "block": {
      "cheat_clients": true, "xray": true, "fullbright": true, "freecam": true,
      "replay": false, "minimap": false, "autoclicker": true,
      "schematic_printer": true, "tweakeroo_utility": false,
      "damage_indicators": false, "zoom": false
    },
    "allow_overrides": [],
    "banned_mods": []
  },
  "resource_packs": {
    "log": true,
    "kick_on_banned": false,
    "banned_packs": [],
    "banned_fingerprints": []
  },
  "exemptions": {
    "floodgate_auto": true,
    "bypass_players": [],
    "player_overrides": {},
    "escalation_rules": []
  },
  "profiling": { "enabled": true, "max_history": 200 },
  "whitelist": { "require_all": true, "official_mods": [] }
}
```

### Root options

| Key | Type | Default | Meaning |
|---|---|---|---|
| `mode` | string | `blacklist` | `blacklist` (open, deny listed mods) or `whitelist` (only the official set). |
| `timeout_seconds` | int | `10` | Seconds the client has to answer the challenge (min effective 1). On slow connections set this higher so legit players aren't kicked before responding. |
| `kick_message` | string | … | Shown to a player kicked for a blocked mod or whitelist failure. Never names the mod. |
| `timeout_message` | string | … | Shown when the client never answers in time. |

### `detection`

| Key | Type | Default | Meaning |
|---|---|---|---|
| `preset` | string | `balanced` | `strict` \| `balanced` \| `lenient` \| `custom`. Chooses which categories are blocked. |
| `monitor_only` | bool | `false` | If `true`, **nothing is kicked** — detections are only logged. Use for safe rollout / observation. |
| `alert_staff` | bool | `true` | Notify online operators in-game with the real mod name on a detection. |
| `block` | map | (balanced) | Per-category on/off map. **Only used when `preset` is `custom`.** |
| `allow_overrides` | string[] | `[]` | Mod ids to un-ban from an otherwise-blocked category (e.g. allow one minimap). |
| `banned_mods` | string[] | `[]` | Extra mod ids to always deny (on top of the signature blocklist). |

### `resource_packs`

| Key | Type | Default | Meaning |
|---|---|---|---|
| `log` | bool | `true` | Record active/inactive resource packs in player profiles and evaluate optional pack bans. |
| `kick_on_banned` | bool | `false` | If `true`, configured resource pack matches disconnect players. Default is audit/log only. |
| `banned_packs` | string[] | `[]` | Exact resource pack ids or display names to flag, active or inactive. Examples: `file/xray.zip`, `xray.zip`. |
| `banned_fingerprints` | string[] | `[]` | Exact SHA-256 pack fingerprints to flag, useful when a known pack may be renamed. |

Resource pack checks are intentionally simple and transparent. Name/id matches
catch common public packs and casual bypasses; fingerprints catch exact known
pack content. A modified companion client can still lie, just like with mod
reporting.

### `exemptions`

| Key | Type | Default | Meaning |
|---|---|---|---|
| `floodgate_auto` | bool | `true` | Auto-exempt Bedrock/Floodgate players (reflection soft-dependency; no-op if Floodgate absent). Internally resolves to the `bypass` tier. |
| `bypass_players` | string[] | `[]` | Player **names or UUIDs** that skip verification entirely (legacy shortcut for a permanent `bypass` grant; still works). |
| `player_overrides` | map | `{}` | Per-player exemption tiers, keyed by lowercase name or UUID. Managed with `/fiwmods exempt add\|remove\|list` (see §10) rather than hand-edited, though the config is plain JSON if you prefer to edit it directly. |
| `escalation_rules` | object[] | `[]` | Rules that auto-apply a tier after repeated detections (see "Escalation rules" below). |

#### Per-player exemption tiers

`bypass_players` is an all-or-nothing shortcut. `player_overrides` supports six
tiers, each entry shaped like:

```jsonc
"player_overrides": {
  "steve": { "tier": "silent", "expires_at": null, "reason": "trusted tester" },
  "griefer123": { "tier": "preset", "preset": "strict", "expires_at": 1735689600000 }
}
```

| Tier | Scanned? | Kicked? | Staff alert? | Notes |
|---|---|---|---|---|
| `bypass` | No — skipped at join before any challenge | Never | Never | Same as a `bypass_players` entry, just expressed as a tier (and can carry an expiry). |
| `silent` | Yes | Never | Never | Console log line + profile history still recorded, just quietly — no kick, no staff ping. |
| `monitor` | Yes | Never | Yes | Per-player version of global `monitor_only`. |
| `quiet_kick` | Yes | Normal (real detection decides) | Never | Kicks happen exactly as they would with no override; only the staff chat alert is suppressed. |
| `preset:<name>` | Yes, against `<name>` instead of `detection.preset` | Normal | Normal | `<name>` is `strict`, `balanced`, `lenient`, or `custom` (custom reuses the server's own `detection.block` map). |
| `force_block` | No — kicked at join before any challenge | Always | Never | Manual per-player deny-list for repeat offenders; bypasses the scan entirely. |

`expires_at` is an epoch-millis timestamp; `null`/absent means permanent. An
expired grant is purged automatically the next time that player is evaluated —
no restart or reload needed.

#### Escalation rules

```jsonc
"escalation_rules": [
  { "enabled": true, "detection_count": 5, "window_hours": 4,
    "action_tier": "force_block", "duration_hours": 4 }
]
```

Each rule watches how many real detections a player accumulates within
`window_hours`; once `detection_count` is reached, `action_tier` is granted
automatically for `duration_hours` (using the same expiry mechanism as a
manual grant). Rules are checked in order and only the first matching rule
fires per detection event. An escalation **never** overrides a tier an admin
already granted — if a player already has any active override, escalation
checks are skipped for them until it expires or is removed.

### `profiling`

| Key | Type | Default | Meaning |
|---|---|---|---|
| `enabled` | bool | `true` | Record per-player mod profiles (see §9). |
| `max_history` | int | `200` | Max change events kept per player before the oldest are trimmed. |

### `whitelist`

| Key | Type | Default | Meaning |
|---|---|---|---|
| `require_all` | bool | `true` | If `true`, a player missing any official mod is kicked (exact set). If `false`, extra official mods may be absent. |
| `official_mods` | object[] | `[]` | The captured official set; filled by `/fiwmods snapshot`. Each entry: `{ "id", "version", "fingerprint" }`. |

`official_mods[].fingerprint` is the SHA-256 of the mod's jar. **When present, it
is enforced** — a mod claiming that id with a different jar is rejected
(anti-impersonation). Re-snapshot after a pack update to refresh fingerprints.

---

## 6. Evaluation order

For each join, in order:

1. **Exemption tier** (Floodgate Bedrock, `bypass_players`, or `player_overrides`,
   see §5) is resolved first:
   - `bypass` → **PASS**, no scan at all.
   - `force_block` → **KICK**, no scan at all.
   - any other tier (`silent`, `monitor`, `quiet_kick`, `preset:<name>`, or no
     override) proceeds to the normal scan below; `preset:<name>` swaps in
     that preset's category block-map for step 2 only.
2. **Blocklist** (runs in both modes). For each reported mod, match against the
   bundled signature database (§8). If a signature matches, its category is
   enabled, and the id is not in `allow_overrides` → record a detection. Mod ids
   in `banned_mods` also detect.
3. **Resource pack audit.** If `resource_packs.log` is true, check active and
   inactive packs against `banned_packs` and `banned_fingerprints`. Matches are
   logged by default and only kick when `resource_packs.kick_on_banned` is true.
4. **Mode**:
   - `blacklist` → kick if any detection (otherwise PASS — open server).
   - `whitelist` → also require every reported mod to be in `official_mods`
     (unknown mod → kick), enforce pinned fingerprints (mismatch → kick), and if
     `require_all`, kick when an official mod is missing.
5. **`monitor_only`** suppresses *all* kicks — detections are logged only. A
   `monitor` or `silent` tier does the same thing for just that one player,
   regardless of the global `monitor_only` setting; `silent` additionally
   suppresses the staff alert, and `quiet_kick` lets the kick happen normally
   but suppresses only the staff alert.

Empty `official_mods` in `whitelist` mode = **setup mode**: everyone is allowed
(with a loud log warning) until you capture a snapshot.

---

## 7. Modes in practice

**Blacklist (default).** Players may run anything except mods that match an
enabled blocklist category or `banned_mods`. Best for an open server that just
wants to keep out cheats/x-ray/etc.

**Whitelist.** Only the captured official modpack is allowed; anything else is
kicked. Best for a locked modpack server or a controlled launcher. Workflow:

1. Set `"mode": "whitelist"`.
2. Join with the official pack installed and run `/fiwmods snapshot player <you>`
   (or `/fiwmods snapshot server` to capture the server's own mods).
3. The official set (id + version + fingerprint) is written and reloaded.
4. Re-snapshot whenever the pack changes.

---

## 8. Signature database (known-bad mods)

The blocklist is a bundled data file: `core/src/main/resources/signatures.json`.
Each entry is pure data — add mods or categories without touching code:

```jsonc
{ "name": "Some Cheat", "category": "cheat_clients",
  "match": { "ids": ["somecheat"], "packages": ["com.example.cheat"],
             "mixins": ["somecheat.mixins.json"] } }
```

A signature matches if **any** rule hits:
- `ids` — exact mod id (survives a jar-file rename; id lives in metadata),
- `packages` — root package prefix (survives an internal mod-id rename),
- `mixins` — declared mixin config file name.

Matching is **never** by version or hash, so it survives mod updates. Categories:

`cheat_clients`, `xray`, `fullbright`, `freecam`, `replay`, `minimap`,
`autoclicker`, `schematic_printer`, `tweakeroo_utility`, `damage_indicators`,
`zoom`.

### Presets

| Preset | Blocks |
|---|---|
| `strict` | all categories |
| `balanced` (default) | cheat_clients, xray, fullbright, freecam, autoclicker, schematic_printer |
| `lenient` | cheat_clients, xray, autoclicker |
| `custom` | exactly the `detection.block` map |

`allow_overrides`, `banned_mods`, and `bypass_players` always apply on top.

> Detection is **per mod**, not per setting — the mod can block a minimap mod
> entirely, but can't read a toggle *inside* a mod (e.g. allow Xaero's map but
> block only its cave/entity radar).

---

## 9. Per-player profiles

When `profiling.enabled`, the server keeps a profile per player at:

```
config/fiw-mods-api/profiles/<uuid>.json
```

On each join it diffs the reported mod set against the stored one and appends
`added` / `removed` / `updated` events with timestamps (capped at `max_history`).
Recorded on every verified report (including kicks, so the offending join is
captured). Output **separates real player-installed mods from platform noise**
(minecraft, loader, fabric-api, neoforge, the anticheat itself, etc.) so staff
see the meaningful list.

Profiles also store resource packs in two sections:

- active resource packs — currently enabled in the client's pack stack,
- inactive resource packs — installed/available in the client's resource pack
  folder but not currently enabled.

Resource pack history records `added`, `enabled`, `disabled`, `removed`, and
`updated` events. "Removed" means the companion mod saw the pack in an earlier
report and no longer sees it in a later report; it cannot prove a pack existed
if the player installed and deleted it before ever reporting to the server.

View it with `/fiwmods profile <name>` — shows grouped current mods, active and
inactive resource packs, plus recent changes. This is useful for spotting "this
player just added freecam" or "this player enabled and then removed an x-ray
pack".

---

## 10. Commands

`/fiwmods` — requires OP permission level 4.

| Command | Description |
|---|---|
| `/fiwmods reload` | Reload config + bundled signatures from disk |
| `/fiwmods snapshot server` | Capture the **server's own** loaded mods as the whitelist |
| `/fiwmods snapshot player <name>` | Capture an online player's reported mods as the whitelist |
| `/fiwmods profile <name>` | Show a player's grouped current mods, resource packs, and recent history |
| `/fiwmods exempt add <player> <tier> [hours] [preset]` | Grant `<player>` an exemption tier (`bypass`, `silent`, `monitor`, `quiet_kick`, `preset`, `force_block`). `[hours]` sets an expiry (omit or `0` for permanent); `[preset]` is required only when `<tier>` is `preset`. |
| `/fiwmods exempt remove <player>` | Remove any exemption (tier override or legacy bypass-list entry) for `<player>` |
| `/fiwmods exempt list` | List every active exemption and its tier/expiry |

---

## 11. Performance

Mod verification is **per-join** and off the main thread for I/O; nothing runs
per-tick on the server except a lightweight timeout sweep over pending joins.
Client-side jar hashing happens once at join on the client. Resource packs are
scanned on join and then periodically client-side while connected; only changed
pack sets are sent to the server.

---

## 12. Building from source

```bash
# Use Java 21 to run the full multi-project build.
./gradlew build          # builds core + every loader target
./gradlew :core:test     # run the engine unit tests
```

Per-target jars land in each module's `build/libs/`. Build tooling note: the
project pins Gradle 8.10.2 (Loom/ForgeGradle/NeoForge compatibility) and uses
the foojay toolchain resolver to auto-provision Java 17/21 as needed; the Gradle
daemon must run on Java 21 (Loom requires it for the 1.21.x targets).

Project layout:

```
core/             Pure-Java engine: config, signatures, evaluation, profiles (+ tests)
fabric-1.20.1/    Fabric 1.20.1 adapter (legacy channel networking)
fabric-1.21.1/    Fabric 1.21.1 adapter (CustomPayload networking)
fabric-1.21.11/   Fabric 1.21.11 adapter
forge-1.20.1/     MinecraftForge 1.20.1 adapter (event-based freeze, SimpleChannel)
neoforge-1.21.1/  NeoForge 1.21.1 adapter (payload networking, mixin freeze)
neoforge-1.21.11/ NeoForge 1.21.11 adapter
```

---

## 13. Troubleshooting

- **Legit players randomly kicked on join** → raise `timeout_seconds`; slow
  connections may not answer in time.
- **A nested/dependency sub-mod gets flagged** → add its id to `allow_overrides`,
  or use a less strict preset.
- **Auto-updating mods (e.g. Essential) break whitelist mode** → pin them via a
  controlled launcher, or keep them in `blacklist` mode.
- **Resource pack bans log but do not kick** → set
  `resource_packs.kick_on_banned` to `true`; default behavior is audit-only.
- **A renamed x-ray pack is not caught by name** → add its SHA-256 fingerprint to
  `resource_packs.banned_fingerprints` when you know the exact pack file.
- **Bedrock players kicked** → ensure Floodgate is installed and
  `floodgate_auto` is `true`, or add them to `bypass_players`.
- **Rolling out on a live server** → set `monitor_only: true` first, watch the
  logs, then enforce.

---

## 14. License

Fiw AntiCheat is released under the **Fiw AntiCheat License (Attribution,
Non-Commercial)** — Copyright © 2026 Fi3w0. You may use, modify, fork, and
redistribute it (including modified builds) as long as you give clear, visible
credit to Fi3w0 as the original creator, link back to the original, and keep
existing notices intact. You **may not sell** it or any fork without written
permission (running it on a server, even a monetized one, is fine). Fi3w0 retains
authorship of the original work. See [LICENSE](LICENSE). Contact: Discord `fi3w0`.
