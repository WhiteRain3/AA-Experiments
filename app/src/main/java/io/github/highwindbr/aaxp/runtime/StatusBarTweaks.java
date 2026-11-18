package io.github.highwindbr.aaxp.runtime;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Changes to Android Auto's status bar (clock, signal, battery). The setting is stored as
 * "hide=clock,battery_level;clock=AARRGGBB;icons=AARRGGBB;plate=off;battery=percent"; every
 * part is optional.
 * The names are the view ids in Android Auto's own status bar layouts.
 */
public final class StatusBarTweaks {
    public static final String KEY = "statusbar.config";
    /** Parts that can be hidden: view id and what it is. */
    public static final List<String> HIDEABLE = Arrays.asList(
            "clock", "signal_icon", "battery_level", "dnd_icon", "notification_count_wrapper");
    public static final List<String> TINTABLE_ICONS = Arrays.asList(
            "cell_signal", "cell_info_overlay", "battery_level", "dnd_icon");
    public static final String CLOCK = "clock";
    public static final String PLATE = "status_bar_card_view";
    /** The row holding the signal and battery icons, where the battery number is added. */
    public static final String ICON_ROW = "icon_row";

    public static final int DEFAULT_BATTERY_SIZE = 70;
    public static final int MIN_BATTERY_SIZE = 50;
    public static final int MAX_BATTERY_SIZE = 130;

    /** Battery level as text, e.g. "74%"; empty when the level is unknown. */
    public static String percentText(int level, int scale) {
        if (level < 0 || scale <= 0) return "";
        return Math.min(100, Math.round(level * 100f / scale)) + "%";
    }
    private static final List<String> LAYOUTS = Arrays.asList(
            "rail_statusbar", "rail_statusbar_rhd", "vertical_rail_statusbar", "hero_vertical_rail_statusbar");

    private StatusBarTweaks() { }

    public static final class Config {
        public final Set<String> hide;
        public final Integer clockColor;
        public final Integer iconColor;
        public final boolean noPlate;
        /** Show the phone's battery level as a number next to the status icons. */
        public final boolean batteryPercent;
        /** Colour of the battery number; null follows the clock. */
        public final Integer batteryColor;
        /** Size of the battery number as a percentage of the clock's text size. */
        public final int batterySize;

        public Config(Set<String> hide, Integer clockColor, Integer iconColor, boolean noPlate) {
            this(hide, clockColor, iconColor, noPlate, false);
        }

        public Config(Set<String> hide, Integer clockColor, Integer iconColor, boolean noPlate, boolean batteryPercent) {
            this(hide, clockColor, iconColor, noPlate, batteryPercent, null, DEFAULT_BATTERY_SIZE);
        }

        public Config(Set<String> hide, Integer clockColor, Integer iconColor, boolean noPlate, boolean batteryPercent,
                      Integer batteryColor, int batterySize) {
            this.batteryColor = batteryColor;
            this.batterySize = Math.max(MIN_BATTERY_SIZE, Math.min(MAX_BATTERY_SIZE, batterySize));
            this.batteryPercent = batteryPercent;
            this.hide = hide;
            this.clockColor = clockColor;
            this.iconColor = iconColor;
            this.noPlate = noPlate;
        }

        public boolean isEmpty() {
            return hide.isEmpty() && clockColor == null && iconColor == null && !noPlate && !batteryPercent;
        }
    }

    public static boolean isStatusBarLayout(String entryName) {
        return entryName != null && LAYOUTS.contains(entryName);
    }

    /** Parses the stored setting; null when it is missing, empty or malformed. Never throws. */
    public static Config parse(String text) {
        if (text == null || text.isEmpty()) return null;
        Set<String> hide = new LinkedHashSet<>();
        Integer clock = null, icons = null;
        boolean noPlate = false, batteryPercent = false;
        Integer batteryColor = null;
        int batterySize = DEFAULT_BATTERY_SIZE;
        for (String part : text.split(";")) {
            int equals = part.indexOf('=');
            if (equals <= 0) return null;
            String name = part.substring(0, equals), value = part.substring(equals + 1);
            switch (name) {
                case "hide":
                    for (String id : value.split(",")) {
                        if (!HIDEABLE.contains(id)) return null;
                        hide.add(id);
                    }
                    break;
                case "clock":
                    clock = color(value);
                    if (clock == null) return null;
                    break;
                case "icons":
                    icons = color(value);
                    if (icons == null) return null;
                    break;
                case "plate":
                    if (!"off".equals(value)) return null;
                    noPlate = true;
                    break;
                case "battery":
                    if (!"percent".equals(value)) return null;
                    batteryPercent = true;
                    break;
                case "battery_color":
                    batteryColor = color(value);
                    if (batteryColor == null) return null;
                    break;
                case "battery_size":
                    try {
                        batterySize = Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                    if (batterySize < MIN_BATTERY_SIZE || batterySize > MAX_BATTERY_SIZE) return null;
                    break;
                default:
                    return null;
            }
        }
        Config config = new Config(hide, clock, icons, noPlate, batteryPercent, batteryColor, batterySize);
        return config.isEmpty() ? null : config;
    }

    /** The stored form of {@code config}; null when it changes nothing. */
    public static String encode(Config config) {
        if (config == null || config.isEmpty()) return null;
        StringBuilder text = new StringBuilder();
        if (!config.hide.isEmpty()) text.append("hide=").append(String.join(",", config.hide));
        if (config.clockColor != null) append(text, "clock=" + String.format("%08X", config.clockColor));
        if (config.iconColor != null) append(text, "icons=" + String.format("%08X", config.iconColor));
        if (config.noPlate) append(text, "plate=off");
        if (config.batteryPercent) {
            append(text, "battery=percent");
            // The number's own colour and size only mean something while the number is shown.
            if (config.batteryColor != null) append(text, "battery_color=" + String.format("%08X", config.batteryColor));
            if (config.batterySize != DEFAULT_BATTERY_SIZE) append(text, "battery_size=" + config.batterySize);
        }
        return text.toString();
    }

    private static void append(StringBuilder text, String part) {
        if (text.length() > 0) text.append(';');
        text.append(part);
    }

    private static Integer color(String hex) {
        if (hex.length() != 8) return null;
        try {
            return (int) Long.parseLong(hex, 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
