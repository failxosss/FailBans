package cz.failban.plugin.sus.commands;

import cz.failban.plugin.sus.SusModule;
import cz.failban.plugin.sus.data.SusRecord;
import cz.failban.plugin.sus.gui.SusGui;
import cz.failban.plugin.sus.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class SusCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUB = Arrays.asList(
            "add", "remove", "clear", "back", "list", "flag", "hooks", "reload");

    private final SusModule module;

    public SusCommand(SusModule module) {
        this.module = module;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("failban.sus.use")) {
            sender.sendMessage(msg("messages.no-permission", "&cYou don't have permission for that."));
            return true;
        }

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only a player can open the /sus GUI. Try /sus list.");
                return true;
            }
            new SusGui(module, player).open();
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "back" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Players only.");
                    return true;
                }
                if (!module.spectate().isSpectating(player.getUniqueId())) {
                    player.sendMessage(msg("messages.not-spectating", "&cYou are not spectating anyone."));
                    return true;
                }
                module.spectate().stop(player, true);
                return true;
            }
            case "add" -> {
                if (!sender.hasPermission("failban.sus.manage")) {
                    sender.sendMessage(msg("messages.no-permission", "&cYou don't have permission for that."));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Msg.color("&cUsage: &e/sus add <player> [reason]"));
                    return true;
                }
                OfflinePlayer target = resolve(args[1]);
                if (target == null) {
                    sender.sendMessage(msg("messages.player-not-found", "&cPlayer &e{player} &cwas not found.")
                            .replace("{player}", args[1]));
                    return true;
                }
                String reason = args.length > 2
                        ? String.join("_", Arrays.copyOfRange(args, 2, args.length))
                        : "SUSPICIOUS";
                SusRecord record = module.sus().manual(target, reason, sender.getName());
                sender.sendMessage(msg("messages.record-added", "&aAdded &e{player} &ato /sus &7({reason})")
                        .replace("{player}", record.getName())
                        .replace("{reason}", record.getReason()));
                return true;
            }
            case "remove" -> {
                if (!sender.hasPermission("failban.sus.manage")) {
                    sender.sendMessage(msg("messages.no-permission", "&cYou don't have permission for that."));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Msg.color("&cUsage: &e/sus remove <player>"));
                    return true;
                }
                SusRecord record = module.sus().getByName(args[1]);
                if (record == null) {
                    sender.sendMessage(msg("messages.no-record", "&cNo sus record found for &e{player}&c.")
                            .replace("{player}", args[1]));
                    return true;
                }
                module.sus().remove(record.getUuid());
                sender.sendMessage(msg("messages.record-removed", "&aRecord of &e{player} &awas removed.")
                        .replace("{player}", record.getName()));
                return true;
            }
            case "clear" -> {
                if (!sender.hasPermission("failban.sus.manage")) {
                    sender.sendMessage(msg("messages.no-permission", "&cYou don't have permission for that."));
                    return true;
                }
                int cleared = module.sus().clearAll();
                sender.sendMessage(msg("messages.records-cleared", "&aCleared &e{amount} &asus records.")
                        .replace("{amount}", String.valueOf(cleared)));
                return true;
            }
            case "list" -> {
                List<SusRecord> records = module.sus().sorted("ALL", "LAST_FLAG");
                if (records.isEmpty()) {
                    sender.sendMessage(msg("messages.no-records", "&7Nobody is suspicious right now."));
                    return true;
                }
                sender.sendMessage(Msg.color("&8&m---- &d&lSUS &8&m----"));
                for (SusRecord r : records) {
                    sender.sendMessage(Msg.color("&f" + r.getName() + " &8| &d" + r.getReason()
                            + " &8| &7flags: &f" + r.getTotalFlags()
                            + " &8| &7vl: &f" + r.getViolations()
                            + " &8| &7" + Msg.ago(System.currentTimeMillis() - r.getLastFlag())
                            + " &8| &b" + r.getAnticheat()));
                }
                return true;
            }
            case "flag" -> {
                if (!sender.hasPermission("failban.sus.flag")) {
                    sender.sendMessage(msg("messages.no-permission", "&cYou don't have permission for that."));
                    return true;
                }
                return SusFlagCommand.handleFlag(module, sender,
                        Arrays.copyOfRange(args, 1, args.length));
            }
            case "hooks" -> {
                if (!sender.hasPermission("failban.sus.manage")) {
                    sender.sendMessage(msg("messages.no-permission", "&cYou don't have permission for that."));
                    return true;
                }
                sender.sendMessage(Msg.color("&8&m---- &d&lSUS HOOKS &8&m----"));
                if (module.hooks().active().isEmpty()) {
                    sender.sendMessage(Msg.color("&7No anticheat hook is active. "
                            + "&7Use the &f/susflag &7bridge in your anticheat config."));
                } else {
                    for (String hook : module.hooks().active()) {
                        sender.sendMessage(Msg.color("&a\u2714 &f" + hook));
                    }
                }
                return true;
            }
            case "reload" -> {
                if (!sender.hasPermission("failban.sus.manage")) {
                    sender.sendMessage(msg("messages.no-permission", "&cYou don't have permission for that."));
                    return true;
                }
                module.reload();
                sender.sendMessage(msg("messages.reloaded", "&aSus configuration reloaded."));
                return true;
            }
            default -> {
                sender.sendMessage(Msg.color("&8&m---- &d&lSUS &8&m----"));
                sender.sendMessage(Msg.color("&f/sus &7- open the GUI"));
                sender.sendMessage(Msg.color("&f/sus back &7- leave spectator"));
                sender.sendMessage(Msg.color("&f/sus add <player> [reason] &7- add manually"));
                sender.sendMessage(Msg.color("&f/sus remove <player> &7- remove a record"));
                sender.sendMessage(Msg.color("&f/sus clear &7- remove all records"));
                sender.sendMessage(Msg.color("&f/sus list &7- list in chat"));
                sender.sendMessage(Msg.color("&f/sus hooks &7- show active anticheat hooks"));
                sender.sendMessage(Msg.color("&f/sus reload &7- reload sus.yml"));
                return true;
            }
        }
    }

    private OfflinePlayer resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        return offline.hasPlayedBefore() ? offline : null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : SUB) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(s);
                }
            }
            return out;
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("remove")
                || args[0].equalsIgnoreCase("add")
                || args[0].equalsIgnoreCase("flag"))) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(p.getName());
                }
            }
        }
        return out;
    }

    private String msg(String path, String def) {
        return Msg.color(module.config().getString(path, def));
    }
}
