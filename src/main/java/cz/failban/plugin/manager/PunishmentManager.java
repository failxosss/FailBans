package cz.failban.plugin.manager;

import cz.failban.plugin.FailBan;
import cz.failban.plugin.model.Punishment;
import cz.failban.plugin.model.PunishmentType;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PunishmentManager {
    private final FailBan plugin;

    public PunishmentManager(FailBan plugin) {
        this.plugin = plugin;
    }

    private Connection conn() {
        return this.plugin.getDatabaseManager().getConnection();
    }

    public Punishment addPunishment(UUID uuid, String playerName, PunishmentType type, String reason, String staff, String ip, long durationMillis) {
        long now = System.currentTimeMillis();
        long expiresAt = durationMillis == -1L ? -1L : now + durationMillis;
        String sql = "INSERT INTO punishments (uuid, player_name, type, reason, staff, ip, created_at, expires_at, active) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1)";
        try (PreparedStatement ps = this.conn().prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, playerName);
            ps.setString(3, type.name());
            ps.setString(4, reason);
            ps.setString(5, staff);
            ps.setString(6, ip);
            ps.setLong(7, now);
            ps.setLong(8, expiresAt);
            ps.executeUpdate();

            int id = -1;
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    id = keys.getInt(1);
                }
            }
            return new Punishment(id, uuid, playerName, type, reason, staff, ip, now, expiresAt, true, null, null);
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Punishment getActiveByCategory(UUID uuid, boolean banType, boolean muteType) {
        String sql = "SELECT * FROM punishments WHERE uuid = ? AND active = 1 ORDER BY created_at DESC";
        try (PreparedStatement ps = this.conn().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Punishment p = this.mapRow(rs);
                    boolean matches = (banType && p.getType().isBanType()) || (muteType && p.getType().isMuteType());
                    if (!matches) continue;
                    if (p.isExpired()) {
                        this.expire(p.getId());
                        continue;
                    }
                    return p;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public Punishment getActiveBan(UUID uuid) {
        return this.getActiveByCategory(uuid, true, false);
    }

    public Punishment getActiveBanByIp(String ip) {
        if (ip == null) {
            return null;
        }
        String sql = "SELECT * FROM punishments WHERE ip = ? AND active = 1 AND (type = 'BAN' OR type = 'TEMPBAN') ORDER BY created_at DESC";
        try (PreparedStatement ps = this.conn().prepareStatement(sql)) {
            ps.setString(1, ip);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Punishment p = this.mapRow(rs);
                    if (p.isExpired()) {
                        this.expire(p.getId());
                        continue;
                    }
                    return p;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public Punishment getActiveMute(UUID uuid) {
        return this.getActiveByCategory(uuid, false, true);
    }

    public boolean unpunishActive(UUID uuid, boolean banType, boolean muteType, String staff, String reason) {
        Punishment p = this.getActiveByCategory(uuid, banType, muteType);
        if (p == null) {
            return false;
        }
        return this.unpunishById(p.getId(), staff, reason);
    }

    public boolean unpunishById(int id, String staff, String reason) {
        String sql = "UPDATE punishments SET active = 0, removed_by = ?, remove_reason = ? WHERE id = ? AND active = 1";
        try (PreparedStatement ps = this.conn().prepareStatement(sql)) {
            ps.setString(1, staff);
            ps.setString(2, reason);
            ps.setInt(3, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean changeReason(int id, String newReason) {
        String sql = "UPDATE punishments SET reason = ? WHERE id = ?";
        try (PreparedStatement ps = this.conn().prepareStatement(sql)) {
            ps.setString(1, newReason);
            ps.setInt(2, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void expire(int id) {
        this.unpunishById(id, "SYSTEM", "Punishment expired.");
    }

    public Punishment getById(int id) {
        String sql = "SELECT * FROM punishments WHERE id = ?";
        try (PreparedStatement ps = this.conn().prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return this.mapRow(rs);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<Punishment> getHistory(UUID uuid) {
        ArrayList<Punishment> list = new ArrayList<Punishment>();
        String sql = "SELECT * FROM punishments WHERE uuid = ? ORDER BY created_at DESC";
        try (PreparedStatement ps = this.conn().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(this.mapRow(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public List<Punishment> getActivePunishments(UUID uuid) {
        ArrayList<Punishment> list = new ArrayList<Punishment>();
        for (Punishment p : this.getHistory(uuid)) {
            if (!p.isActive()) continue;
            if (p.isExpired()) {
                this.expire(p.getId());
                continue;
            }
            list.add(p);
        }
        return list;
    }

    public List<Punishment> getActiveBans() {
        ArrayList<Punishment> list = new ArrayList<Punishment>();
        String sql = "SELECT * FROM punishments WHERE active = 1 AND (type = 'BAN' OR type = 'TEMPBAN') ORDER BY created_at DESC";
        try (PreparedStatement ps = this.conn().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Punishment p = this.mapRow(rs);
                if (p.isExpired()) {
                    this.expire(p.getId());
                    continue;
                }
                list.add(p);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    private Punishment mapRow(ResultSet rs) throws SQLException {
        return new Punishment(
                rs.getInt("id"),
                UUID.fromString(rs.getString("uuid")),
                rs.getString("player_name"),
                PunishmentType.valueOf(rs.getString("type")),
                rs.getString("reason"),
                rs.getString("staff"),
                rs.getString("ip"),
                rs.getLong("created_at"),
                rs.getLong("expires_at"),
                rs.getInt("active") == 1,
                rs.getString("removed_by"),
                rs.getString("remove_reason")
        );
    }
}
