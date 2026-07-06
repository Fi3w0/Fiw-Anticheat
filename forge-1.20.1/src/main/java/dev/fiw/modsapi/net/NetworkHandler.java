package dev.fiw.modsapi.net;

import dev.fiw.modsapi.FiwModsApi;
import dev.fiw.modsapi.ModEnumerator;
import dev.fiw.modsapi.core.model.ModEntry;
import dev.fiw.modsapi.core.model.ModMarkers;
import dev.fiw.modsapi.core.model.ResourcePackEntry;
import dev.fiw.modsapi.core.resourcepack.ResourcePackScanner;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** Forge SimpleChannel handshake: server challenge, client mod report response. */
public final class NetworkHandler {

    private static final String PROTOCOL = "1";
    private static final int MAX_MODS = 4000;
    private static final int MAX_PACKS = 1024;
    private static final int MAX_LIST = 512;
    private static final AtomicBoolean PACK_REPORTER_STARTED = new AtomicBoolean();
    private static final ScheduledExecutorService PACK_REPORTER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "FiwAntiCheat Resource Pack Reporter");
        thread.setDaemon(true);
        return thread;
    });
    private static volatile String lastResourcePackKey = "";

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
        CHANNEL.registerMessage(++id, ResourcePackUpdateMessage.class,
                ResourcePackUpdateMessage::encode,
                ResourcePackUpdateMessage::decode,
                ResourcePackUpdateMessage::handle,
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
                    List<ResourcePackEntry> resourcePacks = collectResourcePacks();
                    CHANNEL.sendToServer(new ResponseMessage(mods, resourcePacks, msg.nonce));
                    startResourcePackReporter(resourcePacks);
                } catch (Exception e) {
                    FiwModsApi.LOGGER.error("[FiwAntiCheat] Failed to send verification response", e);
                }
            });
            context.setPacketHandled(true);
        }
    }

    private record ResponseMessage(List<ModEntry> mods, List<ResourcePackEntry> resourcePacks, byte[] nonce) {
        static void encode(ResponseMessage msg, FriendlyByteBuf buf) {
            writeMods(buf, msg.mods);
            writeResourcePacks(buf, msg.resourcePacks);
            buf.writeByteArray(msg.nonce);
        }

        static ResponseMessage decode(FriendlyByteBuf buf) {
            List<ModEntry> mods = readMods(buf);
            List<ResourcePackEntry> resourcePacks = readResourcePacks(buf);
            byte[] nonce = buf.readByteArray(64);
            return new ResponseMessage(mods, resourcePacks, nonce);
        }

        static void handle(ResponseMessage msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            ServerPlayer player = context.getSender();
            if (player != null) {
                context.enqueueWork(() -> FiwModsApi.handleResponse(player.server, player,
                        msg.mods, msg.resourcePacks, msg.nonce));
            }
            context.setPacketHandled(true);
        }
    }

    private record ResourcePackUpdateMessage(List<ResourcePackEntry> resourcePacks) {
        static void encode(ResourcePackUpdateMessage msg, FriendlyByteBuf buf) {
            writeResourcePacks(buf, msg.resourcePacks);
        }

        static ResourcePackUpdateMessage decode(FriendlyByteBuf buf) {
            return new ResourcePackUpdateMessage(readResourcePacks(buf));
        }

        static void handle(ResourcePackUpdateMessage msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            ServerPlayer player = context.getSender();
            if (player != null) {
                context.enqueueWork(() -> FiwModsApi.handleResourcePackUpdate(player.server, player, msg.resourcePacks));
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

    private static void writeResourcePacks(FriendlyByteBuf buf, List<ResourcePackEntry> packs) {
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

    private static List<ResourcePackEntry> readResourcePacks(FriendlyByteBuf buf) {
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
        for (int i = 0; i < size; i++) buf.writeUtf(list.get(i), 256);
    }

    private static List<String> readStringList(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), MAX_LIST);
        List<String> out = new ArrayList<>(Math.max(0, n));
        for (int i = 0; i < n; i++) out.add(buf.readUtf(256));
        return out;
    }

    private static List<ResourcePackEntry> collectResourcePacks() {
        return ResourcePackScanner.collect(FMLPaths.GAMEDIR.get());
    }

    private static void startResourcePackReporter(List<ResourcePackEntry> initial) {
        lastResourcePackKey = ResourcePackScanner.stableKey(initial);
        if (!PACK_REPORTER_STARTED.compareAndSet(false, true)) return;
        PACK_REPORTER.scheduleAtFixedRate(() -> {
            try {
                List<ResourcePackEntry> resourcePacks = collectResourcePacks();
                String key = ResourcePackScanner.stableKey(resourcePacks);
                if (key.equals(lastResourcePackKey)) return;
                lastResourcePackKey = key;
                CHANNEL.sendToServer(new ResourcePackUpdateMessage(resourcePacks));
            } catch (Exception e) {
                FiwModsApi.LOGGER.error("[FiwAntiCheat] Failed to send resource pack update", e);
            }
        }, 10, 10, TimeUnit.SECONDS);
    }
}
