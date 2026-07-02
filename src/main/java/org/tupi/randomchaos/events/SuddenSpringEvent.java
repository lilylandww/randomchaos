package org.tupi.randomchaos.events;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

import org.tupi.randomchaos.RandomChaosMod;
import org.tupi.randomchaos.event.ChaosEvent;
import org.tupi.randomchaos.event.ChaosTier;

public class SuddenSpringEvent implements ChaosEvent {
    @Override
    public Identifier id() {
        return RandomChaosMod.id("sudden_spring");
    }

    @Override
    public ChaosTier tier() {
        return ChaosTier.MEDIUM;
    }

    @Override
    public int defaultDurationTicks() {
        return 0;
    }

    @Override
    public void apply(ServerPlayer victim) {
        ServerLevel level = victim.level();
        BlockPos feet = victim.blockPosition();
        if (!level.getBlockState(feet).canBeReplaced()) {
            RandomChaosMod.LOGGER.info("Sudden spring: block at {} not replaceable for {}", feet, victim.getName().getString());
            return;
        }
        level.setBlock(feet, Blocks.WATER.defaultBlockState(), 3);
        RandomChaosMod.LOGGER.info("Sudden spring: placed water at feet of {}", victim.getName().getString());
    }
}
