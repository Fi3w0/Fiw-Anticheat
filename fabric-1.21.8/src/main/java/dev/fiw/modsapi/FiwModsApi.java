package dev.fiw.modsapi;

import dev.fiw.modsapi.command.FiwModsCommand;
import dev.fiw.modsapi.compat.FloodgateDetector;
import dev.fiw.modsapi.core.FiwModsEngine;
import dev.fiw.modsapi.core.challenge.ChallengeManager;
import dev.fiw.modsapi.core.exemption.ExemptionResolution;
import dev.fiw.modsapi.core.exemption.ExemptionTier;
import dev.fiw.modsapi.core.freeze.FreezeState;
import dev.fiw.modsapi.core.model.ModEntry;
import dev.fiw.modsapi.core.model.ResourcePackEntry;
import dev.fiw.modsapi.core.verify.EvaluationResult;
import dev.fiw.modsapi.net.ServerNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
            if (!server.isDedicatedServer()) return;
            ServerPlayer player = handler.player;
            ExemptionResolution resolution = resolveExemption(player);
            if (resolution.tier() == ExemptionTier.BYPASS) {
                LOGGER.info("[FiwAntiCheat] {} is exempt (Bedrock/bypass) — skipping verification",
                        player.getName().getString());
                return;
            }
            if (resolution.tier() == ExemptionTier.FORCE_BLOCK) {
                LOGGER.warn("[FiwAntiCheat] Force-blocking {} on join (admin override)",
                        player.getName().getString());
                player.connection.disconnect(Component.literal(engine.config().kick_message));
                return;
            }
            freezeAndChallenge(player, false);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (!server.isDedicatedServer()) return;
            UUID uuid = handler.player.getUUID();
            engine.forget(uuid);
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!server.isDedicatedServer()) return;
            if (server.getTickCount() % 10 == 0) {
                // Snap frozen players back on the main thread; the movement mixin only drops packets.
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    FreezeState state = engine.freezes().get(player.getUUID());
                    if (state != null) player.teleportTo(state.x(), state.y(), state.z());
                }
            }
            List<UUID> expired = engine.challenges().expired(engine.timeoutMillis());
            for (UUID uuid : expired) {
                ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                if (player != null) {
                    player.connection.disconnect(Component.literal(engine.config().timeout_message));
                }
                engine.challenges().remove(uuid);
                engine.freezes().unfreeze(uuid);
            }
        });

        FiwModsCommand.register();
        LOGGER.info("[FiwAntiCheat] Ready (Fabric).");
    }

    // --- shared orchestration ---

    public static void freezeAndChallenge(ServerPlayer player, boolean capture) {
        UUID uuid = player.getUUID();
        FreezeState state = new FreezeState(
                player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot(), System.currentTimeMillis());
        engine.freezes().freeze(uuid, state);
        byte[] nonce = engine.challenges().create(uuid, capture);
        ServerNetworking.sendChallenge(player, nonce);
    }

    /** Send a snapshot-capture challenge to an already-online player (no freeze). False while a verification is pending. */
    public static boolean sendCaptureChallenge(ServerPlayer player) {
        byte[] nonce = engine.challenges().createIfIdle(player.getUUID(), true);
        if (nonce == null) return false;
        ServerNetworking.sendChallenge(player, nonce);
        return true;
    }

    /**
     * Re-run the join check on an online player without freezing them; a bad or missing
     * reply kicks exactly as on join. Returns why the player was skipped, or null if a
     * challenge was sent.
     */
    public static String recheck(ServerPlayer player) {
        ExemptionResolution resolution = resolveExemption(player);
        if (resolution.tier() == ExemptionTier.BYPASS) return "exempt";
        if (resolution.tier() == ExemptionTier.FORCE_BLOCK) {
            player.connection.disconnect(Component.literal(engine.config().kick_message));
            return "force-blocked, disconnected";
        }
        byte[] nonce = engine.challenges().createIfIdle(player.getUUID(), false);
        if (nonce == null) return "already being verified";
        ServerNetworking.sendChallenge(player, nonce);
        return null;
    }

    public static void handleResponse(MinecraftServer server, ServerPlayer player,
                                      List<ModEntry> mods, List<ResourcePackEntry> resourcePacks, byte[] nonceEcho) {
        UUID uuid = player.getUUID();
        String name = player.getName().getString();

        // A packet still queued from a connection that has since been replaced must not
        // consume the new connection's challenge.
        ServerPlayer current = server.getPlayerList().getPlayer(uuid);
        if (current == null || current.connection != player.connection) return;

        ChallengeManager.Verification check =
                engine.challenges().verify(uuid, nonceEcho, engine.timeoutMillis());
        if (check.outcome() == ChallengeManager.Outcome.NONE) {
            return; // no live challenge: unsolicited, duplicate, or late snapshot reply
        }
        if (check.outcome() != ChallengeManager.Outcome.VALID) {
            // Fail closed: the challenge is spent, so the timeout can no longer catch this player.
            boolean late = check.outcome() == ChallengeManager.Outcome.EXPIRED;
            LOGGER.warn("[FiwAntiCheat] {} from {} — disconnecting",
                    late ? "Late verification response" : "Nonce mismatch", name);
            player.connection.disconnect(Component.literal(late
                    ? engine.config().timeout_message : engine.config().kick_message));
            return;
        }
        ChallengeManager.Pending pending = check.pending();

        engine.recordProfile(uuid, name, mods, resourcePacks, pending.join());

        if (pending.capture()) {
            engine.captureSnapshot(mods, "player " + name);
            engine.freezes().unfreeze(uuid);
            alertStaff(server, "[FiwAntiCheat] Snapshot captured from " + name + " ("
                    + mods.size() + " mods). Whitelist updated.");
            return;
        }

        ExemptionResolution resolution = resolveExemption(player);
        EvaluationResult result = engine.evaluate(mods, resourcePacks, resolution);
        engine.checkEscalation(uuid, name, result);

        if (result.hasDetections()) {
            for (EvaluationResult.Detected d : result.detected()) {
                LOGGER.warn("[FiwAntiCheat] {} flagged: {}", name, d.describe());
            }
            if (engine.config().detection.alert_staff && !result.suppressAlert()) {
                alertStaff(server, result.alertText(name));
            }
        }

        if (result.kick()) {
            LOGGER.warn("[FiwAntiCheat] Kicking {}: {}", name, result.logSummary());
            player.connection.disconnect(Component.literal(result.kickMessage()));
        } else {
            engine.freezes().unfreeze(uuid);
            LOGGER.info("[FiwAntiCheat] {} passed verification: {}", name, result.logSummary());
        }
    }

    public static void handleResourcePackUpdate(MinecraftServer server,
                                                ServerPlayer player,
                                                List<ResourcePackEntry> resourcePacks) {
        if (!engine.allowResourcePackUpdate(player.getUUID())) return;
        ExemptionResolution resolution = resolveExemption(player);
        if (resolution.tier() == ExemptionTier.BYPASS) return;
        UUID uuid = player.getUUID();
        String name = player.getName().getString();
        if (resolution.tier() == ExemptionTier.FORCE_BLOCK) {
            LOGGER.warn("[FiwAntiCheat] Force-blocking {} (admin override)", name);
            player.connection.disconnect(Component.literal(engine.config().kick_message));
            return;
        }
        engine.recordResourcePacks(uuid, name, resourcePacks);

        EvaluationResult result = engine.evaluateResourcePacks(resourcePacks, resolution);
        engine.checkEscalation(uuid, name, result);
        if (result.hasDetections()) {
            for (EvaluationResult.Detected d : result.detected()) {
                LOGGER.warn("[FiwAntiCheat] {} flagged: {}", name, d.describe());
            }
            if (engine.config().detection.alert_staff && !result.suppressAlert()) {
                alertStaff(server, result.alertText(name));
            }
        }
        if (result.kick()) {
            LOGGER.warn("[FiwAntiCheat] Kicking {}: {}", name, result.logSummary());
            player.connection.disconnect(Component.literal(result.kickMessage()));
        }
    }

    private static ExemptionResolution resolveExemption(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (engine.config().exemptions.floodgate_auto && FloodgateDetector.isBedrockPlayer(uuid)) {
            return ExemptionResolution.of(ExemptionTier.BYPASS);
        }
        return engine.resolveExemption(player.getName().getString(), uuid);
    }

    private static void alertStaff(MinecraftServer server, String message) {
        Component text = Component.literal(message);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (server.getPlayerList().isOp(p.getGameProfile())) {
                p.sendSystemMessage(text);
            }
        }
    }
}
