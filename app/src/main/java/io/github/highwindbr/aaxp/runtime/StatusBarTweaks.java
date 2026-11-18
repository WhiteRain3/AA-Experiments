package io.github.highwindbr.aaxp.runtime;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Changes to Android Auto's status bar (clock, signal, battery). The setting is stored as
 * "hide=clock,battery_level;clock=AARRGGBB;icons=AARRGGBB;plate=off;battery=percent;
 * clock_size=120;clock_bold=on;plate_color=AARRGGBB;plate_radius=12;icon_size=120;battery_low_red=on;battery_level=70,30;battery_high_color=AARRGGBB;battery_mid_color=AARRGGBB;
 * battery_low_color=AARRGGBB;battery_charge_color=AARRGGBB"; every part is optional.
 * The names are the view ids in Android Auto's own status bar layouts.
 */
public final class StatusBarTweaks {
    public static final String KEY = "statusbar.config";
    /** Parts that can be hidden: view id and what it is. */
    public static final List<String> HIDEABLE = Arrays.asList(
            "clock", "signal_icon", "battery_level", "dnd_icon", "notification_count_wrapper");
    public static final List<String> TINTABLE_ICONS = Arrays.asList(
            "cell_signal", "cell_info_overlay", "battery_level", "dnd_icon");
    /**
     * Android Auto 18.0's battery icon paints itself red at this level and below while the phone
     * is not charging (read from its drawable: {@code if (level <= 15) red}).
     */
    public static final int LOW_BATTERY = 15;

    /** True when Android Auto's own battery icon would show its red warning. Unknown level is not low. */
    public static boolean isLowBattery(int percent, boolean charging) {
        return percent >= 0 && percent <= LOW_BATTERY && !charging;
    }

    /**
     * Whether the battery icon gets the icon colour right now. It does whenever an icon colour
     * is set, except, with "keep the red warning" on, while the battery is low: then the colour
     * is taken off so Android Auto's own red shows.
     */
    public static boolean colourBatteryIcon(Config config, int percent, boolean charging) {
        if (config == null || config.iconColor == null) return false;
        return !(config.batteryLowRed && isLowBattery(percent, charging));
    }

    /** Icons that take their colour from a colour filter because their drawable ignores a tint. */
    public static final List<String> FILTERED_ICONS = Arrays.asList("battery_level");
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

    /** Size of the signal and battery icons as a percentage of Android Auto's own. */
    public static final int DEFAULT_ICON_SIZE = 100;
    public static final int MIN_ICON_SIZE = 70;
    public static final int MAX_ICON_SIZE = 140;
    /** The icons the size applies to when the row holding them cannot be found. */
    public static final List<String> SIZED_ICONS = Arrays.asList("signal_icon", "battery_level");

    /** The drawing scale for an icon size in percent, e.g. 1.2 for 120. */
    public static float iconScale(int percent) {
        return Math.max(MIN_ICON_SIZE, Math.min(MAX_ICON_SIZE, percent)) / 100f;
    }

    /**
     * The battery number lives in the icon row, so it is drawn at the row's scale too. This is
     * the text size that makes it come out at {@code wanted} on screen all the same.
     */
    public static float sizeInsideScaledRow(float wanted, int iconPercent) {
        return wanted / iconScale(iconPercent);
    }

    /** The widest battery number, used to reserve room so the row does not jump between levels. */
    public static final String WIDEST_PERCENT = "100%";
    /** The icon row is never drawn smaller than this, however little room there is. */
    public static final float MIN_ROW_SCALE = 0.5f;

    /** How the icon row is drawn: at which scale, and how wide its container has to be for that. */
    public static final class RowFit {
        public final float scale;
        public final int containerWidth;

        RowFit(float scale, int containerWidth) {
            this.scale = scale;
            this.containerWidth = containerWidth;
        }
    }

