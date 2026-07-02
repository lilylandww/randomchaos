package org.tupi.randomchaos.events;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import org.tupi.randomchaos.RandomChaosMod;
import org.tupi.randomchaos.event.ChaosEvent;
import org.tupi.randomchaos.event.ChaosTier;

public class TeleportToGroundEvent implements ChaosEvent {
    @Override
    public Identifier id() {
        return RandomChaosMod.id("teleport_to_ground");
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
        BlockPos feet = victim.blockPosition();
        int x = feet.getX();
        int z = feet.getZ();

        int targetY = findSafeSurface(level, x, feet.getY(), z);
        if (targetY < 0) {
            int heightmapY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (isSafeLanding(level, heightmapY, x, z)) {
                targetY = heightmapY;
            } else {
                RandomChaosMod.LOGGER.warn("TeleportToGround: no safe landing for {}", victim.getName().getString());
                return;
            }
        }

        victim.teleportTo(x + 0.5, targetY, z + 0.5);
        RandomChaosMod.LOGGER.info("Teleported {} to ground level y={}", victim.getName().getString(), targetY);
    }

    private static int findSafeSurface(ServerLevel level, int x, int fromY, int z) {
        for (int y = fromY; y > level.getMinY(); y--) {
            if (isSafeLanding(level, y, x, z)) {
                return y;
            }
        }
        return -1;
    }

    private static boolean isSafeLanding(ServerLevel level, int feetY, int x, int z) {
        BlockState feetBlock = level.getBlockState(new BlockPos(x, feetY, z));
        BlockState headBlock = level.getBlockState(new BlockPos(x, feetY + 1, z));
        BlockState floorBlock = level.getBlockState(new BlockPos(x, feetY - 1, z));
        return feetBlock.isAir() && headBlock.isAir() && !floorBlock.isAir();
    }
}
