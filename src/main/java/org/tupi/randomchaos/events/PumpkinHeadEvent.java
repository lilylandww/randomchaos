package org.tupi.randomchaos.events;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.tupi.randomchaos.RandomChaosMod;
import org.tupi.randomchaos.event.ChaosEvent;
import org.tupi.randomchaos.event.ChaosTier;

public class PumpkinHeadEvent implements ChaosEvent {
    @Override
    public Identifier id() {
        return RandomChaosMod.id("pumpkin_head");
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
        ItemStack existingHelmet = victim.getItemBySlot(EquipmentSlot.HEAD);
        if (!existingHelmet.isEmpty()) {
            victim.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            if (!victim.getInventory().add(existingHelmet)) {
                victim.drop(existingHelmet, false, true);
            }
        }
        victim.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
        RandomChaosMod.LOGGER.info("Pumpkin head: forced carved pumpkin onto {}", victim.getName().getString());
    }
}
