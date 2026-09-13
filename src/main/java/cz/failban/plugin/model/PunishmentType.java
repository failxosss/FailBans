package cz.failban.plugin.model;

public enum PunishmentType {
    BAN,
    TEMPBAN,
    KICK,
    MUTE,
    TEMPMUTE,
    WARN,
    TEMPWARN;

    public boolean isBanType() {
        return this == BAN || this == TEMPBAN;
    }

    public boolean isMuteType() {
        return this == MUTE || this == TEMPMUTE;
    }

    public boolean isWarnType() {
        return this == WARN || this == TEMPWARN;
    }

    public boolean isTemporary() {
        return this == TEMPBAN || this == TEMPMUTE || this == TEMPWARN;
    }
}
