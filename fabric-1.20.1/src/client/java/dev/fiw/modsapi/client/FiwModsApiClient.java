package dev.fiw.modsapi.client;

import dev.fiw.modsapi.ModEnumerator;
import dev.fiw.modsapi.core.model.ModEntry;
import dev.fiw.modsapi.net.Channels;
import dev.fiw.modsapi.net.ModListCodec;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Client side: on receiving a challenge, enumerate this client's mods and reply
 * with the list + the nonce echo. All heavy work happens off the network thread.
 */
public final class FiwModsApiClient implements ClientModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("fiw-mods-api");

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(Channels.CHALLENGE,
                (client, handler, buf, sender) -> {
                    final byte[] nonce;
                    try {
                        nonce = buf.readByteArray(64);
                    } catch (Exception e) {
                        LOGGER.error("[FiwAntiCheat] Bad challenge packet", e);
                        return;
                    }
                    client.execute(() -> {
                        try {
                            List<ModEntry> mods = ModEnumerator.collect();
                            PacketByteBuf response = PacketByteBufs.create();
                            ModListCodec.writeMods(response, mods);
                            response.writeByteArray(nonce);
                            ClientPlayNetworking.send(Channels.RESPONSE, response);
                        } catch (Exception e) {
                            LOGGER.error("[FiwAntiCheat] Failed to send verification response", e);
                        }
                    });
                });
    }
}
