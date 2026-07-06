package dev.fiw.modsapi.core.resourcepack;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.fiw.modsapi.core.model.ResourcePackEntry;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Pure-Java scanner for the client's enabled and installed resource packs. */
public final class ResourcePackScanner {

    private ResourcePackScanner() {}

    public static List<ResourcePackEntry> collect(Path gameDir) {
        if (gameDir == null) return List.of();

        Set<String> activeIds = readActivePackIds(gameDir.resolve("options.txt"));
        Map<String, ResourcePackEntry> packs = new LinkedHashMap<>();

        Path resourcePacksDir = gameDir.resolve("resourcepacks");
        if (Files.isDirectory(resourcePacksDir)) {
            try (var children = Files.list(resourcePacksDir)) {
                children
                        .filter(ResourcePackScanner::looksLikeResourcePack)
                        .forEach(path -> addFilePack(packs, path, activeIds.contains(filePackId(path))));
            } catch (Exception ignored) {
                // best effort; active built-in packs below are still reported
            }
        }

        for (String activeId : activeIds) {
            packs.computeIfAbsent(activeId, id -> activePackOnly(gameDir, id));
        }

        List<ResourcePackEntry> out = new ArrayList<>(packs.values());
        Collections.sort(out);
        return out;
    }

    public static String stableKey(List<ResourcePackEntry> packs) {
        StringBuilder key = new StringBuilder();
        for (ResourcePackEntry pack : packs) {
            key.append(pack.id()).append('\t')
                    .append(pack.name()).append('\t')
                    .append(pack.fingerprint()).append('\t')
                    .append(pack.active()).append('\n');
        }
        return key.toString();
    }

    private static Set<String> readActivePackIds(Path optionsFile) {
        Set<String> ids = new LinkedHashSet<>();
        if (!Files.isRegularFile(optionsFile)) return ids;
        try {
            for (String line : Files.readAllLines(optionsFile, StandardCharsets.UTF_8)) {
                if (!line.startsWith("resourcePacks:")) continue;
                String raw = line.substring("resourcePacks:".length()).trim();
                ids.addAll(parsePackList(raw));
            }
        } catch (Exception ignored) {
        }
        return ids;
    }

    private static List<String> parsePackList(String raw) {
        List<String> out = new ArrayList<>();
        try {
            JsonElement parsed = JsonParser.parseString(raw);
            if (parsed.isJsonArray()) {
                for (JsonElement element : parsed.getAsJsonArray()) {
                    if (element.isJsonPrimitive()) out.add(normalizeActiveId(element.getAsString()));
                }
                return out;
            }
        } catch (Exception ignored) {
        }

        String trimmed = raw;
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        for (String part : trimmed.split(",")) {
            String value = part.trim();
            if ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'"))) {
                value = value.substring(1, value.length() - 1);
            }
            if (!value.isBlank()) out.add(normalizeActiveId(value));
        }
        return out;
    }

    private static String normalizeActiveId(String raw) {
        String id = raw == null ? "" : raw.trim();
        if (id.startsWith("file/")) return id;
        if (id.startsWith("file:")) return "file/" + id.substring("file:".length());
        return id;
    }

    private static boolean looksLikeResourcePack(Path path) {
        try {
            if (Files.isDirectory(path)) return Files.exists(path.resolve("pack.mcmeta"));
            String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
            return Files.isRegularFile(path) && name.endsWith(".zip");
        } catch (Exception e) {
            return false;
        }
    }

    private static void addFilePack(Map<String, ResourcePackEntry> packs, Path path, boolean active) {
        String id = filePackId(path);
        packs.put(id, new ResourcePackEntry(id, path.getFileName().toString(), fingerprint(path), active));
    }

    private static ResourcePackEntry activePackOnly(Path gameDir, String id) {
        if (id.startsWith("file/")) {
            Path path = gameDir.resolve("resourcepacks").resolve(id.substring("file/".length()));
            if (Files.exists(path)) {
                return new ResourcePackEntry(id, path.getFileName().toString(), fingerprint(path), true);
            }
        }
        return new ResourcePackEntry(id, friendlyBuiltInName(id), "", true);
    }

    private static String filePackId(Path path) {
        return "file/" + path.getFileName();
    }

    private static String friendlyBuiltInName(String id) {
        return switch (id) {
            case "vanilla" -> "Default";
            case "programmer_art" -> "Programmer Art";
            default -> id;
        };
    }

    private static String fingerprint(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            if (Files.isRegularFile(path)) {
                hashFile(digest, path);
            } else if (Files.isDirectory(path)) {
                try (var walk = Files.walk(path)) {
                    List<Path> files = walk.filter(Files::isRegularFile)
                            .sorted()
                            .toList();
                    for (Path file : files) {
                        String relative = path.relativize(file).toString().replace('\\', '/');
                        digest.update(relative.getBytes(StandardCharsets.UTF_8));
                        digest.update((byte) 0);
                        hashFile(digest, file);
                    }
                }
            } else {
                return "";
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            return "";
        }
    }

    private static void hashFile(MessageDigest digest, Path file) throws java.io.IOException {
        try (InputStream is = Files.newInputStream(file)) {
            byte[] buf = new byte[8192];
            int read;
            while ((read = is.read(buf)) != -1) {
                digest.update(buf, 0, read);
            }
        }
    }
}
