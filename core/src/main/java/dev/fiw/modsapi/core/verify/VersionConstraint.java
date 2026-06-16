package dev.fiw.modsapi.core.verify;

/**
 * A tiny version constraint: {@code any} ("*"/null/empty), an exact match, or a
 * minimum ({@code ">=1.2.0"}). Deliberately not a full Maven range parser —
 * exact / at-least / any covers the overwhelming majority of modpack needs.
 */
public final class VersionConstraint {

    public enum Kind { ANY, EXACT, AT_LEAST }

    private final Kind kind;
    private final String version;

    private VersionConstraint(Kind kind, String version) {
        this.kind = kind;
        this.version = version;
    }

    public static VersionConstraint parse(String raw) {
        if (raw == null) return new VersionConstraint(Kind.ANY, "");
        String s = raw.trim();
        if (s.isEmpty() || s.equals("*") || s.equalsIgnoreCase("any")) {
            return new VersionConstraint(Kind.ANY, "");
        }
        if (s.startsWith(">=")) {
            return new VersionConstraint(Kind.AT_LEAST, s.substring(2).trim());
        }
        return new VersionConstraint(Kind.EXACT, s);
    }

    public boolean matches(String actual) {
        if (kind == Kind.ANY) return true;
        if (actual == null) return false;
        String a = actual.trim();
        return switch (kind) {
            case EXACT -> a.equals(version);
            case AT_LEAST -> compare(a, version) >= 0;
            default -> true;
        };
    }

    public Kind kind() { return kind; }

    /**
     * Compares two dotted version strings numerically segment by segment,
     * falling back to lexicographic comparison for non-numeric segments.
     * Missing trailing segments are treated as 0 (so "1.2" == "1.2.0").
     */
    public static int compare(String a, String b) {
        String[] as = a.split("[._\\-+]");
        String[] bs = b.split("[._\\-+]");
        int n = Math.max(as.length, bs.length);
        for (int i = 0; i < n; i++) {
            String sa = i < as.length ? as[i] : "0";
            String sb = i < bs.length ? bs[i] : "0";
            Integer ia = tryInt(sa);
            Integer ib = tryInt(sb);
            int cmp;
            if (ia != null && ib != null) {
                cmp = Integer.compare(ia, ib);
            } else {
                cmp = sa.compareTo(sb);
            }
            if (cmp != 0) return cmp;
        }
        return 0;
    }

    private static Integer tryInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private VersionConstraint() { this(Kind.ANY, ""); }
}
