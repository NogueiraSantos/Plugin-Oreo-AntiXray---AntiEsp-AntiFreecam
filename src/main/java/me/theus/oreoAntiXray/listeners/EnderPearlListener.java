package me.theus.oreoAntiXray.listeners;

import me.theus.oreoAntiXray.OreoAntiXray;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Openable;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.util.Vector;

public class EnderPearlListener implements Listener {

    private final OreoAntiXray plugin;

    public EnderPearlListener(OreoAntiXray plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof EnderPearl pearl)) return;
        if (!(pearl.getShooter() instanceof Player player)) return;

        Block hitBlock = event.getHitBlock();
        if (hitBlock == null) return;

        Vector velocity = pearl.getVelocity();
        double speed = velocity.length();
        if (speed < 0.05) return;

        Vector dir = velocity.clone().normalize();
        Location pearlLoc = pearl.getLocation();

        if (isPassablePvpBlock(hitBlock)) {
            event.setCancelled(true);
            pearl.teleport(pearlLoc.add(dir.clone().multiply(0.5)));
            pearl.setVelocity(velocity);
            return;
        }

        Location ahead06 = pearlLoc.clone().add(dir.clone().multiply(0.6));
        Location ahead12 = pearlLoc.clone().add(dir.clone().multiply(1.2));

        if (isAirOrPassable(ahead06.getBlock()) || isAirOrPassable(ahead12.getBlock())) {
            event.setCancelled(true);
            pearl.teleport(ahead06);
            pearl.setVelocity(velocity);
        }
    }

    private boolean isPassablePvpBlock(Block block) {
        Material mat = block.getType();
        BlockData data = block.getBlockData();

        if (data instanceof Openable openable) {
            if (openable.isOpen()) {
                return true;
            }
        }

        if (mat == Material.COBWEB) {
            return true;
        }

        if (mat == Material.IRON_BARS || mat.name().endsWith("_PANE") || mat == Material.GLASS_PANE) {
            return true;
        }

        if (mat.name().endsWith("_SLAB") || mat.name().endsWith("_TRAPDOOR")) {
            return true;
        }

        if (Tag.FENCES.isTagged(mat) || Tag.WALLS.isTagged(mat) || Tag.FENCE_GATES.isTagged(mat)) {
            return true;
        }

        return false;
    }

    private boolean isAirOrPassable(Block block) {
        if (block == null) return false;
        Material mat = block.getType();
        return mat.isAir() || block.isPassable() || isPassablePvpBlock(block);
    }
}
