package cz.failban.plugin.sus.gui;

import cz.failban.plugin.sus.SusModule;
import cz.failban.plugin.sus.data.SusRecord;
import cz.failban.plugin.sus.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Locale;
import java.util.List;

public class SusGui implements InventoryHolder {

    public static final String[] FILTERS = {"ALL", "OVERWORLD", "NETHER", "END"};
    public static final String[] SORTS = {"LAST_FLAG", "VIOLATIONS", "FLAGS", "NAME"};

    public static final int SLOT_PREV = 45;
    public static final int SLOT_FILTER = 47;
    public static final int SLOT_REFRESH = 49;
    public static final int SLOT_SORT = 51;
    public static final int SLOT_NEXT = 53;
    public static final int PER_PAGE = 45;

    private final SusModule module;
    private final Player viewer;

    private int page;
    private int filterIndex;
    private int sortIndex;

    private Inventory inventory;
    private String currentTitle = "";
    private List<SusRecord> snapshot = new ArrayList<>();

    public SusGui(SusModule module, Player viewer) {
        this.module = module;
        this.viewer = viewer;
    }

    public String filter() {
        return FILTERS[filterIndex % FILTERS.length];
    }

    public String sort() {
        return SORTS[sortIndex % SORTS.length];
    }

    public void cycleFilter() {
        filterIndex = (filterIndex + 1) % FILTERS.length;
        page = 0;
    }

    public void cycleSort() {
        sortIndex = (sortIndex + 1) % SORTS.length;
        page = 0;
    }

    public void nextPage() {
        if ((page + 1) * PER_PAGE < snapshot.size()) {
            page++;
        }
    }

    public void previousPage() {
        if (page > 0) {
            page--;
        }
    }

    public SusRecord recordAt(int slot) {
        if (slot < 0 || slot >= PER_PAGE) {
            return null;
        }
        int index = page * PER_PAGE + slot;
        if (index >= snapshot.size()) {
            return null;
        }
        return snapshot.get(index);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open() {
        build();
        viewer.openInventory(inventory);
    }

    public void refresh() {
        List<SusRecord> fresh = module.sus().sorted(filter(), sort());
        int pages = Math.max(1, (int) Math.ceil(fresh.size() / (double) PER_PAGE));
        if (page >= pages) {
            page = pages - 1;
        }
        String title = title(fresh.size(), pages);
        snapshot = fresh;
        if (inventory == null || !currentTitle.equals(title)) {
            build();
            viewer.openInventory(inventory);
            return;
        }
        fill();
        viewer.updateInventory();
    }

    private String title(int total, int pages) {
        return Msg.color(module.config()
                .getString("gui.title", "&8{page}/{pages} sus &8#&f{total}")
                .replace("{page}", String.valueOf(page + 1))
                .replace("{pages}", String.valueOf(pages))
                .replace("{total}", String.valueOf(total))
                .replace("{filter}", filter()));
    }

    private void build() {
        snapshot = module.sus().sorted(filter(), sort());
        int pages = Math.max(1, (int) Math.ceil(snapshot.size() / (double) PER_PAGE));
        if (page >= pages) {
            page = pages - 1;
        }
        currentTitle = title(snapshot.size(), pages);
        inventory = Bukkit.createInventory(this, 54, currentTitle);
        fill();
    }

    private void fill() {
        inventory.clear();

        int start = page * PER_PAGE;
        for (int i = 0; i < PER_PAGE; i++) {
            int index = start + i;
            if (index >= snapshot.size()) {
                break;
            }
            inventory.setItem(i, head(snapshot.get(index)));
        }

        int pages = Math.max(1, (int) Math.ceil(snapshot.size() / (double) PER_PAGE));

        if (page > 0) {
            inventory.setItem(SLOT_PREV, button(Material.ARROW, "&e&lPrevious page",
                    List.of("&7Page &f" + page + "&7/&f" + pages, "", "&e\u25B6 Click to go back")));
        }
        if ((page + 1) * PER_PAGE < snapshot.size()) {
            inventory.setItem(SLOT_NEXT, button(Material.ARROW, "&e&lNext page",
                    List.of("&7Page &f" + (page + 2) + "&7/&f" + pages, "", "&e\u25B6 Click to continue")));
        }

        List<String> filterLore = new ArrayList<>();
        filterLore.add("&7Showing flags from:");
        filterLore.add("");
        for (String f : FILTERS) {
            filterLore.add(f.equals(filter()) ? "&b&l\u25AA " + f : "&8\u25AA &7" + f);
        }
        filterLore.add("");
        filterLore.add("&b\u25B6 Click to cycle");
        inventory.setItem(SLOT_FILTER, button(
                mat("gui.buttons.filter-material", Material.ENDER_EYE),
                "&b&l" + filter(), filterLore));

        inventory.setItem(SLOT_REFRESH, button(
                mat("gui.buttons.refresh-material", Material.CLOCK),
                "&6&lRefresh",
                List.of("&7Click this button", "&7to refresh the page.", "", "&6\u25B6 Click to refresh")));

        List<String> sortLore = new ArrayList<>();
        sortLore.add("&7Sorting by:");
        sortLore.add("");
        for (String s : SORTS) {
            sortLore.add(s.equals(sort()) ? "&d&l\u25AA " + s : "&8\u25AA &7" + s);
        }
        sortLore.add("");
        sortLore.add("&d\u25B6 Click to cycle");
        inventory.setItem(SLOT_SORT, button(
                mat("gui.buttons.sort-material", Material.COMPARATOR),
                "&d&lSort: " + sort(), sortLore));
    }

    private ItemStack head(SusRecord record) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta == null) {
            return item;
        }
        OfflinePlayer owner = Bukkit.getOfflinePlayer(record.getUuid());
        meta.setOwningPlayer(owner);

        Player online = Bukkit.getPlayer(record.getUuid());
        String status = online != null
                ? module.config().getString("gui.status-online", "&aONLINE")
                : module.config().getString("gui.status-offline", "&cOFFLINE");

        meta.setDisplayName(Msg.color(module.config().getString("gui.head.name", "&e&l{player}")
                .replace("{player}", record.getName())));

        List<String> lore = new ArrayList<>();
        for (String line : module.config().getStringList("gui.head.lore")) {
            lore.add(Msg.color(line
                    .replace("{player}", record.getName())
                    .replace("{reason}", record.getReason())
                    .replace("{anticheat}", record.getAnticheat())
                    .replace("{check}", record.getLastCheck())
                    .replace("{checks}", record.getCheckSummary())
                    .replace("{vl}", String.valueOf(record.getViolations()))
                    .replace("{flags}", String.valueOf(record.getTotalFlags()))
                    .replace("{last_flag}", Msg.ago(System.currentTimeMillis() - record.getLastFlag()))
                    .replace("{first_flag}", Msg.ago(System.currentTimeMillis() - record.getFirstFlag()))
                    .replace("{status}", status)
                    .replace("{world}", record.getWorld())
                    .replace("{env}", module.sus().environmentOf(record))
                    .replace("{x}", String.valueOf(record.getX()))
                    .replace("{y}", String.valueOf(record.getY()))
                    .replace("{z}", String.valueOf(record.getZ()))
                    .replace("{uuid}", record.getUuid().toString())));
        }
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }

    private Material mat(String path, Material def) {
        try {
            return Material.valueOf(module.config().getString(path, def.name()).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return def;
        }
    }

    private ItemStack button(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material == null ? Material.PAPER : material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Msg.color(name));
            meta.setLore(Msg.color(lore));
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }
}
