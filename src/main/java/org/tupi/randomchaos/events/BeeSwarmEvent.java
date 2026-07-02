package org.tupi.randomchaos.events;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.phys.Vec3;

import org.tupi.randomchaos.RandomChaosMod;
import org.tupi.randomchaos.event.ChaosEvent;
import org.tupi.randomchaos.event.ChaosTier;

public class BeeSwarmEvent implements ChaosEvent {
    @Override
    public Identifier id() {
        return RandomChaosMod.id("bee_swarm");
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
        int count = 3 + rng.nextInt(2);
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            double angle = rng.nextDouble() * Math.PI * 2;
            double distance = 2 + rng.nextInt(3);
            double x = victim.getX() + Math.cos(angle) * distance;
            double z = victim.getZ() + Math.sin(angle) * distance;

            Bee bee = EntityType.BEE.create(level, EntitySpawnReason.EVENT);
            if (bee == null) {
                continue;
            }
            bee.setPos(x, victim.getY(), z);
            bee.setYRot(rng.nextFloat() * 360f);
            bee.setPersistentAngerTarget(EntityReference.of(victim));
            bee.startPersistentAngerTimer();
            level.addFreshEntity(bee);
            spawned++;
        }
        RandomChaosMod.LOGGER.info("Bee swarm: spawned {} angry bees near {}", spawned, victim.getName().getString());
    }
}
