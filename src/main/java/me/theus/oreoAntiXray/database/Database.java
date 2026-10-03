package me.theus.oreoAntiXray.database;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.theus.oreoAntiXray.OreoAntiXray;
import me.theus.oreoAntiXray.models.AlertLog;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class Database {

    private final OreoAntiXray plugin;
    private HikariDataSource dataSource;
    private MongoClient mongoClient;
    private MongoDatabase mongoDatabase;
    private String databaseType;

    public Database(OreoAntiXray plugin) {
        this.plugin = plugin;
    }

    public boolean connect() {
        FileConfiguration config = plugin.getDatabaseConfig();
        databaseType = config.getString("DATABASE", "SQLITE").toUpperCase();

        if (databaseType.equals("MONGODB")) {
            try {
                String uri = config.getString("MONGODB.URI", "");
                if (uri != null && !uri.isEmpty()) {
                    mongoClient = MongoClients.create(uri);
                } else {
                    String host = config.getString("MONGODB.ADDRESS", "127.0.0.1");
                    int port = config.getInt("MONGODB.PORT", 27017);

                    MongoClientSettings.Builder settingsBuilder = MongoClientSettings.builder()
                            .applyConnectionString(new ConnectionString("mongodb://" + host + ":" + port));

                    if (config.getBoolean("MONGODB.AUTHENTICATION.ENABLED", false)) {
                        String user = config.getString("MONGODB.AUTHENTICATION.USERNAME");
                        String password = config.getString("MONGODB.AUTHENTICATION.PASSWORD");
                        String authDb = config.getString("MONGODB.AUTHENTICATION.DATABASE");
                        MongoCredential credential = MongoCredential.createCredential(user, authDb, password.toCharArray());
                        settingsBuilder.credential(credential);
                    }

                    mongoClient = MongoClients.create(settingsBuilder.build());
                }
                String dbName = config.getString("MONGODB.DATABASE", "OreoAntiXray");
                mongoDatabase = mongoClient.getDatabase(dbName);
                plugin.getLogger().info("Conectado ao MongoDB com sucesso!");
                return true;
            } catch (Throwable t) {
                plugin.getLogger().severe("Erro ao conectar ao MongoDB: " + t.getMessage());
                return false;
            }
        }

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setPoolName("OreoAntiXray-Pool");

        if (databaseType.equals("MYSQL")) {
            String host = config.getString("MYSQL.HOST", "127.0.0.1");
            int port = config.getInt("MYSQL.PORT", 3306);
            String db = config.getString("MYSQL.DATABASE", "oreo_antixray");
            String user = config.getString("MYSQL.USER", "root");
            String pass = config.getString("MYSQL.PASSWORD", "");
            String params = config.getString("MYSQL.PARAMS", "useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");

            hikariConfig.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + db + "?" + params);
            hikariConfig.setUsername(user);
            hikariConfig.setPassword(pass);
            hikariConfig.setMaximumPoolSize(config.getInt("MYSQL.POOL.MAX-POOL-SIZE", 10));
            hikariConfig.setMinimumIdle(config.getInt("MYSQL.POOL.MIN-IDLE", 2));
            hikariConfig.setConnectionTimeout(config.getLong("MYSQL.POOL.CONNECTION-TIMEOUT", 30000));
        } else {
            databaseType = "SQLITE";
            String filePath = config.getString("SQLITE.FILE", "plugins/OreoAntiXray/oreoantixray.db");
            File file = new File(plugin.getDataFolder().getParentFile().getParentFile(), filePath);
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            hikariConfig.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath());
            hikariConfig.setMaximumPoolSize(1);
            hikariConfig.setConnectionTestQuery("SELECT 1");
        }

        try {
            dataSource = new HikariDataSource(hikariConfig);
            createTables();
            plugin.getLogger().info("Conectado ao banco de dados (" + databaseType + ") com sucesso!");
            return true;
        } catch (Throwable t) {
            plugin.getLogger().severe("Erro ao inicializar pool HikariCP (" + databaseType + "): " + t.getMessage());
            return false;
        }
    }

    private void createTables() {
        String sql;
        if (databaseType.equals("MYSQL")) {
            sql = "CREATE TABLE IF NOT EXISTS oreo_alerts (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "uuid VARCHAR(36) NOT NULL, " +
                    "player VARCHAR(32) NOT NULL, " +
                    "world VARCHAR(64) NOT NULL, " +
                    "x INT NOT NULL, " +
                    "y INT NOT NULL, " +
                    "z INT NOT NULL, " +
                    "timestamp BIGINT NOT NULL, " +
                    "details TEXT NOT NULL" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";
        } else {
            sql = "CREATE TABLE IF NOT EXISTS oreo_alerts (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "uuid VARCHAR(36) NOT NULL, " +
                    "player VARCHAR(32) NOT NULL, " +
                    "world VARCHAR(64) NOT NULL, " +
                    "x INT NOT NULL, " +
                    "y INT NOT NULL, " +
                    "z INT NOT NULL, " +
                    "timestamp BIGINT NOT NULL, " +
                    "details TEXT NOT NULL" +
                    ");";
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao criar tabela oreo_alerts: " + e.getMessage());
        }
    }

    public void logAlertAsync(AlertLog log) {
        if (log == null) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if ("MONGODB".equals(databaseType)) {
                if (mongoDatabase == null) return;
                try {
                    MongoCollection<Document> coll = mongoDatabase.getCollection("oreo_alerts");
                    Document doc = new Document("uuid", log.getPlayerUuid().toString())
                            .append("player", log.getPlayerName())
                            .append("world", log.getWorld())
                            .append("x", log.getX())
                            .append("y", log.getY())
                            .append("z", log.getZ())
                            .append("timestamp", log.getTimestamp())
                            .append("details", log.getDetails());
                    coll.insertOne(doc);
                } catch (Throwable t) {
                    plugin.getLogger().warning("Erro ao salvar alerta no MongoDB: " + t.getMessage());
                }
                return;
            }

            if (dataSource == null || dataSource.isClosed()) return;
            String sql = "INSERT INTO oreo_alerts (uuid, player, world, x, y, z, timestamp, details) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, log.getPlayerUuid().toString());
                ps.setString(2, log.getPlayerName());
                ps.setString(3, log.getWorld());
                ps.setInt(4, log.getX());
                ps.setInt(5, log.getY());
                ps.setInt(6, log.getZ());
                ps.setLong(7, log.getTimestamp());
                ps.setString(8, log.getDetails());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Erro ao registrar alerta no banco de dados: " + e.getMessage());
            }
        });
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
        if (mongoClient != null) {
            mongoClient.close();
        }
    }
}
