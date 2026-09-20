package cz.failban.plugin.sus.spectate;

import cz.failban.plugin.sus.SusModule;
import cz.failban.plugin.sus.util.Msg;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

public class SpectateManager {
    public static final String SEE_VANISH_PERMISSION = "failban.sus.seevanish";
    /** How long after a teleport / world change other plugins may NOT switch the spectator out of SPECTATOR. */
    private static final long GUARD_MILLIS = 3000L;

    private final SusModule module;
    private final File file;
    private final Map<UUID, SpectateSession> sessions = new HashMap<UUID, SpectateSession>();
    private final Set<UUID> vanished = new HashSet<UUID>();
    private final Map<UUID, Long> guardUntil = new HashMap<UUID, Long>();
    private BukkitTask followTask;
    private boolean useVanish;
    private boolean useNightVision;
    private boolean lockCamera;
    private boolean stopWhenTargetLeaves;
    private boolean keepSpectatorMode;
    private boolean removeRecordOnSpectate;
    private boolean autoTeleport;
    private double autoTeleportDistance;
    private long autoTeleportInterval;

    public SpectateManager(SusModule module) {
        this.module = module;
        this.file = new File(module.plugin().getDataFolder(), "sus-sessions.yml");
        this.reloadSettings();
    }

    public void reloadSettings() {
        this.useVanish = this.module.config().getBoolean("spectate.vanish", true);
        this.useNightVision = this.module.config().getBoolean("spectate.night-vision", true);
        this.lockCamera = this.module.config().getBoolean("spectate.lock-camera", false);
        this.stopWhenTargetLeaves = this.module.config().getBoolean("spectate.stop-when-target-leaves", false);
        this.keepSpectatorMode = this.module.config().getBoolean("spectate.keep-spectator-mode", true);
        this.removeRecordOnSpectate = this.module.config().getBoolean("spectate.remove-record-on-spectate", true);
        this.autoTeleport = this.module.config().getBoolean("spectate.auto-teleport.enabled", true);
        this.autoTeleportDistance = Math.max(1.0, this.module.config().getDouble("spectate.auto-teleport.distance", 50.0));
        this.autoTeleportInterval = Math.max(1L, this.module.config().getLong("spectate.auto-teleport.check-interval-ticks", 10L));
        this.restartFollowTask();
    }

    public boolean isSpectating(UUID uuid) {
        return this.sessions.containsKey(uuid);
    }

    public SpectateSession session(UUID uuid) {
        return this.sessions.get(uuid);
    }

    public boolean isVanished(UUID uuid) {
        return this.vanished.contains(uuid);
    }

    public boolean stopWhenTargetLeaves() {
        return this.stopWhenTargetLeaves;
    }

    // ------------------------------------------------------------------
    //  Keeping the SPECTATOR gamemode (other plugins like Multiverse set the
    //  world's default gamemode - usually SURVIVAL - when the player changes world)
    // ------------------------------------------------------------------

    private void markGuard(UUID uuid) {
        this.guardUntil.put(uuid, System.currentTimeMillis() + GUARD_MILLIS);
    }

    /** True when a gamemode change to something other than SPECTATOR should be blocked right now. */
    public boolean isGameModeLocked(UUID uuid) {
        if (!this.keepSpectatorMode || !this.sessions.containsKey(uuid)) {
            return false;
        }
        Long until = this.guardUntil.get(uuid);
        return until != null && System.currentTimeMillis() < until;
    }

    /** Called when a player changed world (by anything: /sus, portal, /mv tp, /tp ...). */
    public void onWorldChange(Player player) {
        if (!this.keepSpectatorMode || !this.sessions.containsKey(player.getUniqueId())) {
            return;
        }
        this.markGuard(player.getUniqueId());
        this.keepSpectator(player);
    }

    /** Forces SPECTATOR now and again a few ticks later, in case another plugin reverts it. */
    private void keepSpectator(Player staff) {
        if (!this.keepSpectatorMode) {
            return;
        }
        this.enforce(staff);
        long[] delays = new long[]{1L, 5L, 20L};
        for (long delay : delays) {
            Bukkit.getScheduler().runTaskLater((Plugin)this.module.plugin(), () -> this.enforce(staff), delay);
        }
    }

