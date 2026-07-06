package dev.fiw.modsapi.net;

import dev.fiw.modsapi.core.model.ModEntry;
import dev.fiw.modsapi.core.model.ResourcePackEntry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.List;

/** 1.21.11 custom payloads for the verification handshake. */
public final class Payloads {

    private Payloads() {}

    public record ChallengePayload(byte[] nonce) implements CustomPayload {
        public static final CustomPayload.Id<ChallengePayload> ID =
                new CustomPayload.Id<>(Identifier.of("fiw-mods-api", "challenge"));

        public static final PacketCodec<RegistryByteBuf, ChallengePayload> CODEC = PacketCodec.of(
                (value, buf) -> buf.writeByteArray(value.nonce),
                buf -> new ChallengePayload(buf.readByteArray()));

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record ResponsePayload(List<ModEntry> mods, List<ResourcePackEntry> resourcePacks, byte[] nonce) implements CustomPayload {
        public static final CustomPayload.Id<ResponsePayload> ID =
                new CustomPayload.Id<>(Identifier.of("fiw-mods-api", "response"));

        public static final PacketCodec<RegistryByteBuf, ResponsePayload> CODEC = PacketCodec.of(
                (value, buf) -> {
                    ModListCodec.writeMods(buf, value.mods);
                    ModListCodec.writeResourcePacks(buf, value.resourcePacks);
                    buf.writeByteArray(value.nonce);
                },
                buf -> {
                    List<ModEntry> mods = ModListCodec.readMods(buf);
                    List<ResourcePackEntry> resourcePacks = ModListCodec.readResourcePacks(buf);
                    byte[] nonce = buf.readByteArray();
                    return new ResponsePayload(mods, resourcePacks, nonce);
                });

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record ResourcePackUpdatePayload(List<ResourcePackEntry> resourcePacks) implements CustomPayload {
        public static final CustomPayload.Id<ResourcePackUpdatePayload> ID =
                new CustomPayload.Id<>(Identifier.of("fiw-mods-api", "resource_pack_update"));

        public static final PacketCodec<RegistryByteBuf, ResourcePackUpdatePayload> CODEC = PacketCodec.of(
                (value, buf) -> ModListCodec.writeResourcePacks(buf, value.resourcePacks),
                buf -> new ResourcePackUpdatePayload(ModListCodec.readResourcePacks(buf)));

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
