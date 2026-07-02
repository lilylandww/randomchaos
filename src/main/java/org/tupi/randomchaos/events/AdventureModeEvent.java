package org.tupi.randomchaos.events;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import org.tupi.randomchaos.RandomChaosMod;
import org.tupi.randomchaos.event.ChaosEvent;
import org.tupi.randomchaos.event.ChaosTier;
import org.tupi.randomchaos.state.ChaosState;

public class AdventureModeEvent implements ChaosEvent {

    @Override
    public Identifier id() {
        return RandomChaosMod.id("adventure_mode");
    }

    @Override
    public ChaosTier tier() {
        return ChaosTier.MINOR;
    }

    @Override
    public int defaultDurationTicks() {
        return Integer.MAX_VALUE / 2;
    }

    @Override
    public void apply(ServerPlayer victim) {
        ChaosState state = ChaosState.get(victim.level().getServer());
        state.pendingGameModeRestores.put(victim.getUUID(), victim.gameMode.getGameModeForPlayer());
        state.setDirty();
        victim.gameMode.changeGameModeForPlayer(GameType.ADVENTURE);
        RandomChaosMod.LOGGER.info("Switched {} to adventure mode", victim.getName().getString());
    }

    @Override
    public void onEnd(ServerPlayer victim) {
        MinecraftServer server = victim.level().getServer();
        if (server == null) return;
        ChaosState state = ChaosState.get(server);
        GameType previous = state.pendingGameModeRestores.remove(victim.getUUID());
        if (previous != null) {
            victim.gameMode.changeGameModeForPlayer(previous);
            state.setDirty();
            RandomChaosMod.LOGGER.info("Restored game mode {} for {}", previous, victim.getName().getString());
        }
    }
}
