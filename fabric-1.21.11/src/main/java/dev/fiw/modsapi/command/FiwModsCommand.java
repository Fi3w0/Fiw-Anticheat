package dev.fiw.modsapi.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.fiw.modsapi.FiwModsApi;
import dev.fiw.modsapi.ModEnumerator;
import dev.fiw.modsapi.core.model.ModEntry;
import dev.fiw.modsapi.core.profile.PlayerProfile;
import dev.fiw.modsapi.core.profile.ProfileView;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.permission.LeveledPermissionPredicate;
import net.minecraft.command.permission.PermissionLevel;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** {@code /fiwmods reload | snapshot server | snapshot player <name> | profile <name>} (OP 4). */
public final class FiwModsCommand {

    private FiwModsCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
                dispatcher.register(CommandManager.literal("fiwmods")
                        .requires(FiwModsCommand::canUseCommands)
                        .then(CommandManager.literal("reload").executes(FiwModsCommand::reload))
                        .then(CommandManager.literal("snapshot")
                                .then(CommandManager.literal("server").executes(FiwModsCommand::snapshotServer))
                                .then(CommandManager.literal("player")
                                        .then(CommandManager.argument("name", StringArgumentType.word())
                                                .executes(FiwModsCommand::snapshotPlayer))))
                        .then(CommandManager.literal("profile")
                                .then(CommandManager.argument("name", StringArgumentType.word())
                                        .executes(FiwModsCommand::profile)))));
    }

    private static int reload(CommandContext<ServerCommandSource> ctx) {
        FiwModsApi.engine().reload();
        ctx.getSource().sendFeedback(() -> Text.literal("[FiwAntiCheat] Config + signatures reloaded."), true);
        return 1;
    }

    private static int snapshotServer(CommandContext<ServerCommandSource> ctx) {
        List<ModEntry> mods = ModEnumerator.collect();
        FiwModsApi.engine().captureSnapshot(mods, "server");
        ctx.getSource().sendFeedback(
                () -> Text.literal("[FiwAntiCheat] Captured " + mods.size() + " server mods into the whitelist."), true);
        return 1;
    }

    private static int snapshotPlayer(CommandContext<ServerCommandSource> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        ServerPlayerEntity target = ctx.getSource().getServer().getPlayerManager().getPlayer(name);
        if (target == null) {
            ctx.getSource().sendError(Text.literal("[FiwAntiCheat] Player '" + name + "' is not online."));
            return 0;
        }
        FiwModsApi.sendCaptureChallenge(target);
        ctx.getSource().sendFeedback(
                () -> Text.literal("[FiwAntiCheat] Requested mod snapshot from " + name + "…"), true);
        return 1;
    }

    private static int profile(CommandContext<ServerCommandSource> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        Optional<UUID> uuid = findUuid(ctx.getSource(), name);
        if (uuid.isEmpty()) {
            ctx.getSource().sendError(Text.literal("[FiwAntiCheat] Unknown player '" + name + "'."));
            return 0;
        }
        PlayerProfile p = FiwModsApi.engine().profiles().load(uuid.get());
        if (p == null) {
            ctx.getSource().sendError(Text.literal("[FiwAntiCheat] No profile recorded for " + name + " yet."));
            return 0;
        }
        for (ProfileView.CommandLine line : ProfileView.commandRows(p, name)) {
            ctx.getSource().sendFeedback(() -> profileLine(line), false);
        }
        return 1;
    }

    private static boolean canUseCommands(ServerCommandSource source) {
        if (source.getEntity() == null) return true; // console / server source
        return source.getPermissions() instanceof LeveledPermissionPredicate leveled
                && leveled.getLevel().isAtLeast(PermissionLevel.OWNERS);
    }

    private static Optional<UUID> findUuid(ServerCommandSource source, String name) {
        ServerPlayerEntity online = source.getServer().getPlayerManager().getPlayer(name);
        if (online != null) return Optional.of(online.getUuid());
        Optional<GameProfile> profile = source.getServer().getApiServices()
                .profileResolver()
                .getProfileByName(name);
        return profile.map(GameProfile::id);
    }

    private static Text profileLine(ProfileView.CommandLine line) {
        Formatting color = switch (line.type()) {
            case HEADER -> Formatting.AQUA;
            case MODS -> Formatting.GREEN;
            case PLATFORM -> Formatting.DARK_GRAY;
            case CHANGES -> Formatting.GOLD;
            case EVENT -> Formatting.YELLOW;
            case EMPTY -> Formatting.GRAY;
        };
        Text text = Text.literal(line.text()).formatted(color);
        return line.type() == ProfileView.LineType.HEADER ? text.copy().formatted(color, Formatting.BOLD) : text;
    }
}
