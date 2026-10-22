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
        SpawnPointStore.Point point = SpawnPointStore.get(e.getPlayer());
        if (point == null || point.dimension() == dimension) {
            if (e.isAnchorSpawn() || e.isBedSpawn()) return;
        }
        if (point != null && point.dimension() == dimension) {
            // Vanilla rejected this local bed/anchor (broken or out of charge).
            SpawnPointStore.set(e.getPlayer(), null);
            point = null;
        }
        if (point == null && dimension == Dimension.OVERWORLD) return;

        Dimension target = point == null ? Dimension.OVERWORLD : point.dimension();
        LocationData dest = point == null ? LocationData.of(0, 64, 0, 0, 0) : point.location();

        Bukkit.getScheduler().runTaskLater(plugin, () ->
            dimensionSwitchService.sendSwitchRequest(
                e.getPlayer().getUniqueId(),
                target,
                dest,
                false
            ), 1L
        );
    }
}
