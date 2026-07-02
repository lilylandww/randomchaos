package org.tupi.randomchaos.events;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import org.tupi.randomchaos.RandomChaosMod;
import org.tupi.randomchaos.event.ChaosEvent;
import org.tupi.randomchaos.event.ChaosTier;

public class SugarRushEvent implements ChaosEvent {
    private static final int DURATION_TICKS = 200;

    @Override
    public Identifier id() {
        return RandomChaosMod.id("sugar_rush");
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
        victim.addEffect(new MobEffectInstance(MobEffects.SPEED, DURATION_TICKS, 0));
        RandomChaosMod.LOGGER.info("Sugar rush: applied speed I to {}", victim.getName().getString());
    }
}
