package cz.failban.plugin.sus.spectate;

import cz.failban.plugin.sus.SusModule;
import cz.failban.plugin.sus.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SpectateManager {

    public static final String SEE_VANISH_PERMISSION = "failban.sus.seevanish";

    private final SusModule module;
    private final File file;
    private final Map<UUID, SpectateSession> sessions = new HashMap<>();
    private final Set<UUID> vanished = new HashSet<>();

    private boolean useVanish;
    private boolean useNightVision;
    private boolean lockCamera;
    private boolean stopWhenTargetLeaves;

    public SpectateManager(SusModule module) {
        this.module = module;
        this.file = new File(module.plugin().getDataFolder(), "sus-sessions.yml");
        reloadSettings();
    }

    public void reloadSettings() {
        this.useVanish = module.config().getBoolean("spectate.vanish", true);
        this.useNightVision = module.config().getBoolean("spectate.night-vision", true);
        this.lockCamera = module.config().getBoolean("spectate.lock-camera", false);
        this.stopWhenTargetLeaves = module.config().getBoolean("spectate.stop-when-target-leaves", false);
    }

    public boolean isSpectating(UUID uuid) {
        return sessions.containsKey(uuid);
    }

    public SpectateSession session(UUID uuid) {
        return sessions.get(uuid);
    }

    public boolean isVanished(UUID uuid) {
        return vanished.contains(uuid);
    }

    public boolean stopWhenTargetLeaves() {
        return stopWhenTargetLeaves;
    }

    public void start(Player staff, Player target) {
        SpectateSession session = sessions.get(staff.getUniqueId());
        if (session == null) {
            session = new SpectateSession(
                    staff.getUniqueId(),
                    target.getUniqueId(),
                    staff.getGameMode(),
                    staff.getLocation().clone(),
                    staff.getAllowFlight(),
                    staff.isFlying(),
                    useNightVision && !staff.hasPotionEffect(PotionEffectType.NIGHT_VISION));
            sessions.put(staff.getUniqueId(), session);
        } else {
            session.setTarget(target.getUniqueId());
        }

        staff.setGameMode(GameMode.SPECTATOR);
        staff.teleport(target.getLocation());

        if (useVanish) {
            vanish(staff);
        }
        if (session.isGaveNightVision()) {
            staff.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION,
                    PotionEffect.INFINITE_DURATION, 0, false, false, false));
        }
        if (lockCamera) {
            final Player t = target;
            Bukkit.getScheduler().runTaskLater(module.plugin(), () -> {
                if (staff.isOnline() && t.isOnline() && staff.getGameMode() == GameMode.SPECTATOR) {
                    staff.setSpectatorTarget(t);
                }
            }, 5L);
        }

        staff.playSound(staff.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.4f);
        staff.sendMessage(msg("messages.spectate-start",
                "&d[SUS] &7You are now spectating &f{player}&7. Type &f/sus back &7to return.")
                .replace("{player}", target.getName()));

        save();
    }

    public void stop(Player staff, boolean notify) {
        SpectateSession session = sessions.remove(staff.getUniqueId());
        if (session == null) {
            return;
        }
        restore(staff, session);
        if (notify) {
            staff.sendMessage(msg("messages.spectate-stop", "&d[SUS] &7You are back at your previous location."));
            staff.playSound(staff.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.0f);
        }
        save();
    }

    private void restore(Player staff, SpectateSession session) {
        if (staff.getGameMode() == GameMode.SPECTATOR) {
            staff.setSpectatorTarget(null);
        }
        staff.setGameMode(session.getGameMode());
        Location back = session.getBack();
        if (back != null && back.getWorld() != null) {
            staff.teleport(back);
        }
        staff.setAllowFlight(session.isAllowFlight());
        staff.setFlying(session.isAllowFlight() && session.isFlying());
        if (session.isGaveNightVision()) {
            staff.removePotionEffect(PotionEffectType.NIGHT_VISION);
        }
        unvanish(staff);
    }

    public void restoreAll() {
        for (Map.Entry<UUID, SpectateSession> entry : new HashMap<>(sessions).entrySet()) {
            Player staff = Bukkit.getPlayer(entry.getKey());
            if (staff != null && staff.isOnline()) {
                restore(staff, entry.getValue());
                sessions.remove(entry.getKey());
            }
        }
    }

    public void restoreOnJoin(Player staff) {
        SpectateSession session = sessions.get(staff.getUniqueId());
        if (session == null) {
            return;
        }
        if (!module.config().getBoolean("spectate.restore-on-join", true)) {
            return;
        }
        sessions.remove(staff.getUniqueId());
        restore(staff, session);
        staff.sendMessage(msg("messages.spectate-restored",
                "&d[SUS] &7Your spectator session was restored after a restart."));
        save();
    }

    public void vanish(Player staff) {
        vanished.add(staff.getUniqueId());
        staff.setMetadata("vanished", new FixedMetadataValue(module.plugin(), true));
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(staff) && !other.hasPermission(SEE_VANISH_PERMISSION)) {
                other.hidePlayer(module.plugin(), staff);
            }
        }
    }

    public void unvanish(Player staff) {
        vanished.remove(staff.getUniqueId());
        staff.removeMetadata("vanished", module.plugin());
        for (Player other : Bukkit.getOnlinePlayers()) {
            other.showPlayer(module.plugin(), staff);
        }
    }

    public void hideVanishedFrom(Player joiner) {
        if (joiner.hasPermission(SEE_VANISH_PERMISSION)) {
            return;
        }
        for (UUID uuid : vanished) {
            Player staff = Bukkit.getPlayer(uuid);
            if (staff != null && !staff.equals(joiner)) {
                joiner.hidePlayer(module.plugin(), staff);
            }
        }
    }

    public List<UUID> spectatorsOf(UUID target) {
        List<UUID> list = new ArrayList<>();
        for (Map.Entry<UUID, SpectateSession> entry : sessions.entrySet()) {
            if (target.equals(entry.getValue().getTarget())) {
                list.add(entry.getKey());
            }
        }
        return list;
    }

    public void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("sessions");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            try {
                UUID staff = UUID.fromString(key);
                ConfigurationSection s = root.getConfigurationSection(key);
                if (s == null) {
                    continue;
                }
                World world = Bukkit.getWorld(s.getString("world", ""));
                Location back = world == null ? null : new Location(world,
                        s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                        (float) s.getDouble("yaw"), (float) s.getDouble("pitch"));
                GameMode mode;
                try {
                    mode = GameMode.valueOf(s.getString("gamemode", "SURVIVAL"));
                } catch (IllegalArgumentException e) {
                    mode = GameMode.SURVIVAL;
                }
                String targetRaw = s.getString("target", null);
                SpectateSession session = new SpectateSession(
                        staff,
                        targetRaw == null ? null : UUID.fromString(targetRaw),
                        mode,
                        back,
                        s.getBoolean("allow-flight", false),
                        s.getBoolean("flying", false),
                        s.getBoolean("night-vision", false));
                sessions.put(staff, session);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, SpectateSession> entry : sessions.entrySet()) {
            SpectateSession s = entry.getValue();
            String base = "sessions." + entry.getKey();
            yaml.set(base + ".target", s.getTarget() == null ? null : s.getTarget().toString());
            yaml.set(base + ".gamemode", s.getGameMode().name());
            yaml.set(base + ".allow-flight", s.isAllowFlight());
            yaml.set(base + ".flying", s.isFlying());
            yaml.set(base + ".night-vision", s.isGaveNightVision());
            Location back = s.getBack();
            if (back != null && back.getWorld() != null) {
                yaml.set(base + ".world", back.getWorld().getName());
                yaml.set(base + ".x", back.getX());
                yaml.set(base + ".y", back.getY());
                yaml.set(base + ".z", back.getZ());
                yaml.set(base + ".yaw", back.getYaw());
                yaml.set(base + ".pitch", back.getPitch());
            }
        }
        try {
            File folder = module.plugin().getDataFolder();
            if (!folder.exists() && !folder.mkdirs()) {
                module.plugin().getLogger().warning("[Sus] Could not create the plugin folder.");
            }
            yaml.save(file);
        } catch (IOException e) {
            module.plugin().getLogger().warning("[Sus] Could not save sus-sessions.yml: " + e.getMessage());
        }
    }

    private String msg(String path, String def) {
        return Msg.color(module.config().getString(path, def));
    }
}
