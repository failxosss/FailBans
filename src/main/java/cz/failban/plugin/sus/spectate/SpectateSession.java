package cz.failban.plugin.sus.spectate;

import org.bukkit.GameMode;
import org.bukkit.Location;

import java.util.UUID;

public class SpectateSession {

    private final UUID staff;
    private UUID target;
    private final GameMode gameMode;
    private final Location back;
    private final boolean allowFlight;
    private final boolean flying;
    private final boolean gaveNightVision;
    private final long started = System.currentTimeMillis();

    public SpectateSession(UUID staff, UUID target, GameMode gameMode, Location back,
                           boolean allowFlight, boolean flying, boolean gaveNightVision) {
        this.staff = staff;
        this.target = target;
        this.gameMode = gameMode;
        this.back = back;
        this.allowFlight = allowFlight;
        this.flying = flying;
        this.gaveNightVision = gaveNightVision;
    }

    public UUID getStaff() {
        return staff;
    }

    public UUID getTarget() {
        return target;
    }

    public void setTarget(UUID target) {
        this.target = target;
    }

    public GameMode getGameMode() {
        return gameMode == null ? GameMode.SURVIVAL : gameMode;
    }

    public Location getBack() {
        return back;
    }

    public boolean isAllowFlight() {
        return allowFlight;
    }

    public boolean isFlying() {
        return flying;
    }

    public boolean isGaveNightVision() {
        return gaveNightVision;
    }

    public long getStarted() {
        return started;
    }
}
