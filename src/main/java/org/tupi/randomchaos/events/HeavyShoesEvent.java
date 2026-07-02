package org.tupi.randomchaos.events;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import org.tupi.randomchaos.RandomChaosMod;
import org.tupi.randomchaos.event.ChaosEvent;
import org.tupi.randomchaos.event.ChaosTier;

public class HeavyShoesEvent implements ChaosEvent {
    private static final int DURATION_TICKS = 200;

    @Override
    public Identifier id() {
        return RandomChaosMod.id("heavy_shoes");
    }

    @Override
    public ChaosTier tier() {
        return ChaosTier.MINOR;
    }

    @Override
    public int defaultDurationTicks() {
        return DURATION_TICKS;
    }

    @Override
    public void apply(ServerPlayer victim) {
        victim.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, DURATION_TICKS, 5));
        victim.addEffect(new MobEffectInstance(MobEffects.LEVITATION, DURATION_TICKS, 0));
        RandomChaosMod.LOGGER.info("Heavy shoes: applied jump boost VI + levitation to {}", victim.getName().getString());
    }
}
