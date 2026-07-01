package org.tupi.randomchaos.events;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;

import org.tupi.randomchaos.RandomChaosMod;
import org.tupi.randomchaos.event.ChaosEvent;

public class SpawnZombieEvent implements ChaosEvent {
    @Override
    public Identifier id() {
        return RandomChaosMod.id("spawn_zombie");
    }

    @Override
    public void apply(ServerPlayer victim) {
        ServerLevel level = victim.level();
        RandomSource rng = level.getRandom();

        double angle = rng.nextDouble() * Math.PI * 2;
        double distance = 3 + rng.nextInt(3);
        double dx = Math.cos(angle) * distance;
        double dz = Math.sin(angle) * distance;
        double x = victim.getX() + dx;
        double y = victim.getY();
        double z = victim.getZ() + dz;

        Zombie zombie = EntityType.ZOMBIE.create(level, EntitySpawnReason.EVENT);
        if (zombie == null) {
            RandomChaosMod.LOGGER.warn("Failed to create zombie entity");
            return;
        }

        zombie.setPos(x, y, z);
        zombie.setYRot(rng.nextFloat() * 360f);
        level.addFreshEntity(zombie);

        RandomChaosMod.LOGGER.info("Spawned zombie near {}", victim.getName().getString());
    }

    @Override
    public int defaultDurationTicks() {
        return 0;
    }
}
