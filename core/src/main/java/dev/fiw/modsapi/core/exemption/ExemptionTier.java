package dev.fiw.modsapi.core.exemption;

/**
 * Per-player override of the normal detection pipeline. {@link #NONE} means
 * no override is active and the global mode/preset/monitor_only apply as-is.
 */
public enum ExemptionTier {
    /** No override — normal detection applies. */
    NONE,
    /** Skip scanning entirely; always allowed to join. */
    BYPASS,
    /** Scanned and profiled normally; kick and staff alert both suppressed. */
    SILENT,
    /** Scanned and profiled normally; staff alerted but never kicked. */
    MONITOR,
    /** Normal detection + kick; only the staff alert is suppressed. */
    QUIET_KICK,
    /** Normal detection, but evaluated against a pinned preset instead of the server default. */
    PRESET,
    /** Always kicked on join, before any scan. */
    FORCE_BLOCK;

    /** Case-insensitive lookup. Returns {@code null} on no match (callers must reject bad input). */
    public static ExemptionTier fromString(String s) {
        if (s == null) return null;
        try {
            return valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
