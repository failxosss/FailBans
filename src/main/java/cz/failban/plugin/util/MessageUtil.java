package cz.failban.plugin.util;

import cz.failban.plugin.FailBan;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

public class MessageUtil {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    public static String color(String input) {
        if (input == null) return "";
        return ChatColor.translateAlternateColorCodes('&', input);
    }

    public static Component toComponent(String legacyText) {
        return LEGACY.deserialize(legacyText);
    }

    public static void send(CommandSender sender, String message) {
        if (message == null || message.isEmpty()) return;
        sender.sendMessage(color(message));
    }

    public static String getMessage(FailBan plugin, String path, Map<String, String> placeholders) {
        String prefix = plugin.getConfig().getString("prefix", "");
        String msg = plugin.getConfig().getString("messages." + path, path);
        msg = prefix + msg;
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                msg = msg.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
            }
        }
        return msg;
    }

    public static String formatDate(long millis) {
        return new SimpleDateFormat("dd.MM.yyyy HH:mm").format(new Date(millis));
    }
}
