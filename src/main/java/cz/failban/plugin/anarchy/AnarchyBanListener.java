package cz.failban.plugin.anarchy;

import cz.failban.plugin.FailBan;
import cz.failban.plugin.util.MessageUtil;
import cz.failban.plugin.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.List;

/** Keeps anarchy-banned players inside the anarchy world. */
public class AnarchyBanListener implements Listener {

    private final FailBan plugin;
    private final AnarchyBanManager manager;

    public AnarchyBanListener(FailBan plugin, AnarchyBanManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    // ------------------------------------------------------------ helpers

    private static String worldName(FailBan plugin) {
        return plugin.getConfig().getString("anarchy-ban.world", "anarchy");
    }

    private static boolean isAnarchyWorld(FailBan plugin, World w) {
        return w != null && w.getName().toLowerCase().startsWith(worldName(plugin).toLowerCase());
    }

    private static String fill(String s, AnarchyBanManager.Entry b) {
        return s.replace("{reason}", b.reason() == null ? "" : b.reason())
                .replace("{staff}", b.staff() == null ? "" : b.staff())
                .replace("{time_left}", b.isPermanent() ? "permanent"
                        : TimeUtil.formatDuration(b.remainingMillis()));
    }

    private static void send(FailBan plugin, Player p, String key, String def, AnarchyBanManager.Entry b) {
        String prefix = plugin.getConfig().getString("prefix", "");
        String raw = plugin.getConfig().getString("anarchy-ban.messages." + key, def);
        p.sendMessage(MessageUtil.toComponent(MessageUtil.color(prefix + fill(raw, b))));
    }

    private static Location spawnOf(FailBan plugin, World w) {
        var cfg = plugin.getConfig();
        if (cfg.contains("anarchy-ban.spawn.x")) {
            return new Location(w,
                    cfg.getDouble("anarchy-ban.spawn.x"),
                    cfg.getDouble("anarchy-ban.spawn.y"),
                    cfg.getDouble("anarchy-ban.spawn.z"),
                    (float) cfg.getDouble("anarchy-ban.spawn.yaw", 0),
                    (float) cfg.getDouble("anarchy-ban.spawn.pitch", 0));
        }
        return w.getSpawnLocation();
    }

    /** Teleports the player to the anarchy world and tells him about the ban. Used by the command too. */
    public static void moveToAnarchy(FailBan plugin, Player p, AnarchyBanManager.Entry ban) {
        if (ban == null) return;
        World w = Bukkit.getWorld(worldName(plugin));
        if (w == null) {
            plugin.getLogger().warning("anarchy-ban: world '" + worldName(plugin) + "' does not exist!");
            return;
        }
        if (!isAnarchyWorld(plugin, p.getWorld())) {
            p.teleport(spawnOf(plugin, w));
        }
        send(plugin, p, "moved",
                "&cYou have an anarchy ban! You were moved to the anarchy world. Reason: &e{reason} &7(time left: {time_left})",
                ban);
    }

    // ------------------------------------------------------------ events

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (manager.get(p.getUniqueId()) == null) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            moveToAnarchy(plugin, p, manager.get(p.getUniqueId()));
        }, 2L);
    }

    /** Dying must not be a way out (bed / main world respawn). */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        if (manager.get(p.getUniqueId()) == null) return;
        World w = Bukkit.getWorld(worldName(plugin));
        if (w == null) return;
        if (!isAnarchyWorld(plugin, e.getRespawnLocation().getWorld())) {
            e.setRespawnLocation(spawnOf(plugin, w));
        }
    }

    /** Blocks /lobby, /hub, /spawn... */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        String msg = e.getMessage();
        if (msg.length() < 2) return;
        String label = msg.substring(1).split(" ")[0].toLowerCase();
        int colon = label.indexOf(':');
        if (colon >= 0) label = label.substring(colon + 1);

        List<String> blocked = plugin.getConfig().getStringList("anarchy-ban.blocked-commands");
        boolean match = false;
        for (String b : blocked) {
            if (b.equalsIgnoreCase(label)) { match = true; break; }
        }
        if (!match) return;

        Player p = e.getPlayer();
        AnarchyBanManager.Entry ban = manager.get(p.getUniqueId());
        if (ban == null) return;

        e.setCancelled(true);
        send(plugin, p, "blocked",
                "&cYou have an anarchy ban, so you cannot leave this world! Reason: &e{reason} &7(time left: {time_left})",
                ban);
    }

    /** Blocks teleports out of the anarchy world (pearls, plugins, portals...). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Location to = e.getTo();
        if (to == null || to.getWorld() == null) return;
        if (isAnarchyWorld(plugin, to.getWorld())) return;

        Player p = e.getPlayer();
        AnarchyBanManager.Entry ban = manager.get(p.getUniqueId());
        if (ban == null) return;

        e.setCancelled(true);
        send(plugin, p, "blocked",
                "&cYou have an anarchy ban, so you cannot leave this world! Reason: &e{reason} &7(time left: {time_left})",
                ban);
    }
}
