package cz.failban.plugin.sus.data;

import cz.failban.plugin.sus.SusModule;
import cz.failban.plugin.sus.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SusManager {

    private final SusModule module;
    private final Map<UUID, SusRecord> records = new ConcurrentHashMap<>();
    private final File file;

    private long expireMillis;
    private int minViolations;
    private boolean notifyStaff;

    public SusManager(SusModule module) {
        this.module = module;
        this.file = new File(module.plugin().getDataFolder(), "sus-records.yml");
        reloadSettings();
    }

    public void reloadSettings() {
        this.expireMillis = Math.max(0L, module.config().getLong("records.expire-minutes", 60)) * 60_000L;
        this.minViolations = module.config().getInt("records.min-violations", 1);
        this.notifyStaff = module.config().getBoolean("records.notify-staff", true);
    }

    public int size() {
        return records.size();
    }

    public SusRecord get(UUID uuid) {
        return records.get(uuid);
    }

    public SusRecord getByName(String name) {
        for (SusRecord r : records.values()) {
            if (r.getName().equalsIgnoreCase(name)) {
                return r;
            }
        }
        return null;
    }

    public boolean remove(UUID uuid) {
        return records.remove(uuid) != null;
    }

    public int clearAll() {
        int size = records.size();
        records.clear();
        return size;
    }

    public void flag(UUID uuid, String name, String anticheat, String check, int violations) {
        if (uuid == null || violations < minViolations) {
            return;
        }
        Player online = Bukkit.getPlayer(uuid);
        if (online != null && online.hasPermission("failban.sus.bypass")) {
            return;
        }

        SusRecord record = records.computeIfAbsent(uuid, u -> new SusRecord(u, name));
        boolean isNew = record.getTotalFlags() == 0;

        record.setName(name);
        record.setAnticheat(anticheat);
        record.setLastCheck(check);
        record.countCheck(check);
        record.addFlag();
        record.setViolations(Math.max(record.getViolations(), violations));
        record.setLastFlag(System.currentTimeMillis());
        record.setReason(resolveReason(check, record.getReason()));

        if (online != null) {
            if (Bukkit.isPrimaryThread()) {
                storeLocation(record, online);
            } else {
                Bukkit.getScheduler().runTask(module.plugin(), () -> {
                    if (online.isOnline()) {
                        storeLocation(record, online);
                    }
                });
            }
        }

        if (notifyStaff && isNew) {
            String raw = module.config().getString("messages.new-sus",
                    "&8[&dSUS&8] &f{player} &7was added to /sus &8(&d{reason} &7by &b{anticheat}&8)");
            String msg = Msg.color(raw
                    .replace("{player}", record.getName())
                    .replace("{reason}", record.getReason())
                    .replace("{anticheat}", record.getAnticheat())
                    .replace("{check}", record.getLastCheck())
                    .replace("{vl}", String.valueOf(record.getViolations())));
            Bukkit.getScheduler().runTask(module.plugin(), () -> {
                for (Player staff : Bukkit.getOnlinePlayers()) {
                    if (staff.hasPermission("failban.sus.notify")) {
                        staff.sendMessage(msg);
                        staff.playSound(staff.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.6f);
                    }
                }
            });
        }
    }

    private void storeLocation(SusRecord record, Player player) {
        Location loc = player.getLocation();
        record.setWorld(loc.getWorld() == null ? "unknown" : loc.getWorld().getName());
        record.setPosition(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public SusRecord manual(OfflinePlayer target, String reason, String staffName) {
        String display = reason.toUpperCase(Locale.ROOT).replace(' ', '_');
        SusRecord record = records.computeIfAbsent(target.getUniqueId(),
                u -> new SusRecord(u, target.getName() == null ? "unknown" : target.getName()));
        record.setName(target.getName());
        record.setAnticheat("MANUAL/" + staffName);
        record.setReason(display);
        record.setLastCheck(display);
        record.countCheck(display);
        record.addFlag();
        record.setLastFlag(System.currentTimeMillis());
        Player online = target.getPlayer();
        if (online != null) {
            storeLocation(record, online);
        }
        return record;
    }

    private String resolveReason(String check, String current) {
        if (check == null || check.isEmpty()) {
            return current;
        }
        ConfigurationSection section = module.config().getConfigurationSection("reason-map");
        if (section != null) {
            String lower = check.toLowerCase(Locale.ROOT);
            for (String key : section.getKeys(false)) {
                for (String keyword : key.toLowerCase(Locale.ROOT).split("\\|")) {
                    if (!keyword.isEmpty() && lower.contains(keyword)) {
                        return section.getString(key, "CHEATING");
                    }
                }
            }
        }
        return module.config().getString("reason-map-default", "CHEATING");
    }

    public void purgeExpired() {
        if (expireMillis <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        records.values().removeIf(r -> now - r.getLastFlag() > expireMillis);
    }

    public List<SusRecord> sorted(String worldFilter, String sortMode) {
        List<SusRecord> list = new ArrayList<>(records.values());
        if (worldFilter != null && !worldFilter.equalsIgnoreCase("ALL")) {
            list.removeIf(r -> !environmentOf(r).equalsIgnoreCase(worldFilter));
        }
        Comparator<SusRecord> comparator;
        if ("VIOLATIONS".equalsIgnoreCase(sortMode)) {
            comparator = Comparator.comparingInt(SusRecord::getViolations).reversed();
        } else if ("FLAGS".equalsIgnoreCase(sortMode)) {
            comparator = Comparator.comparingInt(SusRecord::getTotalFlags).reversed();
        } else if ("NAME".equalsIgnoreCase(sortMode)) {
            comparator = Comparator.comparing(r -> r.getName().toLowerCase(Locale.ROOT));
        } else {
            comparator = Comparator.comparingLong(SusRecord::getLastFlag).reversed();
        }
        list.sort(comparator);
        return list;
    }

    public String environmentOf(SusRecord record) {
        World world = Bukkit.getWorld(record.getWorld());
        if (world != null) {
            switch (world.getEnvironment()) {
                case NETHER:
                    return "NETHER";
                case THE_END:
                    return "END";
                default:
                    return "OVERWORLD";
            }
        }
        String lower = record.getWorld().toLowerCase(Locale.ROOT);
        if (lower.contains("nether")) {
            return "NETHER";
        }
        if (lower.endsWith("_end") || lower.contains("end")) {
            return "END";
        }
        return "OVERWORLD";
    }

    public void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("records");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                ConfigurationSection s = root.getConfigurationSection(key);
                if (s == null) {
                    continue;
                }
                SusRecord r = new SusRecord(uuid, s.getString("name", "unknown"));
                r.setReason(s.getString("reason", "CHEATING"));
                r.setAnticheat(s.getString("anticheat", "UNKNOWN"));
                r.setLastCheck(s.getString("last-check", "-"));
                r.setTotalFlags(s.getInt("total-flags", 1));
                r.setViolations(s.getInt("violations", 1));
                r.setFirstFlag(s.getLong("first-flag", System.currentTimeMillis()));
                r.setLastFlag(s.getLong("last-flag", System.currentTimeMillis()));
                r.setWorld(s.getString("world", "unknown"));
                r.setPosition(s.getInt("x"), s.getInt("y"), s.getInt("z"));
                ConfigurationSection checks = s.getConfigurationSection("checks");
                if (checks != null) {
                    for (String c : checks.getKeys(false)) {
                        r.getCheckCounts().put(c, checks.getInt(c));
                    }
                }
                records.put(uuid, r);
            } catch (IllegalArgumentException ignored) {
            }
        }
        purgeExpired();
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (SusRecord r : records.values()) {
            String base = "records." + r.getUuid();
            yaml.set(base + ".name", r.getName());
            yaml.set(base + ".reason", r.getReason());
            yaml.set(base + ".anticheat", r.getAnticheat());
            yaml.set(base + ".last-check", r.getLastCheck());
            yaml.set(base + ".total-flags", r.getTotalFlags());
            yaml.set(base + ".violations", r.getViolations());
            yaml.set(base + ".first-flag", r.getFirstFlag());
            yaml.set(base + ".last-flag", r.getLastFlag());
            yaml.set(base + ".world", r.getWorld());
            yaml.set(base + ".x", r.getX());
            yaml.set(base + ".y", r.getY());
            yaml.set(base + ".z", r.getZ());
            for (Map.Entry<String, Integer> e : r.getCheckCounts().entrySet()) {
                yaml.set(base + ".checks." + e.getKey(), e.getValue());
            }
        }
        try {
            File folder = module.plugin().getDataFolder();
            if (!folder.exists() && !folder.mkdirs()) {
                module.plugin().getLogger().warning("[Sus] Could not create the plugin folder.");
            }
            yaml.save(file);
        } catch (IOException e) {
            module.plugin().getLogger().warning("[Sus] Could not save sus-records.yml: " + e.getMessage());
        }
    }
}
