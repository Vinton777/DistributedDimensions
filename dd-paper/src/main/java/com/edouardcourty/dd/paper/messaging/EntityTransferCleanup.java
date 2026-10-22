package com.edouardcourty.dd.paper.messaging;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;

/** Removes a transferred source entity without leaving its inventory or passengers behind. */
public final class EntityTransferCleanup {
    private EntityTransferCleanup() {}

    public static void remove(Entity entity) {
        if (entity instanceof Player) return;
        for (Entity passenger : List.copyOf(entity.getPassengers())) {
            entity.removePassenger(passenger);
            if (!(passenger instanceof Player)) remove(passenger);
        }
        if (entity instanceof InventoryHolder holder) {
            holder.getInventory().clear();
        }
        if (entity instanceof LivingEntity living && living.getEquipment() != null) {
            if (entity instanceof Wolf || entity instanceof AbstractHorse || entity instanceof org.bukkit.entity.SulfurCube) {
                living.getEquipment().setItem(EquipmentSlot.BODY, new ItemStack(Material.AIR));
            }
            living.getEquipment().clear();
        }
        entity.remove();
    }
}
