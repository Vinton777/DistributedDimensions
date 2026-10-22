package com.edouardcourty.dd.paper.messaging;

import com.edouardcourty.dd.common.model.PortalConstants;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * Instancie les entités reçues via un transfert cross-serveur.
 */
public final class EntitySpawner {
    private EntitySpawner() {}

    public static Entity spawn(World world, Location loc, EntityTransferSerializer.EntityData data) {
        EntityType type = EntityType.valueOf(data.entityType());

        // Les blocs en chute deviennent des items droppés à l'arrivée (comportement vanilla)
        if (type == EntityType.FALLING_BLOCK) {
            Map<String, String> extra = EntityExtraDataCodec.parse(data.extraData());
            String blockTypeStr = extra.get("fallingBlockType");
            if (blockTypeStr != null) {
                org.bukkit.block.data.BlockData bd = org.bukkit.Bukkit.createBlockData(blockTypeStr);
                return world.dropItemNaturally(loc, new ItemStack(bd.getMaterial(), 1));
            }
        }

        Entity entity = world.spawnEntity(loc, type);

        entity.setVelocity(new org.bukkit.util.Vector(data.vx(), data.vy(), data.vz()));
        entity.setFireTicks(data.fireTicks());
        if (data.customName() != null) {
            entity.customName(net.kyori.adventure.text.Component.text(data.customName()));
            entity.setCustomNameVisible(true);
        }

        if (entity instanceof LivingEntity living && data.health() > 0) {
            living.setMaxHealth(data.maxHealth());
            living.setHealth(Math.min(data.health(), data.maxHealth()));
        }

        if (entity instanceof Item item && data.itemData() != null) {
            item.setItemStack(ItemStack.deserializeBytes(data.itemData()));
        }

        EntityExtraDataCodec.apply(entity, data.extraData());
        // Chest state and llama strength determine inventory size.
        if (entity instanceof InventoryHolder holder && data.chestContents() != null) {
            holder.getInventory().setContents(data.chestContents());
        }
        entity.setPortalCooldown(PortalConstants.PORTAL_COOLDOWN_TICKS);

        for (EntityTransferSerializer.EntityData passengerData : data.passengers()) {
            // Ignore PLAYER passengers in messages queued by older plugin versions.
            if (EntityType.PLAYER.name().equals(passengerData.entityType())) continue;
            entity.addPassenger(spawn(world, loc, passengerData));
        }

        return entity;
    }
}
