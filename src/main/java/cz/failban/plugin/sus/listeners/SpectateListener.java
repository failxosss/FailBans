package cz.failban.plugin.sus.listeners;

import cz.failban.plugin.sus.SusModule;
import cz.failban.plugin.sus.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public class SpectateListener implements Listener {

    private final SusModule module;

    public SpectateListener(SusModule module) {
        this.module = module;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(module.plugin(), () -> {
            if (!player.isOnline()) {
                return;
            }
            module.spectate().hideVanishedFrom(player);
            module.spectate().restoreOnJoin(player);
        }, 10L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (module.spectate().isSpectating(player.getUniqueId())) {
            module.spectate().stop(player, false);
        }

        for (UUID staffUuid : module.spectate().spectatorsOf(player.getUniqueId())) {
            Player staff = Bukkit.getPlayer(staffUuid);
            if (staff == null || !staff.isOnline()) {
                continue;
            }
            staff.sendMessage(Msg.color(module.config().getString("messages.target-left",
                            "&d[SUS] &e{player} &7has left the server.")
                    .replace("{player}", player.getName())));
            if (module.spectate().stopWhenTargetLeaves()) {
                module.spectate().stop(staff, true);
            }
        }
    }
}
