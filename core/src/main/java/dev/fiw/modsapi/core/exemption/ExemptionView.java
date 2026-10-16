package dev.fiw.modsapi.core.exemption;

import dev.fiw.modsapi.core.config.ModConfig;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Formats {@code /fiwmods exempt list} output. Mirrors {@code profile.ProfileView}'s role. */
public final class ExemptionView {

    private ExemptionView() {}

    public enum LineType { HEADER, ENTRY, EMPTY }

    public record CommandLine(LineType type, String text) {}

    public static List<CommandLine> commandRows(ModConfig config) {
        return commandRows(config, System.currentTimeMillis());
    }

    static List<CommandLine> commandRows(ModConfig config, long now) {
        List<CommandLine> lines = new ArrayList<>();
        Map<String, ModConfig.PlayerOverride> overrides = config.exemptions.player_overrides;
        List<String> bypassLegacy = config.exemptions.bypass_players;

        List<Map.Entry<String, ModConfig.PlayerOverride>> active = overrides == null ? List.of()
                : overrides.entrySet().stream()
                    .filter(e -> e.getValue() != null && (e.getValue().expires_at == null || e.getValue().expires_at > now))
                    .toList();
        int total = active.size() + (bypassLegacy == null ? 0 : bypassLegacy.size());
        lines.add(new CommandLine(LineType.HEADER, "[FiwAntiCheat] " + total + " active exemption(s); expiry times: UTC"));

        if (total == 0) {
            lines.add(new CommandLine(LineType.EMPTY, "  none"));
            return lines;
        }

        if (overrides != null) {
            for (Map.Entry<String, ModConfig.PlayerOverride> e : active) {
                lines.add(new CommandLine(LineType.ENTRY, "  " + e.getKey() + " -> " + describe(e.getValue())));
            }
        }
        if (bypassLegacy != null) {
            for (String key : bypassLegacy) {
                lines.add(new CommandLine(LineType.ENTRY, "  " + key + " -> bypass (permanent, legacy list)"));
            }
        }
        return lines;
    }

    private static String describe(ModConfig.PlayerOverride override) {
        StringBuilder sb = new StringBuilder(override.tier);
        if ("preset".equalsIgnoreCase(override.tier) && override.preset != null) {
            sb.append(" [").append(override.preset).append("]");
        }
        if (override.expires_at == null) {
            sb.append(" (permanent)");
        } else {
            sb.append(" (expires ").append(Instant.ofEpochMilli(override.expires_at)).append(")");
        }
        if (override.reason != null && !override.reason.isBlank()) {
            sb.append(" — ").append(override.reason);
        }
        return sb.toString();
    }
}
