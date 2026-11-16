package dev.fiw.modsapi.net;

import dev.fiw.modsapi.FiwModsApi;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/** Server-side payload registration + sending for 1.21.8. */
public final class ServerNetworking {

    private ServerNetworking() {}

    public static void register() {
        // Payload types must be registered on both sides (main entrypoint runs on both).
        PayloadTypeRegistry.playS2C().register(Payloads.ChallengePayload.ID, Payloads.ChallengePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(Payloads.ResponsePayload.ID, Payloads.ResponsePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(Payloads.ResourcePackUpdatePayload.ID, Payloads.ResourcePackUpdatePayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(Payloads.ResponsePayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() ->
                    FiwModsApi.handleResponse(context.server(), player,
                            payload.mods(), payload.resourcePacks(), payload.nonce()));
        });

        ServerPlayNetworking.registerGlobalReceiver(Payloads.ResourcePackUpdatePayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() ->
                    FiwModsApi.handleResourcePackUpdate(context.server(), player, payload.resourcePacks()));
        });
    }

    public static void sendChallenge(ServerPlayer player, byte[] nonce) {
        ServerPlayNetworking.send(player, new Payloads.ChallengePayload(nonce));
    }
}
