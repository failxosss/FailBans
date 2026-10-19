package cz.failban.plugin.anarchy;

import cz.failban.plugin.FailBan;
import cz.failban.plugin.model.PlayerData;
import cz.failban.plugin.util.MessageUtil;
import cz.failban.plugin.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** /anarchyban <player> <time|perm> [reason]   and   /anarchyunban <player> */
public class AnarchyBanCommand implements CommandExecutor, TabCompleter {

    private final FailBan plugin;
    private final AnarchyBanManager manager;

    public AnarchyBanCommand(FailBan plugin, AnarchyBanManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    private void msg(CommandSender s, String text) {
        String prefix = plugin.getConfig().getString("prefix", "");
        s.sendMessage(MessageUtil.toComponent(MessageUtil.color(prefix + text)));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("anarchyunban")) {
            return unban(sender, args);
        }
        return ban(sender, args);
    }

    private boolean ban(CommandSender sender, String[] args) {
        if (!sender.hasPermission("failban.anarchyban")) {
            msg(sender, "&cYou don't have permission to use this command.");
            return true;
        }
        if (args.length < 2) {
            msg(sender, "&cInvalid usage: &e/anarchyban <player> <time|perm> [reason]");
            return true;
        }

        long duration = TimeUtil.parseDuration(args[1]);
        if (duration != -1L && duration <= 0L) {
            msg(sender, "&cInvalid time format. Example: 10s, 30m, 2h, 7d, 1mo, perm");
            return true;
        }

        String reason = args.length > 2
                ? String.join(" ", Arrays.copyOfRange(args, 2, args.length))
                : "Anarchy ban";

        UUID uuid;
        String name;
        Player online = Bukkit.getPlayerExact(args[0]);
        if (online != null) {
            uuid = online.getUniqueId();
            name = online.getName();
        } else {
            PlayerData pd = plugin.getPlayerDataManager().getByName(args[0]);
            if (pd == null) {
                msg(sender, "&cPlayer &e" + args[0] + " &cwas not found (they must have joined at least once).");
                return true;
            }
            uuid = pd.getUuid();
            name = pd.getName();
        }

        String staff = sender.getName();
        manager.add(uuid, name, duration, staff, reason);

        String timeText = duration == -1L ? "permanently" : "for " + TimeUtil.formatDuration(duration);
        msg(sender, "&aPlayer &e" + name + " &awas anarchy-banned " + timeText + " &a(" + reason + ")");

        if (online != null) {
            AnarchyBanListener.moveToAnarchy(plugin, online, manager.get(uuid));
        }
        return true;
    }

    private boolean unban(CommandSender sender, String[] args) {
        if (!sender.hasPermission("failban.anarchyunban")) {
            msg(sender, "&cYou don't have permission to use this command.");
            return true;
        }
        if (args.length < 1) {
            msg(sender, "&cInvalid usage: &e/anarchyunban <player>");
            return true;
        }

        UUID uuid;
        String name;
        Player online = Bukkit.getPlayerExact(args[0]);
        if (online != null) {
            uuid = online.getUniqueId();
            name = online.getName();
        } else {
            PlayerData pd = plugin.getPlayerDataManager().getByName(args[0]);
            if (pd == null) {
                msg(sender, "&cPlayer &e" + args[0] + " &cwas not found (they must have joined at least once).");
                return true;
            }
            uuid = pd.getUuid();
            name = pd.getName();
        }

        if (!manager.remove(uuid)) {
            msg(sender, "&cPlayer &e" + name + " &cdoes not have an anarchy ban.");
            return true;
        }
        msg(sender, "&aAnarchy ban for &e" + name + " &awas removed by &e" + sender.getName());
        if (online != null) {
            msg(online, "&aYour anarchy ban was removed. You can leave the anarchy world now.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[0].toLowerCase())) out.add(p.getName());
            }
        } else if (args.length == 2 && cmd.getName().equalsIgnoreCase("anarchyban")) {
            for (String t : List.of("30m", "1h", "1d", "7d", "30d", "perm")) {
                if (t.startsWith(args[1].toLowerCase())) out.add(t);
            }
        }
        return out;
    }
}
