package com.edouardcourty.dd.paper.listener;

import com.edouardcourty.dd.paper.messaging.EntityTransferSerializer;
import com.edouardcourty.dd.common.messaging.Channels;
import com.edouardcourty.dd.common.model.Dimension;
import com.edouardcourty.dd.common.model.PortalConstants;
import com.edouardcourty.dd.paper.messaging.PlayerStateSerializer;
import com.edouardcourty.dd.paper.portal.SafeLocationFinder;
import com.edouardcourty.dd.paper.store.PrePortalPositionStore;
import com.edouardcourty.dd.paper.store.SpawnPointStore;
import com.google.common.io.ByteStreams;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
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
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player carrier, byte @NotNull [] bytes) {
        if (!channel.equals(Channels.DIM_SWITCH.toString())) return;

        PlayerStateSerializer.PlayerState state = PlayerStateSerializer.read(ByteStreams.newDataInput(bytes));
        Player player = plugin.getServer().getPlayer(state.playerUuid());
        if (player == null) {
            plugin.getLogger().warning("[DimensionSwitch] Player " + state.playerUuid() + " is not online; state not applied.");
            return;
        }
        if (state.spawnPoint() != null) SpawnPointStore.applyRaw(player, state.spawnPoint());
        if (state.stateOnly()) {
            positionStore.clear(state.playerUuid());
            applyState(player, state);
            player.saveData();
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
            // Respawn or End exit: prefer the synchronized bed/anchor on this server.
            SpawnPointStore.Point savedSpawn = SpawnPointStore.get(player);
            Location respawn = player.getRespawnLocation();
            if (savedSpawn != null && savedSpawn.dimension() == state.targetDimension()) {
                var point = savedSpawn.location();
                target = SafeLocationFinder.findNear(world, point.x, point.y, point.z, point.yaw, point.pitch);
            } else if (respawn != null && world.equals(respawn.getWorld())) {
                target = SafeLocationFinder.findNear(world, respawn.getX(), respawn.getY(), respawn.getZ(),
                    respawn.getYaw(), respawn.getPitch());
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
        player.saveData();

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
        if (state.hasVitals() && state.maxHealthBase() > 0) {
            var maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealth != null) maxHealth.setBaseValue(state.maxHealthBase());
            double effectiveMax = maxHealth == null ? player.getMaxHealth() : maxHealth.getValue();
            if (effectiveMax > 0) player.setHealth(Math.max(0.001, Math.min(state.health(), effectiveMax)));
            player.setAbsorptionAmount(Math.max(0, state.absorption()));
        }
    }
}

