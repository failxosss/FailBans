package cz.failban.plugin.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TimeUtil {

    private static final Pattern PATTERN = Pattern.compile("(\\d+)(s|m|h|d|w|mo|y)");

    /**
     * Returns the duration in milliseconds. -1 means permanent.
     * Supported units: s, m, h, d, w, mo, y
     * Supports combinations like "1d12h30m".
     * Returns -2 if the format is invalid.
     */
    public static long parseDuration(String input) {
        if (input == null) return -2;
        String lower = input.toLowerCase().trim();
        if (lower.equals("perm") || lower.equals("permanent") || lower.equals("trvale")) {
            return -1;
        }

        Matcher matcher = PATTERN.matcher(lower);
        long totalMillis = 0;
        boolean found = false;

        while (matcher.find()) {
            found = true;
            long value = Long.parseLong(matcher.group(1));
            String unit = matcher.group(2);
            totalMillis += switch (unit) {
                case "s" -> value * 1000L;
                case "m" -> value * 60_000L;
                case "h" -> value * 3_600_000L;
                case "d" -> value * 86_400_000L;
                case "w" -> value * 604_800_000L;
                case "mo" -> value * 2_592_000_000L; // 30 days
                case "y" -> value * 31_536_000_000L; // 365 days
                default -> 0L;
            };
        }

        if (!found || totalMillis <= 0) return -2;
        return totalMillis;
    }

    public static String formatDuration(long millis) {
        if (millis < 0) return "permanently";
        if (millis == 0) return "0s";

        long seconds = millis / 1000;
        long days = seconds / 86400;
        seconds %= 86400;
        long hours = seconds / 3600;
        seconds %= 3600;
        long minutes = seconds / 60;
        seconds %= 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0 || sb.isEmpty()) sb.append(seconds).append("s");
        return sb.toString().trim();
    }
}
