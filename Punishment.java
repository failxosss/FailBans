package cz.failban.plugin.model;

import java.util.UUID;

public class Punishment {

    private int id;
    private final UUID uuid;
    private final String playerName;
    private final PunishmentType type;
    private String reason;
    private final String staff;
    private final String ip;
    private final long createdAt;
    private final long expiresAt; // -1 = permanent
    private boolean active;
    private String removedBy;
    private String removeReason;

    public Punishment(int id, UUID uuid, String playerName, PunishmentType type, String reason,
                       String staff, String ip, long createdAt, long expiresAt, boolean active,
                       String removedBy, String removeReason) {
        this.id = id;
        this.uuid = uuid;
        this.playerName = playerName;
        this.type = type;
        this.reason = reason;
        this.staff = staff;
        this.ip = ip;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.active = active;
        this.removedBy = removedBy;
        this.removeReason = removeReason;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public UUID getUuid() { return uuid; }
    public String getPlayerName() { return playerName; }
    public PunishmentType getType() { return type; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getStaff() { return staff; }
    public String getIp() { return ip; }
    public long getCreatedAt() { return createdAt; }
    public long getExpiresAt() { return expiresAt; }
    public boolean isPermanent() { return expiresAt == -1; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getRemovedBy() { return removedBy; }
    public void setRemovedBy(String removedBy) { this.removedBy = removedBy; }
    public String getRemoveReason() { return removeReason; }
    public void setRemoveReason(String removeReason) { this.removeReason = removeReason; }

    public boolean isExpired() {
        if (isPermanent()) return false;
        return System.currentTimeMillis() >= expiresAt;
    }

    public long getRemainingMillis() {
        if (isPermanent()) return -1;
        return Math.max(0, expiresAt - System.currentTimeMillis());
    }
}
