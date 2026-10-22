package com.edouardcourty.dd.paper.portal;

import org.bukkit.Axis;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.Orientable;

import java.util.Set;

/**
 * Trouve un emplacement de téléportation sûr à partir des coordonnées données.
 *
 * Deux modes :
 * - {@link #findAndBuildPortal} : cherche d'abord un portail existant dans un rayon,
 *   sinon trouve un sol valide et construit un portail, sinon construit avec plateforme.
 * - {@link #findOnly} : retourne juste un emplacement sûr, sans construire (respawn).
 *
 * "Sûr" = sol solide + 2 blocs d'air pur au-dessus (pas de lave, feu, eau...).
 * Dans le Nether, la recherche est plafonnée à Y={@value #NETHER_MAX_Y} pour éviter le toit.
 * Si aucun sol n'est trouvé dans la colonne de départ, la recherche s'élargit en spirale
 * jusqu'à {@value #MAX_SEARCH_RADIUS} blocs.
 */
public class SafeLocationFinder {

    private static final Set<Material> SAFE_AIR = Set.of(
        Material.AIR, Material.CAVE_AIR, Material.VOID_AIR
    );

    private static final int MAX_SEARCH_RADIUS = 16;
    private static final int PORTAL_SEARCH_RADIUS = 30;
    private static final int NETHER_MAX_Y = 115;

    private SafeLocationFinder() {}

    /**
     * Trouve un sol sûr ET construit un portail dessus.
     * Vérifie d'abord si un portail existant est proche — si oui, le réutilise.
     * Vérifie que l'espace 4×5 du portail est libre (intérieur 2×3 en air pur, cadre sans bédrock).
     * Si aucun emplacement valide trouvé après spirale : construit un portail avec plateforme.
     */
    public static Location findAndBuildPortal(World world, double x, double y, double z, float yaw, float pitch) {
        int ix = (int) Math.floor(x);
        int iz = (int) Math.floor(z);
        int maxY = getMaxSafeY(world);

        // 1. Chercher un portail existant à proximité
        Location existing = findExistingPortal(world, ix, iz, PORTAL_SEARCH_RADIUS, maxY, yaw, pitch);
        if (existing != null) return existing;

        // 2. Trouver un sol valide et construire un nouveau portail
        for (int[] offset : spiral(MAX_SEARCH_RADIUS)) {
            int cx = ix + offset[0];
            int cz = iz + offset[1];
            Integer safeY = findSafeY(world, cx, cz, maxY);
            if (safeY != null && hasPortalSpace(world, cx, safeY + 1, cz)) {
                return PortalBuilder.buildOnGround(world, cx, safeY, cz, yaw, pitch);
            }
        }

        // 3. Fallback : construire avec plateforme
        return PortalBuilder.buildWithPlatform(world, ix, iz, yaw, pitch);
    }

    /**
     * Trouve l'arrivée pour une entité non-joueur : cherche d'abord un portail existant,
     * sinon un sol sûr. Ne construit rien.
     */
    public static Location findEntityArrival(World world, double x, double y, double z) {
        int ix = (int) Math.floor(x);
        int iz = (int) Math.floor(z);
        int maxY = getMaxSafeY(world);

        Location existing = findExistingPortal(world, ix, iz, PORTAL_SEARCH_RADIUS, maxY, 0f, 0f);
        if (existing != null) return existing;

        return findOnly(world, x, y, z, 0f, 0f);
    }

    /**
     * Trouve un sol sûr sans construire quoi que ce soit (ex : respawn).
     * Fallback : Y+1 aux coordonnées données.
     */
    public static Location findOnly(World world, double x, double y, double z, float yaw, float pitch) {
        int ix = (int) Math.floor(x);
        int iz = (int) Math.floor(z);
        int maxY = getMaxSafeY(world);

        for (int[] offset : spiral(MAX_SEARCH_RADIUS)) {
            int cx = ix + offset[0];
            int cz = iz + offset[1];
            Integer safeY = findSafeY(world, cx, cz, maxY);
            if (safeY != null) {
                return new Location(world, cx + 0.5, safeY + 1, cz + 0.5, yaw, pitch);
            }
        }

        return new Location(world, ix + 0.5, y + 1, iz + 0.5, yaw, pitch);
    }

