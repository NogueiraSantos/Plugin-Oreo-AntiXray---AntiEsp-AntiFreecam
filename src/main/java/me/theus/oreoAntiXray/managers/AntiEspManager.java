package me.theus.oreoAntiXray.managers;

import com.github.retrooper.packetevents.protocol.world.states.type.StateType;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import me.theus.oreoAntiXray.OreoAntiXray;
import me.theus.oreoAntiXray.models.BlockPosition;
import me.theus.oreoAntiXray.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AntiEspManager {

    private final OreoAntiXray plugin;
    private final Set<Material> targetMaterials = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<StateType> targetStateTypes = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<UUID, Set<BlockPosition>> playerHiddenEspBlocks = new ConcurrentHashMap<>();
    private final Map<UUID, Set<BlockPosition>> playerRevealedEspBlocks = new ConcurrentHashMap<>();

    private boolean enabled;
    private double maxDistance;
    private double proximityBypass;
    private BlockData airBlockData;

    public AntiEspManager(OreoAntiXray plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        this.enabled = plugin.getConfig().getBoolean("ANTI-ESP.ENABLED", true);
        this.maxDistance = plugin.getConfig().getDouble("ANTI-ESP.MAX-DISTANCE", 32.0);
        this.proximityBypass = plugin.getConfig().getDouble("ANTI-ESP.PROXIMITY-BYPASS", 2.5);
        this.airBlockData = Bukkit.createBlockData(Material.AIR);

        targetMaterials.clear();
        targetStateTypes.clear();

        List<String> blockNames = plugin.getConfig().getStringList("ANTI-ESP.BLOCKS");
        for (String name : blockNames) {
            Material mat = Material.matchMaterial(name);
            if (mat != null) {
                targetMaterials.add(mat);
            }
        }

        for (Material m : Material.values()) {
            if (m.name().endsWith("_SHULKER_BOX") || m == Material.SHULKER_BOX) {
                targetMaterials.add(m);
            }
        }

        initStateTypes();
    }

    private void initStateTypes() {
        addStateType(StateTypes.CHEST);
        addStateType(StateTypes.TRAPPED_CHEST);
        addStateType(StateTypes.BARREL);
        addStateType(StateTypes.SPAWNER);
        addStateType(StateTypes.TRIAL_SPAWNER);
        addStateType(StateTypes.SHULKER_BOX);
    }

    private void addStateType(StateType stateType) {
        if (stateType != null) {
            targetStateTypes.add(stateType);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isTarget(Material mat) {
        return targetMaterials.contains(mat);
    }

    public boolean isTarget(StateType type) {
        if (type == null) return false;
        if (targetStateTypes.contains(type)) return true;
        return type.getName().contains("shulker_box") || type.getName().contains("chest") || type.getName().contains("spawner");
    }

    public void markHidden(UUID playerUuid, BlockPosition pos) {
        playerHiddenEspBlocks.computeIfAbsent(playerUuid, k -> ConcurrentHashMap.newKeySet()).add(pos);
        Set<BlockPosition> revealed = playerRevealedEspBlocks.get(playerUuid);
        if (revealed != null) {
            revealed.remove(pos);
        }
    }

    public void markRevealed(UUID playerUuid, BlockPosition pos) {
        playerRevealedEspBlocks.computeIfAbsent(playerUuid, k -> ConcurrentHashMap.newKeySet()).add(pos);
        Set<BlockPosition> hidden = playerHiddenEspBlocks.get(playerUuid);
        if (hidden != null) {
            hidden.remove(pos);
        }
    }

    public void updatePlayerEsp(Player player) {
        if (!enabled || player == null || !player.isOnline()) return;
        if (plugin.isWorldDisabled(player.getWorld().getName())) return;

        Location eye = player.getEyeLocation();
        String worldName = eye.getWorld().getName();
        Set<BlockPosition> hiddenSet = playerHiddenEspBlocks.computeIfAbsent(player.getUniqueId(), k -> ConcurrentHashMap.newKeySet());
        Set<BlockPosition> revealedSet = playerRevealedEspBlocks.computeIfAbsent(player.getUniqueId(), k -> ConcurrentHashMap.newKeySet());
        Set<BlockPosition> currentlyInRange = new HashSet<>();

        int radius = (int) Math.min(maxDistance, 24);
        int minX = eye.getBlockX() - radius;
        int maxX = eye.getBlockX() + radius;
        int minY = Math.max(eye.getWorld().getMinHeight(), eye.getBlockY() - radius);
        int maxY = Math.min(eye.getWorld().getMaxHeight(), eye.getBlockY() + radius);
        int minZ = eye.getBlockZ() - radius;
        int maxZ = eye.getBlockZ() + radius;

        double maxDistSq = maxDistance * maxDistance;
        double bypassDistSq = proximityBypass * proximityBypass;

        Block targetBlock = null;
        try {
            targetBlock = player.getTargetBlockExact(radius);
        } catch (Throwable ignored) {}

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    double dx = (x + 0.5) - eye.getX();
                    double dy = (y + 0.5) - eye.getY();
                    double dz = (z + 0.5) - eye.getZ();
                    double distSq = dx * dx + dy * dy + dz * dz;

                    if (distSq <= maxDistSq) {
                        Block block = eye.getWorld().getBlockAt(x, y, z);
                        if (isTarget(block.getType())) {
                            BlockPosition pos = new BlockPosition(worldName, x, y, z);
                            currentlyInRange.add(pos);

                            boolean canSee;
                            if (targetBlock != null && block.equals(targetBlock)) {
                                canSee = true;
                            } else {
                                canSee = (distSq <= bypassDistSq) || Utils.canSeeBlock(eye, block, false);
                            }

                            if (canSee) {
                                if (hiddenSet.contains(pos) || !revealedSet.contains(pos)) {
                                    player.sendBlockChange(block.getLocation(), block.getBlockData());
                                    hiddenSet.remove(pos);
                                    revealedSet.add(pos);
                                }
                            } else {
                                if (revealedSet.contains(pos) || !hiddenSet.contains(pos)) {
                                    player.sendBlockChange(block.getLocation(), airBlockData);
                                    revealedSet.remove(pos);
                                    hiddenSet.add(pos);
                                }
                            }
                        }
                    }
                }
            }
        }

        Iterator<BlockPosition> it = revealedSet.iterator();
        while (it.hasNext()) {
            BlockPosition pos = it.next();
            if (!currentlyInRange.contains(pos)) {
                it.remove();
                hiddenSet.add(pos);
                Location loc = pos.toLocation();
                if (loc != null && loc.getWorld() != null && loc.getWorld().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
                    Block b = loc.getBlock();
                    if (isTarget(b.getType())) {
                        player.sendBlockChange(loc, airBlockData);
                    }
                }
            }
        }
    }

    public void restoreForPlayer(Player player) {
        if (player == null) return;
        playerRevealedEspBlocks.remove(player.getUniqueId());
        Set<BlockPosition> hiddenSet = playerHiddenEspBlocks.remove(player.getUniqueId());
        if (hiddenSet != null) {
            for (BlockPosition pos : hiddenSet) {
                Location loc = pos.toLocation();
                if (loc != null && loc.getWorld() != null && loc.getWorld().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
                    Block b = loc.getBlock();
                    if (isTarget(b.getType())) {
                        player.sendBlockChange(loc, b.getBlockData());
                    }
                }
            }
        }
    }

    public void removePlayerData(UUID uuid) {
        playerHiddenEspBlocks.remove(uuid);
        playerRevealedEspBlocks.remove(uuid);
    }

    public void clearAll() {
        playerHiddenEspBlocks.clear();
        playerRevealedEspBlocks.clear();
    }
}
