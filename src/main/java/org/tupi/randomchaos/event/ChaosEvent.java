package org.tupi.randomchaos.event;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

public interface ChaosEvent {
    Identifier id();

    void apply(ServerPlayer victim);

    int defaultDurationTicks();

    default boolean instant() {
        return defaultDurationTicks() <= 0;
    }

    default ChaosTier tier() {
        return ChaosTier.MEDIUM;
    }

    default void tick(ServerPlayer victim, long now) {
    }

    default void onEnd(ServerPlayer victim) {
    }
}
