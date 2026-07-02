package org.tupi.randomchaos.scheduler;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;

import org.tupi.randomchaos.RandomChaosMod;
import org.tupi.randomchaos.config.ChaosConfig;
import org.tupi.randomchaos.event.ChaosEvent;
import org.tupi.randomchaos.event.ChaosEventRegistry;
import org.tupi.randomchaos.event.ChaosTier;
import org.tupi.randomchaos.net.ChaosNetworking;
import org.tupi.randomchaos.net.ChaosStatePayload;
import org.tupi.randomchaos.state.ChaosState;
import org.tupi.randomchaos.state.DeferredAction;

public final class ChaosScheduler {
	private static final int PERIODIC_BROADCAST_TICKS = 20;

	private ChaosScheduler() {}

	public static long gameTime(MinecraftServer server) {
		return server.overworld().getGameTime();
	}

	public static void tick(MinecraftServer server) {
		ChaosState state = ChaosState.get(server);
		if (state.challengeStartTick == 0) return;
		if (state.challengeEndTick != 0) return;

		long now = gameTime(server);
		boolean changed = false;
		List<ServerPlayer> players = server.getPlayerList().getPlayers();

		if (state.currentEffectExpiryTick != 0 && now >= state.currentEffectExpiryTick) {
			endCurrentEvent(players, state);
			changed = true;
		}

		if (state.currentEffectExpiryTick != 0 && now < state.currentEffectExpiryTick) {
			tickCurrentEvent(players, state, now);
		}

		if (now >= state.nextEventTick) {
			fireEvent(server, state, players, now);
			changed = true;
		}

		if (drainDeferred(server, state, now)) {
			changed = true;
		}

		if (changed || now % PERIODIC_BROADCAST_TICKS == 0) {
			broadcast(server, state, now);
		}
	}

	private static void endCurrentEvent(List<ServerPlayer> players, ChaosState state) {
		ServerPlayer victim = null;
		if (state.currentEventId != null && !state.currentEventId.isBlank() && state.currentVictimUuid != null) {
			victim = findByUuid(players, state.currentVictimUuid);
		}
		endCurrentEventWith(victim, state);
	}

	/**
	 * End the current event immediately for a known victim (e.g. at disconnect).
	 * Calls {@code onEnd} with the given player, then clears the active-event fields.
	 */
	public static void endCurrentEventFor(ServerPlayer victim, ChaosState state) {
		endCurrentEventWith(victim, state);
	}

	private static void endCurrentEventWith(ServerPlayer victim, ChaosState state) {
		if (state.currentEventId != null && !state.currentEventId.isBlank()) {
			ChaosEvent event = lookupEvent(state.currentEventId);
			if (event != null && victim != null) {
				try {
					event.onEnd(victim);
				} catch (Throwable t) {
					RandomChaosMod.LOGGER.error("Chaos event {} threw in onEnd", event.id(), t);
				}
			}
		}
		state.currentEventId = "";
		state.currentVictimUuid = null;
		state.currentEffectExpiryTick = 0;
	}

	private static void tickCurrentEvent(List<ServerPlayer> players, ChaosState state, long now) {
		if (state.currentEventId == null || state.currentEventId.isBlank() || state.currentVictimUuid == null) {
			return;
		}
		ChaosEvent event = lookupEvent(state.currentEventId);
		if (event == null) {
			return;
		}
		ServerPlayer victim = findByUuid(players, state.currentVictimUuid);
		if (victim == null) {
			return;
		}
		try {
			event.tick(victim, now);
		} catch (Throwable t) {
			RandomChaosMod.LOGGER.error("Chaos event {} threw in tick", event.id(), t);
		}
	}

	private static ChaosEvent lookupEvent(String id) {
		Identifier identifier = Identifier.tryParse(id);
		if (identifier == null) {
			return null;
		}
		return ChaosEventRegistry.INSTANCE.get(identifier);
	}