    private void enforce(Player staff) {
        SpectateSession session = this.sessions.get(staff.getUniqueId());
        if (session == null || !staff.isOnline()) {
            return;
        }
        if (staff.getGameMode() != GameMode.SPECTATOR) {
            staff.setGameMode(GameMode.SPECTATOR);
        }
        if (session.isGaveNightVision() && !staff.hasPotionEffect(PotionEffectType.NIGHT_VISION)) {
            staff.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, -1, 0, false, false, false));
        }
    }

    // ------------------------------------------------------------------
    //  Start / stop
    // ------------------------------------------------------------------

    public void start(Player staff, Player target) {
        SpectateSession session = this.sessions.get(staff.getUniqueId());
        if (session == null) {
            session = new SpectateSession(staff.getUniqueId(), target.getUniqueId(), staff.getGameMode(), staff.getLocation().clone(), staff.getAllowFlight(), staff.isFlying(), this.useNightVision && !staff.hasPotionEffect(PotionEffectType.NIGHT_VISION));
            this.sessions.put(staff.getUniqueId(), session);
        } else {
            session.setTarget(target.getUniqueId());
        }
        this.markGuard(staff.getUniqueId());
        staff.setGameMode(GameMode.SPECTATOR);
        this.teleportToTarget(staff, target);
        if (this.useVanish) {
            this.vanish(staff);
        }
        if (session.isGaveNightVision()) {
            staff.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, -1, 0, false, false, false));
        }
        staff.playSound(staff.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.4f);
        staff.sendMessage(this.msg("messages.spectate-start", "&d[SUS] &7You are now spectating &f{player}&7. Type &f/sus back &7to return.").replace("{player}", target.getName()));
        if (this.removeRecordOnSpectate) {
            // the player is being dealt with -> he disappears from the /sus list
            this.module.sus().remove(target.getUniqueId());
        }
        this.save();
    }

    /** Teleports the spectator to the target (also across worlds) and keeps him in SPECTATOR mode. */
    private void teleportToTarget(Player staff, Player target) {
        this.markGuard(staff.getUniqueId());
        if (staff.getSpectatorTarget() != null) {
            staff.setSpectatorTarget(null);
        }
        staff.teleport(target.getLocation());
        this.keepSpectator(staff);
        if (this.lockCamera) {
            Player t = target;
            Bukkit.getScheduler().runTaskLater((Plugin)this.module.plugin(), () -> {
                if (staff.isOnline() && t.isOnline() && staff.getGameMode() == GameMode.SPECTATOR) {
                    staff.setSpectatorTarget((Entity)t);
                }
            }, 5L);
        }
    }

    public void stop(Player staff, boolean notify) {
        SpectateSession session = this.sessions.remove(staff.getUniqueId());
        this.guardUntil.remove(staff.getUniqueId());
        if (session == null) {
            return;
        }
        this.restore(staff, session);
        if (notify) {
            staff.sendMessage(this.msg("messages.spectate-stop", "&d[SUS] &7You are back at your previous location."));
            staff.playSound(staff.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.0f);
        }
        this.save();
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
        this.unvanish(staff);
    }

    public void restoreAll() {
        this.stopFollowTask();
        for (Map.Entry<UUID, SpectateSession> entry : new HashMap<UUID, SpectateSession>(this.sessions).entrySet()) {
            Player staff = Bukkit.getPlayer((UUID)entry.getKey());
            if (staff == null || !staff.isOnline()) continue;
            // remove the session first so the gamemode guard does not block the restore
            this.sessions.remove(entry.getKey());
            this.guardUntil.remove(entry.getKey());
            this.restore(staff, entry.getValue());
        }
    }

    public void restoreOnJoin(Player staff) {
        SpectateSession session = this.sessions.get(staff.getUniqueId());
        if (session == null) {
            return;
        }
        if (!this.module.config().getBoolean("spectate.restore-on-join", true)) {
            return;
        }
        this.sessions.remove(staff.getUniqueId());
        this.guardUntil.remove(staff.getUniqueId());
        this.restore(staff, session);
        staff.sendMessage(this.msg("messages.spectate-restored", "&d[SUS] &7Your spectator session was restored after a restart."));
        this.save();
    }

    // ------------------------------------------------------------------
    //  Auto teleport to the target when it gets too far away
    // ------------------------------------------------------------------

    private void restartFollowTask() {
        this.stopFollowTask();
        if (!this.autoTeleport) {
            return;
        }
        this.followTask = Bukkit.getScheduler().runTaskTimer((Plugin)this.module.plugin(), () -> this.followTick(), this.autoTeleportInterval, this.autoTeleportInterval);
    }

    private void stopFollowTask() {
        if (this.followTask != null) {
            this.followTask.cancel();
            this.followTask = null;
        }
    }

    private void followTick() {
        if (this.sessions.isEmpty()) {
            return;
        }
        double maxSquared = this.autoTeleportDistance * this.autoTeleportDistance;
        for (Map.Entry<UUID, SpectateSession> entry : new HashMap<UUID, SpectateSession>(this.sessions).entrySet()) {
            UUID targetId = entry.getValue().getTarget();
            if (targetId == null) continue;
            Player staff = Bukkit.getPlayer((UUID)entry.getKey());
            Player target = Bukkit.getPlayer((UUID)targetId);
            if (staff == null || target == null || !staff.isOnline() || !target.isOnline()) continue;
            if (staff.getGameMode() != GameMode.SPECTATOR) continue;
            Location from = staff.getLocation();
            Location to = target.getLocation();
            World fromWorld = from.getWorld();
            World toWorld = to.getWorld();
            if (fromWorld == null || toWorld == null) continue;
            if (fromWorld.equals((Object)toWorld)) {
                // camera is locked onto the target -> he is with him already
                if (staff.getSpectatorTarget() != null) continue;
                if (from.distanceSquared(to) <= maxSquared) continue;
            }
            // different world, or farther than the limit -> jump to the target
            this.teleportToTarget(staff, target);
        }
    }

    // ------------------------------------------------------------------
    //  Vanish
    // ------------------------------------------------------------------

    public void vanish(Player staff) {
        this.vanished.add(staff.getUniqueId());
        staff.setMetadata("vanished", (MetadataValue)new FixedMetadataValue((Plugin)this.module.plugin(), (Object)true));
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals((Object)staff) || other.hasPermission(SEE_VANISH_PERMISSION)) continue;
            other.hidePlayer((Plugin)this.module.plugin(), staff);
        }
    }

    public void unvanish(Player staff) {
        this.vanished.remove(staff.getUniqueId());
        staff.removeMetadata("vanished", (Plugin)this.module.plugin());
        for (Player other : Bukkit.getOnlinePlayers()) {
            other.showPlayer((Plugin)this.module.plugin(), staff);
        }
    }

    public void hideVanishedFrom(Player joiner) {
        if (joiner.hasPermission(SEE_VANISH_PERMISSION)) {
            return;
        }
        for (UUID uuid : this.vanished) {
            Player staff = Bukkit.getPlayer((UUID)uuid);
            if (staff == null || staff.equals((Object)joiner)) continue;
            joiner.hidePlayer((Plugin)this.module.plugin(), staff);
        }
    }

    public List<UUID> spectatorsOf(UUID target) {
        ArrayList<UUID> list = new ArrayList<UUID>();
        for (Map.Entry<UUID, SpectateSession> entry : this.sessions.entrySet()) {
            if (!target.equals(entry.getValue().getTarget())) continue;
            list.add(entry.getKey());
        }
        return list;
    }

    // ------------------------------------------------------------------
    //  Persistence (unchanged)
    // ------------------------------------------------------------------

    public void load() {
        if (!this.file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration((File)this.file);
        ConfigurationSection root = yaml.getConfigurationSection("sessions");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            try {
                GameMode mode;
                UUID staff = UUID.fromString(key);
                ConfigurationSection s = root.getConfigurationSection(key);
                if (s == null) continue;
                World world = Bukkit.getWorld((String)s.getString("world", ""));
                Location back = world == null ? null : new Location(world, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"), (float)s.getDouble("yaw"), (float)s.getDouble("pitch"));
                try {
                    mode = GameMode.valueOf((String)s.getString("gamemode", "SURVIVAL"));
                }
                catch (IllegalArgumentException e) {
                    mode = GameMode.SURVIVAL;
                }
                String targetRaw = s.getString("target", null);
                SpectateSession session = new SpectateSession(staff, targetRaw == null ? null : UUID.fromString(targetRaw), mode, back, s.getBoolean("allow-flight", false), s.getBoolean("flying", false), s.getBoolean("night-vision", false));
                this.sessions.put(staff, session);
            }
            catch (IllegalArgumentException illegalArgumentException) {}
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, SpectateSession> entry : this.sessions.entrySet()) {
            SpectateSession s = entry.getValue();
            String base = "sessions." + String.valueOf(entry.getKey());
            yaml.set(base + ".target", s.getTarget() == null ? null : s.getTarget().toString());
            yaml.set(base + ".gamemode", (Object)s.getGameMode().name());
            yaml.set(base + ".allow-flight", (Object)s.isAllowFlight());
            yaml.set(base + ".flying", (Object)s.isFlying());
            yaml.set(base + ".night-vision", (Object)s.isGaveNightVision());
            Location back = s.getBack();
            if (back == null || back.getWorld() == null) continue;
            yaml.set(base + ".world", (Object)back.getWorld().getName());
            yaml.set(base + ".x", (Object)back.getX());
            yaml.set(base + ".y", (Object)back.getY());
            yaml.set(base + ".z", (Object)back.getZ());
            yaml.set(base + ".yaw", (Object)Float.valueOf(back.getYaw()));
            yaml.set(base + ".pitch", (Object)Float.valueOf(back.getPitch()));
        }
        try {
            File folder = this.module.plugin().getDataFolder();
            if (!folder.exists() && !folder.mkdirs()) {
                this.module.plugin().getLogger().warning("[Sus] Could not create the plugin folder.");
            }
            yaml.save(this.file);
        }
        catch (IOException e) {
            this.module.plugin().getLogger().warning("[Sus] Could not save sus-sessions.yml: " + e.getMessage());
        }
    }

    private String msg(String path, String def) {
        return Msg.color(this.module.config().getString(path, def));
    }
}
