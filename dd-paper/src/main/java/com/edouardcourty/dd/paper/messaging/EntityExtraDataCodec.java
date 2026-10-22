package com.edouardcourty.dd.paper.messaging;

import org.bukkit.DyeColor;
import org.bukkit.Bukkit;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sérialise et désérialise les attributs spécifiques d'une entité sous forme de chaîne "key=val;...".
 */
public final class EntityExtraDataCodec {
    private EntityExtraDataCodec() {}

    public static String encode(Entity entity) {
        Map<String, String> map = new LinkedHashMap<>();

        if (entity instanceof Ageable ageable) {
            map.put("adult", String.valueOf(ageable.isAdult()));
            if (!ageable.isAdult()) map.put("age", String.valueOf(ageable.getAge()));
        }
        if (entity instanceof AbstractCubeMob cube) {
            map.put("size", String.valueOf(cube.getSize()));
            map.put("canWander", String.valueOf(cube.canWander()));
        }
        if (entity instanceof SulfurCube cube) {
            map.put("fuseTicks", String.valueOf(cube.getFuseTicks()));
        }
        if (entity instanceof Tameable tameable) {
            map.put("tamed", String.valueOf(tameable.isTamed()));
            UUID owner = tameable.getOwnerUniqueId();
            if (owner != null) map.put("ownerUuid", owner.toString());
        }
        if (entity instanceof Sittable sittable) {
            map.put("sitting", String.valueOf(sittable.isSitting()));
        }
        if (entity instanceof Sheep sheep) {
            map.put("sheared", String.valueOf(sheep.isSheared()));
            if (sheep.getColor() != null) map.put("woolColor", sheep.getColor().name());
        }
        if (entity instanceof Pig pig) {
            map.put("saddle", String.valueOf(pig.hasSaddle()));
        }
        if (entity instanceof AbstractHorse horse) {
            map.put("domestication", String.valueOf(horse.getDomestication()));
            map.put("jumpStrength", String.valueOf(horse.getJumpStrength()));
        }
        if (entity instanceof ChestedHorse horse) {
            map.put("carryingChest", String.valueOf(horse.isCarryingChest()));
        }
        if (entity instanceof Horse horse) {
            map.put("horseColor", horse.getColor().name());
            map.put("horseStyle", horse.getStyle().name());
        }
        if (entity instanceof Llama llama) {
            map.put("llamaColor", llama.getColor().name());
            map.put("strength", String.valueOf(llama.getStrength()));
        }
        if (entity instanceof Cat cat) {
            map.put("catType", cat.getCatType().name());
            map.put("catColor", cat.getCollarColor().name());
        }
        if (entity instanceof Wolf wolf) {
            map.put("collarColor", wolf.getCollarColor().name());
            map.put("angry", String.valueOf(wolf.isAngry()));
            map.put("wolfVariant", wolf.getVariant().getKey().toString());
        }
        if (entity instanceof Fox fox) {
            map.put("foxType", fox.getFoxType().name());
        }
        if (entity instanceof Rabbit rabbit) {
            map.put("rabbitType", rabbit.getRabbitType().name());
        }
        if (entity instanceof Frog frog) {
            map.put("frogVariant", frog.getVariant().getKey().toString());
        }
        if (entity instanceof TropicalFish fish) {
            map.put("fishPattern", fish.getPattern().name());
            map.put("fishBodyColor", fish.getBodyColor().name());
            map.put("fishPatternColor", fish.getPatternColor().name());
        }
        if (entity instanceof Creeper creeper) {
            map.put("powered", String.valueOf(creeper.isPowered()));
        }
        if (entity instanceof Zombie zombie) {
            map.put("baby", String.valueOf(zombie.isBaby()));
        }
        if (entity instanceof Villager villager) {
            map.put("villagerType", villager.getVillagerType().name());
            map.put("villagerProfession", villager.getProfession().name());
            map.put("villagerLevel", String.valueOf(villager.getVillagerLevel()));
        }
        if (entity instanceof ZombieVillager zv) {
            map.put("zvProfession", zv.getVillagerProfession().name());
        }
        if (entity instanceof MushroomCow mooshroom) {
            map.put("mushroomVariant", mooshroom.getVariant().name());
        }
        if (entity instanceof FallingBlock fb) {
            map.put("fallingBlockType", fb.getBlockData().getAsString());
        }
        if (entity instanceof LivingEntity living) {
            saveAttribute(map, living, Attribute.MAX_HEALTH, "baseMaxHealth");
            saveAttribute(map, living, Attribute.MOVEMENT_SPEED, "baseMovementSpeed");
            saveAttribute(map, living, Attribute.JUMP_STRENGTH, "baseJumpStrength");
        }

        if (map.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        map.forEach((k, v) -> sb.append(k).append('=').append(v).append(';'));
        return sb.toString();
    }

    public static Map<String, String> parse(String extraData) {
        Map<String, String> map = new HashMap<>();
        if (extraData == null || extraData.isEmpty()) return map;
        for (String pair : extraData.split(";")) {
            int eq = pair.indexOf('=');
            if (eq > 0) map.put(pair.substring(0, eq), pair.substring(eq + 1));
        }
        return map;
    }

    @SuppressWarnings("deprecation")
    public static void apply(Entity entity, String extraData) {
        Map<String, String> map = parse(extraData);
        if (map.isEmpty()) return;

        if (entity instanceof Ageable ageable) {
            if ("true".equals(map.get("adult"))) ageable.setAdult();
            else if (map.containsKey("age")) ageable.setAge(Integer.parseInt(map.get("age")));
        }
        if (entity instanceof AbstractCubeMob cube) {
            if (map.containsKey("size")) cube.setSize(Integer.parseInt(map.get("size")));
            if (map.containsKey("canWander")) cube.setWander(Boolean.parseBoolean(map.get("canWander")));
        }
        if (entity instanceof SulfurCube cube && map.containsKey("fuseTicks")) {
            int fuseTicks = Integer.parseInt(map.get("fuseTicks"));
            if (fuseTicks >= 0) cube.setFuseTicks(fuseTicks);
        }
        if (entity instanceof Tameable tameable && map.containsKey("tamed")) {
            tameable.setTamed("true".equals(map.get("tamed")));
            if (map.containsKey("ownerUuid")) {
                tameable.setOwner(Bukkit.getOfflinePlayer(UUID.fromString(map.get("ownerUuid"))));
            }
        }
        if (entity instanceof Sittable sittable && map.containsKey("sitting")) {
            sittable.setSitting("true".equals(map.get("sitting")));
        }
        if (entity instanceof Sheep sheep && map.containsKey("sheared")) {
            sheep.setSheared("true".equals(map.get("sheared")));
            if (map.containsKey("woolColor")) sheep.setColor(DyeColor.valueOf(map.get("woolColor")));
        }
        if (entity instanceof Pig pig && map.containsKey("saddle")) {
            pig.setSaddle("true".equals(map.get("saddle")));
        }
        if (entity instanceof AbstractHorse horse) {
            if (map.containsKey("domestication")) horse.setDomestication(Integer.parseInt(map.get("domestication")));
            if (map.containsKey("jumpStrength")) horse.setJumpStrength(Double.parseDouble(map.get("jumpStrength")));
        }
        if (entity instanceof ChestedHorse horse && map.containsKey("carryingChest")) {
            horse.setCarryingChest(Boolean.parseBoolean(map.get("carryingChest")));
        }
        if (entity instanceof Horse horse) {
            if (map.containsKey("horseColor")) horse.setColor(Horse.Color.valueOf(map.get("horseColor")));
            if (map.containsKey("horseStyle")) horse.setStyle(Horse.Style.valueOf(map.get("horseStyle")));
        }
        if (entity instanceof Llama llama) {
            if (map.containsKey("llamaColor")) llama.setColor(Llama.Color.valueOf(map.get("llamaColor")));
            if (map.containsKey("strength")) llama.setStrength(Integer.parseInt(map.get("strength")));
        }
        if (entity instanceof Cat cat) {
            if (map.containsKey("catType")) cat.setCatType(Cat.Type.valueOf(map.get("catType")));
            if (map.containsKey("catColor")) cat.setCollarColor(DyeColor.valueOf(map.get("catColor")));
        }
        if (entity instanceof Wolf wolf) {
            if (map.containsKey("collarColor")) wolf.setCollarColor(DyeColor.valueOf(map.get("collarColor")));
            if (map.containsKey("angry")) wolf.setAngry("true".equals(map.get("angry")));
            if (map.containsKey("wolfVariant")) Registry.WOLF_VARIANT.stream()
                .filter(v -> v.getKey().toString().equals(map.get("wolfVariant")))
                .findFirst().ifPresent(wolf::setVariant);
        }
        if (entity instanceof Fox fox && map.containsKey("foxType")) {
            fox.setFoxType(Fox.Type.valueOf(map.get("foxType")));
        }
        if (entity instanceof Rabbit rabbit && map.containsKey("rabbitType")) {
            rabbit.setRabbitType(Rabbit.Type.valueOf(map.get("rabbitType")));
        }
        if (entity instanceof Frog frog && map.containsKey("frogVariant")) {
            org.bukkit.Registry.FROG_VARIANT.stream()
                .filter(v -> v.getKey().toString().equals(map.get("frogVariant")))
                .findFirst()
                .ifPresent(frog::setVariant);
        }
        if (entity instanceof TropicalFish fish) {
            if (map.containsKey("fishPattern")) fish.setPattern(TropicalFish.Pattern.valueOf(map.get("fishPattern")));
            if (map.containsKey("fishBodyColor")) fish.setBodyColor(DyeColor.valueOf(map.get("fishBodyColor")));
            if (map.containsKey("fishPatternColor")) fish.setPatternColor(DyeColor.valueOf(map.get("fishPatternColor")));
        }
        if (entity instanceof Creeper creeper && map.containsKey("powered")) {
            creeper.setPowered("true".equals(map.get("powered")));
        }
        if (entity instanceof Zombie zombie && map.containsKey("baby")) {
            zombie.setBaby("true".equals(map.get("baby")));
        }
        if (entity instanceof Villager villager) {
            if (map.containsKey("villagerType")) villager.setVillagerType(Villager.Type.valueOf(map.get("villagerType")));
            if (map.containsKey("villagerProfession")) villager.setProfession(Villager.Profession.valueOf(map.get("villagerProfession")));
            if (map.containsKey("villagerLevel")) villager.setVillagerLevel(Integer.parseInt(map.get("villagerLevel")));
        }
        if (entity instanceof ZombieVillager zv && map.containsKey("zvProfession")) {
            zv.setVillagerProfession(Villager.Profession.valueOf(map.get("zvProfession")));
        }
        if (entity instanceof MushroomCow mooshroom && map.containsKey("mushroomVariant")) {
            mooshroom.setVariant(MushroomCow.Variant.valueOf(map.get("mushroomVariant")));
        }
        if (entity instanceof LivingEntity living) {
            restoreAttribute(map, living, Attribute.MAX_HEALTH, "baseMaxHealth");
            restoreAttribute(map, living, Attribute.MOVEMENT_SPEED, "baseMovementSpeed");
            restoreAttribute(map, living, Attribute.JUMP_STRENGTH, "baseJumpStrength");
        }
    }

    private static void saveAttribute(Map<String, String> map, LivingEntity living, Attribute attribute, String key) {
        AttributeInstance instance = living.getAttribute(attribute);
        if (instance != null) map.put(key, Double.toString(instance.getBaseValue()));
    }

    private static void restoreAttribute(Map<String, String> map, LivingEntity living, Attribute attribute, String key) {
        if (!map.containsKey(key)) return;
        AttributeInstance instance = living.getAttribute(attribute);
        if (instance != null) instance.setBaseValue(Double.parseDouble(map.get(key)));
    }
}
