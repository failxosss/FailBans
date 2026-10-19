package cz.failban.plugin.voice;

import cz.failban.plugin.FailBan;
import cz.failban.plugin.model.PlayerData;
import cz.failban.plugin.model.Punishment;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps voice chat permissions (voicechat.speak / voicechat.listen) in sync with FailBan mutes.
 * Uses LuckPerms console commands, so no extra dependency is needed.
 */
public class VoiceMuteSync implements Listener {

    private static final Set<String> TRIGGER_COMMANDS = Set.of("mute", "tempmute", "unmute", "unpunish");

    private final FailBan plugin;
    private final File file;

    /** UUID -> expiresAt of the mute we applied the voice mute for. */
    private final Map<UUID, Long> applied = new ConcurrentHashMap<>();
    /** UUIDs that should be checked on the next reconcile (e.g. offline targets of /mute). */
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();

    private BukkitTask task;
    private boolean warnedNoLuckPerms = false;

    public VoiceMuteSync(FailBan plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "voicemutes.yml");
        load();
    }

    // ------------------------------------------------------------ lifecycle

    public void start() {
        long seconds = Math.max(2, plugin.getConfig().getInt("voice-mute.check-interval-seconds", 10));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::reconcile, 100L, seconds * 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        save();
    }

    // ------------------------------------------------------------ config helpers

    private boolean enabled() {
        return plugin.getConfig().getBoolean("voice-mute.enabled", true);
    }

    private List<String> nodes() {
        List<String> list = plugin.getConfig().getStringList("voice-mute.nodes");
        if (list.isEmpty()) list = List.of("voicechat.speak", "voicechat.listen");
        return list;
    }

    private boolean luckPermsAvailable() {
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") != null) return true;
        if (!warnedNoLuckPerms) {
            warnedNoLuckPerms = true;
            plugin.getLogger().warning("voice-mute: LuckPerms was not found, voice mute is disabled.");
        }
        return false;
    }

    // ------------------------------------------------------------ persistence

    private void load() {
        applied.clear();
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = yml.getConfigurationSection("applied");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            try {
                applied.put(UUID.fromString(key), sec.getLong(key));
            } catch (IllegalArgumentException ignored) {
                // broken UUID, skip
            }
        }
    }

    private synchronized void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<UUID, Long> e : applied.entrySet()) {
            yml.set("applied." + e.getKey(), e.getValue());
        }
        try {
            plugin.getDataFolder().mkdirs();
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save voicemutes.yml: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------ core logic

    /** Checks all online players, players with an applied voice mute and "dirty" players. */
    private void reconcile() {
        if (!enabled() || !luckPermsAvailable()) return;

        Set<UUID> check = new HashSet<>(applied.keySet());
        check.addAll(dirty);
        dirty.clear();
        for (Player p : Bukkit.getOnlinePlayers()) {
            check.add(p.getUniqueId());
        }
        for (UUID uuid : check) {
            sync(uuid);
        }
    }

    private void sync(UUID uuid) {
        Punishment mute = plugin.getPunishmentManager().getActiveMute(uuid);
        if (mute != null && (!mute.isActive() || mute.isExpired())) {
            mute = null;
        }

        Long current = applied.get(uuid);

        if (mute != null) {
            long expiresAt = mute.getExpiresAt();
            if (current == null || current != expiresAt) {
                applyVoiceMute(uuid, mute);
                applied.put(uuid, expiresAt);
                save();
            }
        } else if (current != null) {
            removeVoiceMute(uuid);
            applied.remove(uuid);
            save();
        }
    }

    private void run(String command) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }

    private void applyVoiceMute(UUID uuid, Punishment mute) {
        String target = uuid.toString();
        for (String node : nodes()) {
            if (mute.isPermanent()) {
                run("lp user " + target + " permission set " + node + " false");
            } else {
                long seconds = Math.max(1L, mute.getRemainingMillis() / 1000L);
                run("lp user " + target + " permission settemp " + node + " false " + seconds + "s replace");
            }
        }
        plugin.getLogger().info("voice-mute: voice muted " + uuid + (mute.isPermanent() ? " (permanent)" : ""));
    }

    private void removeVoiceMute(UUID uuid) {
        String target = uuid.toString();
        for (String node : nodes()) {
            run("lp user " + target + " permission unset " + node);
            run("lp user " + target + " permission unsettemp " + node);
        }
        plugin.getLogger().info("voice-mute: voice unmuted " + uuid);
    }

    // ------------------------------------------------------------ events

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent e) {
        if (!enabled() || !luckPermsAvailable()) return;
        sync(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent e) {
        handleCommand(e.getMessage());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsoleCommand(ServerCommandEvent e) {
        handleCommand("/" + e.getCommand());
    }

    /** After /mute, /tempmute, /unmute, /unpunish: re-check shortly after the command ran. */
    private void handleCommand(String message) {
        if (!enabled()) return;
        if (message == null || message.length() < 2) return;

        String[] parts = message.substring(1).trim().split("\\s+");
        String label = parts[0].toLowerCase();
        int colon = label.indexOf(':');
        if (colon >= 0) label = label.substring(colon + 1);
        if (!TRIGGER_COMMANDS.contains(label)) return;

        if (parts.length >= 2 && !label.equals("unpunish")) {
            Player online = Bukkit.getPlayerExact(parts[1]);
            if (online != null) {
                dirty.add(online.getUniqueId());
            } else {
                PlayerData pd = plugin.getPlayerDataManager().getByName(parts[1]);
                if (pd != null) dirty.add(pd.getUuid());
            }
        }

        // the command itself runs after this event, so check a few ticks later
        Bukkit.getScheduler().runTaskLater(plugin, this::reconcile, 3L);
    }
}
