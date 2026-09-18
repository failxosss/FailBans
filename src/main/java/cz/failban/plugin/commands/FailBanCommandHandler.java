package cz.failban.plugin.commands;

import cz.failban.plugin.FailBan;
import cz.failban.plugin.model.PlayerData;
import cz.failban.plugin.model.Punishment;
import cz.failban.plugin.model.PunishmentType;
import cz.failban.plugin.util.DiscordWebhook;
import cz.failban.plugin.util.MessageUtil;
import cz.failban.plugin.util.TimeUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class FailBanCommandHandler
implements CommandExecutor,
TabCompleter {
    private final FailBan plugin;

    public FailBanCommandHandler(FailBan plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase();
        if (!sender.hasPermission("failban." + this.permissionFor(name))) {
            this.msg(sender, "no-permission", null);
            return true;
        }
        switch (name) {
            case "ban": {
                this.handleBan(sender, args, false);
                break;
            }
            case "tempban": {
                this.handleBan(sender, args, true);
                break;
            }
            case "unban": {
                this.handleUnpunishCategory(sender, args, true, false, "unbanned-by-staff", "not-banned");
                break;
            }
            case "kick": {
                this.handleKick(sender, args);
                break;
            }
            case "mute": {
                this.handleMute(sender, args, false);
                break;
            }
            case "tempmute": {
                this.handleMute(sender, args, true);
                break;
            }
            case "unmute": {
                this.handleUnpunishCategory(sender, args, false, true, "unmuted-by-staff", "not-muted");
                break;
            }
            case "warn": {
                this.handleWarn(sender, args, false);
                break;
            }
            case "tempwarn": {
                this.handleWarn(sender, args, true);
                break;
            }
            case "unwarn": {
                this.handleUnwarn(sender, args);
                break;
            }
            case "history": {
                this.handleHistory(sender, args);
                break;
            }
            case "check": {
                this.handleCheck(sender, args);
                break;
            }
            case "banlist": {
                this.handleBanList(sender, args);
                break;
            }
            case "unpunish": {
                this.handleUnpunishById(sender, args);
                break;
            }
            case "change-reason": {
                this.handleChangeReason(sender, args);
                break;
            }
            case "failcheck": {
                this.handleFailCheck(sender, args);
                break;
            }
            case "failban": {
                this.handleFailBanRoot(sender, args);
                break;
            }
            case "alts": {
                this.handleAlts(sender, args);
                break;
            }
            case "punish": {
                this.handlePunish(sender, args);
                break;
            }
            default: {
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

    private void handleBan(CommandSender sender, String[] args, boolean temp) {
        String reason;

        if (!temp && args.length == 2) {
            QuickReason preset = this.getQuickReason(args[1]);
            if (preset != null) {
                switch (preset.type) {
                    case "WARN": {
                        this.applyQuickWarn(sender, args[0], preset);
                        return;
                    }
                    case "KICK": {
                        this.applyQuickKick(sender, args[0], preset);
                        return;
                    }
                    default: {
                        this.applyQuickBan(sender, args[0], preset);
                        return;
                    }
                }
            }
        }

        int minArgs = temp ? 3 : 2;
        int n = minArgs;
        if (args.length < minArgs) {
            this.msg(sender, "invalid-usage", Map.of("usage", temp ? "/tempban <player> <time> <reason>" : "/ban <player> <reason>"));
            return;
        }
        String targetName = args[0];
        Target target = this.resolveTarget(sender, targetName);
        if (target == null) {
            return;
        }
        long duration = -1L;
        if (temp) {
            duration = TimeUtil.parseDuration(args[1]);
            if (duration == -2L) {
                this.msg(sender, "invalid-time", null);
                return;
            }
            reason = String.join((CharSequence)" ", Arrays.copyOfRange(args, 2, args.length));
        } else {
            reason = String.join((CharSequence)" ", Arrays.copyOfRange(args, 1, args.length));
        }
        Punishment existing = this.plugin.getPunishmentManager().getActiveBan(target.uuid);
        if (existing != null) {
            this.msg(sender, "already-banned", Map.of("player", target.name));
            return;
        }
        String ip = target.ip != null ? target.ip : "unknown";
        PunishmentType type = temp ? PunishmentType.TEMPBAN : PunishmentType.BAN;
        Punishment p = this.plugin.getPunishmentManager().addPunishment(target.uuid, target.name, type, reason, sender.getName(), ip, duration);
        Player online = Bukkit.getPlayer((UUID)target.uuid);
        if (online != null) {
            this.kickWithScreen(online, "kick-screen.ban", reason, sender.getName(), temp ? TimeUtil.formatDuration(duration) : "permanent");
        }
        HashMap<String, String> ph = new HashMap<String, String>();
        ph.put("player", target.name);
        ph.put("staff", sender.getName());
        ph.put("reason", reason);
        ph.put("time_left", temp ? TimeUtil.formatDuration(duration) : "permanent");
        this.broadcastNotify(sender, temp ? "tempbanned-by-staff" : "banned-by-staff", ph);
        DiscordWebhook.send(this.plugin, temp ? "Temp Ban" : "Ban", target.name, sender.getName(), reason, temp ? TimeUtil.formatDuration(duration) : "permanent", "15158332");
    }

    private void handleKick(CommandSender sender, String[] args) {
        if (args.length == 2) {
            QuickReason preset = this.getQuickReason(args[1]);
            if (preset != null && preset.type.equals("KICK")) {
                this.applyQuickKick(sender, args[0], preset);
                return;
            }
        }
        if (args.length < 2) {
            this.msg(sender, "invalid-usage", Map.of("usage", "/kick <player> <reason>"));
            return;
        }
        String targetName = args[0];
        Player online = Bukkit.getPlayer((String)targetName);
        if (online == null) {
            this.msg(sender, "player-not-found", Map.of("player", targetName));
            return;
        }
        String reason = String.join((CharSequence)" ", Arrays.copyOfRange(args, 1, args.length));
        String ip = online.getAddress() != null ? online.getAddress().getAddress().getHostAddress() : "unknown";
        Punishment p = this.plugin.getPunishmentManager().addPunishment(online.getUniqueId(), online.getName(), PunishmentType.KICK, reason, sender.getName(), ip, -1L);
        if (p != null) {
            this.plugin.getPunishmentManager().unpunishById(p.getId(), "SYSTEM", "Instant kick");
        }
        this.kickWithScreen(online, "kick-screen.kick", reason, sender.getName(), "-");
        Map<String, String> ph = Map.of("player", online.getName(), "staff", sender.getName(), "reason", reason);
        this.broadcastNotify(sender, "kicked-by-staff", ph);
        DiscordWebhook.send(this.plugin, "Kick", online.getName(), sender.getName(), reason, null, "10181046");
    }

    private void handleMute(CommandSender sender, String[] args, boolean temp) {
        String reason;
        int minArgs = temp ? 3 : 2;
        int n = minArgs;
        if (args.length < minArgs) {
            this.msg(sender, "invalid-usage", Map.of("usage", temp ? "/tempmute <player> <time> <reason>" : "/mute <player> <reason>"));
            return;
        }
        String targetName = args[0];
        Target target = this.resolveTarget(sender, targetName);
        if (target == null) {
            return;
        }
        long duration = -1L;
        if (temp) {
            duration = TimeUtil.parseDuration(args[1]);
            if (duration == -2L) {
                this.msg(sender, "invalid-time", null);
                return;
            }
            reason = String.join((CharSequence)" ", Arrays.copyOfRange(args, 2, args.length));
        } else {
            reason = String.join((CharSequence)" ", Arrays.copyOfRange(args, 1, args.length));
        }
        Punishment existing = this.plugin.getPunishmentManager().getActiveMute(target.uuid);
        if (existing != null) {
            this.msg(sender, "already-muted", Map.of("player", target.name));
            return;
        }
        String ip = target.ip != null ? target.ip : "unknown";
        PunishmentType type = temp ? PunishmentType.TEMPMUTE : PunishmentType.MUTE;
        this.plugin.getPunishmentManager().addPunishment(target.uuid, target.name, type, reason, sender.getName(), ip, duration);
        HashMap<String, String> ph = new HashMap<String, String>();
        ph.put("player", target.name);
        ph.put("staff", sender.getName());
        ph.put("reason", reason);
        ph.put("time_left", temp ? TimeUtil.formatDuration(duration) : "permanent");
        this.broadcastNotify(sender, temp ? "tempmuted-by-staff" : "muted-by-staff", ph);
        DiscordWebhook.send(this.plugin, temp ? "Temp Mute" : "Mute", target.name, sender.getName(), reason, temp ? TimeUtil.formatDuration(duration) : "permanent", "15105570");
    }

    private void handleWarn(CommandSender sender, String[] args, boolean temp) {
        String reason;

        if (!temp && args.length == 2) {
            QuickReason preset = this.getQuickReason(args[1]);
            if (preset != null && preset.type.equals("WARN")) {
                this.applyQuickWarn(sender, args[0], preset);
                return;
            }
        }

        int minArgs = temp ? 3 : 2;
        int n = minArgs;
        if (args.length < minArgs) {
            this.msg(sender, "invalid-usage", Map.of("usage", temp ? "/tempwarn <player> <time> <reason>" : "/warn <player> <reason>"));
            return;
        }
        String targetName = args[0];
        Target target = this.resolveTarget(sender, targetName);
        if (target == null) {
            return;
        }
        long duration = -1L;
        if (temp) {
            duration = TimeUtil.parseDuration(args[1]);
            if (duration == -2L) {
                this.msg(sender, "invalid-time", null);
                return;
            }
            reason = String.join((CharSequence)" ", Arrays.copyOfRange(args, 2, args.length));
        } else {
            reason = String.join((CharSequence)" ", Arrays.copyOfRange(args, 1, args.length));
        }
        String ip = target.ip != null ? target.ip : "unknown";
        PunishmentType type = temp ? PunishmentType.TEMPWARN : PunishmentType.WARN;
        this.plugin.getPunishmentManager().addPunishment(target.uuid, target.name, type, reason, sender.getName(), ip, duration);
        Player online = Bukkit.getPlayer((UUID)target.uuid);
        if (online != null) {
            this.msg((CommandSender)online, "warn-notify-target", Map.of("reason", reason));
        }
        Map<String, String> ph = Map.of("player", target.name, "staff", sender.getName(), "reason", reason);
        this.broadcastNotify(sender, "warned-by-staff", ph);
        DiscordWebhook.send(this.plugin, temp ? "Temp Warn" : "Warn", target.name, sender.getName(), reason, temp ? TimeUtil.formatDuration(duration) : "permanent", "16776960");
        this.checkWarnEscalation(sender, target);
    }

    private void checkWarnEscalation(CommandSender sender, Target target) {
        long warns = this.plugin.getPunishmentManager().getHistory(target.uuid).stream().filter(p -> p.getType().isWarnType()).count();
        String ip = target.ip != null ? target.ip : "unknown";
        Player online = Bukkit.getPlayer((UUID)target.uuid);
        if (warns == 3L) {
            Punishment p2;
            String reason = "Automatic kick after 3 warns";
            if (online != null) {
                this.kickWithScreen(online, "kick-screen.kick", reason, "FailBan", "-");
            }
            if ((p2 = this.plugin.getPunishmentManager().addPunishment(target.uuid, target.name, PunishmentType.KICK, reason, "FailBan", ip, -1L)) != null) {
                this.plugin.getPunishmentManager().unpunishById(p2.getId(), "SYSTEM", "Instant kick");
            }
            this.broadcastNotify(sender, "kicked-by-staff", Map.of("player", target.name, "staff", "FailBan", "reason", reason));
            DiscordWebhook.send(this.plugin, "Auto-Kick", target.name, "FailBan", reason, null, "10181046");
            return;
        }
        if (warns == 5L || warns == 10L || warns == 15L) {
            String timeLeft;
            if (this.plugin.getPunishmentManager().getActiveBan(target.uuid) != null) {
                return;
            }
            long duration = warns == 5L ? TimeUtil.parseDuration("5h") : (warns == 10L ? TimeUtil.parseDuration("12h") : -1L);
            PunishmentType type = duration == -1L ? PunishmentType.BAN : PunishmentType.TEMPBAN;
            String reason = "Automatic ban after " + warns + " warns";
            this.plugin.getPunishmentManager().addPunishment(target.uuid, target.name, type, reason, "FailBan", ip, duration);
            String string = timeLeft = duration == -1L ? "permanent" : TimeUtil.formatDuration(duration);
            if (online != null) {
                this.kickWithScreen(online, "kick-screen.ban", reason, "FailBan", timeLeft);
            }
            Map<String, String> ph = Map.of("player", target.name, "staff", "FailBan", "reason", reason, "time_left", timeLeft);
            this.broadcastNotify(sender, duration == -1L ? "banned-by-staff" : "tempbanned-by-staff", ph);
            DiscordWebhook.send(this.plugin, "Auto-Ban", target.name, "FailBan", reason, timeLeft, "15158332");
        }
    }

    private void handleUnwarn(CommandSender sender, String[] args) {
        if (args.length < 1) {
            this.msg(sender, "invalid-usage", Map.of("usage", "/unwarn <player> [reason]"));
            return;
        }
        Target target = this.resolveTarget(sender, args[0]);
        if (target == null) {
            return;
        }
        String reason = args.length > 1 ? String.join((CharSequence)" ", Arrays.copyOfRange(args, 1, args.length)) : "Removed";
        List<Punishment> actives = this.plugin.getPunishmentManager().getActivePunishments(target.uuid).stream().filter(p -> p.getType().isWarnType()).collect(Collectors.toList());
        if (actives.isEmpty()) {
            this.msg(sender, "not-muted", Map.of("player", target.name));
            return;
        }
        for (Punishment p2 : actives) {
            this.plugin.getPunishmentManager().unpunishById(p2.getId(), sender.getName(), reason);
        }
        this.broadcastNotify(sender, "unwarned-by-staff", Map.of("player", target.name, "staff", sender.getName()));
        DiscordWebhook.send(this.plugin, "Unwarn", target.name, sender.getName(), reason, null, "3066993");
    }

    private void handleUnpunishCategory(CommandSender sender, String[] args, boolean ban, boolean mute, String successKey, String notFoundKey) {
        if (args.length < 1) {
            this.msg(sender, "invalid-usage", Map.of("usage", "/unban|unmute <player> [reason]"));
            return;
        }
        Target target = this.resolveTarget(sender, args[0]);
        if (target == null) {
            return;
        }
        String reason = args.length > 1 ? String.join((CharSequence)" ", Arrays.copyOfRange(args, 1, args.length)) : "Removed";
        boolean success = this.plugin.getPunishmentManager().unpunishActive(target.uuid, ban, mute, sender.getName(), reason);
        if (!success) {
            this.msg(sender, notFoundKey, Map.of("player", target.name));
            return;
        }
        this.broadcastNotify(sender, successKey, Map.of("player", target.name, "staff", sender.getName()));
        String action = ban ? "Unban" : "Unmute";
        DiscordWebhook.send(this.plugin, action, target.name, sender.getName(), reason, null, "3066993");
    }

    private void handleHistory(CommandSender sender, String[] args) {
        if (args.length < 1) {
            this.msg(sender, "invalid-usage", Map.of("usage", "/history <player>"));
            return;
        }
        Target target = this.resolveTarget(sender, args[0]);
        if (target == null) {
            return;
        }
        List<Punishment> history = this.plugin.getPunishmentManager().getHistory(target.uuid);
        sender.sendMessage(MessageUtil.color("&8&m--------------------"));
        sender.sendMessage(MessageUtil.color("&cPunishment history: &e" + target.name + " &7(" + history.size() + ")"));
        if (history.isEmpty()) {
            sender.sendMessage(MessageUtil.color("&7No records."));
        }
        for (Punishment p : history) {
            sender.sendMessage(MessageUtil.color(this.formatHistoryLine(p)));
        }
        sender.sendMessage(MessageUtil.color("&8&m--------------------"));
    }

    private void handleCheck(CommandSender sender, String[] args) {
        if (args.length < 1) {
            this.msg(sender, "invalid-usage", Map.of("usage", "/check <player>"));
            return;
        }
        Target target = this.resolveTarget(sender, args[0]);
        if (target == null) {
            return;
        }
        List<Punishment> actives = this.plugin.getPunishmentManager().getActivePunishments(target.uuid);
        sender.sendMessage(MessageUtil.color("&8&m--------------------"));
        sender.sendMessage(MessageUtil.color("&cActive punishments: &e" + target.name));
        if (actives.isEmpty()) {
            sender.sendMessage(MessageUtil.color("&aThis player has no active punishments."));
        }
        for (Punishment p : actives) {
            sender.sendMessage(MessageUtil.color(this.formatHistoryLine(p)));
        }
        sender.sendMessage(MessageUtil.color("&8&m--------------------"));
    }

    private void handleBanList(CommandSender sender, String[] args) {
        List<Punishment> bans = this.plugin.getPunishmentManager().getActiveBans();
        int perPage = 8;
        int page = 1;
        if (args.length >= 1) {
            try {
                page = Math.max(1, Integer.parseInt(args[0]));
            }
            catch (NumberFormatException numberFormatException) {
                // empty catch block
            }
        }
        int maxPage = Math.max(1, (int)Math.ceil((double)bans.size() / (double)perPage));
        page = Math.min(page, maxPage);
        sender.sendMessage(MessageUtil.color("&8&m--------------------"));
        sender.sendMessage(MessageUtil.color("&cActive bans &7(page " + page + "/" + maxPage + ", total " + bans.size() + ")"));
        int from = (page - 1) * perPage;
        int to = Math.min(bans.size(), from + perPage);
        for (int i = from; i < to; ++i) {
            sender.sendMessage(MessageUtil.color(this.formatHistoryLine(bans.get(i))));
        }
        sender.sendMessage(MessageUtil.color("&8&m--------------------"));
    }

    private void handleUnpunishById(CommandSender sender, String[] args) {
        int id;
        if (args.length < 1) {
            this.msg(sender, "invalid-usage", Map.of("usage", "/unpunish <id> [reason]"));
            return;
        }
        try {
            id = Integer.parseInt(args[0]);
        }
        catch (NumberFormatException e) {
            this.msg(sender, "invalid-usage", Map.of("usage", "/unpunish <id> [reason]"));
            return;
        }
        String reason = args.length > 1 ? String.join((CharSequence)" ", Arrays.copyOfRange(args, 1, args.length)) : "Removed";
        boolean success = this.plugin.getPunishmentManager().unpunishById(id, sender.getName(), reason);
        if (!success) {
            this.msg(sender, "unpunish-not-found", Map.of("id", String.valueOf(id)));
            return;
        }
        this.msg(sender, "unpunish-success", Map.of("id", String.valueOf(id), "staff", sender.getName()));
        Punishment p = this.plugin.getPunishmentManager().getById(id);
        String playerName = p != null ? p.getPlayerName() : "ID #" + id;
        DiscordWebhook.send(this.plugin, "Unpunish (#" + id + ")", playerName, sender.getName(), reason, null, "3066993");
    }

    private void handleChangeReason(CommandSender sender, String[] args) {
        int id;
        if (args.length < 2) {
            this.msg(sender, "invalid-usage", Map.of("usage", "/change-reason <id> <new reason>"));
            return;
        }
        try {
            id = Integer.parseInt(args[0]);
        }
        catch (NumberFormatException e) {
            this.msg(sender, "invalid-usage", Map.of("usage", "/change-reason <id> <new reason>"));
            return;
        }
        String newReason = String.join((CharSequence)" ", Arrays.copyOfRange(args, 1, args.length));
        boolean success = this.plugin.getPunishmentManager().changeReason(id, newReason);
        if (!success) {
            this.msg(sender, "change-reason-not-found", Map.of("id", String.valueOf(id)));
            return;
        }
        this.msg(sender, "change-reason-success", Map.of("id", String.valueOf(id), "reason", newReason));
        Punishment p = this.plugin.getPunishmentManager().getById(id);
        String playerName = p != null ? p.getPlayerName() : "ID #" + id;
        DiscordWebhook.send(this.plugin, "Reason Changed (#" + id + ")", playerName, sender.getName(), newReason, null, "3447003");
    }

    private void handleFailCheck(CommandSender sender, String[] args) {
        if (args.length < 1) {
            this.msg(sender, "invalid-usage", Map.of("usage", "/failcheck <player>"));
            return;
        }
        Target target = this.resolveTarget(sender, args[0]);
        if (target == null) {
            return;
        }
        List<String> knownIps = this.plugin.getPlayerDataManager().getKnownIps(target.uuid);
        String currentIp = target.ip != null ? target.ip : (knownIps.isEmpty() ? "unknown" : knownIps.get(0));
        List<String> alts = this.plugin.getPlayerDataManager().getAltAccounts(currentIp, target.uuid);
        List<Punishment> history = this.plugin.getPunishmentManager().getHistory(target.uuid);
        long bans = history.stream().filter(p -> p.getType().isBanType()).count();
        long mutes = history.stream().filter(p -> p.getType().isMuteType()).count();
        long warns = history.stream().filter(p -> p.getType().isWarnType()).count();
        long kicks = history.stream().filter(p -> p.getType() == PunishmentType.KICK).count();
        sender.sendMessage(MessageUtil.color("&8&m--------------------------------"));
        sender.sendMessage(MessageUtil.color("&c&lFailCheck &7\u00bb &e" + target.name));
        sender.sendMessage(MessageUtil.color("&7Current IP: &f" + currentIp));
        sender.sendMessage(MessageUtil.color("&7Known IP addresses: &f" + (knownIps.isEmpty() ? "none" : String.join((CharSequence)", ", knownIps))));
        sender.sendMessage(MessageUtil.color("&7Alt accounts (same IP): " + (String)(alts.isEmpty() ? "&anone" : "&c" + String.join((CharSequence)", ", alts))));
        sender.sendMessage(MessageUtil.color("&7Bany: &c" + bans + " &7| Kicky: &e" + kicks + " &7| Muty: &6" + mutes + " &7| Warny: &e" + warns));
        List<Punishment> active = this.plugin.getPunishmentManager().getActivePunishments(target.uuid);
        sender.sendMessage(MessageUtil.color("&7Currently active punishments: &f" + active.size()));
        for (Punishment p2 : active) {
            sender.sendMessage(MessageUtil.color(" &8- " + this.formatHistoryLine(p2)));
        }
        sender.sendMessage(MessageUtil.color("&8&m--------------------------------"));
    }

    private void handleAlts(CommandSender sender, String[] args) {
        if (args.length < 1) {
            this.msg(sender, "invalid-usage", Map.of("usage", "/alts <player>"));
            return;
        }
        Target target = this.resolveTarget(sender, args[0]);
        if (target == null) {
            return;
        }
        List<String> knownIps = this.plugin.getPlayerDataManager().getKnownIps(target.uuid);
        String currentIp = target.ip != null ? target.ip : (knownIps.isEmpty() ? null : knownIps.get(0));

        sender.sendMessage(MessageUtil.color("&8&m--------------------"));
        sender.sendMessage(MessageUtil.color("&cAlt accounts \u00bb &e" + target.name));

        if (currentIp == null) {
            sender.sendMessage(MessageUtil.color("&7No known IP for this player."));
            sender.sendMessage(MessageUtil.color("&8&m--------------------"));
            return;
        }

        List<String> alts = this.plugin.getPlayerDataManager().getAltAccounts(currentIp, target.uuid);
        if (alts.isEmpty()) {
            sender.sendMessage(MessageUtil.color("&aNo alt accounts found."));
        } else {
            for (String alt : alts) {
                sender.sendMessage(MessageUtil.color(" &8- &f" + alt));
            }
        }
        sender.sendMessage(MessageUtil.color("&8&m--------------------"));
    }

    private QuickReason getQuickReason(String key) {
        int annotationIdx = key.indexOf('\u00A0');
        if (annotationIdx >= 0) {
            key = key.substring(0, annotationIdx);
        }
        String base = "quick-reasons." + key.toLowerCase();
        String reason = this.plugin.getConfig().getString(base + ".reason");
        if (reason == null) {
            return null;
        }
        String type = this.plugin.getConfig().getString(base + ".type", "BAN").toUpperCase();
        String timeStr = this.plugin.getConfig().getString(base + ".time", "perm");
        long duration = timeStr.equalsIgnoreCase("perm") ? -1L : TimeUtil.parseDuration(timeStr);
        if (duration == -2L) {
            duration = -1L;
        }
        return new QuickReason(reason, type, duration);
    }

    private String describeQuickReason(String key) {
        String base = "quick-reasons." + key.toLowerCase();
        String type = this.plugin.getConfig().getString(base + ".type", "BAN").toUpperCase();
        if (type.equals("BAN")) {
            String timeStr = this.plugin.getConfig().getString(base + ".time", "perm");
            if (timeStr == null || timeStr.equalsIgnoreCase("perm")) {
                return "BAN PERM";
            }
            return "BAN " + timeStr;
        }
        return type;
    }

    private void handlePunish(CommandSender sender, String[] args) {
        if (args.length < 2) {
            this.msg(sender, "invalid-usage", Map.of("usage", "/punish <player> <key>"));
            return;
        }
        QuickReason preset = this.getQuickReason(args[1]);
        if (preset == null) {
            sender.sendMessage(MessageUtil.color("&cUnknown punishment key: &e" + args[1]));
            return;
        }
        switch (preset.type) {
            case "WARN": {
                this.applyQuickWarn(sender, args[0], preset);
                break;
            }
            case "KICK": {
                this.applyQuickKick(sender, args[0], preset);
                break;
            }
            default: {
                this.applyQuickBan(sender, args[0], preset);
                break;
            }
        }
    }

    private void applyQuickWarn(CommandSender sender, String targetName, QuickReason preset) {
        Target target = this.resolveTarget(sender, targetName);
        if (target == null) {
            return;
        }
        String ip = target.ip != null ? target.ip : "unknown";
        this.plugin.getPunishmentManager().addPunishment(target.uuid, target.name, PunishmentType.WARN, preset.reason, sender.getName(), ip, -1L);
        Player online = Bukkit.getPlayer((UUID) target.uuid);
        if (online != null) {
            this.msg((CommandSender) online, "warn-notify-target", Map.of("reason", preset.reason));
        }
        Map<String, String> ph = Map.of("player", target.name, "staff", sender.getName(), "reason", preset.reason);
        this.broadcastNotify(sender, "warned-by-staff", ph);
        DiscordWebhook.send(this.plugin, "Warn", target.name, sender.getName(), preset.reason, null, "16776960");
        this.checkWarnEscalation(sender, target);
    }

    private void applyQuickKick(CommandSender sender, String targetName, QuickReason preset) {
        Player online = Bukkit.getPlayer((String) targetName);
        if (online == null) {
            this.msg(sender, "player-not-found", Map.of("player", targetName));
            return;
        }
        String ip = online.getAddress() != null ? online.getAddress().getAddress().getHostAddress() : "unknown";
        Punishment p = this.plugin.getPunishmentManager().addPunishment(online.getUniqueId(), online.getName(), PunishmentType.KICK, preset.reason, sender.getName(), ip, -1L);
        if (p != null) {
            this.plugin.getPunishmentManager().unpunishById(p.getId(), "SYSTEM", "Instant kick");
        }
        this.kickWithScreen(online, "kick-screen.kick", preset.reason, sender.getName(), "-");
        Map<String, String> ph = Map.of("player", online.getName(), "staff", sender.getName(), "reason", preset.reason);
        this.broadcastNotify(sender, "kicked-by-staff", ph);
        DiscordWebhook.send(this.plugin, "Kick", online.getName(), sender.getName(), preset.reason, null, "10181046");
    }

    private void applyQuickBan(CommandSender sender, String targetName, QuickReason preset) {
        Target target = this.resolveTarget(sender, targetName);
        if (target == null) {
            return;
        }
        Punishment existing = this.plugin.getPunishmentManager().getActiveBan(target.uuid);
        if (existing != null) {
            this.msg(sender, "already-banned", Map.of("player", target.name));
            return;
        }
        boolean temp = preset.durationMillis != -1L;
        String ip = target.ip != null ? target.ip : "unknown";
        PunishmentType type = temp ? PunishmentType.TEMPBAN : PunishmentType.BAN;
        this.plugin.getPunishmentManager().addPunishment(target.uuid, target.name, type, preset.reason, sender.getName(), ip, preset.durationMillis);

        Player online = Bukkit.getPlayer((UUID) target.uuid);
        if (online != null) {
            this.kickWithScreen(online, "kick-screen.ban", preset.reason, sender.getName(), temp ? TimeUtil.formatDuration(preset.durationMillis) : "permanent");
        }

        HashMap<String, String> ph = new HashMap<>();
        ph.put("player", target.name);
        ph.put("staff", sender.getName());
        ph.put("reason", preset.reason);
        ph.put("time_left", temp ? TimeUtil.formatDuration(preset.durationMillis) : "permanent");
        this.broadcastNotify(sender, temp ? "tempbanned-by-staff" : "banned-by-staff", ph);
        DiscordWebhook.send(this.plugin, temp ? "Temp Ban" : "Ban", target.name, sender.getName(), preset.reason, temp ? TimeUtil.formatDuration(preset.durationMillis) : "permanent", "15158332");
    }

    private void handleFailBanRoot(CommandSender sender, String[] args) {
        if (args.length < 1) {
            this.sendHelp(sender);
            return;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            this.plugin.reloadConfig();
            this.msg(sender, "reload-success", null);
        } else if (args[0].equalsIgnoreCase("help")) {
            this.sendHelp(sender);
        } else {
            this.sendHelp(sender);
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(MessageUtil.color("&8&m--------------------------------"));
        sender.sendMessage(MessageUtil.color("&c&lFailBan &7- Help"));
        String[] lines = new String[]{"/ban <player> <reason>", "/tempban <player> <time> <reason>", "/unban <player> [reason]", "/kick <player> <reason>", "/mute <player> <reason>", "/tempmute <player> <time> <reason>", "/unmute <player> [reason]", "/warn <player> <reason>", "/tempwarn <player> <time> <reason>", "/unwarn <player> [reason]", "/history <player>", "/check <player>", "/banlist [page]", "/unpunish <id> [reason]", "/change-reason <id> <reason>", "/failcheck <player>", "/failban reload", "/failban help"};
        for (String l : lines) {
            sender.sendMessage(MessageUtil.color("&7- &e" + l));
        }
        sender.sendMessage(MessageUtil.color("&8&m--------------------------------"));
    }

    private void kickWithScreen(Player online, String path, String reason, String staff, String timeLeft) {
        String template = this.plugin.getConfig().getString(path, "&cYou have been kicked.");
        template = template.replace("{reason}", reason == null ? "" : reason)
                .replace("{staff}", staff == null ? "" : staff)
                .replace("{time_left}", timeLeft == null ? "" : timeLeft)
                .replace("{date}", MessageUtil.formatDate(System.currentTimeMillis()));
        Component component = MessageUtil.toComponent(MessageUtil.color(template));
        online.kick(component);
    }

    private void broadcastNotify(CommandSender sender, String key, Map<String, String> placeholders) {
        String message = MessageUtil.getMessage(this.plugin, key, placeholders);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.hasPermission("failban.notify")) continue;
            p.sendMessage(MessageUtil.color(message));
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(MessageUtil.color(message));
        } else if (!sender.hasPermission("failban.notify")) {
            sender.sendMessage(MessageUtil.color(message));
        }
        this.plugin.getLogger().info(this.stripColor(message));
    }

    private String stripColor(String s) {
        return s.replaceAll("&[0-9a-fk-or]", "");
    }

    private void msg(CommandSender sender, String key, Map<String, String> placeholders) {
        sender.sendMessage(MessageUtil.color(MessageUtil.getMessage(this.plugin, key, placeholders)));
    }

    private String formatHistoryLine(Punishment p) {
        String format = this.plugin.getConfig().getString("history-format", "&8#{id} &7[{type}] &e{player} &7- &f{reason}");
        return format.replace("{id}", String.valueOf(p.getId())).replace("{type}", p.getType().name()).replace("{player}", p.getPlayerName()).replace("{reason}", p.getReason() == null ? "-" : p.getReason()).replace("{staff}", p.getStaff() == null ? "-" : p.getStaff()).replace("{date}", MessageUtil.formatDate(p.getCreatedAt()));
    }

    private Target resolveTarget(CommandSender sender, String name) {
        Player online = Bukkit.getPlayer((String)name);
        if (online != null) {
            String ip = online.getAddress() != null ? online.getAddress().getAddress().getHostAddress() : null;
            return new Target(online.getUniqueId(), online.getName(), ip);
        }
        PlayerData data = this.plugin.getPlayerDataManager().getByName(name);
        if (data != null) {
            return new Target(data.getUuid(), data.getName(), data.getLastIp());
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer((String)name);
        if (offline.hasPlayedBefore() && offline.getUniqueId() != null) {
            return new Target(offline.getUniqueId(), offline.getName() != null ? offline.getName() : name, null);
        }
        this.msg(sender, "player-not-found", Map.of("player", name));
        return null;
    }

    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase();
        if (name.equals("failban") && args.length == 1) {
            return this.filter(List.of("reload", "help"), args[0]);
        }
        if (!(args.length != 1 || name.equals("unpunish") || name.equals("change-reason") || name.equals("failban"))) {
            List<String> names = Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
            return this.filter(names, args[0]);
        }
        if (args.length == 2 && (name.equals("tempban") || name.equals("tempmute") || name.equals("tempwarn"))) {
            return this.filter(List.of("10m", "1h", "1d", "7d", "30d", "perm"), args[1]);
        }
        if (args.length == 2 && (name.equals("ban") || name.equals("punish") || name.equals("kick") || name.equals("warn"))) {
            List<String> keys = new ArrayList<>();
            if (this.plugin.getConfig().isConfigurationSection("quick-reasons")) {
                keys.addAll(this.plugin.getConfig().getConfigurationSection("quick-reasons").getKeys(false));
            }
            String lower = args[1].toLowerCase();
            return keys.stream()
                    .filter(k -> k.toLowerCase().startsWith(lower))
                    .map(k -> k + "\u00A0(" + this.describeQuickReason(k) + ")")
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> options, String input) {
        String lower = input.toLowerCase();
        return options.stream().filter(o -> o.toLowerCase().startsWith(lower)).collect(Collectors.toList());
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

    private static class QuickReason {
        final String reason;
        final String type;
        final long durationMillis;

        QuickReason(String reason, String type, long durationMillis) {
            this.reason = reason;
            this.type = type;
            this.durationMillis = durationMillis;
        }
    }
}
