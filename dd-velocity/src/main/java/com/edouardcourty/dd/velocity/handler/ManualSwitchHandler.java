package com.edouardcourty.dd.velocity.handler;

import com.edouardcourty.dd.common.messaging.Channels;
import com.edouardcourty.dd.common.model.Dimension;
import com.google.common.io.ByteStreams;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Gets a fresh Paper snapshot before Velocity's /server connection proceeds. */
public final class ManualSwitchHandler {
    private static final long SHUTDOWN_SNAPSHOT_MS = 10 * 60_000;
    private final Map<String, String> serverNames;
    private final DimensionSwitchHandler portalSwitches;
    private final BatchedTransferHandler advancements;
    private final BatchedTransferHandler enderChests;
    private final Map<UUID, Waiting> waiting = new ConcurrentHashMap<>();
    private final Map<UUID, Ready> ready = new ConcurrentHashMap<>();
    private final Map<UUID, Ready> shutdown = new ConcurrentHashMap<>();
    private final MinecraftChannelIdentifier requestChannel = MinecraftChannelIdentifier.from(Channels.STATE_REQUEST.toString());
    private final MinecraftChannelIdentifier stateChannel = MinecraftChannelIdentifier.from(Channels.DIM_SWITCH.toString());

    public ManualSwitchHandler(Map<String, String> serverNames, DimensionSwitchHandler portalSwitches,
                               BatchedTransferHandler advancements, BatchedTransferHandler enderChests) {
        this.serverNames = serverNames;
        this.portalSwitches = portalSwitches;
        this.advancements = advancements;
        this.enderChests = enderChests;
    }

    public EventTask beforeConnect(ServerPreConnectEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (portalSwitches.isPortalSwitch(uuid)) return null;
        ServerConnection source = event.getPlayer().getCurrentServer().orElse(null);
        RegisteredServer target = event.getResult().getServer().orElse(null);
        if (source == null || target == null || source.getServer().equals(target)) return null;
        Dimension dimension = dimensionFor(target.getServerInfo().getName());
        if (dimension == null) return null;

        CompletableFuture<byte[]> response = new CompletableFuture<>();
        Waiting request = new Waiting(source.getServerInfo().getName(), target.getServerInfo().getName(), response);
        waiting.put(uuid, request);
        ready.remove(uuid);
        advancements.discard(uuid);
        enderChests.discard(uuid);
        var out = ByteStreams.newDataOutput();
        out.writeUTF(uuid.toString());
        out.writeUTF(dimension.name());
        if (!source.sendPluginMessage(requestChannel, out.toByteArray())) {
            waiting.remove(uuid, request);
            event.setResult(ServerPreConnectEvent.ServerResult.denied());
            return null;
        }

        CompletableFuture<Void> gate = response.orTimeout(5, TimeUnit.SECONDS).handle((bytes, error) -> {
            waiting.remove(uuid, request);
            if (error != null || bytes == null) {
                event.setResult(ServerPreConnectEvent.ServerResult.denied());
                advancements.discard(uuid);
                enderChests.discard(uuid);
            } else {
                ready.put(uuid, new Ready(request.sourceServer, request.targetServer, bytes, System.currentTimeMillis()));
            }
            return null;
        });
        return EventTask.resumeWhenComplete(gate);
    }

    public void onResponse(PluginMessageEvent event, boolean shutdownPush) {
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        if (!(event.getSource() instanceof ServerConnection source)) return;
        UUID uuid = UUID.fromString(ByteStreams.newDataInput(event.getData()).readUTF());
        if (!source.getPlayer().getUniqueId().equals(uuid)) return;
        if (shutdownPush) {
            shutdown.put(uuid, new Ready(source.getServerInfo().getName(), "", event.getData().clone(),
                System.currentTimeMillis()));
            return;
        }
        Waiting request = waiting.get(uuid);
        if (request != null && request.sourceServer.equals(source.getServerInfo().getName())) {
            request.response.complete(event.getData().clone());
        }
    }

    public void afterConnect(ServerPostConnectEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        ServerConnection destination = event.getPlayer().getCurrentServer().orElse(null);
        if (destination == null) return;
        String name = destination.getServerInfo().getName();

        Ready snapshot = ready.remove(uuid);
        if (snapshot != null && !snapshot.targetServer.equals(name)) {
            snapshot = null;
            advancements.discard(uuid);
            enderChests.discard(uuid);
        }
        if (snapshot == null) {
            Ready fallback = shutdown.get(uuid);
            if (fallback != null && System.currentTimeMillis() - fallback.createdAt <= SHUTDOWN_SNAPSHOT_MS
                && !fallback.sourceServer.equals(name)) {
                snapshot = fallback;
                shutdown.remove(uuid, fallback);
            } else if (fallback != null && fallback.sourceServer.equals(name)) {
                shutdown.remove(uuid, fallback);
                advancements.discard(uuid);
                enderChests.discard(uuid);
            } else if (fallback != null) {
                shutdown.remove(uuid, fallback);
                advancements.discard(uuid);
                enderChests.discard(uuid);
            }
        }
        if (snapshot != null && (snapshot.targetServer.isEmpty() || snapshot.targetServer.equals(name))) {
            shutdown.remove(uuid);
            enderChests.forward(uuid, destination);
            destination.sendPluginMessage(stateChannel, snapshot.bytes);
            advancements.forward(uuid, destination);
        }
    }

    public boolean onDisconnect(UUID uuid) {
        Waiting request = waiting.remove(uuid);
        if (request != null) request.response.completeExceptionally(new IllegalStateException("Player disconnected"));
        ready.remove(uuid);
        Ready fallback = shutdown.get(uuid);
        return fallback != null && System.currentTimeMillis() - fallback.createdAt <= SHUTDOWN_SNAPSHOT_MS;
    }

    private Dimension dimensionFor(String serverName) {
        for (Dimension dimension : Dimension.values()) {
            if (serverName.equals(serverNames.get(dimension.name().toLowerCase()))) return dimension;
        }
        return null;
    }

    private record Waiting(String sourceServer, String targetServer, CompletableFuture<byte[]> response) {}
    private record Ready(String sourceServer, String targetServer, byte[] bytes, long createdAt) {}
}
