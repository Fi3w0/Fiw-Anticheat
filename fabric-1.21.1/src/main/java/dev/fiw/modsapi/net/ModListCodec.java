package dev.fiw.modsapi.net;

import dev.fiw.modsapi.core.model.ModEntry;
import dev.fiw.modsapi.core.model.ModMarkers;
import net.minecraft.network.PacketByteBuf;

import java.util.ArrayList;
import java.util.List;

/** Reads/writes a reported mod list on a {@link PacketByteBuf}, with sane caps. */
public final class ModListCodec {

    private static final int MAX_MODS = 4000;
    private static final int MAX_LIST = 512;

    private ModListCodec() {}

    public static void writeMods(PacketByteBuf buf, List<ModEntry> mods) {
        buf.writeVarInt(mods.size());
        for (ModEntry m : mods) {
            buf.writeString(m.id(), 256);
            buf.writeString(m.version(), 128);
            buf.writeString(m.fingerprint(), 128);
            writeStringList(buf, m.markers().mixinConfigs());
            writeStringList(buf, m.markers().entrypoints());
            writeStringList(buf, m.markers().packages());
        }
    }

    public static List<ModEntry> readMods(PacketByteBuf buf) {
        int n = Math.min(buf.readVarInt(), MAX_MODS);
        List<ModEntry> mods = new ArrayList<>(Math.max(0, n));
        for (int i = 0; i < n; i++) {
            String id = buf.readString(256);
            String version = buf.readString(128);
            String fingerprint = buf.readString(128);
            List<String> mixins = readStringList(buf);
            List<String> entrypoints = readStringList(buf);
            List<String> packages = readStringList(buf);
            mods.add(new ModEntry(id, version, fingerprint, new ModMarkers(mixins, entrypoints, packages)));
        }
        return mods;
    }

    private static void writeStringList(PacketByteBuf buf, List<String> list) {
        int size = Math.min(list.size(), MAX_LIST);
        buf.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            buf.writeString(list.get(i), 256);
        }
    }

    private static List<String> readStringList(PacketByteBuf buf) {
        int n = Math.min(buf.readVarInt(), MAX_LIST);
        List<String> out = new ArrayList<>(Math.max(0, n));
        for (int i = 0; i < n; i++) {
            out.add(buf.readString(256));
        }
        return out;
    }
}
