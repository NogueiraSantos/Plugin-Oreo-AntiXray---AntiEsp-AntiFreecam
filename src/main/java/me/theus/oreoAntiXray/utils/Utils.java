package me.theus.oreoAntiXray.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    public static String color(String text) {
        if (text == null) return "";
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String color = matcher.group(1);
            matcher.appendReplacement(buffer, "§x§" + color.charAt(0) + "§" + color.charAt(1)
                    + "§" + color.charAt(2) + "§" + color.charAt(3)
                    + "§" + color.charAt(4) + "§" + color.charAt(5));
        }
        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    public static Component parseComponent(String text) {
        if (text == null || text.isEmpty()) return Component.empty();
        if (text.contains("<") && text.contains(">")) {
            try {
                return MINI_MESSAGE.deserialize(text);
            } catch (Throwable ignored) {}
        }
        return LegacyComponentSerializer.legacySection().deserialize(color(text));
    }

    public static Component createClickableAlert(String playerName, Location loc) {
        return createClickableAlert(playerName, null, loc);
    }

    public static Component createClickableAlert(String playerName, String oreDetail, Location loc) {
        String detailText = (oreDetail != null && !oreDetail.isEmpty()) ? " &7(" + oreDetail + ")" : "";
        String baseText = "&4[Oreo] &fDetectamos uma atividade suspeita de &e" + playerName + detailText + " &8- ";
        Component prefixAndName = parseComponent(baseText);

        Component tpButton = Component.text("[Teleportar]")
                .color(NamedTextColor.YELLOW)
                .decorate(TextDecoration.BOLD)
                .hoverEvent(HoverEvent.showText(parseComponent("&eClique para teleportar até &f" + playerName + " &eem modo espectador!")))
                .clickEvent(ClickEvent.runCommand("/oreo tp " + playerName + " " + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ()));

        return Component.text().append(prefixAndName).append(tpButton).build();
    }

    public static void playSound(Player player, String soundName) {
        if (player == null || soundName == null || soundName.isEmpty()) return;
        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase());
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        } catch (Throwable ignored) {}
    }

    public static boolean canSeeBlock(Location eye, Block block, boolean isOre) {
        if (eye == null || block == null || eye.getWorld() == null || !eye.getWorld().equals(block.getWorld())) {
            return false;
        }

        int bx = block.getX();
        int by = block.getY();
        int bz = block.getZ();
        World world = block.getWorld();

        Location center = new Location(world, bx + 0.5, by + 0.5, bz + 0.5);
        if (hasLineOfSight(eye, center, bx, by, bz, isOre)) {
            return true;
        }

        for (BlockFace face : BlockFace.values()) {
            if (face.isCartesian()) {
                Block rel = block.getRelative(face);
                Material relMat = rel.getType();
                if (relMat.isAir() || rel.isPassable() || !relMat.isOccluding() || relMat == Material.WATER || relMat == Material.LAVA) {
                    Location facePt = new Location(world,
                            bx + 0.5 + face.getModX() * 0.45,
                            by + 0.5 + face.getModY() * 0.45,
                            bz + 0.5 + face.getModZ() * 0.45);
                    if (hasLineOfSight(eye, facePt, bx, by, bz, isOre)) {
                        return true;
                    }
                }
            }
        }

        double[][] cornerOffsets = {
                {0.15, 0.15, 0.15},
                {0.85, 0.15, 0.15},
                {0.15, 0.85, 0.15},
                {0.85, 0.85, 0.15},
                {0.15, 0.15, 0.85},
                {0.85, 0.15, 0.85},
                {0.15, 0.85, 0.85},
                {0.85, 0.85, 0.85}
        };

        for (double[] offset : cornerOffsets) {
            Location pt = new Location(world, bx + offset[0], by + offset[1], bz + offset[2]);
            if (hasLineOfSight(eye, pt, bx, by, bz, isOre)) {
                return true;
            }
        }

        double[][] edgeOffsets = {
                {0.15, 0.5, 0.15}, {0.85, 0.5, 0.15}, {0.15, 0.5, 0.85}, {0.85, 0.5, 0.85},
                {0.5, 0.15, 0.15}, {0.5, 0.85, 0.15}, {0.5, 0.15, 0.85}, {0.5, 0.85, 0.85},
                {0.15, 0.15, 0.5}, {0.85, 0.15, 0.5}, {0.15, 0.85, 0.5}, {0.85, 0.85, 0.5}
        };

        for (double[] offset : edgeOffsets) {
            Location pt = new Location(world, bx + offset[0], by + offset[1], bz + offset[2]);
            if (hasLineOfSight(eye, pt, bx, by, bz, isOre)) {
                return true;
            }
        }

        return false;
    }

    public static boolean hasLineOfSight(Location from, Location to) {
        if (to == null) return false;
        return hasLineOfSight(from, to, to.getBlockX(), to.getBlockY(), to.getBlockZ(), false);
    }

    public static boolean hasLineOfSight(Location from, Location to, int targetX, int targetY, int targetZ, boolean isOreTarget) {
        if (from == null || to == null || from.getWorld() == null || !from.getWorld().equals(to.getWorld())) {
            return false;
        }

        int eyeBlockX = from.getBlockX();
        int eyeBlockY = from.getBlockY();
        int eyeBlockZ = from.getBlockZ();

        if (targetX == eyeBlockX && targetY == eyeBlockY && targetZ == eyeBlockZ) {
            return true;
        }

        World world = from.getWorld();
        Vector start = from.toVector();
        Vector end = to.toVector();
        Vector direction = end.clone().subtract(start);
        double distance = direction.length();

        if (distance <= 0.2) return true;

        direction.normalize();

        try {
            RayTraceResult hit = world.rayTraceBlocks(from, direction, distance, FluidCollisionMode.NEVER, true, block -> {
                int bx = block.getX();
                int by = block.getY();
                int bz = block.getZ();

                if (bx == eyeBlockX && by == eyeBlockY && bz == eyeBlockZ) {
                    return false;
                }

                if (bx == targetX && by == targetY && bz == targetZ) {
                    return true;
                }

                Material mat = block.getType();

                if (isOreTarget && (mat.name().contains("ORE") || mat == Material.ANCIENT_DEBRIS)) {
                    return true;
                }

                if (!isOreTarget && (mat == Material.CHEST || mat == Material.TRAPPED_CHEST || mat == Material.BARREL || mat == Material.SPAWNER || mat.name().endsWith("SHULKER_BOX"))) {
                    return true;
                }

                return mat.isOccluding() && !block.isPassable();
            });

            if (hit == null) {
                return true;
            }

            Block hitBlock = hit.getHitBlock();
            if (hitBlock == null) {
                return true;
            }

            int hx = hitBlock.getX();
            int hy = hitBlock.getY();
            int hz = hitBlock.getZ();

            if (hx == targetX && hy == targetY && hz == targetZ) {
                return true;
            }

            Material hitMat = hitBlock.getType();

            if (isOreTarget && (hitMat.name().contains("ORE") || hitMat == Material.ANCIENT_DEBRIS)) {
                return true;
            }

            if (!isOreTarget && (hitMat == Material.CHEST || hitMat == Material.TRAPPED_CHEST || hitMat == Material.BARREL || hitMat == Material.SPAWNER || hitMat.name().endsWith("SHULKER_BOX"))) {
                return true;
            }

            return false;
        } catch (Throwable t) {
            return true;
        }
    }
}
