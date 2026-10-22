package com.edouardcourty.dd.paper.listener;

import com.edouardcourty.dd.common.messaging.Channels;
import com.edouardcourty.dd.paper.messaging.EnderChestTransferSerializer;
import com.edouardcourty.dd.paper.util.ItemSerializer;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Applies a complete ender chest only after every packet has arrived. */
public final class EnderChestTransferListener implements PluginMessageListener {
    private static final long EXPIRY_MS = 60_000;
    private final JavaPlugin plugin;
    private final Map<UUID, Pending> pending = new HashMap<>();

    public EnderChestTransferListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player carrier, byte @NotNull [] bytes) {
        if (!Channels.ENDER_CHEST_TRANSFER.toString().equals(channel)) return;

        ByteArrayDataInput in = ByteStreams.newDataInput(bytes);
        UUID playerUuid = UUID.fromString(in.readUTF());
        int index = in.readInt();
        int total = in.readInt();
        int length = in.readInt();
        if (total <= 0 || total > 4096 || index < 0 || index >= total
            || length < 0 || length > EnderChestTransferSerializer.MAX_CHUNK_BYTES) return;
        byte[] chunk = new byte[length];
        in.readFully(chunk);

        Pending transfer = pending.get(playerUuid);
        if (transfer == null || transfer.expired() || transfer.chunks.length != total
            || (index == 0 && transfer.chunks[0] != null)) {
            transfer = new Pending(total);
            pending.put(playerUuid, transfer);
        }
        transfer.chunks[index] = chunk;
        if (Arrays.stream(transfer.chunks).anyMatch(part -> part == null)) return;
        pending.remove(playerUuid);

        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null) {
            plugin.getLogger().warning("[EnderChestTransfer] Player " + playerUuid + " is not online; chest not applied.");
            return;
        }

        ByteArrayOutputStream joined = new ByteArrayOutputStream();
        for (byte[] part : transfer.chunks) joined.writeBytes(part);
        ByteArrayDataInput contentsIn = ByteStreams.newDataInput(joined.toByteArray());
        int size = contentsIn.readInt();
        if (size != player.getEnderChest().getSize()) {
            plugin.getLogger().warning("[EnderChestTransfer] Unexpected chest size " + size + " for " + playerUuid);
            return;
        }
        ItemStack[] contents = new ItemStack[size];
        for (int slot = 0; slot < size; slot++) contents[slot] = ItemSerializer.read(contentsIn);
        player.getEnderChest().setContents(contents);
        player.updateInventory();
        player.saveData();
    }

    private static final class Pending {
        private final long createdAt = System.currentTimeMillis();
        private final byte[][] chunks;

        private Pending(int total) { chunks = new byte[total][]; }
        private boolean expired() { return System.currentTimeMillis() - createdAt > EXPIRY_MS; }
    }
}
