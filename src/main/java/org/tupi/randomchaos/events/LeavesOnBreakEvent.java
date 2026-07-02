package org.tupi.randomchaos.events;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

import org.tupi.randomchaos.RandomChaosMod;
import org.tupi.randomchaos.event.ChaosEvent;
import org.tupi.randomchaos.event.ChaosTier;

public class LeavesOnBreakEvent implements ChaosEvent {
    private static final int DURATION_TICKS = 600;

    private final Set<UUID> activePlayers = new HashSet<>();

    public LeavesOnBreakEvent() {
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, entity) -> {
            if (!(player instanceof ServerPlayer serverPlayer)) return;
            if (!activePlayers.contains(serverPlayer.getUUID())) return;
            if (state.is(Blocks.OAK_LEAVES)) return;
            if (!(level instanceof ServerLevel serverLevel)) return;
            serverLevel.setBlock(pos, Blocks.OAK_LEAVES.defaultBlockState(), 3);
        });
    }

    @Override
    public Identifier id() {
        return RandomChaosMod.id("leaves_on_break");
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
        activePlayers.add(victim.getUUID());
        RandomChaosMod.LOGGER.info("Leaves on break started for {}", victim.getName().getString());
    }

    @Override
    public void onEnd(ServerPlayer victim) {
        activePlayers.remove(victim.getUUID());
    }
}
