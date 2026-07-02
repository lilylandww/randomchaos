package org.tupi.randomchaos.events;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import org.tupi.randomchaos.RandomChaosMod;
import org.tupi.randomchaos.event.ChaosEvent;
import org.tupi.randomchaos.event.ChaosTier;

public class ButterFingersEvent implements ChaosEvent {
    @Override
    public Identifier id() {
        return RandomChaosMod.id("butter_fingers");
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
        Inventory inventory = victim.getInventory();
        ItemStack held = inventory.removeFromSelected(true);
        if (held.isEmpty()) {
            RandomChaosMod.LOGGER.info("Butter fingers: {} had nothing in hand", victim.getName().getString());
            return;
        }
        victim.drop(held, false, true);
        RandomChaosMod.LOGGER.info("Butter fingers: {} dropped {}", victim.getName().getString(), held.getCount());
    }
}