    /**
     * Fits the icon row into the rail. {@code needed} is the row's full width at 100% (icons plus
     * the battery number), {@code wanted} the scale asked for, {@code maxContainer} the widest
     * the row's container can become inside the rail. The row is drawn at the wanted scale when
     * that fits, otherwise as large as fits, so nothing is cut off or pushed out of view.
     */
    public static RowFit fitRow(int needed, float wanted, int maxContainer) {
        if (needed <= 0 || maxContainer <= 0) return new RowFit(wanted, 0);
        float scale = Math.max(MIN_ROW_SCALE, Math.min(wanted, (float) maxContainer / needed));
        return new RowFit(scale, Math.min(maxContainer, (int) Math.ceil(needed * scale)));
    }

    /**
     * Whether the battery number gets a line of its own. Beside a signal or battery icon there
     * is no room for it in the rail (the plate is about 85 px wide; two icons and a number at
     * the clock's size need well over 100), so it goes between the icons and the clock. With
     * both icons hidden it stays where the icons were.
     */
    public static boolean stacksNumber(Config config) {
        if (config == null || !config.batteryPercent) return false;
        for (String icon : SIZED_ICONS) if (!config.hide.contains(icon)) return true;
        return false;
    }

    /** Lines of a stacked status bar are never drawn smaller than this share of their size. */
    public static final float MIN_STACK_SCALE = 0.6f;

    /** Three lines in the plate, top to bottom: icons, battery number, clock. */
    public static final class Stack {
        /** Scale of the icon row and of the number. The clock is never scaled. */
        public final float iconScale, numberScale;
        /** Top edge of each line, measured from the top of the plate. */
        public final float iconTop, numberTop, clockTop;

        Stack(float iconScale, float numberScale, float iconTop, float numberTop, float clockTop) {
            this.iconScale = iconScale;
            this.numberScale = numberScale;
            this.iconTop = iconTop;
            this.numberTop = numberTop;
            this.clockTop = clockTop;
        }
    }

    /**
     * Lays the three lines out in a plate of {@code plateHeight}. The icon row is wanted at
     * {@code iconScale}. When the lines are taller than the plate together, the icons and the
     * number are drawn smaller by the same share (never below {@link #MIN_STACK_SCALE}); the
     * clock keeps its size. What is left over is spread evenly above, between and below.
     */
    public static Stack stack(int plateHeight, int rowHeight, int numberHeight, int clockHeight, float iconScale) {
        float icons = Math.max(0, rowHeight) * iconScale, number = Math.max(0, numberHeight);
        float clock = Math.max(0, clockHeight);
        float shrink = 1f;
        if (icons + number > 0f && icons + number + clock > plateHeight) {
            shrink = Math.max(MIN_STACK_SCALE, Math.min(1f, (plateHeight - clock) / (icons + number)));
        }
        icons *= shrink;
        number *= shrink;
        float gap = Math.max(0f, plateHeight - icons - number - clock) / 4f;
        float numberTop = gap + icons + gap;
        // When even the smallest size does not fit, the clock still ends at the plate's bottom.
        float clockTop = Math.min(numberTop + number + gap, Math.max(0f, plateHeight - clock));
        return new Stack(iconScale * shrink, shrink, gap, numberTop, clockTop);
    }

    /** Where the row's left edge goes so that the scaled row is centred in its container. */
    public static float centredLeft(int containerWidth, int rowWidth, float scale) {
        return (containerWidth - rowWidth * scale) / 2f;
    }

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

    /** Colouring the battery number by level: at or above the high mark, below the low mark, or in between. */
    public static final int DEFAULT_BATTERY_HIGH = 70;
    public static final int DEFAULT_BATTERY_LOW = 30;
    public static final int DEFAULT_BATTERY_HIGH_COLOR = 0xFF4CAF50;
    public static final int DEFAULT_BATTERY_LOW_COLOR = 0xFFF44336;

