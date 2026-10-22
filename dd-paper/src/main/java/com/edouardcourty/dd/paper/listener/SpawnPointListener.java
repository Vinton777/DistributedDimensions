package com.edouardcourty.dd.paper.listener;

import com.destroystokyo.paper.event.player.PlayerSetSpawnEvent;
import com.edouardcourty.dd.paper.store.SpawnPointStore;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/** Records beds and respawn anchors when the player's spawn changes. */
public final class SpawnPointListener implements Listener {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSetSpawn(PlayerSetSpawnEvent event) {
        SpawnPointStore.set(event.getPlayer(), event.getLocation());
    }
}
