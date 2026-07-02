package org.tupi.randomchaos.events;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import org.tupi.randomchaos.RandomChaosMod;
import org.tupi.randomchaos.event.ChaosEvent;
import org.tupi.randomchaos.event.ChaosTier;

public class HotbarShuffleEvent implements ChaosEvent {
    @Override
    public Identifier id() {
        return RandomChaosMod.id("hotbar_shuffle");
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
        Inventory inventory = victim.getInventory();
        RandomSource rng = victim.level().getRandom();
        ItemStack[] slots = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            slots[i] = inventory.getItem(i);
        }
        for (int i = slots.length - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            ItemStack tmp = slots[i];
            slots[i] = slots[j];
            slots[j] = tmp;
        }
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, slots[i]);
        }
        RandomChaosMod.LOGGER.info("Hotbar shuffle: shuffled hotbar for {}", victim.getName().getString());
    }
}
