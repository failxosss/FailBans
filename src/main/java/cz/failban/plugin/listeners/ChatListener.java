package cz.failban.plugin.listeners;

import cz.failban.plugin.FailBan;
import cz.failban.plugin.model.Punishment;
import cz.failban.plugin.util.MessageUtil;
import cz.failban.plugin.util.TimeUtil;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ChatListener implements Listener {

    private final FailBan plugin;

    public ChatListener(FailBan plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("failban.bypass.mute")) return;

        UUID uuid = player.getUniqueId();
        Punishment mute = plugin.getPunishmentManager().getActiveMute(uuid);
        if (mute == null) return;

        event.setCancelled(true);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("reason", mute.getReason());
        placeholders.put("time_left", mute.isPermanent() ? "permanently" : TimeUtil.formatDuration(mute.getRemainingMillis()));

        String msg = MessageUtil.getMessage(plugin, "is-muted-chat", placeholders);
        player.sendMessage(MessageUtil.color(msg));
    }
}
