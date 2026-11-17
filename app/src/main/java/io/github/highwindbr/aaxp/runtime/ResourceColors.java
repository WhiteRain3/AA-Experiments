package io.github.highwindbr.aaxp.runtime;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Colours Android Auto takes from its fixed resource palette instead of the palette roles, so
 * the role overrides never reach them. Each group names what it paints on screen and the colour
 * resources behind it. The setting is stored as "group=AARRGGBB;group=AARRGGBB".
 *
 * The resource names are from Android Auto 18.0. A name that does not exist in the installed
 * version is simply skipped.
 */
public final class ResourceColors {
    public static final String KEY = "theme.resource_colors";

    public static final String TEXT = "text";
    public static final String LAUNCHER_BACKGROUND = "launcher_bg";
    public static final String MAP_FOCUS = "map_focus";

    /** Group id to the colour resources it covers. */
    private static final Map<String, List<String>> GROUPS = new LinkedHashMap<>();
    static {
        GROUPS.put(TEXT, Arrays.asList("baseline_palette_n_95", "dynamic_palette_n_95"));
        GROUPS.put(LAUNCHER_BACKGROUND, Arrays.asList("baseline_palette_n_15", "dynamic_palette_n_15"));
        GROUPS.put(MAP_FOCUS, Arrays.asList("neutral_palette_p_40"));
    }

    private ResourceColors() { }

    public static List<String> groups() {
        return List.copyOf(GROUPS.keySet());
    }

    /** Parses the stored setting into group to colour; null when malformed. Never throws. */
    public static Map<String, Integer> parse(String text) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (text == null || text.isEmpty()) return result;
        for (String part : text.split(";")) {
            int equals = part.indexOf('=');
            if (equals <= 0) return null;
            String group = part.substring(0, equals), hex = part.substring(equals + 1);
            if (!GROUPS.containsKey(group) || hex.length() != 8) return null;
            try {
                result.put(group, (int) Long.parseLong(hex, 16));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return result;
    }

    /** The stored form of {@code colors}; null when empty. Unknown groups are left out. */
    public static String encode(Map<String, Integer> colors) {
        StringBuilder text = new StringBuilder();
        for (String group : GROUPS.keySet()) {
            Integer color = colors.get(group);
            if (color == null) continue;
            if (text.length() > 0) text.append(';');
            text.append(group).append('=').append(String.format("%08X", color));
        }
        return text.length() == 0 ? null : text.toString();
    }

    /** Colour resource name to colour, for every resource of every configured group. */
    public static Map<String, Integer> byResourceName(Map<String, Integer> colors) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (colors == null) return result;
        for (Map.Entry<String, Integer> entry : colors.entrySet()) {
            List<String> names = GROUPS.get(entry.getKey());
            if (names != null) for (String name : names) result.put(name, entry.getValue());
        }
        return result;
    }
}
