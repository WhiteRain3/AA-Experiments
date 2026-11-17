package io.github.highwindbr.aaxp.runtime;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Changes to Android Auto's status bar (clock, signal, battery). The setting is stored as
 * "hide=clock,battery_level;clock=AARRGGBB;icons=AARRGGBB;plate=off"; every part is optional.
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
    private static final List<String> LAYOUTS = Arrays.asList(
            "rail_statusbar", "rail_statusbar_rhd", "vertical_rail_statusbar", "hero_vertical_rail_statusbar");

    private StatusBarTweaks() { }

    public static final class Config {
        public final Set<String> hide;
        public final Integer clockColor;
        public final Integer iconColor;
        public final boolean noPlate;

        public Config(Set<String> hide, Integer clockColor, Integer iconColor, boolean noPlate) {
            this.hide = hide;
            this.clockColor = clockColor;
            this.iconColor = iconColor;
            this.noPlate = noPlate;
        }

        public boolean isEmpty() {
            return hide.isEmpty() && clockColor == null && iconColor == null && !noPlate;
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
        boolean noPlate = false;
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
                default:
                    return null;
            }
        }
        Config config = new Config(hide, clock, icons, noPlate);
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
