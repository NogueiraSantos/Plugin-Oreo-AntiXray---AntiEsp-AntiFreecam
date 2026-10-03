package me.theus.oreoAntiXray.managers;

import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.type.StateType;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import me.theus.oreoAntiXray.OreoAntiXray;
import me.theus.oreoAntiXray.models.BlockPosition;
import me.theus.oreoAntiXray.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class OreObfuscatorManager {

    private final OreoAntiXray plugin;
    private final Set<Material> targetOreMaterials = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<StateType> targetOreStateTypes = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final Map<UUID, Set<BlockPosition>> playerRevealedOres = new ConcurrentHashMap<>();
    private final Map<UUID, Set<BlockPosition>> playerHiddenOres = new ConcurrentHashMap<>();

    private boolean enabled;
    private boolean hideBehindWalls;
    private int proximityRadius;
    private int deepslateYLevel;

    private BlockData stoneBlockData;
    private BlockData deepslateBlockData;
    private BlockData netherrackBlockData;

    public OreObfuscatorManager(OreoAntiXray plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        this.enabled = plugin.getConfig().getBoolean("ORE-OBFUSCATOR.ENABLED", true);
        this.hideBehindWalls = plugin.getConfig().getBoolean("ORE-OBFUSCATOR.HIDE-BEHIND-WALLS", true);
        this.proximityRadius = plugin.getConfig().getInt("ORE-OBFUSCATOR.PROXIMITY-RADIUS", 8);
        this.deepslateYLevel = plugin.getConfig().getInt("ORE-OBFUSCATOR.DEEPSLATE-Y-LEVEL", 0);

        this.stoneBlockData = Bukkit.createBlockData(Material.STONE);
        this.deepslateBlockData = Bukkit.createBlockData(Material.DEEPSLATE);
        this.netherrackBlockData = Bukkit.createBlockData(Material.NETHERRACK);

        targetOreMaterials.clear();
        targetOreStateTypes.clear();

        List<String> oreNames = plugin.getConfig().getStringList("ORE-OBFUSCATOR.ORES");
        for (String name : oreNames) {
            Material mat = Material.matchMaterial(name);
            if (mat != null) {
                targetOreMaterials.add(mat);
            }
        }

        initOreStateTypes();

        playerRevealedOres.clear();
        playerHiddenOres.clear();
    }

    private void initOreStateTypes() {
        addStateType(StateTypes.DIAMOND_ORE);
        addStateType(StateTypes.DEEPSLATE_DIAMOND_ORE);
        addStateType(StateTypes.ANCIENT_DEBRIS);
        addStateType(StateTypes.GOLD_ORE);
        addStateType(StateTypes.DEEPSLATE_GOLD_ORE);
        addStateType(StateTypes.IRON_ORE);
        addStateType(StateTypes.DEEPSLATE_IRON_ORE);
        addStateType(StateTypes.EMERALD_ORE);
        addStateType(StateTypes.DEEPSLATE_EMERALD_ORE);
        addStateType(StateTypes.LAPIS_ORE);
        addStateType(StateTypes.DEEPSLATE_LAPIS_ORE);
        addStateType(StateTypes.REDSTONE_ORE);
        addStateType(StateTypes.DEEPSLATE_REDSTONE_ORE);
        addStateType(StateTypes.COPPER_ORE);
        addStateType(StateTypes.DEEPSLATE_COPPER_ORE);
        addStateType(StateTypes.COAL_ORE);
        addStateType(StateTypes.DEEPSLATE_COAL_ORE);
        addStateType(StateTypes.NETHER_GOLD_ORE);
        addStateType(StateTypes.NETHER_QUARTZ_ORE);
    }

    private void addStateType(StateType stateType) {
        if (stateType != null) {
            targetOreStateTypes.add(stateType);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isHideBehindWalls() {
        return hideBehindWalls;
    }

    public boolean isOre(Material material) {
        return targetOreMaterials.contains(material);
    }

    public boolean isOre(StateType stateType) {
        return targetOreStateTypes.contains(stateType);
    }

    public BlockData getMaskedBlockData(Material oreMaterial, int y) {
        if (oreMaterial == Material.ANCIENT_DEBRIS || oreMaterial == Material.NETHER_GOLD_ORE || oreMaterial == Material.NETHER_QUARTZ_ORE) {
            return netherrackBlockData;
        }
        if (y <= deepslateYLevel || oreMaterial.name().startsWith("DEEPSLATE_")) {
            return deepslateBlockData;
        }
        return stoneBlockData;
    }

    public WrappedBlockState getMaskedState(StateType oreStateType, int y) {
        if (oreStateType == StateTypes.ANCIENT_DEBRIS || oreStateType == StateTypes.NETHER_GOLD_ORE || oreStateType == StateTypes.NETHER_QUARTZ_ORE) {
            return StateTypes.NETHERRACK.createBlockState();
        }
        if (y <= deepslateYLevel || oreStateType.getName().contains("deepslate")) {
            return StateTypes.DEEPSLATE.createBlockState();
        }
        return StateTypes.STONE.createBlockState();
    }

    public boolean canPlayerSeeOre(Location eye, Block oreBlock) {
        if (oreBlock == null) return false;
        if (!isExposedToAirOrFluid(oreBlock)) {
            return false;
        }
        return Utils.canSeeBlock(eye, oreBlock, true);
    }

    public void markHidden(UUID playerUuid, BlockPosition pos) {
        playerHiddenOres.computeIfAbsent(playerUuid, k -> ConcurrentHashMap.newKeySet()).add(pos);
        Set<BlockPosition> revealed = playerRevealedOres.get(playerUuid);
        if (revealed != null) {
            revealed.remove(pos);
        }
    }

    public void markRevealed(UUID playerUuid, BlockPosition pos) {
        playerRevealedOres.computeIfAbsent(playerUuid, k -> ConcurrentHashMap.newKeySet()).add(pos);
        Set<BlockPosition> hidden = playerHiddenOres.get(playerUuid);
        if (hidden != null) {
            hidden.remove(pos);
        }
    }

    public boolean isExposedToAirOrFluid(Block block) {
        if (block == null) return false;
        for (BlockFace face : BlockFace.values()) {
            if (face.isCartesian()) {
                Block rel = block.getRelative(face);
                Material mat = rel.getType();
                if (mat.isAir() || rel.isPassable() || !mat.isOccluding() || mat == Material.WATER || mat == Material.LAVA || mat == Material.SNOW || mat == Material.MOSS_CARPET) {
                    return true;
                }
            }
        }
        return false;
    }

    public void updatePlayerProximity(Player player) {
        if (!enabled || player == null || !player.isOnline()) return;
        if (plugin.isWorldDisabled(player.getWorld().getName())) return;

        Location eye = player.getEyeLocation();
        String worldName = eye.getWorld().getName();

        Set<BlockPosition> hiddenSet = playerHiddenOres.computeIfAbsent(player.getUniqueId(), k -> ConcurrentHashMap.newKeySet());
        Set<BlockPosition> revealedSet = playerRevealedOres.computeIfAbsent(player.getUniqueId(), k -> ConcurrentHashMap.newKeySet());
        Set<BlockPosition> currentlyInRange = new HashSet<>();

        boolean freecamActive = plugin.getAntiFreecamManager().shouldObfuscateUnderground(player);
        int lowerY = plugin.getAntiFreecamManager().getLowerY();

        int radius = proximityRadius;
        int minX = eye.getBlockX() - radius;
        int maxX = eye.getBlockX() + radius;
        int minY = Math.max(eye.getWorld().getMinHeight(), eye.getBlockY() - radius);
        int maxY = Math.min(eye.getWorld().getMaxHeight(), eye.getBlockY() + radius);
        int minZ = eye.getBlockZ() - radius;
        int maxZ = eye.getBlockZ() + radius;

        double radiusSq = radius * radius;

        Block targetBlock = null;
        try {
            targetBlock = player.getTargetBlockExact(radius);
        } catch (Throwable ignored) {}

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                if (freecamActive && y <= lowerY) {
                    continue;
                }
                for (int z = minZ; z <= maxZ; z++) {
                    double dx = (x + 0.5) - eye.getX();
                    double dy = (y + 0.5) - eye.getY();
                    double dz = (z + 0.5) - eye.getZ();
                    double distSq = dx * dx + dy * dy + dz * dz;

                    if (distSq <= radiusSq) {
                        Block block = eye.getWorld().getBlockAt(x, y, z);
                        if (isOre(block.getType())) {
                            BlockPosition pos = new BlockPosition(worldName, x, y, z);
                            currentlyInRange.add(pos);

                            boolean canSee;
                            if (targetBlock != null && block.equals(targetBlock)) {
                                canSee = true;
                            } else {
                                canSee = hideBehindWalls ? canPlayerSeeOre(eye, block) : true;
                            }

                            if (canSee) {
                                if (hiddenSet.contains(pos) || !revealedSet.contains(pos)) {
                                    player.sendBlockChange(block.getLocation(), block.getBlockData());
                                    hiddenSet.remove(pos);
                                    revealedSet.add(pos);
                                }
                            } else {
                                if (revealedSet.contains(pos) || !hiddenSet.contains(pos)) {
                                    player.sendBlockChange(block.getLocation(), getMaskedBlockData(block.getType(), block.getY()));
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
                    Block block = loc.getBlock();
                    if (isOre(block.getType())) {
                        player.sendBlockChange(loc, getMaskedBlockData(block.getType(), block.getY()));
                    }
                }
            }
        }
    }

    public void revealNearbyOres(Location brokenLocation) {
        if (!enabled || brokenLocation == null) return;
        Block center = brokenLocation.getBlock();

        List<Block> oresToReveal = new ArrayList<>();
        for (BlockFace face : BlockFace.values()) {
            if (face.isCartesian()) {
                Block rel = center.getRelative(face);
                if (isOre(rel.getType())) {
                    oresToReveal.add(rel);
                }
            }
        }

        if (oresToReveal.isEmpty()) return;

        double distSq = (proximityRadius + 4) * (proximityRadius + 4);
        for (Player p : brokenLocation.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(brokenLocation) <= distSq) {
                Set<BlockPosition> hiddenSet = playerHiddenOres.get(p.getUniqueId());
                Set<BlockPosition> revealedSet = playerRevealedOres.get(p.getUniqueId());

                for (Block ore : oresToReveal) {
                    p.sendBlockChange(ore.getLocation(), ore.getBlockData());
                    BlockPosition pos = BlockPosition.fromLocation(ore.getLocation());
                    if (hiddenSet != null) hiddenSet.remove(pos);
                    if (revealedSet != null) revealedSet.add(pos);
                }
            }
        }
    }

    public void restoreForPlayer(Player player) {
        if (player == null) return;
        Set<BlockPosition> hidden = playerHiddenOres.remove(player.getUniqueId());
        playerRevealedOres.remove(player.getUniqueId());

        if (hidden != null) {
            for (BlockPosition pos : hidden) {
                Location loc = pos.toLocation();
                if (loc != null && loc.getWorld() != null && loc.getWorld().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
                    Block block = loc.getBlock();
                    if (isOre(block.getType())) {
                        player.sendBlockChange(loc, block.getBlockData());
                    }
                }
            }
        }
    }

    public void removePlayerData(UUID uuid) {
        playerRevealedOres.remove(uuid);
        playerHiddenOres.remove(uuid);
    }

    public void clearAll() {
        playerRevealedOres.clear();
        playerHiddenOres.clear();
    }
}
