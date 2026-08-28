package dev.fiw.modsapi.core.verify;

import dev.fiw.modsapi.core.config.ModConfig;
import dev.fiw.modsapi.core.config.Preset;
import dev.fiw.modsapi.core.exemption.ExemptionResolution;
import dev.fiw.modsapi.core.exemption.ExemptionTier;
import dev.fiw.modsapi.core.model.ModEntry;
import dev.fiw.modsapi.core.model.ResourcePackEntry;
import dev.fiw.modsapi.core.signature.Signature;
import dev.fiw.modsapi.core.signature.SignatureDatabase;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The decision engine. Given a client's reported mods, the config, and the
 * signature DB, decides PASS or KICK. Pure and side-effect free so it is fully
 * unit-testable without any Minecraft types.
 *
 * <p>Order of checks: exemption → known-bad blocklist → mode (blacklist/whitelist).
 * {@code monitor_only} suppresses all kicks (observe-only rollout) but still
 * reports detections for logging.
 */
public final class Evaluator {

    private Evaluator() {}

    public static EvaluationResult evaluate(List<ModEntry> mods,
                                            ModConfig config,
                                            SignatureDatabase signatures,
                                            ExemptionResolution exemption) {
        return evaluate(mods, List.of(), config, signatures, exemption);
    }

    public static EvaluationResult evaluate(List<ModEntry> mods,
                                            List<ResourcePackEntry> resourcePacks,
                                            ModConfig config,
                                            SignatureDatabase signatures,
                                            ExemptionResolution exemption) {
        ExemptionTier tier = exemption == null ? ExemptionTier.NONE : exemption.tier();

        if (tier == ExemptionTier.BYPASS) {
            return new EvaluationResult(false, config.kick_message, "bypass exemption — skipped", List.of(), true);
        }
        if (tier == ExemptionTier.FORCE_BLOCK) {
            return new EvaluationResult(true, config.kick_message, "force-blocked (admin override)", List.of(), true);
        }

        List<EvaluationResult.Detected> detected = new ArrayList<>();
        List<EvaluationResult.Detected> blockedMods = new ArrayList<>();
        List<EvaluationResult.Detected> blockedResourcePacks = new ArrayList<>();
        List<String> reasons = new ArrayList<>();

        // --- Layer 1: known-bad blocklist (runs in both modes) ---
        Map<String, Boolean> blocked = tier == ExemptionTier.PRESET
                ? Preset.fromString(exemption.presetOverride()).resolve(config.detection.block)
                : config.resolvedBlock();
        Set<String> overrides = lowerSet(config.detection.allow_overrides);
        Set<String> bannedIds = lowerSet(config.detection.banned_mods);

        for (ModEntry mod : mods) {
            if (overrides.contains(mod.id().toLowerCase())) continue;

            Optional<Signature> match = signatures.firstMatch(mod);
            if (match.isPresent()) {
                Signature sig = match.get();
                if (blocked.getOrDefault(sig.category, false)) {
                    blockedMods.add(new EvaluationResult.Detected(sig.name, sig.category, mod.id()));
                    continue;
                }
            }
            // explicit admin-banned id (blacklist extra)
            if (bannedIds.contains(mod.id().toLowerCase())) {
                blockedMods.add(new EvaluationResult.Detected(mod.id(), "banned_mods", mod.id()));
            }
        }
        detected.addAll(blockedMods);
        if (config.resource_packs != null && config.resource_packs.log) {
            blockedResourcePacks.addAll(checkResourcePacks(resourcePacks, config));
            detected.addAll(blockedResourcePacks);
        }
        if (!blockedMods.isEmpty()) {
            StringBuilder sb = new StringBuilder("blocked mods: ");
            for (int i = 0; i < blockedMods.size(); i++) {
                EvaluationResult.Detected d = blockedMods.get(i);
                if (i > 0) sb.append(", ");
                sb.append(d.modName()).append(" [").append(d.category()).append("]");
            }
            reasons.add(sb.toString());
        }
        if (!blockedResourcePacks.isEmpty()) {
            StringBuilder sb = new StringBuilder("banned resource packs: ");
            for (int i = 0; i < blockedResourcePacks.size(); i++) {
                EvaluationResult.Detected d = blockedResourcePacks.get(i);
                if (i > 0) sb.append(", ");
                sb.append(d.modName()).append(" [").append(d.category()).append("]");
            }
            if (!config.resource_packs.kick_on_banned) {
                sb.append(" (log only)");
            }
            reasons.add(sb.toString());
        }

        // --- Layer 2: mode ---
        if (config.isWhitelistMode()) {
            List<ModConfig.OfficialMod> official = config.whitelist.official_mods;
            if (official == null || official.isEmpty()) {
                // setup mode: allow everyone (caller logs a loud warning)
                reasons.add("whitelist setup mode — no official mods captured yet");
            } else {
                reasons.addAll(checkWhitelist(mods, official, config.whitelist.require_all));
            }
        }

        boolean wouldKick = !blockedMods.isEmpty()
                || hasWhitelistViolation(reasons)
                || (!blockedResourcePacks.isEmpty()
                        && config.resource_packs != null
                        && config.resource_packs.kick_on_banned);

        boolean kick;
        boolean suppressAlert;
        switch (tier) {
            case MONITOR -> { kick = false; suppressAlert = false; }
            case SILENT -> { kick = false; suppressAlert = true; }
            case QUIET_KICK -> { kick = wouldKick && !config.detection.monitor_only; suppressAlert = true; }
            default -> { kick = wouldKick && !config.detection.monitor_only; suppressAlert = false; } // NONE, PRESET
        }

        String summary;
        if (reasons.isEmpty()) {
            summary = "OK (" + mods.size() + " mods)";
        } else {
            summary = String.join("; ", reasons);
            if (wouldKick && !kick) {
                summary = "[MONITOR — not kicked] " + summary;
            }
        }

        return new EvaluationResult(kick, config.kick_message, summary, detected, suppressAlert);
    }

