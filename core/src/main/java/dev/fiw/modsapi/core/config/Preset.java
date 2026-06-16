package dev.fiw.modsapi.core.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Optional blacklist presets. A named preset supplies the category on/off map;
 * {@link #CUSTOM} instead uses the literal {@code detection.block} map from the
 * config. {@code allow_overrides} and {@code bypass_players} always apply on top.
 */
public enum Preset {
    STRICT,
    BALANCED,
    LENIENT,
    CUSTOM;

    /** All known categories. Adding one here + in signatures.json is all that's needed. */
    public static final List<String> CATEGORIES = List.of(
            "cheat_clients",
            "xray",
            "fullbright",
            "freecam",
            "replay",
            "minimap",
            "autoclicker",
            "schematic_printer",
            "tweakeroo_utility",
            "damage_indicators",
            "zoom"
    );

    public static Preset fromString(String s) {
        if (s == null) return BALANCED;
        try {
            return valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return BALANCED;
        }
    }

    /**
     * Resolve this preset to a full category→enabled map.
     * For {@link #CUSTOM}, the provided {@code customBlock} is used (defaulting
     * unspecified categories to disabled).
     */
    public Map<String, Boolean> resolve(Map<String, Boolean> customBlock) {
        Map<String, Boolean> m = new LinkedHashMap<>();
        for (String cat : CATEGORIES) {
            m.put(cat, defaultFor(cat));
        }
        if (this == CUSTOM && customBlock != null) {
            for (String cat : CATEGORIES) {
                if (customBlock.containsKey(cat)) {
                    m.put(cat, customBlock.get(cat));
                }
            }
        }
        return m;
    }

    private boolean defaultFor(String category) {
        return switch (this) {
            case STRICT -> true; // block everything
            case LENIENT -> switch (category) {
                case "cheat_clients", "xray", "autoclicker" -> true;
                default -> false;
            };
            case BALANCED -> switch (category) {
                // clear cheats on; QoL (replay, minimap, zoom, indicators, tweakeroo) off
                case "cheat_clients", "xray", "fullbright", "freecam",
                     "autoclicker", "schematic_printer" -> true;
                default -> false;
            };
            case CUSTOM -> false; // overlaid by customBlock in resolve()
        };
    }
}
