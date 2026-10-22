package com.edouardcourty.dd.paper.listener;

import com.edouardcourty.dd.paper.messaging.EntityTransferSerializer;
import com.edouardcourty.dd.common.messaging.Channels;
import com.edouardcourty.dd.common.model.Dimension;
import com.edouardcourty.dd.common.model.PortalConstants;
import com.edouardcourty.dd.paper.messaging.PlayerStateSerializer;
import com.edouardcourty.dd.paper.portal.SafeLocationFinder;
import com.edouardcourty.dd.paper.store.PrePortalPositionStore;
import com.google.common.io.ByteStreams;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

public class DimensionSwitchListener implements PluginMessageListener {
    private final JavaPlugin plugin;
    private final PrePortalPositionStore positionStore;

    public DimensionSwitchListener(JavaPlugin plugin, PrePortalPositionStore positionStore) {
        this.plugin = plugin;
        this.positionStore = positionStore;
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] bytes) {
        boolean isRestore = channel.equals(Channels.RESTORE_STATE.toString());
        if (!channel.equals(Channels.DIM_SWITCH.toString()) && !isRestore) return;

        PlayerStateSerializer.PlayerState state = PlayerStateSerializer.read(ByteStreams.newDataInput(bytes));

        // Restoration : appliquer état sans téléporter ni construire de portail
        if (isRestore) {
            positionStore.clear(state.playerUuid());
            applyState(player, state);
            return;
        }

        World world = plugin.getServer().getWorld(state.targetDimension().toBukkitWorldName());
        if (world == null) {
            plugin.getLogger().severe("World not found for dimension: " + state.targetDimension());
            return;
        }

        Location target;
        if (state.targetDimension() == com.edouardcourty.dd.common.model.Dimension.END && !state.buildPortal()) {
            // Arrivée dans l'End : toujours reconstruire la plateforme vanilla à (100, 49, 0)
            target = com.edouardcourty.dd.paper.portal.PortalBuilder.buildEndPlatform(
                world,
                (int) state.location().x,
                (int) state.location().y,
                (int) state.location().z
            );
        } else if (state.buildPortal()) {
            target = SafeLocationFinder.findAndBuildPortal(
                world,
                state.location().x, state.location().y, state.location().z,
                state.location().yaw, state.location().pitch);
        } else {
            // Pour un respawn (mort cross-serveur) : utiliser le point de respawn connu de CE serveur
            Location respawn = player.getRespawnLocation();
            if (respawn != null && world.equals(respawn.getWorld())) {
                target = respawn;
            } else {
                target = SafeLocationFinder.findOnly(
                    world,
                    state.location().x, state.location().y, state.location().z,
                    state.location().yaw, state.location().pitch);
            }
        }

        player.teleport(target);
        player.setPortalCooldown(PortalConstants.PLAYER_PORTAL_COOLDOWN_TICKS);

        positionStore.clear(state.playerUuid());
        applyState(player, state);

        // Remonter le joueur dans son véhicule si applicable
        if (state.vehicle() != null) {
            final Location spawnLoc = target;
            final Player p = player;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                org.bukkit.entity.Entity vehicle = EntityTransferSerializer.spawn(world, spawnLoc, state.vehicle());
                vehicle.addPassenger(p);
            }, 2L);
        }
    }

    private void applyState(Player player, PlayerStateSerializer.PlayerState state) {
        player.getInventory().setContents(state.inventoryContents());
        player.getInventory().setArmorContents(state.armorContents());
        player.getInventory().setItemInOffHand(state.offhand());
        if (state.selectedHotbarSlot() >= 0 && state.selectedHotbarSlot() <= 8) {
            player.getInventory().setHeldItemSlot(state.selectedHotbarSlot());
        }
        player.updateInventory();
        player.setLevel(state.xpLevel());
        player.setExp(state.xpProgress());
        player.setFoodLevel(state.foodLevel());
        player.setSaturation(state.saturation());
        player.setExhaustion(state.exhaustion());
        player.setGameMode(state.gameMode());
        player.clearActivePotionEffects();
        state.potionEffects().forEach(player::addPotionEffect);
    }
}

