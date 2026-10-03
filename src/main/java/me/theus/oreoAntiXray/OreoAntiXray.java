package me.theus.oreoAntiXray;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import me.theus.oreoAntiXray.commands.OreoCommand;
import me.theus.oreoAntiXray.database.Database;
import me.theus.oreoAntiXray.listeners.EnderPearlListener;
import me.theus.oreoAntiXray.listeners.PacketEventsListener;
import me.theus.oreoAntiXray.listeners.PlayerListener;
import me.theus.oreoAntiXray.managers.AntiEspManager;
import me.theus.oreoAntiXray.managers.AntiFreecamManager;
import me.theus.oreoAntiXray.managers.FakeOreManager;
import me.theus.oreoAntiXray.managers.OreObfuscatorManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class OreoAntiXray extends JavaPlugin {

    private static OreoAntiXray instance;

    private FileConfiguration messagesConfig;
    private FileConfiguration databaseConfig;

    private File messagesFile;
    private File databaseFile;

    private Database database;
    private OreObfuscatorManager oreObfuscatorManager;
    private FakeOreManager fakeOreManager;
    private AntiEspManager antiEspManager;
    private AntiFreecamManager antiFreecamManager;

    private PacketEventsListener packetListener;
    private com.github.retrooper.packetevents.event.PacketListenerCommon registeredListener;
    private BukkitTask proximityTask;

    private final Set<String> disabledWorlds = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<UUID> alertsDisabledPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public static OreoAntiXray getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        loadCustomConfigs();

        database = new Database(this);
        if (!database.connect()) {
            getLogger().severe("Falha ao inicializar banco de dados do OreoAntiXray! Desativando plugin...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        oreObfuscatorManager = new OreObfuscatorManager(this);
        fakeOreManager = new FakeOreManager(this);
        antiEspManager = new AntiEspManager(this);
        antiFreecamManager = new AntiFreecamManager(this);

        reloadAll();

        try {
            if (getServer().getPluginManager().isPluginEnabled("packetevents")) {
                packetListener = new PacketEventsListener(this);
                registeredListener = PacketEvents.getAPI().getEventManager().registerListener(packetListener, PacketListenerPriority.NORMAL);
                getLogger().info("Hook com PacketEvents 2.x registrado com sucesso!");
            } else {
                getLogger().warning("PacketEvents não encontrado ou desativado! Interceptação de pacotes inativa.");
            }
        } catch (Throwable t) {
            getLogger().severe("Erro ao registrar PacketEvents listener: " + t.getMessage());
        }

        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new EnderPearlListener(this), this);

        OreoCommand cmd = new OreoCommand(this);
        PluginCommand oreoCmd = getCommand("oreo");
        if (oreoCmd != null) {
            oreoCmd.setExecutor(cmd);
            oreoCmd.setTabCompleter(cmd);
        }

        int interval = getConfig().getInt("PERFORMANCE.CHECK-INTERVAL-TICKS", 4);
        proximityTask = Bukkit.getScheduler().runTaskTimer(this, this::tickProximity, interval, interval);

        getLogger().info("OreoAntiXray ativado com sucesso! Protegendo contra X-Ray, ESP e Freecam (Autor: Nogueira).");
    }

    @Override
    public void onDisable() {
        if (proximityTask != null) {
            proximityTask.cancel();
            proximityTask = null;
        }

        if (registeredListener != null) {
            try {
                PacketEvents.getAPI().getEventManager().unregisterListener(registeredListener);
            } catch (Throwable ignored) {}
        }

        if (antiEspManager != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                antiEspManager.restoreForPlayer(p);
            }
            antiEspManager.clearAll();
        }

        if (oreObfuscatorManager != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                oreObfuscatorManager.restoreForPlayer(p);
            }
            oreObfuscatorManager.clearAll();
        }

        if (fakeOreManager != null) {
            fakeOreManager.clearAll();
        }

        if (antiFreecamManager != null) {
            antiFreecamManager.clearAll();
        }

        if (database != null) {
            database.close();
        }

        getLogger().info("OreoAntiXray desativado com sucesso!");
    }

    public void reloadAll() {
        reloadConfig();
        loadCustomConfigs();

        disabledWorlds.clear();
        disabledWorlds.addAll(getConfig().getStringList("PERFORMANCE.DISABLED-WORLDS"));

        if (oreObfuscatorManager != null) oreObfuscatorManager.reload();
        if (fakeOreManager != null) fakeOreManager.reload();
        if (antiEspManager != null) antiEspManager.reload();
        if (antiFreecamManager != null) antiFreecamManager.reload();
    }

    private void tickProximity() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.isValid() || !p.isOnline() || isWorldDisabled(p.getWorld().getName())) continue;

            if (oreObfuscatorManager.isEnabled()) {
                oreObfuscatorManager.updatePlayerProximity(p);
            }
            if (antiEspManager.isEnabled()) {
                antiEspManager.updatePlayerEsp(p);
            }
        }
    }

    public void loadCustomConfigs() {
        messagesFile = new File(getDataFolder(), "mensagens.yml");
        if (!messagesFile.exists()) {
            saveResource("mensagens.yml", false);
        }
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);

        databaseFile = new File(getDataFolder(), "database.yml");
        if (!databaseFile.exists()) {
            saveResource("database.yml", false);
        }
        databaseConfig = YamlConfiguration.loadConfiguration(databaseFile);
    }

    public boolean isWorldDisabled(String worldName) {
        if (worldName == null) return false;
        return disabledWorlds.contains(worldName.toLowerCase());
    }

    public boolean toggleAlerts(UUID uuid) {
        if (alertsDisabledPlayers.contains(uuid)) {
            alertsDisabledPlayers.remove(uuid);
            return true;
        } else {
            alertsDisabledPlayers.add(uuid);
            return false;
        }
    }

    public void removeAlertToggled(UUID uuid) {
        alertsDisabledPlayers.remove(uuid);
    }

    public boolean isAlertsActiveFor(UUID uuid) {
        return !alertsDisabledPlayers.contains(uuid);
    }

    public FileConfiguration getMessagesConfig() {
        return messagesConfig;
    }

    public FileConfiguration getDatabaseConfig() {
        return databaseConfig;
    }

    public Database getDatabase() {
        return database;
    }

    public OreObfuscatorManager getOreObfuscatorManager() {
        return oreObfuscatorManager;
    }

    public FakeOreManager getFakeOreManager() {
        return fakeOreManager;
    }

    public AntiEspManager getAntiEspManager() {
        return antiEspManager;
    }

    public AntiFreecamManager getAntiFreecamManager() {
        return antiFreecamManager;
    }
}
