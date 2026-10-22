package com.edouardcourty.dd.paper.service;

import com.edouardcourty.dd.common.messaging.Channels;
import com.edouardcourty.dd.common.model.Dimension;
import com.edouardcourty.dd.common.model.LocationData;
import com.edouardcourty.dd.common.service.DimensionSwitchService;
import com.edouardcourty.dd.paper.messaging.PlayerStateSerializer;
import com.edouardcourty.dd.paper.messaging.EntityTransferCleanup;
import com.edouardcourty.dd.paper.messaging.EntityTransferSerializer;
import com.edouardcourty.dd.paper.messaging.AdvancementTransferSerializer;
import com.edouardcourty.dd.paper.messaging.EnderChestTransferSerializer;
import com.edouardcourty.dd.paper.store.PendingVehicleStore;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public class VelocityPluginMessageSwitchService implements DimensionSwitchService {
    private final JavaPlugin plugin;
    private final PendingVehicleStore pendingVehicleStore;

    public VelocityPluginMessageSwitchService(JavaPlugin plugin, PendingVehicleStore pendingVehicleStore) {
        this.plugin = plugin;
        this.pendingVehicleStore = pendingVehicleStore;
    }

    @Override
    public void sendSwitchRequest(UUID playerUuid, Dimension target, LocationData destination, boolean buildPortal) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null) return;
        if (!pendingVehicleStore.begin(playerUuid)) return;
        org.bukkit.entity.Entity vehicle = player.getVehicle();

        try {
            var out = ByteStreams.newDataOutput();
            PlayerStateSerializer.write(out, playerUuid, target, destination, player, buildPortal);
            var advancementBatches = AdvancementTransferSerializer.encode(player);
            var enderChestBatches = EnderChestTransferSerializer.encode(player);
            if (vehicle != null) {
                var vehicleOut = ByteStreams.newDataOutput();
                EntityTransferSerializer.writeRoot(vehicleOut, vehicle, target, destination.x, destination.y, destination.z);
                pendingVehicleStore.remember(playerUuid, vehicle.getLocation(),
                    EntityTransferSerializer.readRoot(ByteStreams.newDataInput(vehicleOut.toByteArray())));
                EntityTransferCleanup.remove(vehicle);
            }
            for (byte[] batch : advancementBatches) {
                player.sendPluginMessage(plugin, Channels.ADVANCEMENT_TRANSFER.toString(), batch);
            }
            for (byte[] batch : enderChestBatches) {
                player.sendPluginMessage(plugin, Channels.ENDER_CHEST_TRANSFER.toString(), batch);
            }
            player.sendPluginMessage(plugin, Channels.DIM_SWITCH.toString(), out.toByteArray());
        } catch (RuntimeException ex) {
            pendingVehicleStore.restore(player);
            throw ex;
        }
    }

    /** Sends a fresh state-only snapshot before a manual Velocity /server switch. */
    public void sendSnapshot(Player player, Dimension target, Channels responseChannel) {
        var out = ByteStreams.newDataOutput();
        org.bukkit.Location from = player.getLocation();
        PlayerStateSerializer.write(out, player.getUniqueId(), target,
            LocationData.of(from.getX(), from.getY(), from.getZ(), from.getYaw(), from.getPitch()),
            player, false, true);
        var advancements = AdvancementTransferSerializer.encode(player);
        var enderChest = EnderChestTransferSerializer.encode(player);
        for (byte[] batch : advancements) {
            player.sendPluginMessage(plugin, Channels.ADVANCEMENT_TRANSFER.toString(), batch);
        }
        for (byte[] batch : enderChest) {
            player.sendPluginMessage(plugin, Channels.ENDER_CHEST_TRANSFER.toString(), batch);
        }
        player.sendPluginMessage(plugin, responseChannel.toString(), out.toByteArray());
    }
}

