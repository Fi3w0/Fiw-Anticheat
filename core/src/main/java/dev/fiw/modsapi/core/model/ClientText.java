package dev.fiw.modsapi.core.model;

/**
 * Cleans strings reported by a client before they reach logs, profiles or staff chat:
 * control characters (newlines could forge extra console lines) and the legacy
 * {@code §} formatting code (could recolour or fake {@code /fiwmods profile} output)
 * are removed.
 */
public final class ClientText {

    private ClientText() {}

    public static String clean(String s) {
        if (s == null || s.isEmpty()) return s;
        StringBuilder out = null;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean drop = c == '§' || Character.isISOControl(c);
            if (drop && out == null) {
                out = new StringBuilder(s.length()).append(s, 0, i);
            } else if (!drop && out != null) {
                out.append(c);
            }
        }
        return out == null ? s : out.toString();
    }
}
