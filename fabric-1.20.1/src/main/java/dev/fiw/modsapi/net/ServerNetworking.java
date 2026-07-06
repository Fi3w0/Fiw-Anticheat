package dev.fiw.modsapi.net;

import dev.fiw.modsapi.FiwModsApi;
import dev.fiw.modsapi.core.model.ModEntry;
import dev.fiw.modsapi.core.model.ResourcePackEntry;
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
                    List<ResourcePackEntry> resourcePacks = ModListCodec.readResourcePacks(buf);
                    byte[] nonceEcho = buf.readByteArray(64);
                    server.execute(() -> FiwModsApi.handleResponse(server, player, mods, resourcePacks, nonceEcho));
                });

        ServerPlayNetworking.registerGlobalReceiver(Channels.RESOURCE_PACK_UPDATE,
                (server, player, handler, buf, sender) -> {
                    List<ResourcePackEntry> resourcePacks = ModListCodec.readResourcePacks(buf);
                    server.execute(() -> FiwModsApi.handleResourcePackUpdate(server, player, resourcePacks));
                });
    }

    public static void sendChallenge(ServerPlayerEntity player, byte[] nonce) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeByteArray(nonce);
        ServerPlayNetworking.send(player, Channels.CHALLENGE, buf);
    }
}
