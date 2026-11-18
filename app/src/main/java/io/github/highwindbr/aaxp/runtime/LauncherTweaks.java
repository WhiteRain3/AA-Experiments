package io.github.highwindbr.aaxp.runtime;

/**
 * Changes to Android Auto's app launcher (the grid of app icons). The setting is stored as
 * "names=off"; a missing part means Android Auto's own behaviour.
 * The names are view ids in Android Auto's launcher item layout (seen in 18.0): each item holds
 * an image container and, under it, a text container with the app's name.
 */
public final class LauncherTweaks {
    public static final String KEY = "launcher.config";
    /** The part of a launcher item that holds the app's name (and a second line of text). */
    public static final String TEXT_CONTAINER = "grid_item_text_container";

    private LauncherTweaks() { }

    public static final class Config {
        /** Hide the app names under the launcher icons. */
        public final boolean hideNames;

        public Config(boolean hideNames) {
            this.hideNames = hideNames;
        }

        public boolean isEmpty() {
            return !hideNames;
        }
    }

    /** Parses the stored setting; null when it is missing, empty, malformed or changes nothing. Never throws. */
    public static Config parse(String text) {
        if (text == null || text.isEmpty()) return null;
        boolean hideNames = false;
        for (String part : text.split(";")) {
            int equals = part.indexOf('=');
            if (equals <= 0) return null;
            String name = part.substring(0, equals), value = part.substring(equals + 1);
            switch (name) {
                case "names":
                    if (!"off".equals(value)) return null;
                    hideNames = true;
                    break;
                default:
                    return null;
            }
        }
        Config config = new Config(hideNames);
        return config.isEmpty() ? null : config;
    }

    /** The stored form of {@code config}; null when it changes nothing. */
    public static String encode(Config config) {
        if (config == null || config.isEmpty()) return null;
        return "names=off";
    }
}