    /** Battery level in percent, 0 to 100; -1 when the level is unknown. */
    public static int percent(int level, int scale) {
        if (level < 0 || scale <= 0) return -1;
        return Math.min(100, Math.round(level * 100f / scale));
    }

    /** Battery level as text, e.g. "74%"; empty when the level is unknown. */
    public static String percentText(int level, int scale) {
        int percent = percent(level, scale);
        return percent < 0 ? "" : percent + "%";
    }

    /**
     * The colour of the battery number at {@code percent} (-1 = unknown). {@code base} is the
     * colour the number has without colouring by level: the battery colour, else the clock's.
     * With colouring by level off, or the level unknown, that is the answer. Otherwise: the
     * charging colour while charging, if one is set; the high colour at or above the high mark;
     * the low colour below the low mark; in between the middle colour, which is {@code base}
     * unless one was chosen.
     */
    public static int levelColor(int percent, boolean charging, Config config, int base) {
        if (config == null || !config.batteryByLevel || percent < 0) return base;
        if (charging && config.batteryChargeColor != null) return config.batteryChargeColor;
        if (percent >= config.batteryHigh) return config.batteryHighColor;
        if (percent < config.batteryLow) return config.batteryLowColor;
        return config.batteryMidColor != null ? config.batteryMidColor : base;
    }

