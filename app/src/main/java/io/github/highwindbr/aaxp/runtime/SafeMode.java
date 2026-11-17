package io.github.highwindbr.aaxp.runtime;

import java.util.ArrayList;
import java.util.List;

/**
 * Crash safe mode for the look features (wallpaper, glyph icons, colours, status bar).
 *
 * Each Android Auto process keeps a small state file: the signature of the look settings it was
 * started with, when it crashed, and whether safe mode has tripped. Two crashes within
 * {@link #WINDOW_MS} with the same settings trip it; the look hooks are then left out until the
 * settings change or the Manager bumps {@link #EPOCH_KEY}.
 */
public final class SafeMode {
    /** Bumped by the Manager to switch the look back on after safe mode tripped. */
    public static final String EPOCH_KEY = "look.safe_epoch";
    public static final long WINDOW_MS = 5 * 60_000L;
    public static final int CRASH_LIMIT = 2;
    private static final String HEADER = "aax-safe-1";

    private SafeMode() { }

    public static final class State {
        public final String signature;
        public final long[] crashes;
        public final boolean tripped;

        public State(String signature, long[] crashes, boolean tripped) {
            this.signature = signature;
            this.crashes = crashes;
            this.tripped = tripped;
        }
    }

    /** Signature of the look settings; null when none of them is set. */
    public static String signature(String... values) {
        boolean any = false;
        StringBuilder joined = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            String value = values[i];
            // The epoch (last value) alone does not count as a look setting.
            if (value != null && !value.isEmpty() && i < values.length - 1) any = true;
            joined.append(value == null ? "" : value).append('\u0001');
        }
        if (!any) return null;
        return Integer.toHexString(joined.toString().hashCode()) + "-" + joined.length();
    }

    /** Reads a state file's text; null when it is missing or unreadable. */
    public static State parse(String text) {
        if (text == null) return null;
        String[] lines = text.split("\n", -1);
        if (lines.length < 4 || !HEADER.equals(lines[0]) || lines[1].isEmpty()) return null;
        List<Long> crashes = new ArrayList<>();
        try {
            if (!lines[2].isEmpty()) for (String part : lines[2].split(",")) crashes.add(Long.parseLong(part));
        } catch (NumberFormatException e) {
            return null;
        }
        long[] times = new long[crashes.size()];
        for (int i = 0; i < times.length; i++) times[i] = crashes.get(i);
        return new State(lines[1], times, "1".equals(lines[3]));
    }

    public static String encode(State state) {
        StringBuilder crashes = new StringBuilder();
        for (long crash : state.crashes) {
            if (crashes.length() > 0) crashes.append(',');
            crashes.append(crash);
        }
        return HEADER + "\n" + state.signature + "\n" + crashes + "\n" + (state.tripped ? "1" : "0");
    }

    /** State for a process starting with {@code signature}: changed settings start clean. */
    public static State atStart(State stored, String signature) {
        if (stored == null || !stored.signature.equals(signature)) return new State(signature, new long[0], false);
        return stored;
    }

    /** State after a crash at {@code nowMs}; trips at {@link #CRASH_LIMIT} crashes in the window. */
    public static State afterCrash(State state, long nowMs) {
        List<Long> recent = new ArrayList<>();
        for (long crash : state.crashes) if (crash <= nowMs && nowMs - crash <= WINDOW_MS) recent.add(crash);
        recent.add(nowMs);
        long[] times = new long[recent.size()];
        for (int i = 0; i < times.length; i++) times[i] = recent.get(i);
        return new State(state.signature, times, state.tripped || times.length >= CRASH_LIMIT);
    }

    /** A file-name-safe form of a process name. */
    public static String fileToken(String processName) {
        return processName == null ? "unknown" : processName.replaceAll("[^A-Za-z0-9_.-]", "_");
    }
}
