package dev.fiw.modsapi.core.signature;

import dev.fiw.modsapi.core.model.ModEntry;

import java.util.List;

/**
 * One known-mod fingerprint. A signature matches a reported mod if <em>any</em>
 * of its rules hit:
 * <ul>
 *   <li>{@code ids} — exact mod id (survives jar-file rename, since the id lives in metadata)</li>
 *   <li>{@code entrypoints} / {@code packages} — class/package prefix (survives an internal id rename)</li>
 *   <li>{@code mixins} — declared mixin config file name</li>
 * </ul>
 * Matching is never version- or hash-based, so it survives mod updates.
 */
public final class Signature {

    public String name;
    public String category;
    public Match match = new Match();

    public static final class Match {
        public List<String> ids = List.of();
        public List<String> entrypoints = List.of();
        public List<String> mixins = List.of();
        public List<String> packages = List.of();
    }

    public boolean matches(ModEntry mod) {
        if (match == null) return false;

        if (containsIgnoreCase(match.ids, mod.id())) return true;

        if (match.mixins != null) {
            for (String declared : mod.markers().mixinConfigs()) {
                if (containsIgnoreCase(match.mixins, declared)) return true;
            }
        }
        if (anyPrefix(match.entrypoints, mod.markers().entrypoints())) return true;
        if (anyPrefix(match.packages, mod.markers().packages())) return true;

        return false;
    }

    private static boolean containsIgnoreCase(List<String> haystack, String needle) {
        if (haystack == null || needle == null) return false;
        for (String h : haystack) {
            if (h != null && h.equalsIgnoreCase(needle)) return true;
        }
        return false;
    }

    /** True if any reported value starts with any rule prefix (case-insensitive). */
    private static boolean anyPrefix(List<String> prefixes, List<String> values) {
        if (prefixes == null || values == null) return false;
        for (String value : values) {
            if (value == null) continue;
            String v = value.toLowerCase();
            for (String prefix : prefixes) {
                if (prefix != null && v.startsWith(prefix.toLowerCase())) return true;
            }
        }
        return false;
    }
}