	private static void fireEvent(MinecraftServer server, ChaosState state, List<ServerPlayer> players, long now) {
		ChaosConfig cfg = ChaosConfig.get();
		int interval = cfg.intervalTicks();

		if (players.isEmpty()) {
			state.nextEventTick = now + interval;
			state.setDirty();
			return;
		}

		ChaosEventRegistry registry = ChaosEventRegistry.INSTANCE;
		if (registry.size() == 0) {
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

		ChaosTier tier = chooseTier(
			rng,
			state.picksSinceLastMajor,
			cfg.majorCooldownPicks,
			cfg.minorWeight, cfg.mediumWeight, cfg.majorWeight,
			registry.hasTier(ChaosTier.MINOR),
			registry.hasTier(ChaosTier.MEDIUM),
			registry.hasTier(ChaosTier.MAJOR));
		ChaosEvent event = registry.pickRandom(rng, tier);

		boolean applied = true;
		try {
			event.apply(victim);
		} catch (Throwable t) {
			applied = false;
			RandomChaosMod.LOGGER.error("Chaos event {} threw while applying to {}", event.id(), victim.getName().getString(), t);
		}

		if (applied) {
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

			Component chatMsg = Component.literal("[Chaos] " + victim.getName().getString() + " - " + event.displayName())
				.withColor(0xFFAA00);
			server.getPlayerList().broadcastSystemMessage(chatMsg, false);
		}

		state.picksSinceLastMajor = (tier == ChaosTier.MAJOR) ? 0 : state.picksSinceLastMajor + 1;

		if (victim.getUUID().equals(state.lastVictimUuid)) {
			state.consecutivePicks = state.consecutivePicks + 1;
		} else {
			state.lastVictimUuid = victim.getUUID();
			state.consecutivePicks = 1;
		}

		state.nextEventTick = now + interval;
		state.setDirty();
	}

	public static ChaosTier chooseTier(
			RandomSource rng,
			int picksSinceLastMajor,
			int majorCooldownPicks,
			int minorWeight, int mediumWeight, int majorWeight,
			boolean hasMinor, boolean hasMedium, boolean hasMajor) {
		boolean majorOnCooldown = picksSinceLastMajor < majorCooldownPicks;
		boolean majorAvailable = hasMajor && !majorOnCooldown;

		List<ChaosTier> candidates = new ArrayList<>();
		if (hasMinor) candidates.add(ChaosTier.MINOR);
		if (hasMedium) candidates.add(ChaosTier.MEDIUM);
		if (majorAvailable) candidates.add(ChaosTier.MAJOR);

		if (candidates.isEmpty() && hasMajor) {
			candidates.add(ChaosTier.MAJOR);
		}
		if (candidates.isEmpty()) {
			throw new IllegalStateException("no chaos events registered for any tier");
		}
		if (candidates.size() == 1) {
			return candidates.get(0);
		}

		int sum = 0;
		for (ChaosTier t : candidates) {
			sum += weightOf(t, minorWeight, mediumWeight, majorWeight);
		}
		if (sum <= 0) {
			return candidates.get(rng.nextInt(candidates.size()));
		}
		int roll = rng.nextInt(sum);
		int acc = 0;
		for (ChaosTier t : candidates) {
			acc += weightOf(t, minorWeight, mediumWeight, majorWeight);
			if (roll < acc) {
				return t;
			}
		}
		return candidates.get(candidates.size() - 1);
	}

	private static int weightOf(ChaosTier tier, int minor, int medium, int major) {
		return switch (tier) {
			case MINOR -> minor;
			case MEDIUM -> medium;
			case MAJOR -> major;
		};
	}

	private static boolean drainDeferred(MinecraftServer server, ChaosState state, long now) {
		if (state.deferredActions.isEmpty()) {
			return false;
		}
		boolean changed = false;
		Iterator<DeferredAction> it = state.deferredActions.iterator();
		while (it.hasNext()) {
			DeferredAction action = it.next();
			if (action.fireAtTick() < now - DeferredAction.STALE_TICKS) {
				it.remove();
				changed = true;
			} else if (action.fireAtTick() <= now) {
				dispatchDeferred(server, action);
				it.remove();
				changed = true;
			}
		}
		if (changed) {
			state.setDirty();
		}
		return changed;
	}

	private static void dispatchDeferred(MinecraftServer server, DeferredAction action) {
		if (DeferredAction.KIND_LIGHTNING.equals(action.kind())) {
			ServerPlayer victim = server.getPlayerList().getPlayer(action.victimUuid());
			if (victim != null && !victim.isRemoved()) {
				spawnLightningNear(victim);
			}
		} else {
			RandomChaosMod.LOGGER.warn("Unknown deferred action kind: {}", action.kind());
		}
	}

	public static void spawnLightningNear(ServerPlayer victim) {
		ServerLevel level = victim.level();
		RandomSource rng = level.getRandom();
		double angle = rng.nextDouble() * Math.PI * 2;
		double distance = 2 + rng.nextInt(3);
		double x = victim.getX() + Math.cos(angle) * distance;
		double z = victim.getZ() + Math.sin(angle) * distance;

		LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.EVENT);
		if (bolt == null) {
			RandomChaosMod.LOGGER.warn("Failed to create lightning entity");
			return;
		}
		bolt.setPos(x, victim.getY(), z);
		bolt.setVisualOnly(false);
		level.addFreshEntity(bolt);
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
		if (cap <= 0) return 0;
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
