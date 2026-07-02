package org.tupi.randomchaos.events;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;

import org.tupi.randomchaos.RandomChaosMod;
import org.tupi.randomchaos.event.ChaosEvent;
import org.tupi.randomchaos.event.ChaosTier;

public class SpawnCreeperEvent implements ChaosEvent {
    @Override
    public Identifier id() {
        return RandomChaosMod.id("spawn_creeper");
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
        RandomSource rng = level.getRandom();

        double angle = rng.nextDouble() * Math.PI * 2;
        double distance = 3 + rng.nextInt(3);
        double x = victim.getX() + Math.cos(angle) * distance;
        double z = victim.getZ() + Math.sin(angle) * distance;

        Creeper creeper = EntityType.CREEPER.create(level, EntitySpawnReason.EVENT);
        if (creeper == null) {
            RandomChaosMod.LOGGER.warn("Failed to create creeper entity");
            return;
        }
        creeper.setPos(x, victim.getY(), z);
        creeper.setYRot(rng.nextFloat() * 360f);
        level.addFreshEntity(creeper);

        RandomChaosMod.LOGGER.info("Spawned creeper near {}", victim.getName().getString());
    }
}
