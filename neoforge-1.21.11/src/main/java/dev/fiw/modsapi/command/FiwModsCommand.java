package dev.fiw.modsapi.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.fiw.modsapi.FiwModsApi;
import dev.fiw.modsapi.ModEnumerator;
import dev.fiw.modsapi.core.exemption.ExemptionTier;
import dev.fiw.modsapi.core.exemption.ExemptionView;
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
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** {@code /fiwmods reload | snapshot server | snapshot player <name> | profile <name> | exempt add|remove|list} (OP 4). */
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
                                .executes(FiwModsCommand::profile)))
                .then(Commands.literal("exempt")
                        .then(Commands.literal("add")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .then(Commands.argument("tier", StringArgumentType.word())
                                                .executes(ctx -> exemptAdd(ctx, null, null))
                                                .then(Commands.argument("hours", IntegerArgumentType.integer(0))
                                                        .executes(ctx -> exemptAdd(ctx,
                                                                IntegerArgumentType.getInteger(ctx, "hours"), null))
                                                        .then(Commands.argument("preset", StringArgumentType.word())
                                                                .executes(ctx -> exemptAdd(ctx,
                                                                        IntegerArgumentType.getInteger(ctx, "hours"),
                                                                        StringArgumentType.getString(ctx, "preset"))))))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .executes(FiwModsCommand::exemptRemove)))
                        .then(Commands.literal("list").executes(FiwModsCommand::exemptList))));
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

    private static int exemptAdd(CommandContext<CommandSourceStack> ctx, Integer hours, String preset) {
        String player = StringArgumentType.getString(ctx, "player");
        String tierArg = StringArgumentType.getString(ctx, "tier");
        ExemptionTier tier = ExemptionTier.fromString(tierArg);
        if (tier == null) {
            ctx.getSource().sendFailure(Component.literal("[FiwAntiCheat] Unknown tier '" + tierArg
                    + "' — expected bypass, silent, monitor, quiet_kick, preset, or force_block."));
            return 0;
        }
        String err = FiwModsApi.engine().addExemption(player, tier, preset, hours);
        if (err != null) {
            ctx.getSource().sendFailure(Component.literal("[FiwAntiCheat] " + err));
            return 0;
        }
        String durationText = (hours != null && hours > 0) ? " for " + hours + "h" : " (permanent)";
        ctx.getSource().sendSuccess(() -> Component.literal("[FiwAntiCheat] Granted '"
                + tier.name().toLowerCase(Locale.ROOT) + "' exemption to " + player + durationText), true);
        return 1;
    }

    private static int exemptRemove(CommandContext<CommandSourceStack> ctx) {
        String player = StringArgumentType.getString(ctx, "player");
        if (!FiwModsApi.engine().removeExemption(player)) {
            ctx.getSource().sendFailure(Component.literal("[FiwAntiCheat] No exemption found for '" + player + "'."));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("[FiwAntiCheat] Removed exemption for " + player + "."), true);
        return 1;
    }

    private static int exemptList(CommandContext<CommandSourceStack> ctx) {
        for (ExemptionView.CommandLine line : FiwModsApi.engine().listExemptions()) {
            ctx.getSource().sendSuccess(() -> Component.literal(line.text()), false);
        }
        return 1;
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
