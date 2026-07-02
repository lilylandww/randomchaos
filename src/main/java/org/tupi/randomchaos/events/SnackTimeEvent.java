package org.tupi.randomchaos.events;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;

import org.tupi.randomchaos.RandomChaosMod;
import org.tupi.randomchaos.event.ChaosEvent;
import org.tupi.randomchaos.event.ChaosTier;

public class SnackTimeEvent implements ChaosEvent {
    @Override
    public Identifier id() {
        return RandomChaosMod.id("snack_time");
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
        food.eat(4, 0.5f);
        RandomChaosMod.LOGGER.info("Snack time: fed {} (+4 hunger, sat mod 0.5)", victim.getName().getString());
    }
}
