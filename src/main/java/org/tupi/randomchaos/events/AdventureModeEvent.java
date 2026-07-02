package org.tupi.randomchaos.events;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import org.tupi.randomchaos.RandomChaosMod;
import org.tupi.randomchaos.event.ChaosEvent;
import org.tupi.randomchaos.event.ChaosTier;

public class AdventureModeEvent implements ChaosEvent {
    private final Map<UUID, GameType> previousGameModes = new HashMap<>();

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
        previousGameModes.put(victim.getUUID(), victim.gameMode.getGameModeForPlayer());
        victim.gameMode.changeGameModeForPlayer(GameType.ADVENTURE);
        RandomChaosMod.LOGGER.info("Switched {} to adventure mode", victim.getName().getString());
    }

    @Override
    public void onEnd(ServerPlayer victim) {
        restoreIfSaved(victim);
    }

    public void restoreIfSaved(ServerPlayer player) {
        GameType previous = previousGameModes.remove(player.getUUID());
        if (previous != null) {
            player.gameMode.changeGameModeForPlayer(previous);
            RandomChaosMod.LOGGER.info("Restored game mode {} for {}", previous, player.getName().getString());
        }
    }
}
