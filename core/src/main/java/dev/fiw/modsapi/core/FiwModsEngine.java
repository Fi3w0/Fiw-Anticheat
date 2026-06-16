package dev.fiw.modsapi.core;

import dev.fiw.modsapi.core.challenge.ChallengeManager;
import dev.fiw.modsapi.core.config.ModConfig;
import dev.fiw.modsapi.core.freeze.FreezeTracker;
import dev.fiw.modsapi.core.model.ModEntry;
import dev.fiw.modsapi.core.profile.PlayerProfile;
import dev.fiw.modsapi.core.profile.ProfileStore;
import dev.fiw.modsapi.core.signature.SignatureDatabase;
import dev.fiw.modsapi.core.snapshot.SnapshotWriter;
import dev.fiw.modsapi.core.verify.EvaluationResult;
import dev.fiw.modsapi.core.verify.Evaluator;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Loader-agnostic facade tying config, signatures, challenge/freeze tracking and
 * profiles together. The adapters instantiate one of these and call into it from
 * their join/response/tick handlers.
 */
public final class FiwModsEngine {

    private final Platform platform;
    private final ChallengeManager challenges = new ChallengeManager();
    private final FreezeTracker freezes = new FreezeTracker();

    private volatile ModConfig config;
    private volatile SignatureDatabase signatures;
    private volatile ProfileStore profiles;

    public FiwModsEngine(Platform platform) {
        this.platform = platform;
        reload();
    }

    /** (Re)load config + signature database from disk. */
    public void reload() {
        try {
            this.config = ModConfig.load(platform.configDir());
        } catch (IOException e) {
            platform.logError("Failed to load config.json — using defaults", e);
            this.config = new ModConfig();
        }
        this.signatures = SignatureDatabase.loadBundled();
        this.profiles = new ProfileStore(platform.configDir());
        platform.logInfo("Loaded config (mode=" + config.mode + ", preset=" + config.detection.preset
                + ", monitor_only=" + config.detection.monitor_only + "), "
                + signatures.size() + " mod signatures");
        if (config.isWhitelistMode()
                && (config.whitelist.official_mods == null || config.whitelist.official_mods.isEmpty())) {
            platform.logWarn("WHITELIST mode with no official mods captured — running in SETUP MODE "
                    + "(everyone allowed). Run '/fiwmods snapshot server' or 'snapshot player <name>' to lock it in.");
        }
    }

    public ModConfig config() { return config; }
    public ChallengeManager challenges() { return challenges; }
    public FreezeTracker freezes() { return freezes; }
    public ProfileStore profiles() { return profiles; }

    public long timeoutMillis() {
        return Math.max(1, config.timeout_seconds) * 1000L;
    }

    /** Whether a player (by name + uuid) is exempt via the bypass list. */
    public boolean isBypassed(String name, UUID uuid) {
        Set<String> set = config.exemptions.bypass_players.stream()
                .filter(s -> s != null)
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        return (name != null && set.contains(name.toLowerCase(Locale.ROOT)))
                || (uuid != null && set.contains(uuid.toString().toLowerCase(Locale.ROOT)));
    }

    /** Evaluate a reported mod list. {@code exempt} short-circuits to PASS. */
    public EvaluationResult evaluate(List<ModEntry> mods, boolean exempt) {
        return Evaluator.evaluate(mods, config, signatures, exempt);
    }

    /** Record a profiling entry (no-op if profiling disabled). Logs notable changes. */
    public void recordProfile(UUID uuid, String name, List<ModEntry> mods) {
        if (!config.profiling.enabled) return;
        try {
            List<PlayerProfile.Event> events =
                    profiles.record(uuid, name, mods, config.profiling.max_history);
            if (!events.isEmpty()) {
                String summary = events.stream()
                        .map(e -> e.event + " " + e.id + (e.to != null ? " (" + e.from + "→" + e.to + ")" : ""))
                        .collect(Collectors.joining(", "));
                platform.logInfo("Profile change for " + name + ": " + summary);
            }
        } catch (IOException e) {
            platform.logError("Failed to write profile for " + name, e);
        }
    }

    /** Capture a mod list as the official whitelist set, persist + reload. */
    public void captureSnapshot(List<ModEntry> mods, String source) {
        config.whitelist.official_mods = SnapshotWriter.toOfficialMods(mods);
        try {
            config.save(platform.configDir());
            platform.logInfo("Captured snapshot from " + source + ": "
                    + config.whitelist.official_mods.size() + " mods written to whitelist.official_mods");
        } catch (IOException e) {
            platform.logError("Failed to save snapshot", e);
        }
    }
}
