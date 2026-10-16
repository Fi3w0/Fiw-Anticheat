# 2.1.1 regression pack

This is for a disposable dedicated server, never the live server's config. There is no 1.21.8 AntiCheat jar; use its six supported targets. The user's two-instance player pass is for Bosses/Custom Items; this document does not ask for another manual AntiCheat pass yet.

Status on 2026-10-16: all six `runServer` launches failed before configuration with the local-socket restriction. No server reached `Done`, and no real companion handshake/join result has been established. Core-engine simulations are recorded separately below.

## Build and seed config

From this repository, run Gradle on Java 21 (not 25):

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home ./gradlew build --stacktrace
```

After building, install the matching 2.1.1 production jar on the test server and matching companion clients. Fabric also needs Fabric API. Copy only this folder's `config.json` to `config/fiw-mods-api/config.json`, backing up any previous file. The `reported-*.json` files are synthetic input for direct core validation, **not** files loaded by the Minecraft companion or server config loader.

Expected `/fiwmods exempt list` before any command changes:

- Header: **3 active** grants/legacy entries, with expiry timezone **UTC**.
- `qa_expired` does not appear (its timestamp is 1970 and deliberately expired).
- `qa_future`: silent, expiry at 2100-01-01 UTC.
- `qa_permanent`: preset/strict, permanent.
- `qa_legacy`: permanent bypass from the legacy list.

Listing is read-only: the expired map entry may still be present in the file until an exemption resolution purges it. Hiding an expired entry is not a promise that a list command edits the config.

## Console and restart workflow

All `/fiwmods` commands require level 4. Enter `console-smoke.txt` one line at a time, without `/` in the dedicated-server console. In player chat prefix the same commands with `/`.

- [ ] Reload and list: three visible grants and explicit UTC, with no expired row.
- [ ] Add `qa_added silent 1`: list now contains four. Reload: still four. Inspect `config.json` for that grant and its expiry.
- [ ] Clean `stop`, restart, list: the same grant/expiry is still present, not reset to a new hour.
- [ ] Remove `qa_added`: list returns to three; reload/restart does not resurrect it.
- [ ] Invalid tier and invalid preset commands report useful errors and do not add `qa_invalid`.
- [ ] Add `qa_preset preset 0 strict`: stored as separate `"tier": "preset", "preset": "strict"` fields with a permanent expiry. Remove it afterwards.
- [ ] After saves, `config.json` remains parseable and there are no abandoned `*.tmp` files. Previously recorded `profiles/<uuid>.json` files should also remain parseable across repeated reports/restarts.

A successful clean write does not prove crash-during-write behavior. The direct core unit test simulates an interrupted write and checks the prior file is intact; do not corrupt a live config/profile to test it manually.

## Real client cases still required when server execution is available

- [ ] Matching clean companion joins, completes verification and unfreezes.
- [ ] No companion times out using the configured message.
- [ ] On the disposable server only, temporarily add `minecraft` to `detection.banned_mods`, reload, and join with the companion: detect/kick. Restore the original config afterwards. This checks an actual loaded id without installing a cheat.
- [ ] With the same disposable ban, grant the test player `silent` or `monitor`, reconnect and verify its kick/alert behavior. Remove the grant afterwards.
- [ ] Join once with active and inactive local test resource packs; inspect both profile sections. Toggle a pack and check history; restart and verify the profile survives.

The synthetic `reported-mod-cases.json` exercises real bundled package signatures without installing a cheat. It tests exact/descendant/case-folded matches plus unrelated longer names. It cannot validate what a loader actually collects or sends. `reported-pack-cases.json` uses synthetic hashes to exercise profile persistence, not hashing or network code. Protocol versioning, mixin collection, hashing changes and payload-size work remain deferred.

## Direct core checks

The 45 existing JUnit tests pass. Production-core validation of this pack separately checks its active/expired list, atomic grant/remove persistence through engine recreation, six report cases against the bundled signatures, and active/inactive pack history through profile reload. These checks are not Minecraft server tests.

## Dedicated-server attempt record

Each command was attempted from this repo on Java 21 with a writable temporary Gradle cache. All failed before configuration with `FileLockContentionHandler` → `java.net.SocketException: Operation not permitted`. Direct TCP and UDP binds on 127.0.0.1 also return EPERM. Logs: `/private/tmp/fiw-runtime-20261016/`.

| Target | Command | Result |
|---|---|---|
| `fabric-1.21.11` | `./gradlew :fabric-1.21.11:runServer --console=plain --stacktrace` | Blocked before configuration |
| `neoforge-1.21.11` | `./gradlew :neoforge-1.21.11:runServer --console=plain --stacktrace` | Blocked before configuration |
| `fabric-1.21.1` | `./gradlew :fabric-1.21.1:runServer --console=plain --stacktrace` | Blocked before configuration |
| `neoforge-1.21.1` | `./gradlew :neoforge-1.21.1:runServer --console=plain --stacktrace` | Blocked before configuration |
| `fabric-1.20.1` | `./gradlew :fabric-1.20.1:runServer --console=plain --stacktrace` | Blocked before configuration |
| `forge-1.20.1` | `./gradlew :forge-1.20.1:runServer --console=plain --stacktrace` | Blocked before configuration |
