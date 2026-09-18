package cz.failban.plugin;

import cz.failban.plugin.commands.FailBanCommandHandler;
import cz.failban.plugin.database.DatabaseManager;
import cz.failban.plugin.listeners.ChatListener;
import cz.failban.plugin.listeners.PlayerConnectionListener;
import cz.failban.plugin.manager.PlayerDataManager;
import cz.failban.plugin.manager.PunishmentManager;
import cz.failban.plugin.sus.SusModule; // <-- NOVÉ
import org.bukkit.plugin.java.JavaPlugin;

public class FailBan extends JavaPlugin {

    private static FailBan instance;

    private DatabaseManager databaseManager;
    private PunishmentManager punishmentManager;
    private PlayerDataManager playerDataManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        this.databaseManager = new DatabaseManager(this);
        this.databaseManager.connect();

        this.punishmentManager = new PunishmentManager(this);
        this.playerDataManager = new PlayerDataManager(this);

        FailBanCommandHandler handler = new FailBanCommandHandler(this);
        registerCommand("ban", handler);
        registerCommand("tempban", handler);
        registerCommand("unban", handler);
        registerCommand("kick", handler);
        registerCommand("mute", handler);
        registerCommand("tempmute", handler);
        registerCommand("unmute", handler);
        registerCommand("warn", handler);
        registerCommand("tempwarn", handler);
        registerCommand("unwarn", handler);
        registerCommand("history", handler);
        registerCommand("check", handler);
        registerCommand("banlist", handler);
        registerCommand("unpunish", handler);
        registerCommand("change-reason", handler);
        registerCommand("failcheck", handler);
        registerCommand("failban", handler);

        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);

        getLogger().info("FailBan has been successfully loaded! (version " + getDescription().getVersion() + ")");

        SusModule.enable(this); // <-- NOVÉ
    }

    @Override
    public void onDisable() {
        SusModule.disable(); // <-- NOVÉ

        if (databaseManager != null) databaseManager.close();
        getLogger().info("FailBan has been disabled.");
    }

    private void registerCommand(String name, FailBanCommandHandler handler) {
        if (getCommand(name) != null) {
            getCommand(name).setExecutor(handler);
            getCommand(name).setTabCompleter(handler);
        } else {
            getLogger().warning("Command '" + name + "' was not found in plugin.yml!");
        }
    }

    public static FailBan getInstance() {
        return instance;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public PunishmentManager getPunishmentManager() {
        return punishmentManager;
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }
}
