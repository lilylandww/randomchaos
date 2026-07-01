package org.tupi.randomchaos.scheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

import org.tupi.randomchaos.RandomChaosMod;
import org.tupi.randomchaos.config.ChaosConfig;
import org.tupi.randomchaos.event.ChaosEvent;
import org.tupi.randomchaos.event.ChaosEventRegistry;
import org.tupi.randomchaos.net.ChaosNetworking;
import org.tupi.randomchaos.net.ChaosStatePayload;
import org.tupi.randomchaos.state.ChaosState;

public final class ChaosScheduler {
	private static final int PERIODIC_BROADCAST_TICKS = 20;

	private ChaosScheduler() {}

	public static void tick(MinecraftServer server) {
		ChaosState state = ChaosState.get(server);
		if (state.challengeStartTick == 0) return;
		if (state.challengeEndTick != 0) return;

		long now = server.getTickCount();
		boolean changed = false;

		if (state.currentEffectExpiryTick != 0 && now >= state.currentEffectExpiryTick) {
			state.currentEventId = "";
			state.currentVictimUuid = null;
			state.currentEffectExpiryTick = 0;
			changed = true;
		}

		if (now >= state.nextEventTick) {
			fireEvent(server, state, now);
			changed = true;
		}

		if (changed || now % PERIODIC_BROADCAST_TICKS == 0) {
			broadcast(server, state, now);
		}
	}

	private static void fireEvent(MinecraftServer server, ChaosState state, long now) {
		List<ServerPlayer> players = server.getPlayerList().getPlayers();
		ChaosConfig cfg = ChaosConfig.get();
		int interval = cfg.intervalTicks();

		if (players.isEmpty()) {
			state.nextEventTick = now + interval;
			state.setDirty();
			return;
		}

		if (ChaosEventRegistry.INSTANCE.size() == 0) {
			state.nextEventTick = now + interval;
			state.setDirty();
			return;
		}

		RandomSource rng = server.overworld().getRandom();
		List<UUID> uuids = uuidsOf(players);
		UUID victimUuid = pickVictimUuid(uuids, state.lastVictimUuid, state.consecutivePicks, rng);
		ServerPlayer victim = findByUuid(players, victimUuid);
		if (victim == null) {
			state.nextEventTick = now + interval;
			state.setDirty();
			return;
		}

		ChaosEvent event = ChaosEventRegistry.INSTANCE.pickRandom(rng);
		try {
			event.apply(victim);
		} catch (Throwable t) {
			RandomChaosMod.LOGGER.error("Chaos event {} threw while applying to {}", event.id(), victim.getName().getString(), t);
		}

		int duration = clampDuration(event.defaultDurationTicks(), interval, cfg.effectCapRatio);
		if (duration > 0) {
			state.currentEventId = event.id().toString();
			state.currentVictimUuid = victim.getUUID();
			state.currentEffectExpiryTick = now + duration;
		} else {
			state.currentEventId = "";
			state.currentVictimUuid = null;
			state.currentEffectExpiryTick = 0;
		}

		if (victim.getUUID().equals(state.lastVictimUuid)) {
			state.consecutivePicks = state.consecutivePicks + 1;
		} else {
			state.lastVictimUuid = victim.getUUID();
			state.consecutivePicks = 1;
		}

		state.nextEventTick = now + interval;
		state.setDirty();
	}

	public static UUID pickVictimUuid(List<UUID> playerUuids, UUID lastVictimUuid, int consecutivePicks, RandomSource rng) {
		if (playerUuids.isEmpty()) {
			throw new IllegalStateException("no players to pick a victim from");
		}
		if (playerUuids.size() == 1) {
			return playerUuids.get(0);
		}
		for (int attempt = 0; attempt < 16; attempt++) {
			UUID candidate = playerUuids.get(rng.nextInt(playerUuids.size()));
			if (lastVictimUuid != null && candidate.equals(lastVictimUuid) && consecutivePicks >= 4) {
				continue;
			}
			return candidate;
		}
		for (UUID candidate : playerUuids) {
			if (lastVictimUuid == null || !candidate.equals(lastVictimUuid)) {
				return candidate;
			}
		}
		return playerUuids.get(0);
	}

	public static int clampDuration(int eventDurationTicks, int intervalTicks, double effectCapRatio) {
		if (eventDurationTicks <= 0) return 0;
		int cap = (int) Math.round(intervalTicks * effectCapRatio);
		return Math.min(eventDurationTicks, cap);
	}

	private static List<UUID> uuidsOf(List<ServerPlayer> players) {
		List<UUID> out = new ArrayList<>(players.size());
		for (ServerPlayer p : players) out.add(p.getUUID());
		return out;
	}

	private static ServerPlayer findByUuid(List<ServerPlayer> players, UUID uuid) {
		for (ServerPlayer p : players) {
			if (p.getUUID().equals(uuid)) return p;
		}
		return null;
	}

	public static void broadcast(MinecraftServer server, ChaosState state, long serverTick) {
		ChaosNetworking.broadcast(server, toPayload(state, serverTick));
	}

	public static void sendTo(ServerPlayer player, ChaosState state, long serverTick) {
		ChaosNetworking.sendTo(player, toPayload(state, serverTick));
	}

	public static ChaosStatePayload toPayload(ChaosState state, long serverTick) {
		return new ChaosStatePayload(
			serverTick,
			state.challengeStartTick,
			state.challengeEndTick,
			state.nextEventTick,
			state.currentEventId == null ? "" : state.currentEventId,
			state.currentVictimUuid,
			state.currentEffectExpiryTick
		);
	}
}
