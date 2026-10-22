package com.edouardcourty.dd.paper.messaging;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Encodes awarded advancement criteria in plugin-message-sized batches. */
public final class AdvancementTransferSerializer {
    private static final int MAX_RECORD_BYTES_PER_BATCH = 16_000;

    private AdvancementTransferSerializer() {}

    public static List<byte[]> encode(Player player) {
        List<List<byte[]>> batches = new ArrayList<>();
        List<byte[]> current = new ArrayList<>();
        int currentSize = 0;

        var advancements = Bukkit.advancementIterator();
        while (advancements.hasNext()) {
            Advancement advancement = advancements.next();
            String key = advancement.getKey().toString();
            for (String criterion : player.getAdvancementProgress(advancement).getAwardedCriteria()) {
                ByteArrayDataOutput record = ByteStreams.newDataOutput();
                record.writeUTF(key);
                record.writeUTF(criterion);
                byte[] bytes = record.toByteArray();
                if (bytes.length > MAX_RECORD_BYTES_PER_BATCH) {
                    throw new IllegalStateException("Advancement criterion is too large to transfer: " + key);
                }
                if (!current.isEmpty() && currentSize + bytes.length > MAX_RECORD_BYTES_PER_BATCH) {
                    batches.add(current);
                    current = new ArrayList<>();
                    currentSize = 0;
                }
                current.add(bytes);
                currentSize += bytes.length;
            }
        }
        batches.add(current);

        List<byte[]> packets = new ArrayList<>(batches.size());
        UUID playerUuid = player.getUniqueId();
        for (int index = 0; index < batches.size(); index++) {
            List<byte[]> records = batches.get(index);
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF(playerUuid.toString());
            out.writeInt(index);
            out.writeInt(batches.size());
            out.writeInt(records.size());
            for (byte[] record : records) out.write(record);
            packets.add(out.toByteArray());
        }
        return packets;
    }
}
