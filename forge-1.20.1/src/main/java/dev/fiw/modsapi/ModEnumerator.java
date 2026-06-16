package dev.fiw.modsapi;

import dev.fiw.modsapi.core.model.ModEntry;
import dev.fiw.modsapi.core.model.ModMarkers;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.forgespi.locating.IModFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/** Enumerates Forge mods into the loader-agnostic {@link ModEntry} model. */
public final class ModEnumerator {

    private static final Set<String> BUILTIN_SKIP = Set.of("minecraft", "forge", "java", "fml");
    private static final Set<String> NON_CODE_PKG_ROOTS = Set.of("META-INF", "assets", "data", "mappings");

    private ModEnumerator() {}

    public static List<ModEntry> collect() {
        List<ModEntry> entries = new ArrayList<>();
        for (IModInfo info : ModList.get().getMods()) {
            String id = info.getModId();
            if (BUILTIN_SKIP.contains(id)) continue;

            String version = info.getVersion().toString();
            IModFile file = info.getOwningFile().getFile();
            String fingerprint = fingerprint(file);
            ModMarkers markers = markers(file);
            entries.add(new ModEntry(id, version, fingerprint, markers));
        }
        Collections.sort(entries);
        return entries;
    }

    private static String fingerprint(IModFile file) {
        try {
            Path jar = file.getFilePath();
            if (jar == null || !Files.isRegularFile(jar)) return "";
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream is = Files.newInputStream(jar)) {
                byte[] buf = new byte[8192];
                int read;
                while ((read = is.read(buf)) != -1) digest.update(buf, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            return "";
        }
    }

    private static ModMarkers markers(IModFile file) {
        List<String> packages = new ArrayList<>();
        try {
            java.util.Set<String> pkgs = file.getSecureJar().moduleDataProvider().descriptor().packages();
            for (String pkg : pkgs) {
                if (pkg == null || pkg.isEmpty()) continue;
                String root = pkg.split("\\.")[0];
                if (NON_CODE_PKG_ROOTS.contains(root)) continue;
                packages.add(pkg);
                if (packages.size() >= 64) break;
            }
        } catch (Exception ignored) {
        }
        return new ModMarkers(List.of(), List.of(), packages);
    }
}
