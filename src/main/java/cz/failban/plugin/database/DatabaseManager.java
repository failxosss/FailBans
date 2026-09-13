package cz.failban.plugin.database;

import cz.failban.plugin.FailBan;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {

    private final FailBan plugin;
    private Connection connection;

    public DatabaseManager(FailBan plugin) {
        this.plugin = plugin;
    }

    public void connect() {
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            File dbFile = new File(plugin.getDataFolder(),
                    plugin.getConfig().getString("storage.file", "database.db"));
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            createTables();
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to connect to the database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                connect();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return connection;
    }

    private void createTables() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS punishments (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid TEXT NOT NULL,
                    player_name TEXT NOT NULL,
                    type TEXT NOT NULL,
                    reason TEXT,
                    staff TEXT,
                    ip TEXT,
                    created_at INTEGER,
                    expires_at INTEGER,
                    active INTEGER DEFAULT 1,
                    removed_by TEXT,
                    remove_reason TEXT
                );
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS player_data (
                    uuid TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    last_ip TEXT,
                    first_join INTEGER,
                    last_join INTEGER
                );
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS ip_history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid TEXT NOT NULL,
                    ip TEXT NOT NULL,
                    seen_at INTEGER
                );
            """);

            st.execute("CREATE INDEX IF NOT EXISTS idx_punishments_uuid ON punishments(uuid);");
            st.execute("CREATE INDEX IF NOT EXISTS idx_ip_history_ip ON ip_history(ip);");
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
