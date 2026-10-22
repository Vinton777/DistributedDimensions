package com.edouardcourty.dd.paper;

import com.edouardcourty.dd.common.messaging.Channels;
import com.edouardcourty.dd.common.model.Dimension;
import com.edouardcourty.dd.common.service.DimensionSwitchService;
import com.edouardcourty.dd.paper.listener.DimensionGuardListener;
import com.edouardcourty.dd.paper.listener.DimensionSwitchListener;
import com.edouardcourty.dd.paper.listener.EndPortalListener;
import com.edouardcourty.dd.paper.listener.EntityPortalListener;
import com.edouardcourty.dd.paper.listener.EntitySpawnListener;
import com.edouardcourty.dd.paper.listener.NetherPortalListener;
import com.edouardcourty.dd.paper.listener.PlayerBroadcastListener;
import com.edouardcourty.dd.paper.listener.RespawnListener;
import com.edouardcourty.dd.paper.listener.SwitchFailedListener;
import com.edouardcourty.dd.paper.service.VelocityPluginMessageSwitchService;
import com.edouardcourty.dd.paper.store.PrePortalPositionStore;
import com.edouardcourty.dd.paper.store.PendingVehicleStore;
import org.bukkit.plugin.java.JavaPlugin;

public class DistributedDimensions extends JavaPlugin {
    private Dimension dimension;
    private DimensionSwitchService dimensionSwitchService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        dimension = Dimension.valueOf(getConfig().getString("world", Dimension.OVERWORLD.name()));
        PrePortalPositionStore positionStore = new PrePortalPositionStore(this);
        PendingVehicleStore pendingVehicleStore = new PendingVehicleStore();
        dimensionSwitchService = new VelocityPluginMessageSwitchService(this, pendingVehicleStore);

        DimensionSwitchListener dimSwitchListener = new DimensionSwitchListener(this, positionStore);
        getServer().getMessenger().registerOutgoingPluginChannel(this, Channels.DIM_SWITCH.toString());
        getServer().getMessenger().registerIncomingPluginChannel(this, Channels.DIM_SWITCH.toString(), dimSwitchListener);

        getServer().getMessenger().registerOutgoingPluginChannel(this, Channels.ENTITY_TRANSFER.toString());
        getServer().getMessenger().registerIncomingPluginChannel(this, Channels.ENTITY_TRANSFER.toString(), new EntitySpawnListener(this));

        getServer().getMessenger().registerIncomingPluginChannel(this, Channels.SWITCH_FAILED.toString(), new SwitchFailedListener(this, positionStore, pendingVehicleStore));
        getServer().getPluginManager().registerEvents(pendingVehicleStore, this);

        getServer().getPluginManager().registerEvents(new PlayerBroadcastListener(), this);

        getServer().getPluginManager().registerEvents(new NetherPortalListener(dimension, dimensionSwitchService, positionStore), this);
        getServer().getPluginManager().registerEvents(new EndPortalListener(dimension, dimensionSwitchService, positionStore), this);
        getServer().getPluginManager().registerEvents(new EntityPortalListener(dimension, dimensionSwitchService, this), this);
        getServer().getPluginManager().registerEvents(new RespawnListener(dimension, dimensionSwitchService, this), this);
        getServer().getPluginManager().registerEvents(new DimensionGuardListener(this, dimension), this);
    }
}
