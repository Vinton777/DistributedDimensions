package com.edouardcourty.dd.paper.store;

import com.edouardcourty.dd.common.model.Dimension;
import com.edouardcourty.dd.common.model.LocationData;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/** Keeps an anchor as the preferred spawn and a bed as its fallback across Paper servers. */
public final class SpawnPointStore {
    private static final NamespacedKey FALLBACK_KEY = new NamespacedKey("distributed-dimensions", "spawn_point");
    private static final NamespacedKey ANCHOR_KEY = new NamespacedKey("distributed-dimensions", "anchor_point");
    private static final String NONE = "none";
    private static final String VERSION = "v2|";

    private SpawnPointStore() {}

    public record Point(Dimension dimension, LocationData location) {}

    public static void setAnchor(Player player, Location location) {
        migrateLegacy(player);
        write(player, ANCHOR_KEY, encode(location));
    }

    public static void setFallback(Player player, Location location) {
        migrateLegacy(player);
        write(player, FALLBACK_KEY, encode(location));
    }

    public static void clearAnchor(Player player) {
        migrateLegacy(player);
        write(player, ANCHOR_KEY, NONE);
    }

    public static void clearFallback(Player player) {
        migrateLegacy(player);
        write(player, FALLBACK_KEY, NONE);
    }

    public static Point get(Player player) {
        Point anchor = getAnchor(player);
        return anchor != null ? anchor : getFallback(player);
    }

    public static Point getAnchor(Player player) {
        migrateLegacy(player);
        return decode(read(player, ANCHOR_KEY));
    }

    public static Point getFallback(Player player) {
        migrateLegacy(player);
        return decode(read(player, FALLBACK_KEY));
    }

    /** One string carries both locations in the existing player-state message field. */
    public static String raw(Player player) {
        migrateLegacy(player);
        return VERSION + read(player, ANCHOR_KEY) + "|" + read(player, FALLBACK_KEY);
    }

    public static void applyRaw(Player player, String raw) {
        if (raw != null && raw.startsWith(VERSION)) {
            String[] parts = raw.substring(VERSION.length()).split("\\|", 2);
            if (parts.length == 2) {
                write(player, ANCHOR_KEY, valid(parts[0]));
                write(player, FALLBACK_KEY, valid(parts[1]));
            }
            return;
        }
        // v9/v10 sent only one point. Nether spawn points were respawn anchors.
        Point legacy = decode(raw);
        if (legacy != null && legacy.dimension() == Dimension.NETHER) {
            write(player, ANCHOR_KEY, raw);
            String vanilla = encode(player.getRespawnLocation());
            Point local = decode(vanilla);
            write(player, FALLBACK_KEY, local != null && local.dimension() == Dimension.OVERWORLD ? vanilla : NONE);
        } else {
            write(player, ANCHOR_KEY, NONE);
            write(player, FALLBACK_KEY, valid(raw));
        }
    }

    private static void migrateLegacy(Player player) {
        if (player.getPersistentDataContainer().has(ANCHOR_KEY, PersistentDataType.STRING)) return;
        String old = player.getPersistentDataContainer().get(FALLBACK_KEY, PersistentDataType.STRING);
        if (old == null) old = encode(player.getRespawnLocation());
        applyRaw(player, old);
    }

    private static String read(Player player, NamespacedKey key) {
        return player.getPersistentDataContainer().getOrDefault(key, PersistentDataType.STRING, NONE);
    }

    private static void write(Player player, NamespacedKey key, String value) {
        player.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
    }

    private static String valid(String value) {
        return decode(value) == null ? NONE : value;
    }

    private static String encode(Location location) {
        if (location == null || location.getWorld() == null) return NONE;
        for (Dimension dimension : Dimension.values()) {
            if (dimension.toBukkitWorldName().equals(location.getWorld().getName())) {
                return dimension.name() + "," + location.getX() + "," + location.getY() + ","
                    + location.getZ() + "," + location.getYaw() + "," + location.getPitch();
            }
        }
        return NONE;
    }

    private static Point decode(String saved) {
        if (saved == null || NONE.equals(saved)) return null;
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

}
