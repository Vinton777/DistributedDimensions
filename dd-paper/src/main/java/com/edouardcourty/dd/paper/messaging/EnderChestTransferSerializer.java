package com.edouardcourty.dd.paper.messaging;

import com.edouardcourty.dd.paper.util.ItemSerializer;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Splits the complete ender chest into packets below the plugin-message limit. */
public final class EnderChestTransferSerializer {
    public static final int MAX_CHUNK_BYTES = 16_000;

    private EnderChestTransferSerializer() {}

    public static List<byte[]> encode(Player player) {
        ItemStack[] contents = player.getEnderChest().getContents();
        ByteArrayDataOutput contentsOut = ByteStreams.newDataOutput();
        contentsOut.writeInt(contents.length);
        for (ItemStack item : contents) ItemSerializer.write(contentsOut, item);
        byte[] data = contentsOut.toByteArray();

        int total = Math.max(1, (data.length + MAX_CHUNK_BYTES - 1) / MAX_CHUNK_BYTES);
        if (total > 4096) throw new IllegalStateException("Ender chest is too large to transfer");
        List<byte[]> packets = new ArrayList<>(total);
        for (int index = 0; index < total; index++) {
            int start = index * MAX_CHUNK_BYTES;
            int length = Math.min(MAX_CHUNK_BYTES, data.length - start);
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF(player.getUniqueId().toString());
            out.writeInt(index);
            out.writeInt(total);
            out.writeInt(length);
            out.write(data, start, length);
            packets.add(out.toByteArray());
        }
        return packets;
    }
}
