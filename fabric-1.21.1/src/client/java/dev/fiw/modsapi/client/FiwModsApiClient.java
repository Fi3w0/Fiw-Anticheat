package dev.fiw.modsapi.client;

import dev.fiw.modsapi.ModEnumerator;
import dev.fiw.modsapi.core.model.ModEntry;
import dev.fiw.modsapi.core.model.ResourcePackEntry;
import dev.fiw.modsapi.core.resourcepack.ResourcePackScanner;
import dev.fiw.modsapi.net.Payloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/** Client side (1.21.1): on challenge, enumerate mods and reply with list + nonce. */
public final class FiwModsApiClient implements ClientModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("fiw-mods-api");
    private static final int PACK_SCAN_INTERVAL_TICKS = 200;

    private static int packScanTicks;
    private static String lastResourcePackKey = "";

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(Payloads.ChallengePayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    try {
                        List<ModEntry> mods = ModEnumerator.collect();
                        List<ResourcePackEntry> resourcePacks = collectResourcePacks();
                        ClientPlayNetworking.send(new Payloads.ResponsePayload(mods, resourcePacks, payload.nonce()));
                        rememberResourcePacks(resourcePacks);
                    } catch (Exception e) {
                        LOGGER.error("[FiwAntiCheat] Failed to send verification response", e);
                    }
                }));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.getNetworkHandler() == null) return;
            if (++packScanTicks < PACK_SCAN_INTERVAL_TICKS) return;
            packScanTicks = 0;
            try {
                List<ResourcePackEntry> resourcePacks = collectResourcePacks();
                String key = ResourcePackScanner.stableKey(resourcePacks);
                if (key.equals(lastResourcePackKey)) return;
                lastResourcePackKey = key;
                ClientPlayNetworking.send(new Payloads.ResourcePackUpdatePayload(resourcePacks));
            } catch (Exception e) {
                LOGGER.error("[FiwAntiCheat] Failed to send resource pack update", e);
            }
        });
    }

    private static List<ResourcePackEntry> collectResourcePacks() {
        return ResourcePackScanner.collect(FabricLoader.getInstance().getGameDir());
    }

    private static void rememberResourcePacks(List<ResourcePackEntry> resourcePacks) {
        lastResourcePackKey = ResourcePackScanner.stableKey(resourcePacks);
    }
}
