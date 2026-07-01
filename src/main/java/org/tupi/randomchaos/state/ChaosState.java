package org.tupi.randomchaos.state;

import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import org.tupi.randomchaos.RandomChaosMod;

public class ChaosState extends SavedData {
	private static final String DATA_NAME = "randomchaos_state";

	public static final SavedDataType<ChaosState> TYPE = new SavedDataType<>(
		RandomChaosMod.id(DATA_NAME),
		ChaosState::new,
		ChaosState.CODEC,
		null
	);

	public static final Codec<ChaosState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.LONG.fieldOf("challenge_start_tick").forGetter(s -> s.challengeStartTick),
		Codec.LONG.fieldOf("challenge_end_tick").forGetter(s -> s.challengeEndTick),
		Codec.LONG.fieldOf("next_event_tick").forGetter(s -> s.nextEventTick),
		Codec.STRING.fieldOf("current_event_id").forGetter(s -> s.currentEventId),
		UUIDUtil.CODEC.optionalFieldOf("current_victim_uuid").forGetter(s -> Optional.ofNullable(s.currentVictimUuid)),
		Codec.LONG.fieldOf("current_effect_expiry_tick").forGetter(s -> s.currentEffectExpiryTick),
		UUIDUtil.CODEC.optionalFieldOf("last_victim_uuid").forGetter(s -> Optional.ofNullable(s.lastVictimUuid)),
		Codec.INT.fieldOf("consecutive_picks").forGetter(s -> s.consecutivePicks)
	).apply(instance, (challengeStartTick, challengeEndTick, nextEventTick, currentEventId,
			currentVictimUuid, currentEffectExpiryTick, lastVictimUuid, consecutivePicks) -> {
		ChaosState state = new ChaosState();
		state.challengeStartTick = challengeStartTick;
		state.challengeEndTick = challengeEndTick;
		state.nextEventTick = nextEventTick;
		state.currentEventId = currentEventId;
		state.currentVictimUuid = currentVictimUuid.orElse(null);
		state.currentEffectExpiryTick = currentEffectExpiryTick;
		state.lastVictimUuid = lastVictimUuid.orElse(null);
		state.consecutivePicks = consecutivePicks;
		return state;
	}));

	public long challengeStartTick;
	public long challengeEndTick;
	public long nextEventTick;
	public String currentEventId = "";
	public UUID currentVictimUuid;
	public long currentEffectExpiryTick;
	public UUID lastVictimUuid;
	public int consecutivePicks;

	public ChaosState() {
	}

	public static ChaosState get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}
}
