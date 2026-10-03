package me.theus.oreoAntiXray.managers;

import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.type.StateType;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import me.theus.oreoAntiXray.OreoAntiXray;
import me.theus.oreoAntiXray.models.AlertLog;
import me.theus.oreoAntiXray.models.BlockPosition;
import me.theus.oreoAntiXray.utils.Utils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FakeOreManager {

    private final OreoAntiXray plugin;
    private final Map<String, Map<Long, Set<BlockPosition>>> worldChunkFakeOres = new ConcurrentHashMap<>();

    private boolean enabled;
    private int trapsPerChunk;
    private int minY;
    private int maxY;
    private int minDistanceFromAir;
    private String alertSound;

    private boolean netherEnabled;
    private int netherTrapsPerChunk;
    private int netherMinY;
    private int netherMaxY;

    public FakeOreManager(OreoAntiXray plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        this.enabled = plugin.getConfig().getBoolean("FAKE-ORES.ENABLED", true);
        this.trapsPerChunk = plugin.getConfig().getInt("FAKE-ORES.TRAPS-PER-CHUNK", 3);
        this.minY = plugin.getConfig().getInt("FAKE-ORES.OVERWORLD.MIN-Y", plugin.getConfig().getInt("FAKE-ORES.MIN-Y", -58));
        this.maxY = plugin.getConfig().getInt("FAKE-ORES.OVERWORLD.MAX-Y", plugin.getConfig().getInt("FAKE-ORES.MAX-Y", 30));
        this.minDistanceFromAir = plugin.getConfig().getInt("FAKE-ORES.MIN-DISTANCE-FROM-AIR", 3);
        this.alertSound = plugin.getConfig().getString("FAKE-ORES.ALERT-SOUND", "ENTITY_EXPERIENCE_ORB_PICKUP");

        this.netherEnabled = plugin.getConfig().getBoolean("FAKE-ORES.NETHER.ENABLED", true);
        this.netherTrapsPerChunk = plugin.getConfig().getInt("FAKE-ORES.NETHER.TRAPS-PER-CHUNK", 3);
        this.netherMinY = plugin.getConfig().getInt("FAKE-ORES.NETHER.MIN-Y", 8);
        this.netherMaxY = plugin.getConfig().getInt("FAKE-ORES.NETHER.MAX-Y", 118);

        worldChunkFakeOres.clear();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isNetherWorld(String worldName) {
        if (worldName == null) return false;
        World w = Bukkit.getWorld(worldName);
        if (w != null) {
            return w.getEnvironment() == World.Environment.NETHER;
        }
        String lower = worldName.toLowerCase();
        return lower.contains("nether") || lower.endsWith("_nether");
    }

    private long getChunkKey(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    public Set<BlockPosition> getOrCreateChunkFakeOres(String worldName, int chunkX, int chunkZ) {
        if (!enabled) return Collections.emptySet();

        Map<Long, Set<BlockPosition>> chunkMap = worldChunkFakeOres.computeIfAbsent(worldName, k -> new ConcurrentHashMap<>());
        long key = getChunkKey(chunkX, chunkZ);

        return chunkMap.computeIfAbsent(key, k -> generateFakeOresForChunk(worldName, chunkX, chunkZ));
    }

    private Set<BlockPosition> generateFakeOresForChunk(String worldName, int chunkX, int chunkZ) {
        Set<BlockPosition> set = Collections.newSetFromMap(new ConcurrentHashMap<>());

        boolean isNether = isNetherWorld(worldName);
        if (isNether && !netherEnabled) {
            return set;
        }

        long seed = (long) chunkX * 341873128712L + (long) chunkZ * 132897987541L + worldName.hashCode();
        Random random = new Random(seed);

        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;

        int currentMinY = isNether ? netherMinY : minY;
        int currentMaxY = isNether ? netherMaxY : maxY;
        int traps = isNether ? netherTrapsPerChunk : trapsPerChunk;

        int yRange = Math.max(1, (currentMaxY - currentMinY));

        for (int i = 0; i < traps; i++) {
            int offsetX = 2 + random.nextInt(12);
            int offsetZ = 2 + random.nextInt(12);
            int y = currentMinY + random.nextInt(yRange);

            int x = baseX + offsetX;
            int z = baseZ + offsetZ;

            set.add(new BlockPosition(worldName, x, y, z));
        }

        return set;
    }

    public boolean isFakeOre(Location loc) {
        if (!enabled || loc == null || loc.getWorld() == null) return false;

        String worldName = loc.getWorld().getName();
        int chunkX = loc.getBlockX() >> 4;
        int chunkZ = loc.getBlockZ() >> 4;

        Map<Long, Set<BlockPosition>> chunkMap = worldChunkFakeOres.get(worldName);
        if (chunkMap == null) return false;

        Set<BlockPosition> set = chunkMap.get(getChunkKey(chunkX, chunkZ));
        if (set == null || set.isEmpty()) return false;

        BlockPosition pos = new BlockPosition(worldName, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        return set.contains(pos);
    }

    public boolean isHostBlock(StateType type, boolean isNether) {
        if (type == null) return false;
        if (isNether) {
            return type == StateTypes.NETHERRACK
                    || type == StateTypes.BLACKSTONE
                    || type == StateTypes.BASALT;
        } else {
            return type == StateTypes.STONE
                    || type == StateTypes.DEEPSLATE
                    || type == StateTypes.ANDESITE
                    || type == StateTypes.DIORITE
                    || type == StateTypes.GRANITE
                    || type == StateTypes.TUFF;
        }
    }

    public Material getFakeOreType(String worldName, int x, int y, int z) {
        boolean isNether = isNetherWorld(worldName);

        long seed = ((long) x * 3129871L) ^ ((long) z * 612871L) ^ ((long) y * 13L) ^ worldName.hashCode();
        Random r = new Random(seed);

        if (isNether) {
            int roll = r.nextInt(100);
            if (roll < 75) {
                return Material.ANCIENT_DEBRIS;
            } else {
                return Material.NETHER_GOLD_ORE;
            }
        } else {
            int roll = r.nextInt(100);
            if (y <= 0) {
                if (roll < 65) return Material.DEEPSLATE_DIAMOND_ORE;
                else if (roll < 85) return Material.DEEPSLATE_EMERALD_ORE;
                else return Material.DEEPSLATE_GOLD_ORE;
            } else {
                if (roll < 65) return Material.DIAMOND_ORE;
                else if (roll < 85) return Material.EMERALD_ORE;
                else return Material.GOLD_ORE;
            }
        }
    }

    public WrappedBlockState getFakeOreState(String worldName, int x, int y, int z) {
        Material mat = getFakeOreType(worldName, x, y, z);
        return switch (mat) {
            case ANCIENT_DEBRIS -> StateTypes.ANCIENT_DEBRIS.createBlockState();
            case NETHER_GOLD_ORE -> StateTypes.NETHER_GOLD_ORE.createBlockState();
            case NETHER_QUARTZ_ORE -> StateTypes.NETHER_QUARTZ_ORE.createBlockState();
            case DEEPSLATE_DIAMOND_ORE -> StateTypes.DEEPSLATE_DIAMOND_ORE.createBlockState();
            case DIAMOND_ORE -> StateTypes.DIAMOND_ORE.createBlockState();
            case DEEPSLATE_EMERALD_ORE -> StateTypes.DEEPSLATE_EMERALD_ORE.createBlockState();
            case EMERALD_ORE -> StateTypes.EMERALD_ORE.createBlockState();
            case DEEPSLATE_GOLD_ORE -> StateTypes.DEEPSLATE_GOLD_ORE.createBlockState();
            case GOLD_ORE -> StateTypes.GOLD_ORE.createBlockState();
            default -> (y <= 0) ? StateTypes.DEEPSLATE_DIAMOND_ORE.createBlockState() : StateTypes.DIAMOND_ORE.createBlockState();
        };
    }

    public String formatOreName(Material mat) {
        if (mat == null) return "Minério";
        return switch (mat) {
            case ANCIENT_DEBRIS -> "Ancient Debris";
            case NETHER_GOLD_ORE -> "Ouro do Nether";
            case NETHER_QUARTZ_ORE -> "Quartzo do Nether";
            case DEEPSLATE_DIAMOND_ORE -> "Diamante (Deepslate)";
            case DIAMOND_ORE -> "Diamante";
            case DEEPSLATE_EMERALD_ORE -> "Esmeralda (Deepslate)";
            case EMERALD_ORE -> "Esmeralda";
            case DEEPSLATE_GOLD_ORE -> "Ouro (Deepslate)";
            case GOLD_ORE -> "Ouro";
            default -> mat.name();
        };
    }

    public void handleFakeOreMined(Player player, Location loc) {
        if (player == null || loc == null || loc.getWorld() == null) return;

        String worldName = loc.getWorld().getName();
        int chunkX = loc.getBlockX() >> 4;
        int chunkZ = loc.getBlockZ() >> 4;

        Map<Long, Set<BlockPosition>> chunkMap = worldChunkFakeOres.get(worldName);
        if (chunkMap != null) {
            Set<BlockPosition> set = chunkMap.get(getChunkKey(chunkX, chunkZ));
            if (set != null) {
                set.remove(new BlockPosition(worldName, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
            }
        }

        Material oreType = getFakeOreType(worldName, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        String oreNameFormatted = formatOreName(oreType);

        plugin.getLogger().warning("ALERTA: Atividade suspeita de X-Ray detectada de " + player.getName()
                + " em (" + worldName + ": " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ()
                + ") - Minerou Fake Ore: " + oreType.name());

        Component alertComponent = Utils.createClickableAlert(player.getName(), oreNameFormatted, loc);

        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("oreo.alerts") || staff.isOp()) {
                staff.sendMessage(alertComponent);
                Utils.playSound(staff, alertSound);
            }
        }

        AlertLog log = new AlertLog(
                player.getUniqueId(),
                player.getName(),
                worldName,
                loc.getBlockX(),
                loc.getBlockY(),
                loc.getBlockZ(),
                System.currentTimeMillis(),
                "Minerou Fake Ore (" + oreType.name() + ")"
        );
        plugin.getDatabase().logAlertAsync(log);
    }

    public void onChunkUnload(String worldName, int chunkX, int chunkZ) {
        Map<Long, Set<BlockPosition>> chunkMap = worldChunkFakeOres.get(worldName);
        if (chunkMap != null) {
            chunkMap.remove(getChunkKey(chunkX, chunkZ));
        }
    }

    public void clearAll() {
        worldChunkFakeOres.clear();
    }
}
