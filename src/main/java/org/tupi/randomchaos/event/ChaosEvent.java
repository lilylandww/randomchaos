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

    default String displayName() {
        String path = id().getPath();
        StringBuilder sb = new StringBuilder();
        boolean capitalize = true;
        for (char c : path.toCharArray()) {
            if (c == '_') {
                sb.append(' ');
                capitalize = true;
            } else if (capitalize) {
                sb.append(Character.toUpperCase(c));
                capitalize = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    default void tick(ServerPlayer victim, long now) {
    }

    default void onEnd(ServerPlayer victim) {
    }
}