    public static EvaluationResult evaluateResourcePacks(List<ResourcePackEntry> resourcePacks,
                                                         ModConfig config,
                                                         ExemptionResolution exemption) {
        ExemptionTier tier = exemption == null ? ExemptionTier.NONE : exemption.tier();

        if (tier == ExemptionTier.BYPASS) {
            return new EvaluationResult(false, config.kick_message, "bypass exemption — skipped", List.of(), true);
        }
        if (tier == ExemptionTier.FORCE_BLOCK) {
            return new EvaluationResult(true, config.kick_message, "force-blocked (admin override)", List.of(), true);
        }

        List<EvaluationResult.Detected> detected =
                config.resource_packs == null || !config.resource_packs.log
                        ? List.of()
                        : checkResourcePacks(resourcePacks, config);
        if (detected.isEmpty()) {
            int packCount = resourcePacks == null ? 0 : resourcePacks.size();
            return new EvaluationResult(false, config.kick_message,
                    "OK (" + packCount + " resource packs)", List.of(), false);
        }
        StringBuilder sb = new StringBuilder("banned resource packs: ");
        for (int i = 0; i < detected.size(); i++) {
            EvaluationResult.Detected d = detected.get(i);
            if (i > 0) sb.append(", ");
            sb.append(d.modName()).append(" [").append(d.category()).append("]");
        }
        boolean wouldKick = config.resource_packs != null && config.resource_packs.kick_on_banned;
        if (!wouldKick) sb.append(" (log only)");

        boolean kick;
        boolean suppressAlert;
        switch (tier) {
            case MONITOR -> { kick = false; suppressAlert = false; }
            case SILENT -> { kick = false; suppressAlert = true; }
            case QUIET_KICK -> { kick = wouldKick && !config.detection.monitor_only; suppressAlert = true; }
            default -> { kick = wouldKick && !config.detection.monitor_only; suppressAlert = false; }
        }

        String summary = sb.toString();
        if (wouldKick && !kick) {
            summary = "[MONITOR — not kicked] " + summary;
        }
        return new EvaluationResult(kick, config.kick_message, summary, detected, suppressAlert);
    }

    private static List<String> checkWhitelist(List<ModEntry> mods,
                                               List<ModConfig.OfficialMod> official,
                                               boolean requireAll) {
        List<String> reasons = new ArrayList<>();
        Set<String> officialIds = new HashSet<>();
        for (ModConfig.OfficialMod om : official) {
            if (om.id != null) officialIds.add(om.id.toLowerCase());
        }

        Set<String> present = new HashSet<>();
        for (ModEntry mod : mods) {
            present.add(mod.id().toLowerCase());
            ModConfig.OfficialMod om = findOfficial(official, mod.id());
            if (om == null) {
                reasons.add("unknown mod: " + mod.id());
                continue;
            }
            // anti-impersonation: a pinned fingerprint must match
            if (om.fingerprint != null && !om.fingerprint.isEmpty()
                    && !om.fingerprint.equalsIgnoreCase(mod.fingerprint())) {
                reasons.add("fingerprint mismatch for " + mod.id() + " (tampered or wrong build)");
            }
        }

        if (requireAll) {
            for (ModConfig.OfficialMod om : official) {
                if (om.id != null && !present.contains(om.id.toLowerCase())) {
                    reasons.add("missing required mod: " + om.id);
                }
            }
        }
        return reasons;
    }

    private static boolean hasWhitelistViolation(List<String> reasons) {
        for (String r : reasons) {
            if (r.startsWith("unknown mod:")
                    || r.startsWith("fingerprint mismatch")
                    || r.startsWith("missing required mod:")) {
                return true;
            }
        }
        return false;
    }

    private static List<EvaluationResult.Detected> checkResourcePacks(List<ResourcePackEntry> resourcePacks,
                                                                      ModConfig config) {
        List<EvaluationResult.Detected> detected = new ArrayList<>();
        if (resourcePacks == null || config.resource_packs == null) return detected;

        Set<String> banned = lowerSet(config.resource_packs.banned_packs);
        Set<String> bannedFingerprints = lowerSet(config.resource_packs.banned_fingerprints);
        if (banned.isEmpty() && bannedFingerprints.isEmpty()) return detected;

        for (ResourcePackEntry pack : resourcePacks) {
            String id = pack.id().toLowerCase();
            String name = pack.displayName().toLowerCase();
            String fingerprint = pack.fingerprint().toLowerCase();
            if (banned.contains(id) || banned.contains(name)
                    || (!fingerprint.isEmpty() && bannedFingerprints.contains(fingerprint))) {
                detected.add(new EvaluationResult.Detected(pack.displayName(),
                        pack.active() ? "active_resource_pack" : "inactive_resource_pack",
                        pack.id()));
            }
        }
        return detected;
    }

    private static ModConfig.OfficialMod findOfficial(List<ModConfig.OfficialMod> official, String id) {
        for (ModConfig.OfficialMod om : official) {
            if (om.id != null && om.id.equalsIgnoreCase(id)) return om;
        }
        return null;
    }

    private static Set<String> lowerSet(List<String> in) {
        Set<String> out = new HashSet<>();
        if (in != null) {
            for (String s : in) {
                if (s != null) out.add(s.toLowerCase());
            }
        }
        return out;
    }
}