    /** True when the two marks can be used: 0 <= low < high <= 100. */
    public static boolean validMarks(int high, int low) {
        return low >= 0 && low < high && high <= 100;
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
        /** Size of the signal and battery icons in percent. */
        public final int iconSize;
        /** Colour the battery number by level. Only means something while the number is shown. */
        public final boolean batteryByLevel;
        public final int batteryHigh;
        public final int batteryLow;
        public final int batteryHighColor;
        public final int batteryLowColor;
        /** Colour between the marks; null follows the battery colour, else the clock. */
        public final Integer batteryMidColor;
        /** Colour while charging; null means charging is not treated differently. */
        public final Integer batteryChargeColor;
        /** Keep Android Auto's red battery icon at low battery although an icon colour is set. */
        public final boolean batteryLowRed;

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
            this.batteryByLevel = from.batteryByLevel;
            boolean marks = validMarks(from.batteryHigh, from.batteryLow);
            this.batteryHigh = marks ? from.batteryHigh : DEFAULT_BATTERY_HIGH;
            this.batteryLow = marks ? from.batteryLow : DEFAULT_BATTERY_LOW;
            this.batteryHighColor = from.batteryHighColor;
            this.batteryLowColor = from.batteryLowColor;
            this.batteryMidColor = from.batteryMidColor;
            this.batteryChargeColor = from.batteryChargeColor;
            this.batteryLowRed = from.batteryLowRed;
            this.iconSize = Math.max(MIN_ICON_SIZE, Math.min(MAX_ICON_SIZE, from.iconSize));
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

        /** Every setting copied into a builder, for changing a few and keeping the rest. */
        public Builder toBuilder() {
            Builder builder = builder(new LinkedHashSet<>(hide), clockColor, iconColor, noPlate, batteryPercent,
                    batteryColor, batterySize, clockSize, clockBold);
            builder.plateColor = plateColor;
            builder.plateRadius = plateRadius;
            builder.iconSize = iconSize;
            builder.batteryByLevel = batteryByLevel;
            builder.batteryHigh = batteryHigh;
            builder.batteryLow = batteryLow;
            builder.batteryHighColor = batteryHighColor;
            builder.batteryLowColor = batteryLowColor;
            builder.batteryMidColor = batteryMidColor;
            builder.batteryChargeColor = batteryChargeColor;
            builder.batteryLowRed = batteryLowRed;
            return builder;
        }

        public boolean isEmpty() {
            return hide.isEmpty() && clockColor == null && iconColor == null && !noPlate && !batteryPercent
                    && clockSize == DEFAULT_CLOCK_SIZE && !clockBold && plateColor == null && plateRadius == null
                    && iconSize == DEFAULT_ICON_SIZE;
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
        public int iconSize = DEFAULT_ICON_SIZE;
        public boolean batteryByLevel;
        public int batteryHigh = DEFAULT_BATTERY_HIGH;
        public int batteryLow = DEFAULT_BATTERY_LOW;
        public int batteryHighColor = DEFAULT_BATTERY_HIGH_COLOR;
        public int batteryLowColor = DEFAULT_BATTERY_LOW_COLOR;
        public Integer batteryMidColor;
        public Integer batteryChargeColor;
        public boolean batteryLowRed;

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
        int iconSize = DEFAULT_ICON_SIZE;
        Builder level = new Builder();
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
                case "battery_level": {
                    String[] marks = value.split(",");
                    if (marks.length != 2) return null;
                    try {
                        level.batteryHigh = Integer.parseInt(marks[0]);
                        level.batteryLow = Integer.parseInt(marks[1]);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                    if (!validMarks(level.batteryHigh, level.batteryLow)) return null;
                    level.batteryByLevel = true;
                    break;
                }
                case "battery_high_color":
                case "battery_low_color":
                case "battery_mid_color":
                case "battery_charge_color": {
                    Integer parsed = color(value);
                    if (parsed == null) return null;
                    if (name.equals("battery_high_color")) level.batteryHighColor = parsed;
                    else if (name.equals("battery_low_color")) level.batteryLowColor = parsed;
                    else if (name.equals("battery_mid_color")) level.batteryMidColor = parsed;
                    else level.batteryChargeColor = parsed;
                    break;
                }
                case "battery_low_red":
                    if (!"on".equals(value)) return null;
                    level.batteryLowRed = true;
                    break;
                case "icon_size":
                    try {
                        iconSize = Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                    if (iconSize < MIN_ICON_SIZE || iconSize > MAX_ICON_SIZE) return null;
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
        builder.iconSize = iconSize;
        builder.batteryByLevel = level.batteryByLevel;
        builder.batteryHigh = level.batteryHigh;
        builder.batteryLow = level.batteryLow;
        builder.batteryHighColor = level.batteryHighColor;
        builder.batteryLowColor = level.batteryLowColor;
        builder.batteryMidColor = level.batteryMidColor;
        builder.batteryChargeColor = level.batteryChargeColor;
        builder.batteryLowRed = level.batteryLowRed;
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
            if (config.batteryByLevel) {
                append(text, "battery_level=" + config.batteryHigh + "," + config.batteryLow);
                if (config.batteryHighColor != DEFAULT_BATTERY_HIGH_COLOR) {
                    append(text, "battery_high_color=" + String.format("%08X", config.batteryHighColor));
                }
                if (config.batteryMidColor != null) append(text, "battery_mid_color=" + String.format("%08X", config.batteryMidColor));
                if (config.batteryLowColor != DEFAULT_BATTERY_LOW_COLOR) {
                    append(text, "battery_low_color=" + String.format("%08X", config.batteryLowColor));
                }
                if (config.batteryChargeColor != null) {
                    append(text, "battery_charge_color=" + String.format("%08X", config.batteryChargeColor));
                }
            }
        }
        if (config.clockSize != DEFAULT_CLOCK_SIZE) append(text, "clock_size=" + config.clockSize);
        if (config.clockBold) append(text, "clock_bold=on");
        // A removed plate has no colour or corners to speak of.
        if (!config.noPlate) {
            if (config.plateColor != null) append(text, "plate_color=" + String.format("%08X", config.plateColor));
            if (config.plateRadius != null) append(text, "plate_radius=" + config.plateRadius);
        }
        if (config.iconSize != DEFAULT_ICON_SIZE) append(text, "icon_size=" + config.iconSize);
        // Only means something while an icon colour is set.
        if (config.batteryLowRed && config.iconColor != null) append(text, "battery_low_red=on");
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
