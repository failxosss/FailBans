package cz.failban.plugin.model;

import java.util.UUID;

public class PlayerData {
    private final UUID uuid;
    private final String name;
    private final String lastIp;
    private final long firstJoin;
    private final long lastJoin;

    public PlayerData(UUID uuid, String name, String lastIp, long firstJoin, long lastJoin) {
        this.uuid = uuid;
        this.name = name;
        this.lastIp = lastIp;
        this.firstJoin = firstJoin;
        this.lastJoin = lastJoin;
    }

    public UUID getUuid() { return uuid; }
    public String getName() { return name; }
    public String getLastIp() { return lastIp; }
    public long getFirstJoin() { return firstJoin; }
    public long getLastJoin() { return lastJoin; }
}
