package dev.fiw.modsapi;

import dev.fiw.modsapi.command.FiwModsCommand;
import dev.fiw.modsapi.compat.FloodgateDetector;
import dev.fiw.modsapi.core.FiwModsEngine;
import dev.fiw.modsapi.core.challenge.ChallengeManager;
import dev.fiw.modsapi.core.freeze.FreezeState;
import dev.fiw.modsapi.core.model.ModEntry;
import dev.fiw.modsapi.core.verify.EvaluationResult;
import dev.fiw.modsapi.net.ServerNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerConfigEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;

public final class FiwModsApi implements ModInitializer {

    public static final String MOD_ID = "fiw-mods-api";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static FiwModsEngine engine;

    public static FiwModsEngine engine() {
        return engine;
    }

    @Override
    public void onInitialize() {
        engine = new FiwModsEngine(new FabricPlatform(LOGGER));
        ServerNetworking.register();

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (!server.isDedicated()) return;
            ServerPlayerEntity player = handler.player;
            if (isExempt(player)) {
                LOGGER.info("[FiwAntiCheat] {} is exempt (Bedrock/bypass) — skipping verification",
                        player.getName().getString());
                return;
            }
            freezeAndChallenge(player, false);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (!server.isDedicated()) return;
            UUID uuid = handler.player.getUuid();
            engine.challenges().remove(uuid);
            engine.freezes().unfreeze(uuid);
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!server.isDedicated()) return;
            List<UUID> expired = engine.challenges().expired(engine.timeoutMillis());
            for (UUID uuid : expired) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
                if (player != null) {
                    player.networkHandler.disconnect(Text.literal(engine.config().timeout_message));
                }
                engine.challenges().remove(uuid);
                engine.freezes().unfreeze(uuid);
            }
        });

        FiwModsCommand.register();
        LOGGER.info("[FiwAntiCheat] Ready (Fabric).");
    }

    /** Freeze a player at their current position and send a verification challenge. */
    public static void freezeAndChallenge(ServerPlayerEntity player, boolean capture) {
        UUID uuid = player.getUuid();
        FreezeState state = new FreezeState(
                player.getX(), player.getY(), player.getZ(),
                player.getYaw(), player.getPitch(), System.currentTimeMillis());
        engine.freezes().freeze(uuid, state);
        byte[] nonce = engine.challenges().create(uuid, capture);
        ServerNetworking.sendChallenge(player, nonce);
    }

    /** Send a snapshot-capture challenge to an already-online player (no freeze). */
    public static void sendCaptureChallenge(ServerPlayerEntity player) {
        byte[] nonce = engine.challenges().create(player.getUuid(), true);
        ServerNetworking.sendChallenge(player, nonce);
    }

    /** Handle a client's verification response (already on the server thread). */
    public static void handleResponse(MinecraftServer server, ServerPlayerEntity player,
                                      List<ModEntry> mods, byte[] nonceEcho) {
        UUID uuid = player.getUuid();
        String name = player.getName().getString();

        ChallengeManager.Pending pending = engine.challenges().consume(uuid);
        if (pending == null) {
            engine.freezes().unfreeze(uuid);
            return; // no active challenge (already handled or timed out)
        }
        if (!MessageDigest.isEqual(pending.nonce(), nonceEcho)) {
            LOGGER.warn("[FiwAntiCheat] Nonce mismatch from {} — ignoring response", name);
            engine.freezes().unfreeze(uuid);
            return;
        }

        engine.recordProfile(uuid, name, mods);

        // snapshot capture mode
        if (pending.capture()) {
            engine.captureSnapshot(mods, "player " + name);
            engine.freezes().unfreeze(uuid);
            alertStaff(server, "[FiwAntiCheat] Snapshot captured from " + name + " ("
                    + mods.size() + " mods). Whitelist updated.");
            return;
        }

        boolean exempt = isExempt(player);
        EvaluationResult result = engine.evaluate(mods, exempt);

        if (result.hasDetections()) {
            for (EvaluationResult.Detected d : result.detected()) {
                LOGGER.warn("[FiwAntiCheat] {} flagged: {} [{}] (id={})",
                        name, d.modName(), d.category(), d.modId());
            }
            if (engine.config().detection.alert_staff) {
                StringBuilder sb = new StringBuilder("[FiwAntiCheat] ").append(name).append(" using: ");
                for (int i = 0; i < result.detected().size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(result.detected().get(i).modName());
                }
                alertStaff(server, sb.toString());
            }
        }

        if (result.kick()) {
            LOGGER.warn("[FiwAntiCheat] Kicking {}: {}", name, result.logSummary());
            player.networkHandler.disconnect(Text.literal(result.kickMessage()));
        } else {
            engine.freezes().unfreeze(uuid);
            LOGGER.info("[FiwAntiCheat] {} passed verification: {}", name, result.logSummary());
        }
    }

    private static boolean isExempt(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        return (engine.config().exemptions.floodgate_auto && FloodgateDetector.isBedrockPlayer(uuid))
                || engine.isBypassed(player.getName().getString(), uuid);
    }

    /** Send an alert to all online operators. */
    private static void alertStaff(MinecraftServer server, String message) {
        Text text = Text.literal(message);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (server.getPlayerManager().isOperator(new PlayerConfigEntry(p.getGameProfile()))) {
                p.sendMessage(text, false);
            }
        }
    }

}
