package cz.failban.plugin.manager;

import cz.failban.plugin.FailBan;
import cz.failban.plugin.model.PlayerData;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class PlayerDataManager {

    private final FailBan plugin;

    public PlayerDataManager(FailBan plugin) {
        this.plugin = plugin;
    }

    private Connection conn() {
        return plugin.getDatabaseManager().getConnection();
    }

    public void recordJoin(UUID uuid, String name, String ip) {
        long now = System.currentTimeMillis();
        try {
            PlayerData existing = getByUuid(uuid);
            if (existing == null) {
                String sql = "INSERT INTO player_data (uuid, name, last_ip, first_join, last_join) VALUES (?, ?, ?, ?, ?)";
                try (PreparedStatement ps = conn().prepareStatement(sql)) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, name);
                    ps.setString(3, ip);
                    ps.setLong(4, now);
                    ps.setLong(5, now);
                    ps.executeUpdate();
                }
            } else {
                String sql = "UPDATE player_data SET name = ?, last_ip = ?, last_join = ? WHERE uuid = ?";
                try (PreparedStatement ps = conn().prepareStatement(sql)) {
                    ps.setString(1, name);
                    ps.setString(2, ip);
                    ps.setLong(3, now);
                    ps.setString(4, uuid.toString());
                    ps.executeUpdate();
                }
            }

            String ipSql = "INSERT INTO ip_history (uuid, ip, seen_at) VALUES (?, ?, ?)";
            try (PreparedStatement ps = conn().prepareStatement(ipSql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, ip);
                ps.setLong(3, now);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public PlayerData getByUuid(UUID uuid) {
        String sql = "SELECT * FROM player_data WHERE uuid = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return map(rs);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public PlayerData getByName(String name) {
        String sql = "SELECT * FROM player_data WHERE LOWER(name) = LOWER(?)";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return map(rs);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Returns a list of all unique player names that have ever connected
     * from the same IP address as the given player (excluding the player themselves).
     */
    public List<String> getAltAccounts(String currentIp, UUID excludeUuid) {
        Set<String> names = new LinkedHashSet<>();
        String sql = "SELECT DISTINCT pd.name, pd.uuid FROM player_data pd " +
                "WHERE pd.uuid IN (SELECT DISTINCT uuid FROM ip_history WHERE ip = ?)";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, currentIp);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID u = UUID.fromString(rs.getString("uuid"));
                    if (u.equals(excludeUuid)) continue;
                    names.add(rs.getString("name"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return new ArrayList<>(names);
    }

    public List<String> getKnownIps(UUID uuid) {
        List<String> ips = new ArrayList<>();
        String sql = "SELECT DISTINCT ip FROM ip_history WHERE uuid = ? ORDER BY seen_at DESC";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ips.add(rs.getString("ip"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return ips;
    }

    private PlayerData map(ResultSet rs) throws SQLException {
        return new PlayerData(
                UUID.fromString(rs.getString("uuid")),
                rs.getString("name"),
                rs.getString("last_ip"),
                rs.getLong("first_join"),
                rs.getLong("last_join")
        );
    }
}
