package com.edouardcourty.dd.paper;

import com.edouardcourty.dd.common.messaging.Channels;
import com.edouardcourty.dd.common.model.Dimension;
import com.edouardcourty.dd.common.service.DimensionSwitchService;
import com.edouardcourty.dd.paper.listener.DimensionGuardListener;
import com.edouardcourty.dd.paper.listener.AdvancementTransferListener;
import com.edouardcourty.dd.paper.listener.DimensionSwitchListener;
import com.edouardcourty.dd.paper.listener.EndPortalListener;
import com.edouardcourty.dd.paper.listener.EnderChestTransferListener;
import com.edouardcourty.dd.paper.listener.EntityPortalListener;
import com.edouardcourty.dd.paper.listener.EntitySpawnListener;
import com.edouardcourty.dd.paper.listener.NetherPortalListener;
import com.edouardcourty.dd.paper.listener.PlayerBroadcastListener;
import com.edouardcourty.dd.paper.listener.RespawnListener;
import com.edouardcourty.dd.paper.listener.SwitchFailedListener;
import com.edouardcourty.dd.paper.listener.SpawnPointListener;
import com.edouardcourty.dd.paper.listener.PlayerSnapshotListener;
import com.edouardcourty.dd.paper.service.VelocityPluginMessageSwitchService;
import com.edouardcourty.dd.paper.store.PrePortalPositionStore;
import com.edouardcourty.dd.paper.store.PendingVehicleStore;
import org.bukkit.plugin.java.JavaPlugin;

public class DistributedDimensions extends JavaPlugin {
    private Dimension dimension;
    private DimensionSwitchService dimensionSwitchService;
    private VelocityPluginMessageSwitchService snapshotService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        dimension = Dimension.valueOf(getConfig().getString("world", Dimension.OVERWORLD.name()));
        PrePortalPositionStore positionStore = new PrePortalPositionStore(this);
        PendingVehicleStore pendingVehicleStore = new PendingVehicleStore();
        VelocityPluginMessageSwitchService switchService = new VelocityPluginMessageSwitchService(this, pendingVehicleStore);
        dimensionSwitchService = switchService;
        snapshotService = switchService;

        DimensionSwitchListener dimSwitchListener = new DimensionSwitchListener(this, positionStore, dimensionSwitchService);
        getServer().getMessenger().registerOutgoingPluginChannel(this, Channels.DIM_SWITCH.toString());
        getServer().getMessenger().registerIncomingPluginChannel(this, Channels.DIM_SWITCH.toString(), dimSwitchListener);
        getServer().getMessenger().registerIncomingPluginChannel(this, Channels.STATE_REQUEST.toString(),
            new PlayerSnapshotListener(switchService));
        getServer().getMessenger().registerOutgoingPluginChannel(this, Channels.STATE_RESPONSE.toString());
        getServer().getMessenger().registerOutgoingPluginChannel(this, Channels.STATE_PUSH.toString());

        getServer().getMessenger().registerOutgoingPluginChannel(this, Channels.ENTITY_TRANSFER.toString());
        getServer().getMessenger().registerIncomingPluginChannel(this, Channels.ENTITY_TRANSFER.toString(), new EntitySpawnListener(this));

        getServer().getMessenger().registerOutgoingPluginChannel(this, Channels.ADVANCEMENT_TRANSFER.toString());
        AdvancementTransferListener advancementListener = new AdvancementTransferListener(this);
        getServer().getMessenger().registerIncomingPluginChannel(this, Channels.ADVANCEMENT_TRANSFER.toString(), advancementListener);
        getServer().getPluginManager().registerEvents(advancementListener, this);

        getServer().getMessenger().registerOutgoingPluginChannel(this, Channels.ENDER_CHEST_TRANSFER.toString());
        getServer().getMessenger().registerIncomingPluginChannel(this, Channels.ENDER_CHEST_TRANSFER.toString(),
            new EnderChestTransferListener(this));

        getServer().getMessenger().registerIncomingPluginChannel(this, Channels.SWITCH_FAILED.toString(), new SwitchFailedListener(this, positionStore, pendingVehicleStore));
        getServer().getPluginManager().registerEvents(pendingVehicleStore, this);

        getServer().getPluginManager().registerEvents(new PlayerBroadcastListener(), this);
        getServer().getPluginManager().registerEvents(new SpawnPointListener(), this);

        getServer().getPluginManager().registerEvents(new NetherPortalListener(dimension, dimensionSwitchService, positionStore), this);
        getServer().getPluginManager().registerEvents(new EndPortalListener(dimension, dimensionSwitchService, positionStore), this);
        getServer().getPluginManager().registerEvents(new EntityPortalListener(dimension, dimensionSwitchService, this), this);
        getServer().getPluginManager().registerEvents(new RespawnListener(dimension, dimensionSwitchService, this), this);
        getServer().getPluginManager().registerEvents(new DimensionGuardListener(this, dimension), this);
    }

    @Override
    public void onDisable() {
        if (snapshotService == null || dimension == null) return;
        for (org.bukkit.entity.Player player : getServer().getOnlinePlayers()) {
            try {
                snapshotService.sendSnapshot(player, dimension, Channels.STATE_PUSH);
            } catch (RuntimeException ex) {
                getLogger().warning("Could not send shutdown snapshot for " + player.getName() + ": " + ex.getMessage());
            }
        }
    }
}
