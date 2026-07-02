package org.tupi.randomchaos.events;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.spider.Spider;

import org.tupi.randomchaos.RandomChaosMod;
import org.tupi.randomchaos.event.ChaosEvent;
import org.tupi.randomchaos.event.ChaosTier;

public class SpawnSpiderEvent implements ChaosEvent {
    @Override
    public Identifier id() {
        return RandomChaosMod.id("spawn_spider");
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
        RandomSource rng = level.getRandom();

        double angle = rng.nextDouble() * Math.PI * 2;
        double distance = 3 + rng.nextInt(3);
        double x = victim.getX() + Math.cos(angle) * distance;
        double z = victim.getZ() + Math.sin(angle) * distance;

        Spider spider = EntityType.SPIDER.create(level, EntitySpawnReason.EVENT);
        if (spider == null) {
            RandomChaosMod.LOGGER.warn("Failed to create spider entity");
            return;
        }
        spider.setPos(x, victim.getY(), z);
        spider.setYRot(rng.nextFloat() * 360f);
        level.addFreshEntity(spider);

        RandomChaosMod.LOGGER.info("Spawned spider near {}", victim.getName().getString());
    }
}
