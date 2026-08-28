package dev.fiw.modsapi.core;

import dev.fiw.modsapi.core.challenge.ChallengeManager;
import dev.fiw.modsapi.core.config.ModConfig;
import dev.fiw.modsapi.core.config.Preset;
import dev.fiw.modsapi.core.exemption.ExemptionResolution;
import dev.fiw.modsapi.core.exemption.ExemptionTier;
import dev.fiw.modsapi.core.exemption.ExemptionView;
import dev.fiw.modsapi.core.freeze.FreezeTracker;
import dev.fiw.modsapi.core.model.ModEntry;
import dev.fiw.modsapi.core.model.ResourcePackEntry;
import dev.fiw.modsapi.core.profile.PlayerProfile;
import dev.fiw.modsapi.core.profile.ProfileStore;
import dev.fiw.modsapi.core.signature.SignatureDatabase;
import dev.fiw.modsapi.core.snapshot.SnapshotWriter;
import dev.fiw.modsapi.core.verify.EvaluationResult;
import dev.fiw.modsapi.core.verify.Evaluator;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    /** Whether a player (by name + uuid) is exempt via the legacy bypass list. */
    public boolean isBypassed(String name, UUID uuid) {
        Set<String> set = config.exemptions.bypass_players.stream()
                .filter(s -> s != null)
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        return (name != null && set.contains(name.toLowerCase(Locale.ROOT)))
                || (uuid != null && set.contains(uuid.toString().toLowerCase(Locale.ROOT)));
    }

    /**
     * Resolve the active per-player exemption tier. Checks {@code player_overrides} first
     * (purging any expired grants), then falls back to the legacy {@code bypass_players} list.
     */
    public ExemptionResolution resolveExemption(String name, UUID uuid) {
        purgeExpiredOverrides();
        ModConfig.PlayerOverride override = findOverride(name, uuid);
        if (override != null) {
            ExemptionTier tier = ExemptionTier.fromString(override.tier);
            if (tier != null) {
                return new ExemptionResolution(tier, override.preset);
            }
        }
        if (isBypassed(name, uuid)) {
            return ExemptionResolution.of(ExemptionTier.BYPASS);
        }
        return ExemptionResolution.NONE;
    }

    private ModConfig.PlayerOverride findOverride(String name, UUID uuid) {
        Map<String, ModConfig.PlayerOverride> overrides = config.exemptions.player_overrides;
        if (overrides == null) return null;
        if (name != null) {
            ModConfig.PlayerOverride byName = overrides.get(name.toLowerCase(Locale.ROOT));
            if (byName != null) return byName;
        }
        if (uuid != null) {
            return overrides.get(uuid.toString().toLowerCase(Locale.ROOT));
        }
        return null;
    }

    private void purgeExpiredOverrides() {
        Map<String, ModConfig.PlayerOverride> overrides = config.exemptions.player_overrides;
        if (overrides == null || overrides.isEmpty()) return;
        long now = System.currentTimeMillis();
        boolean changed = overrides.entrySet().removeIf(e ->
                e.getValue() != null && e.getValue().expires_at != null && e.getValue().expires_at <= now);
        if (changed) {
            try {
                config.save(platform.configDir());
            } catch (IOException e) {
                platform.logError("Failed to persist expired exemption purge", e);
            }
        }
    }

    /**
     * Grant a per-player exemption tier. {@code hours} null/&lt;=0 means permanent.
     * Returns an error message on invalid input, or {@code null} on success.
     */
    public String addExemption(String key, ExemptionTier tier, String presetOverride, Integer hours) {
        return addExemption(key, tier, presetOverride, hours, null);
    }

    private String addExemption(String key, ExemptionTier tier, String presetOverride, Integer hours, String reason) {
        if (key == null || key.isBlank()) return "player name or UUID is required";
        if (tier == null || tier == ExemptionTier.NONE) {
            return "tier must be one of: bypass, silent, monitor, quiet_kick, preset, force_block";
        }
        String presetName = null;
        if (tier == ExemptionTier.PRESET) {
            if (presetOverride == null) {
                return "preset tier requires a preset name: strict, balanced, lenient, or custom";
            }
            try {
                presetName = Preset.valueOf(presetOverride.trim().toUpperCase(Locale.ROOT)).name().toLowerCase(Locale.ROOT);
            } catch (IllegalArgumentException e) {
                return "unknown preset '" + presetOverride + "' — expected strict, balanced, lenient, or custom";
            }
        }
        String normalized = key.toLowerCase(Locale.ROOT);
        Long expiresAt = (hours != null && hours > 0) ? System.currentTimeMillis() + hours * 3600_000L : null;
        config.exemptions.player_overrides.put(normalized,
                new ModConfig.PlayerOverride(tier.name().toLowerCase(Locale.ROOT), presetName, expiresAt, reason));
        try {
            config.save(platform.configDir());
            platform.logInfo("Granted '" + tier.name().toLowerCase(Locale.ROOT) + "' exemption to " + key
                    + (expiresAt != null ? " (expires " + Instant.ofEpochMilli(expiresAt) + ")" : " (permanent)"));
        } catch (IOException e) {
            platform.logError("Failed to save exemption grant for " + key, e);
            return "applied in memory but failed to write config.json — check server logs";
        }
        return null;
    }

    /** Remove any exemption (override map or legacy bypass list) for a name/UUID key. */
    public boolean removeExemption(String key) {
        if (key == null || key.isBlank()) return false;
        String normalized = key.toLowerCase(Locale.ROOT);
        boolean removed = config.exemptions.player_overrides.remove(normalized) != null;
        removed |= config.exemptions.bypass_players.removeIf(s -> s != null && s.equalsIgnoreCase(key));
        if (removed) {
            try {
                config.save(platform.configDir());
                platform.logInfo("Removed exemption for " + key);
            } catch (IOException e) {
                platform.logError("Failed to save exemption removal for " + key, e);
            }
        }
        return removed;
    }

    /** Formatted rows for {@code /fiwmods exempt list}. */
    public List<ExemptionView.CommandLine> listExemptions() {
        return ExemptionView.commandRows(config);
    }

    /**
     * Track a detection event for escalation-rule purposes and auto-apply a tier once a rule's
     * threshold is met. No-ops if there are no detections, no enabled rules, or the player already
     * has an active override (never restacks on top of an existing admin/auto grant).
     */
    public void checkEscalation(UUID uuid, String name, EvaluationResult result) {
        if (result == null || !result.hasDetections()) return;
        List<ModConfig.EscalationRule> rules = config.exemptions.escalation_rules;
        if (rules == null || rules.isEmpty()) return;
        if (resolveExemption(name, uuid).tier() != ExemptionTier.NONE) return;

        int maxWindow = 24;
        boolean anyEnabled = false;
        for (ModConfig.EscalationRule rule : rules) {
            if (!rule.enabled) continue;
            anyEnabled = true;
            maxWindow = Math.max(maxWindow, rule.window_hours);
        }
        if (!anyEnabled) return;

        List<String> timestamps;
        try {
            timestamps = profiles.recordDetection(uuid, name, maxWindow);
        } catch (IOException e) {
            platform.logError("Failed to record detection for escalation tracking: " + name, e);
            return;
        }

        for (ModConfig.EscalationRule rule : rules) {
            if (!rule.enabled) continue;
            Instant cutoff = Instant.now().minus(Duration.ofHours(Math.max(1, rule.window_hours)));
            long count = timestamps.stream().filter(ts -> {
                try {
                    return !Instant.parse(ts).isBefore(cutoff);
                } catch (Exception e) {
                    return false;
                }
            }).count();
            if (count >= rule.detection_count) {
                ExemptionTier actionTier = ExemptionTier.fromString(rule.action_tier);
                if (actionTier == null || actionTier == ExemptionTier.NONE) {
                    platform.logWarn("Escalation rule has invalid action_tier: " + rule.action_tier);
                    continue;
                }
                String key = uuid != null ? uuid.toString() : name;
                String err = addExemption(key, actionTier, null, rule.duration_hours, "auto-escalation rule");
                if (err == null) {
                    platform.logWarn("[Escalation] " + name + " hit " + count + " detections in " + rule.window_hours
                            + "h — auto-applying '" + rule.action_tier + "' for " + rule.duration_hours + "h");
                }
                return;
            }
        }
    }

    /** Evaluate a reported mod list against the resolved exemption tier. */
    public EvaluationResult evaluate(List<ModEntry> mods, ExemptionResolution exemption) {
        return Evaluator.evaluate(mods, config, signatures, exemption);
    }

    /** Evaluate a full client report against the resolved exemption tier. */
    public EvaluationResult evaluate(List<ModEntry> mods, List<ResourcePackEntry> resourcePacks,
                                     ExemptionResolution exemption) {
        return Evaluator.evaluate(mods, resourcePacks, config, signatures, exemption);
    }

    /** Evaluate a resource-pack-only update after the join response. */
    public EvaluationResult evaluateResourcePacks(List<ResourcePackEntry> resourcePacks, ExemptionResolution exemption) {
        return Evaluator.evaluateResourcePacks(resourcePacks, config, exemption);
    }

    /** Record a profiling entry (no-op if profiling disabled). Logs notable changes. */
    public void recordProfile(UUID uuid, String name, List<ModEntry> mods) {
        recordProfile(uuid, name, mods, List.of());
    }

    /** Record a profiling entry with both mods and resource packs. */
    public void recordProfile(UUID uuid, String name, List<ModEntry> mods, List<ResourcePackEntry> resourcePacks) {
        if (!config.profiling.enabled) return;
        try {
            List<PlayerProfile.Event> events =
                    config.resource_packs != null && config.resource_packs.log
                            ? profiles.record(uuid, name, mods, resourcePacks, config.profiling.max_history)
                            : profiles.record(uuid, name, mods, config.profiling.max_history);
            if (!events.isEmpty()) {
                String summary = summarizeEvents(events);
                platform.logInfo("Profile change for " + name + ": " + summary);
            }
        } catch (IOException e) {
            platform.logError("Failed to write profile for " + name, e);
        }
    }

    /** Record a resource-pack-only profile update without incrementing the join counter. */
    public void recordResourcePacks(UUID uuid, String name, List<ResourcePackEntry> resourcePacks) {
        if (!config.profiling.enabled || config.resource_packs == null || !config.resource_packs.log) return;
        try {
            List<PlayerProfile.Event> events =
                    profiles.recordResourcePacks(uuid, name, resourcePacks, config.profiling.max_history);
            if (!events.isEmpty()) {
                platform.logInfo("Resource pack change for " + name + ": " + summarizeEvents(events));
            }
        } catch (IOException e) {
            platform.logError("Failed to write resource pack profile for " + name, e);
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

    private static String summarizeEvents(List<PlayerProfile.Event> events) {
        return events.stream()
                .map(e -> {
                    String type = e.type == null || e.type.isBlank() ? "mod" : e.type;
                    String name = e.name == null || e.name.isBlank() ? e.id : e.name;
                    String state = e.state == null || e.state.isBlank() ? "" : " [" + e.state + "]";
                    String update = e.to != null ? " (" + e.from + "→" + e.to + ")" : "";
                    return e.event + " " + type + " " + name + state + update;
                })
                .collect(Collectors.joining(", "));
    }
}
