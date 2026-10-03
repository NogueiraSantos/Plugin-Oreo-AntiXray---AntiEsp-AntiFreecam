package me.theus.oreoAntiXray.listeners;

import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import me.theus.oreoAntiXray.OreoAntiXray;
import me.theus.oreoAntiXray.models.BlockPosition;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import java.util.UUID;

public class PlayerListener implements Listener {

    private final OreoAntiXray plugin;

    public PlayerListener(OreoAntiXray plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();
        if (plugin.isWorldDisabled(player.getWorld().getName())) return;

        if (plugin.getOreObfuscatorManager().isEnabled() && plugin.getOreObfuscatorManager().isOre(block.getType())) {
            BlockPosition pos = BlockPosition.fromLocation(block.getLocation());
            plugin.getOreObfuscatorManager().markRevealed(player.getUniqueId(), pos);
            player.sendBlockChange(block.getLocation(), block.getBlockData());
        }

        if (plugin.getAntiEspManager().isEnabled() && plugin.getAntiEspManager().isTarget(block.getType())) {
            BlockPosition pos = BlockPosition.fromLocation(block.getLocation());
            plugin.getAntiEspManager().markRevealed(player.getUniqueId(), pos);
            player.sendBlockChange(block.getLocation(), block.getBlockData());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();
        Location loc = block.getLocation();

        if (plugin.getFakeOreManager().isFakeOre(loc)) {
            event.setDropItems(false);
            event.setExpToDrop(0);

            block.setType(Material.AIR, false);

            plugin.getFakeOreManager().handleFakeOreMined(player, loc);
            return;
        }

        plugin.getOreObfuscatorManager().revealNearbyOres(loc);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        plugin.getAntiFreecamManager().handlePlayerMove(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        plugin.getAntiFreecamManager().handlePlayerTeleport(player, from, to);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                plugin.getAntiFreecamManager().handlePlayerJoin(player);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                plugin.getAntiFreecamManager().handlePlayerMove(player);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                plugin.getAntiFreecamManager().handlePlayerMove(player);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerTrackEntity(PlayerTrackEntityEvent event) {
        Player player = event.getPlayer();
        if (plugin.isWorldDisabled(player.getWorld().getName())) return;
        if (!plugin.getAntiFreecamManager().isEnabled()) return;

        if (plugin.getAntiFreecamManager().shouldObfuscateUnderground(player)) {
            Entity entity = event.getEntity();
            if (entity.equals(player)) return;
            if (entity instanceof org.bukkit.entity.Projectile) return;

            if (entity.getLocation().getY() <= plugin.getAntiFreecamManager().getLowerY()) {
                event.setCancelled(true);
                plugin.getAntiFreecamManager().trackHiddenEntity(player.getUniqueId(), entity.getUniqueId());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        plugin.getFakeOreManager().onChunkUnload(event.getWorld().getName(), event.getChunk().getX(), event.getChunk().getZ());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        plugin.getOreObfuscatorManager().removePlayerData(uuid);
        plugin.getAntiEspManager().removePlayerData(uuid);
        plugin.getAntiFreecamManager().removePlayerData(uuid);
        plugin.removeAlertToggled(uuid);
    }
}
