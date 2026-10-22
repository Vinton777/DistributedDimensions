package com.edouardcourty.dd.paper.messaging;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;

/** Removes a transferred source entity without leaving its inventory or passengers behind. */
public final class EntityTransferCleanup {
    private EntityTransferCleanup() {}

    public static void remove(Entity entity) {
        for (Entity passenger : List.copyOf(entity.getPassengers())) {
            entity.removePassenger(passenger);
            if (!(passenger instanceof Player)) remove(passenger);
        }
        if (entity instanceof InventoryHolder holder) {
            holder.getInventory().clear();
        }
        entity.remove();
    }
}