    /** Finds standing room close to a bed or respawn anchor at its actual height. */
    public static Location findNear(World world, double x, double y, double z, float yaw, float pitch) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y), iz = (int) Math.floor(z);
        for (int[] offset : spiral(4)) {
            for (int dy : new int[]{0, 1, -1, 2, -2}) {
                int feetY = iy + dy;
                if (feetY <= world.getMinHeight() || feetY + 1 >= world.getMaxHeight()) continue;
                int px = ix + offset[0], pz = iz + offset[1];
                if (world.getBlockAt(px, feetY - 1, pz).getType().isSolid()
                    && SAFE_AIR.contains(world.getBlockAt(px, feetY, pz).getType())
                    && SAFE_AIR.contains(world.getBlockAt(px, feetY + 1, pz).getType())) {
                    return new Location(world, px + 0.5, feetY, pz + 0.5, yaw, pitch);
                }
            }
        }
        return findOnly(world, x, y, z, yaw, pitch);
    }

    // Cherche un bloc NETHER_PORTAL existant dans un rayon en spirale, retourne l'intérieur bas du portail
    private static Location findExistingPortal(World world, int ix, int iz, int radius, int maxY, float yaw, float pitch) {
        for (int[] offset : spiral(radius)) {
            int cx = ix + offset[0];
            int cz = iz + offset[1];
            for (int cy = world.getMinHeight() + 1; cy < maxY; cy++) {
                if (world.getBlockAt(cx, cy, cz).getType() == Material.NETHER_PORTAL) {
                    return portalSpawnLocation(world, cx, cy, cz, yaw, pitch);
                }
            }
        }
        return null;
    }

    // À partir d'un bloc NETHER_PORTAL trouvé, retourne la position de spawn à l'EXTÉRIEUR du cadre
    private static Location portalSpawnLocation(World world, int px, int py, int pz, float yaw, float pitch) {
        // Descendre au bas du portail
        while (py > world.getMinHeight() + 1 && world.getBlockAt(px, py - 1, pz).getType() == Material.NETHER_PORTAL) {
            py--;
        }
        // Placer l'entité à l'extérieur du portail selon son axe
        var blockData = world.getBlockAt(px, py, pz).getBlockData();
        if (blockData instanceof Orientable orientable && orientable.getAxis() == Axis.X) {
            // Portail orienté X (faces sur Z) : sortir côté -Z ou +Z
            while (world.getBlockAt(px - 1, py, pz).getType() == Material.NETHER_PORTAL) px--;
            // Trouver la largeur du portail en X pour centrer
            int maxPx = px;
            while (world.getBlockAt(maxPx + 1, py, pz).getType() == Material.NETHER_PORTAL) maxPx++;
            double centerX = (px + maxPx) / 2.0 + 0.5;
            // Sortir côté -Z si possible, sinon +Z
            double exitZ = SAFE_AIR.contains(world.getBlockAt(px, py, pz - 1).getType())
                ? pz - 0.5 : pz + 1.5;
            return new Location(world, centerX, py, exitZ, yaw, pitch);
        } else {
            // Portail orienté Z (faces sur X) : sortir côté -X ou +X
            while (world.getBlockAt(px, py, pz - 1).getType() == Material.NETHER_PORTAL) pz--;
            int maxPz = pz;
            while (world.getBlockAt(px, py, maxPz + 1).getType() == Material.NETHER_PORTAL) maxPz++;
            double centerZ = (pz + maxPz) / 2.0 + 0.5;
            // Sortir côté -X si possible, sinon +X
            double exitX = SAFE_AIR.contains(world.getBlockAt(px - 1, py, pz).getType())
                ? px - 0.5 : px + 1.5;
            return new Location(world, exitX, py, centerZ, yaw, pitch);
        }
    }

    // Retourne le Y du sol sûr en cherchant depuis le haut vers le bas (priorité à la surface)
    private static Integer findSafeY(World world, int ix, int iz, int maxY) {
        for (int iy = maxY - 2; iy >= world.getMinHeight() + 1; iy--) {
            if (world.getBlockAt(ix, iy, iz).getType().isSolid()
                    && SAFE_AIR.contains(world.getBlockAt(ix, iy + 1, iz).getType())
                    && SAFE_AIR.contains(world.getBlockAt(ix, iy + 2, iz).getType())) {
                return iy;
            }
        }
        return null;
    }

    /**
     * Vérifie qu'un portail 4×5 peut être construit proprement à (ix, frameBaseY, iz) :
     * - L'intérieur 2×3 (ix+1..ix+2, frameBaseY+1..frameBaseY+3) doit être en air pur
     * - Les positions du cadre ne doivent pas être de la bédrock (indestructible)
     * - La colonne au-dessus du cadre (frameBaseY+5) doit avoir de la place (pas de bédrock)
     */
    private static boolean hasPortalSpace(World world, int ix, int frameBaseY, int iz) {
        // Intérieur 2×3 doit être de l'air pur
        for (int dx = 1; dx <= 2; dx++) {
            for (int dy = 1; dy <= 3; dy++) {
                if (!SAFE_AIR.contains(world.getBlockAt(ix + dx, frameBaseY + dy, iz).getType())) {
                    return false;
                }
            }
        }
        // Le cadre (4×5) ne doit pas contenir de bédrock
        for (int dx = 0; dx <= 3; dx++) {
            if (world.getBlockAt(ix + dx, frameBaseY,     iz).getType() == Material.BEDROCK) return false;
            if (world.getBlockAt(ix + dx, frameBaseY + 4, iz).getType() == Material.BEDROCK) return false;
        }
        for (int dy = 0; dy <= 4; dy++) {
            if (world.getBlockAt(ix,     frameBaseY + dy, iz).getType() == Material.BEDROCK) return false;
            if (world.getBlockAt(ix + 3, frameBaseY + dy, iz).getType() == Material.BEDROCK) return false;
        }
        return true;
    }

    // Y maximum de spawn selon l'environnement (cap Nether à 115 pour éviter le toit)
    private static int getMaxSafeY(World world) {
        if (world.getEnvironment() == World.Environment.NETHER) return NETHER_MAX_Y;
        return world.getMaxHeight() - 2;
    }

    // Génère les offsets (dx, dz) en spirale carrée : (0,0), puis périmètre à r=1, r=2...
    private static int[][] spiral(int maxRadius) {
        int size = (2 * maxRadius + 1) * (2 * maxRadius + 1);
        int[][] offsets = new int[size][2];
        int idx = 0;
        for (int r = 0; r <= maxRadius; r++) {
            if (r == 0) {
                offsets[idx++] = new int[]{0, 0};
                continue;
            }
            for (int dx = -r; dx <= r; dx++) {
                offsets[idx++] = new int[]{dx, -r};
                offsets[idx++] = new int[]{dx,  r};
            }
            for (int dz = -r + 1; dz <= r - 1; dz++) {
                offsets[idx++] = new int[]{-r, dz};
                offsets[idx++] = new int[]{ r, dz};
            }
        }
        return offsets;
    }
}
