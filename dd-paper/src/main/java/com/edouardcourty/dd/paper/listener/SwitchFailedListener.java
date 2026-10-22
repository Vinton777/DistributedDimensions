package com.edouardcourty.dd.paper.listener;

import com.edouardcourty.dd.common.messaging.Channels;
import com.edouardcourty.dd.common.model.PortalConstants;
import com.edouardcourty.dd.paper.store.PrePortalPositionStore;
import com.edouardcourty.dd.paper.store.PendingVehicleStore;
import com.google.common.io.ByteStreams;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Reçoit dd:switch_failed de Velocity quand le serveur cible est inaccessible.
 * Remet le joueur à sa position d'avant le portail via {@link PrePortalPositionStore}.
 */
public class SwitchFailedListener implements PluginMessageListener {
    private final JavaPlugin plugin;
    private final PrePortalPositionStore positionStore;
    private final PendingVehicleStore pendingVehicleStore;

    public SwitchFailedListener(JavaPlugin plugin, PrePortalPositionStore positionStore, PendingVehicleStore pendingVehicleStore) {
        this.plugin = plugin;
        this.positionStore = positionStore;
        this.pendingVehicleStore = pendingVehicleStore;
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player ignored, byte @NotNull [] bytes) {
        if (!channel.equals(Channels.SWITCH_FAILED.toString())) return;

        UUID uuid = UUID.fromString(ByteStreams.newDataInput(bytes).readUTF());
        Player player = plugin.getServer().getPlayer(uuid);
        if (player == null) return;

        positionStore.remove(uuid).ifPresent(player::teleport);
        pendingVehicleStore.restore(player);
        player.setPortalCooldown(PortalConstants.PORTAL_COOLDOWN_TICKS);
        player.sendMessage(Component.text(
            "Destination server is unreachable. Please try again later.",
            NamedTextColor.RED
        ));
        plugin.getLogger().warning("[SwitchFailed] Player " + player.getName() + " could not switch dimension — server offline.");
    }
}

