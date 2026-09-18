package cz.failban.plugin.sus.data;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class SusRecord {

    private final UUID uuid;
    private String name;
    private String reason;
    private String anticheat;
    private String lastCheck;
    private int totalFlags;
    private int violations;
    private long firstFlag;
    private long lastFlag;
    private String world = "unknown";
    private int x, y, z;

    private final Map<String, Integer> checkCounts = new LinkedHashMap<>();

    public SusRecord(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
        this.firstFlag = System.currentTimeMillis();
        this.lastFlag = this.firstFlag;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name == null ? "unknown" : name;
    }

    public void setName(String name) {
        if (name != null && !name.isEmpty()) {
            this.name = name;
        }
    }

    public String getReason() {
        return reason == null ? "CHEATING" : reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getAnticheat() {
        return anticheat == null ? "UNKNOWN" : anticheat;
    }

    public void setAnticheat(String anticheat) {
        this.anticheat = anticheat;
    }

    public String getLastCheck() {
        return lastCheck == null ? "-" : lastCheck;
    }

    public void setLastCheck(String lastCheck) {
        this.lastCheck = lastCheck;
    }

    public int getTotalFlags() {
        return totalFlags;
    }

    public void setTotalFlags(int totalFlags) {
        this.totalFlags = totalFlags;
    }

    public void addFlag() {
        this.totalFlags++;
    }

    public int getViolations() {
        return violations;
    }

    public void setViolations(int violations) {
        this.violations = violations;
    }

    public long getFirstFlag() {
        return firstFlag;
    }

    public void setFirstFlag(long firstFlag) {
        this.firstFlag = firstFlag;
    }

    public long getLastFlag() {
        return lastFlag;
    }

    public void setLastFlag(long lastFlag) {
        this.lastFlag = lastFlag;
    }

    public String getWorld() {
        return world == null ? "unknown" : world;
    }

    public void setWorld(String world) {
        this.world = world;
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

    public void setPosition(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Map<String, Integer> getCheckCounts() {
        return checkCounts;
    }

    public void countCheck(String check) {
        if (check == null || check.isEmpty()) {
            return;
        }
        checkCounts.merge(check, 1, Integer::sum);
    }

    public String getCheckSummary() {
        if (checkCounts.isEmpty()) {
            return getLastCheck();
        }
        StringBuilder sb = new StringBuilder();
        checkCounts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(4)
                .forEach(e -> {
                    if (sb.length() > 0) {
                        sb.append(", ");
                    }
                    sb.append(e.getKey());
                    if (e.getValue() > 1) {
                        sb.append(" x").append(e.getValue());
                    }
                });
        return sb.toString();
    }
}
