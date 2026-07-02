package org.tupi.randomchaos.events;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;

import org.tupi.randomchaos.RandomChaosMod;
import org.tupi.randomchaos.event.ChaosEvent;
import org.tupi.randomchaos.event.ChaosTier;

public class TheGangsAllHereEvent implements ChaosEvent {
    private static final int RING_DISTANCE = 7;
    private static final int MIN_DISTANCE = 2;

    @Override
    public Identifier id() {
        return RandomChaosMod.id("the_gangs_all_here");
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
        ServerLevel level = victim.level();
        EntityType<?>[] types = {
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER, EntityType.SPIDER
        };
        int spawned = 0;
        for (int i = 0; i < types.length; i++) {
            double angle = (Math.PI * 2 * i) / types.length;
            BlockPos pos = findSpawnPos(level, victim, angle);
            if (pos == null) {
                continue;
            }
            Entity entity = types[i].create(level, EntitySpawnReason.EVENT);
            if (entity == null) {
                continue;
            }
            entity.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            entity.setYRot((float) Math.toDegrees(angle) - 90f);
            level.addFreshEntity(entity);
            spawned++;
        }
        RandomChaosMod.LOGGER.info("The gang's all here: spawned {} mobs around {}", spawned, victim.getName().getString());
    }

    private static BlockPos findSpawnPos(ServerLevel level, ServerPlayer victim, double angle) {
        for (int distance = RING_DISTANCE; distance >= MIN_DISTANCE; distance--) {
            int dx = (int) Math.round(Math.cos(angle) * distance);
            int dz = (int) Math.round(Math.sin(angle) * distance);
            BlockPos feet = victim.blockPosition().offset(dx, 0, dz);
            BlockPos head = feet.above();
            if (level.getBlockState(feet).canBeReplaced()
                    && level.getBlockState(head).canBeReplaced()) {
                return feet;
            }
        }
        return null;
    }
}
