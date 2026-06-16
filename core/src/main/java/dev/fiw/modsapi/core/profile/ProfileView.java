package dev.fiw.modsapi.core.profile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Groups noisy loader/API entries away from player-installed mods. */
public final class ProfileView {

    private ProfileView() {}

    public enum LineType {
        HEADER,
        MODS,
        PLATFORM,
        CHANGES,
        EVENT,
        EMPTY
    }

    public record Groups(List<PlayerProfile.Mod> visibleMods, List<PlayerProfile.Mod> platformMods) {}
    public record CommandLine(LineType type, String text) {}

    public static Groups group(PlayerProfile profile) {
        List<PlayerProfile.Mod> source = profile.currentMods == null ? List.of() : profile.currentMods;
        return groupMods(source);
    }

    public static Groups groupMods(List<PlayerProfile.Mod> mods) {
        List<PlayerProfile.Mod> visible = new ArrayList<>();
        List<PlayerProfile.Mod> platform = new ArrayList<>();
        for (PlayerProfile.Mod mod : mods) {
            if (isPlatformNoise(mod.id())) {
                platform.add(mod);
            } else {
                visible.add(mod);
            }
        }
        return new Groups(visible, platform);
    }

    public static List<String> commandLines(PlayerProfile profile, String requestedName) {
        return commandRows(profile, requestedName).stream().map(CommandLine::text).toList();
    }

    public static List<CommandLine> commandRows(PlayerProfile profile, String requestedName) {
        Groups groups = group(profile);
        int total = profile.currentMods == null ? 0 : profile.currentMods.size();
        List<CommandLine> lines = new ArrayList<>();
        lines.add(new CommandLine(LineType.HEADER, "[FiwAntiCheat] " + requestedName + " - " + groups.visibleMods().size()
                + " mods, " + groups.platformMods().size() + " platform entries, "
                + total + " total, " + profile.joins + " joins (first seen " + profile.firstSeen + ")"));
        lines.addAll(wrapped(LineType.MODS, "Mods", groups.visibleMods()));
        lines.addAll(wrapped(LineType.PLATFORM, "Platform", groups.platformMods()));
        lines.add(new CommandLine(LineType.CHANGES, "Recent changes:"));
        int from = profile.history == null ? 0 : Math.max(0, profile.history.size() - 8);
        int size = profile.history == null ? 0 : profile.history.size();
        if (from == size) {
            lines.add(new CommandLine(LineType.EMPTY, "  none"));
        } else {
            for (int i = from; i < size; i++) {
                PlayerProfile.Event e = profile.history.get(i);
                lines.add(new CommandLine(LineType.EVENT, "  " + e.time + "  " + e.event + " " + e.id
                        + (e.to != null ? " (" + e.from + " -> " + e.to + ")"
                        : (e.version != null ? " " + e.version : ""))));
            }
        }
        return lines;
    }

    private static List<CommandLine> wrapped(LineType type, String label, List<PlayerProfile.Mod> mods) {
        List<CommandLine> lines = new ArrayList<>();
        if (mods.isEmpty()) {
            lines.add(new CommandLine(type, label + " (0): none"));
            return lines;
        }
        String prefix = label + " (" + mods.size() + "): ";
        StringBuilder line = new StringBuilder(prefix);
        for (int i = 0; i < mods.size(); i++) {
            String item = mods.get(i).id() + versionSuffix(mods.get(i).version());
            String next = (line.length() == prefix.length() ? "" : ", ") + item;
            if (line.length() + next.length() > 180) {
                lines.add(new CommandLine(type, line.toString()));
                line = new StringBuilder("  ");
                next = item;
            }
            line.append(next);
        }
        lines.add(new CommandLine(type, line.toString()));
        return lines;
    }

    private static String versionSuffix(String version) {
        return version == null || version.isEmpty() ? "" : "@" + version;
    }

    private static boolean isPlatformNoise(String rawId) {
        if (rawId == null) return false;
        String id = rawId.toLowerCase(Locale.ROOT);
        return id.equals("minecraft")
                || id.equals("java")
                || id.equals("fabricloader")
                || id.equals("fabric-api")
                || id.startsWith("fabric-")
                || id.equals("forge")
                || id.equals("neoforge")
                || id.equals("fml")
                || id.equals("mixinextras")
                || id.equals("fiw-mods-api")
                || id.equals("fiw_mods_api");
    }
}
