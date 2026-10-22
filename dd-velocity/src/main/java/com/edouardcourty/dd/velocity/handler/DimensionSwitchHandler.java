package com.edouardcourty.dd.velocity.handler;

import com.edouardcourty.dd.common.messaging.Channels;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.ConnectionRequestBuilder;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class DimensionSwitchHandler {
    private final ProxyServer server;
    private final Map<String, String> serverNames;
    private final BatchedTransferHandler advancementTransferHandler;
    private final BatchedTransferHandler enderChestTransferHandler;

    public DimensionSwitchHandler(ProxyServer server, Map<String, String> serverNames,
                                  BatchedTransferHandler advancementTransferHandler,
                                  BatchedTransferHandler enderChestTransferHandler) {
        this.server = server;
        this.serverNames = serverNames;
        this.advancementTransferHandler = advancementTransferHandler;
        this.enderChestTransferHandler = enderChestTransferHandler;
    }

    public void handle(PluginMessageEvent event) {
        ByteArrayDataInput in = ByteStreams.newDataInput(event.getData());
        UUID playerUuid = UUID.fromString(in.readUTF());
        String dimensionKey = in.readUTF().toLowerCase();
        String targetServerName = serverNames.getOrDefault(dimensionKey, dimensionKey);
        // les autres champs restent dans event.getData() pour le forward

        Player player = server.getPlayer(playerUuid).orElse(null);
        if (player == null) return;

        RegisteredServer targetServer = server.getServer(targetServerName).orElse(null);
        if (targetServer == null) {
            sendSwitchFailed(event, playerUuid);
            return;
        }

        ConnectionRequestBuilder req = player.createConnectionRequest(targetServer);
        byte[] stateBytes = event.getData();

        req.connect()
            .orTimeout(5, TimeUnit.SECONDS)
            .thenAccept(result -> {
                if (result.getStatus() == ConnectionRequestBuilder.Status.SUCCESS) {
                    player.getCurrentServer().ifPresent(conn -> {
                        enderChestTransferHandler.forward(playerUuid, conn);
                        conn.sendPluginMessage(
                            MinecraftChannelIdentifier.from(Channels.DIM_SWITCH.toString()),
                            stateBytes
                        );
                        advancementTransferHandler.forward(playerUuid, conn);
                    });
                } else {
                    sendSwitchFailed(event, playerUuid);
                }
            })
            .exceptionally(ex -> {
                sendSwitchFailed(event, playerUuid);
                return null;
            });
    }

    private void sendSwitchFailed(PluginMessageEvent event, UUID playerUuid) {
        advancementTransferHandler.discard(playerUuid);
        enderChestTransferHandler.discard(playerUuid);
        if (!(event.getSource() instanceof ServerConnection sourceConn)) return;
        var out = ByteStreams.newDataOutput();
        out.writeUTF(playerUuid.toString());
        sourceConn.getServer().sendPluginMessage(
            MinecraftChannelIdentifier.from(Channels.SWITCH_FAILED.toString()),
            out.toByteArray()
        );
    }
}
