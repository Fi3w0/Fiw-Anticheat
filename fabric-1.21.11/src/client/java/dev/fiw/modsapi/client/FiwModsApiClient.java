package dev.fiw.modsapi.client;

import dev.fiw.modsapi.ModEnumerator;
import dev.fiw.modsapi.core.model.ModEntry;
import dev.fiw.modsapi.net.Payloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/** Client side (1.21.11): on challenge, enumerate mods and reply with list + nonce. */
public final class FiwModsApiClient implements ClientModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("fiw-mods-api");

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(Payloads.ChallengePayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    try {
                        List<ModEntry> mods = ModEnumerator.collect();
                        ClientPlayNetworking.send(new Payloads.ResponsePayload(mods, payload.nonce()));
                    } catch (Exception e) {
                        LOGGER.error("[FiwAntiCheat] Failed to send verification response", e);
                    }
                }));
    }
}
