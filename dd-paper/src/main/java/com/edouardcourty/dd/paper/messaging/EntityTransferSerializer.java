package com.edouardcourty.dd.paper.messaging;

import com.edouardcourty.dd.common.model.Dimension;
import com.edouardcourty.dd.paper.util.ItemSerializer;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;

import java.util.ArrayList;
import java.util.List;

/**
 * Sérialise/désérialise une entité non-joueur pour le transfert cross-serveur.
 *
 * Format (récursif pour les passagers) :
 *   targetDimension (UTF), destX/Y/Z (double)
 *   DD_ENTITY_V2 marker (UTF), entityType (UTF), vx/vy/vz (double),
 *   customName (UTF, "null" si absent), fireTicks (int)
 *   hasItemData (boolean) → [itemBytes.length (int) + bytes]
 *   health (float, -1 si non-LivingEntity), maxHealth (float)
 *   hasChestInventory (boolean) → [item array]
 *   extraData (UTF, clés=valeur séparées par ';') — couleur, âge, taille, etc.
 *   equipment (boolean) → [hands, armor, drop chances]
 *   villager (boolean) → [experience, restocks, recipes]
 *   passengerCount (int) → [passengers récursifs sans dimension/dest]
 * Legacy messages without the marker remain readable.
 *
 * Spawn : {@link EntitySpawner}
 * Attributs spécifiques : {@link EntityExtraDataCodec}
 */
public class EntityTransferSerializer {
    private static final String VERSION_2_MARKER = "DD_ENTITY_V2";

    private EntityTransferSerializer() {}

    public record EntityData(
        Dimension targetDimension,
        double destX, double destY, double destZ,
        String entityType,
        double vx, double vy, double vz,
        String customName,
        int fireTicks,
        byte[] itemData,
        float health,
        float maxHealth,
        ItemStack[] chestContents,
        String extraData,
        EquipmentData equipment,
        VillagerData villager,
        List<EntityData> passengers
    ) {}

    public record EquipmentData(ItemStack mainHand, ItemStack offHand, ItemStack[] armor,
                                float[] dropChances) {}

    public record VillagerData(int experience, int restocksToday, List<MerchantRecipe> recipes) {}

    // ── Écriture ─────────────────────────────────────────────────────────────

    public static void writeRoot(ByteArrayDataOutput out, Entity entity, Dimension target, double destX, double destY, double destZ) {
        out.writeUTF(target.name());
        out.writeDouble(destX);
        out.writeDouble(destY);
        out.writeDouble(destZ);
        out.writeUTF(VERSION_2_MARKER);
        writeEntity(out, entity);
    }

    private static void writeEntity(ByteArrayDataOutput out, Entity entity) {
        out.writeUTF(entity.getType().name());

        var vel = entity.getVelocity();
        out.writeDouble(vel.getX());
        out.writeDouble(vel.getY());
        out.writeDouble(vel.getZ());

        var name = entity.customName();
        out.writeUTF(name != null ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(name) : "null");
        out.writeInt(entity.getFireTicks());

        if (entity instanceof Item item) {
            byte[] bytes = item.getItemStack().serializeAsBytes();
            out.writeBoolean(true);
            out.writeInt(bytes.length);
            out.write(bytes);
        } else {
            out.writeBoolean(false);
        }

        if (entity instanceof LivingEntity living) {
            out.writeFloat((float) living.getHealth());
            out.writeFloat((float) living.getMaxHealth());
        } else {
            out.writeFloat(-1f);
            out.writeFloat(-1f);
        }

        if (entity instanceof InventoryHolder holder) {
            ItemStack[] contents = holder.getInventory().getContents();
            out.writeBoolean(true);
            out.writeInt(contents.length);
            for (ItemStack stack : contents) ItemSerializer.write(out, stack);
        } else {
            out.writeBoolean(false);
        }

        out.writeUTF(EntityExtraDataCodec.encode(entity));

        EntityEquipment equipment = entity instanceof LivingEntity living ? living.getEquipment() : null;
        out.writeBoolean(equipment != null);
        if (equipment != null) {
            ItemSerializer.write(out, equipment.getItemInMainHand());
            ItemSerializer.write(out, equipment.getItemInOffHand());
            ItemStack[] armor = equipment.getArmorContents();
            out.writeInt(armor.length);
            for (ItemStack stack : armor) ItemSerializer.write(out, stack);
            out.writeFloat(equipment.getItemInMainHandDropChance());
            out.writeFloat(equipment.getItemInOffHandDropChance());
            out.writeFloat(equipment.getBootsDropChance());
            out.writeFloat(equipment.getLeggingsDropChance());
            out.writeFloat(equipment.getChestplateDropChance());
            out.writeFloat(equipment.getHelmetDropChance());
        }

        out.writeBoolean(entity instanceof Villager);
        if (entity instanceof Villager villager) {
            out.writeInt(villager.getVillagerExperience());
            out.writeInt(villager.getRestocksToday());
            List<MerchantRecipe> recipes = villager.getRecipes();
            out.writeInt(recipes.size());
            for (MerchantRecipe recipe : recipes) writeRecipe(out, recipe);
        }

        // Players are transferred through PlayerStateSerializer, not spawned as entities.
        List<Entity> passengers = entity.getPassengers().stream()
            .filter(passenger -> !(passenger instanceof Player))
            .toList();
        out.writeInt(passengers.size());
        for (Entity passenger : passengers) writeEntity(out, passenger);
    }

