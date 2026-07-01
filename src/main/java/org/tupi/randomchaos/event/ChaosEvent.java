package org.tupi.randomchaos.event;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

public interface ChaosEvent {
    Identifier id();
    void apply(ServerPlayer victim);
    int defaultDurationTicks();
    default boolean instant() { return defaultDurationTicks() <= 0; }
}
