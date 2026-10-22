package com.edouardcourty.dd.paper.listener;

import com.edouardcourty.dd.common.messaging.Channels;
import com.edouardcourty.dd.common.model.Dimension;
import com.edouardcourty.dd.common.model.PortalConstants;
import com.edouardcourty.dd.paper.messaging.EntityTransferSerializer;
import com.edouardcourty.dd.paper.service.NetherCoordinateScaler;
import com.google.common.io.ByteStreams;
import io.papermc.paper.event.entity.EntityInsideBlockEvent;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Transfère les entités non-joueur (mobs, items, minecarts...) vers le serveur
 * de la dimension cible.
 *
 * - Portail Nether : via EntityPortalEvent (OVERWORLD ↔ NETHER, échelle 1:8)
 * - Portail End    : via EntityInsideBlockEvent sur END_PORTAL (car vanilla ne
 *   déclenche pas EntityPortalEvent pour les non-joueurs dans les portails End)
 */
public class EntityPortalListener implements Listener {
    private final Dimension dimension;
    private final JavaPlugin plugin;

    /** UUIDs d'entités récemment spawned via transfer — ignorés pendant 15s pour éviter re-trigger. */
    private static final Map<UUID, Long> RECENTLY_TRANSFERRED = new ConcurrentHashMap<>();
    private static final long TRANSFER_GRACE_MS = PortalConstants.PORTAL_COOLDOWN_TICKS * 50L; // ticks → ms

    public static void markTransferred(UUID uuid) {
        RECENTLY_TRANSFERRED.put(uuid, System.currentTimeMillis());
    }

    public EntityPortalListener(Dimension dimension, JavaPlugin plugin) {
        this.dimension = dimension;
        this.plugin = plugin;
    }

    // ── Portail Nether (EntityPortalEvent) ────────────────────────────────────

    @EventHandler
    public void onEntityPortal(EntityPortalEvent e) {
        Entity entity = e.getEntity();
        if (entity instanceof Player) return;
        if (dimension == Dimension.END) return; // pas de portail nether dans l'End vanilla

        if (isRecentlyTransferred(entity)) { e.setCancelled(true); return; }

        e.setCancelled(true);

        NetherCoordinateScaler.ScaledCoords dest = NetherCoordinateScaler.scale(
            dimension, e.getFrom().getX(), e.getFrom().getY(), e.getFrom().getZ()
        );

        sendTransfer(entity, dest.target(), dest.x(), dest.y(), dest.z());
    }

    // ── Portail End (EntityInsideBlockEvent) ──────────────────────────────────
    // Paper ne déclenche pas EntityPortalEvent pour les non-joueurs dans les portails End.
    // On utilise EntityInsideBlockEvent pour détecter le contact avec END_PORTAL / END_GATEWAY.

    @EventHandler
    public void onEntityInsideBlock(EntityInsideBlockEvent e) {
        Material type = e.getBlock().getType();
        if (type != Material.END_PORTAL && type != Material.END_GATEWAY) return;
        Entity entity = e.getEntity();
        if (entity instanceof Player) return; // géré par EndPortalListener

        if (isRecentlyTransferred(entity)) { e.setCancelled(true); return; }

        Dimension target;
        double destX, destY, destZ;

        if (type == Material.END_PORTAL) {
            if (dimension == Dimension.OVERWORLD) {
                target = Dimension.END;
                destX = PortalConstants.END_PLATFORM_X;
                destY = PortalConstants.END_PLATFORM_Y;
                destZ = PortalConstants.END_PLATFORM_Z;
            } else if (dimension == Dimension.END) {
                // End → Overworld via portail de sortie (à l'origine 0,0,0 de l'End)
                target = Dimension.OVERWORLD;
                destX = 0; destY = 64; destZ = 0;
            } else return;
        } else {
            // END_GATEWAY : non géré pour l'instant
            return;
        }

        // Prevent the vanilla portal action from running alongside our cross-server transfer.
        e.setCancelled(true);
        sendTransfer(entity, target, destX, destY, destZ);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isRecentlyTransferred(Entity entity) {
        Long t = RECENTLY_TRANSFERRED.get(entity.getUniqueId());
        if (t == null) return false;
        if (System.currentTimeMillis() - t < TRANSFER_GRACE_MS) return true;
        RECENTLY_TRANSFERRED.remove(entity.getUniqueId());
        return false;
    }

    private void sendTransfer(Entity entity, Dimension target, double destX, double destY, double destZ) {
        Collection<? extends Player> online = plugin.getServer().getOnlinePlayers();
        if (online.isEmpty()) {
            plugin.getLogger().warning("[EntityPortal] No player online; transfer deferred.");
            return;
        }
        Player carrier = online.iterator().next();

        plugin.getLogger().info("[EntityPortal] Transferring " + entity.getType() + " → " + target);

        var out = ByteStreams.newDataOutput();
        EntityTransferSerializer.writeRoot(out, entity, target, destX, destY, destZ);
        carrier.sendPluginMessage(plugin, Channels.ENTITY_TRANSFER.toString(), out.toByteArray());

        // The payload owns these items now. Empty every inventory in the passenger tree
        // before removing the source entities, so removal cannot drop a second copy.
        clearInventories(entity);
        removeEntityTree(entity);
    }

    private void clearInventories(Entity entity) {
        entity.getPassengers().forEach(this::clearInventories);
        if (entity instanceof InventoryHolder holder) {
            holder.getInventory().clear();
        }
    }

    private void removeEntityTree(Entity entity) {
        for (Entity passenger : entity.getPassengers()) {
            entity.removePassenger(passenger);
            removeEntityTree(passenger);
        }
        entity.remove();
    }
}
