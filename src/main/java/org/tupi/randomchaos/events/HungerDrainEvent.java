package org.tupi.randomchaos.events;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;

import org.tupi.randomchaos.RandomChaosMod;
import org.tupi.randomchaos.event.ChaosEvent;
import org.tupi.randomchaos.event.ChaosTier;

public class HungerDrainEvent implements ChaosEvent {
    private static final int HUNGER_DRAIN = 3;

    @Override
    public Identifier id() {
        return RandomChaosMod.id("hunger_drain");
    }

    @Override
    public ChaosTier tier() {
        return ChaosTier.MINOR;
    }

    @Override
    public int defaultDurationTicks() {
        return 0;
    }

    @Override
    public void apply(ServerPlayer victim) {
        FoodData food = victim.getFoodData();
        food.setFoodLevel(Math.max(0, food.getFoodLevel() - HUNGER_DRAIN));
        RandomChaosMod.LOGGER.info("Drained {} hunger from {}", HUNGER_DRAIN, victim.getName().getString());
    }
}
