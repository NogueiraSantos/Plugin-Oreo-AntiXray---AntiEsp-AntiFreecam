package me.theus.oreoAntiXray.listeners;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk;
import com.github.retrooper.packetevents.protocol.world.chunk.Column;
import com.github.retrooper.packetevents.protocol.world.chunk.TileEntity;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.type.StateType;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockEntityData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMultiBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnExperienceOrb;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnLivingEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnPlayer;
import me.theus.oreoAntiXray.OreoAntiXray;
import me.theus.oreoAntiXray.models.BlockPosition;
import me.theus.oreoAntiXray.utils.Utils;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class PacketEventsListener implements PacketListener {

    private final OreoAntiXray plugin;

    public PacketEventsListener(OreoAntiXray plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();

        if (event.getPacketType() == PacketType.Play.Server.CHUNK_DATA) {
            handleChunkData(event, player);
        } else if (event.getPacketType() == PacketType.Play.Server.BLOCK_CHANGE) {
            handleBlockChange(event, player);
        } else if (event.getPacketType() == PacketType.Play.Server.MULTI_BLOCK_CHANGE) {
            handleMultiBlockChange(event, player);
        } else if (event.getPacketType() == PacketType.Play.Server.BLOCK_ENTITY_DATA) {
            handleBlockEntityData(event, player);
        } else if (event.getPacketType() == PacketType.Play.Server.SPAWN_ENTITY) {
            handleSpawnEntity(event, player);
        } else if (event.getPacketType() == PacketType.Play.Server.SPAWN_LIVING_ENTITY) {
            handleSpawnLivingEntity(event, player);
        } else if (event.getPacketType() == PacketType.Play.Server.SPAWN_PLAYER) {
            handleSpawnPlayer(event, player);
        } else if (event.getPacketType() == PacketType.Play.Server.SPAWN_EXPERIENCE_ORB) {
            handleSpawnExperienceOrb(event, player);
        }
    }

    private void handleChunkData(PacketSendEvent event, Player player) {
        World world = player.getWorld();
        if (plugin.isWorldDisabled(world.getName())) return;

        WrapperPlayServerChunkData chunkData = new WrapperPlayServerChunkData(event);
        Column column = chunkData.getColumn();
        if (column == null) return;

        BaseChunk[] chunks = column.getChunks();
        if (chunks == null || chunks.length == 0) return;

        int chunkX = column.getX();
        int chunkZ = column.getZ();

        Location eye = player.getEyeLocation();
        double eyeX = eye.getX(), eyeY = eye.getY(), eyeZ = eye.getZ();

        boolean oreObfEnabled = plugin.getOreObfuscatorManager().isEnabled();
        boolean fakeOreEnabled = plugin.getFakeOreManager().isEnabled();
        boolean freecamActive = plugin.getAntiFreecamManager().shouldObfuscateUnderground(player);
        boolean espEnabled = plugin.getAntiEspManager().isEnabled();

        int minHeight = world.getMinHeight();
        double proxRadius = plugin.getConfig().getInt("ORE-OBFUSCATOR.PROXIMITY-RADIUS", 8);
        double proxRadiusSq = proxRadius * proxRadius;
        int lowerY = plugin.getAntiFreecamManager().getLowerY();

        Set<BlockPosition> fakeOres = (fakeOreEnabled && !freecamActive)
                ? plugin.getFakeOreManager().getOrCreateChunkFakeOres(world.getName(), chunkX, chunkZ)
                : null;

        for (int i = 0; i < chunks.length; i++) {
            BaseChunk subchunk = chunks[i];
            int subMinY = minHeight + (i * 16);
            int subMaxY = subMinY + 15;

            if (subchunk == null) {
                if (freecamActive && subMinY <= lowerY) {
                    subchunk = BaseChunk.create();
                    chunks[i] = subchunk;
                } else {
                    continue;
                }
            } else if (subchunk.isEmpty()) {
                if (!freecamActive || subMinY > lowerY) {
                    continue;
                }
            }

            for (int y = 0; y < 16; y++) {
                int worldY = subMinY + y;
                for (int x = 0; x < 16; x++) {
                    int worldX = (chunkX << 4) + x;
                    for (int z = 0; z < 16; z++) {
                        int worldZ = (chunkZ << 4) + z;

                        if (freecamActive && worldY <= lowerY) {
                            subchunk.set(x, y, z, plugin.getAntiFreecamManager().getSolidReplacement(worldY));
                            continue;
                        }

                        WrappedBlockState state = subchunk.get(x, y, z);
                        if (state == null) continue;
                        StateType type = state.getType();

                        if (oreObfEnabled && plugin.getOreObfuscatorManager().isOre(type)) {
                            double distSq = (worldX + 0.5 - eyeX) * (worldX + 0.5 - eyeX)
                                    + (worldY + 0.5 - eyeY) * (worldY + 0.5 - eyeY)
                                    + (worldZ + 0.5 - eyeZ) * (worldZ + 0.5 - eyeZ);

                            BlockPosition pos = new BlockPosition(world.getName(), worldX, worldY, worldZ);
                            boolean canSee = false;
                            if (distSq <= proxRadiusSq) {
                                Block worldBlock = world.getBlockAt(worldX, worldY, worldZ);
                                canSee = plugin.getOreObfuscatorManager().canPlayerSeeOre(eye, worldBlock);
                            }

                            if (!canSee) {
                                subchunk.set(x, y, z, plugin.getOreObfuscatorManager().getMaskedState(type, worldY));
                                plugin.getOreObfuscatorManager().markHidden(player.getUniqueId(), pos);
                            } else {
                                plugin.getOreObfuscatorManager().markRevealed(player.getUniqueId(), pos);
                            }
                        }
                    }
                }
            }

            if (fakeOres != null && !fakeOres.isEmpty()) {
                boolean isNether = plugin.getFakeOreManager().isNetherWorld(world.getName());
                for (BlockPosition fake : fakeOres) {
                    if (fake.getY() >= subMinY && fake.getY() <= subMaxY) {
                        int lx = fake.getX() & 15;
                        int ly = fake.getY() - subMinY;
                        int lz = fake.getZ() & 15;
                        WrappedBlockState current = subchunk.get(lx, ly, lz);
                        if (current != null && plugin.getFakeOreManager().isHostBlock(current.getType(), isNether)) {
                            subchunk.set(lx, ly, lz, plugin.getFakeOreManager().getFakeOreState(world.getName(), fake.getX(), fake.getY(), fake.getZ()));
                        }
                    }
                }
            }
        }

        TileEntity[] tileEntities = column.getTileEntities();
        List<TileEntity> keptTiles = new ArrayList<>();
        if (tileEntities != null && tileEntities.length > 0) {
            double bypassDist = plugin.getConfig().getDouble("ANTI-ESP.PROXIMITY-BYPASS", 2.5);
            double bypassDistSq = bypassDist * bypassDist;

            for (TileEntity te : tileEntities) {
                int worldY = te.getY();

                if (freecamActive && worldY <= lowerY) {
                    continue;
                }

                if (espEnabled) {
                    int worldX = (chunkX << 4) + te.getX();
                    int worldZ = (chunkZ << 4) + te.getZ();

                    Block block = world.getBlockAt(worldX, worldY, worldZ);
                    double distSq = eye.distanceSquared(new Location(world, worldX + 0.5, worldY + 0.5, worldZ + 0.5));
                    BlockPosition pos = new BlockPosition(world.getName(), worldX, worldY, worldZ);

                    boolean canSee = (distSq <= bypassDistSq) || Utils.canSeeBlock(eye, block, false);
                    if (canSee) {
                        keptTiles.add(te);
                        plugin.getAntiEspManager().markRevealed(player.getUniqueId(), pos);
                    } else {
                        int subIndex = (worldY - minHeight) >> 4;
                        if (subIndex >= 0 && subIndex < chunks.length && chunks[subIndex] != null) {
                            chunks[subIndex].set(te.getX(), worldY & 15, te.getZ(), StateTypes.AIR.createBlockState());
                        }
                        plugin.getAntiEspManager().markHidden(player.getUniqueId(), pos);
                    }
                } else {
                    keptTiles.add(te);
                }
            }
        }

        TileEntity[] finalTiles = keptTiles.toArray(new TileEntity[0]);
        Column newColumn;
        if (column.hasHeightMaps()) {
            newColumn = new Column(column.getX(), column.getZ(), column.isFullChunk(), chunks,
                    finalTiles, column.getHeightMaps());
        } else {
            newColumn = new Column(column.getX(), column.getZ(), column.isFullChunk(), chunks,
                    finalTiles);
        }
        chunkData.setColumn(newColumn);
        chunkData.write();
    }

    private void handleBlockChange(PacketSendEvent event, Player player) {
        World world = player.getWorld();
        if (plugin.isWorldDisabled(world.getName())) return;

        WrapperPlayServerBlockChange blockChange = new WrapperPlayServerBlockChange(event);
        Vector3i pos = blockChange.getBlockPosition();
        if (pos == null) return;

        if (plugin.getAntiFreecamManager().shouldObfuscateUnderground(player) && pos.getY() <= plugin.getAntiFreecamManager().getLowerY()) {
            blockChange.setBlockState(plugin.getAntiFreecamManager().getSolidReplacement(pos.getY()));
            blockChange.write();
            return;
        }

        WrappedBlockState state = blockChange.getBlockState();
        if (state == null) return;

        Location eye = player.getEyeLocation();
        double distSq = eye.distanceSquared(new Location(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));

        if (plugin.getOreObfuscatorManager().isEnabled() && plugin.getOreObfuscatorManager().isOre(state.getType())) {
            double proxRadius = plugin.getConfig().getInt("ORE-OBFUSCATOR.PROXIMITY-RADIUS", 8);
            double proxRadiusSq = proxRadius * proxRadius;

            boolean canSee = false;
            if (distSq <= proxRadiusSq) {
                Block block = world.getBlockAt(pos.getX(), pos.getY(), pos.getZ());
                canSee = plugin.getOreObfuscatorManager().canPlayerSeeOre(eye, block);
            }

            if (!canSee) {
                blockChange.setBlockState(plugin.getOreObfuscatorManager().getMaskedState(state.getType(), pos.getY()));
                blockChange.write();
                return;
            }
        }

        if (plugin.getAntiEspManager().isEnabled() && plugin.getAntiEspManager().isTarget(state.getType())) {
            Block block = world.getBlockAt(pos.getX(), pos.getY(), pos.getZ());
            double bypassDist = plugin.getConfig().getDouble("ANTI-ESP.PROXIMITY-BYPASS", 2.5);
            double bypassDistSq = bypassDist * bypassDist;
            boolean canSee = (distSq <= bypassDistSq) || Utils.canSeeBlock(eye, block, false);
            if (!canSee) {
                blockChange.setBlockState(StateTypes.AIR.createBlockState());
                blockChange.write();
            }
        }
    }

    private void handleMultiBlockChange(PacketSendEvent event, Player player) {
        World world = player.getWorld();
        if (plugin.isWorldDisabled(world.getName())) return;

        WrapperPlayServerMultiBlockChange multi = new WrapperPlayServerMultiBlockChange(event);
        WrapperPlayServerMultiBlockChange.EncodedBlock[] blocks = multi.getBlocks();
        if (blocks == null || blocks.length == 0) return;

        boolean freecamActive = plugin.getAntiFreecamManager().shouldObfuscateUnderground(player);
        int lowerY = plugin.getAntiFreecamManager().getLowerY();
        boolean oreObfEnabled = plugin.getOreObfuscatorManager().isEnabled();
        double proxRadius = plugin.getConfig().getInt("ORE-OBFUSCATOR.PROXIMITY-RADIUS", 8);
        double proxRadiusSq = proxRadius * proxRadius;
        Location eye = player.getEyeLocation();

        boolean modified = false;
        for (WrapperPlayServerMultiBlockChange.EncodedBlock block : blocks) {
            int worldY = block.getY();

            if (freecamActive && worldY <= lowerY) {
                block.setBlockState(plugin.getAntiFreecamManager().getSolidReplacement(worldY));
                modified = true;
                continue;
            }

            if (oreObfEnabled) {
                WrappedBlockState state = block.getBlockState(event.getUser().getClientVersion());
                if (state != null && plugin.getOreObfuscatorManager().isOre(state.getType())) {
                    double distSq = eye.distanceSquared(new Location(world, block.getX() + 0.5, worldY + 0.5, block.getZ() + 0.5));
                    boolean canSee = false;
                    if (distSq <= proxRadiusSq) {
                        Block worldBlock = world.getBlockAt(block.getX(), worldY, block.getZ());
                        canSee = plugin.getOreObfuscatorManager().canPlayerSeeOre(eye, worldBlock);
                    }

                    if (!canSee) {
                        block.setBlockState(plugin.getOreObfuscatorManager().getMaskedState(state.getType(), worldY));
                        modified = true;
                    }
                }
            }
        }

        if (modified) {
            multi.setBlocks(blocks);
            multi.write();
        }
    }

    private void handleBlockEntityData(PacketSendEvent event, Player player) {
        World world = player.getWorld();
        if (plugin.isWorldDisabled(world.getName())) return;

        WrapperPlayServerBlockEntityData data = new WrapperPlayServerBlockEntityData(event);
        Vector3i pos = data.getPosition();
        if (pos == null) return;

        if (plugin.getAntiFreecamManager().shouldObfuscateUnderground(player) && pos.getY() <= plugin.getAntiFreecamManager().getLowerY()) {
            event.setCancelled(true);
            return;
        }

        if (!plugin.getAntiEspManager().isEnabled()) return;
        Location eye = player.getEyeLocation();
        Block block = world.getBlockAt(pos.getX(), pos.getY(), pos.getZ());

        double distSq = eye.distanceSquared(new Location(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
        double bypassDist = plugin.getConfig().getDouble("ANTI-ESP.PROXIMITY-BYPASS", 2.5);
        double bypassDistSq = bypassDist * bypassDist;
        if (distSq > bypassDistSq && !Utils.canSeeBlock(eye, block, false)) {
            event.setCancelled(true);
        }
    }

    private void handleSpawnEntity(PacketSendEvent event, Player player) {
        if (!plugin.getAntiFreecamManager().shouldObfuscateUnderground(player)) return;

        WrapperPlayServerSpawnEntity spawn = new WrapperPlayServerSpawnEntity(event);
        if (spawn.getEntityId() == player.getEntityId()) return;

        EntityType type = spawn.getEntityType();
        if (type == EntityTypes.ENDER_PEARL || type == EntityTypes.ARROW || type == EntityTypes.SPECTRAL_ARROW || type == EntityTypes.TRIDENT || type == EntityTypes.SNOWBALL) {
            return;
        }

        Vector3d pos = spawn.getPosition();
        if (pos != null && pos.getY() <= plugin.getAntiFreecamManager().getLowerY()) {
            event.setCancelled(true);
            spawn.getUUID().ifPresent(uuid ->
                    plugin.getAntiFreecamManager().trackHiddenEntity(player.getUniqueId(), uuid)
            );
        }
    }

    private void handleSpawnLivingEntity(PacketSendEvent event, Player player) {
        if (!plugin.getAntiFreecamManager().shouldObfuscateUnderground(player)) return;

        WrapperPlayServerSpawnLivingEntity spawn = new WrapperPlayServerSpawnLivingEntity(event);
        if (spawn.getEntityId() == player.getEntityId()) return;

        Vector3d pos = spawn.getPosition();
        if (pos != null && pos.getY() <= plugin.getAntiFreecamManager().getLowerY()) {
            event.setCancelled(true);
            if (spawn.getEntityUUID() != null) {
                plugin.getAntiFreecamManager().trackHiddenEntity(player.getUniqueId(), spawn.getEntityUUID());
            }
        }
    }

    private void handleSpawnPlayer(PacketSendEvent event, Player player) {
        if (!plugin.getAntiFreecamManager().shouldObfuscateUnderground(player)) return;

        WrapperPlayServerSpawnPlayer spawn = new WrapperPlayServerSpawnPlayer(event);
        if (spawn.getEntityId() == player.getEntityId()) return;

        Vector3d pos = spawn.getPosition();
        if (pos != null && pos.getY() <= plugin.getAntiFreecamManager().getLowerY()) {
            event.setCancelled(true);
            if (spawn.getUUID() != null) {
                plugin.getAntiFreecamManager().trackHiddenEntity(player.getUniqueId(), spawn.getUUID());
            }
        }
    }

    private void handleSpawnExperienceOrb(PacketSendEvent event, Player player) {
        if (!plugin.getAntiFreecamManager().shouldObfuscateUnderground(player)) return;

        WrapperPlayServerSpawnExperienceOrb orb = new WrapperPlayServerSpawnExperienceOrb(event);
        if (orb.getY() <= plugin.getAntiFreecamManager().getLowerY()) {
            event.setCancelled(true);
        }
    }
}
