package dev.fiw.modsapi.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.fiw.modsapi.FiwModsApi;
import dev.fiw.modsapi.ModEnumerator;
import dev.fiw.modsapi.core.exemption.ExemptionTier;
import dev.fiw.modsapi.core.exemption.ExemptionView;
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
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** {@code /fiwmods reload | snapshot server | snapshot player <name> | profile <name> | exempt add|remove|list} (OP 4). */
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
                                        .executes(FiwModsCommand::profile)))
                        .then(CommandManager.literal("exempt")
                                .then(CommandManager.literal("add")
                                        .then(CommandManager.argument("player", StringArgumentType.word())
                                                .then(CommandManager.argument("tier", StringArgumentType.word())
                                                        .executes(ctx -> exemptAdd(ctx, null, null))
                                                        .then(CommandManager.argument("hours", IntegerArgumentType.integer(0))
                                                                .executes(ctx -> exemptAdd(ctx,
                                                                        IntegerArgumentType.getInteger(ctx, "hours"), null))
                                                                .then(CommandManager.argument("preset", StringArgumentType.word())
                                                                        .executes(ctx -> exemptAdd(ctx,
                                                                                IntegerArgumentType.getInteger(ctx, "hours"),
                                                                                StringArgumentType.getString(ctx, "preset"))))))))
                                .then(CommandManager.literal("remove")
                                        .then(CommandManager.argument("player", StringArgumentType.word())
                                                .executes(FiwModsCommand::exemptRemove)))
                                .then(CommandManager.literal("list").executes(FiwModsCommand::exemptList)))));
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

    private static int exemptAdd(CommandContext<ServerCommandSource> ctx, Integer hours, String preset) {
        String player = StringArgumentType.getString(ctx, "player");
        String tierArg = StringArgumentType.getString(ctx, "tier");
        ExemptionTier tier = ExemptionTier.fromString(tierArg);
        if (tier == null) {
            ctx.getSource().sendError(Text.literal("[FiwAntiCheat] Unknown tier '" + tierArg
                    + "' — expected bypass, silent, monitor, quiet_kick, preset, or force_block."));
            return 0;
        }
        String err = FiwModsApi.engine().addExemption(player, tier, preset, hours);
        if (err != null) {
            ctx.getSource().sendError(Text.literal("[FiwAntiCheat] " + err));
            return 0;
        }
        String durationText = (hours != null && hours > 0) ? " for " + hours + "h" : " (permanent)";
        ctx.getSource().sendFeedback(() -> Text.literal("[FiwAntiCheat] Granted '"
                + tier.name().toLowerCase(Locale.ROOT) + "' exemption to " + player + durationText), true);
        return 1;
    }

    private static int exemptRemove(CommandContext<ServerCommandSource> ctx) {
        String player = StringArgumentType.getString(ctx, "player");
        if (!FiwModsApi.engine().removeExemption(player)) {
            ctx.getSource().sendError(Text.literal("[FiwAntiCheat] No exemption found for '" + player + "'."));
            return 0;
        }
        ctx.getSource().sendFeedback(() -> Text.literal("[FiwAntiCheat] Removed exemption for " + player + "."), true);
        return 1;
    }

    private static int exemptList(CommandContext<ServerCommandSource> ctx) {
        for (ExemptionView.CommandLine line : FiwModsApi.engine().listExemptions()) {
            ctx.getSource().sendFeedback(() -> Text.literal(line.text()), false);
        }
        return 1;
    }

    private static Text profileLine(ProfileView.CommandLine line) {
        Formatting color = switch (line.type()) {
            case HEADER -> Formatting.AQUA;
            case MODS -> Formatting.GREEN;
            case PLATFORM -> Formatting.DARK_GRAY;
            case RESOURCE_PACKS_ACTIVE -> Formatting.GOLD;
            case RESOURCE_PACKS_INACTIVE -> Formatting.GRAY;
            case CHANGES -> Formatting.GOLD;
            case EVENT -> Formatting.YELLOW;
            case EMPTY -> Formatting.GRAY;
        };
        Text text = Text.literal(line.text()).formatted(color);
        return line.type() == ProfileView.LineType.HEADER ? text.copy().formatted(color, Formatting.BOLD) : text;
    }
}
