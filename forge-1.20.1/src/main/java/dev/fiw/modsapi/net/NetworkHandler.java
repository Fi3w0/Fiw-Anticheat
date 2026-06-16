package dev.fiw.modsapi.net;

import dev.fiw.modsapi.FiwModsApi;
import dev.fiw.modsapi.ModEnumerator;
import dev.fiw.modsapi.core.model.ModEntry;
import dev.fiw.modsapi.core.model.ModMarkers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** Forge SimpleChannel handshake: server challenge, client mod report response. */
public final class NetworkHandler {

    private static final String PROTOCOL = "1";
    private static final int MAX_MODS = 4000;
    private static final int MAX_LIST = 512;

    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(FiwModsApi.MOD_ID, "main"))
            .networkProtocolVersion(() -> PROTOCOL)
            .clientAcceptedVersions(PROTOCOL::equals)
            .serverAcceptedVersions(PROTOCOL::equals)
            .simpleChannel();

    private NetworkHandler() {}

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, ChallengeMessage.class,
                ChallengeMessage::encode,
                ChallengeMessage::decode,
                ChallengeMessage::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id, ResponseMessage.class,
                ResponseMessage::encode,
                ResponseMessage::decode,
                ResponseMessage::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    public static void sendChallenge(ServerPlayer player, byte[] nonce) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ChallengeMessage(nonce));
    }

    private record ChallengeMessage(byte[] nonce) {
        static void encode(ChallengeMessage msg, FriendlyByteBuf buf) {
            buf.writeByteArray(msg.nonce);
        }

        static ChallengeMessage decode(FriendlyByteBuf buf) {
            return new ChallengeMessage(buf.readByteArray(64));
        }

        static void handle(ChallengeMessage msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                try {
                    List<ModEntry> mods = ModEnumerator.collect();
                    CHANNEL.sendToServer(new ResponseMessage(mods, msg.nonce));
                } catch (Exception e) {
                    FiwModsApi.LOGGER.error("[FiwAntiCheat] Failed to send verification response", e);
                }
            });
            context.setPacketHandled(true);
        }
    }

    private record ResponseMessage(List<ModEntry> mods, byte[] nonce) {
        static void encode(ResponseMessage msg, FriendlyByteBuf buf) {
            writeMods(buf, msg.mods);
            buf.writeByteArray(msg.nonce);
        }

        static ResponseMessage decode(FriendlyByteBuf buf) {
            List<ModEntry> mods = readMods(buf);
            byte[] nonce = buf.readByteArray(64);
            return new ResponseMessage(mods, nonce);
        }

        static void handle(ResponseMessage msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            ServerPlayer player = context.getSender();
            if (player != null) {
                context.enqueueWork(() -> FiwModsApi.handleResponse(player.server, player, msg.mods, msg.nonce));
            }
            context.setPacketHandled(true);
        }
    }

    private static void writeMods(FriendlyByteBuf buf, List<ModEntry> mods) {
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

    private static List<ModEntry> readMods(FriendlyByteBuf buf) {
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

    private static void writeStringList(FriendlyByteBuf buf, List<String> list) {
        int size = Math.min(list.size(), MAX_LIST);
        buf.writeVarInt(size);
        for (int i = 0; i < size; i++) buf.writeUtf(list.get(i), 256);
    }

    private static List<String> readStringList(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), MAX_LIST);
        List<String> out = new ArrayList<>(Math.max(0, n));
        for (int i = 0; i < n; i++) out.add(buf.readUtf(256));
        return out;
    }
}
