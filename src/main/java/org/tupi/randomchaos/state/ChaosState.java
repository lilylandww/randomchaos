package org.tupi.randomchaos.state;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import org.tupi.randomchaos.RandomChaosMod;

public class ChaosState extends SavedData {
	private static final String DATA_NAME = "randomchaos_state";

	public static final Codec<ChaosState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.LONG.fieldOf("challenge_start_tick").forGetter(s -> s.challengeStartTick),
		Codec.LONG.fieldOf("challenge_end_tick").forGetter(s -> s.challengeEndTick),
		Codec.LONG.fieldOf("next_event_tick").forGetter(s -> s.nextEventTick),
		Codec.STRING.fieldOf("current_event_id").forGetter(s -> s.currentEventId),
		UUIDUtil.CODEC.optionalFieldOf("current_victim_uuid").forGetter(s -> Optional.ofNullable(s.currentVictimUuid)),
		Codec.LONG.fieldOf("current_effect_expiry_tick").forGetter(s -> s.currentEffectExpiryTick),
		Codec.LONG.optionalFieldOf("current_effect_start_tick", 0L).forGetter(s -> s.currentEffectStartTick),
		UUIDUtil.CODEC.optionalFieldOf("last_victim_uuid").forGetter(s -> Optional.ofNullable(s.lastVictimUuid)),
		Codec.INT.fieldOf("consecutive_picks").forGetter(s -> s.consecutivePicks),
		Codec.INT.optionalFieldOf("picks_since_last_major", 0).forGetter(s -> s.picksSinceLastMajor),
		DeferredAction.CODEC.listOf().optionalFieldOf("deferred_actions").forGetter(s -> Optional.of(s.deferredActions)),
		Codec.unboundedMap(UUIDUtil.CODEC, GameType.CODEC)
			.optionalFieldOf("pending_game_mode_restores", Map.of())
			.forGetter(s -> s.pendingGameModeRestores)
	).apply(instance, (challengeStartTick, challengeEndTick, nextEventTick, currentEventId,
			currentVictimUuid, currentEffectExpiryTick, currentEffectStartTick, lastVictimUuid, consecutivePicks,
			picksSinceLastMajor, deferredActions, pendingGameModeRestores) -> {
		ChaosState state = new ChaosState();
		state.challengeStartTick = challengeStartTick;
		state.challengeEndTick = challengeEndTick;
		state.nextEventTick = nextEventTick;
		state.currentEventId = currentEventId;
		state.currentVictimUuid = currentVictimUuid.orElse(null);
		state.currentEffectExpiryTick = currentEffectExpiryTick;
		state.currentEffectStartTick = currentEffectStartTick;
		state.lastVictimUuid = lastVictimUuid.orElse(null);
		state.consecutivePicks = consecutivePicks;
		state.picksSinceLastMajor = picksSinceLastMajor;
		state.deferredActions = new ArrayList<>(deferredActions.orElseGet(ArrayList::new));
		state.pendingGameModeRestores = new HashMap<>(pendingGameModeRestores);
		return state;
	}));

	public static final SavedDataType<ChaosState> TYPE = new SavedDataType<>(
		RandomChaosMod.id(DATA_NAME),
		ChaosState::new,
		ChaosState.CODEC,
		null
	);

	public long challengeStartTick;
	public long challengeEndTick;
	public long nextEventTick;
	public String currentEventId = "";
	public UUID currentVictimUuid;
	public long currentEffectExpiryTick;
	public long currentEffectStartTick;
	public UUID lastVictimUuid;
	public int consecutivePicks;
	public int picksSinceLastMajor;
	public List<DeferredAction> deferredActions = new ArrayList<>();
	public Map<UUID, GameType> pendingGameModeRestores = new HashMap<>();

	public ChaosState() {
	}

	public void enqueueDeferred(DeferredAction action) {
		deferredActions.add(action);
		while (deferredActions.size() > DeferredAction.MAX_QUEUE) {
			deferredActions.remove(0);
		}
		setDirty();
	}

	public static ChaosState get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}
}
