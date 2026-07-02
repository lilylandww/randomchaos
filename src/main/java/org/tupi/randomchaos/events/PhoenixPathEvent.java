package org.tupi.randomchaos.events;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

import org.tupi.randomchaos.RandomChaosMod;
import org.tupi.randomchaos.event.ChaosEvent;
import org.tupi.randomchaos.event.ChaosTier;

public class PhoenixPathEvent implements ChaosEvent {
    private static final int DURATION_TICKS = 600;

    private final Map<UUID, BlockPos> lastPositions = new HashMap<>();

    @Override
    public Identifier id() {
        return RandomChaosMod.id("phoenix_path");
    }

    @Override
    public ChaosTier tier() {
        return ChaosTier.MEDIUM;
    }

    @Override
    public int defaultDurationTicks() {
        return DURATION_TICKS;
    }

    @Override
    public void apply(ServerPlayer victim) {
        lastPositions.put(victim.getUUID(), victim.blockPosition());
        RandomChaosMod.LOGGER.info("Phoenix path started for {}", victim.getName().getString());
    }

    @Override
    public void tick(ServerPlayer victim, long now) {
        BlockPos currentPos = victim.blockPosition();
        BlockPos lastPos = lastPositions.get(victim.getUUID());

        if (lastPos != null && !currentPos.equals(lastPos)) {
            ServerLevel level = victim.level();
            if (level.getBlockState(lastPos).isAir() && level.getBlockState(lastPos.below()).isSolidRender()) {
                level.setBlock(lastPos, Blocks.FIRE.defaultBlockState(), 3);
            }
        }

        lastPositions.put(victim.getUUID(), currentPos);
    }

    @Override
    public void onEnd(ServerPlayer victim) {
        lastPositions.remove(victim.getUUID());
    }
}
