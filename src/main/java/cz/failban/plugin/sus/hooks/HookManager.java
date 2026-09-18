package cz.failban.plugin.sus.hooks;

import cz.failban.plugin.sus.SusModule;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Hookne libovolný anticheat, který vyhazuje Bukkit event, čistě přes reflexi -
 * nic nemusí být na compile classpath. Vše (jméno event třídy a getterů) je
 * konfigurovatelné v sus.yml, takže nový anticheat lze přidat bez zásahu do kódu.
 *
 * Anticheaty bez API mají k dispozici most /susflag.
 */
public class HookManager {

    private final SusModule module;
    private final Listener listener = new Listener() {
    };
    private final List<String> active = new ArrayList<>();

    public HookManager(SusModule module) {
        this.module = module;
    }

    public List<String> active() {
        return active;
    }

    public void unregisterAll() {
        HandlerList.unregisterAll(listener);
        active.clear();
    }

    public void registerAll() {
        unregisterAll();

        ConfigurationSection root = module.config().getConfigurationSection("anticheat-hooks");
        if (root == null) {
            return;
        }

        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null || !section.getBoolean("enabled", true)) {
                continue;
            }

            String className = section.getString("event", "");
            if (className == null || className.isEmpty()) {
                continue;
            }

            Class<?> raw;
            try {
                raw = Class.forName(className);
            } catch (ClassNotFoundException | NoClassDefFoundError e) {
                continue;
            }
            if (!Event.class.isAssignableFrom(raw)) {
                module.plugin().getLogger().warning("[Sus] " + className + " is not a Bukkit event, skipping.");
                continue;
            }

            @SuppressWarnings("unchecked")
            Class<? extends Event> eventClass = (Class<? extends Event>) raw;

            List<String> playerGetters = getters(section, "player", List.of("getPlayer", "getUser", "getBukkitPlayer"));
            List<String> checkGetters = getters(section, "check",
                    List.of("getCheckName", "getCheck", "getHackType", "getType", "getName"));
            List<String> vlGetters = getters(section, "violations",
                    List.of("getViolations", "getVl", "getViolationLevel", "getVL", "getLevel"));
            int minVl = section.getInt("min-violations", 0);
            String label = section.getString("display-name", key);

            EventExecutor executor = (ignored, event) -> {
                if (!eventClass.isInstance(event)) {
                    return;
                }
                try {
                    handle(event, label, playerGetters, checkGetters, vlGetters, minVl);
                } catch (Throwable t) {
                    module.plugin().getLogger().warning("[Sus] Hook " + label + " failed: " + t);
                }
            };

            try {
                Bukkit.getPluginManager().registerEvent(eventClass, listener,
                        EventPriority.MONITOR, executor, module.plugin(), true);
                active.add(label + " (" + className + ")");
                module.plugin().getLogger().info("[Sus] Hooked anticheat: " + label);
            } catch (Throwable t) {
                module.plugin().getLogger().warning("[Sus] Could not hook " + label + ": " + t.getMessage());
            }
        }

        if (active.isEmpty()) {
            module.plugin().getLogger().info("[Sus] No anticheat event hook active - "
                    + "use the /susflag bridge in your anticheat's punishment commands.");
        }
    }

    private List<String> getters(ConfigurationSection section, String path, List<String> defaults) {
        List<String> list = section.getStringList(path);
        if (list.isEmpty()) {
            String single = section.getString(path, null);
            if (single != null && !single.isEmpty()) {
                return List.of(single);
            }
            return defaults;
        }
        return list;
    }

    private void handle(Object event, String label, List<String> playerGetters,
                        List<String> checkGetters, List<String> vlGetters, int minVl) {
        Object playerObj = invokeFirst(event, playerGetters);
        UUID uuid = resolveUuid(playerObj, 0);
        if (uuid == null) {
            return;
        }
        String name = resolveName(playerObj, uuid);

        String check = asText(invokeFirst(event, checkGetters));
        int vl = asInt(invokeFirst(event, vlGetters));
        if (vl < minVl) {
            return;
        }
        module.sus().flag(uuid, name, label, check, vl);
    }

    private Object invokeFirst(Object target, List<String> methodNames) {
        if (target == null) {
            return null;
        }
        for (String methodName : methodNames) {
            try {
                Method method = target.getClass().getMethod(methodName);
                method.setAccessible(true);
                Object value = method.invoke(target);
                if (value != null) {
                    return value;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private UUID resolveUuid(Object obj, int depth) {
        if (obj == null || depth > 2) {
            return null;
        }
        if (obj instanceof Player player) {
            return player.getUniqueId();
        }
        if (obj instanceof UUID uuid) {
            return uuid;
        }
        if (obj instanceof String name) {
            Player online = Bukkit.getPlayerExact(name);
            return online == null ? null : online.getUniqueId();
        }
        Object nested = invokeFirst(obj, List.of(
                "getPlayer", "getBukkitPlayer", "getUniqueId", "getUUID", "getUuid", "uuid", "getName"));
        return resolveUuid(nested, depth + 1);
    }

    private String resolveName(Object playerObj, UUID uuid) {
        if (playerObj instanceof Player player) {
            return player.getName();
        }
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return online.getName();
        }
        String offline = Bukkit.getOfflinePlayer(uuid).getName();
        return offline == null ? "unknown" : offline;
    }

    private String asText(Object obj) {
        if (obj == null) {
            return "UNKNOWN";
        }
        if (obj instanceof String s) {
            return s;
        }
        if (obj instanceof Enum<?> e) {
            return e.name();
        }
        Object nested = invokeFirst(obj, List.of("getName", "getCheckName", "name"));
        if (nested instanceof String s) {
            return s;
        }
        return String.valueOf(obj);
    }

    private int asInt(Object obj) {
        if (obj instanceof Number number) {
            return number.intValue();
        }
        if (obj instanceof String s) {
            try {
                return (int) Double.parseDouble(s.replace(",", "."));
            } catch (NumberFormatException ignored) {
                return 1;
            }
        }
        return 1;
    }
}
