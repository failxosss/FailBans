package cz.failban.plugin.listeners;

import cz.failban.plugin.FailBan;
import cz.failban.plugin.model.Punishment;
import cz.failban.plugin.util.MessageUtil;
import cz.failban.plugin.util.TimeUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerConnectionListener implements Listener {

    private final FailBan plugin;

    public PlayerConnectionListener(FailBan plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        UUID uuid = event.getUniqueId();
        Punishment ban = plugin.getPunishmentManager().getActiveBan(uuid);
        if (ban == null) return;

        String path = ban.isPermanent() ? "kick-screen.ban" : "kick-screen.ban";
        String template = plugin.getConfig().getString(path, "&cYou are banned.");

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("reason", ban.getReason());
        placeholders.put("staff", ban.getStaff());
        placeholders.put("time_left", ban.isPermanent() ? "permanent" : TimeUtil.formatDuration(ban.getRemainingMillis()));

        String text = template;
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            text = text.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
        }

        Component kickComponent = MessageUtil.toComponent(MessageUtil.color(text));
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, kickComponent);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        String name = event.getPlayer().getName();
        String ip = event.getPlayer().getAddress() != null
                ? event.getPlayer().getAddress().getAddress().getHostAddress()
                : "unknown";
        plugin.getPlayerDataManager().recordJoin(uuid, name, ip);
    }
}
