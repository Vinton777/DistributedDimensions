package com.edouardcourty.dd.paper.store;

import com.edouardcourty.dd.common.model.Dimension;
import com.edouardcourty.dd.common.model.LocationData;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/** Keeps the chosen bed or respawn anchor across separate Paper player files. */
public final class SpawnPointStore {
    private static final NamespacedKey KEY = new NamespacedKey("distributed-dimensions", "spawn_point");
    private static final String NONE = "none";

    private SpawnPointStore() {}

    public record Point(Dimension dimension, LocationData location) {}

    public static void set(Player player, Location location) {
        if (location == null || location.getWorld() == null) {
            applyRaw(player, NONE);
            return;
        }
        Dimension dimension = dimensionOf(location);
        if (dimension == null) return;
        applyRaw(player, dimension.name() + "," + location.getX() + "," + location.getY() + ","
            + location.getZ() + "," + location.getYaw() + "," + location.getPitch());
    }

    public static String raw(Player player) {
        String saved = player.getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
        if (saved != null) return saved;
        Location vanilla = player.getRespawnLocation();
        if (vanilla != null) set(player, vanilla);
        return player.getPersistentDataContainer().getOrDefault(KEY, PersistentDataType.STRING, NONE);
    }

    public static Point get(Player player) {
        String saved = raw(player);
        if (NONE.equals(saved)) return null;
        String[] parts = saved.split(",", 6);
        if (parts.length != 6) return null;
        try {
            return new Point(Dimension.valueOf(parts[0]), LocationData.of(
                Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]),
                Float.parseFloat(parts[4]), Float.parseFloat(parts[5])));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public static void applyRaw(Player player, String raw) {
        player.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, raw == null ? NONE : raw);
    }

    private static Dimension dimensionOf(Location location) {
        for (Dimension dimension : Dimension.values()) {
            if (dimension.toBukkitWorldName().equals(location.getWorld().getName())) return dimension;
        }
        return null;
    }
}
