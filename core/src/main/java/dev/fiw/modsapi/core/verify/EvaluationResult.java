package dev.fiw.modsapi.core.verify;

import java.util.List;

/**
 * Outcome of evaluating a client's reported mod list.
 *
 * @param kick          whether the player should be disconnected
 * @param kickMessage   generic, non-revealing message shown to a kicked player
 * @param logSummary    detailed reason for the server console / staff (names real mods)
 * @param detected      blocked mods that matched (for console logging + staff alerts);
 *                      may be non-empty even when {@code kick} is false (monitor-only mode)
 * @param suppressAlert whether the staff chat alert should be skipped even if there are
 *                      detections (set by the {@code silent}/{@code quiet_kick} exemption tiers);
 *                      the console log line is unaffected by this flag
 */
public record EvaluationResult(boolean kick,
                               String kickMessage,
                               String logSummary,
                               List<Detected> detected,
                               boolean suppressAlert) {

    /**
     * A single blocked-mod or banned-pack hit.
     *
     * @param matchType  how it matched: {@code id}, {@code mixin}, {@code entrypoint},
     *                   {@code package}, {@code pack id}, {@code pack name} or {@code pack fingerprint}
     * @param matchValue the reported value that matched
     */
    public record Detected(String modName, String category, String modId, String matchType, String matchValue) {

        public Detected(String modName, String category, String modId) {
            this(modName, category, modId, "id", modId);
        }

        /** Console line: what matched, how, and on which reported value. */
        public String describe() {
            return modName + " [" + category + "] matched " + matchType + " '" + matchValue + "'"
                    + (modId.equals(matchValue) ? "" : " (id=" + modId + ")");
        }
    }

    public boolean hasDetections() {
        return detected != null && !detected.isEmpty();
    }

    /** Staff chat alert for these detections, e.g. {@code Steve using: Freecam (package com.x.freecam)}. */
    public String alertText(String playerName) {
        StringBuilder sb = new StringBuilder("[FiwAntiCheat] ").append(playerName).append(" using: ");
        for (int i = 0; i < detected.size(); i++) {
            Detected d = detected.get(i);
            if (i > 0) sb.append(", ");
            sb.append(d.modName()).append(" (").append(d.matchType()).append(' ').append(d.matchValue()).append(')');
        }
        return sb.toString();
    }
}
