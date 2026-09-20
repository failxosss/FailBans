package cz.failban.plugin.sus.data;

import cz.failban.plugin.sus.SusModule;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/**
 * Per-player switch for the "X was added to /sus" chat notification.
 * Stored in plugins/FailBan/sus-notify.yml (list of players who turned it OFF).
 */
public final class NotifyManager {
    private static final Set<UUID> MUTED = ConcurrentHashMap.newKeySet();
    private static boolean loaded = false;

    private NotifyManager() {
    }

    /** Permission AND notifications not switched off. */
    public static boolean canReceive(Player staff, String permission) {
        return staff.hasPermission(permission) && !NotifyManager.isMuted(staff.getUniqueId());
    }

    public static boolean isMuted(UUID uuid) {
        NotifyManager.ensureLoaded();
        return MUTED.contains(uuid);
    }

    public static synchronized void setMuted(UUID uuid, boolean muted) {
        NotifyManager.ensureLoaded();
        if (muted) {
            MUTED.add(uuid);
        } else {
            MUTED.remove(uuid);
        }
        NotifyManager.save();
    }

    private static File file() {
        SusModule module = SusModule.get();
        return module == null ? null : new File(module.plugin().getDataFolder(), "sus-notify.yml");
    }

    private static synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        File file = NotifyManager.file();
        if (file == null) {
            return;
        }
        loaded = true;
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String raw : yaml.getStringList("muted")) {
            try {
                MUTED.add(UUID.fromString(raw));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private static void save() {
        File file = NotifyManager.file();
        if (file == null) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        ArrayList<String> list = new ArrayList<String>();
        for (UUID uuid : MUTED) {
            list.add(uuid.toString());
        }
        yaml.set("muted", list);
        try {
            File folder = file.getParentFile();
            if (folder != null && !folder.exists() && !folder.mkdirs()) {
                SusModule.get().plugin().getLogger().warning("[Sus] Could not create the plugin folder.");
            }
            yaml.save(file);
        } catch (IOException e) {
            SusModule.get().plugin().getLogger().warning("[Sus] Could not save sus-notify.yml: " + e.getMessage());
        }
    }
}
