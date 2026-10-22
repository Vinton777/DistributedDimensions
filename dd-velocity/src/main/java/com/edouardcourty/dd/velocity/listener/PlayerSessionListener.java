package com.edouardcourty.dd.velocity.listener;

import com.edouardcourty.dd.common.messaging.Channels;
import com.edouardcourty.dd.velocity.handler.EntityTransferHandler;
import com.edouardcourty.dd.velocity.handler.BatchedTransferHandler;
import com.edouardcourty.dd.velocity.store.LastServerStore;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class PlayerSessionListener {
    private final ProxyServer server;
    private final LastServerStore lastServerStore;
    private final EntityTransferHandler entityTransferHandler;
    private final BatchedTransferHandler advancementTransferHandler;
    private final BatchedTransferHandler enderChestTransferHandler;

    public PlayerSessionListener(ProxyServer server, LastServerStore lastServerStore,
                                 EntityTransferHandler entityTransferHandler,
                                 BatchedTransferHandler advancementTransferHandler,
                                 BatchedTransferHandler enderChestTransferHandler) {
        this.server = server;
        this.lastServerStore = lastServerStore;
        this.entityTransferHandler = entityTransferHandler;
        this.advancementTransferHandler = advancementTransferHandler;
        this.enderChestTransferHandler = enderChestTransferHandler;
    }

    @Subscribe
    public EventTask onChooseInitialServer(PlayerChooseInitialServerEvent event) {
        Optional<String> lastServerOpt = lastServerStore.get(event.getPlayer().getUniqueId());
        if (lastServerOpt.isEmpty()) return null;

        String serverName = lastServerOpt.get();
        Optional<RegisteredServer> registeredOpt = server.getServer(serverName);
        if (registeredOpt.isEmpty()) return null;

        RegisteredServer target = registeredOpt.get();
        return EventTask.async(() -> {
            try {
                target.ping().get(3, TimeUnit.SECONDS);
                event.setInitialServer(target);
            } catch (Exception e) {
                event.getPlayer().disconnect(
                    Component.text("Server ", NamedTextColor.RED)
                        .append(Component.text(serverName, NamedTextColor.YELLOW))
                        .append(Component.text(" is offline.\nPlease reconnect when it becomes available.", NamedTextColor.RED))
                );
            }
        });
    }

    @Subscribe
    public void onServerPostConnect(ServerPostConnectEvent event) {
        event.getPlayer().getCurrentServer().ifPresent(conn -> {
            entityTransferHandler.deliverPending(conn.getServer());

        });
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        advancementTransferHandler.discard(event.getPlayer().getUniqueId());
        enderChestTransferHandler.discard(event.getPlayer().getUniqueId());
        event.getPlayer().getCurrentServer().ifPresent(conn ->
            lastServerStore.save(event.getPlayer().getUniqueId(), conn.getServerInfo().getName())
        );
    }
}
