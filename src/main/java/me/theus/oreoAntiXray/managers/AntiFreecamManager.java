package me.theus.oreoAntiXray.managers;

import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import me.theus.oreoAntiXray.OreoAntiXray;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AntiFreecamManager {

    private final OreoAntiXray plugin;
    private final Map<UUID, Boolean> playerAboveState = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> hiddenEntities = new ConcurrentHashMap<>();

    private boolean enabled;
    private int upperY;
    private int lowerY;
    private int refreshRadius;
    private boolean hideEntities;

    private WrappedBlockState stoneState;
    private WrappedBlockState deepslateState;

    private BukkitTask scanTask;

    public AntiFreecamManager(OreoAntiXray plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        if (scanTask != null) {
            scanTask.cancel();
            scanTask = null;
        }

        this.enabled = plugin.getConfig().getBoolean("ANTI-FREECAM.ENABLED", true);
        this.upperY = plugin.getConfig().getInt("ANTI-FREECAM.UPPER-Y", 45);
        this.lowerY = plugin.getConfig().getInt("ANTI-FREECAM.LOWER-Y", 40);
        this.refreshRadius = plugin.getConfig().getInt("ANTI-FREECAM.REFRESH-RADIUS", 4);
        this.hideEntities = plugin.getConfig().getBoolean("ANTI-FREECAM.HIDE-ENTITIES", true);

        this.stoneState = StateTypes.STONE.createBlockState();
        this.deepslateState = StateTypes.DEEPSLATE.createBlockState();

        playerAboveState.clear();
        hiddenEntities.clear();

        if (enabled && hideEntities) {
            scanTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (shouldObfuscateUnderground(player)) {
                        hideUndergroundEntities(player);
                    }
                }
            }, 20L, 20L);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getLowerY() {
        return lowerY;
    }

    public int getUpperY() {
        return upperY;
    }

    public boolean isHideEntities() {
        return hideEntities;
    }

    public boolean shouldObfuscateUnderground(Player player) {
        if (!enabled || player == null || !player.isOnline()) return false;
        World world = player.getWorld();
        if (plugin.isWorldDisabled(world.getName())) return false;
        if (world.getEnvironment() != World.Environment.NORMAL) return false;

        Boolean cached = playerAboveState.get(player.getUniqueId());
        if (cached != null) {
            return cached;
        }
        return player.getLocation().getY() > upperY;
    }

    public WrappedBlockState getSolidReplacement(int y) {
        if (y <= 0) {
            return deepslateState;
        }
        return stoneState;
    }

    public void trackHiddenEntity(UUID playerUuid, UUID entityUuid) {
        hiddenEntities.computeIfAbsent(playerUuid, k -> ConcurrentHashMap.newKeySet()).add(entityUuid);
    }

    public void handlePlayerJoin(Player player) {
        if (!enabled || player == null || !player.isOnline()) return;
        if (player.getWorld().getEnvironment() != World.Environment.NORMAL) return;

        boolean isAbove = player.getLocation().getY() > upperY;
        playerAboveState.put(player.getUniqueId(), isAbove);
        if (isAbove && hideEntities) {
            hideUndergroundEntities(player);
        }
    }

    public void handlePlayerMove(Player player) {
        if (!enabled || player == null || !player.isOnline()) return;
        World world = player.getWorld();
        if (plugin.isWorldDisabled(world.getName())) return;

        if (world.getEnvironment() != World.Environment.NORMAL) {
            if (playerAboveState.remove(player.getUniqueId()) != null && hideEntities) {
                restoreUndergroundEntities(player);
            }
            return;
        }

        double currentY = player.getLocation().getY();
        boolean isCurrentlyAbove = currentY > upperY;

        Boolean previousState = playerAboveState.put(player.getUniqueId(), isCurrentlyAbove);

        if (previousState != null && previousState != isCurrentlyAbove) {
            if (isCurrentlyAbove) {
                if (hideEntities) {
                    hideUndergroundEntities(player);
                }
                refreshNearbyChunks(player);
            } else {
                if (hideEntities) {
                    restoreUndergroundEntities(player);
                }
                refreshNearbyChunks(player);
            }
        }
    }

    public void hideUndergroundEntities(Player player, Location center) {
        if (!player.isOnline() || !hideEntities || center == null || center.getWorld() == null) return;
        World world = center.getWorld();
        if (world.getEnvironment() != World.Environment.NORMAL) return;

        Collection<Entity> nearby = world.getNearbyEntities(center, 128, 128, 128);
        Set<UUID> set = hiddenEntities.computeIfAbsent(player.getUniqueId(), k -> ConcurrentHashMap.newKeySet());

        for (Entity entity : nearby) {
            if (entity.equals(player)) continue;
            if (entity instanceof org.bukkit.entity.Projectile) continue;
            if (entity.getLocation().getY() <= lowerY) {
                if (player.canSee(entity)) {
                    player.hideEntity(plugin, entity);
                    set.add(entity.getUniqueId());
                }
            }
        }
    }

    public void hideUndergroundEntities(Player player) {
        hideUndergroundEntities(player, player.getLocation());
    }

    public void restoreUndergroundEntities(Player player) {
        Set<UUID> set = hiddenEntities.remove(player.getUniqueId());
        if (set != null && !set.isEmpty()) {
            for (UUID entityId : set) {
                Entity entity = Bukkit.getEntity(entityId);
                if (entity != null && entity.isValid() && entity.getWorld().equals(player.getWorld())) {
                    player.showEntity(plugin, entity);
                }
            }
        }
    }

    public void handlePlayerTeleport(Player player, Location from, Location to) {
        if (!enabled || player == null || !player.isOnline()) return;
        if (plugin.isWorldDisabled(to.getWorld().getName())) return;

        if (to.getWorld().getEnvironment() != World.Environment.NORMAL) {
            playerAboveState.put(player.getUniqueId(), false);
            if (hideEntities) {
                restoreUndergroundEntities(player);
            }
            return;
        }

        boolean toAbove = to.getY() > upperY;
        playerAboveState.put(player.getUniqueId(), toAbove);

        if (toAbove) {
            if (hideEntities) {
                hideUndergroundEntities(player, to);
            }
        } else {
            if (hideEntities) {
                restoreUndergroundEntities(player);
            }
        }

        refreshNearbyChunks(player, to);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && player.getWorld().equals(to.getWorld())) {
                refreshNearbyChunks(player, to);
                if (toAbove && hideEntities) {
                    hideUndergroundEntities(player, to);
                }
            }
        }, 3L);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && player.getWorld().equals(to.getWorld())) {
                refreshNearbyChunks(player, to);
            }
        }, 7L);
    }

    public void refreshNearbyChunks(Player player, Location center) {
        if (player == null || center == null || center.getWorld() == null) return;
        World world = center.getWorld();
        int centerChunkX = center.getBlockX() >> 4;
        int centerChunkZ = center.getBlockZ() >> 4;

        int viewDist = player.getClientViewDistance();
        if (viewDist <= 0) viewDist = player.getViewDistance();
        if (viewDist <= 0) viewDist = world.getViewDistance();
        if (viewDist <= 0) viewDist = 10;

        int radius = Math.max(refreshRadius, viewDist);
        radius = Math.min(16, radius);

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int cx = centerChunkX + dx;
                int cz = centerChunkZ + dz;
                if (world.isChunkLoaded(cx, cz)) {
                    world.refreshChunk(cx, cz);
                }
            }
        }
    }

    public void refreshNearbyChunks(Player player) {
        refreshNearbyChunks(player, player.getLocation());
    }

    public void removePlayerData(UUID uuid) {
        playerAboveState.remove(uuid);
        hiddenEntities.remove(uuid);
    }

    public void clearAll() {
        if (scanTask != null) {
            scanTask.cancel();
            scanTask = null;
        }
        playerAboveState.clear();
        hiddenEntities.clear();
    }
}
