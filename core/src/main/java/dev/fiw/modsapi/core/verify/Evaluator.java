package dev.fiw.modsapi.core.verify;

import dev.fiw.modsapi.core.config.ModConfig;
import dev.fiw.modsapi.core.model.ModEntry;
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
                                            boolean exempt) {
        if (exempt) {
            return new EvaluationResult(false, config.kick_message, "exempt player — skipped", List.of());
        }

        List<EvaluationResult.Detected> detected = new ArrayList<>();
        List<String> reasons = new ArrayList<>();

        // --- Layer 1: known-bad blocklist (runs in both modes) ---
        Map<String, Boolean> blocked = config.resolvedBlock();
        Set<String> overrides = lowerSet(config.detection.allow_overrides);
        Set<String> bannedIds = lowerSet(config.detection.banned_mods);

        for (ModEntry mod : mods) {
            if (overrides.contains(mod.id().toLowerCase())) continue;

            Optional<Signature> match = signatures.firstMatch(mod);
            if (match.isPresent()) {
                Signature sig = match.get();
                if (blocked.getOrDefault(sig.category, false)) {
                    detected.add(new EvaluationResult.Detected(sig.name, sig.category, mod.id()));
                    continue;
                }
            }
            // explicit admin-banned id (blacklist extra)
            if (bannedIds.contains(mod.id().toLowerCase())) {
                detected.add(new EvaluationResult.Detected(mod.id(), "banned_mods", mod.id()));
            }
        }
        if (!detected.isEmpty()) {
            StringBuilder sb = new StringBuilder("blocked mods: ");
            for (int i = 0; i < detected.size(); i++) {
                EvaluationResult.Detected d = detected.get(i);
                if (i > 0) sb.append(", ");
                sb.append(d.modName()).append(" [").append(d.category()).append("]");
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

        boolean wouldKick = !detected.isEmpty() || hasWhitelistViolation(reasons);
        // a whitelist setup-mode note is informational, not a violation
        boolean kick = wouldKick && !config.detection.monitor_only;

        String summary;
        if (reasons.isEmpty()) {
            summary = "OK (" + mods.size() + " mods)";
        } else {
            summary = String.join("; ", reasons);
            if (wouldKick && config.detection.monitor_only) {
                summary = "[MONITOR — not kicked] " + summary;
            }
        }

        return new EvaluationResult(kick, config.kick_message, summary, detected);
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
