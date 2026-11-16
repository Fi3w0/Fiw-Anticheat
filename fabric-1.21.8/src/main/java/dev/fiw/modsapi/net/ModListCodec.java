package dev.fiw.modsapi.net;

import dev.fiw.modsapi.core.model.ModEntry;
import dev.fiw.modsapi.core.model.ModMarkers;
import dev.fiw.modsapi.core.model.ResourcePackEntry;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/** Reads/writes a reported mod list on a {@link FriendlyByteBuf}, with sane caps. */
public final class ModListCodec {

    private static final int MAX_MODS = 4000;
    private static final int MAX_PACKS = 1024;
    private static final int MAX_LIST = 512;

    private ModListCodec() {}

    public static void writeMods(FriendlyByteBuf buf, List<ModEntry> mods) {
        buf.writeVarInt(mods.size());
        for (ModEntry m : mods) {
            buf.writeUtf(m.id(), 256);
            buf.writeUtf(m.version(), 128);
            buf.writeUtf(m.fingerprint(), 128);
            writeStringList(buf, m.markers().mixinConfigs());
            writeStringList(buf, m.markers().entrypoints());
            writeStringList(buf, m.markers().packages());
        }
    }

    public static List<ModEntry> readMods(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), MAX_MODS);
        List<ModEntry> mods = new ArrayList<>(Math.max(0, n));
        for (int i = 0; i < n; i++) {
            String id = buf.readUtf(256);
            String version = buf.readUtf(128);
            String fingerprint = buf.readUtf(128);
            List<String> mixins = readStringList(buf);
            List<String> entrypoints = readStringList(buf);
            List<String> packages = readStringList(buf);
            mods.add(new ModEntry(id, version, fingerprint, new ModMarkers(mixins, entrypoints, packages)));
        }
        return mods;
    }

    public static void writeResourcePacks(FriendlyByteBuf buf, List<ResourcePackEntry> packs) {
        if (packs == null) packs = List.of();
        int size = Math.min(packs.size(), MAX_PACKS);
        buf.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            ResourcePackEntry pack = packs.get(i);
            buf.writeUtf(pack.id(), 256);
            buf.writeUtf(pack.displayName(), 256);
            buf.writeUtf(pack.fingerprint(), 128);
            buf.writeBoolean(pack.active());
        }
    }

    public static List<ResourcePackEntry> readResourcePacks(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), MAX_PACKS);
        List<ResourcePackEntry> packs = new ArrayList<>(Math.max(0, n));
        for (int i = 0; i < n; i++) {
            String id = buf.readUtf(256);
            String name = buf.readUtf(256);
            String fingerprint = buf.readUtf(128);
            boolean active = buf.readBoolean();
            packs.add(new ResourcePackEntry(id, name, fingerprint, active));
        }
        return packs;
    }

    private static void writeStringList(FriendlyByteBuf buf, List<String> list) {
        int size = Math.min(list.size(), MAX_LIST);
        buf.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            buf.writeUtf(list.get(i), 256);
        }
    }

    private static List<String> readStringList(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), MAX_LIST);
        List<String> out = new ArrayList<>(Math.max(0, n));
        for (int i = 0; i < n; i++) {
            out.add(buf.readUtf(256));
        }
        return out;
    }
}
