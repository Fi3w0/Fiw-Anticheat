package dev.fiw.modsapi;

import dev.fiw.modsapi.command.FiwModsCommand;
import dev.fiw.modsapi.compat.FloodgateDetector;
import dev.fiw.modsapi.core.FiwModsEngine;
import dev.fiw.modsapi.core.challenge.ChallengeManager;
import dev.fiw.modsapi.core.freeze.FreezeState;
import dev.fiw.modsapi.core.model.ModEntry;
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

import java.security.MessageDigest;
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
        if (isExempt(player)) {
            LOGGER.info("[FiwAntiCheat] {} is exempt (Bedrock/bypass) — skipping verification",
                    player.getName().getString());
            return;
        }
        freezeAndChallenge(player, false);
    }

    @SubscribeEvent
    public void onPlayerDisconnect(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID uuid = player.getUUID();
            engine.challenges().remove(uuid);
            engine.freezes().unfreeze(uuid);
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

    public static void sendCaptureChallenge(ServerPlayer player) {
        byte[] nonce = engine.challenges().create(player.getUUID(), true);
        NetworkHandler.sendChallenge(player, nonce);
    }

    public static void handleResponse(MinecraftServer server, ServerPlayer player,
                                      List<ModEntry> mods, byte[] nonceEcho) {
        UUID uuid = player.getUUID();
        String name = player.getName().getString();

        ChallengeManager.Pending pending = engine.challenges().consume(uuid);
        if (pending == null) {
            engine.freezes().unfreeze(uuid);
            return;
        }
        if (!MessageDigest.isEqual(pending.nonce(), nonceEcho)) {
            LOGGER.warn("[FiwAntiCheat] Nonce mismatch from {} — ignoring response", name);
            engine.freezes().unfreeze(uuid);
            return;
        }

        engine.recordProfile(uuid, name, mods);

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
            player.connection.disconnect(Component.literal(result.kickMessage()));
        } else {
            engine.freezes().unfreeze(uuid);
            LOGGER.info("[FiwAntiCheat] {} passed verification: {}", name, result.logSummary());
        }
    }

    private static boolean isExempt(ServerPlayer player) {
        UUID uuid = player.getUUID();
        return (engine.config().exemptions.floodgate_auto && FloodgateDetector.isBedrockPlayer(uuid))
                || engine.isBypassed(player.getName().getString(), uuid);
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
