package io.github.highwindbr.aaxp.runtime;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Changes to Android Auto's status bar (clock, signal, battery). The setting is stored as
 * "hide=clock,battery_level;clock=AARRGGBB;icons=AARRGGBB;plate=off;battery=percent;
 * clock_size=120;clock_bold=on;plate_color=AARRGGBB;plate_radius=12"; every part is optional.
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

    /** Clock text size as a percentage of the size Android Auto gives it. */
    public static final int DEFAULT_CLOCK_SIZE = 100;
    public static final int MIN_CLOCK_SIZE = 70;
    public static final int MAX_CLOCK_SIZE = 140;

    /** Largest corner radius offered for the plate, in dp. */
    public static final int MAX_PLATE_RADIUS = 48;

    /** A corner radius in dp as pixels on a screen of the given density; never negative. */
    public static float radiusPx(int dp, float density) {
        return Math.max(0f, dp * density);
    }

    /** {@code original} text size scaled to {@code percent}, never below one pixel. */
    public static float scaledSize(float original, int percent) {
        return Math.max(1f, original * percent / 100f);
    }

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
        /** Size of the clock as a percentage of Android Auto's own size. */
        public final int clockSize;
        public final boolean clockBold;
        /** Colour of the plate behind the status bar, opacity included; null keeps Android Auto's. */
        public final Integer plateColor;
        /** Corner radius of the plate in dp; null keeps Android Auto's. */
        public final Integer plateRadius;

        public Config(Set<String> hide, Integer clockColor, Integer iconColor, boolean noPlate) {
            this(hide, clockColor, iconColor, noPlate, false);
        }

        public Config(Set<String> hide, Integer clockColor, Integer iconColor, boolean noPlate, boolean batteryPercent) {
            this(hide, clockColor, iconColor, noPlate, batteryPercent, null, DEFAULT_BATTERY_SIZE);
        }

        public Config(Set<String> hide, Integer clockColor, Integer iconColor, boolean noPlate, boolean batteryPercent,
                      Integer batteryColor, int batterySize) {
            this(hide, clockColor, iconColor, noPlate, batteryPercent, batteryColor, batterySize, DEFAULT_CLOCK_SIZE, false);
        }

        public Config(Set<String> hide, Integer clockColor, Integer iconColor, boolean noPlate, boolean batteryPercent,
                      Integer batteryColor, int batterySize, int clockSize, boolean clockBold) {
            this(builder(hide, clockColor, iconColor, noPlate, batteryPercent, batteryColor, batterySize, clockSize, clockBold));
        }

        private static Builder builder(Set<String> hide, Integer clockColor, Integer iconColor, boolean noPlate,
                boolean batteryPercent, Integer batteryColor, int batterySize, int clockSize, boolean clockBold) {
            Builder builder = new Builder();
            builder.hide = hide;
            builder.clockColor = clockColor;
            builder.iconColor = iconColor;
            builder.noPlate = noPlate;
            builder.batteryPercent = batteryPercent;
            builder.batteryColor = batteryColor;
            builder.batterySize = batterySize;
            builder.clockSize = clockSize;
            builder.clockBold = clockBold;
            return builder;
        }

        private Config(Builder from) {
            Set<String> hide = from.hide == null ? new LinkedHashSet<>() : from.hide;
            Integer clockColor = from.clockColor, iconColor = from.iconColor, batteryColor = from.batteryColor;
            boolean noPlate = from.noPlate, batteryPercent = from.batteryPercent;
            int batterySize = from.batterySize;
            this.plateColor = from.plateColor;
            this.plateRadius = from.plateRadius == null ? null : Math.max(0, Math.min(MAX_PLATE_RADIUS, from.plateRadius));
            this.clockSize = Math.max(MIN_CLOCK_SIZE, Math.min(MAX_CLOCK_SIZE, from.clockSize));
            this.clockBold = from.clockBold;
            this.batteryColor = batteryColor;
            this.batterySize = Math.max(MIN_BATTERY_SIZE, Math.min(MAX_BATTERY_SIZE, batterySize));
            this.batteryPercent = batteryPercent;
            this.hide = hide;
            this.clockColor = clockColor;
            this.iconColor = iconColor;
            this.noPlate = noPlate;
        }

        public boolean isEmpty() {
            return hide.isEmpty() && clockColor == null && iconColor == null && !noPlate && !batteryPercent
                    && clockSize == DEFAULT_CLOCK_SIZE && !clockBold && plateColor == null && plateRadius == null;
        }
    }

    /** Every setting as a plain field, for building a {@link Config} without a long argument list. */
    public static final class Builder {
        public Set<String> hide = new LinkedHashSet<>();
        public Integer clockColor;
        public Integer iconColor;
        public boolean noPlate;
        public boolean batteryPercent;
        public Integer batteryColor;
        public int batterySize = DEFAULT_BATTERY_SIZE;
        public int clockSize = DEFAULT_CLOCK_SIZE;
        public boolean clockBold;
        public Integer plateColor;
        public Integer plateRadius;

        public Config build() {
            return new Config(this);
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
        int clockSize = DEFAULT_CLOCK_SIZE;
        boolean clockBold = false;
        Integer plateColor = null, plateRadius = null;
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
                case "clock_size":
                    try {
                        clockSize = Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                    if (clockSize < MIN_CLOCK_SIZE || clockSize > MAX_CLOCK_SIZE) return null;
                    break;
                case "plate_color":
                    plateColor = color(value);
                    if (plateColor == null) return null;
                    break;
                case "plate_radius":
                    try {
                        plateRadius = Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                    if (plateRadius < 0 || plateRadius > MAX_PLATE_RADIUS) return null;
                    break;
                case "clock_bold":
                    if (!"on".equals(value)) return null;
                    clockBold = true;
                    break;
                default:
                    return null;
            }
        }
        Builder builder = Config.builder(hide, clock, icons, noPlate, batteryPercent, batteryColor, batterySize, clockSize, clockBold);
        builder.plateColor = plateColor;
        builder.plateRadius = plateRadius;
        Config config = builder.build();
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
        if (config.clockSize != DEFAULT_CLOCK_SIZE) append(text, "clock_size=" + config.clockSize);
        if (config.clockBold) append(text, "clock_bold=on");
        // A removed plate has no colour or corners to speak of.
        if (!config.noPlate) {
            if (config.plateColor != null) append(text, "plate_color=" + String.format("%08X", config.plateColor));
            if (config.plateRadius != null) append(text, "plate_radius=" + config.plateRadius);
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
