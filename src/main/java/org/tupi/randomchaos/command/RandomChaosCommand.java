package org.tupi.randomchaos.command;

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
				.then(Commands.literal("reload")
					.executes(RandomChaosCommand::reload)));
		});
	}

	private static int reload(CommandContext<CommandSourceStack> ctx) {
		ChaosConfig.reload();
		ctx.getSource().sendSuccess(() -> Component.literal("Random Chaos config reloaded."), false);
		return 1;
	}
}
