package com.edouardcourty.dd.paper.listener;

import com.edouardcourty.dd.common.messaging.Channels;
import com.edouardcourty.dd.common.model.Dimension;
import com.edouardcourty.dd.paper.service.VelocityPluginMessageSwitchService;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** Answers a proxy request while the player is still on this backend. */
public final class PlayerSnapshotListener implements PluginMessageListener {
    private final VelocityPluginMessageSwitchService switchService;

    public PlayerSnapshotListener(VelocityPluginMessageSwitchService switchService) {
        this.switchService = switchService;
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player carrier, byte @NotNull [] bytes) {
        if (!Channels.STATE_REQUEST.toString().equals(channel)) return;
        var in = ByteStreams.newDataInput(bytes);
        UUID playerUuid = UUID.fromString(in.readUTF());
        Dimension target = Dimension.valueOf(in.readUTF());
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && player.isOnline() && player.equals(carrier)) {
            switchService.sendSnapshot(player, target, Channels.STATE_RESPONSE);
        }
    }
}
