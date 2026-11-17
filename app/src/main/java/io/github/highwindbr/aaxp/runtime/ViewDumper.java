package io.github.highwindbr.aaxp.runtime;

import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Describes the views a process currently has on screen: class, id name, position, size,
 * visibility and colours. Text content is never included, only its length, so nothing a
 * notification or a media title says leaves Android Auto. Must run on the main thread.
 */
public final class ViewDumper {
    public static final String AUTHORITY = "io.github.highwindbr.aaxp.reports";
    private static final int MAX_NODES = 6000;

    private ViewDumper() { }

    /** The description, or null when this process shows no windows. */
    public static String dump(String processName) throws Exception {
        List<View> roots = roots();
        if (roots.isEmpty()) return null;
        StringBuilder out = new StringBuilder();
        out.append("# AA Experiments screen layout\n# process ").append(processName)
                .append("\n# time ").append(System.currentTimeMillis()).append('\n');
        int[] budget = {MAX_NODES};
        for (View root : roots) {
            out.append("\n== window");
            try {
                Display display = root.getDisplay();
                if (display != null) {
                    DisplayMetrics metrics = new DisplayMetrics();
                    display.getRealMetrics(metrics);
                    out.append(" display=").append(display.getDisplayId())
                            .append(" name=").append(display.getName())
                            .append(" size=").append(metrics.widthPixels).append('x').append(metrics.heightPixels)
                            .append(" dpi=").append(metrics.densityDpi);
                }
            } catch (Throwable ignored) { }
            out.append('\n');
            describe(root, 0, out, budget);
        }
        if (budget[0] <= 0) out.append("\n# truncated at ").append(MAX_NODES).append(" views\n");
        return out.toString();
    }

    @SuppressWarnings("unchecked")
    private static List<View> roots() throws Exception {
        Class<?> globalClass = Class.forName("android.view.WindowManagerGlobal");
        Object global = globalClass.getMethod("getInstance").invoke(null);
        List<View> roots = new ArrayList<>();
        try {
            String[] names = (String[]) globalClass.getMethod("getViewRootNames").invoke(global);
            for (String name : names) {
                Object root = globalClass.getMethod("getRootView", String.class).invoke(global, name);
                if (root instanceof View) roots.add((View) root);
            }
        } catch (Throwable namesUnavailable) {
            Field views = globalClass.getDeclaredField("mViews");
            views.setAccessible(true);
            roots.addAll((List<View>) views.get(global));
        }
        return roots;
    }

    private static void describe(View view, int depth, StringBuilder out, int[] budget) {
        if (budget[0]-- <= 0) return;
        for (int i = 0; i < depth; i++) out.append(' ');
        out.append(view.getClass().getName());
        if (view.getId() != View.NO_ID) {
            try {
                out.append(" #").append(view.getResources().getResourceEntryName(view.getId()));
            } catch (Throwable unnamed) {
                out.append(" #0x").append(Integer.toHexString(view.getId()));
            }
        }
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        out.append(" [").append(location[0]).append(',').append(location[1]).append(' ')
                .append(view.getWidth()).append('x').append(view.getHeight()).append(']');
        if (view.getVisibility() != View.VISIBLE)
            out.append(view.getVisibility() == View.GONE ? " GONE" : " INVISIBLE");
        if (view.getAlpha() != 1f) out.append(" alpha=").append(view.getAlpha());
        if (view.getElevation() != 0f) out.append(" elevation=").append(view.getElevation());
        if (view.isFocusable()) out.append(view.isFocused() ? " FOCUSED" : " focusable");
        Drawable background = view.getBackground();
        if (background != null) {
            out.append(" bg=").append(background.getClass().getSimpleName());
            if (background instanceof ColorDrawable) out.append(hex(((ColorDrawable) background).getColor()));
        }
        Drawable foreground = view.getForeground();
        if (foreground != null) out.append(" fg=").append(foreground.getClass().getSimpleName());
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            out.append(" text=").append(text.length()).append("ch size=").append(text.getTextSize())
                    .append(" color=").append(hex(text.getCurrentTextColor()));
        }
        if (view instanceof ImageView) {
            ImageView image = (ImageView) view;
            Drawable drawable = image.getDrawable();
            if (drawable != null) out.append(" image=").append(drawable.getClass().getSimpleName());
            ColorStateList tint = image.getImageTintList();
            if (tint != null) out.append(" tint=").append(hex(tint.getDefaultColor()));
        }
        out.append('\n');
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) describe(group.getChildAt(i), depth + 1, out, budget);
        }
    }

    private static String hex(int color) {
        return String.format("#%08X", color);
    }
}
