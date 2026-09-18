package cz.failban.plugin.sus.commands;

import cz.failban.plugin.sus.SusModule;
import cz.failban.plugin.sus.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /susflag <player> <check> [vl] [anticheat]
 *
 * Dej do punishment/alert commandu anticheatu, např. GrimAC:
 *   - "1:1 susflag %player% %check% %vl% GrimAC"
 */
public class SusFlagCommand implements CommandExecutor {

    private final SusModule module;

    public SusFlagCommand(SusModule module) {
        this.module = module;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player && !sender.hasPermission("failban.sus.flag")) {
            sender.sendMessage(Msg.color(module.config()
                    .getString("messages.no-permission", "&cYou don't have permission for that.")));
            return true;
        }
        return handleFlag(module, sender, args);
    }

    static boolean handleFlag(SusModule module, CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(Msg.color("&cUsage: &e/susflag <player> [check] [vl] [anticheat]"));
            return true;
        }

        String name = args[0];
        String check = args.length > 1 ? args[1] : "UNKNOWN";
        int vl = 1;
        if (args.length > 2) {
            try {
                vl = (int) Double.parseDouble(args[2].replace(",", "."));
            } catch (NumberFormatException ignored) {
                vl = 1;
            }
        }
        String anticheat = args.length > 3 ? args[3] : "EXTERNAL";

        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            module.sus().flag(online.getUniqueId(), online.getName(), anticheat, check, vl);
        } else {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
            if (!offline.hasPlayedBefore()) {
                sender.sendMessage(Msg.color(module.config()
                        .getString("messages.player-not-found", "&cPlayer &e{player} &cwas not found.")
                        .replace("{player}", name)));
                return true;
            }
            module.sus().flag(offline.getUniqueId(), offline.getName(), anticheat, check, vl);
        }

        if (sender instanceof Player) {
            sender.sendMessage(Msg.color("&aFlag registered for &e" + name + " &7(" + check + ", vl " + vl + ")"));
        }
        return true;
    }
}
