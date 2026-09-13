package cz.failban.plugin.util;

import cz.failban.plugin.FailBan;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;

public class DiscordWebhook {

    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    // Predefined embed colors (decimal RGB)
    public static final String COLOR_BAN = "15158332";    // red
    public static final String COLOR_MUTE = "15105570";   // orange
    public static final String COLOR_WARN = "16776960";   // yellow
    public static final String COLOR_KICK = "10181046";   // purple
    public static final String COLOR_UNDO = "3066993";    // green
    public static final String COLOR_INFO = "3447003";    // blue

    public static void send(FailBan plugin, String action, String player, String staff,
                             String reason, String duration, String colorDecimal) {
        if (!plugin.getConfig().getBoolean("discord.enabled", false)) return;
        String url = plugin.getConfig().getString("discord.webhook-url", "");
        if (url == null || url.isBlank()) return;

        String username = plugin.getConfig().getString("discord.username", "FailBan");
        String avatar = plugin.getConfig().getString("discord.avatar-url", "");

        StringBuilder fields = new StringBuilder();
        fields.append(field("Player", player, true));
        fields.append(",").append(field("Staff", staff, true));
        if (duration != null) {
            fields.append(",").append(field("Duration", duration, true));
        }
        fields.append(",").append(field("Reason", (reason == null || reason.isBlank()) ? "-" : reason, false));

        long color;
        try {
            color = Long.parseLong(colorDecimal);
        } catch (Exception e) {
            color = Long.parseLong(COLOR_INFO);
        }

        String json = ("""
            {
              "username": "%s",
              "avatar_url": "%s",
              "embeds": [
                {
                  "title": "%s",
                  "color": %d,
                  "fields": [%s],
                  "footer": {"text": "FailBan"},
                  "timestamp": "%s"
                }
              ]
            }
            """).formatted(escape(username), escape(avatar), escape(action), color, fields, Instant.now().toString());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        CLIENT.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .exceptionally(ex -> {
                    plugin.getLogger().warning("Failed to send log to Discord: " + ex.getMessage());
                    return null;
                });
    }

    private static String field(String name, String value, boolean inline) {
        return "{\"name\": \"%s\", \"value\": \"%s\", \"inline\": %b}".formatted(escape(name), escape(value), inline);
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
