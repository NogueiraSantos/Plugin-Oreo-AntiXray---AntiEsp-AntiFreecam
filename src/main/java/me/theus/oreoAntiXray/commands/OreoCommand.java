package me.theus.oreoAntiXray.commands;

import me.theus.oreoAntiXray.OreoAntiXray;
import me.theus.oreoAntiXray.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class OreoCommand implements CommandExecutor, TabCompleter {

    private final OreoAntiXray plugin;

    public OreoCommand(OreoAntiXray plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "reload":
                if (!sender.hasPermission("oreo.admin") && !sender.isOp()) {
                    sender.sendMessage(Utils.parseComponent(plugin.getMessagesConfig().getString("COMMANDS.NO-PERMISSION", "&cVocê não tem permissão.")));
                    return true;
                }
                plugin.reloadAll();
                sender.sendMessage(Utils.parseComponent(plugin.getMessagesConfig().getString("COMMANDS.RELOAD-SUCCESS", "&aConfigurações recarregadas com sucesso!")));
                return true;

            case "alerts":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§cApenas jogadores podem alternar alertas.");
                    return true;
                }
                if (!sender.hasPermission("oreo.alerts") && !sender.isOp()) {
                    sender.sendMessage(Utils.parseComponent(plugin.getMessagesConfig().getString("COMMANDS.NO-PERMISSION", "&cVocê não tem permissão.")));
                    return true;
                }
                Player player = (Player) sender;
                boolean active = plugin.toggleAlerts(player.getUniqueId());
                if (active) {
                    player.sendMessage(Utils.parseComponent(plugin.getMessagesConfig().getString("COMMANDS.ALERTS-TOGGLED-ON", "&aAlertas ativados!")));
                } else {
                    player.sendMessage(Utils.parseComponent(plugin.getMessagesConfig().getString("COMMANDS.ALERTS-TOGGLED-OFF", "&cAlertas desativados!")));
                }
                return true;

            case "inspect":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§cApenas jogadores podem usar o comando de inspecionar.");
                    return true;
                }
                if (!sender.hasPermission("oreo.inspect") && !sender.hasPermission("oreo.admin") && !sender.isOp()) {
                    sender.sendMessage(Utils.parseComponent(plugin.getMessagesConfig().getString("COMMANDS.NO-PERMISSION", "&cVocê não tem permissão.")));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("§cUso: /oreo inspect <jogador>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null || !target.isOnline()) {
                    sender.sendMessage(Utils.parseComponent(plugin.getMessagesConfig().getString("COMMANDS.PLAYER-NOT-FOUND", "&cJogador não encontrado.")
                            .replace("{player}", args[1])));
                    return true;
                }
                Player staff = (Player) sender;
                staff.setGameMode(GameMode.SPECTATOR);
                staff.teleport(target.getLocation().add(0, 1.5, 0));
                staff.sendMessage(Utils.parseComponent(plugin.getMessagesConfig().getString("ALERT.TELEPORTED", "&aVocê foi teleportado para &e{player} &aem modo espectador!")
                        .replace("{player}", target.getName())));
                return true;

            case "tp":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§cApenas jogadores podem teleportar.");
                    return true;
                }
                if (!sender.hasPermission("oreo.inspect") && !sender.hasPermission("oreo.admin") && !sender.isOp()) {
                    sender.sendMessage(Utils.parseComponent(plugin.getMessagesConfig().getString("COMMANDS.NO-PERMISSION", "&cVocê não tem permissão.")));
                    return true;
                }
                if (args.length < 5) {
                    sender.sendMessage("§cUso: /oreo tp <jogador> <x> <y> <z>");
                    return true;
                }
                Player pStaff = (Player) sender;
                String targetName = args[1];
                try {
                    int x = Integer.parseInt(args[2]);
                    int y = Integer.parseInt(args[3]);
                    int z = Integer.parseInt(args[4]);

                    World w = pStaff.getWorld();
                    Player pTarget = Bukkit.getPlayer(targetName);
                    if (pTarget != null && pTarget.isOnline()) {
                        w = pTarget.getWorld();
                    }

                    Location tpLoc = new Location(w, x + 0.5, y + 1.5, z + 0.5);
                    pStaff.setGameMode(GameMode.SPECTATOR);
                    pStaff.teleport(tpLoc);
                    pStaff.sendMessage(Utils.parseComponent(plugin.getMessagesConfig().getString("ALERT.TELEPORTED", "&aVocê foi teleportado para &e{player} &aem modo espectador!")
                            .replace("{player}", targetName)));
                } catch (NumberFormatException e) {
                    pStaff.sendMessage("§cCoordenadas inválidas.");
                }
                return true;

            case "status":
                if (!sender.hasPermission("oreo.admin") && !sender.isOp()) {
                    sender.sendMessage(Utils.parseComponent(plugin.getMessagesConfig().getString("COMMANDS.NO-PERMISSION", "&cVocê não tem permissão.")));
                    return true;
                }
                sender.sendMessage("§e---- §4OreoAntiXray Status §e----");
                sender.sendMessage("§7Ore Obfuscator: " + (plugin.getOreObfuscatorManager().isEnabled() ? "§aATIVO" : "§cDESATIVADO"));
                sender.sendMessage("§7Fake Ores (Decoys): " + (plugin.getFakeOreManager().isEnabled() ? "§aATIVO" : "§cDESATIVADO"));
                sender.sendMessage("§7Anti-ESP: " + (plugin.getAntiEspManager().isEnabled() ? "§aATIVO" : "§cDESATIVADO"));
                sender.sendMessage("§7Anti-Freecam: " + (plugin.getAntiFreecamManager().isEnabled() ? "§aATIVO" : "§cDESATIVADO"));
                sender.sendMessage("§7TPS do Servidor: §a" + String.format("%.2f", Bukkit.getTPS()[0]));
                return true;

            default:
                sendHelp(sender);
                return true;
        }
    }

    private void sendHelp(CommandSender sender) {
        List<String> help = plugin.getMessagesConfig().getStringList("COMMANDS.HELP");
        if (help == null || help.isEmpty()) {
            sender.sendMessage("§e---- §4OreoAntiXray §e----");
            sender.sendMessage("§e/oreo reload §7- Recarrega configurações");
            sender.sendMessage("§e/oreo alerts §7- Alterna alertas de suspeita");
            sender.sendMessage("§e/oreo inspect <jogador> §7- Inspeciona em modo espectador");
            sender.sendMessage("§e/oreo status §7- Exibe o status do plugin");
            return;
        }
        for (String line : help) {
            sender.sendMessage(Utils.parseComponent(line));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = Arrays.asList("reload", "alerts", "inspect", "status");
            List<String> result = new ArrayList<>();
            for (String s : subs) {
                if (s.startsWith(args[0].toLowerCase())) {
                    result.add(s);
                }
            }
            return result;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("inspect")) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                    names.add(p.getName());
                }
            }
            return names;
        }
        return Collections.emptyList();
    }
}
