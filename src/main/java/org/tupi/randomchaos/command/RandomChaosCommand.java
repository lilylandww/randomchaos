package org.tupi.randomchaos.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;

import org.tupi.randomchaos.config.ChaosConfig;

public final class RandomChaosCommand {
	private RandomChaosCommand() {}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, ctx, env) -> {
			dispatcher.register(Commands.literal("randomchaos")
				.requires(s -> s.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS)))
				.then(Commands.literal("reload").executes(RandomChaosCommand::reload))
				.then(Commands.literal("show").executes(RandomChaosCommand::show))
				.then(Commands.literal("interval")
					.then(Commands.argument("seconds", IntegerArgumentType.integer(1, 86_400)).executes(RandomChaosCommand::setInterval)))
				.then(Commands.literal("cap")
					.then(Commands.argument("ratio", DoubleArgumentType.doubleArg(0.001, 1.0)).executes(RandomChaosCommand::setCap)))
				.then(Commands.literal("cooldown")
					.then(Commands.argument("picks", IntegerArgumentType.integer(1)).executes(RandomChaosCommand::setCooldown)))
				.then(Commands.literal("weight")
					.then(Commands.literal("minor").then(Commands.argument("value", IntegerArgumentType.integer(0)).executes(RandomChaosCommand::setMinorWeight)))
					.then(Commands.literal("medium").then(Commands.argument("value", IntegerArgumentType.integer(0)).executes(RandomChaosCommand::setMediumWeight)))
					.then(Commands.literal("major").then(Commands.argument("value", IntegerArgumentType.integer(0)).executes(RandomChaosCommand::setMajorWeight)))));
		});
	}

	private static int reload(CommandContext<CommandSourceStack> ctx) {
		ChaosConfig.reload();
		ctx.getSource().sendSuccess(() -> Component.literal("Random Chaos config reloaded from disk."), false);
		return 1;
	}

	private static int show(CommandContext<CommandSourceStack> ctx) {
		ChaosConfig c = ChaosConfig.get();
		String msg = String.format(
			"Random Chaos — interval=%ds, cap=%.2f, majorCooldown=%d, weights(minor/medium/major)=%d/%d/%d",
			c.intervalSeconds, c.effectCapRatio, c.majorCooldownPicks, c.minorWeight, c.mediumWeight, c.majorWeight);
		ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
		return 1;
	}

	private static int setInterval(CommandContext<CommandSourceStack> ctx) {
		int requested = IntegerArgumentType.getInteger(ctx, "seconds");
		ChaosConfig.get().intervalSeconds = requested;
		ChaosConfig.save();
		int actual = ChaosConfig.get().intervalSeconds;
		ctx.getSource().sendSuccess(() -> Component.literal("intervalSeconds set to " + actual + "s (saved)."), false);
		return actual;
	}

	private static int setCap(CommandContext<CommandSourceStack> ctx) {
		double requested = DoubleArgumentType.getDouble(ctx, "ratio");
		ChaosConfig.get().effectCapRatio = requested;
		ChaosConfig.save();
		double actual = ChaosConfig.get().effectCapRatio;
		ctx.getSource().sendSuccess(() -> Component.literal("effectCapRatio set to " + String.format("%.3f", actual) + " (saved)."), false);
		return (int) Math.round(actual * 100);
	}

	private static int setCooldown(CommandContext<CommandSourceStack> ctx) {
		int requested = IntegerArgumentType.getInteger(ctx, "picks");
		ChaosConfig.get().majorCooldownPicks = requested;
		ChaosConfig.save();
		int actual = ChaosConfig.get().majorCooldownPicks;
		ctx.getSource().sendSuccess(() -> Component.literal("majorCooldownPicks set to " + actual + " (saved)."), false);
		return actual;
	}

	private static int setMinorWeight(CommandContext<CommandSourceStack> ctx) {
		int v = IntegerArgumentType.getInteger(ctx, "value");
		ChaosConfig.get().minorWeight = v;
		return finishWeight(ctx, "minor", v);
	}

	private static int setMediumWeight(CommandContext<CommandSourceStack> ctx) {
		int v = IntegerArgumentType.getInteger(ctx, "value");
		ChaosConfig.get().mediumWeight = v;
		return finishWeight(ctx, "medium", v);
	}

	private static int setMajorWeight(CommandContext<CommandSourceStack> ctx) {
		int v = IntegerArgumentType.getInteger(ctx, "value");
		ChaosConfig.get().majorWeight = v;
		return finishWeight(ctx, "major", v);
	}

	private static int finishWeight(CommandContext<CommandSourceStack> ctx, String tier, int requested) {
		ChaosConfig.save();
		ChaosConfig after = ChaosConfig.get();
		ctx.getSource().sendSuccess(() -> Component.literal(
			tier + "Weight set to " + requested + " (saved). Current weights (minor/medium/major): "
			+ after.minorWeight + "/" + after.mediumWeight + "/" + after.majorWeight), false);
		return requested;
	}
}
