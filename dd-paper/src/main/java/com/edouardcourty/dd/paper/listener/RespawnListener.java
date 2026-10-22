package com.edouardcourty.dd.paper.listener;

import com.edouardcourty.dd.common.model.Dimension;
import com.edouardcourty.dd.common.model.LocationData;
import com.edouardcourty.dd.common.service.DimensionSwitchService;
import com.edouardcourty.dd.paper.store.SpawnPointStore;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Routes deaths to the server holding the player's bed or respawn anchor.
 */
public class RespawnListener implements Listener {
    private final Dimension dimension;
    private final DimensionSwitchService dimensionSwitchService;
    private final JavaPlugin plugin;

    public RespawnListener(Dimension dimension, DimensionSwitchService dimensionSwitchService, JavaPlugin plugin) {
        this.dimension = dimension;
        this.dimensionSwitchService = dimensionSwitchService;
        this.plugin = plugin;
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        if (e.getRespawnReason() != PlayerRespawnEvent.RespawnReason.DEATH) return;

        SpawnPointStore.Point anchor = SpawnPointStore.getAnchor(e.getPlayer());
        if (anchor != null && anchor.dimension() == dimension) {
            if (e.isAnchorSpawn()) return; // Vanilla used and charged the local anchor.
            SpawnPointStore.clearAnchor(e.getPlayer());
            anchor = null;
        }
        if (anchor != null) {
            switchTo(e, anchor);
            return;
        }

        SpawnPointStore.Point point = SpawnPointStore.getFallback(e.getPlayer());
        if (point != null && point.dimension() == dimension) {
            if (e.isBedSpawn() || e.isAnchorSpawn()) return;
            SpawnPointStore.clearFallback(e.getPlayer());
            point = null;
        }
        if (point == null && dimension == Dimension.OVERWORLD) return;

        switchTo(e, point);
    }

    private void switchTo(PlayerRespawnEvent event, SpawnPointStore.Point point) {
        Dimension target = point == null ? Dimension.OVERWORLD : point.dimension();
        LocationData dest = point == null ? LocationData.of(0, 64, 0, 0, 0) : point.location();

        Bukkit.getScheduler().runTaskLater(plugin, () ->
            dimensionSwitchService.sendSwitchRequest(
                event.getPlayer().getUniqueId(),
                target,
                dest,
                false
            ), 1L
        );
    }
}
