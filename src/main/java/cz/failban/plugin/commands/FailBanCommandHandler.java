package cz.failban.plugin.commands;

import cz.failban.plugin.FailBan;
import cz.failban.plugin.model.PlayerData;
import cz.failban.plugin.model.Punishment;
import cz.failban.plugin.model.PunishmentType;
import cz.failban.plugin.util.DiscordWebhook;
import cz.failban.plugin.util.MessageUtil;
import cz.failban.plugin.util.TimeUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class FailBanCommandHandler implements CommandExecutor, TabCompleter {

    private final FailBan plugin;

    public FailBanCommandHandler(FailBan plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase();

        if (!sender.hasPermission("failban." + permissionFor(name))) {
            msg(sender, "no-permission", null);
            return true;
        }

        switch (name) {
            case "ban" -> handleBan(sender, args, false);
            case "tempban" -> handleBan(sender, args, true);
            case "unban" -> handleUnpunishCategory(sender, args, true, false, "unbanned-by-staff", "not-banned");
            case "kick" -> handleKick(sender, args);
            case "mute" -> handleMute(sender, args, false);
            case "tempmute" -> handleMute(sender, args, true);
            case "unmute" -> handleUnpunishCategory(sender, args, false, true, "unmuted-by-staff", "not-muted");
            case "warn" -> handleWarn(sender, args, false);
            case "tempwarn" -> handleWarn(sender, args, true);
            case "unwarn" -> handleUnwarn(sender, args);
            case "history" -> handleHistory(sender, args);
            case "check" -> handleCheck(sender, args);
            case "banlist" -> handleBanList(sender, args);
            case "unpunish" -> handleUnpunishById(sender, args);
            case "change-reason" -> handleChangeReason(sender, args);
            case "failcheck" -> handleFailCheck(sender, args);
            case "failban" -> handleFailBanRoot(sender, args);
            default -> {
                return false;
            }
        }
        return true;
    }

    private String permissionFor(String cmd) {
        return switch (cmd) {
            case "change-reason" -> "changereason";
            case "unwarn", "tempwarn" -> "warn";
            default -> cmd;
        };
    }

    // ================= BAN / TEMPBAN =================

    private void handleBan(CommandSender sender, String[] args, boolean temp) {
        int minArgs = temp ? 3 : 2;
        if (args.length < minArgs) {
            msg(sender, "invalid-usage", Map.of("usage", temp ? "/tempban <player> <time> <reason>" : "/ban <player> <reason>"));
            return;
        }

        String targetName = args[0];
        Target target = resolveTarget(sender, targetName);
        if (target == null) return;

        long duration = -1;
        String reason;
        if (temp) {
            duration = TimeUtil.parseDuration(args[1]);
            if (duration == -2) {
                msg(sender, "invalid-time", null);
                return;
            }
            reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        } else {
            reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        }

        Punishment existing = plugin.getPunishmentManager().getActiveBan(target.uuid);
        if (existing != null) {
            msg(sender, "already-banned", Map.of("player", target.name));
            return;
        }

        String ip = target.ip != null ? target.ip : "unknown";
        PunishmentType type = temp ? PunishmentType.TEMPBAN : PunishmentType.BAN;
        Punishment p = plugin.getPunishmentManager().addPunishment(target.uuid, target.name, type, reason,
                sender.getName(), ip, duration);

        Player online = Bukkit.getPlayer(target.uuid);
        if (online != null) {
            kickWithScreen(online, "kick-screen.ban", reason, sender.getName(),
                    temp ? TimeUtil.formatDuration(duration) : "permanent");
        }

        Map<String, String> ph = new HashMap<>();
        ph.put("player", target.name);
        ph.put("staff", sender.getName());
        ph.put("reason", reason);
        ph.put("time_left", temp ? TimeUtil.formatDuration(duration) : "permanent");
        broadcastNotify(sender, temp ? "tempbanned-by-staff" : "banned-by-staff", ph);

        DiscordWebhook.send(plugin, temp ? "Temp Ban" : "Ban", target.name, sender.getName(), reason,
                temp ? TimeUtil.formatDuration(duration) : "permanent", DiscordWebhook.COLOR_BAN);
    }

    // ================= KICK =================

    private void handleKick(CommandSender sender, String[] args) {
        if (args.length < 2) {
            msg(sender, "invalid-usage", Map.of("usage", "/kick <player> <reason>"));
            return;
        }
        String targetName = args[0];
        Player online = Bukkit.getPlayer(targetName);
        if (online == null) {
            msg(sender, "player-not-found", Map.of("player", targetName));
            return;
        }
        String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        String ip = online.getAddress() != null ? online.getAddress().getAddress().getHostAddress() : "unknown";

        Punishment p = plugin.getPunishmentManager().addPunishment(online.getUniqueId(), online.getName(),
                PunishmentType.KICK, reason, sender.getName(), ip, -1);
        if (p != null) plugin.getPunishmentManager().unpunishById(p.getId(), "SYSTEM", "Instant kick");

        kickWithScreen(online, "kick-screen.kick", reason, sender.getName(), "-");

        Map<String, String> ph = Map.of("player", online.getName(), "staff", sender.getName(), "reason", reason);
        broadcastNotify(sender, "kicked-by-staff", ph);

        DiscordWebhook.send(plugin, "Kick", online.getName(), sender.getName(), reason, null, DiscordWebhook.COLOR_KICK);
    }

    // ================= MUTE / TEMPMUTE =================

    private void handleMute(CommandSender sender, String[] args, boolean temp) {
        int minArgs = temp ? 3 : 2;
        if (args.length < minArgs) {
            msg(sender, "invalid-usage", Map.of("usage", temp ? "/tempmute <player> <time> <reason>" : "/mute <player> <reason>"));
            return;
        }
        String targetName = args[0];
        Target target = resolveTarget(sender, targetName);
        if (target == null) return;

        long duration = -1;
        String reason;
        if (temp) {
            duration = TimeUtil.parseDuration(args[1]);
            if (duration == -2) {
                msg(sender, "invalid-time", null);
                return;
            }
            reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        } else {
            reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        }

        Punishment existing = plugin.getPunishmentManager().getActiveMute(target.uuid);
        if (existing != null) {
            msg(sender, "already-muted", Map.of("player", target.name));
            return;
        }

        String ip = target.ip != null ? target.ip : "unknown";
        PunishmentType type = temp ? PunishmentType.TEMPMUTE : PunishmentType.MUTE;
        plugin.getPunishmentManager().addPunishment(target.uuid, target.name, type, reason,
                sender.getName(), ip, duration);

        Map<String, String> ph = new HashMap<>();
        ph.put("player", target.name);
        ph.put("staff", sender.getName());
        ph.put("reason", reason);
        ph.put("time_left", temp ? TimeUtil.formatDuration(duration) : "permanent");
        broadcastNotify(sender, temp ? "tempmuted-by-staff" : "muted-by-staff", ph);

        DiscordWebhook.send(plugin, temp ? "Temp Mute" : "Mute", target.name, sender.getName(), reason,
                temp ? TimeUtil.formatDuration(duration) : "permanent", DiscordWebhook.COLOR_MUTE);
    }

    // ================= WARN / TEMPWARN =================

    private void handleWarn(CommandSender sender, String[] args, boolean temp) {
        int minArgs = temp ? 3 : 2;
        if (args.length < minArgs) {
            msg(sender, "invalid-usage", Map.of("usage", temp ? "/tempwarn <player> <time> <reason>" : "/warn <player> <reason>"));
            return;
        }
        String targetName = args[0];
        Target target = resolveTarget(sender, targetName);
        if (target == null) return;

        long duration = -1;
        String reason;
        if (temp) {
            duration = TimeUtil.parseDuration(args[1]);
            if (duration == -2) {
                msg(sender, "invalid-time", null);
                return;
            }
            reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        } else {
            reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        }

        String ip = target.ip != null ? target.ip : "unknown";
        PunishmentType type = temp ? PunishmentType.TEMPWARN : PunishmentType.WARN;
        plugin.getPunishmentManager().addPunishment(target.uuid, target.name, type, reason,
                sender.getName(), ip, duration);

        Player online = Bukkit.getPlayer(target.uuid);
        if (online != null) {
            msg(online, "warn-notify-target", Map.of("reason", reason));
        }

        Map<String, String> ph = Map.of("player", target.name, "staff", sender.getName(), "reason", reason);
        broadcastNotify(sender, "warned-by-staff", ph);

        DiscordWebhook.send(plugin, temp ? "Temp Warn" : "Warn", target.name, sender.getName(), reason,
                temp ? TimeUtil.formatDuration(duration) : "permanent", DiscordWebhook.COLOR_WARN);
    }

    private void handleUnwarn(CommandSender sender, String[] args) {
        if (args.length < 1) {
            msg(sender, "invalid-usage", Map.of("usage", "/unwarn <player> [reason]"));
            return;
        }
        Target target = resolveTarget(sender, args[0]);
        if (target == null) return;

        String reason = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "Removed";
        List<Punishment> actives = plugin.getPunishmentManager().getActivePunishments(target.uuid).stream()
                .filter(p -> p.getType().isWarnType()).collect(Collectors.toList());

        if (actives.isEmpty()) {
            msg(sender, "not-muted", Map.of("player", target.name));
            return;
        }
        for (Punishment p : actives) {
            plugin.getPunishmentManager().unpunishById(p.getId(), sender.getName(), reason);
        }
        broadcastNotify(sender, "unwarned-by-staff", Map.of("player", target.name, "staff", sender.getName()));

        DiscordWebhook.send(plugin, "Unwarn", target.name, sender.getName(), reason, null, DiscordWebhook.COLOR_UNDO);
    }

    // ================= UNBAN / UNMUTE (generic by category) =================

    private void handleUnpunishCategory(CommandSender sender, String[] args, boolean ban, boolean mute,
                                         String successKey, String notFoundKey) {
        if (args.length < 1) {
            msg(sender, "invalid-usage", Map.of("usage", "/unban|unmute <player> [reason]"));
            return;
        }
        Target target = resolveTarget(sender, args[0]);
        if (target == null) return;

        String reason = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "Removed";
        boolean success = plugin.getPunishmentManager().unpunishActive(target.uuid, ban, mute, sender.getName(), reason);
        if (!success) {
            msg(sender, notFoundKey, Map.of("player", target.name));
            return;
        }
        broadcastNotify(sender, successKey, Map.of("player", target.name, "staff", sender.getName()));

        String action = ban ? "Unban" : "Unmute";
        DiscordWebhook.send(plugin, action, target.name, sender.getName(), reason, null, DiscordWebhook.COLOR_UNDO);
    }

    // ================= HISTORY / CHECK =================

    private void handleHistory(CommandSender sender, String[] args) {
        if (args.length < 1) {
            msg(sender, "invalid-usage", Map.of("usage", "/history <player>"));
            return;
        }
        Target target = resolveTarget(sender, args[0]);
        if (target == null) return;

        List<Punishment> history = plugin.getPunishmentManager().getHistory(target.uuid);
        sender.sendMessage(MessageUtil.color("&8&m--------------------"));
        sender.sendMessage(MessageUtil.color("&cPunishment history: &e" + target.name + " &7(" + history.size() + ")"));
        if (history.isEmpty()) {
            sender.sendMessage(MessageUtil.color("&7No records."));
        }
        for (Punishment p : history) {
            sender.sendMessage(MessageUtil.color(formatHistoryLine(p)));
        }
        sender.sendMessage(MessageUtil.color("&8&m--------------------"));
    }

    private void handleCheck(CommandSender sender, String[] args) {
        if (args.length < 1) {
            msg(sender, "invalid-usage", Map.of("usage", "/check <player>"));
            return;
        }
        Target target = resolveTarget(sender, args[0]);
        if (target == null) return;

        List<Punishment> actives = plugin.getPunishmentManager().getActivePunishments(target.uuid);
        sender.sendMessage(MessageUtil.color("&8&m--------------------"));
        sender.sendMessage(MessageUtil.color("&cActive punishments: &e" + target.name));
        if (actives.isEmpty()) {
            sender.sendMessage(MessageUtil.color("&aThis player has no active punishments."));
        }
        for (Punishment p : actives) {
            sender.sendMessage(MessageUtil.color(formatHistoryLine(p)));
        }
        sender.sendMessage(MessageUtil.color("&8&m--------------------"));
    }

    private void handleBanList(CommandSender sender, String[] args) {
        List<Punishment> bans = plugin.getPunishmentManager().getActiveBans();
        int perPage = 8;
        int page = 1;
        if (args.length >= 1) {
            try { page = Math.max(1, Integer.parseInt(args[0])); } catch (NumberFormatException ignored) {}
        }
        int maxPage = Math.max(1, (int) Math.ceil(bans.size() / (double) perPage));
        page = Math.min(page, maxPage);

        sender.sendMessage(MessageUtil.color("&8&m--------------------"));
        sender.sendMessage(MessageUtil.color("&cActive bans &7(page " + page + "/" + maxPage + ", total " + bans.size() + ")"));
        int from = (page - 1) * perPage;
        int to = Math.min(bans.size(), from + perPage);
        for (int i = from; i < to; i++) {
            sender.sendMessage(MessageUtil.color(formatHistoryLine(bans.get(i))));
        }
        sender.sendMessage(MessageUtil.color("&8&m--------------------"));
    }

    // ================= UNPUNISH / CHANGE-REASON (by ID) =================

    private void handleUnpunishById(CommandSender sender, String[] args) {
        if (args.length < 1) {
            msg(sender, "invalid-usage", Map.of("usage", "/unpunish <id> [reason]"));
            return;
        }
        int id;
        try { id = Integer.parseInt(args[0]); } catch (NumberFormatException e) {
            msg(sender, "invalid-usage", Map.of("usage", "/unpunish <id> [reason]"));
            return;
        }
        String reason = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "Removed";
        boolean success = plugin.getPunishmentManager().unpunishById(id, sender.getName(), reason);
        if (!success) {
            msg(sender, "unpunish-not-found", Map.of("id", String.valueOf(id)));
            return;
        }
        msg(sender, "unpunish-success", Map.of("id", String.valueOf(id), "staff", sender.getName()));

        Punishment p = plugin.getPunishmentManager().getById(id);
        String playerName = p != null ? p.getPlayerName() : ("ID #" + id);
        DiscordWebhook.send(plugin, "Unpunish (#" + id + ")", playerName, sender.getName(), reason, null, DiscordWebhook.COLOR_UNDO);
    }

    private void handleChangeReason(CommandSender sender, String[] args) {
        if (args.length < 2) {
            msg(sender, "invalid-usage", Map.of("usage", "/change-reason <id> <new reason>"));
            return;
        }
        int id;
        try { id = Integer.parseInt(args[0]); } catch (NumberFormatException e) {
            msg(sender, "invalid-usage", Map.of("usage", "/change-reason <id> <new reason>"));
            return;
        }
        String newReason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        boolean success = plugin.getPunishmentManager().changeReason(id, newReason);
        if (!success) {
            msg(sender, "change-reason-not-found", Map.of("id", String.valueOf(id)));
            return;
        }
        msg(sender, "change-reason-success", Map.of("id", String.valueOf(id), "reason", newReason));

        Punishment p = plugin.getPunishmentManager().getById(id);
        String playerName = p != null ? p.getPlayerName() : ("ID #" + id);
        DiscordWebhook.send(plugin, "Reason Changed (#" + id + ")", playerName, sender.getName(), newReason, null, DiscordWebhook.COLOR_INFO);
    }

    // ================= FAILCHECK =================

    private void handleFailCheck(CommandSender sender, String[] args) {
        if (args.length < 1) {
            msg(sender, "invalid-usage", Map.of("usage", "/failcheck <player>"));
            return;
        }
        Target target = resolveTarget(sender, args[0]);
        if (target == null) return;

        List<String> knownIps = plugin.getPlayerDataManager().getKnownIps(target.uuid);
        String currentIp = target.ip != null ? target.ip : (knownIps.isEmpty() ? "unknown" : knownIps.get(0));

        List<String> alts = plugin.getPlayerDataManager().getAltAccounts(currentIp, target.uuid);
        List<Punishment> history = plugin.getPunishmentManager().getHistory(target.uuid);

        long bans = history.stream().filter(p -> p.getType().isBanType()).count();
        long mutes = history.stream().filter(p -> p.getType().isMuteType()).count();
        long warns = history.stream().filter(p -> p.getType().isWarnType()).count();
        long kicks = history.stream().filter(p -> p.getType() == PunishmentType.KICK).count();

        sender.sendMessage(MessageUtil.color("&8&m--------------------------------"));
        sender.sendMessage(MessageUtil.color("&c&lFailCheck &7» &e" + target.name));
        sender.sendMessage(MessageUtil.color("&7Current IP: &f" + currentIp));
        sender.sendMessage(MessageUtil.color("&7Known IP addresses: &f" + (knownIps.isEmpty() ? "none" : String.join(", ", knownIps))));
        sender.sendMessage(MessageUtil.color("&7Alt accounts (same IP): " + (alts.isEmpty() ? "&anone" : "&c" + String.join(", ", alts))));
        sender.sendMessage(MessageUtil.color("&7Bany: &c" + bans + " &7| Kicky: &e" + kicks + " &7| Muty: &6" + mutes + " &7| Warny: &e" + warns));

        List<Punishment> active = plugin.getPunishmentManager().getActivePunishments(target.uuid);
        sender.sendMessage(MessageUtil.color("&7Currently active punishments: &f" + active.size()));
        for (Punishment p : active) {
            sender.sendMessage(MessageUtil.color(" &8- " + formatHistoryLine(p)));
        }
        sender.sendMessage(MessageUtil.color("&8&m--------------------------------"));
    }

    // ================= FAILBAN ROOT (reload/help) =================

    private void handleFailBanRoot(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sendHelp(sender);
            return;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            msg(sender, "reload-success", null);
        } else if (args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
        } else {
            sendHelp(sender);
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(MessageUtil.color("&8&m--------------------------------"));
        sender.sendMessage(MessageUtil.color("&c&lFailBan &7- Help"));
        String[] lines = {
                "/ban <player> <reason>", "/tempban <player> <time> <reason>", "/unban <player> [reason]",
                "/kick <player> <reason>", "/mute <player> <reason>", "/tempmute <player> <time> <reason>",
                "/unmute <player> [reason]", "/warn <player> <reason>", "/tempwarn <player> <time> <reason>",
                "/unwarn <player> [reason]", "/history <player>", "/check <player>", "/banlist [page]",
                "/unpunish <id> [reason]", "/change-reason <id> <reason>", "/failcheck <player>",
                "/failban reload", "/failban help"
        };
        for (String l : lines) {
            sender.sendMessage(MessageUtil.color("&7- &e" + l));
        }
        sender.sendMessage(MessageUtil.color("&8&m--------------------------------"));
    }

    // ================= HELPERS =================

    private void kickWithScreen(Player online, String path, String reason, String staff, String timeLeft) {
        String template = plugin.getConfig().getString(path, "&cByl jsi vyhozen.");
        template = template.replace("{reason}", reason == null ? "" : reason)
                .replace("{staff}", staff == null ? "" : staff)
                .replace("{time_left}", timeLeft == null ? "" : timeLeft);
        Component component = MessageUtil.toComponent(MessageUtil.color(template));
        online.kick(component);
    }

    private void broadcastNotify(CommandSender sender, String key, Map<String, String> placeholders) {
        String message = MessageUtil.getMessage(plugin, key, placeholders);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("failban.notify")) {
                p.sendMessage(MessageUtil.color(message));
            }
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(MessageUtil.color(message));
        } else if (!sender.hasPermission("failban.notify")) {
            sender.sendMessage(MessageUtil.color(message));
        }
        plugin.getLogger().info(stripColor(message));
    }

    private String stripColor(String s) {
        return s.replaceAll("&[0-9a-fk-or]", "");
    }

    private void msg(CommandSender sender, String key, Map<String, String> placeholders) {
        sender.sendMessage(MessageUtil.color(MessageUtil.getMessage(plugin, key, placeholders)));
    }

    private String formatHistoryLine(Punishment p) {
        String format = plugin.getConfig().getString("history-format",
                "&8#{id} &7[{type}] &e{player} &7- &f{reason}");
        return format.replace("{id}", String.valueOf(p.getId()))
                .replace("{type}", p.getType().name())
                .replace("{player}", p.getPlayerName())
                .replace("{reason}", p.getReason() == null ? "-" : p.getReason())
                .replace("{staff}", p.getStaff() == null ? "-" : p.getStaff())
                .replace("{date}", MessageUtil.formatDate(p.getCreatedAt()));
    }

    /**
     * Resolves the command target - by online player, or by database lookup (offline players who have joined before).
     */
    private Target resolveTarget(CommandSender sender, String name) {
        Player online = Bukkit.getPlayer(name);
        if (online != null) {
            String ip = online.getAddress() != null ? online.getAddress().getAddress().getHostAddress() : null;
            return new Target(online.getUniqueId(), online.getName(), ip);
        }

        PlayerData data = plugin.getPlayerDataManager().getByName(name);
        if (data != null) {
            return new Target(data.getUuid(), data.getName(), data.getLastIp());
        }

        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (offline.hasPlayedBefore() && offline.getUniqueId() != null) {
            return new Target(offline.getUniqueId(), offline.getName() != null ? offline.getName() : name, null);
        }

        msg(sender, "player-not-found", Map.of("player", name));
        return null;
    }

    private static class Target {
        final UUID uuid;
        final String name;
        final String ip;

        Target(UUID uuid, String name, String ip) {
            this.uuid = uuid;
            this.name = name;
            this.ip = ip;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase();

        if (name.equals("failban") && args.length == 1) {
            return filter(List.of("reload", "help"), args[0]);
        }

        if (args.length == 1 && !name.equals("unpunish") && !name.equals("change-reason") && !name.equals("failban")) {
            List<String> names = Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
            return filter(names, args[0]);
        }

        if (args.length == 2 && (name.equals("tempban") || name.equals("tempmute") || name.equals("tempwarn"))) {
            return filter(List.of("10m", "1h", "1d", "7d", "30d", "perm"), args[1]);
        }

        return Collections.emptyList();
    }

    private List<String> filter(List<String> options, String input) {
        String lower = input.toLowerCase();
        return options.stream().filter(o -> o.toLowerCase().startsWith(lower)).collect(Collectors.toList());
    }
}
