package dev.fiw.modsapi.core.snapshot;

import dev.fiw.modsapi.core.config.ModConfig;
import dev.fiw.modsapi.core.model.ModEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a reported/enumerated mod list into the {@code whitelist.official_mods}
 * set captured by {@code /fiwmods snapshot}. Each entry pins the content
 * fingerprint (anti-impersonation); version is stored for reference.
 */
public final class SnapshotWriter {

    private SnapshotWriter() {}

    public static List<ModConfig.OfficialMod> toOfficialMods(List<ModEntry> mods) {
        List<ModConfig.OfficialMod> out = new ArrayList<>(mods.size());
        for (ModEntry m : mods) {
            String fp = (m.fingerprint() == null || m.fingerprint().isEmpty()) ? null : m.fingerprint();
            out.add(new ModConfig.OfficialMod(m.id(), m.version(), fp));
        }
        return out;
    }
}
