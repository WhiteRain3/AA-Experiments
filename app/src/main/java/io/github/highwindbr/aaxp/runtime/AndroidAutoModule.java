package io.github.highwindbr.aaxp.runtime;

import android.content.SharedPreferences;
import android.os.ParcelFileDescriptor;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.content.Intent;
import android.content.ComponentName;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import io.github.highwindbr.aaxp.core.ExperimentalBooleanPolicy;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import android.content.pm.ApplicationInfo;
import io.github.highwindbr.aaxp.core.BuildFingerprint;
import io.github.highwindbr.aaxp.core.PackageBuildIdentity;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public final class AndroidAutoModule extends XposedModule {
    private static final String AA_PACKAGE = "com.google.android.projection.gearhead";
    private static final String PREF_GROUP = "dynamic_overrides_v2";
    private static final String TAG = "AAExperiments";
    private static final String EVENT_PREFIX = "AAX_EVENT ";
    private final OverrideSnapshot overrideSnapshot = new OverrideSnapshot();
    private final Map<String, String> lastReturnEvent = new ConcurrentHashMap<>();
    private final Map<String, Boolean> probedCalls = new ConcurrentHashMap<>();
    private final ExperimentalEventLimiter experimentalEvents = new ExperimentalEventLimiter();
    private SharedPreferences remotePreferences;
    private volatile String moduleProcessName;
    private volatile MediaStartupLaunchGate mediaLaunchGate;
    private volatile MediaStartupAutoGate mediaAutoGate;
    private volatile MediaStartupLabGate mediaLabGate;
    private volatile MediaStartupAutoGate mediaLabAutoGate;
    private volatile boolean mediaLabAutoAvailable;
    private volatile Method mediaLabFactory;
    private volatile Method mediaLabHelper;
    private volatile Method mediaLabDirect;
    private volatile Constructor<?> mediaLabOptions;
    private volatile ComponentName mediaLabComponent;
    private volatile Handler mediaLaunchHandler;
    private volatile Method mediaControllerFactory;
    private volatile Method mediaControllerStart;
    private volatile Constructor<?> mediaActivityOptionsConstructor;
    private volatile boolean mediaLaunchAvailable;
    private boolean mediaPollScheduled;
    private final Runnable mediaCommandPoll = new Runnable() {
        @Override public void run() {
            mediaPollScheduled = false;
            if (mediaLabGate != null) {
                if (!mediaLabGate.isReady()) return;
                checkMediaLabRequest();
                startMediaPolling();
                return;
            }
            if (mediaLaunchGate == null || !mediaLaunchGate.isReady() ||
                    !MediaStartupTrace.enabled(overrideSnapshot.get(MediaStartupTrace.KEY)))
                return;
            checkMediaLaunchRequest();
            startMediaPolling();
        }
    };
    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceListener =
            (preferences, key) -> {
                refreshSnapshot(preferences);
                if (MediaStartupLaunchGate.REQUEST_KEY.equals(key) ||
                        MediaStartupLabGate.REQUEST_KEY.equals(key)) requestMediaLaunchCheck();
                if (ControlRequests.RESTART_KEY.equals(key)) handleRestartRequest(preferences);
                if (ControlRequests.HEAD_UNIT_KEY.equals(key)) handleHeadUnitRequest(preferences, false);
                if (ControlRequests.DUMP_KEY.equals(key)) handleDumpRequest(preferences);
            };
    private volatile String handledRestartRequest;
    private volatile String handledHeadUnitRequest;

    @Override public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        moduleProcessName = param.getProcessName();
        event(Log.INFO, "MODULE_LOADED", null, "process", moduleProcessName, "api", String.valueOf(getApiVersion()));
        try {
            remotePreferences = getRemotePreferences(PREF_GROUP);
            refreshSnapshot(remotePreferences);
            remotePreferences.registerOnSharedPreferenceChangeListener(preferenceListener);
            // A head unit request issued together with a restart is picked up by the new process.
            // A restart request is deliberately NOT replayed here: that would restart in a loop.
            handleHeadUnitRequest(remotePreferences, true);
        } catch (Throwable t) {
            event(Log.WARN, "REMOTE_PREFERENCES_UNAVAILABLE", null, "error", t.getClass().getSimpleName());
            log(Log.WARN, TAG, "Remote preferences unavailable; all hooks fail open", t);
        }
    }

    @Override public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        if (!AA_PACKAGE.equals(param.getPackageName()) || !param.isFirstPackage()) return;
        if (lookAllowed(param.getApplicationInfo())) {
            installCarWallpaperHooks();
            installGlyphIconHooks();
            installThemeColorHooks(param.getClassLoader());
            installStatusBarHooks();
            installResourceColorHooks();
        }
        try {
            JSONObject profile = readProfile();
            if (profile.optInt("schemaVersion") != 3) {
                event(Log.WARN, "PROFILE_REJECTED", null, "reason", "unsupported_schema"); return;
            }
            ApplicationInfo app = param.getApplicationInfo();
            // PackageReady exposes ApplicationInfo, not PackageInfo. The version code is
            // bound indirectly by the verified artifact hashes and is supplied by the profile
            // solely to reproduce the canonical fingerprint byte-for-byte.
            long versionCode = profile.getLong("packageVersionCode");
            PackageBuildIdentity identity = BuildFingerprint.computeFromPaths(versionCode, app.sourceDir, app.splitSourceDirs);
            if (!identity.getFingerprintSha256().equalsIgnoreCase(profile.getString("buildFingerprintSha256"))) {
                event(Log.WARN, "PROFILE_REJECTED", null, "reason", "build_fingerprint_mismatch"); return;
            }
            JSONArray mappings = profile.getJSONArray("mappings");
            for (int i = 0; i < mappings.length(); i++) install(param.getClassLoader(), mappings.getJSONObject(i));
            JSONArray probes = profile.optJSONArray("probes");
            if (probes != null) for (int i = 0; i < probes.length(); i++)
                installProbe(param.getClassLoader(), probes.getJSONObject(i), identity.getBaseSha256());
            JSONArray experimental = profile.optJSONArray("experimental");
            if (experimental != null) for (int i = 0; i < experimental.length(); i++)
                installExperimental(param.getClassLoader(), experimental.getJSONObject(i), identity.getFingerprintSha256());
            JSONArray advanced = profile.optJSONArray("advanced");
            if (advanced != null) for (int i = 0; i < advanced.length(); i++) {
                JSONObject candidate = advanced.getJSONObject(i);
                boolean conflict = false;
                for (int j = 0; j < mappings.length(); j++) {
                    JSONObject normal = mappings.getJSONObject(j);
                    if (normal.optString("className").equals(candidate.optString("className")) &&
                            normal.optString("methodName").equals(candidate.optString("methodName")) &&
                            normal.optString("descriptor").equals(candidate.optString("descriptor"))) {
                        conflict = true; break;
                    }
                }
                if (conflict) {
                    event(Log.WARN, "ADVANCED_REJECTED", candidate.optString("key"),
                            "reason", "duplicate_normal_hook");
                } else {
                    installAdvanced(param.getClassLoader(), candidate,
                            identity.getFingerprintSha256(), identity.getBaseSha256());
                }
            }
            if (profile.optBoolean("videoTimingTrace", false)) {
                installVideoTimingTrace(param.getClassLoader(), identity.getBaseSha256());
            }
            if (MediaStartupTrace2.isProjectionProcess(moduleProcessName)) {
                // PF6.1 lab is an explicit manually armed alternative to
                // legacy PF2-PF4 hooks, never a second overlapping hook set.
                JSONObject lab = profile.optJSONObject("mediaStartupLab");
                if (lab != null) {
                    installMediaStartupLab(param.getClassLoader(), lab,
                            identity.getBaseSha256(), identity.getFingerprintSha256());
                } else if (profile.optBoolean("mediaStartupTrace", false)) {
                    installMediaStartupTrace2(param.getClassLoader(), identity.getBaseSha256());
                }
            }
            event(Log.INFO, "PROFILE_ACCEPTED", null, "mappings", String.valueOf(mappings.length()),
                    "probes", String.valueOf(probes == null ? 0 : probes.length()),
                    "experimental", String.valueOf(experimental == null ? 0 : experimental.length()),
                    "advanced", String.valueOf(advanced == null ? 0 : advanced.length()),
                    "build", identity.getFingerprintSha256().substring(0, 16));
        } catch (Throwable t) {
            event(Log.ERROR, "PROFILE_FAILED", null, "error", t.getClass().getSimpleName());
            log(Log.ERROR, TAG, "Dynamic profile failed; no further hooks installed", t);
        }
    }

    private static final android.net.Uri REPORTS = android.net.Uri.parse("content://" + ViewDumper.AUTHORITY);
    private volatile SafeMode.State safeState;
    private volatile java.io.File safeFile;

    private static android.content.Context currentApplication() {
        try {
            Object current = Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
            return current instanceof android.content.Context context ? context : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Crash safe mode. Returns false when the look hooks must be left out because Android Auto
     * crashed repeatedly with the current look settings. With no look setting stored there is
     * nothing to guard, so no bookkeeping happens at all. Any failure here allows the look.
     */
    private boolean lookAllowed(ApplicationInfo app) {
        try {
            String signature = SafeMode.signature(
                    overrideSnapshot.get(ThemeColors.COLORS_KEY), overrideSnapshot.get(GlyphIcons.KEY),
                    overrideSnapshot.get(CarWallpaper.VERSION_KEY), overrideSnapshot.get(StatusBarTweaks.KEY),
                    overrideSnapshot.get(CarWallpaper.RAIL_KEY), overrideSnapshot.get(ResourceColors.KEY),
                    overrideSnapshot.get(LauncherTweaks.KEY), overrideSnapshot.get(CarWallpaper.RAIL_LOOK_KEY),
                    overrideSnapshot.get(GlyphIcons.SIZE_KEY),
                    overrideSnapshot.get(SafeMode.EPOCH_KEY));
            if (signature == null) return true;
            java.io.File file = new java.io.File(app.dataDir, "aax_safe_" + SafeMode.fileToken(moduleProcessName) + ".txt");
            String stored = null;
            if (file.isFile()) stored = new String(java.nio.file.Files.readAllBytes(file.toPath()),
                    java.nio.charset.StandardCharsets.UTF_8);
            SafeMode.State state = SafeMode.atStart(SafeMode.parse(stored), signature);
            safeFile = file;
            safeState = state;
            if (state.tripped) {
                event(Log.WARN, "SAFE_MODE_ACTIVE", null, "process", String.valueOf(moduleProcessName));
                new Handler(Looper.getMainLooper()).postDelayed(this::announceSafeMode, 4_000L);
                return false;
            }
            watchForCrashes();
            // Android Auto may install its own handler later without chaining to ours.
            new Handler(Looper.getMainLooper()).postDelayed(this::watchForCrashes, 15_000L);
            return true;
        } catch (Throwable t) {
            event(Log.WARN, "SAFE_MODE_UNAVAILABLE", null, "error", t.getClass().getSimpleName());
            return true;
        }
    }

    private volatile Thread.UncaughtExceptionHandler crashWatcher;

    private void watchForCrashes() {
        try {
            final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
            if (previous != null && previous == crashWatcher) return;
            Thread.UncaughtExceptionHandler watcher = (thread, error) -> {
                try {
                    SafeMode.State state = safeState;
                    java.io.File file = safeFile;
                    if (state != null && file != null) {
                        SafeMode.State next = SafeMode.afterCrash(state, System.currentTimeMillis());
                        safeState = next;
                        java.nio.file.Files.write(file.toPath(),
                                SafeMode.encode(next).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    }
                } catch (Throwable ignored) { }
                if (previous != null) previous.uncaughtException(thread, error);
            };
            crashWatcher = watcher;
            Thread.setDefaultUncaughtExceptionHandler(watcher);
        } catch (Throwable t) {
            event(Log.WARN, "SAFE_MODE_UNAVAILABLE", null, "error", t.getClass().getSimpleName());
        }
    }

    /** Tells the user, on the phone and in the Manager, that the look was switched off. */
    private void announceSafeMode() {
        android.content.Context context = currentApplication();
        if (context == null) return;
        try {
            context.getContentResolver().call(REPORTS, "safe_mode", String.valueOf(moduleProcessName), null);
        } catch (Throwable t) {
            event(Log.WARN, "SAFE_MODE_REPORT_FAILED", null, "error", t.getClass().getSimpleName());
        }
        try {
            android.widget.Toast.makeText(context, "AA Experiments: look changes are off after repeated "
                    + "Android Auto crashes. Open AA Experiments to turn them back on.",
                    android.widget.Toast.LENGTH_LONG).show();
        } catch (Throwable ignored) { }
    }

    private volatile String handledDumpRequest;

    /** Manager-requested description of what this process shows on screen. Fails open. */
    private void handleDumpRequest(SharedPreferences preferences) {
        try {
            final String request = preferences.getString(ControlRequests.DUMP_KEY, null);
            if (!ControlRequests.isFresh(request, System.currentTimeMillis())
                    || request.equals(handledDumpRequest)) return;
            handledDumpRequest = request;
            new Handler(Looper.getMainLooper()).post(() -> {
                try {
                    final String text = ViewDumper.dump(String.valueOf(moduleProcessName));
                    if (text == null) return;
                    final android.content.Context context = currentApplication();
                    if (context == null) return;
                    new Thread(() -> {
                        try {
                            android.net.Uri target = REPORTS.buildUpon().appendPath("dump")
                                    .appendPath(SafeMode.fileToken(moduleProcessName)).build();
                            try (java.io.OutputStream out = context.getContentResolver().openOutputStream(target)) {
                                out.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                            }
                            event(Log.INFO, "VIEW_DUMP_SENT", null, "chars", String.valueOf(text.length()),
                                    "process", String.valueOf(moduleProcessName));
                        } catch (Throwable t) {
                            event(Log.WARN, "VIEW_DUMP_FAILED", null, "error", t.getClass().getSimpleName());
                        }
                    }, "aax-view-dump").start();
                } catch (Throwable t) {
                    event(Log.WARN, "VIEW_DUMP_FAILED", null, "error", t.getClass().getSimpleName());
                }
            });
        } catch (Throwable t) {
            event(Log.WARN, "VIEW_DUMP_FAILED", null, "error", t.getClass().getSimpleName());
        }
    }

    private volatile String statusBarRaw;
    private volatile StatusBarTweaks.Config statusBarParsed;
    private final java.util.Set<android.view.View> hiddenViews =
            java.util.Collections.synchronizedSet(java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>()));
    private volatile boolean anyHiddenViews;
    private final Map<Integer, Boolean> statusBarLayouts = new ConcurrentHashMap<>();

    /** Parsed status bar setting, re-parsed only when the stored text changes; null when off. */
    private StatusBarTweaks.Config statusBar() {
        String raw = overrideSnapshot.get(StatusBarTweaks.KEY);
        if (raw == null) return null;
        if (!raw.equals(statusBarRaw)) {
            statusBarParsed = StatusBarTweaks.parse(raw);
            statusBarRaw = raw;
        }
        return statusBarParsed;
    }

    /**
     * Status bar changes. Android Auto builds its status bar from one of a few layouts; after
     * such a layout is inflated the configured parts are hidden or recoloured. Hidden parts are
     * kept hidden when Android Auto later tries to show them. Installed only when the setting
     * exists at process start, so the first use needs a restart. Fails open.
     */
    private void installStatusBarHooks() {
        final StatusBarTweaks.Config atStart = statusBar();
        final boolean railAtStart = CarWallpaper.isEnabled(overrideSnapshot.get(CarWallpaper.RAIL_KEY))
                || CarWallpaper.parseRailLook(overrideSnapshot.get(CarWallpaper.RAIL_LOOK_KEY)).color != null;
        final LauncherTweaks.Config launcherAtStart = launcher();
        if (atStart == null && !railAtStart && launcherAtStart == null) return;
        try {
            Method inflate = android.view.LayoutInflater.class.getDeclaredMethod(
                    "inflate", int.class, android.view.ViewGroup.class, boolean.class);
            hook(inflate).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                Object result = chain.proceed();
                try {
                    if (result instanceof android.view.View railRoot) applyRailWallpaper(railRoot, (Integer) chain.getArg(0));
                    StatusBarTweaks.Config config = statusBar();
                    if (config != null && result instanceof android.view.View view) {
                        Integer layout = (Integer) chain.getArg(0);
                        Boolean match = statusBarLayouts.get(layout);
                        if (match == null) {
                            String name = null;
                            try { name = view.getResources().getResourceEntryName(layout); } catch (Throwable ignored) { }
                            match = StatusBarTweaks.isStatusBarLayout(name);
                            statusBarLayouts.put(layout, match);
                        }
                        if (match) applyStatusBar(view, config);
                    }
                } catch (Throwable t) {
                    eventOnce(Log.WARN, "STATUS_BAR_FAILED", "inflate", t.getClass().getSimpleName());
                }
                try {
                    LauncherTweaks.Config config = launcher();
                    if (config != null && result instanceof android.view.View view) applyLauncher(view, (Integer) chain.getArg(0), config);
                } catch (Throwable t) {
                    eventOnce(Log.WARN, "LAUNCHER_FAILED", "inflate", t.getClass().getSimpleName());
                }
                return result;
            });
            if ((atStart != null && !atStart.hide.isEmpty()) || (launcherAtStart != null && launcherAtStart.hideNames)) {
                Method setVisibility = android.view.View.class.getDeclaredMethod("setVisibility", int.class);
                hook(setVisibility).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    if (anyHiddenViews && !Integer.valueOf(android.view.View.GONE).equals(chain.getArg(0))
                            && hiddenViews.contains(chain.getThisObject())) {
                        return chain.proceed(new Object[] {android.view.View.GONE});
                    }
                    return chain.proceed();
                });
                try {
                    Method transition = android.view.View.class.getDeclaredMethod("setTransitionVisibility", int.class);
                    hook(transition).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        if (anyHiddenViews && !Integer.valueOf(android.view.View.GONE).equals(chain.getArg(0))
                                && hiddenViews.contains(chain.getThisObject())) {
                            return chain.proceed(new Object[] {android.view.View.GONE});
                        }
                        return chain.proceed();
                    });
                } catch (Throwable unavailable) {
                    event(Log.INFO, "STATUS_BAR_TRANSITION_HOOK_SKIPPED", null, "error", unavailable.getClass().getSimpleName());
                }
            }
            event(Log.INFO, "STATUS_BAR_HOOKS", null, "hide", String.valueOf(atStart == null ? 0 : atStart.hide.size()),
                    "rail", String.valueOf(railAtStart), "launcher", String.valueOf(launcherAtStart != null));
        } catch (Throwable t) {
            event(Log.WARN, "STATUS_BAR_HOOK_FAILED", null, "error", t.getClass().getSimpleName());
        }
    }

    private volatile String resourceColorsRaw;
    private volatile Map<Integer, Integer> resourceColorsById = Map.of();

    /**
     * Resource id to replacement colour, rebuilt when the stored setting changes. Needs Android
     * Auto's resources to turn names into ids, so it stays empty until the application exists.
     */
    private Map<Integer, Integer> resourceColors() {
        String raw = overrideSnapshot.get(ResourceColors.KEY);
        if (raw == null) return Map.of();
        if (raw.equals(resourceColorsRaw)) return resourceColorsById;
        android.content.Context context = currentApplication();
        if (context == null) return Map.of();
        Map<Integer, Integer> byId = new HashMap<>();
        android.content.res.Resources resources = context.getResources();
        for (Map.Entry<String, Integer> entry : ResourceColors.byResourceName(ResourceColors.parse(raw)).entrySet()) {
            int id = resources.getIdentifier(entry.getKey(), "color", AA_PACKAGE);
            if (id != 0) byId.put(id, entry.getValue());
        }
        resourceColorsById = byId;
        resourceColorsRaw = raw;
        event(Log.INFO, "RESOURCE_COLORS_READY", null, "resources", String.valueOf(byId.size()));
        return byId;
    }

    /**
     * Colours Android Auto reads from its fixed resource palette (labels, clock, rail icons,
     * launcher background, the map's focus border). Layouts resolve those through TypedArray,
     * code through Resources; both are answered with the configured colour when the value came
     * from one of the configured colour resources. Installed only when the setting exists at
     * process start. Fails open.
     */
    private void installResourceColorHooks() {
        if (overrideSnapshot.get(ResourceColors.KEY) == null) return;
        int installed = 0;
        try {
            Method[] typed = {
                    android.content.res.TypedArray.class.getDeclaredMethod("getColor", int.class, int.class),
                    android.content.res.TypedArray.class.getDeclaredMethod("getColorStateList", int.class)};
            for (Method method : typed) {
                final boolean list = method.getReturnType() != int.class;
                hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    Map<Integer, Integer> colors = resourceColors();
                    if (!colors.isEmpty() && chain.getThisObject() instanceof android.content.res.TypedArray array) {
                        int id = array.getResourceId((Integer) chain.getArg(0), 0);
                        Integer color = colors.get(id);
                        // Other apps' resources share the same id range; only Android Auto's own count.
                        if (color != null && isAndroidAutoResource(array.getResources(), id)) {
                            eventOnce(Log.INFO, "RESOURCE_COLOR_SERVED", "layout", "first");
                            return list ? android.content.res.ColorStateList.valueOf(color) : (Object) color;
                        }
                    }
                    return chain.proceed();
                });
                installed++;
            }
            Method[] direct = {
                    android.content.res.Resources.class.getDeclaredMethod("getColor", int.class, android.content.res.Resources.Theme.class),
                    android.content.res.Resources.class.getDeclaredMethod("getColorStateList", int.class, android.content.res.Resources.Theme.class)};
            for (Method method : direct) {
                final boolean list = method.getReturnType() != int.class;
                hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    Map<Integer, Integer> colors = resourceColors();
                    if (!colors.isEmpty()) {
                        int id = (Integer) chain.getArg(0);
                        Integer color = colors.get(id);
                        if (color != null && chain.getThisObject() instanceof android.content.res.Resources resources
                                && isAndroidAutoResource(resources, id)) {
                            eventOnce(Log.INFO, "RESOURCE_COLOR_SERVED", "code", "first");
                            return list ? android.content.res.ColorStateList.valueOf(color) : (Object) color;
                        }
                    }
                    return chain.proceed();
                });
                installed++;
            }
        } catch (Throwable t) {
            event(Log.WARN, "RESOURCE_COLOR_HOOK_FAILED", null, "error", t.getClass().getSimpleName());
        }
        event(Log.INFO, "RESOURCE_COLOR_HOOKS", null, "installed", String.valueOf(installed));
    }

    /** True when {@code id} in {@code resources} belongs to Android Auto and not to another app it is reading. */
    private static boolean isAndroidAutoResource(android.content.res.Resources resources, int id) {
        try {
            return AA_PACKAGE.equals(resources.getResourcePackageName(id));
        } catch (Throwable notFound) {
            return false;
        }
    }

    private final Map<Integer, Boolean> railLayouts = new ConcurrentHashMap<>();
    private volatile String railWallpaperVersion;
    private volatile android.graphics.Bitmap railWallpaperBitmap;
    private static final String RAIL_WALLPAPER_TAG = "aax_rail_wallpaper";

    /**
     * The rail is its own window and Android Auto never paints the wallpaper there. When a rail
     * slice is stored, an image view showing it is added behind the rail's (transparent) content,
     * by default matched to how the launcher shows the wallpaper: 80% opaque over dark grey. The
     * strip's colour and the slice's strength can be set; a colour alone paints the strip.
     */
    private void applyRailWallpaper(android.view.View root, Integer layout) {
        String version = overrideSnapshot.get(CarWallpaper.RAIL_KEY);
        CarWallpaper.RailLook look = CarWallpaper.parseRailLook(overrideSnapshot.get(CarWallpaper.RAIL_LOOK_KEY));
        boolean slice = CarWallpaper.isEnabled(version);
        // Without a wallpaper slice only a chosen colour is painted; with neither, nothing is touched.
        if (!slice && look.color == null) return;
        try {
            Boolean match = railLayouts.get(layout);
            android.view.View container = null;
            if (match == null || match) {
                container = findByName(root, "rail_container");
                if (match == null) railLayouts.put(layout, container != null);
            }
            if (!(container instanceof android.widget.FrameLayout frame)) return;
            frame.setBackgroundColor(look.colorOrDefault());
            if (!slice) {
                eventOnce(Log.INFO, "RAIL_COLOR_APPLIED", "rail", String.format("%08X", look.colorOrDefault()));
                return;
            }
            android.widget.ImageView image = frame.findViewWithTag(RAIL_WALLPAPER_TAG);
            if (image == null) {
                image = new android.widget.ImageView(frame.getContext());
                image.setTag(RAIL_WALLPAPER_TAG);
                image.setScaleType(android.widget.ImageView.ScaleType.FIT_XY);
                image.setBackgroundColor(0);
                frame.addView(image, 0, new android.widget.FrameLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT));
            }
            image.setAlpha(look.alpha());
            android.graphics.Bitmap bitmap = railWallpaperBitmap;
            if (bitmap == null || !version.equals(railWallpaperVersion)) {
                try (ParcelFileDescriptor descriptor = openRemoteFile(CarWallpaper.RAIL_FILE_NAME)) {
                    bitmap = android.graphics.BitmapFactory.decodeFileDescriptor(descriptor.getFileDescriptor());
                }
                railWallpaperBitmap = bitmap;
                railWallpaperVersion = version;
            }
            if (bitmap != null) {
                image.setImageBitmap(bitmap);
                eventOnce(Log.INFO, "RAIL_WALLPAPER_APPLIED", "rail", version);
            }
        } catch (Throwable t) {
            eventOnce(Log.WARN, "RAIL_WALLPAPER_FAILED", "rail", t.getClass().getSimpleName());
        }
    }

    private final java.util.Set<Method> hookedVisibilitySetters =
            java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    /**
     * Some view classes (ImageView among them) override setVisibility. The platform's
     * precompiled code can call View.setVisibility from inside such an override without going
     * through the hook on View, so a hidden view of that class could be shown again unseen.
     * Hook every override between the view's class and View, once each.
     */
    private void hookOwnVisibilitySetters(Class<?> type) {
        for (Class<?> current = type; current != null && current != android.view.View.class; current = current.getSuperclass()) {
            try {
                Method own = current.getDeclaredMethod("setVisibility", int.class);
                if (!hookedVisibilitySetters.add(own)) continue;
                hook(own).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    if (anyHiddenViews && !Integer.valueOf(android.view.View.GONE).equals(chain.getArg(0))
                            && hiddenViews.contains(chain.getThisObject())) {
                        return chain.proceed(new Object[] {android.view.View.GONE});
                    }
                    return chain.proceed();
                });
                event(Log.INFO, "STATUS_BAR_VISIBILITY_HOOK", current.getName());
            } catch (NoSuchMethodException notOverridden) {
                // This class does not override it; nothing to hook.
            } catch (Throwable t) {
                event(Log.WARN, "STATUS_BAR_VISIBILITY_HOOK_FAILED", current.getName(), "error", t.getClass().getSimpleName());
            }
        }
    }

    private static final String BATTERY_PERCENT_TAG = "aax_battery_percent";

    /**
     * Adds the phone's battery level as a number to the status bar's icon row. The text follows
     * the clock's colour and updates from the system's battery broadcast while it is on screen.
     */
    private void addBatteryPercent(android.view.View root, StatusBarTweaks.Config config) {
        try {
            if (!(findByName(root, StatusBarTweaks.ICON_ROW) instanceof android.view.ViewGroup row)) {
                eventOnce(Log.WARN, "BATTERY_PERCENT_NO_ROW", "status_bar", "first");
                return;
            }
            if (row.findViewWithTag(BATTERY_PERCENT_TAG) != null) return;
            final android.widget.TextView text = new android.widget.TextView(row.getContext());
            text.setTag(BATTERY_PERCENT_TAG);
            text.setSingleLine(true);
            text.setIncludeFontPadding(false);
            text.setGravity(android.view.Gravity.CENTER);
            int color = 0xFFF2F0F0;
            float size = 22f;
            if (findByName(root, StatusBarTweaks.CLOCK) instanceof android.widget.TextView clock) {
                color = clock.getCurrentTextColor();
                size = clock.getTextSize();
            } else {
                size = 32f;
            }
            size = size * config.batterySize / 100f;
            if (config.clockColor != null) color = config.clockColor;
            if (config.batteryColor != null) color = config.batteryColor;
            text.setTextColor(color);
            text.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, size);
            final android.content.BroadcastReceiver receiver = new android.content.BroadcastReceiver() {
                @Override public void onReceive(android.content.Context context, Intent intent) {
                    text.setText(StatusBarTweaks.percentText(
                            intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1),
                            intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)));
                }
            };
            text.addOnAttachStateChangeListener(new android.view.View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(android.view.View view) {
                    try {
                        android.content.IntentFilter filter = new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                        Intent sticky = android.os.Build.VERSION.SDK_INT >= 33
                                ? view.getContext().getApplicationContext().registerReceiver(receiver, filter,
                                        android.content.Context.RECEIVER_NOT_EXPORTED)
                                : view.getContext().getApplicationContext().registerReceiver(receiver, filter);
                        if (sticky != null) receiver.onReceive(view.getContext(), sticky);
                    } catch (Throwable t) {
                        eventOnce(Log.WARN, "BATTERY_PERCENT_FAILED", "register", t.getClass().getSimpleName());
                    }
                }
                @Override public void onViewDetachedFromWindow(android.view.View view) {
                    try {
                        view.getContext().getApplicationContext().unregisterReceiver(receiver);
                    } catch (Throwable ignored) { }
                }
            });
            row.addView(text, new android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
            eventOnce(Log.INFO, "BATTERY_PERCENT_ADDED", "status_bar", "first");
        } catch (Throwable t) {
            eventOnce(Log.WARN, "BATTERY_PERCENT_FAILED", "add", t.getClass().getSimpleName());
        }
    }

    private static android.view.View findByName(android.view.View root, String idName) {
        int id = root.getResources().getIdentifier(idName, "id", AA_PACKAGE);
        return id == 0 ? null : root.findViewById(id);
    }

    /** Hides {@code target} and keeps it hidden when Android Auto later tries to show it. */
    private void hideForGood(android.view.View target) {
        hiddenViews.add(target);
        anyHiddenViews = true;
        hookOwnVisibilitySetters(target.getClass());
        target.setVisibility(android.view.View.GONE);
        // Android Auto can show a view again without going through setVisibility; a view
        // with no size and no opacity stays out of sight either way.
        target.setAlpha(0f);
        android.view.ViewGroup.LayoutParams params = target.getLayoutParams();
        // In a ConstraintLayout a size of 0 means "stretch between constraints", not "no
        // size", so there the view keeps its size and relies on being gone and transparent.
        boolean constraintParent = target.getParent() != null
                && target.getParent().getClass().getName().contains("ConstraintLayout");
        if (params != null && !constraintParent) {
            params.width = 0;
            params.height = 0;
            target.setLayoutParams(params);
        }
    }

    private volatile String launcherRaw;
    private volatile LauncherTweaks.Config launcherParsed;
    private final Map<Integer, Boolean> launcherLayouts = new ConcurrentHashMap<>();

    /** Parsed launcher setting, re-parsed only when the stored text changes; null when off. */
    private LauncherTweaks.Config launcher() {
        String raw = overrideSnapshot.get(LauncherTweaks.KEY);
        if (raw == null) return null;
        if (!raw.equals(launcherRaw)) {
            launcherParsed = LauncherTweaks.parse(raw);
            launcherRaw = raw;
        }
        return launcherParsed;
    }

    /**
     * Launcher "icons only". A launcher item is recognised by the view id of its text container
     * in the tree a layout was inflated into; whether a layout produces one is remembered per
     * layout, so other layouts cost one map lookup. The container stays in the hidden set for
     * as long as the view lives, so the list re-binding a recycled item cannot show it again.
     */
    private void applyLauncher(android.view.View root, Integer layout, LauncherTweaks.Config config) {
        if (!config.hideNames) return;
        Boolean match = launcherLayouts.get(layout);
        if (match != null && !match) return;
        int id = root.getResources().getIdentifier(LauncherTweaks.TEXT_CONTAINER, "id", AA_PACKAGE);
        if (id == 0) {
            eventOnce(Log.WARN, "LAUNCHER_NO_ID", LauncherTweaks.TEXT_CONTAINER, "first");
            launcherLayouts.put(layout, false);
            return;
        }
        java.util.List<android.view.View> found = new java.util.ArrayList<>();
        collectById(root, id, found, 0);
        if (match == null) launcherLayouts.put(layout, !found.isEmpty());
        for (android.view.View container : found) {
            if (!hiddenViews.contains(container)) hideForGood(container);
        }
        if (!found.isEmpty()) eventOnce(Log.INFO, "LAUNCHER_NAMES_HIDDEN", "launcher", "first");
    }

    /** Every view with {@code id} under {@code view}; a layout can hold several launcher items. */
    private static void collectById(android.view.View view, int id, java.util.List<android.view.View> into, int depth) {
        if (view.getId() == id) {
            into.add(view);
            return;
        }
        if (depth < 12 && view instanceof android.view.ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) collectById(group.getChildAt(i), id, into, depth + 1);
        }
    }

    /**
     * Colour and corner radius of the plate behind the status bar. A card-type view takes both
     * through its own setters. Any other view gets a rounded background drawn by the module,
     * which needs a colour: a radius alone cannot be applied to it. The module log says which
     * route was taken (STATUS_BAR_PLATE: card, card_no_radius, drawable or radius_unavailable).
     */
    private void applyPlate(android.view.View plate, StatusBarTweaks.Config config) {
        if (plate == null) {
            eventOnce(Log.WARN, "STATUS_BAR_PLATE", "plate", "missing");
            return;
        }
        try {
            float radius = config.plateRadius == null ? -1f
                    : StatusBarTweaks.radiusPx(config.plateRadius, plate.getResources().getDisplayMetrics().density);
            boolean card = false, cardRadius = false;
            if (config.plateColor != null) {
                try {
                    plate.getClass().getMethod("setCardBackgroundColor", int.class).invoke(plate, config.plateColor);
                    card = true;
                } catch (Throwable notACard) {
                    // Handled below with a drawn background.
                }
            }
            if (radius >= 0f) {
                try {
                    plate.getClass().getMethod("setRadius", float.class).invoke(plate, radius);
                    cardRadius = true;
                } catch (Throwable noRadiusSetter) {
                    // Handled below.
                }
            }
            String route;
            if (config.plateColor != null && !card) {
                android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
                background.setColor(config.plateColor);
                if (radius >= 0f && !cardRadius) {
                    background.setCornerRadius(radius);
                    plate.setClipToOutline(true);
                }
                plate.setBackground(background);
                route = "drawable";
            } else if (radius >= 0f && !cardRadius) {
                route = card ? "card_no_radius" : "radius_unavailable";
            } else {
                route = "card";
            }
            // A see-through plate with a shadow under it shows the shadow through itself.
            if (config.plateColor != null && (config.plateColor >>> 24) < 0xFF) plate.setElevation(0f);
            eventOnce(Log.INFO, "STATUS_BAR_PLATE", "plate", route, "class", plate.getClass().getName());
        } catch (Throwable t) {
            eventOnce(Log.WARN, "STATUS_BAR_PLATE_FAILED", "plate", t.getClass().getSimpleName());
        }
    }

    /** The clock's text size and typeface as Android Auto set them, so a size is never applied twice over. */
    private final Map<android.view.View, Object[]> clockOriginals =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /** Clock size and boldness. Runs before the battery number is added, which sizes itself from the clock. */
    private void applyClockText(android.view.View root, StatusBarTweaks.Config config) {
        if (config.clockSize == StatusBarTweaks.DEFAULT_CLOCK_SIZE && !config.clockBold) return;
        try {
            if (!(findByName(root, StatusBarTweaks.CLOCK) instanceof android.widget.TextView clock)) {
                eventOnce(Log.WARN, "CLOCK_TEXT_NO_CLOCK", "status_bar", "first");
                return;
            }
            Object[] original = clockOriginals.get(clock);
            if (original == null) {
                original = new Object[] {clock.getTextSize(), clock.getTypeface()};
                clockOriginals.put(clock, original);
            }
            if (config.clockSize != StatusBarTweaks.DEFAULT_CLOCK_SIZE) {
                clock.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                        StatusBarTweaks.scaledSize((Float) original[0], config.clockSize));
            }
            if (config.clockBold) {
                clock.setTypeface(android.graphics.Typeface.create(
                        (android.graphics.Typeface) original[1], android.graphics.Typeface.BOLD));
            }
            eventOnce(Log.INFO, "CLOCK_TEXT_APPLIED", "status_bar",
                    config.clockSize + (config.clockBold ? "b" : ""), "px", String.valueOf(clock.getTextSize()));
        } catch (Throwable t) {
            eventOnce(Log.WARN, "CLOCK_TEXT_FAILED", "status_bar", t.getClass().getSimpleName());
        }
    }

    private void applyStatusBar(android.view.View root, StatusBarTweaks.Config config) {
        for (String name : config.hide) {
            android.view.View target = findByName(root, name);
            if (target == null) continue;
            hideForGood(target);
        }
        applyClockText(root, config);
        if (config.batteryPercent) addBatteryPercent(root, config);
        if (config.clockColor != null && findByName(root, StatusBarTweaks.CLOCK) instanceof android.widget.TextView clock) {
            clock.setTextColor(config.clockColor);
        }
        if (config.iconColor != null) {
            for (String name : StatusBarTweaks.TINTABLE_ICONS) {
                if (findByName(root, name) instanceof android.widget.ImageView icon) {
                    icon.setImageTintList(android.content.res.ColorStateList.valueOf(config.iconColor));
                }
            }
        }
        if (config.noPlate) {
            android.view.View plate = findByName(root, StatusBarTweaks.PLATE);
            if (plate != null) {
                try {
                    plate.getClass().getMethod("setCardBackgroundColor", int.class).invoke(plate, 0);
                } catch (Throwable notACard) {
                    plate.setBackgroundColor(0);
                }
                plate.setElevation(0f);
            }
        } else if (config.plateColor != null || config.plateRadius != null) {
            applyPlate(findByName(root, StatusBarTweaks.PLATE), config);
        }
        eventOnce(Log.INFO, "STATUS_BAR_APPLIED", "layout", String.valueOf(statusBarRaw));
    }

    private volatile String themeColorsRaw;
    private volatile Map<String, Integer> themeColorsParsed = Map.of();

    /** Parsed colour overrides, re-parsed only when the stored text changes. */
    private Map<String, Integer> themeColors() {
        String raw = overrideSnapshot.get(ThemeColors.COLORS_KEY);
        if (raw == null) return Map.of();
        if (!raw.equals(themeColorsRaw)) {
            Map<String, Integer> parsed = ThemeColors.parse(raw);
            themeColorsParsed = parsed == null ? Map.of() : parsed;
            themeColorsRaw = raw;
        }
        return themeColorsParsed;
    }

    /**
     * Palette colour overrides. The Manager stores the name of Android Auto's palette-role enum
     * for the installed build; here it is checked to really be that enum (an enum with the
     * marker role names) before its colour methods are hooked. A hooked method returns the
     * configured colour for its role when there is one, otherwise the original result.
     */
    private void installThemeColorHooks(ClassLoader loader) {
        String className = overrideSnapshot.get(ThemeColors.CLASS_KEY);
        if (className == null || className.isEmpty()) return;
        try {
            final Class<?> roles = Class.forName(className, false, loader);
            Object[] constants = roles.isEnum() ? roles.getEnumConstants() : null;
            java.util.Set<String> names = new java.util.HashSet<>();
            if (constants != null) for (Object constant : constants) names.add(((Enum<?>) constant).name());
            if (!ThemeColors.looksLikeRoleEnum(names)) {
                event(Log.WARN, "THEME_ROLES_REJECTED", null, "reason", "not_the_role_enum");
                return;
            }
            int installed = 0;
            for (Method method : roles.getDeclaredMethods()) {
                if (method.getReturnType() != int.class || method.isSynthetic()) continue;
                final boolean isStatic = java.lang.reflect.Modifier.isStatic(method.getModifiers());
                // Static helpers take the role as their first argument.
                if (isStatic && (method.getParameterCount() == 0 || method.getParameterTypes()[0] != roles)) continue;
                if (!isStatic && method.getParameterCount() == 0) continue; // ordinal(), hashCode()
                try {
                    hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        Map<String, Integer> colors = themeColors();
                        if (!colors.isEmpty()) {
                            Object role = isStatic ? chain.getArg(0) : chain.getThisObject();
                            if (role instanceof Enum<?> constant) {
                                Integer color = colors.get(constant.name());
                                if (color != null) {
                                    eventOnce(Log.INFO, "THEME_COLOR_SERVED", constant.name(), String.valueOf(color));
                                    return color;
                                }
                            }
                        }
                        return chain.proceed();
                    });
                    installed++;
                } catch (Throwable t) {
                    event(Log.WARN, "THEME_ROLE_HOOK_FAILED", method.getName(), "error", t.getClass().getSimpleName());
                }
            }
            event(Log.INFO, "THEME_ROLE_HOOKS", null, "installed", String.valueOf(installed),
                    "roles", String.valueOf(names.size()));
        } catch (Throwable t) {
            event(Log.WARN, "THEME_ROLES_REJECTED", null, "error", t.getClass().getSimpleName());
        }
    }

    /**
     * Glyph-style icons: wraps the icon a platform loader returns when it is an adaptive icon with
     * a monochrome layer. The original loader always runs first; with the mode off, for icons
     * without such a layer, or on any failure, its result is returned unchanged.
     */
    private void installGlyphIconHooks() {
        if (android.os.Build.VERSION.SDK_INT < 33) return; // monochrome layers exist from Android 13
        int installed = 0;
        String[] owners = {
                "android.content.pm.PackageItemInfo",
                "android.content.pm.LauncherActivityInfo",
                "android.content.pm.ResolveInfo",
                "android.content.res.Resources",
                "android.app.ApplicationPackageManager"
        };
        for (String owner : owners) {
            try {
                for (Method method : Class.forName(owner).getDeclaredMethods()) {
                    if (java.lang.reflect.Modifier.isAbstract(method.getModifiers())
                            || !GlyphIcons.isIconLoader(method.getName(), method.getReturnType().getName())) continue;
                    final String name = owner.substring(owner.lastIndexOf('.') + 1) + "." + method.getName();
                    try {
                        hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                            Object original = chain.proceed();
                            String setting = overrideSnapshot.get(GlyphIcons.KEY);
                            if (setting == null) return original;
                            try {
                                int size = GlyphIcons.parseSize(overrideSnapshot.get(GlyphIcons.SIZE_KEY));
                                Object glyph = toGlyph(original, setting, size);
                                if (glyph == original && original instanceof android.graphics.drawable.Drawable plain
                                        && chain.getThisObject() instanceof android.content.res.Resources resources
                                        && chain.getArg(0) instanceof Integer resourceId) {
                                    glyph = legacyGlyph(plain, resources, resourceId, setting, size);
                                }
                                if (glyph != original) eventOnce(Log.INFO, "GLYPH_ICON_SERVED", name, setting);
                                return glyph;
                            } catch (Throwable t) {
                                eventOnce(Log.WARN, "GLYPH_ICON_FAILED", name, setting, "error", t.getClass().getSimpleName());
                                return original;
                            }
                        });
                        installed++;
                    } catch (Throwable t) {
                        event(Log.WARN, "GLYPH_ICON_HOOK_FAILED", name, "error", t.getClass().getSimpleName());
                    }
                }
            } catch (Throwable t) {
                event(Log.WARN, "GLYPH_ICON_HOOK_FAILED", owner, "error", t.getClass().getSimpleName());
            }
        }
        event(Log.INFO, "GLYPH_ICON_HOOKS", null, "installed", String.valueOf(installed));
    }

    /** Returns a one-colour adaptive icon built from the monochrome layer, or the input unchanged. */
    private static Object toGlyph(Object icon, String setting, int size) {
        if (!(icon instanceof android.graphics.drawable.AdaptiveIconDrawable adaptive)) return icon;
        android.graphics.drawable.Drawable monochrome = adaptive.getMonochrome();
        int[] colors = GlyphIcons.parseColors(setting);
        if (colors == null) return icon;
        android.graphics.drawable.Drawable glyph;
        if (monochrome != null) {
            glyph = monochrome.getConstantState() != null
                    ? monochrome.getConstantState().newDrawable().mutate() : monochrome.mutate();
            glyph.setTint(colors[0]);
        } else {
            // No themed layer: derive a glyph from the icon's own foreground.
            glyph = generatedGlyph(adaptive.getForeground(), colors[0]);
            if (glyph == null) return icon;
        }
        // At the default size the layer is used exactly as before; otherwise it is inset
        // (smaller) or given bounds beyond the layer (larger, clipped by the layer).
        if (size != GlyphIcons.DEFAULT_SIZE) {
            glyph = new android.graphics.drawable.InsetDrawable(glyph, GlyphIcons.insetFraction(GlyphIcons.LAYER_FIGURE, size));
        }
        return new android.graphics.drawable.AdaptiveIconDrawable(
                new android.graphics.drawable.ColorDrawable(colors[1]), glyph);
    }

    /**
     * Icons that are not adaptive: Android Auto's own launcher entries (drawn as a full square
     * with a figure) and other apps' legacy launcher icons. The glyph is inset so that it sits
     * in the visible centre of the adaptive icon it is wrapped in.
     */
    private static Object legacyGlyph(android.graphics.drawable.Drawable icon,
            android.content.res.Resources resources, int resourceId, String setting, int size) {
        if (icon instanceof android.graphics.drawable.AdaptiveIconDrawable) return icon;
        int[] colors = GlyphIcons.parseColors(setting);
        if (colors == null) return icon;
        boolean launcherIcon;
        try {
            launcherIcon = "mipmap".equals(resources.getResourceTypeName(resourceId))
                    || GlyphIcons.isOwnLauncherIcon(resources.getResourceEntryName(resourceId));
        } catch (android.content.res.Resources.NotFoundException unknown) {
            return icon;
        }
        if (!launcherIcon) return icon;
        android.graphics.drawable.Drawable glyph = generatedGlyph(icon, colors[0]);
        if (glyph == null) return icon;
        return new android.graphics.drawable.AdaptiveIconDrawable(
                new android.graphics.drawable.ColorDrawable(colors[1]),
                new android.graphics.drawable.InsetDrawable(glyph, GlyphIcons.insetFraction(GlyphIcons.LEGACY_FIGURE, size)));
    }

    private static final int GENERATED_GLYPH_SIZE = 216;

    private static android.graphics.drawable.Drawable generatedGlyph(
            android.graphics.drawable.Drawable foreground, int color) {
        if (foreground == null) return null;
        android.graphics.drawable.Drawable copy = foreground.getConstantState() != null
                ? foreground.getConstantState().newDrawable().mutate() : foreground;
        int size = GENERATED_GLYPH_SIZE;
        android.graphics.Bitmap rendered = android.graphics.Bitmap.createBitmap(
                size, size, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Rect previous = copy.copyBounds();
        copy.setBounds(0, 0, size, size);
        copy.draw(new android.graphics.Canvas(rendered));
        copy.setBounds(previous);
        int[] pixels = new int[size * size];
        rendered.getPixels(pixels, 0, size, 0, 0, size, size);
        int[] mask = GlyphIcons.glyphAlpha(pixels, size);
        if (mask == null) return null;
        int rgb = color & 0x00FFFFFF;
        int opacity = color >>> 24; // a translucent glyph colour scales the whole mask
        for (int i = 0; i < mask.length; i++) pixels[i] = ((mask[i] * opacity / 255) << 24) | rgb;
        rendered.setPixels(pixels, 0, size, 0, 0, size, size);
        return new android.graphics.drawable.BitmapDrawable(
                android.content.res.Resources.getSystem(), rendered);
    }

    private volatile String carWallpaperVersion;
    private volatile android.graphics.Bitmap carWallpaperBitmap;

    /**
     * Answers platform wallpaper reads made inside Android Auto with the configured car
     * wallpaper. Only public WallpaperManager read methods are hooked; with no image configured,
     * or on any failure, the original call proceeds untouched.
     */
    private void installCarWallpaperHooks() {
        int installed = 0;
        try {
            for (Method method : android.app.WallpaperManager.class.getDeclaredMethods()) {
                final CarWallpaper.Kind kind = CarWallpaper.kindOf(method.getName(), method.getReturnType().getName());
                if (kind == CarWallpaper.Kind.NONE) continue;
                final String name = method.getName();
                try {
                    hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        String version = overrideSnapshot.get(CarWallpaper.VERSION_KEY);
                        if (!CarWallpaper.isEnabled(version)) return chain.proceed();
                        try {
                            if (kind == CarWallpaper.Kind.FILE) {
                                eventOnce(Log.INFO, "CAR_WALLPAPER_SERVED", name, version, "as", "file");
                                return openRemoteFile(CarWallpaper.FILE_NAME);
                            }
                            android.graphics.Bitmap bitmap = carWallpaper(version);
                            if (bitmap == null) return chain.proceed();
                            eventOnce(Log.INFO, "CAR_WALLPAPER_SERVED", name, version, "as", kind.name());
                            return kind == CarWallpaper.Kind.BITMAP ? bitmap
                                    : new android.graphics.drawable.BitmapDrawable(
                                            android.content.res.Resources.getSystem(), bitmap);
                        } catch (Throwable t) {
                            eventOnce(Log.WARN, "CAR_WALLPAPER_FAILED", name, version, "error", t.getClass().getSimpleName());
                            return chain.proceed();
                        }
                    });
                    installed++;
                } catch (Throwable t) {
                    event(Log.WARN, "CAR_WALLPAPER_HOOK_FAILED", name, "error", t.getClass().getSimpleName());
                }
            }
        } catch (Throwable t) {
            event(Log.WARN, "CAR_WALLPAPER_HOOK_FAILED", null, "error", t.getClass().getSimpleName());
        }
        event(Log.INFO, "CAR_WALLPAPER_HOOKS", null, "installed", String.valueOf(installed));
    }

    /** Decodes the configured image once per version. */
    private android.graphics.Bitmap carWallpaper(String version) throws Exception {
        android.graphics.Bitmap cached = carWallpaperBitmap;
        if (cached != null && version.equals(carWallpaperVersion)) return cached;
        synchronized (this) {
            if (carWallpaperBitmap != null && version.equals(carWallpaperVersion)) return carWallpaperBitmap;
            try (ParcelFileDescriptor descriptor = openRemoteFile(CarWallpaper.FILE_NAME)) {
                android.graphics.Bitmap decoded =
                        android.graphics.BitmapFactory.decodeFileDescriptor(descriptor.getFileDescriptor());
                carWallpaperBitmap = decoded;
                carWallpaperVersion = version;
                return decoded;
            }
        }
    }

    /**
     * Manager-requested restart: every Android Auto process hosting the module ends itself, so
     * the next start re-reads experiments. Only a fresh request delivered as a live preference
     * change is honoured, once per process.
     */
    private void handleRestartRequest(SharedPreferences preferences) {
        try {
            String request = preferences.getString(ControlRequests.RESTART_KEY, null);
            if (!ControlRequests.isFresh(request, System.currentTimeMillis())
                    || request.equals(handledRestartRequest)) return;
            handledRestartRequest = request;
            event(Log.INFO, "CONTROL_RESTART", null, "process", String.valueOf(moduleProcessName));
            // Tell the Manager the request was heard, so it does not fall back to other means.
            try {
                android.content.Context context = currentApplication();
                if (context != null) context.getContentResolver().call(REPORTS, "restart_ack", request, null);
            } catch (Throwable ignored) { }
            new Handler(Looper.getMainLooper()).postDelayed(
                    () -> android.os.Process.killProcess(android.os.Process.myPid()), 250L);
        } catch (Throwable t) {
            event(Log.WARN, "CONTROL_RESTART_FAILED", null, "error", t.getClass().getSimpleName());
        }
    }

    /**
     * Manager-requested start of Android Auto's own developer head unit server. Fails open.
     *
     * When the same request also asks for a restart, the process that receives it live is about
     * to end, so it leaves the start to the restarted process. A freshly started process waits
     * for Android Auto to finish coming up and tries more than once, because an early start is
     * lost; starting an already running service is harmless.
     */
    private void handleHeadUnitRequest(SharedPreferences preferences, boolean atProcessStart) {
        try {
            String request = preferences.getString(ControlRequests.HEAD_UNIT_KEY, null);
            if (!ControlRequests.isFresh(request, System.currentTimeMillis())
                    || request.equals(handledHeadUnitRequest)) return;
            boolean restartPending = request.equals(preferences.getString(ControlRequests.RESTART_KEY, null));
            if (!atProcessStart && restartPending) return;
            handledHeadUnitRequest = request;
            Handler handler = new Handler(Looper.getMainLooper());
            if (atProcessStart) {
                for (long delay : ControlRequests.HEAD_UNIT_START_DELAYS_MS)
                    handler.postDelayed(this::startHeadUnitServer, delay);
            } else {
                handler.post(this::startHeadUnitServer);
            }
        } catch (Throwable t) {
            event(Log.WARN, "CONTROL_HEAD_UNIT_FAILED", null, "error", t.getClass().getSimpleName());
        }
    }

    private void startHeadUnitServer() {
        try {
            Object current = Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication").invoke(null);
            if (!(current instanceof android.content.Context context)) {
                event(Log.WARN, "CONTROL_HEAD_UNIT_FAILED", null, "reason", "no_application");
                return;
            }
            android.content.pm.PackageInfo info = context.getPackageManager().getPackageInfo(AA_PACKAGE,
                    android.content.pm.PackageManager.GET_SERVICES
                            | android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS);
            String[] names = new String[info.services == null ? 0 : info.services.length];
            for (int i = 0; i < names.length; i++) names[i] = info.services[i].name;
            String service = ControlRequests.pickHeadUnitService(names);
            if (service == null) {
                event(Log.WARN, "CONTROL_HEAD_UNIT_FAILED", null, "reason", "service_not_found");
                return;
            }
            android.content.Intent intent = new android.content.Intent().setClassName(AA_PACKAGE, service);
            try {
                context.startForegroundService(intent);
            } catch (Throwable foregroundRejected) {
                context.startService(intent);
            }
            event(Log.INFO, "CONTROL_HEAD_UNIT_STARTED", null, "service", service,
                    "process", String.valueOf(moduleProcessName));
        } catch (Throwable t) {
            event(Log.WARN, "CONTROL_HEAD_UNIT_FAILED", null, "error", t.getClass().getSimpleName());
        }
    }

    private void refreshSnapshot(SharedPreferences preferences) {
        Map<String, String> next = new HashMap<>();
        for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
            if (entry.getValue() instanceof String value) next.put(entry.getKey(), value);
        }
        overrideSnapshot.replace(next);
    }

    private void install(ClassLoader loader, JSONObject mapping) {
        String key = mapping.optString("key", "<unknown>");
        try {
            String className = mapping.getString("className");
            String methodName = mapping.getString("methodName");
            String descriptor = mapping.getString("descriptor");
            String preferenceKey = mapping.getString("preferenceKey");
            Class<?> cls = Class.forName(className, false, loader);
            Method method = cls.getDeclaredMethod(methodName);
            if (method.getParameterCount() != 0 || !descriptor.equals(descriptor(method))) {
                event(Log.WARN, "HOOK_SUSPENDED", key, "reason", "changed_signature"); return;
            }
            hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                String encoded = overrideSnapshot.get(preferenceKey);
                if (encoded == null) {
                    Object original = chain.proceed();
                    logReturnOnce(key, "ORIGINAL_RETURNED", "<default>", original);
                    return original;
                }
                try {
                    Object effective = RuntimeOverrideCodec.decode(encoded, method.getReturnType());
                    logReturnOnce(key, "OVERRIDE_RETURNED", encoded, effective);
                    return effective;
                }
                catch (Throwable invalid) {
                    event(Log.WARN, "OVERRIDE_REJECTED", key, "reason", invalid.getClass().getSimpleName());
                    log(Log.WARN, TAG, "Invalid override for " + key + "; original executed", invalid);
                    return chain.proceed();
                }
            });
            event(Log.INFO, "HOOK_INSTALLED", key, "target", className + "." + methodName + descriptor);
        } catch (ClassNotFoundException | NoSuchMethodException | LinkageError error) {
            event(Log.WARN, "HOOK_SUSPENDED", key, "reason", error.getClass().getSimpleName());
            log(Log.WARN, TAG, "Suspended incompatible mapping " + key, error);
        } catch (Throwable error) {
            event(Log.ERROR, "HOOK_FAILED", key, "reason", error.getClass().getSimpleName());
            log(Log.ERROR, TAG, "Failed to install mapping " + key, error);
        }
    }

    /**
     * Explicit one-key experimental BOOLEAN override. The remote snapshot is checked on
     * each invocation so restoring/pausing fails open even in a running Android Auto process.
     * It never accepts a raw protobuf or unverified getter type.
     */
    private void installExperimental(ClassLoader loader, JSONObject mapping, String buildFingerprint) {
        String key = mapping.optString("key", "<unknown>");
        if (!ExperimentalBooleanPolicy.KEY.equals(key)) {
            event(Log.WARN, "EXPERIMENT_REJECTED", key, "reason", "not_allowlisted");
            return;
        }
        try {
            String className = mapping.getString("className");
            String methodName = mapping.getString("methodName");
            String descriptor = mapping.getString("descriptor");
            if (!"()Z".equals(descriptor)) {
                event(Log.WARN, "EXPERIMENT_REJECTED", key, "reason", "invalid_abi"); return;
            }
            Class<?> cls = Class.forName(className, false, loader);
            Method method = cls.getDeclaredMethod(methodName);
            if (method.getParameterCount() != 0 || !"()Z".equals(descriptor(method))) {
                event(Log.WARN, "EXPERIMENT_REJECTED", key, "reason", "changed_signature"); return;
            }
            // Installation proves method resolution, not invocation. Report snapshot
            // availability independently before the first getter call.
            hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                eventOnce(Log.INFO, "EXPERIMENT_GETTER_INVOKED", key, "first");
                ExperimentalBooleanDecision.Result decision = inspectExperimentalSnapshot(
                        key, buildFingerprint, className, methodName, descriptor);
                if (!decision.shouldOverride()) {
                    eventOnce(Log.WARN, "EXPERIMENT_FALLBACK", key,
                            decision.status.name(), "reason", decision.status.name());
                    return chain.proceed();
                }
                String value = String.valueOf(decision.value);
                eventOnce(Log.INFO, "EXPERIMENT_OVERRIDE_RETURNED", key, value,
                        "source", "EXPERIMENTAL", "value", value);
                return decision.value;
            });
            inspectExperimentalSnapshot(key, buildFingerprint, className, methodName, descriptor);
            event(Log.INFO, "EXPERIMENT_HOOK_INSTALLED", key, "target", className + "." + methodName + descriptor);
        } catch (ClassNotFoundException | NoSuchMethodException | LinkageError failure) {
            event(Log.WARN, "EXPERIMENT_REJECTED", key, "reason", failure.getClass().getSimpleName());
        } catch (Throwable failure) {
            event(Log.ERROR, "EXPERIMENT_HOOK_FAILED", key, "reason", failure.getClass().getSimpleName());
            log(Log.ERROR, TAG, "Experimental getter hook failed", failure);
        }
    }

    private ExperimentalBooleanDecision.Result inspectExperimentalSnapshot(
            String key, String build, String cls, String method, String descriptor) {
        ExperimentalBooleanDecision.Result decision = ExperimentalBooleanDecision.evaluate(
                overrideSnapshot.get(ExperimentalBooleanPolicy.RECORD_KEY), key, build,
                cls, method, descriptor);
        // No snapshot JSON, user data, or app state is ever written to the logs.
        eventOnce(Log.INFO, "EXPERIMENT_SNAPSHOT_LOADED", key,
                decision.status.name(), "status", decision.status.name());
        return decision;
    }

    private void eventOnce(int priority, String eventName, String key, String state, String... fields) {
        if (experimentalEvents.first(eventName, key, state)) {
            event(priority, eventName, key, fields);
        }
    }

    /** No arbitrary method or type: every candidate must be present in the validated
     * build-scoped profile, use a supported ABI, and carry an exact matching remote
     * record. Every failed decision calls through to the original implementation. */
    private void installAdvanced(ClassLoader loader, JSONObject mapping,
            String buildFingerprint, String baseSha256) {
        String key = mapping.optString("key", "<unknown>");
        try {
            String prefKey = mapping.getString("preferenceKey");
            String expectedPrefKey = io.github.highwindbr.aaxp.core.AdvancedOverridePolicy.INSTANCE.preferenceKey(key);
            if (!expectedPrefKey.equals(prefKey) || !key.contains("__") ||
                    io.github.highwindbr.aaxp.discovery.SafetyPolicy.INSTANCE.blockedReason(key) != null) {
                event(Log.WARN, "ADVANCED_REJECTED", key, "reason", "policy_denied");
                return;
            }
            String cls = mapping.getString("className");
            String methodName = mapping.getString("methodName");
            String abi = mapping.getString("descriptor");
            String type = mapping.getString("type");
            if (LongHandoffRuntime.KEY.equals(key) &&
                    (!LongHandoffRuntime.isTarget(key, cls, methodName, abi, type) ||
                     !LongHandoffRuntime.isExpectedBase(baseSha256))) {
                event(Log.WARN, "ADVANCED_REJECTED", key, "reason", "handoff_target_or_build_mismatch");
                return;
            }
            if (!abi.equals(AdvancedRuntimeDecision.expectedDescriptor(type))) {
                event(Log.WARN, "ADVANCED_REJECTED", key, "reason", "unsupported_abi"); return;
            }
            Class<?> owner = Class.forName(cls, false, loader);
            Method getter = owner.getDeclaredMethod(methodName);
            if (getter.getParameterCount() != 0 || !abi.equals(descriptor(getter))) {
                event(Log.WARN, "ADVANCED_REJECTED", key, "reason", "changed_signature"); return;
            }
            hook(getter).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                eventOnce(Log.INFO, "ADVANCED_GETTER_INVOKED", key, "first");
                String rawRecord = overrideSnapshot.get(prefKey);
                if (LongHandoffRuntime.KEY.equals(key)) {
                    // The normal LONG:3 preference remains stored throughout
                    // the handoff. A stale Advanced hook must still return 3
                    // after Pause/Restore, even before a process restart.
                    String normal = overrideSnapshot.get(
                            io.github.highwindbr.aaxp.core.DynamicOverrideCodec.INSTANCE.preferenceKey(key));
                    String name = overrideSnapshot.get(
                            io.github.highwindbr.aaxp.core.DynamicOverrideCodec.INSTANCE.namePreferenceKey(key));
                    if (!LongHandoffRuntime.baselineIntact(normal, name) ||
                            !LongHandoffRuntime.activeSnapshot(rawRecord,
                                    buildFingerprint, cls, methodName, abi, type)) {
                        if (LongHandoffRuntime.baselineIntact(normal, name)) {
                            eventOnce(Log.INFO, "LONG_HANDOFF_BASELINE_RETURNED", key, "normal:3",
                                    "type", "LONG", "value", "3");
                            return LongHandoffRuntime.BASELINE_VALUE;
                        }
                        eventOnce(Log.WARN, "LONG_HANDOFF_BASELINE_MISSING", key, "unsafe",
                                "reason", "original_normal_value_missing");
                        return chain.proceed();
                    }
                }
                AdvancedRuntimeDecision.Result value = AdvancedRuntimeDecision.evaluate(
                        rawRecord, key, buildFingerprint, cls, methodName, abi, type);
                if (!value.shouldOverride()) {
                    eventOnce(Log.WARN, "ADVANCED_FALLBACK", key, value.status.name(),
                            "reason", value.status.name());
                    return chain.proceed();
                }
                // One return event per key/value/process. We never log STRING contents.
                String reportedValue = "STRING".equals(type) ? "<redacted>" : String.valueOf(value.value);
                eventOnce(Log.INFO, "ADVANCED_OVERRIDE_RETURNED", key, type + ":" + reportedValue,
                        "type", type, "value", reportedValue);
                return value.value;
            });
            event(Log.INFO, "ADVANCED_HOOK_INSTALLED", key, "target", cls + "." + methodName + abi);
        } catch (ClassNotFoundException | NoSuchMethodException | LinkageError missing) {
            event(Log.WARN, "ADVANCED_REJECTED", key, "reason", missing.getClass().getSimpleName());
        } catch (Throwable failure) {
            event(Log.ERROR, "ADVANCED_HOOK_FAILED", key, "reason", failure.getClass().getSimpleName());
            log(Log.ERROR, TAG, "Advanced hook installation failed for " + key, failure);
        }
    }

    /**
     * Optional diagnostic for AA 18.0, proven by the exact base APK hash.
     * Hooks are observation-only: the original constructor and method always
     * execute via chain.proceed() without modifying arguments or return values.
     * Only protocol versions and the selection state are logged (no vehicle IDs).
     */
    private void installVideoTimingTrace(ClassLoader loader, String actualBaseSha256) {
        if (!VideoTimingProtocolTrace.isSupported(actualBaseSha256)) {
            event(Log.WARN, "VTC_TRACE_NOT_INSTALLED", null, "reason", "base_sha_mismatch");
            return;
        }
        try {
            Class<?> owner = Class.forName(VideoTimingProtocolTrace.CLASS, false, loader);
            Class<?> carInfo = Class.forName(VideoTimingProtocolTrace.CAR_INFO_CLASS, false, loader);
            Constructor<?> ctor = owner.getDeclaredConstructor(carInfo);
            Method selector = owner.getDeclaredMethod(VideoTimingProtocolTrace.SELECTOR_METHOD);
            if (!VideoTimingProtocolTrace.SELECTOR_DESCRIPTOR.equals(descriptor(selector))) {
                event(Log.WARN, "VTC_TRACE_NOT_INSTALLED", null, "reason", "selector_signature_mismatch");
                return;
            }
            Field majorField = carInfo.getDeclaredField("e");
            Field minorField = carInfo.getDeclaredField("f");
            Field modeField = owner.getDeclaredField("a");
            Field normalField = owner.getDeclaredField("b");
            majorField.setAccessible(true);
            minorField.setAccessible(true);
            modeField.setAccessible(true);
            normalField.setAccessible(true);

            hook(ctor).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                Object result = chain.proceed();
                if (!VideoTimingProtocolTrace.enabled(overrideSnapshot.get(VideoTimingProtocolTrace.KEY))) return result;
                try {
                    Object info = chain.getArg(0);
                    int major = majorField.getInt(info);
                    int minor = minorField.getInt(info);
                    eventOnce(Log.INFO, "VTC_HU_PROTOCOL_OBSERVED", "VideoTimingController",
                            major + "." + minor, "major", String.valueOf(major),
                            "minor", String.valueOf(minor),
                            "expectedBranch", VideoTimingProtocolTrace.protocolBranch(major));
                } catch (Throwable observationError) {
                    eventOnce(Log.WARN, "VTC_TRACE_READ_FAILED", "CarInfo",
                            observationError.getClass().getSimpleName(),
                            "reason", observationError.getClass().getSimpleName());
                }
                return result;
            });

            hook(selector).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                if (VideoTimingProtocolTrace.enabled(overrideSnapshot.get(VideoTimingProtocolTrace.KEY))) {
                    eventOnce(Log.INFO, "VTC_SELECTOR_INVOKED", "VideoTimingController", "first");
                    try {
                        Object instance = chain.getThisObject();
                        int mode = modeField.getInt(instance);
                        boolean normal = normalField.getBoolean(instance);
                        String branch = VideoTimingProtocolTrace.selectorBranch(mode, normal);
                        eventOnce(Log.INFO, "VTC_BRANCH_SELECTED", "VideoTimingController",
                                branch, "branch", branch, "mode", String.valueOf(mode),
                                "normalFlag", String.valueOf(normal));
                    } catch (Throwable observationError) {
                        eventOnce(Log.WARN, "VTC_TRACE_READ_FAILED", "Liyo.d",
                                observationError.getClass().getSimpleName(),
                                "reason", observationError.getClass().getSimpleName());
                    }
                }
                return chain.proceed();
            });
            event(Log.INFO, "VTC_TRACE_INSTALLED", null,
                    "target", "iyo.<init>(CarInfo) + iyo.d()Lxxe;",
                    "mode", "READ_ONLY", "build", actualBaseSha256.substring(0, 16));
        } catch (ClassNotFoundException | NoSuchMethodException | NoSuchFieldException | LinkageError incompatible) {
            event(Log.WARN, "VTC_TRACE_NOT_INSTALLED", null, "reason", incompatible.getClass().getSimpleName());
        } catch (Throwable failure) {
            event(Log.ERROR, "VTC_TRACE_HOOK_FAILED", null, "reason", failure.getClass().getSimpleName());
            log(Log.ERROR, TAG, "VideoTiming protocol observation hook failed", failure);
        }
    }


    /**
     * Beta5 RC1: selected media app, manual A/B and separately opted-in auto using
     * ActivityTracker and the direct launch observer dynamically resolved
     * from the installed APK. Missing auto roles keep manual routes usable.
     */
    private void installMediaStartupLab(ClassLoader loader, JSONObject raw,
                                         String baseSha, String fingerprint) {
        try {
            MediaStartupLabProfile lab = MediaStartupLabProfile.parse(raw, baseSha, fingerprint);
            Method ready = resolveLabMethod(loader, lab.role("projection.ready"));
            Method end = resolveLabMethod(loader, lab.role("projection.end"));
            Method teardown = resolveLabMethod(loader, lab.role("projection.teardown"));
            Method media = resolveLabMethod(loader, lab.role("media.selectedApp"));
            Method factory = resolveLabMethod(loader, lab.role("launch.controllerFactory"));
            Method auxiliary = lab.role("launch.auxiliary") == null ? null :
                    resolveLabMethod(loader, lab.role("launch.auxiliary"));
            Method direct = lab.role("launch.startCarActivity") == null ? null :
                    resolveLabMethod(loader, lab.role("launch.startCarActivity"));
            MediaStartupLabProfile.Target navigationRole =
                    direct == null ? lab.role("launch.navigationObserver") :
                    lab.role("launch.startCarActivity");
            Method navigation = navigationRole == null ? null :
                    resolveLabMethod(loader, navigationRole);
            MediaStartupLabProfile.Target ctorTarget = lab.role("launch.optionsNoArgConstructor");
            Class<?> optionsType = ctorTarget == null ? null :
                    Class.forName(ctorTarget.javaOwner(), false, loader);
            Constructor<?> options = optionsType == null ? null :
                    optionsType.getDeclaredConstructor();
            Class<?> controller = factory.getDeclaringClass();
            if (!Modifier.isStatic(factory.getModifiers()) ||
                    !factory.getReturnType().equals(controller) ||
                    (auxiliary == null && direct == null) ||
                    (auxiliary != null && (Modifier.isStatic(auxiliary.getModifiers()) ||
                        auxiliary.getDeclaringClass() != controller)) ||
                    (direct != null && (Modifier.isStatic(direct.getModifiers()) ||
                        direct.getDeclaringClass() != controller || optionsType == null ||
                        !direct.getParameterTypes()[1].equals(optionsType))) ||
                    (navigation != null && (Modifier.isStatic(navigation.getModifiers()) ||
                        navigation.getDeclaringClass() != controller || optionsType == null ||
                        !navigation.getParameterTypes()[1].equals(optionsType))) ||
                    (options != null && options.getParameterCount() != 0)) {
                throw new IllegalArgumentException("Media route reflection mismatch");
            }
            // Never reflect an absent route: one verified opening method is sufficient.
            factory.setAccessible(true);
            if (auxiliary != null) auxiliary.setAccessible(true);
            if (direct != null) direct.setAccessible(true);
            if (navigation != null) navigation.setAccessible(true);
            if (options != null) options.setAccessible(true);
            MediaStartupLabGate gate = new MediaStartupLabGate(
                    overrideSnapshot.get(MediaStartupLabGate.REQUEST_KEY));
            Handler handler = new Handler(Looper.getMainLooper());
            mediaLaunchHandler = handler;
            MediaStartupAutoGate automatic = new MediaStartupAutoGate();
            mediaLabAutoGate = automatic;
            mediaLabAutoAvailable = false;
            mediaLabFactory = factory; mediaLabHelper = auxiliary;
            mediaLabDirect = direct; mediaLabOptions = options;
            // Always hook the essential observer set together; if one fails,
            // leave the AA originals callable and suppress any PF6 dispatch.
            Method[] observed = {ready, end, teardown, media};
            String[] stages = {"READY", "END", "TEARDOWN", "MEDIA"};
            int installed = 0;
            for (int i = 0; i < observed.length; i++) {
                final String stage = stages[i];
                hook(observed[i]).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            if ("END".equals(stage) || "TEARDOWN".equals(stage)) {
                                gate.ended(); automatic.onEnded();
                                mediaLabComponent = null;
                            }
                            Object result = chain.proceed();
                            if ("READY".equals(stage)) {
                                long now = SystemClock.elapsedRealtime();
                                gate.ready(now);
                                boolean enabled = mediaLabAutoAvailable &&
                                    MediaStartupAutoGate.enabled(overrideSnapshot.get(
                                        MediaStartupDynamicAuto.KEY));
                                if (automatic.onReady(enabled, now)) {
                                    event(Log.INFO, "MEDIA_PF6_AUTO_SESSION", null,
                                        "enabled", String.valueOf(enabled));
                                }
                                startMediaPolling();
                            } else if ("MEDIA".equals(stage)) {
                                Object arg = chain.getArg(0);
                                ComponentName target = arg instanceof ComponentName ?
                                        (ComponentName) arg : null;
                                mediaLabComponent = target;
                                gate.media(target == null ? null : target.getPackageName(),
                                        target == null ? null : target.getClassName());
                                boolean eligible = target != null &&
                                        gate.selectedMediaMatches(target.getPackageName(),
                                                target.getClassName());
                                automatic.onMediaChanged(eligible);
                                event(Log.INFO, "MEDIA_PF6_MEDIA_OBSERVED", null,
                                        "selected", String.valueOf(eligible),
                                        "package", eligible ? target.getPackageName() : "none");
                            }
                            return result;
                        });
                installed++;
                event(Log.INFO, "MEDIA_PF6_HOOK_INSTALLED", null, "stage", stage);
            }
            if (installed == observed.length) {
                mediaLabGate = gate;
                // Manual is independent. No auto dispatch occurs unless the
                // unique activity method AND direct navigation observer hook.
                MediaStartupLabProfile.Target trackerRole = lab.role("activity.firstProjected");
                if (MediaStartupLabProfile.MODE.equals(lab.mode) && trackerRole != null) {
                    try {
                        Method activity = resolveLabMethod(loader, trackerRole);
                        if (!Runnable.class.isAssignableFrom(activity.getDeclaringClass()) ||
                                Modifier.isStatic(activity.getModifiers()))
                            throw new IllegalArgumentException("Invalid Runnable implementation");
                        hook(activity).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                                .intercept(chain -> {
                                    Object original = chain.proceed();
                                    int session = automatic.onFirstActivity(SystemClock.elapsedRealtime());
                                    if (session >= 0) scheduleDynamicSelectedMediaAuto(session);
                                    return original;
                                });
                        if (navigation == null)
                            throw new IllegalArgumentException("Navigation observer not proven");
                        hook(navigation).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                                .intercept(chain -> {
                                    Object original = chain.proceed();
                                    automatic.onNavigation();
                                    return original;
                                });
                        mediaLabAutoAvailable = true;
                        event(Log.INFO, "MEDIA_PF6_AUTO_HOOKS_READY", null,
                                "hooks", "2", "mode", "OPT_IN");
                    } catch (Throwable problem) {
                        mediaLabAutoAvailable = false;
                        event(Log.WARN, "MEDIA_PF6_AUTO_UNAVAILABLE", null,
                                "reason", problem.getClass().getSimpleName());
                    }
                } else {
                    event(Log.INFO, "MEDIA_PF6_AUTO_UNAVAILABLE", null,
                            "reason", "activity_role_or_mode_missing");
                }
                event(Log.INFO, "MEDIA_PF6_LAB_ARMED", null,
                        "hooks", String.valueOf(installed), "routes",
                        auxiliary != null && direct != null ? "A_B" :
                            auxiliary != null ? "A" : "B",
                        "mode", mediaLabAutoAvailable ? "MANUAL_PLUS_OPT_IN_AUTO" : "MANUAL_ONLY");
            }
        } catch (Throwable failure) {
            mediaLabGate = null;
            mediaLabAutoGate = null;
            mediaLabAutoAvailable = false;
            event(Log.WARN, "MEDIA_PF6_LAB_DISABLED", null,
                    "reason", failure.getClass().getSimpleName());
        }
    }

    private Method resolveLabMethod(ClassLoader loader, MediaStartupLabProfile.Target target)
            throws ReflectiveOperationException {
        Class<?> owner = Class.forName(target.javaOwner(), false, loader);
        Method match = null;
        for (Method method : owner.getDeclaredMethods()) {
            if (method.getName().equals(target.name) &&
                    descriptor(method).equals(target.descriptor)) {
                if (match != null) throw new IllegalArgumentException("Ambiguous lab ABI");
                match = method;
            }
        }
        if (match == null) throw new NoSuchMethodException(target.reference());
        match.setAccessible(true);
        return match;
    }

    private void checkMediaLabRequest() {
        MediaStartupLabGate gate = mediaLabGate;
        if (gate == null || !gate.isReady()) return;
        if (Looper.myLooper() != Looper.getMainLooper()) {
            requestMediaLaunchCheck();
            return;
        }
        try {
            SharedPreferences prefs = remotePreferences;
            if (prefs == null) return;
            String preferred = MediaStartupRoutePolicy.select(
                    prefs.getString(MediaStartupLabGate.ROUTE_KEY, "A"),
                    mediaLabHelper != null, mediaLabDirect != null);
            if (preferred == null) {
                event(Log.WARN, "MEDIA_PF6_REQUEST_REJECTED", null,
                        "reason", "invalid_route_preference");
                return;
            }
            MediaStartupLabGate.Route route = MediaStartupLabGate.Route.valueOf(preferred);
            MediaStartupLabGate.Result result = gate.consider(
                    prefs.getString(MediaStartupLabGate.REQUEST_KEY, null),
                    SystemClock.elapsedRealtime(), route);
            if (result == MediaStartupLabGate.Result.REPLAY) return;
            if (result != MediaStartupLabGate.Result.ACCEPTED) {
                event(Log.WARN, "MEDIA_PF6_REQUEST_REJECTED", null, "reason", result.name());
                return;
            }
            long sessionId = gate.session();
            ComponentName component = mediaLabComponent;
            if (component == null || !gate.isCurrent(sessionId) ||
                    !gate.selectedMediaMatches(component.getPackageName(), component.getClassName())) {
                event(Log.WARN, "MEDIA_PF6_REQUEST_REJECTED", null,
                        "reason", "media_or_session_changed");
                return;
            }
            if (mediaLabAutoGate != null) mediaLabAutoGate.onManualAttempt();
            event(Log.INFO, "MEDIA_PF6_REQUEST_ACCEPTED", null, "route", route.name());
            try {
                dispatchMediaLabSelectedMedia(route, sessionId);
                event(Log.INFO, "MEDIA_PF6_REQUEST_DISPATCHED", null,
                        "route", route.name(), "result", "RETURNED", "visible", "UNVERIFIED");
            } catch (Throwable problem) {
                Throwable reason = problem instanceof InvocationTargetException &&
                        problem.getCause() != null ? problem.getCause() : problem;
                event(Log.ERROR, "MEDIA_PF6_REQUEST_FAILED", null,
                        "route", route.name(), "reason", reason.getClass().getSimpleName());
            }
        } catch (Throwable error) {
            event(Log.WARN, "MEDIA_PF6_REQUEST_FAILED", null,
                    "reason", error.getClass().getSimpleName());
        }
    }

    /** Identical Intent for manual and automatic tests; no audio autoplay. */
    private void dispatchMediaLabSelectedMedia(MediaStartupLabGate.Route route, long sessionId)
            throws Exception {
        MediaStartupLabGate gate = mediaLabGate;
        ComponentName component = mediaLabComponent;
        if (gate == null || component == null || !gate.isCurrent(sessionId) ||
                !gate.selectedMediaMatches(component.getPackageName(), component.getClassName()))
            throw new IllegalStateException("Selected media or projection changed before dispatch");
        Object controller = mediaLabFactory.invoke(null);
        if (controller == null || !gate.isCurrent(sessionId) ||
                !gate.selectedMediaMatches(component.getPackageName(), component.getClassName()))
            throw new IllegalStateException("Controller unavailable or selected media changed");
        Intent intent = new Intent(MediaStartupLaunchGate.MEDIA_ACTION);
        intent.setComponent(component);
        if (route == MediaStartupLabGate.Route.A) {
            if (mediaLabHelper == null)
                throw new IllegalStateException("Auxiliary route is not verified");
            mediaLabHelper.invoke(controller, intent);
        } else {
            if (mediaLabDirect == null || mediaLabOptions == null)
                throw new IllegalStateException("Direct route is not verified");
            Object options = mediaLabOptions.newInstance();
            mediaLabDirect.invoke(controller, intent, options);
        }
    }

    /** Schedule once after the first projected activity (PF4 settle policy). */
    private void scheduleDynamicSelectedMediaAuto(int scheduledSession) {
        if (!mediaLabAutoAvailable || mediaLabGate == null ||
                mediaLaunchHandler == null) return;
        mediaLaunchHandler.postDelayed(() -> attemptDynamicSelectedMediaAuto(scheduledSession),
                MediaStartupAutoGate.SETTLE_MILLIS);
        event(Log.INFO, "MEDIA_PF6_AUTO_SCHEDULED", null,
                "delayMs", String.valueOf(MediaStartupAutoGate.SETTLE_MILLIS));
    }

    private void attemptDynamicSelectedMediaAuto(int scheduledSession) {
        MediaStartupAutoGate automatic = mediaLabAutoGate;
        MediaStartupLabGate gate = mediaLabGate;
        if (automatic == null || gate == null || !mediaLabAutoAvailable ||
                Looper.myLooper() != Looper.getMainLooper()) return;
        boolean liveEnabled = MediaStartupAutoGate.enabled(overrideSnapshot.get(
                MediaStartupDynamicAuto.KEY));
        MediaStartupAutoGate.Decision decision = automatic.consume(
                scheduledSession, SystemClock.elapsedRealtime(), liveEnabled);
        if (decision != MediaStartupAutoGate.Decision.DISPATCH) {
            event(Log.INFO, "MEDIA_PF6_AUTO_SKIPPED", null, "reason",
                    decision == MediaStartupAutoGate.Decision.MEDIA_NOT_SPOTIFY ?
                            "NO_SELECTED_MEDIA" : decision.name());
            return;
        }
        if (!gate.reserveForAutomatic()) {
            event(Log.INFO, "MEDIA_PF6_AUTO_SKIPPED", null,
                    "reason", "manual_attempt_or_media_changed");
            return;
        }
        String preferred = MediaStartupRoutePolicy.select(
                overrideSnapshot.get(MediaStartupLabGate.ROUTE_KEY),
                mediaLabHelper != null, mediaLabDirect != null);
        if (preferred == null) {
            event(Log.WARN, "MEDIA_PF6_AUTO_SKIPPED", null,
                    "reason", "invalid_preferred_route");
            return;
        }
        MediaStartupLabGate.Route route = MediaStartupLabGate.Route.valueOf(preferred);
        event(Log.INFO, "MEDIA_PF6_AUTO_ATTEMPT", null, "route", route.name());
        try {
            dispatchMediaLabSelectedMedia(route, gate.session());
            event(Log.INFO, "MEDIA_PF6_AUTO_DISPATCHED", null,
                    "route", route.name(), "result", "RETURNED", "visible", "UNVERIFIED");
        } catch (Throwable error) {
            Throwable cause = error instanceof InvocationTargetException &&
                    error.getCause() != null ? error.getCause() : error;
            event(Log.ERROR, "MEDIA_PF6_AUTO_FAILED", null,
                    "route", route.name(), "reason", cause.getClass().getSimpleName());
        }
    }

    /**
     * Beta5 preflight2: inspect precisely identified callbacks observed in
     * real projection logs. No arguments or return values are accessed or
     * changed. The original method is always called once via chain.proceed().
     */
    private void installMediaStartupTrace2(ClassLoader loader, String baseSha256) {
        if (!MediaStartupTrace.isSupported(baseSha256)) {
            event(Log.WARN, "MEDIA_PF2_NOT_INSTALLED", null, "reason", "base_sha_mismatch");
            return;
        }
        final MediaStartupTrace2.Budget budget = new MediaStartupTrace2.Budget();
        final MediaStartupLaunchGate gate = MediaStartupLaunchGate.create(
                overrideSnapshot.get(MediaStartupLaunchGate.REQUEST_KEY));
        mediaLaunchGate = gate;
        mediaAutoGate = new MediaStartupAutoGate();
        mediaLaunchHandler = new Handler(Looper.getMainLooper());
        int installed = 0;
        boolean readinessInstalled = false;
        boolean endInstalled = false;
        boolean teardownInstalled = false;
        boolean activityInstalled = false;
        boolean launchRouteInstalled = false;
        for (MediaStartupTrace2.Target target : MediaStartupTrace2.TARGETS) {
            try {
                Class<?> owner = Class.forName(target.owner, false, loader);
                Class<?>[] parameters;
                switch (target.stage) {
                    case "INTENT_PROCESSED":
                    case "START_CAR_ACTIVITY":
                        parameters = new Class<?>[]{android.content.Intent.class,
                                Class.forName("rgi", false, loader)};
                        break;
                    case "INTENT_TARGET_STORED":
                        parameters = new Class<?>[]{android.content.Intent.class};
                        break;
                    case "MEDIA_APP_CHANGED":
                        parameters = new Class<?>[]{android.content.ComponentName.class};
                        break;
                    default:
                        parameters = new Class<?>[0];
                        break;
                }
                Method method = owner.getDeclaredMethod(target.method, parameters);
                if (!target.descriptor.equals(descriptor(method))) {
                    event(Log.WARN, "MEDIA_PF2_TARGET_REJECTED", null,
                            "stage", target.stage, "reason", "signature_mismatch");
                    continue;
                }
                hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            boolean armed = MediaStartupTrace.enabled(
                                    overrideSnapshot.get(MediaStartupTrace.KEY));
                            if (armed) {
                                int number = budget.take(target.stage);
                                if (number > 0) {
                                    event(Log.INFO, "MEDIA_PF2_STAGE_ENTER", null,
                                            "stage", target.stage, "ordinal", String.valueOf(number),
                                            "mode", "READ_ONLY");
                                }
                            }
                            // Teardown invalidates all outstanding requests before
                            // Android Auto starts shutting down its own session.
                            if ("PROJECTION_END".equals(target.stage) ||
                                    "PROJECTION_TEARDOWN".equals(target.stage)) {
                                gate.onEnded();
                                mediaAutoGate.onEnded();
                            }
                            Object original = chain.proceed();
                            if ("PROJECTION_READY".equals(target.stage) &&
                                    MediaStartupTrace.enabled(
                                            overrideSnapshot.get(MediaStartupTrace.KEY))) {
                                gate.onReady();
                                if (mediaAutoGate.onReady(
                                        MediaStartupAutoGate.enabled(overrideSnapshot.get(
                                                MediaStartupAutoGate.KEY)), SystemClock.elapsedRealtime())) {
                                    event(Log.INFO, "MEDIA_PF4_SESSION_ARMED", null,
                                            "auto", MediaStartupAutoGate.enabled(overrideSnapshot.get(
                                                    MediaStartupAutoGate.KEY)) ? "ON" : "OFF");
                                }
                                startMediaPolling();
                            } else if ("ACTIVITY_START_REQUESTED".equals(target.stage) && armed) {
                                gate.onActivityStart();
                                int sessionId = mediaAutoGate.onFirstActivity(SystemClock.elapsedRealtime());
                                if (sessionId >= 0) scheduleAutoSpotify(sessionId);
                            } else if ("MEDIA_APP_CHANGED".equals(target.stage) && armed) {
                                try {
                                    Object value = chain.getArg(0);
                                    mediaAutoGate.onMediaChanged(value instanceof ComponentName &&
                                            MediaStartupLaunchGate.SPOTIFY_PACKAGE.equals(
                                                    ((ComponentName) value).getPackageName()));
                                } catch (Throwable ignored) {
                                    mediaAutoGate.onMediaChanged(false);
                                }
                            } else if ("START_CAR_ACTIVITY".equals(target.stage) && armed) {
                                mediaAutoGate.onNavigation();
                            }
                            // Callback return is not proof of physical HU visibility.
                            if ("PROJECTION_READY".equals(target.stage) &&
                                    MediaStartupTrace.enabled(
                                            overrideSnapshot.get(MediaStartupTrace.KEY))) {
                                int number = budget.take("PROJECTION_READY_RETURN");
                                if (number > 0) {
                                    event(Log.INFO, "MEDIA_PF2_STAGE_RETURN", null,
                                            "stage", target.stage, "ordinal", String.valueOf(number));
                                }
                            }
                            return original;
                        });
                installed++;
                if ("PROJECTION_READY".equals(target.stage)) readinessInstalled = true;
                if ("PROJECTION_END".equals(target.stage)) endInstalled = true;
                if ("PROJECTION_TEARDOWN".equals(target.stage)) teardownInstalled = true;
                if ("ACTIVITY_START_REQUESTED".equals(target.stage)) activityInstalled = true;
                if ("START_CAR_ACTIVITY".equals(target.stage)) launchRouteInstalled = true;
                event(Log.INFO, "MEDIA_PF2_HOOK_INSTALLED", null,
                        "stage", target.stage, "mode", "READ_ONLY");
            } catch (ClassNotFoundException | NoSuchMethodException | LinkageError incompatible) {
                event(Log.WARN, "MEDIA_PF2_TARGET_REJECTED", null,
                        "stage", target.stage, "reason", incompatible.getClass().getSimpleName());
            } catch (Throwable failure) {
                event(Log.ERROR, "MEDIA_PF2_TARGET_FAILED", null,
                        "stage", target.stage, "reason", failure.getClass().getSimpleName());
            }
        }
        event(Log.INFO, "MEDIA_PF2_INSTALLATION_COMPLETE", null,
                "installed", String.valueOf(installed),
                "expected", String.valueOf(MediaStartupTrace2.TARGETS.length),
                "base", baseSha256.substring(0, 16));
        if (readinessInstalled && endInstalled && teardownInstalled &&
                activityInstalled && launchRouteInstalled) {
            installMediaLaunchProof(loader);
        } else {
            event(Log.WARN, "MEDIA_PF3_DISABLED", null, "reason", "required_hook_missing");
        }
    }

    /**
     * Resolve all required ABI before allowing any request. The DI singleton
     * nfh.a() and no-options rgi() constructor were inspected in AA 18.1 DEX.
     * No invocation occurs during installation; no automatic launch path exists.
     */
    private void installMediaLaunchProof(ClassLoader loader) {
        try {
            Class<?> owner = Class.forName("nfh", false, loader);
            Class<?> options = Class.forName("rgi", false, loader);
            Method factory = owner.getDeclaredMethod("a");
            Method launch = owner.getDeclaredMethod("k", Intent.class, options);
            Constructor<?> ctor = options.getDeclaredConstructor();
            if (!Modifier.isStatic(factory.getModifiers()) ||
                    !owner.equals(factory.getReturnType()) ||
                    !"()Lnfh;".equals(descriptor(factory)) ||
                    !"(Landroid/content/Intent;Lrgi;)V".equals(descriptor(launch)) ||
                    ctor.getParameterCount() != 0) {
                event(Log.WARN, "MEDIA_PF3_DISABLED", null, "reason", "abi_mismatch");
                return;
            }
            mediaControllerFactory = factory;
            mediaControllerStart = launch;
            mediaActivityOptionsConstructor = ctor;
            mediaLaunchAvailable = true;
            event(Log.INFO, "MEDIA_PF3_PROOF_ARMED", null, "mode", "MANUAL_PLUS_OPT_IN_AUTO",
                    "target", "SPOTIFY", "ttlMs",
                    String.valueOf(MediaStartupLaunchGate.TTL_MILLIS));
        } catch (ReflectiveOperationException | LinkageError missing) {
            event(Log.WARN, "MEDIA_PF3_DISABLED", null,
                    "reason", missing.getClass().getSimpleName());
        } catch (Throwable unexpected) {
            event(Log.ERROR, "MEDIA_PF3_DISABLED", null,
                    "reason", unexpected.getClass().getSimpleName());
        }
    }

    /**
     * PF4: schedule exactly one attempt for each first projected activity.
     * The session token invalidates callbacks after disconnect/reconnect.
     */
    private void scheduleAutoSpotify(int sessionId) {
        Handler handler = mediaLaunchHandler;
        if (handler == null || !mediaLaunchAvailable) return;
        handler.postDelayed(() -> attemptAutoSpotify(sessionId),
                MediaStartupAutoGate.SETTLE_MILLIS);
        event(Log.INFO, "MEDIA_PF4_AUTO_SCHEDULED", null,
                "delayMs", String.valueOf(MediaStartupAutoGate.SETTLE_MILLIS));
    }

    private void attemptAutoSpotify(int scheduledSession) {
        if (Looper.myLooper() != Looper.getMainLooper() || mediaAutoGate == null ||
                !mediaLaunchAvailable || !MediaStartupTrace.enabled(
                        overrideSnapshot.get(MediaStartupTrace.KEY))) return;
        MediaStartupAutoGate.Decision decision = mediaAutoGate.consume(scheduledSession,
                SystemClock.elapsedRealtime(), MediaStartupAutoGate.enabled(
                        overrideSnapshot.get(MediaStartupAutoGate.KEY)));
        if (decision != MediaStartupAutoGate.Decision.DISPATCH) {
            event(Log.INFO, "MEDIA_PF4_AUTO_SKIPPED", null, "reason", decision.name());
            return;
        }
        if (mediaLaunchGate == null || !mediaLaunchGate.reserveForAutomatic()) {
            event(Log.INFO, "MEDIA_PF4_AUTO_SKIPPED", null, "reason", "manual_budget_used");
            return;
        }
        event(Log.INFO, "MEDIA_PF4_AUTO_ATTEMPT", null, "target", "SPOTIFY");
        try {
            dispatchSpotify(scheduledSession);
            event(Log.INFO, "MEDIA_PF4_AUTO_DISPATCHED", null,
                    "result", "RETURNED", "visible", "UNVERIFIED");
        } catch (Throwable failed) {
            Throwable cause = failed instanceof InvocationTargetException &&
                    failed.getCause() != null ? failed.getCause() : failed;
            event(Log.ERROR, "MEDIA_PF4_AUTO_FAILED", null,
                    "reason", cause.getClass().getSimpleName());
        }
    }

    /** Called by remote-preference listener; never execute car activity on binder threads. */
    private void requestMediaLaunchCheck() {
        Handler handler = mediaLaunchHandler;
        if (handler != null) handler.post(() -> {
            if (mediaLabGate != null) checkMediaLabRequest();
            else checkMediaLaunchRequest();
        });
    }

    private void startMediaPolling() {
        Handler handler = mediaLaunchHandler;
        if (handler == null) return;
        // The lifecycle hook can arrive on a binder thread. Serialize the
        // poll scheduler on the AA main thread to avoid duplicate timers.
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post(this::startMediaPolling);
            return;
        }
        if (mediaPollScheduled) return;
        mediaPollScheduled = true;
        handler.postDelayed(mediaCommandPoll, 1000);
    }

    /** Fallback polling covers LSPosed implementations without remote change broadcasts. */
    private void checkMediaLaunchRequest() {
        if (!mediaLaunchAvailable || mediaLaunchGate == null ||
                !MediaStartupTrace.enabled(overrideSnapshot.get(MediaStartupTrace.KEY)))
            return;
        if (Looper.myLooper() != Looper.getMainLooper()) {
            requestMediaLaunchCheck();
            return;
        }
        try {
            SharedPreferences prefs = remotePreferences;
            if (prefs == null) return;
            String request = prefs.getString(MediaStartupLaunchGate.REQUEST_KEY, null);
            MediaStartupLaunchGate.Decision decision =
                    mediaLaunchGate.consider(request, SystemClock.elapsedRealtime());
            if (decision == MediaStartupLaunchGate.Decision.NO_CHANGE) return;
            if (decision != MediaStartupLaunchGate.Decision.ACCEPTED) {
                event(Log.WARN, "MEDIA_PF3_REQUEST_REJECTED", null, "reason", decision.name());
                return;
            }
            final int activeSession = mediaLaunchGate.session();
            if (!mediaLaunchGate.stillReadyInSession(activeSession)) {
                event(Log.WARN, "MEDIA_PF3_REQUEST_REJECTED", null, "reason", "session_ended");
                return;
            }
            mediaAutoGate.onManualAttempt();
            event(Log.INFO, "MEDIA_PF3_REQUEST_ACCEPTED", null,
                    "mode", "MANUAL_ONESHOT", "target", "SPOTIFY");
            try {
                dispatchSpotify(activeSession);
                // Successful return is NOT proof the HU displayed Spotify.
                event(Log.INFO, "MEDIA_PF3_REQUEST_DISPATCHED", null,
                        "result", "RETURNED", "visible", "UNVERIFIED");
            } catch (Throwable error) {
                Throwable cause = error instanceof InvocationTargetException &&
                        error.getCause() != null ? error.getCause() : error;
                event(Log.ERROR, "MEDIA_PF3_LAUNCH_FAILED", null,
                        "reason", cause.getClass().getSimpleName());
            }
        } catch (Throwable error) {
            event(Log.ERROR, "MEDIA_PF3_REQUEST_FAILED", null,
                    "reason", error.getClass().getSimpleName());
        }
    }

    private void dispatchSpotify(int sessionId) throws Exception {
        if (mediaLaunchGate == null || !mediaLaunchGate.stillReadyInSession(sessionId))
            throw new IllegalStateException("Projection ended");
        Object controller = mediaControllerFactory.invoke(null);
        if (controller == null || !mediaLaunchGate.stillReadyInSession(sessionId))
            throw new IllegalStateException("Controller unavailable or session ended");
        Object options = mediaActivityOptionsConstructor.newInstance();
        Intent intent = new Intent(MediaStartupLaunchGate.MEDIA_ACTION);
        intent.setComponent(new ComponentName(
                MediaStartupLaunchGate.SPOTIFY_PACKAGE,
                MediaStartupLaunchGate.SPOTIFY_SERVICE));
        // The inspected AA controller remains responsible for routing and policy.
        mediaControllerStart.invoke(controller, intent, options);
    }

    private void installProbe(ClassLoader loader, JSONObject mapping, String baseSha256) {
        String key = mapping.optString("key", "<unknown>");
        // The read-only diagnostic feature is intentionally narrower than a future
        // Advanced Overrides mode. The runtime independently enforces the cohort.
        if (!("PhoneThemeFeature__enable_dynamic_app_icon_shape_by_manufacturer_kill_switch".equals(key)
                || "CarAppLibrary__recreate_map_view_context_on_config_change_kill_switch".equals(key)
                || "WirelessLatencyMonitor__warning_threshold_ms".equals(key)
                || DoubleGetterProbeRuntime.isDoubleTarget(key))) {
            event(Log.WARN, "PROBE_REJECTED", key, "reason", "not_allowlisted");
            return;
        }
        String expectedDescriptor = DoubleGetterProbeRuntime.isDoubleTarget(key) ? "()D" :
                key.equals("WirelessLatencyMonitor__warning_threshold_ms") ? "()J" : "()Z";
        try {
            String className = mapping.getString("className");
            String methodName = mapping.getString("methodName");
            String descriptor = mapping.getString("descriptor");
            if (!expectedDescriptor.equals(descriptor)) {
                event(Log.WARN, "PROBE_REJECTED", key, "reason", "type_mismatch");
                return;
            }
            if (DoubleGetterProbeRuntime.isDoubleTarget(key) &&
                    !DoubleGetterProbeRuntime.permitted(key, className, methodName,
                            descriptor, baseSha256)) {
                event(Log.WARN, "PROBE_REJECTED", key, "reason", "double_target_or_build_mismatch");
                return;
            }
            Class<?> cls = Class.forName(className, false, loader);
            Method method = cls.getDeclaredMethod(methodName);
            if (method.getParameterCount() != 0 || !descriptor.equals(descriptor(method))) {
                event(Log.WARN, "PROBE_REJECTED", key, "reason", "changed_signature");
                return;
            }
            hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                if (probedCalls.putIfAbsent(key, Boolean.TRUE) == null) {
                    // Log before proceeding: this proves the original getter was reached,
                    // without logging its value or ever changing its return.
                    event(Log.INFO, "PROBE_INVOKED", key);
                }
                return chain.proceed();
            });
            event(Log.INFO, "PROBE_HOOK_INSTALLED", key, "target", className + "." + methodName + descriptor);
        } catch (ClassNotFoundException | NoSuchMethodException | LinkageError failure) {
            event(Log.WARN, "PROBE_REJECTED", key, "reason", failure.getClass().getSimpleName());
        } catch (Throwable failure) {
            event(Log.ERROR, "PROBE_FAILED", key, "reason", failure.getClass().getSimpleName());
            log(Log.ERROR, TAG, "Read-only probe failed for " + key, failure);
        }
    }

    private void logReturnOnce(String key, String event, String generation, Object value) {
        String token = event + ':' + generation;
        if (token.equals(lastReturnEvent.put(key, token))) return;
        // Normal STRING overrides may contain personal or provider data. Never log contents.
        event(Log.INFO, event, key, "value", value instanceof String ? "<redacted>" : String.valueOf(value));
    }

    private void event(int priority, String event, String key, String... fields) {
        try {
            JSONObject payload = new JSONObject().put("event", event);
            if (key != null) payload.put("key", key);
            for (int i = 0; i + 1 < fields.length; i += 2) payload.put(fields[i], fields[i + 1]);
            log(priority, TAG, EVENT_PREFIX + payload);
        } catch (Throwable ignored) {
            log(priority, TAG, EVENT_PREFIX + event + (key == null ? "" : " key=" + key));
        }
    }

    private JSONObject readProfile() throws Exception {
        SharedPreferences prefs = remotePreferences != null
                ? remotePreferences : getRemotePreferences(PREF_GROUP);
        String active = prefs.getString(ProfileSlotPolicy.POINTER_KEY, null);
        Throwable lastFailure = null;
        for (String candidate : ProfileSlotPolicy.readOrder(active)) {
            try (ParcelFileDescriptor descriptor = openRemoteFile(candidate);
                 FileInputStream input = new FileInputStream(descriptor.getFileDescriptor());
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[16384];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                    if (output.size() > ProfileSlotPolicy.MAX_PROFILE_BYTES)
                        throw new IllegalArgumentException("Profile too large");
                }
                JSONObject profile = new JSONObject(output.toString("UTF-8"));
                // A wrong-type mappings field is rejected by getJSONArray.
                profile.getJSONArray("mappings");
                if (profile.getInt("schemaVersion") != 3 ||
                        profile.getString("buildFingerprintSha256").isEmpty())
                    throw new IllegalArgumentException("Invalid profile header");
                if (active != null && !candidate.equals(active))
                    event(Log.WARN, "PROFILE_FALLBACK", null, "source", candidate);
                event(Log.INFO, "PROFILE_SOURCE", null, "file", candidate);
                return profile;
            } catch (Exception error) {
                lastFailure = error;
            }
        }
        throw new IllegalStateException("No valid profile slot or legacy profile", lastFailure);
    }

    private static String descriptor(Method method) {
        StringBuilder out = new StringBuilder("(");
        for (Class<?> type : method.getParameterTypes()) out.append(typeDescriptor(type));
        return out.append(')').append(typeDescriptor(method.getReturnType())).toString();
    }

    private static String typeDescriptor(Class<?> type) {
        if (type.isPrimitive()) {
            if (type == void.class) return "V"; if (type == boolean.class) return "Z"; if (type == byte.class) return "B";
            if (type == short.class) return "S"; if (type == char.class) return "C"; if (type == int.class) return "I";
            if (type == long.class) return "J"; if (type == float.class) return "F"; if (type == double.class) return "D";
        }
        if (type.isArray()) return type.getName().replace('.', '/');
        return "L" + type.getName().replace('.', '/') + ";";
    }

}
