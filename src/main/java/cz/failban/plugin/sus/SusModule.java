package cz.failban.plugin.sus;

import cz.failban.plugin.sus.commands.SusCommand;
import cz.failban.plugin.sus.commands.SusFlagCommand;
import cz.failban.plugin.sus.data.SusManager;
import cz.failban.plugin.sus.gui.SusGuiListener;
import cz.failban.plugin.sus.hooks.HookManager;
import cz.failban.plugin.sus.listeners.SpectateListener;
import cz.failban.plugin.sus.spectate.SpectateManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;

public final class SusModule {

    private static SusModule instance;

    private final JavaPlugin plugin;
    private final File configFile;

    private FileConfiguration config;
    private SusManager susManager;
    private SpectateManager spectateManager;
    private HookManager hookManager;
    private BukkitTask saveTask;

    private SusModule(JavaPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "sus.yml");
    }

    public static void enable(JavaPlugin plugin) {
        if (instance != null) {
            disable();
        }
        instance = new SusModule(plugin);
        instance.start();
    }

    public static void disable() {
        if (instance != null) {
            instance.stop();
            instance = null;
        }
    }

    public static SusModule get() {
        return instance;
    }

    private void start() {
        reloadConfigFile();

        this.susManager = new SusManager(this);
        this.susManager.load();

        this.spectateManager = new SpectateManager(this);
        this.spectateManager.load();

        Bukkit.getPluginManager().registerEvents(new SusGuiListener(this), plugin);
        Bukkit.getPluginManager().registerEvents(new SpectateListener(this), plugin);

        SusCommand susCommand = new SusCommand(this);
        PluginCommand sus = plugin.getCommand("sus");
        if (sus != null) {
            sus.setExecutor(susCommand);
            sus.setTabCompleter(susCommand);
        } else {
            plugin.getLogger().warning("[Sus] Command 'sus' is missing in plugin.yml!");
        }

        PluginCommand susflag = plugin.getCommand("susflag");
        if (susflag != null) {
            susflag.setExecutor(new SusFlagCommand(this));
        } else {
            plugin.getLogger().warning("[Sus] Command 'susflag' is missing in plugin.yml!");
        }

        this.hookManager = new HookManager(this);
        Bukkit.getScheduler().runTask(plugin, () -> hookManager.registerAll());

        long ticks = Math.max(200L, config.getLong("storage.autosave-seconds", 300) * 20L);
        this.saveTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            susManager.purgeExpired();
            susManager.save();
            spectateManager.save();
        }, ticks, ticks);

        plugin.getLogger().info("[Sus] Module enabled, " + susManager.size() + " records loaded.");
    }

    private void stop() {
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
        if (spectateManager != null) {
            spectateManager.restoreAll();
            spectateManager.save();
        }
        if (susManager != null) {
            susManager.save();
        }
        if (hookManager != null) {
            hookManager.unregisterAll();
        }
    }

    public void reload() {
        reloadConfigFile();
        susManager.reloadSettings();
        spectateManager.reloadSettings();
        hookManager.registerAll();
    }

    private void reloadConfigFile() {
        if (!configFile.exists()) {
            plugin.saveResource("sus.yml", false);
        }
        this.config = YamlConfiguration.loadConfiguration(configFile);
    }

    public JavaPlugin plugin() {
        return plugin;
    }

    public FileConfiguration config() {
        return config;
    }

    public SusManager sus() {
        return susManager;
    }

    public SpectateManager spectate() {
        return spectateManager;
    }

    public HookManager hooks() {
        return hookManager;
    }
}
