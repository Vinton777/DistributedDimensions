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
        // Respawning may restore the backend's old bed; it must not replace a remote anchor.
        if (event.getCause() == PlayerSetSpawnEvent.Cause.PLAYER_RESPAWN) return;
        if (event.getLocation() == null) {
            SpawnPointStore.clearAnchor(event.getPlayer());
            SpawnPointStore.clearFallback(event.getPlayer());
        } else if (event.getCause() == PlayerSetSpawnEvent.Cause.RESPAWN_ANCHOR) {
            SpawnPointStore.setAnchor(event.getPlayer(), event.getLocation());
        } else if (event.getCause() == PlayerSetSpawnEvent.Cause.BED) {
            SpawnPointStore.setFallback(event.getPlayer(), event.getLocation());
        } else {
            SpawnPointStore.clearAnchor(event.getPlayer());
            SpawnPointStore.setFallback(event.getPlayer(), event.getLocation());
        }
    }
}
