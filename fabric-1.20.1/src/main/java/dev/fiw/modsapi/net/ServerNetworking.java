package dev.fiw.modsapi.net;

import dev.fiw.modsapi.FiwModsApi;
import dev.fiw.modsapi.core.model.ModEntry;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

/** Server-side registration: receives client responses, sends nonce challenges. */
public final class ServerNetworking {

    private ServerNetworking() {}

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(Channels.RESPONSE,
                (server, player, handler, buf, sender) -> {
                    // Decode on the network thread, then hand off to the main thread.
                    List<ModEntry> mods = ModListCodec.readMods(buf);
                    byte[] nonceEcho = buf.readByteArray(64);
                    server.execute(() -> FiwModsApi.handleResponse(server, player, mods, nonceEcho));
                });
    }

    public static void sendChallenge(ServerPlayerEntity player, byte[] nonce) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeByteArray(nonce);
        ServerPlayNetworking.send(player, Channels.CHALLENGE, buf);
    }
}
