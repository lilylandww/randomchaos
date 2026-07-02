package org.tupi.randomchaos.events;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import org.tupi.randomchaos.RandomChaosMod;
import org.tupi.randomchaos.event.ChaosEvent;
import org.tupi.randomchaos.event.ChaosTier;

public class ClayFillEvent implements ChaosEvent {
    @Override
    public Identifier id() {
        return RandomChaosMod.id("clay_fill");
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
        int filled = 0;
        for (int i = 0; i < 36; i++) {
            if (inventory.getItem(i).isEmpty()) {
                inventory.setItem(i, new ItemStack(Blocks.CLAY));
                filled++;
            }
        }
        RandomChaosMod.LOGGER.info("Filled {} empty slots with clay for {}", filled, victim.getName().getString());
    }
}
