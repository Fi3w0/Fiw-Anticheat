package dev.fiw.modsapi.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.fiw.modsapi.FiwModsApi;
import dev.fiw.modsapi.ModEnumerator;
import dev.fiw.modsapi.core.model.ModEntry;
import dev.fiw.modsapi.core.profile.PlayerProfile;
import dev.fiw.modsapi.core.profile.ProfileView;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** {@code /fiwmods reload | snapshot server | snapshot player <name> | profile <name>} (OP 4). */
public final class FiwModsCommand {

    private FiwModsCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("fiwmods")
                .requires(FiwModsCommand::canUseCommands)
                .then(Commands.literal("reload").executes(FiwModsCommand::reload))
                .then(Commands.literal("snapshot")
                        .then(Commands.literal("server").executes(FiwModsCommand::snapshotServer))
                        .then(Commands.literal("player")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(FiwModsCommand::snapshotPlayer))))
                .then(Commands.literal("profile")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(FiwModsCommand::profile))));
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        FiwModsApi.engine().reload();
        ctx.getSource().sendSuccess(() -> Component.literal("[FiwAntiCheat] Config + signatures reloaded."), true);
        return 1;
    }

    private static int snapshotServer(CommandContext<CommandSourceStack> ctx) {
        List<ModEntry> mods = ModEnumerator.collect();
        FiwModsApi.engine().captureSnapshot(mods, "server");
        ctx.getSource().sendSuccess(
                () -> Component.literal("[FiwAntiCheat] Captured " + mods.size() + " server mods into the whitelist."), true);
        return 1;
    }

    private static int snapshotPlayer(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        ServerPlayer target = ctx.getSource().getServer().getPlayerList().getPlayerByName(name);
        if (target == null) {
            ctx.getSource().sendFailure(Component.literal("[FiwAntiCheat] Player '" + name + "' is not online."));
            return 0;
        }
        FiwModsApi.sendCaptureChallenge(target);
        ctx.getSource().sendSuccess(
                () -> Component.literal("[FiwAntiCheat] Requested mod snapshot from " + name + "…"), true);
        return 1;
    }

    private static int profile(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        Optional<UUID> uuid = findUuid(ctx.getSource(), name);
        if (uuid.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("[FiwAntiCheat] Unknown player '" + name + "'."));
            return 0;
        }
        PlayerProfile p = FiwModsApi.engine().profiles().load(uuid.get());
        if (p == null) {
            ctx.getSource().sendFailure(Component.literal("[FiwAntiCheat] No profile recorded for " + name + " yet."));
            return 0;
        }
        for (ProfileView.CommandLine line : ProfileView.commandRows(p, name)) {
            ctx.getSource().sendSuccess(() -> profileLine(line), false);
        }
        return 1;
    }

    private static boolean canUseCommands(CommandSourceStack source) {
        if (source.getEntity() == null) return true; // console / server source
        return source.permissions() instanceof LevelBasedPermissionSet leveled
                && leveled.level().isEqualOrHigherThan(PermissionLevel.OWNERS);
    }

    private static Optional<UUID> findUuid(CommandSourceStack source, String name) {
        ServerPlayer online = source.getServer().getPlayerList().getPlayerByName(name);
        if (online != null) return Optional.of(online.getUUID());
        Optional<NameAndId> cached = source.getServer().services().nameToIdCache().get(name);
        if (cached.isPresent()) return cached.map(NameAndId::id);
        Optional<GameProfile> profile = source.getServer().services().profileResolver().fetchByName(name);
        return profile.map(GameProfile::id);
    }

    private static Component profileLine(ProfileView.CommandLine line) {
        ChatFormatting color = switch (line.type()) {
            case HEADER -> ChatFormatting.AQUA;
            case MODS -> ChatFormatting.GREEN;
            case PLATFORM -> ChatFormatting.DARK_GRAY;
            case RESOURCE_PACKS_ACTIVE -> ChatFormatting.GOLD;
            case RESOURCE_PACKS_INACTIVE -> ChatFormatting.GRAY;
            case CHANGES -> ChatFormatting.GOLD;
            case EVENT -> ChatFormatting.YELLOW;
            case EMPTY -> ChatFormatting.GRAY;
        };
        Component text = Component.literal(line.text()).withStyle(color);
        return line.type() == ProfileView.LineType.HEADER ? text.copy().withStyle(color, ChatFormatting.BOLD) : text;
    }
}
