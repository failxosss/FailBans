package cz.failban.plugin.sus.gui;

import cz.failban.plugin.sus.SusModule;
import cz.failban.plugin.sus.data.SusRecord;
import cz.failban.plugin.sus.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

public class SusGuiListener implements Listener {

    private final SusModule module;

    public SusGuiListener(SusModule module) {
        this.module = module;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof SusGui) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof SusGui gui)) {
            return;
        }
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player staff)) {
            return;
        }
        if (event.getClickedInventory() == null
                || !event.getClickedInventory().equals(event.getInventory())) {
            return;
        }

        int slot = event.getRawSlot();

        switch (slot) {
            case SusGui.SLOT_PREV -> {
                gui.previousPage();
                gui.refresh();
                click(staff, 1.0f);
                return;
            }
            case SusGui.SLOT_NEXT -> {
                gui.nextPage();
                gui.refresh();
                click(staff, 1.2f);
                return;
            }
            case SusGui.SLOT_FILTER -> {
                gui.cycleFilter();
                gui.refresh();
                click(staff, 1.4f);
                return;
            }
            case SusGui.SLOT_SORT -> {
                gui.cycleSort();
                gui.refresh();
                click(staff, 1.6f);
                return;
            }
            case SusGui.SLOT_REFRESH -> {
                gui.refresh();
                click(staff, 0.8f);
                return;
            }
            default -> {
            }
        }

        SusRecord record = gui.recordAt(slot);
        if (record == null) {
            return;
        }

        ClickType type = event.getClick();

        if (type.isShiftClick() && type.isLeftClick()) {
            String command = module.config().getString("integration.shift-left-command", "failcheck {player}");
            if (command != null && !command.isEmpty()) {
                staff.closeInventory();
                Bukkit.dispatchCommand(staff, command.replace("{player}", record.getName()));
            }
            return;
        }

        if (type.isRightClick()) {
            if (!staff.hasPermission("failban.sus.manage")) {
                staff.sendMessage(msg("messages.no-permission", "&cYou don't have permission for that."));
                return;
            }
            module.sus().remove(record.getUuid());
            staff.sendMessage(msg("messages.record-removed", "&aRecord of &e{player} &awas removed.")
                    .replace("{player}", record.getName()));
            staff.playSound(staff.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, 0.6f, 1.4f);
            gui.refresh();
            return;
        }

        if (type.isLeftClick()) {
            if (!staff.hasPermission("failban.sus.spectate")) {
                staff.sendMessage(msg("messages.no-permission", "&cYou don't have permission for that."));
                return;
            }
            Player target = Bukkit.getPlayer(record.getUuid());
            if (target == null || !target.isOnline()) {
                staff.sendMessage(msg("messages.target-offline", "&e{player} &cis not online right now.")
                        .replace("{player}", record.getName()));
                return;
            }
            staff.closeInventory();
            module.spectate().start(staff, target);
        }
    }

    private void click(Player player, float pitch) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, pitch);
    }

    private String msg(String path, String def) {
        return Msg.color(module.config().getString(path, def));
    }
}
