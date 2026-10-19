package cz.failban.plugin.anarchy;

import cz.failban.plugin.FailBan;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Stores anarchy bans in plugins/FailBan/anarchybans.yml. */
public class AnarchyBanManager {

    /** until = 0 means permanent. */
    public record Entry(UUID uuid, String name, long until, String staff, String reason) {
        public boolean isPermanent() {
            return until == 0L;
        }

        public long remainingMillis() {
            return isPermanent() ? -1L : Math.max(0L, until - System.currentTimeMillis());
        }
    }

    private final FailBan plugin;
    private final File file;
    private final Map<UUID, Entry> entries = new ConcurrentHashMap<>();

    public AnarchyBanManager(FailBan plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "anarchybans.yml");
        load();
    }

    private void load() {
        entries.clear();
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = yml.getConfigurationSection("bans");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                ConfigurationSection s = sec.getConfigurationSection(key);
                if (s == null) continue;
                entries.put(uuid, new Entry(
                        uuid,
                        s.getString("name", "?"),
                        s.getLong("until", 0L),
                        s.getString("staff", "Console"),
                        s.getString("reason", "")));
            } catch (IllegalArgumentException ignored) {
                // broken UUID in file, skip
            }
        }
    }

    private synchronized void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Entry e : entries.values()) {
            String base = "bans." + e.uuid();
            yml.set(base + ".name", e.name());
            yml.set(base + ".until", e.until());
            yml.set(base + ".staff", e.staff());
            yml.set(base + ".reason", e.reason());
        }
        try {
            plugin.getDataFolder().mkdirs();
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save anarchybans.yml: " + ex.getMessage());
        }
    }

    /** Returns the active anarchy ban, or null (expired bans are removed). */
    public Entry get(UUID uuid) {
        Entry e = entries.get(uuid);
        if (e == null) return null;
        if (!e.isPermanent() && e.until() <= System.currentTimeMillis()) {
            entries.remove(uuid);
            save();
            return null;
        }
        return e;
    }

    public void add(UUID uuid, String name, long durationMillis, String staff, String reason) {
        long until = durationMillis < 0 ? 0L : System.currentTimeMillis() + durationMillis;
        entries.put(uuid, new Entry(uuid, name, until, staff, reason));
        save();
    }

    public boolean remove(UUID uuid) {
        boolean removed = entries.remove(uuid) != null;
        if (removed) save();
        return removed;
    }
}
