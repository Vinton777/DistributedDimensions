package com.edouardcourty.dd.paper.listener;

import com.edouardcourty.dd.paper.messaging.EntityTransferSerializer;
import com.edouardcourty.dd.common.messaging.Channels;
import com.edouardcourty.dd.common.model.Dimension;
import com.edouardcourty.dd.common.model.PortalConstants;
import com.edouardcourty.dd.common.model.LocationData;
import com.edouardcourty.dd.common.service.DimensionSwitchService;
import com.edouardcourty.dd.paper.messaging.PlayerStateSerializer;
import com.edouardcourty.dd.paper.portal.SafeLocationFinder;
import com.edouardcourty.dd.paper.store.PrePortalPositionStore;
import com.edouardcourty.dd.paper.store.SpawnPointStore;
import com.google.common.io.ByteStreams;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.RespawnAnchor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

public class DimensionSwitchListener implements PluginMessageListener {
    private final JavaPlugin plugin;
    private final PrePortalPositionStore positionStore;
    private final DimensionSwitchService switchService;

    public DimensionSwitchListener(JavaPlugin plugin, PrePortalPositionStore positionStore,
                                   DimensionSwitchService switchService) {
        this.plugin = plugin;
        this.positionStore = positionStore;
        this.switchService = switchService;
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
        Dimension redirectDimension = null;
        LocationData redirectLocation = null;
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
            SpawnPointStore.Point anchorPoint = SpawnPointStore.getAnchor(player);
            Location respawn = player.getRespawnLocation();
            if (state.targetDimension() == Dimension.NETHER && anchorPoint != null
                && anchorPoint.dimension() == Dimension.NETHER) {
                var point = anchorPoint.location();
                Block anchorBlock = world.getBlockAt((int) Math.floor(point.x),
                    (int) Math.floor(point.y), (int) Math.floor(point.z));
                if (anchorBlock.getBlockData() instanceof RespawnAnchor anchor && anchor.getCharges() > 0) {
                    target = SafeLocationFinder.findNear(world, point.x, point.y, point.z,
                        point.yaw, point.pitch);
                    anchor.setCharges(anchor.getCharges() - 1);
                    anchorBlock.setBlockData(anchor);
                } else {
                    // The remote anchor was removed or depleted; use the stored bed instead.
                    SpawnPointStore.clearAnchor(player);
                    SpawnPointStore.Point fallback = SpawnPointStore.getFallback(player);
                    redirectDimension = fallback == null ? Dimension.OVERWORLD : fallback.dimension();
                    redirectLocation = fallback == null ? LocationData.of(0, 64, 0, 0, 0) : fallback.location();
                    target = world.getSpawnLocation();
                }
            } else if (savedSpawn != null && savedSpawn.dimension() == state.targetDimension()) {
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

        if (redirectDimension != null && redirectDimension != state.targetDimension()) {
            Dimension fallbackDimension = redirectDimension;
            LocationData fallbackLocation = redirectLocation;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) switchService.sendSwitchRequest(player.getUniqueId(),
                    fallbackDimension, fallbackLocation, false);
            }, 2L);
        }

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

