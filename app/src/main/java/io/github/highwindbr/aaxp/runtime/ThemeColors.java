package io.github.highwindbr.aaxp.runtime;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Colour overrides for Android Auto's internal palette roles.
 *
 * Android Auto resolves its interface colours through one enum of roles ("Primary", "Surface",
 * "OutlineVariant", ...). The Manager locates that enum in the installed build by its role names
 * and stores its class name; the module then answers the enum's colour methods with the colours
 * configured here, for the roles that have one. Every other role, and every failure, falls
 * through to Android Auto's own colour.
 */
public final class ThemeColors {
    /** Remote preference: binary name of the role enum in the installed Android Auto build. */
    public static final String CLASS_KEY = "theme.role_class";
    /** Remote preference: "Role=AARRGGBB;Role=RRGGBB;..."; absent means no overrides. */
    public static final String COLORS_KEY = "theme.role_colors";

    /** Role names that together identify the enum; they are ordinary Material role names. */
    public static final String[] MARKERS = {
            "OnPrimaryContainer", "OutlineVariant", "SurfaceContainerHighest", "InverseOnSurface"
    };

    private ThemeColors() { }

    /** True when the strings of a class initialiser contain every marker role. */
    public static boolean looksLikeRoleEnum(Collection<String> initialiserStrings) {
        if (initialiserStrings == null) return false;
        for (String marker : MARKERS) if (!initialiserStrings.contains(marker)) return false;
        return true;
    }

    /**
     * Parses "Role=colour" entries separated by ';' or new lines. Colours are RRGGBB (opaque) or
     * AARRGGBB, with an optional '#'. Returns null when any entry is malformed.
     */
    public static Map<String, Integer> parse(String text) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (text == null) return result;
        for (String raw : text.split("[;\\n]")) {
            String entry = raw.trim();
            if (entry.isEmpty()) continue;
            int equals = entry.indexOf('=');
            if (equals <= 0) return null;
            String role = entry.substring(0, equals).trim();
            String hex = entry.substring(equals + 1).trim();
            if (hex.startsWith("#")) hex = hex.substring(1);
            if (!role.matches("[A-Za-z]+") || (hex.length() != 6 && hex.length() != 8)) return null;
            try {
                long parsed = Long.parseLong(hex, 16);
                result.put(role, (int) (hex.length() == 6 ? 0xFF000000L | parsed : parsed));
            } catch (NumberFormatException invalid) {
                return null;
            }
        }
        return result;
    }

    /** Canonical single-line form of [colors], or null when there is nothing to store. */
    public static String encode(Map<String, Integer> colors) {
        if (colors == null || colors.isEmpty()) return null;
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, Integer> entry : colors.entrySet()) {
            if (out.length() > 0) out.append(';');
            out.append(entry.getKey()).append('=').append(String.format("%08X", entry.getValue()));
        }
        return out.toString();
    }
}
