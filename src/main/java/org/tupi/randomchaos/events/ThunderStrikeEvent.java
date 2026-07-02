package org.tupi.randomchaos.events;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import org.tupi.randomchaos.RandomChaosMod;
import org.tupi.randomchaos.event.ChaosEvent;
import org.tupi.randomchaos.event.ChaosTier;
import org.tupi.randomchaos.scheduler.ChaosScheduler;
import org.tupi.randomchaos.state.ChaosState;
import org.tupi.randomchaos.state.DeferredAction;

public class ThunderStrikeEvent implements ChaosEvent {
    private static final long STRIKE_INTERVAL_TICKS = 60L;

    @Override
    public Identifier id() {
        return RandomChaosMod.id("thunder_strike");
    }

    @Override
    public ChaosTier tier() {
        return ChaosTier.MAJOR;
    }

    @Override
    public int defaultDurationTicks() {
        return 0;
    }

    @Override
    public void apply(ServerPlayer victim) {
        MinecraftServer server = victim.level().getServer();
        if (server == null) {
            return;
        }
        ChaosState state = ChaosState.get(server);
        long now = server.getTickCount();

        ChaosScheduler.spawnLightningNear(victim);
        state.enqueueDeferred(new DeferredAction(now + STRIKE_INTERVAL_TICKS, victim.getUUID(), DeferredAction.KIND_LIGHTNING));
        state.enqueueDeferred(new DeferredAction(now + STRIKE_INTERVAL_TICKS * 2, victim.getUUID(), DeferredAction.KIND_LIGHTNING));

        RandomChaosMod.LOGGER.info("Thunder strike begun near {}", victim.getName().getString());
    }
}
