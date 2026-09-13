# FailBan – Complete Punishment System for Minecraft (Paper 1.21+)

The plugin includes: ban, tempban, unban, kick, mute, tempmute, unmute, warn, tempwarn,
unwarn, history, check, banlist, unpunish, change-reason, failban reload/help,
plus a bonus **/failcheck** command – shows a player's current IP, all known IP
addresses, alt accounts (other names that joined from the same IP), and a summary
of bans/kicks/mutes/warns.

A custom "Connection Lost" screen (styled after the reference image) is shown on
ban and kick, fully configurable in `config.yml` (colors, text, "Unban in", TS/forum
links, etc.).

**Discord webhook logging** is also included — every punishment (ban, mute, warn,
kick, unban, unpunish, reason changes...) can be logged to a Discord channel as a
colored embed.

## How to build the plugin (requires Java 21 and Maven)

1. Install (if you don't have them): Java 21 JDK and Apache Maven.
2. Extract this `FailBan/` folder anywhere on your disk.
3. In a terminal/cmd, navigate to the `FailBan/` folder (where `pom.xml` is located).
4. Run:
   ```
   mvn clean package
   ```
5. The finished plugin will be in `target/FailBan.jar`.
6. Upload `FailBan.jar` to the `plugins/` folder on your Paper 1.21+ server and restart it.

Maven will automatically download the PaperMC API and the SQLite library — no manual
installation needed.

## After first launch

`plugins/FailBan/` will contain:
- `config.yml` – all messages, kick screen, prefix, Discord webhook settings
- `database.db` – SQLite database storing all punishments and IP history (no external
  setup required)

## Discord webhook logging

1. In Discord: Channel Settings → Integrations → Webhooks → New Webhook → copy the URL.
2. In `config.yml` set:
   ```yaml
   discord:
     enabled: true
     webhook-url: "https://discord.com/api/webhooks/..."
   ```
3. Run `/failban reload`.

Colors: bans are red, mutes orange, warns yellow, kicks purple, punishment removals green.

## Permissions

Everything defaults to `op`. It's recommended to set up your own permission groups
with LuckPerms, e.g. `failban.ban`, `failban.mute`, `failban.failcheck`, etc. — see
`plugin.yml`.

## Notes

- `/failcheck <player>` also works on offline players who have joined the server before.
- Durations for `tempban`, `tempmute`, `tempwarn` are written like: `10m`, `2h`, `7d`,
  `1mo`, `1y`, or combined: `1d12h30m`. Use `perm` for a permanent punishment on temp
  commands.
- Customize the kick screen appearance in `config.yml` under `kick-screen:` — supports
  `&` color codes.
- The database is a local SQLite file, so no external MySQL server is required.
