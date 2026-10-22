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
import dev.fiw.modsapi.net.NetworkHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

@Mod(FiwModsApi.MOD_ID)
public final class FiwModsApi {

    public static final String MOD_ID = "fiw_mods_api";
    public static final Logger LOGGER = LoggerFactory.getLogger("fiw-mods-api");

    private static FiwModsEngine engine;

    public static FiwModsEngine engine() {
        return engine;
    }

    public FiwModsApi(IEventBus modBus) {
        engine = new FiwModsEngine(new NeoForgePlatform(LOGGER));
        modBus.addListener((RegisterPayloadHandlersEvent event) -> NetworkHandler.register(event));
        NeoForge.EVENT_BUS.register(this);
        LOGGER.info("[FiwAntiCheat] Ready (NeoForge).");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // config already loaded in constructor; nothing further needed
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MinecraftServer server = player.level().getServer();
        if (server == null || server.isSingleplayer()) return;
        ExemptionResolution resolution = resolveExemption(player);
        if (resolution.tier() == ExemptionTier.BYPASS) {
            LOGGER.info("[FiwAntiCheat] {} is exempt (Bedrock/bypass) — skipping verification",
                    player.getName().getString());
            return;
        }
        if (resolution.tier() == ExemptionTier.FORCE_BLOCK) {
            LOGGER.warn("[FiwAntiCheat] Force-blocking {} on join (admin override)", player.getName().getString());
            player.connection.disconnect(Component.literal(engine.config().kick_message));
            return;
        }
        freezeAndChallenge(player, false);
    }

    @SubscribeEvent
    public void onPlayerDisconnect(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID uuid = player.getUUID();
            engine.forget(uuid);
        }
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        List<UUID> expired = engine.challenges().expired(engine.timeoutMillis());
        for (UUID uuid : expired) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                player.connection.disconnect(Component.literal(engine.config().timeout_message));
            }
            engine.challenges().remove(uuid);
            engine.freezes().unfreeze(uuid);
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        FiwModsCommand.register(event.getDispatcher());
    }

    // --- shared orchestration ---

    public static void freezeAndChallenge(ServerPlayer player, boolean capture) {
        UUID uuid = player.getUUID();
        FreezeState state = new FreezeState(
                player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot(), System.currentTimeMillis());
        engine.freezes().freeze(uuid, state);
        byte[] nonce = engine.challenges().create(uuid, capture);
        NetworkHandler.sendChallenge(player, nonce);
    }

    /** Send a snapshot-capture challenge to an already-online player (no freeze). False while a verification is pending. */
    public static boolean sendCaptureChallenge(ServerPlayer player) {
        byte[] nonce = engine.challenges().createIfIdle(player.getUUID(), true);
        if (nonce == null) return false;
        NetworkHandler.sendChallenge(player, nonce);
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
        NetworkHandler.sendChallenge(player, nonce);
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

        engine.recordProfile(uuid, name, mods, resourcePacks);

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
            if (server.getPlayerList().isOp(new NameAndId(p.getGameProfile()))) {
                p.sendSystemMessage(text);
            }
        }
    }
}
