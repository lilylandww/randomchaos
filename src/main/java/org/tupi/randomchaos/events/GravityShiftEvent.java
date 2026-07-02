package org.tupi.randomchaos.events;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import org.tupi.randomchaos.RandomChaosMod;
import org.tupi.randomchaos.event.ChaosEvent;
import org.tupi.randomchaos.event.ChaosTier;

public class GravityShiftEvent implements ChaosEvent {
    private static final int LEVITATION_DURATION = 200;
    private static final int SLOW_FALLING_DURATION = 60;
    private static final int DURATION_TICKS = LEVITATION_DURATION + SLOW_FALLING_DURATION;

    private final Map<UUID, State> states = new HashMap<>();

    private static final class State {
        long levitationEndTick;
        boolean slowFallingApplied;
    }

    @Override
    public Identifier id() {
        return RandomChaosMod.id("gravity_shift");
    }

    @Override
    public ChaosTier tier() {
        return ChaosTier.MAJOR;
    }

    @Override
    public int defaultDurationTicks() {
        return DURATION_TICKS;
    }

    @Override
    public void apply(ServerPlayer victim) {
        long now = victim.level().getGameTime();
        victim.addEffect(new MobEffectInstance(MobEffects.LEVITATION, LEVITATION_DURATION, 4));
        State s = new State();
        s.levitationEndTick = now + LEVITATION_DURATION;
        s.slowFallingApplied = false;
        states.put(victim.getUUID(), s);
        RandomChaosMod.LOGGER.info("Gravity shift: levitating {} for {} ticks", victim.getName().getString(), LEVITATION_DURATION);
    }

    @Override
    public void tick(ServerPlayer victim, long now) {
        State s = states.get(victim.getUUID());
        if (s == null || s.slowFallingApplied) {
            return;
        }
        if (now >= s.levitationEndTick) {
            victim.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, SLOW_FALLING_DURATION, 0));
            s.slowFallingApplied = true;
            RandomChaosMod.LOGGER.info("Gravity shift: slow falling for {}", victim.getName().getString());
        }
    }

    @Override
    public void onEnd(ServerPlayer victim) {
        State s = states.remove(victim.getUUID());
        if (s != null && !s.slowFallingApplied) {
            victim.removeEffect(MobEffects.LEVITATION);
            victim.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40, 0));
            RandomChaosMod.LOGGER.info("Gravity shift: safety slow falling for {}", victim.getName().getString());
        }
    }
}
