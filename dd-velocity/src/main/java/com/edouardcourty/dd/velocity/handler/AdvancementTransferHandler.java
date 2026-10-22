package com.edouardcourty.dd.velocity.handler;

import com.edouardcourty.dd.common.messaging.Channels;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Holds advancement batches until the player finishes switching servers. */
public final class AdvancementTransferHandler {
    private static final long EXPIRY_MS = 60_000;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private final MinecraftChannelIdentifier channel = MinecraftChannelIdentifier.from(Channels.ADVANCEMENT_TRANSFER.toString());

    public void handle(PluginMessageEvent event) {
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        if (!(event.getSource() instanceof ServerConnection)) return;

        ByteArrayDataInput in = ByteStreams.newDataInput(event.getData());
        UUID playerUuid = UUID.fromString(in.readUTF());
        int index = in.readInt();
        int total = in.readInt();
        if (total <= 0 || total > 4096 || index < 0 || index >= total) return;

        pending.compute(playerUuid, (ignored, old) -> {
            Pending next = old == null || old.expired() || (old.batches != null && old.batches.length != total)
                || (index == 0 && old.batches != null && old.batches[0] != null)
                ? new Pending(total) : old;
            if (next != old && old != null && !old.expired()) next.destination = old.destination;
            if (next.batches == null) next.batches = new byte[total][];
            next.batches[index] = event.getData().clone();
            return flushIfComplete(next);
        });
    }

    public void forward(UUID playerUuid, ServerConnection destination) {
        pending.compute(playerUuid, (ignored, old) -> {
            Pending transfer = old == null || old.expired() ? new Pending(0) : old;
            transfer.destination = destination;
            return flushIfComplete(transfer);
        });
    }

    public void discard(UUID playerUuid) {
        pending.remove(playerUuid);
    }

    private Pending flushIfComplete(Pending transfer) {
        if (transfer.destination == null || transfer.batches == null
            || Arrays.stream(transfer.batches).anyMatch(bytes -> bytes == null)) return transfer;
        for (byte[] batch : transfer.batches) transfer.destination.sendPluginMessage(channel, batch);
        return null;
    }

    private static final class Pending {
        private final long createdAt = System.currentTimeMillis();
        private byte[][] batches;
        private ServerConnection destination;

        private Pending(int total) { if (total > 0) this.batches = new byte[total][]; }
        private boolean expired() { return System.currentTimeMillis() - createdAt > EXPIRY_MS; }
    }
}
