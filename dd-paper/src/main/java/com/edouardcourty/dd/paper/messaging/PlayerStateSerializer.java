package com.edouardcourty.dd.paper.messaging;

import com.edouardcourty.dd.common.model.Dimension;
import com.edouardcourty.dd.common.model.LocationData;
import com.edouardcourty.dd.paper.util.ItemSerializer;
import com.edouardcourty.dd.paper.store.SpawnPointStore;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.EOFException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Sérialise et désérialise l'état complet d'un joueur pour les switches cross-serveur.
 *
 * Format du message (dans l'ordre) :
 *   playerUUID (UTF), targetDimension (UTF),
 *   x/y/z (double), yaw/pitch (float),
 *   xpLevel (int), xpProgress (float),
 *   foodLevel (int), saturation (float), exhaustion (float),
 *   gameMode (UTF),
 *   inventory contents, armor contents, offhand item,
 *   buildPortal, potion effects, vehicle, selected hotbar slot,
 *   hasVitals (boolean) → [health, base max health, absorption (double)],
 *   stateOnly (boolean), cross-server spawn point (UTF)
 *   The final fields are optional when reading older messages.
 *
 * Format d'un item : int length (0 = vide/AIR), puis bytes si non vide.
 */
public class PlayerStateSerializer {

    private PlayerStateSerializer() {}

    public record PlayerState(
        UUID playerUuid,
        Dimension targetDimension,
        LocationData location,
        int xpLevel,
        float xpProgress,
        int foodLevel,
        float saturation,
        float exhaustion,
        GameMode gameMode,
        ItemStack[] inventoryContents,
        ItemStack[] armorContents,
        ItemStack offhand,
        int selectedHotbarSlot,
        boolean hasVitals,
        double health,
        double maxHealthBase,
        double absorption,
        boolean stateOnly,
        String spawnPoint,
        boolean buildPortal,
        EntityTransferSerializer.EntityData vehicle,  // null si le joueur n'est pas dans un véhicule
        List<PotionEffect> potionEffects
    ) {}

    public static void write(ByteArrayDataOutput out, UUID playerUuid, Dimension target, LocationData dest, Player player, boolean buildPortal) {
        write(out, playerUuid, target, dest, player, buildPortal, false);
    }

    public static void write(ByteArrayDataOutput out, UUID playerUuid, Dimension target, LocationData dest,
                             Player player, boolean buildPortal, boolean stateOnly) {
        out.writeUTF(playerUuid.toString());
        out.writeUTF(target.name());
        out.writeDouble(dest.x);
        out.writeDouble(dest.y);
        out.writeDouble(dest.z);
        out.writeFloat(dest.yaw);
        out.writeFloat(dest.pitch);
        out.writeInt(player.getLevel());
        out.writeFloat(player.getExp());
        out.writeInt(player.getFoodLevel());
        out.writeFloat(player.getSaturation());
        out.writeFloat(player.getExhaustion());
        out.writeUTF(player.getGameMode().name());

        PlayerInventory inv = player.getInventory();
        writeItemArray(out, inv.getContents());
        writeItemArray(out, inv.getArmorContents());
        writeItem(out, inv.getItemInOffHand());
        out.writeBoolean(buildPortal);

        // Effets de potion
        Collection<PotionEffect> effects = player.getActivePotionEffects();
        out.writeInt(effects.size());
        for (PotionEffect effect : effects) {
            out.writeUTF(effect.getType().getKey().toString());
            out.writeInt(effect.getDuration());
            out.writeInt(effect.getAmplifier());
            out.writeBoolean(effect.isAmbient());
            out.writeBoolean(effect.hasParticles());
            out.writeBoolean(effect.hasIcon());
        }

        // Véhicule optionnel
        org.bukkit.entity.Entity vehicle = player.getVehicle();
        if (vehicle != null && !stateOnly) {
            out.writeBoolean(true);
            EntityTransferSerializer.writeRoot(out, vehicle, target, dest.x, dest.y, dest.z);
        } else {
            out.writeBoolean(false);
        }

        // Appended so older persisted player states can still be restored.
        out.writeInt(inv.getHeldItemSlot());
        boolean hasVitals = !player.isDead() && player.getHealth() > 0;
        out.writeBoolean(hasVitals);
        if (hasVitals) {
            out.writeDouble(player.getHealth());
            var maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
            out.writeDouble(maxHealth == null ? player.getMaxHealth() : maxHealth.getBaseValue());
            out.writeDouble(player.getAbsorptionAmount());
        }
        out.writeBoolean(stateOnly);
        out.writeUTF(SpawnPointStore.raw(player));
    }

    public static PlayerState read(ByteArrayDataInput in) {
        UUID playerUuid       = UUID.fromString(in.readUTF());
        Dimension dimension   = Dimension.valueOf(in.readUTF());
        double x = in.readDouble(), y = in.readDouble(), z = in.readDouble();
        float yaw = in.readFloat(), pitch = in.readFloat();
        int xpLevel           = in.readInt();
        float xpProgress      = in.readFloat();
        int foodLevel         = in.readInt();
        float saturation      = in.readFloat();
        float exhaustion      = in.readFloat();
        GameMode gameMode     = GameMode.valueOf(in.readUTF());
        ItemStack[] contents  = readItemArray(in);
        ItemStack[] armor     = readItemArray(in);
        ItemStack offhand     = readItem(in);
        boolean buildPortal   = in.readBoolean();

        // Effets de potion
        int effectCount = in.readInt();
        List<PotionEffect> potionEffects = new ArrayList<>(effectCount);
        for (int i = 0; i < effectCount; i++) {
            PotionEffectType type = Registry.EFFECT.get(NamespacedKey.fromString(in.readUTF()));
            int duration  = in.readInt();
            int amplifier = in.readInt();
            boolean ambient   = in.readBoolean();
            boolean particles = in.readBoolean();
            boolean icon      = in.readBoolean();
            if (type != null) potionEffects.add(new PotionEffect(type, duration, amplifier, ambient, particles, icon));
        }

        // Véhicule optionnel
        EntityTransferSerializer.EntityData vehicle = in.readBoolean()
            ? EntityTransferSerializer.readRoot(in)
            : null;

        int selectedHotbarSlot = 0;
        boolean hasVitals = false;
        double health = 0, maxHealthBase = 0, absorption = 0;
        try {
            selectedHotbarSlot = in.readInt();
        } catch (IllegalStateException ex) {
            if (!(ex.getCause() instanceof EOFException)) throw ex;
        }
        try {
            hasVitals = in.readBoolean();
        } catch (IllegalStateException ex) {
            if (!(ex.getCause() instanceof EOFException)) throw ex;
        }
        if (hasVitals) {
            health = in.readDouble();
            maxHealthBase = in.readDouble();
            absorption = in.readDouble();
        }
        boolean stateOnly = false;
        String spawnPoint = null;
        boolean hasExtendedState = false;
        try {
            stateOnly = in.readBoolean();
            hasExtendedState = true;
        } catch (IllegalStateException ex) {
            if (!(ex.getCause() instanceof EOFException)) throw ex;
        }
        if (hasExtendedState) spawnPoint = in.readUTF();

        return new PlayerState(
            playerUuid, dimension,
            LocationData.of(x, y, z, yaw, pitch),
            xpLevel, xpProgress,
            foodLevel, saturation, exhaustion,
            gameMode, contents, armor, offhand, selectedHotbarSlot,
            hasVitals, health, maxHealthBase, absorption, stateOnly, spawnPoint,
            buildPortal, vehicle, potionEffects
        );
    }


    private static void writeItemArray(ByteArrayDataOutput out, ItemStack[] items) {
        out.writeInt(items.length);
        for (ItemStack item : items) ItemSerializer.write(out, item);
    }

    private static ItemStack[] readItemArray(ByteArrayDataInput in) {
        ItemStack[] items = new ItemStack[in.readInt()];
        for (int i = 0; i < items.length; i++) items[i] = ItemSerializer.read(in);
        return items;
    }

    private static void writeItem(ByteArrayDataOutput out, ItemStack item) {
        ItemSerializer.write(out, item);
    }

    private static ItemStack readItem(ByteArrayDataInput in) {
        return ItemSerializer.read(in);
    }
}
