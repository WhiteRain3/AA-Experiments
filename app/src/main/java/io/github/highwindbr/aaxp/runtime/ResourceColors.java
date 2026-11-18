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
    /**
     * The launcher's ring around the item the knob is on ({@code ?attr/palette_p_85}). The ring
     * may instead be computed from the Primary role at runtime, in which case this has no effect.
     */
    public static final String FOCUS_RING = "focus_ring";

    /** Group id to the colour resources it covers. */
    private static final Map<String, List<String>> GROUPS = new LinkedHashMap<>();
    static {
        GROUPS.put(TEXT, Arrays.asList("baseline_palette_n_95", "dynamic_palette_n_95"));
        GROUPS.put(LAUNCHER_BACKGROUND, Arrays.asList("baseline_palette_n_15", "dynamic_palette_n_15"));
        GROUPS.put(MAP_FOCUS, Arrays.asList("neutral_palette_p_40"));
        GROUPS.put(FOCUS_RING, Arrays.asList("baseline_palette_p_85", "dynamic_palette_p_85"));
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

    /** Layout of the platform's resolved attribute table: seven numbers per attribute. */
    public static final int ENTRY_SIZE = 7;
    private static final int ENTRY_TYPE = 0;
    private static final int ENTRY_DATA = 1;
    private static final int ENTRY_RESOURCE_ID = 3;
    /** android.util.TypedValue: the four "colour written as a number" types. */
    private static final int TYPE_FIRST_COLOR = 0x1c;
    private static final int TYPE_LAST_COLOR = 0x1f;
    private static final int TYPE_COLOR_ARGB8 = 0x1c;

    /**
     * Whether the platform's table has the layout {@link #rewrite} expects. It has seven numbers
     * per attribute from Android 10 (API 29); before that it had six. {@code platformEntries} is
     * the platform's own count when it could be read, else null; a count that is not seven
     * rules the table out whatever the version.
     */
    public static boolean tableLayoutSupported(int sdk, Integer platformEntries) {
        if (sdk < 29) return false;
        return platformEntries == null || platformEntries == ENTRY_SIZE;
    }

    /** True when a resolved value of this type is a plain colour (not a file, state list or reference). */
    public static boolean isColorType(int type) {
        return type >= TYPE_FIRST_COLOR && type <= TYPE_LAST_COLOR;
    }

    /**
     * Rewrites a resolved attribute table in place: every plain colour that came from one of the
     * resources in {@code byId} becomes the configured colour. This is where layouts get their
     * colours from, whatever is read afterwards (a colour, a text colour, a background).
     * Returns the positions (attribute indexes) that would change; nothing is written when
     * {@code apply} is false. A table that does not have the expected layout is left alone.
     */
    public static int rewrite(int[] data, Map<Integer, Integer> byId, boolean apply) {
        if (data == null || byId == null || byId.isEmpty() || data.length % ENTRY_SIZE != 0) return 0;
        int changed = 0;
        for (int at = 0; at + ENTRY_SIZE <= data.length; at += ENTRY_SIZE) {
            int id = data[at + ENTRY_RESOURCE_ID];
            if (id == 0 || !isColorType(data[at + ENTRY_TYPE])) continue;
            Integer color = byId.get(id);
            if (color == null) continue;
            if (apply) {
                data[at + ENTRY_TYPE] = TYPE_COLOR_ARGB8;
                data[at + ENTRY_DATA] = color;
            }
            changed++;
        }
        return changed;
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
