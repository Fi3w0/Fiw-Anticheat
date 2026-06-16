package dev.fiw.modsapi.core.verify;

import java.util.List;

/**
 * Outcome of evaluating a client's reported mod list.
 *
 * @param kick        whether the player should be disconnected
 * @param kickMessage generic, non-revealing message shown to a kicked player
 * @param logSummary  detailed reason for the server console / staff (names real mods)
 * @param detected    blocked mods that matched (for console logging + staff alerts);
 *                     may be non-empty even when {@code kick} is false (monitor-only mode)
 */
public record EvaluationResult(boolean kick,
                               String kickMessage,
                               String logSummary,
                               List<Detected> detected) {

    /** A single blocked-mod hit. */
    public record Detected(String modName, String category, String modId) {}

    public boolean hasDetections() {
        return detected != null && !detected.isEmpty();
    }
}
