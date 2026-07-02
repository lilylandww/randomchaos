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

public class CobbleCageEvent implements ChaosEvent {
    @Override
    public Identifier id() {
        return RandomChaosMod.id("cobble_cage");
    }

    @Override
    public ChaosTier tier() {
        return ChaosTier.MINOR;
    }

    @Override
    public int defaultDurationTicks() {
        return 0;
    }

    @Override
    public void apply(ServerPlayer victim) {
        ServerLevel level = victim.level();
        BlockPos feet = victim.blockPosition();
        BlockState cobble = Blocks.COBBLESTONE.defaultBlockState();

        int placed = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                for (int dy = 0; dy <= 1; dy++) {
                    BlockPos pos = feet.offset(dx, dy, dz);
                    if (level.getBlockState(pos).canBeReplaced()) {
                        level.setBlock(pos, cobble, 3);
                        placed++;
                    }
                }
            }
        }
        RandomChaosMod.LOGGER.info("Built cobble cage around {} ({} blocks)", victim.getName().getString(), placed);
    }
}
