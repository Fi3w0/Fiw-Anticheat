package dev.fiw.modsapi.net;

import dev.fiw.modsapi.core.model.ModEntry;
import dev.fiw.modsapi.core.model.ResourcePackEntry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * 26.1.2 custom payloads for the verification handshake. Channel ids and byte layout are the
 * same as every other Fabric target, so companions and servers of one release understand each other.
 */
public final class Payloads {

    private Payloads() {}

    public record ChallengePayload(byte[] nonce) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ChallengePayload> ID =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("fiw-mods-api", "challenge"));

        public static final StreamCodec<RegistryFriendlyByteBuf, ChallengePayload> CODEC = StreamCodec.of(
                (buf, value) -> buf.writeByteArray(value.nonce),
                buf -> new ChallengePayload(buf.readByteArray()));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record ResponsePayload(List<ModEntry> mods, List<ResourcePackEntry> resourcePacks, byte[] nonce) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ResponsePayload> ID =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("fiw-mods-api", "response"));

        public static final StreamCodec<RegistryFriendlyByteBuf, ResponsePayload> CODEC = StreamCodec.of(
                (buf, value) -> {
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
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record ResourcePackUpdatePayload(List<ResourcePackEntry> resourcePacks) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ResourcePackUpdatePayload> ID =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("fiw-mods-api", "resource_pack_update"));

        public static final StreamCodec<RegistryFriendlyByteBuf, ResourcePackUpdatePayload> CODEC = StreamCodec.of(
                (buf, value) -> ModListCodec.writeResourcePacks(buf, value.resourcePacks),
                buf -> new ResourcePackUpdatePayload(ModListCodec.readResourcePacks(buf)));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }
}
