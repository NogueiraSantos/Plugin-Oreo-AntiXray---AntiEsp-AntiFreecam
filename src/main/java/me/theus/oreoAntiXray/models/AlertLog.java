package me.theus.oreoAntiXray.models;

import java.util.UUID;

public class AlertLog {

    private final UUID playerUuid;
    private final String playerName;
    private final String world;
    private final int x;
    private final int y;
    private final int z;
    private final long timestamp;
    private final String details;

    public AlertLog(UUID playerUuid, String playerName, String world, int x, int y, int z, long timestamp, String details) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.timestamp = timestamp;
        this.details = details;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getWorld() {
        return world;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getDetails() {
        return details;
    }
}
