package dev.fiw.modsapi.net;

import dev.fiw.modsapi.FiwModsApi;
import dev.fiw.modsapi.ModEnumerator;
import dev.fiw.modsapi.core.model.ModEntry;
import dev.fiw.modsapi.core.model.ModMarkers;
import dev.fiw.modsapi.core.model.ResourcePackEntry;
import dev.fiw.modsapi.core.resourcepack.ResourcePackScanner;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** NeoForge custom-payload handshake: server → challenge, client → mod list + nonce. */
public final class NetworkHandler {

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

    private NetworkHandler() {}

    // --- payloads ---

    public record ChallengePayload(byte[] nonce) implements CustomPacketPayload {
        public static final Type<ChallengePayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(FiwModsApi.MOD_ID, "challenge"));

        public static final StreamCodec<ByteBuf, ChallengePayload> STREAM_CODEC = StreamCodec.of(
                (buf, msg) -> ((FriendlyByteBuf) buf).writeByteArray(msg.nonce),
                buf -> new ChallengePayload(((FriendlyByteBuf) buf).readByteArray(64)));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ResponsePayload(List<ModEntry> mods, List<ResourcePackEntry> resourcePacks, byte[] nonce) implements CustomPacketPayload {
        public static final Type<ResponsePayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(FiwModsApi.MOD_ID, "response"));

        public static final StreamCodec<ByteBuf, ResponsePayload> STREAM_CODEC = StreamCodec.of(
                NetworkHandler::encode, NetworkHandler::decode);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ResourcePackUpdatePayload(List<ResourcePackEntry> resourcePacks) implements CustomPacketPayload {
        public static final Type<ResourcePackUpdatePayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(FiwModsApi.MOD_ID, "resource_pack_update"));

        public static final StreamCodec<ByteBuf, ResourcePackUpdatePayload> STREAM_CODEC = StreamCodec.of(
                NetworkHandler::encodeResourcePackUpdate, NetworkHandler::decodeResourcePackUpdate);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private static void encode(ByteBuf raw, ResponsePayload msg) {
        FriendlyByteBuf buf = (FriendlyByteBuf) raw;
        buf.writeVarInt(msg.mods.size());
        for (ModEntry m : msg.mods) {
            buf.writeUtf(m.id(), 256);
            buf.writeUtf(m.version(), 128);
            buf.writeUtf(m.fingerprint(), 128);
            writeStringList(buf, m.markers().mixinConfigs());
            writeStringList(buf, m.markers().entrypoints());
            writeStringList(buf, m.markers().packages());
        }
        writeResourcePacks(buf, msg.resourcePacks);
        buf.writeByteArray(msg.nonce);
    }

    private static ResponsePayload decode(ByteBuf raw) {
        FriendlyByteBuf buf = (FriendlyByteBuf) raw;
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
        List<ResourcePackEntry> resourcePacks = readResourcePacks(buf);
        byte[] nonce = buf.readByteArray(64);
        return new ResponsePayload(mods, resourcePacks, nonce);
    }

    private static void encodeResourcePackUpdate(ByteBuf raw, ResourcePackUpdatePayload msg) {
        writeResourcePacks((FriendlyByteBuf) raw, msg.resourcePacks);
    }

    private static ResourcePackUpdatePayload decodeResourcePackUpdate(ByteBuf raw) {
        return new ResourcePackUpdatePayload(readResourcePacks((FriendlyByteBuf) raw));
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

    // --- registration + handlers ---

    public static void register(RegisterPayloadHandlersEvent event) {
        // optional() so vanilla / non-modded clients can still connect (they then
        // never respond and are kicked on timeout by the verification logic).
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToClient(ChallengePayload.TYPE, ChallengePayload.STREAM_CODEC,
                NetworkHandler::onChallengeClient);
        registrar.playToServer(ResponsePayload.TYPE, ResponsePayload.STREAM_CODEC,
                NetworkHandler::onResponseServer);
        registrar.playToServer(ResourcePackUpdatePayload.TYPE, ResourcePackUpdatePayload.STREAM_CODEC,
                NetworkHandler::onResourcePackUpdateServer);
    }

    private static void onChallengeClient(ChallengePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            try {
                List<ModEntry> mods = ModEnumerator.collect();
                List<ResourcePackEntry> resourcePacks = collectResourcePacks();
                PacketDistributor.sendToServer(new ResponsePayload(mods, resourcePacks, payload.nonce));
                startResourcePackReporter(resourcePacks);
            } catch (Exception e) {
                FiwModsApi.LOGGER.error("[FiwAntiCheat] Failed to send verification response", e);
            }
        });
    }

    private static void onResponseServer(ResponsePayload payload, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer sp) {
            sp.server.execute(() -> FiwModsApi.handleResponse(sp.server, sp,
                    payload.mods, payload.resourcePacks, payload.nonce));
        }
    }

    private static void onResourcePackUpdateServer(ResourcePackUpdatePayload payload, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer sp) {
            sp.server.execute(() -> FiwModsApi.handleResourcePackUpdate(sp.server, sp, payload.resourcePacks));
        }
    }

    public static void sendChallenge(ServerPlayer player, byte[] nonce) {
        PacketDistributor.sendToPlayer(player, new ChallengePayload(nonce));
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
                PacketDistributor.sendToServer(new ResourcePackUpdatePayload(resourcePacks));
            } catch (Exception e) {
                FiwModsApi.LOGGER.error("[FiwAntiCheat] Failed to send resource pack update", e);
            }
        }, 10, 10, TimeUnit.SECONDS);
    }
}
