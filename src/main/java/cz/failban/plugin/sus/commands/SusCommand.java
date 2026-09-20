package cz.failban.plugin.sus.commands;

import cz.failban.plugin.sus.SusModule;
import cz.failban.plugin.sus.commands.SusFlagCommand;
import cz.failban.plugin.sus.data.NotifyManager;
import cz.failban.plugin.sus.data.SusRecord;
import cz.failban.plugin.sus.gui.SusGui;
import cz.failban.plugin.sus.util.Msg;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class SusCommand
implements CommandExecutor,
TabCompleter {
    private static final List<String> SUB = Arrays.asList("add", "remove", "clear", "back", "list", "flag", "hooks", "notify", "reload");
    private final SusModule module;

    public SusCommand(SusModule module) {
        this.module = module;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub;
        if (!sender.hasPermission("failban.sus.use")) {
            sender.sendMessage(this.msg("messages.no-permission", "&cYou don't have permission for that."));
            return true;
        }
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Only a player can open the /sus GUI. Try /sus list.");
                return true;
            }
            Player player = (Player)sender;
            new SusGui(this.module, player).open();
            return true;
        }
        switch (sub = args[0].toLowerCase(Locale.ROOT)) {
            case "back": {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("Players only.");
                    return true;
                }
                Player player = (Player)sender;
                if (!this.module.spectate().isSpectating(player.getUniqueId())) {
                    player.sendMessage(this.msg("messages.not-spectating", "&cYou are not spectating anyone."));
                    return true;
                }
                this.module.spectate().stop(player, true);
                return true;
            }
            case "add": {
                if (!sender.hasPermission("failban.sus.manage")) {
                    sender.sendMessage(this.msg("messages.no-permission", "&cYou don't have permission for that."));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Msg.color("&cUsage: &e/sus add <player> [reason]"));
                    return true;
                }
                OfflinePlayer target = this.resolve(args[1]);
                if (target == null) {
                    sender.sendMessage(this.msg("messages.player-not-found", "&cPlayer &e{player} &cwas not found.").replace("{player}", args[1]));
                    return true;
                }
                String reason = args.length > 2 ? String.join((CharSequence)"_", Arrays.copyOfRange(args, 2, args.length)) : "SUSPICIOUS";
                SusRecord record = this.module.sus().manual(target, reason, sender.getName());
                sender.sendMessage(this.msg("messages.record-added", "&aAdded &e{player} &ato /sus &7({reason})").replace("{player}", record.getName()).replace("{reason}", record.getReason()));
                return true;
            }
            case "remove": {
                if (!sender.hasPermission("failban.sus.manage")) {
                    sender.sendMessage(this.msg("messages.no-permission", "&cYou don't have permission for that."));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Msg.color("&cUsage: &e/sus remove <player>"));
                    return true;
                }
                SusRecord record = this.module.sus().getByName(args[1]);
                if (record == null) {
                    sender.sendMessage(this.msg("messages.no-record", "&cNo sus record found for &e{player}&c.").replace("{player}", args[1]));
                    return true;
                }
                this.module.sus().remove(record.getUuid());
                sender.sendMessage(this.msg("messages.record-removed", "&aRecord of &e{player} &awas removed.").replace("{player}", record.getName()));
                return true;
            }
            case "clear": {
                if (!sender.hasPermission("failban.sus.manage")) {
                    sender.sendMessage(this.msg("messages.no-permission", "&cYou don't have permission for that."));
                    return true;
                }
                int cleared = this.module.sus().clearAll();
                sender.sendMessage(this.msg("messages.records-cleared", "&aCleared &e{amount} &asus records.").replace("{amount}", String.valueOf(cleared)));
                return true;
            }
            case "list": {
                List<SusRecord> records = this.module.sus().sorted("ALL", "LAST_FLAG");
                if (records.isEmpty()) {
                    sender.sendMessage(this.msg("messages.no-records", "&7Nobody is suspicious right now."));
                    return true;
                }
                sender.sendMessage(Msg.color("&8&m---- &d&lSUS &8&m----"));
                for (SusRecord r : records) {
                    sender.sendMessage(Msg.color("&f" + r.getName() + " &8| &d" + r.getReason() + " &8| &7flags: &f" + r.getTotalFlags() + " &8| &7vl: &f" + r.getViolations() + " &8| &7" + Msg.ago(System.currentTimeMillis() - r.getLastFlag()) + " &8| &b" + r.getAnticheat()));
                }
                return true;
            }
            case "flag": {
                if (!sender.hasPermission("failban.sus.flag")) {
                    sender.sendMessage(this.msg("messages.no-permission", "&cYou don't have permission for that."));
                    return true;
                }
                return SusFlagCommand.handleFlag(this.module, sender, Arrays.copyOfRange(args, 1, args.length));
            }
            case "hooks": {
                if (!sender.hasPermission("failban.sus.manage")) {
                    sender.sendMessage(this.msg("messages.no-permission", "&cYou don't have permission for that."));
                    return true;
                }
                sender.sendMessage(Msg.color("&8&m---- &d&lSUS HOOKS &8&m----"));
                if (this.module.hooks().active().isEmpty()) {
                    sender.sendMessage(Msg.color("&7No anticheat hook is active. &7Use the &f/susflag &7bridge in your anticheat config."));
                } else {
                    for (String hook : this.module.hooks().active()) {
                        sender.sendMessage(Msg.color("&a\u2714 &f" + hook));
                    }
                }
                return true;
            }
            case "notify": {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("Players only.");
                    return true;
                }
                if (!sender.hasPermission("failban.sus.notify")) {
                    sender.sendMessage(this.msg("messages.no-permission", "&cYou don't have permission for that."));
                    return true;
                }
                Player player = (Player)sender;
                boolean mute;
                if (args.length >= 2) {
                    String arg = args[1].toLowerCase(Locale.ROOT);
                    if (arg.equals("on")) {
                        mute = false;
                    } else if (arg.equals("off")) {
                        mute = true;
                    } else {
                        sender.sendMessage(Msg.color("&cUsage: &e/sus notify [on|off]"));
                        return true;
                    }
                } else {
                    mute = !NotifyManager.isMuted(player.getUniqueId());
                }
                NotifyManager.setMuted(player.getUniqueId(), mute);
                if (mute) {
                    sender.sendMessage(this.msg("messages.notify-off", "&8[&dSUS&8] &7New-sus notifications: &cOFF &8(&7/sus notify &7to turn them on&8)"));
                } else {
                    sender.sendMessage(this.msg("messages.notify-on", "&8[&dSUS&8] &7New-sus notifications: &aON"));
                }
                return true;
            }
            case "reload": {
                if (!sender.hasPermission("failban.sus.manage")) {
                    sender.sendMessage(this.msg("messages.no-permission", "&cYou don't have permission for that."));
                    return true;
                }
                this.module.reload();
                sender.sendMessage(this.msg("messages.reloaded", "&aSus configuration reloaded."));
                return true;
            }
        }
        sender.sendMessage(Msg.color("&8&m---- &d&lSUS &8&m----"));
        sender.sendMessage(Msg.color("&f/sus &7- open the GUI"));
        sender.sendMessage(Msg.color("&f/sus back &7- leave spectator"));
        sender.sendMessage(Msg.color("&f/sus add <player> [reason] &7- add manually"));
        sender.sendMessage(Msg.color("&f/sus remove <player> &7- remove a record"));
        sender.sendMessage(Msg.color("&f/sus clear &7- remove all records"));
        sender.sendMessage(Msg.color("&f/sus list &7- list in chat"));
        sender.sendMessage(Msg.color("&f/sus hooks &7- show active anticheat hooks"));
        sender.sendMessage(Msg.color("&f/sus notify [on|off] &7- turn the \"new player in /sus\" chat message on/off"));
        sender.sendMessage(Msg.color("&f/sus reload &7- reload sus.yml"));
        return true;
    }

    private OfflinePlayer resolve(String name) {
        Player online = Bukkit.getPlayerExact((String)name);
        if (online != null) {
            return online;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer((String)name);
        return offline.hasPlayedBefore() ? offline : null;
    }

    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        ArrayList<String> out = new ArrayList<String>();
        if (args.length == 1) {
            for (String s : SUB) {
                if (!s.startsWith(args[0].toLowerCase(Locale.ROOT))) continue;
                out.add(s);
            }
            return out;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("notify")) {
            for (String s : Arrays.asList("on", "off")) {
                if (!s.startsWith(args[1].toLowerCase(Locale.ROOT))) continue;
                out.add(s);
            }
            return out;
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("flag"))) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) continue;
                out.add(p.getName());
            }
        }
        return out;
    }

    private String msg(String path, String def) {
        return Msg.color(this.module.config().getString(path, def));
    }
}
