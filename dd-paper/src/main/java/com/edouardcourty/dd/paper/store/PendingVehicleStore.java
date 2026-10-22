package com.edouardcourty.dd.paper.store;

import com.edouardcourty.dd.paper.messaging.EntityTransferSerializer;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Restores a removed vehicle if Velocity rejects a dimension switch. */
public final class PendingVehicleStore implements Listener {
    private record Snapshot(Location location, EntityTransferSerializer.EntityData vehicle) {}

    private final Map<UUID, Snapshot> pending = new HashMap<>();
    private final Set<UUID> switching = ConcurrentHashMap.newKeySet();

    public boolean begin(UUID playerUuid) {
        return switching.add(playerUuid);
    }

    public void remember(UUID playerUuid, Location location, EntityTransferSerializer.EntityData vehicle) {
        pending.put(playerUuid, new Snapshot(location.clone(), vehicle));
    }

    public void restore(Player player) {
        switching.remove(player.getUniqueId());
        Snapshot snapshot = pending.remove(player.getUniqueId());
        if (snapshot == null) return;
        var vehicle = EntityTransferSerializer.spawn(snapshot.location().getWorld(), snapshot.location(), snapshot.vehicle());
        vehicle.addPassenger(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        switching.remove(event.getPlayer().getUniqueId());
        pending.remove(event.getPlayer().getUniqueId());
    }
}
