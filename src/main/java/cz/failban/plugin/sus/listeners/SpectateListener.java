package cz.failban.plugin.sus.listeners;

import cz.failban.plugin.sus.SusModule;
import cz.failban.plugin.sus.util.Msg;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

public class SpectateListener
implements Listener {
    private final SusModule module;

    public SpectateListener(SusModule module) {
        this.module = module;
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater((Plugin)this.module.plugin(), () -> {
            if (!player.isOnline()) {
                return;
            }
            this.module.spectate().hideVanishedFrom(player);
            this.module.spectate().restoreOnJoin(player);
        }, 10L);
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (this.module.spectate().isSpectating(player.getUniqueId())) {
            this.module.spectate().stop(player, false);
        }
        for (UUID staffUuid : this.module.spectate().spectatorsOf(player.getUniqueId())) {
            Player staff = Bukkit.getPlayer((UUID)staffUuid);
            if (staff == null || !staff.isOnline()) continue;
            staff.sendMessage(Msg.color(this.module.config().getString("messages.target-left", "&d[SUS] &e{player} &7has left the server.").replace("{player}", player.getName())));
            if (!this.module.spectate().stopWhenTargetLeaves()) continue;
            this.module.spectate().stop(staff, true);
        }
    }

    /**
     * Runs FIRST (LOWEST), so the "gamemode lock" is active before Multiverse & co.
     * get to handle the same world change and try to set their per-world gamemode.
     */
    @EventHandler(priority=EventPriority.LOWEST)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        this.module.spectate().onWorldChange(event.getPlayer());
    }

    /** Blocks other plugins from switching a /sus spectator out of SPECTATOR right after a teleport / world change. */
    @EventHandler(priority=EventPriority.HIGHEST)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        if (event.getNewGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (this.module.spectate().isGameModeLocked(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
