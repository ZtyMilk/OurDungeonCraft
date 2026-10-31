package com.github.ztymilk.command;

import com.github.ztymilk.network.ModNetworking;
import com.github.ztymilk.soul.SoulDataAccessor;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.Collections;

/**
 * Command handler for {@code /set soul} and alias {@code /soul}.
 * Supports:
 * <ul>
 *   <li>{@code /set soul add <amount> [targets]}</li>
 *   <li>{@code /set soul remove <amount> [targets]}</li>
 *   <li>{@code /set soul set <amount> [targets]}</li>
 *   <li>{@code /set soul get [target]}</li>
 *   <li>{@code /set soul query [target]}</li>
 * </ul>
 */
public final class SetSoulCommand {

    private SetSoulCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> soulTree = buildSoulCommandTree();

        // Register /set soul ...
        dispatcher.register(
                Commands.literal("set")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(soulTree)
        );

        // Also register /soul ... as a direct convenient alias
        dispatcher.register(
                Commands.literal("soul")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(buildSubCommandNodes(soulTree))
        );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildSoulCommandTree() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("soul")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
        return buildSubCommandNodes(root);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildSubCommandNodes(LiteralArgumentBuilder<CommandSourceStack> builder) {
        return builder
                // add <amount> [targets]
                .then(Commands.literal("add")
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                .executes(ctx -> executeAdd(ctx, IntegerArgumentType.getInteger(ctx, "amount"), Collections.singleton(ctx.getSource().getPlayerOrException())))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> executeAdd(ctx, IntegerArgumentType.getInteger(ctx, "amount"), EntityArgument.getPlayers(ctx, "targets")))
                                )
                        )
                )
                // remove <amount> [targets]
                .then(Commands.literal("remove")
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                .executes(ctx -> executeRemove(ctx, IntegerArgumentType.getInteger(ctx, "amount"), Collections.singleton(ctx.getSource().getPlayerOrException())))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> executeRemove(ctx, IntegerArgumentType.getInteger(ctx, "amount"), EntityArgument.getPlayers(ctx, "targets")))
                                )
                        )
                )
                // set <amount> [targets]
                .then(Commands.literal("set")
                        .then(Commands.argument("amount", IntegerArgumentType.integer(0, 40))
                                .executes(ctx -> executeSet(ctx, IntegerArgumentType.getInteger(ctx, "amount"), Collections.singleton(ctx.getSource().getPlayerOrException())))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> executeSet(ctx, IntegerArgumentType.getInteger(ctx, "amount"), EntityArgument.getPlayers(ctx, "targets")))
                                )
                        )
                )
                // get [target]
                .then(Commands.literal("get")
                        .executes(ctx -> executeGet(ctx, ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(ctx -> executeGet(ctx, EntityArgument.getPlayer(ctx, "target")))
                        )
                )
                // query [target]
                .then(Commands.literal("query")
                        .executes(ctx -> executeGet(ctx, ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(ctx -> executeGet(ctx, EntityArgument.getPlayer(ctx, "target")))
                        )
                );
    }

    private static int executeAdd(CommandContext<CommandSourceStack> ctx, int amount, Collection<ServerPlayer> targets) {
        int count = 0;
        for (ServerPlayer player : targets) {
            SoulDataAccessor accessor = SoulDataAccessor.get(player);
            if (accessor != null) {
                int added = accessor.addSouls(amount);
                ModNetworking.syncSouls(player);
                ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                        "Added %d soul(s) to %s (now %d/%d)",
                        added,
                        player.getScoreboardName(),
                        accessor.getSouls(),
                        accessor.getMaxSouls()
                )), true);
                count++;
            }
        }
        return count;
    }

    private static int executeRemove(CommandContext<CommandSourceStack> ctx, int amount, Collection<ServerPlayer> targets) {
        int count = 0;
        for (ServerPlayer player : targets) {
            SoulDataAccessor accessor = SoulDataAccessor.get(player);
            if (accessor != null) {
                accessor.getSoulData().forceRemoveSouls(amount);
                ModNetworking.syncSouls(player);
                ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                        "Removed %d soul(s) from %s (now %d/%d)",
                        amount,
                        player.getScoreboardName(),
                        accessor.getSouls(),
                        accessor.getMaxSouls()
                )), true);
                count++;
            }
        }
        return count;
    }

    private static int executeSet(CommandContext<CommandSourceStack> ctx, int amount, Collection<ServerPlayer> targets) {
        int count = 0;
        for (ServerPlayer player : targets) {
            SoulDataAccessor accessor = SoulDataAccessor.get(player);
            if (accessor != null) {
                accessor.setSouls(amount);
                ModNetworking.syncSouls(player);
                ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                        "Set souls of %s to %d/%d",
                        player.getScoreboardName(),
                        accessor.getSouls(),
                        accessor.getMaxSouls()
                )), true);
                count++;
            }
        }
        return count;
    }

    private static int executeGet(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        SoulDataAccessor accessor = SoulDataAccessor.get(target);
        int current = accessor != null ? accessor.getSouls() : 0;
        int max = accessor != null ? accessor.getMaxSouls() : 40;
        ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                "%s currently has %d/%d souls",
                target.getScoreboardName(),
                current,
                max
        )), false);
        return current;
    }
}
