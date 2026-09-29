package cz.failban.plugin.model;

public enum PunishmentType {
    BAN,
    TEMPBAN,
    IPBAN,
    TEMPIPBAN,
    KICK,
    MUTE,
    TEMPMUTE,
    WARN,
    TEMPWARN;

    public boolean isBanType() {
        return this == BAN || this == TEMPBAN || this == IPBAN || this == TEMPIPBAN;
    }

    public boolean isIpBanType() {
        return this == IPBAN || this == TEMPIPBAN;
    }

    public boolean isMuteType() {
        return this == MUTE || this == TEMPMUTE;
    }

    public boolean isWarnType() {
        return this == WARN || this == TEMPWARN;
    }

    public boolean isTemporary() {
        return this == TEMPBAN || this == TEMPIPBAN || this == TEMPMUTE || this == TEMPWARN;
    }
}
