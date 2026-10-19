package cz.failban.plugin;

import cz.failban.plugin.anarchy.AnarchyBanCommand;
import cz.failban.plugin.anarchy.AnarchyBanListener;
import cz.failban.plugin.anarchy.AnarchyBanManager;
import cz.failban.plugin.commands.FailBanCommandHandler;
import cz.failban.plugin.database.DatabaseManager;
import cz.failban.plugin.listeners.ChatListener;
import cz.failban.plugin.listeners.PlayerConnectionListener;
import cz.failban.plugin.manager.PlayerDataManager;
import cz.failban.plugin.manager.PunishmentManager;
import cz.failban.plugin.sus.SusModule; // <-- NOVÉ
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
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
        registerCommand("ipban", handler);
        registerCommand("tempipban", handler);
        registerCommand("unipban", handler);
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

        // <-- NOVÉ: Anarchy ban (/anarchyban, /anarchyunban)
        AnarchyBanManager anarchyManager = new AnarchyBanManager(this);
        getServer().getPluginManager().registerEvents(new AnarchyBanListener(this, anarchyManager), this);
        AnarchyBanCommand anarchyCmd = new AnarchyBanCommand(this, anarchyManager);
        registerCommand("anarchyban", anarchyCmd, anarchyCmd);
        registerCommand("anarchyunban", anarchyCmd, anarchyCmd);

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
        registerCommand(name, handler, handler);
    }

    private void registerCommand(String name, CommandExecutor executor, TabCompleter completer) {
        if (getCommand(name) != null) {
            getCommand(name).setExecutor(executor);
            getCommand(name).setTabCompleter(completer);
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
