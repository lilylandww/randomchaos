package org.tupi.randomchaos.lifecycle;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;

import org.tupi.randomchaos.RandomChaosMod;
import org.tupi.randomchaos.config.ChaosConfig;
import org.tupi.randomchaos.scheduler.ChaosScheduler;
import org.tupi.randomchaos.state.ChaosState;

public final class ChaosLifecycle {
	private ChaosLifecycle() {}

	public static void register() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			onPlayerJoin(server);
			ServerPlayer player = handler.player;
			if (player != null) {
				ChaosScheduler.sendTo(player, ChaosState.get(server), server.getTickCount());
			}
		});

		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof EnderDragon) {
				endChallenge(entity.level().getServer());
			}
		});
	}

	private static void onPlayerJoin(MinecraftServer server) {
		ChaosState state = ChaosState.get(server);
		if (state.challengeStartTick != 0 || state.challengeEndTick != 0) return;

		long now = server.getTickCount();
		state.challengeStartTick = now;
		state.nextEventTick = now + ChaosConfig.get().intervalTicks();
		state.setDirty();

		long secondsUntilFirst = ChaosConfig.get().intervalSeconds;
		RandomChaosMod.LOGGER.info("Challenge started at tick {} (first event in ~{}s)", now, secondsUntilFirst);
		ChaosScheduler.broadcast(server, state, now);
	}

	private static void endChallenge(MinecraftServer server) {
		if (server == null) return;
		ChaosState state = ChaosState.get(server);
		if (state.challengeEndTick != 0) return;

		long now = server.getTickCount();
		state.challengeEndTick = now;
		state.currentEventId = "";
		state.currentVictimUuid = null;
		state.currentEffectExpiryTick = 0;
		state.setDirty();

		long seconds = state.challengeStartTick == 0 ? 0 : (state.challengeEndTick - state.challengeStartTick) / 20;
		RandomChaosMod.LOGGER.info("Challenge finished! Dragon defeated after {}s", seconds);
		ChaosScheduler.broadcast(server, state, now);
	}
}
