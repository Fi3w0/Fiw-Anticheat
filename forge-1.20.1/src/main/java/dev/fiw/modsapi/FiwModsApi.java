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
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
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

    public FiwModsApi() {
        engine = new FiwModsEngine(new ForgePlatform(LOGGER));
        NetworkHandler.register();
        MinecraftForge.EVENT_BUS.register(this);
        LOGGER.info("[FiwAntiCheat] Ready (Forge 1.20.1).");
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MinecraftServer server = player.getServer();
        if (server == null || server.isSingleplayer()) return;
        if (isExempt(player)) {
            LOGGER.info("[FiwAntiCheat] {} is exempt (Bedrock/bypass) - skipping verification",
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
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
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
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        FreezeState state = engine.freezes().get(player.getUUID());
        if (state != null) {
            player.teleportTo(state.x(), state.y(), state.z());
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        FiwModsCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        cancelIfFrozen(event);
    }

    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        cancelIfFrozen(event);
    }

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        cancelIfFrozen(event);
    }

    @SubscribeEvent
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        cancelIfFrozen(event);
    }

    @SubscribeEvent
    public void onAttackEntity(AttackEntityEvent event) {
        cancelIfFrozen(event);
    }

    @SubscribeEvent
    public void onChat(ServerChatEvent event) {
        if (engine.freezes().isFrozen(event.getPlayer().getUUID())) {
            event.setCanceled(true);
        }
    }

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
            LOGGER.warn("[FiwAntiCheat] Nonce mismatch from {} - ignoring response", name);
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
            if (server.getPlayerList().isOp(p.getGameProfile())) {
                p.sendSystemMessage(text);
            }
        }
    }

    private static void cancelIfFrozen(PlayerEvent event) {
        if (engine.freezes().isFrozen(event.getEntity().getUUID())) {
            event.setCanceled(true);
            if (event instanceof PlayerInteractEvent interact) {
                interact.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
            }
        }
    }

    private static void cancelIfFrozen(Event event) {
        if (event instanceof AttackEntityEvent attack
                && engine.freezes().isFrozen(attack.getEntity().getUUID())) {
            attack.setCanceled(true);
        }
    }
}
