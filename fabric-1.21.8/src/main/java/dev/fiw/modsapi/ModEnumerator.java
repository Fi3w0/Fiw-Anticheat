package dev.fiw.modsapi;

import dev.fiw.modsapi.core.model.ModEntry;
import dev.fiw.modsapi.core.model.ModMarkers;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * Enumerates the Fabric mods present in this runtime into the loader-agnostic
 * {@link ModEntry} model: id, version, jar fingerprint, and stable markers
 * (mixin configs, entrypoint classes, root packages). Used by the client to
 * report itself and by the server for {@code /fiwmods snapshot server}.
 *
 * <p>Built-in environment entries (minecraft/java/loader) are skipped; everything
 * else — including nested and library jars — is reported so coverage is complete
 * and snapshots capture the whole set without admins hand-listing anything.
 */
public final class ModEnumerator {

    private static final Set<String> BUILTIN_SKIP = Set.of("minecraft", "java", "fabricloader");

    private ModEnumerator() {}

    public static List<ModEntry> collect() {
        List<ModEntry> entries = new ArrayList<>();
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            ModMetadata meta = mod.getMetadata();
            String id = meta.getId();
            if (BUILTIN_SKIP.contains(id)) continue;

            String version = meta.getVersion().getFriendlyString();
            String fingerprint = fingerprint(mod);
            ModMarkers markers = markers(mod);
            entries.add(new ModEntry(id, version, fingerprint, markers));
        }
        Collections.sort(entries);
        return entries;
    }

    private static String fingerprint(ModContainer mod) {
        try {
            List<Path> origins = mod.getOrigin().getPaths();
            if (origins.isEmpty()) return "";
            Path p = origins.get(0);
            if (!Files.isRegularFile(p)) return ""; // dev classpath dir — no stable jar hash
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream is = Files.newInputStream(p)) {
                byte[] buf = new byte[8192];
                int read;
                while ((read = is.read(buf)) != -1) digest.update(buf, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Derive stable markers by walking the mod's jar filesystem for the root
     * Java packages of its classes (e.g. {@code net.xolt.freecam}). These survive
     * a jar rename and an internal mod-id rename, so signature matching still works.
     */
    private static ModMarkers markers(ModContainer mod) {
        Set<String> packages = new java.util.LinkedHashSet<>();
        try {
            for (Path root : mod.getRootPaths()) {
                collectPackages(root, packages);
            }
        } catch (Exception ignored) {
            // best effort — id-based matching still applies
        }
        return new ModMarkers(List.of(), List.of(), new ArrayList<>(packages));
    }

    private static final Set<String> NON_CODE_ROOTS =
            Set.of("META-INF", "assets", "data", "mappings");

    /** Collect up to 3-segment package prefixes that contain class files. */
    private static void collectPackages(Path root, Set<String> out) throws java.io.IOException {
        try (var top = Files.list(root)) {
            top.filter(Files::isDirectory).forEach(dir -> {
                String name = dir.getFileName().toString().replace("/", "");
                if (NON_CODE_ROOTS.contains(name)) return;
                walkForPackages(dir, name, 1, out);
            });
        }
    }

    private static void walkForPackages(Path dir, String prefix, int depth, Set<String> out) {
        if (out.size() > 64) return;
        try (var children = Files.list(dir)) {
            List<Path> kids = children.toList();
            boolean hasClass = kids.stream().anyMatch(p -> p.getFileName().toString().endsWith(".class"));
            if (hasClass) out.add(prefix);
            if (depth >= 3) return;
            for (Path kid : kids) {
                if (Files.isDirectory(kid)) {
                    String seg = kid.getFileName().toString().replace("/", "");
                    walkForPackages(kid, prefix + "." + seg, depth + 1, out);
                }
            }
        } catch (Exception ignored) {
        }
    }
}
