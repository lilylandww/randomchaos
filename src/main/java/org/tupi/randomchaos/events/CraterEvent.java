package org.tupi.randomchaos.events;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import org.tupi.randomchaos.RandomChaosMod;
import org.tupi.randomchaos.event.ChaosEvent;
import org.tupi.randomchaos.event.ChaosTier;

public class CraterEvent implements ChaosEvent {
    private static final double RADIUS = 1.5;
    private static final int MIN_Y_FLOOR_MARGIN = 4;

    @Override
    public Identifier id() {
        return RandomChaosMod.id("crater");
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
        if (victim.isCreative() || victim.isSpectator()) {
            return;
        }
        ServerLevel level = victim.level();
        BlockPos center = victim.blockPosition().below(2);
        if (center.getY() <= level.getMinY() + MIN_Y_FLOOR_MARGIN) {
            RandomChaosMod.LOGGER.warn("CraterEvent: too close to void for {}, aborting", victim.getName().getString());
            return;
        }

        int r = (int) Math.ceil(RADIUS);
        double rSq = RADIUS * RADIUS;
        int destroyed = 0;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dy * dy + dz * dz > rSq) continue;
                    BlockPos pos = center.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) continue;
                    if (state.getBlock() == Blocks.BEDROCK) continue;
                    level.destroyBlock(pos, true);
                    destroyed++;
                }
            }
        }
        RandomChaosMod.LOGGER.info("CraterEvent destroyed {} blocks below {}", destroyed, victim.getName().getString());
    }
}
