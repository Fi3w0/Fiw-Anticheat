package dev.fiw.modsapi.core.exemption;

/**
 * Resolved per-player exemption: the tier, plus the preset name when
 * {@code tier == PRESET} (otherwise {@code null}).
 */
public record ExemptionResolution(ExemptionTier tier, String presetOverride) {

    public static final ExemptionResolution NONE = new ExemptionResolution(ExemptionTier.NONE, null);

    public static ExemptionResolution of(ExemptionTier tier) {
        return new ExemptionResolution(tier, null);
    }
}
