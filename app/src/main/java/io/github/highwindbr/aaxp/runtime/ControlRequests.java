package io.github.highwindbr.aaxp.runtime;

/**
 * One-shot requests the Manager sends to the module through remote preferences:
 * restart Android Auto, and start its developer head unit server.
 *
 * A request is "wall-clock millis | nonce". It is only honoured while fresh, so a stale value
 * left in preferences can never restart Android Auto or start a service later on.
 */
public final class ControlRequests {
    public static final String RESTART_KEY = "control.restart_request";
    public static final String HEAD_UNIT_KEY = "control.head_unit_request";
    public static final long MAX_AGE_MS = 30_000L;
    /** When a restarted process tries to start the head unit server, counted from module load. */
    public static final long[] HEAD_UNIT_START_DELAYS_MS = {3_000L, 7_000L, 12_000L};

    private ControlRequests() { }

    public static String make(long nowMs, String nonce) {
        return nowMs + "|" + nonce;
    }

    /** True when {@code value} is a well-formed request issued at most {@link #MAX_AGE_MS} ago. */
    public static boolean isFresh(String value, long nowMs) {
        if (value == null) return false;
        int bar = value.indexOf('|');
        if (bar <= 0 || bar == value.length() - 1) return false;
        final long issued;
        try {
            issued = Long.parseLong(value.substring(0, bar));
        } catch (NumberFormatException invalid) {
            return false;
        }
        long age = nowMs - issued;
        return age >= 0 && age <= MAX_AGE_MS;
    }

    /**
     * Picks the service behind Android Auto's "Start head unit server" developer action from the
     * package's declared services. The name is matched structurally because it is not part of
     * any public contract; null means this build has no recognisable candidate.
     */
    public static String pickHeadUnitService(String[] serviceNames) {
        if (serviceNames == null) return null;
        String fallback = null;
        for (String name : serviceNames) {
            if (name == null) continue;
            String lower = name.toLowerCase(java.util.Locale.ROOT);
            if (!lower.contains("headunit")) continue;
            if (lower.contains("developer")) return name;
            if (fallback == null) fallback = name;
        }
        return fallback;
    }
}