    // ── Lecture ───────────────────────────────────────────────────────────────

    public static EntityData readRoot(ByteArrayDataInput in) {
        Dimension target = Dimension.valueOf(in.readUTF());
        double destX = in.readDouble(), destY = in.readDouble(), destZ = in.readDouble();
        String first = in.readUTF();
        boolean version2 = VERSION_2_MARKER.equals(first);
        return readEntity(in, target, destX, destY, destZ, version2, version2 ? null : first);
    }

    private static EntityData readEntity(ByteArrayDataInput in, Dimension target, double destX, double destY, double destZ,
                                         boolean version2, String knownType) {
        String type       = knownType != null ? knownType : in.readUTF();
        double vx = in.readDouble(), vy = in.readDouble(), vz = in.readDouble();
        String rawName    = in.readUTF();
        String customName = "null".equals(rawName) ? null : rawName;
        int fireTicks     = in.readInt();

        byte[] itemData = null;
        if (in.readBoolean()) {
            int len = in.readInt();
            itemData = new byte[len];
            in.readFully(itemData);
        }

        float health    = in.readFloat();
        float maxHealth = in.readFloat();

        ItemStack[] chestContents = null;
        if (in.readBoolean()) {
            chestContents = new ItemStack[in.readInt()];
            for (int i = 0; i < chestContents.length; i++) chestContents[i] = ItemSerializer.read(in);
        }

        String extraData = in.readUTF();

        EquipmentData equipment = null;
        VillagerData villager = null;
        if (version2) {
            if (in.readBoolean()) {
                ItemStack mainHand = ItemSerializer.read(in);
                ItemStack offHand = ItemSerializer.read(in);
                ItemStack[] armor = new ItemStack[in.readInt()];
                for (int i = 0; i < armor.length; i++) armor[i] = ItemSerializer.read(in);
                float[] chances = new float[6];
                for (int i = 0; i < chances.length; i++) chances[i] = in.readFloat();
                equipment = new EquipmentData(mainHand, offHand, armor, chances);
            }
            if (in.readBoolean()) {
                int experience = in.readInt();
                int restocksToday = in.readInt();
                int recipeCount = in.readInt();
                List<MerchantRecipe> recipes = new ArrayList<>(recipeCount);
                for (int i = 0; i < recipeCount; i++) recipes.add(readRecipe(in));
                villager = new VillagerData(experience, restocksToday, recipes);
            }
        }

        int passengerCount = in.readInt();
        List<EntityData> passengers = new ArrayList<>(passengerCount);
        for (int i = 0; i < passengerCount; i++) {
            passengers.add(readEntity(in, target, destX, destY, destZ, version2, null));
        }

        return new EntityData(target, destX, destY, destZ, type, vx, vy, vz, customName,
            fireTicks, itemData, health, maxHealth, chestContents, extraData, equipment, villager, passengers);
    }

    private static void writeRecipe(ByteArrayDataOutput out, MerchantRecipe recipe) {
        ItemSerializer.write(out, recipe.getResult());
        out.writeInt(recipe.getUses());
        out.writeInt(recipe.getMaxUses());
        out.writeBoolean(recipe.hasExperienceReward());
        out.writeInt(recipe.getVillagerExperience());
        out.writeFloat(recipe.getPriceMultiplier());
        out.writeInt(recipe.getDemand());
        out.writeInt(recipe.getSpecialPrice());
        out.writeBoolean(recipe.shouldIgnoreDiscounts());
        List<ItemStack> ingredients = recipe.getIngredients();
        out.writeInt(ingredients.size());
        for (ItemStack ingredient : ingredients) ItemSerializer.write(out, ingredient);
    }

    private static MerchantRecipe readRecipe(ByteArrayDataInput in) {
        ItemStack result = ItemSerializer.read(in);
        int uses = in.readInt();
        int maxUses = in.readInt();
        boolean experienceReward = in.readBoolean();
        int villagerExperience = in.readInt();
        float priceMultiplier = in.readFloat();
        int demand = in.readInt();
        int specialPrice = in.readInt();
        boolean ignoreDiscounts = in.readBoolean();
        int ingredientCount = in.readInt();
        List<ItemStack> ingredients = new ArrayList<>(ingredientCount);
        for (int i = 0; i < ingredientCount; i++) ingredients.add(ItemSerializer.read(in));

        MerchantRecipe recipe = new MerchantRecipe(result, maxUses);
        recipe.setUses(uses);
        recipe.setExperienceReward(experienceReward);
        recipe.setVillagerExperience(villagerExperience);
        recipe.setPriceMultiplier(priceMultiplier);
        recipe.setDemand(demand);
        recipe.setSpecialPrice(specialPrice);
        recipe.setIgnoreDiscounts(ignoreDiscounts);
        recipe.setIngredients(ingredients);
        return recipe;
    }

    // ── Spawn (délégué à EntitySpawner) ──────────────────────────────────────

    public static Entity spawn(World world, Location loc, EntityData data) {
        return EntitySpawner.spawn(world, loc, data);
    }
}
