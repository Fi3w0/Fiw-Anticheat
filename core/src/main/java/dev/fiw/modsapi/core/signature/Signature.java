package dev.fiw.modsapi.core.signature;

import dev.fiw.modsapi.core.model.ModEntry;

import java.util.List;
import java.util.Locale;

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

    /**
     * Which rule hit and the reported value it hit on.
     *
     * @param type  {@code id}, {@code mixin}, {@code entrypoint} or {@code package}
     * @param value the client-reported value that matched (e.g. the full package name)
     */
    public record Hit(String type, String value) {}

    public boolean matches(ModEntry mod) {
        return findHit(mod) != null;
    }

    /** The first rule that matches {@code mod}, or null. */
    public Hit findHit(ModEntry mod) {
        if (match == null) return null;

        if (containsIgnoreCase(match.ids, mod.id())) return new Hit("id", mod.id());

        if (match.mixins != null) {
            for (String declared : mod.markers().mixinConfigs()) {
                if (containsIgnoreCase(match.mixins, declared)) return new Hit("mixin", declared);
            }
        }
        String entrypoint = firstPrefixed(match.entrypoints, mod.markers().entrypoints());
        if (entrypoint != null) return new Hit("entrypoint", entrypoint);
        String pkg = firstPrefixed(match.packages, mod.markers().packages());
        if (pkg != null) return new Hit("package", pkg);

        return null;
    }

    private static boolean containsIgnoreCase(List<String> haystack, String needle) {
        if (haystack == null || needle == null) return false;
        for (String h : haystack) {
            if (h != null && h.equalsIgnoreCase(needle)) return true;
        }
        return false;
    }

    /** First value that is a case-insensitive exact match or dot-boundary descendant of a prefix. */
    private static String firstPrefixed(List<String> prefixes, List<String> values) {
        if (prefixes == null || values == null) return null;
        for (String value : values) {
            if (value == null) continue;
            String v = value.toLowerCase(Locale.ROOT);
            for (String prefix : prefixes) {
                if (prefix == null || prefix.isEmpty()) continue;
                String p = prefix.toLowerCase(Locale.ROOT);
                if (v.equals(p) || v.startsWith(p + ".")) return value;
            }
        }
        return null;
    }
}
