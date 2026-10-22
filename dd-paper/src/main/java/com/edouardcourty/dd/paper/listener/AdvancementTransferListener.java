package com.edouardcourty.dd.paper.listener;

import com.edouardcourty.dd.common.messaging.Channels;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Merges source advancement criteria into the destination player's progress. */
public final class AdvancementTransferListener implements PluginMessageListener, Listener {
    private final JavaPlugin plugin;
    private final Set<UUID> applying = new HashSet<>();

    public AdvancementTransferListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player carrier, byte @NotNull [] bytes) {
        if (!Channels.ADVANCEMENT_TRANSFER.toString().equals(channel)) return;

        ByteArrayDataInput in = ByteStreams.newDataInput(bytes);
        UUID playerUuid = UUID.fromString(in.readUTF());
        in.readInt(); // batch index, used by Velocity to verify a complete transfer
        in.readInt(); // total batches
        int recordCount = in.readInt();
        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null) {
            plugin.getLogger().warning("[AdvancementTransfer] Player " + playerUuid + " is not online; progress not applied.");
            return;
        }

        applying.add(playerUuid);
        try {
            for (int i = 0; i < recordCount; i++) {
                NamespacedKey key = NamespacedKey.fromString(in.readUTF());
                String criterion = in.readUTF();
                Advancement advancement = key == null ? null : Bukkit.getAdvancement(key);
                if (advancement != null && advancement.getCriteria().contains(criterion)) {
                    player.getAdvancementProgress(advancement).awardCriteria(criterion);
                }
            }
        } finally {
            applying.remove(playerUuid);
        }
        player.saveData();
    }

    @EventHandler
    public void onAdvancementDone(PlayerAdvancementDoneEvent event) {
        if (applying.contains(event.getPlayer().getUniqueId())) event.message(null);
    }
}
