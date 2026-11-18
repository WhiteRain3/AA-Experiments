# Fork handoff (WhiteRain3)

Every session working on this fork reads this file first and updates it last. Sessions do not
share memory; this file is the shared memory. Keep it short and factual. If something here is
wrong, fix it in the same commit as the work that showed it was wrong.

## What this fork is

A fork of AA Experiments (GPL-3.0-or-later): a Kotlin/Compose manager app plus a Java LSPosed
module that runs inside Android Auto. Upstream edits Android Auto's experiment flags. This fork
adds look customisation (colours, wallpaper, glyph icons, status bar), presets, an in-app
updater, a restart button and diagnostics. Fork changes are listed in `CHANGELOG.md` under
"Fork additions (WhiteRain3)".

- Repository: `WhiteRain3/AA-Experiments`, working branch `claude/companion-features`.
- Fork build numbers: `versionName '1.0.0-wrN'` in `app/build.gradle`. `versionCode` stays as is.

## The owner's rules

These come from the owner and are not up for reinterpretation.

1. Keep the existing flag-writing logic working. Refactor it only if needed, and say why.
2. No analytics and nothing that phones home. The only network use is the updater, which the
   owner asked for: it reads this fork's GitHub releases on request and sends nothing about
   the user.
3. Small commits with clear messages. After each major step, a short summary and how to test
   it on the phone and in the car.
4. Where the effect of something is not known, say so. Do not invent descriptions.
5. No third-party logos, artwork or fonts (game or car brands). Themes are original palettes;
   the owner supplies their own images.
6. Plain English in the app and in messages to the owner. They are not an Android developer.

## Build, test, release

The cloud environment installs the Android SDK at `/opt/android-sdk` when a session starts
(unproven on a fresh session at the time of writing; if `/opt/android-sdk/platforms/android-36`
is missing, see "If the SDK is missing" below).

Build and run all unit tests, with a hard pass/fail. Never commit when this fails:

```bash
set -o pipefail; if ANDROID_HOME=/opt/android-sdk gradle --no-daemon --max-workers=1 -q :app:assembleDebug :app:testDebugUnitTest --console=plain > /tmp/build.log 2>&1; then echo BUILD_OK; else echo BUILD_FAILED; grep -E "^e: |error:|FAILED" /tmp/build.log | head -20; fi
```

The first build in a fresh session downloads the libraries. One of them comes from
`api.xposed.info`, which is on the environment's allowed hosts; if dependency resolution fails
on the first try, run the command again before treating it as a code problem. Maven Central has
also answered 404 at random for files that exist (seen 2026-11-18 for
`kotlin-compose-compiler-plugin-embeddable-2.2.10`); the same cure applies, sometimes twice.

It takes about three minutes. Test results are in `app/build/test-results/testDebugUnitTest/`.
At the time of writing there are 463 tests and none fail.

Release: `.github/workflows/fork-release.yml` runs on every push to `claude/companion-features`.
If the `versionName` in `app/build.gradle` has no GitHub release yet, it tests, builds, signs
with the owner's key (repository secrets) and publishes a release named after the version. The
app's "Check for updates" then offers it. So:

- A push that does not change `versionName` publishes nothing.
- To ship: bump `versionName` to the next `wrN`, update the matching line at the end of the
  fork section in `CHANGELOG.md`, push, then confirm
  `https://github.com/WhiteRain3/AA-Experiments/releases/latest` redirects to the new tag
  (about five minutes).
- After pushing a version bump, push nothing else to the branch until the release is confirmed
  published. This went wrong once (wr27): commits pushed behind the release commit each
  started their own build with the same version; the builds for the older commits failed at
  `gh release create` with HTTP 403, and the newest commit's build published, so unreviewed
  work shipped under the release's number. Keep unreviewed work local until then.
- Only the Release session bumps the version. Workers leave it alone, so two sessions never
  fight over it.

APKs built in a session are signed with a throwaway key and cannot install over a released
build. Do not send them to the owner; ship through a release.

### If the SDK is missing

```bash
mkdir -p /opt/android-sdk/cmdline-tools /tmp/aasdk && curl -fsSL -o /tmp/aasdk/tools.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip && unzip -q -o /tmp/aasdk/tools.zip -d /tmp/aasdk && mv /tmp/aasdk/cmdline-tools /opt/android-sdk/cmdline-tools/latest && yes | /opt/android-sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=/opt/android-sdk "platforms;android-36" "build-tools;35.0.0" "platform-tools"
```

## Nobody here can see the phone or the car

There is no emulator and no device. Everything is verified by building, by unit tests on the
pure logic, and by what the owner reports back. Consequences:

- Put logic that can be tested into plain classes (`core/` in Kotlin, `runtime/` in Java) with
  unit tests. Keep Android-dependent code thin.
- Every module hook fails open: on any error, the original Android Auto behaviour proceeds.
- Report honestly: "builds and passes tests, not run on a device" until the owner confirms.
- The owner tests in the car and updates from anywhere through the in-app updater.

## The owner's setup

- Phone: Android 16, arm64, rooted, LSPosed-compatible framework with the libxposed API 101.
- Android Auto 18.0.664264.
- Car: Audi with a 7-inch non-touch screen driven by a rotary knob, through a wireless adapter.
  The owner no longer uses the Desktop Head Unit.
- Car screen as Android Auto sees it: 1200 x 723 pixels at 213 dpi. The rail is on the left,
  107 px wide, and is a separate window (`GhFacetBar`). The main area is 1093 x 723. The
  video frame is 1280 x 720 with about 80 px unused on the right.

## How the module works (the parts that matter)

- `runtime/AndroidAutoModule.java` is the LSPosed module. Hooks use
  `hook(method).setExceptionMode(PROTECTIVE).intercept(chain -> …)`.
- The manager talks to the module through LSPosed remote preferences (group
  `dynamic_overrides_v2`, all values strings) and remote files. The module reads them through
  `overrideSnapshot`.
- The module talks back through `ModuleReportProvider` (authority
  `io.github.highwindbr.aaxp.reports`), which only accepts callers running as Android Auto.
- One-shot requests (`runtime/ControlRequests.java`): restart, head unit server, layout dump.
  A request is only honoured while fresh (30 s).
- Android Auto's code is obfuscated and changes with every version. Class names such as the
  colour-role enum (`lfv` in 18.0) are found at runtime by `discovery/ThemeRoleLocator.kt`,
  never hard-coded.

## What exists, and how far it has been confirmed

Confirmed by the owner in the car:

- Flag overrides, presets.
- Colour roles (theme editor): they recolour dashboard cards, card icons and text, rail icon
  rings. See the confirmed role list below.
- Glyph app icons.
- Car wallpaper on the launcher; the slice behind the rail (shows, a few pixels misaligned;
  the editor has nudge sliders for this).
- In-app update, once (wr18 to wr19).
- Screen layout capture.

Built and unit-tested, not yet confirmed on the device:

- The reorganised app (Home, Look, Flags, Settings tabs; one page per feature), wr25.
- Crash safe mode for the look hooks.
- Status bar changes (hide or recolour clock, signal, battery; remove the plate).
- Status bar plate colour and corner radius (`plate_color`, `plate_radius` in dp). Read from the
  18.0 code: `status_bar_card_view` is `CoolwalkCardView extends MaterialCardView extends
  CardView`, and the card setters are renamed by obfuscation (`setCardBackgroundColor(int)` is
  `es(int)`; there is no `setRadius` by that name), so reflection by name fails and the module
  takes its `drawable` route: `setBackground(GradientDrawable)` with `clipToOutline`.
  MaterialCardView allows a custom background after construction (it only logs "Setting a custom
  background is not supported"). A radius without a colour cannot be applied, so the app only
  offers roundness with a colour. Unknown until seen: whether the card's own foreground (stroke,
  ripple) still shows the old shape. The same applies to "remove the plate", which ends up in
  `setBackgroundColor(0)`. New status bar settings go in `StatusBarTweaks.Builder`.
- Status bar clock size and boldness (`clock_size`, `clock_bold` in `statusbar.config`). Unknown
  until seen: whether Android Auto resets the text size or typeface later (for example on a
  day/night switch), and whether 140% is clipped by the 107 px rail.
- Style packs and backup (one file with the whole look).
- Restart button's acknowledgement and root fallback (wr20).
- Update check through the release page redirect (wr21).
- Wallpaper darken and blur, seed colour from the wallpaper, theme copy and paste, the
  quick settings tile, rail alignment nudges.
- "More colours" (labels, clock, rail icons, launcher background, map focus border, and a
  focus ring row that may do nothing).
- Rail strip colour and wallpaper strength (`wallpaper.rail_look`, "color=AARRGGBB;strength=80",
  parsed by `CarWallpaper.parseRailLook`). Not in look files or Undo yet.
- Glyph figure size (`icons.glyph_size`, 50 to 150, `GlyphIcons.insetFraction`). Above 100% a
  themed layer gets an InsetDrawable with a negative fraction (bounds beyond the layer, clipped
  by it); that this draws as intended is read from the platform source, not seen. Not in look
  files or Undo yet.
- Launcher "icons only" (`runtime/LauncherTweaks.java`, key `launcher.config`, value
  `names=off`): hides `grid_item_text_container` in each launcher item after inflation, through
  the same inflate hook and keep-hidden set as the status bar. Not in look files or Undo yet
  (`core/LookBundles.kt`, `core/LookUndo.kt` are the App worker's). Unknown until seen: whether
  rows close up or leave a gap, and whether the item layout goes through
  `LayoutInflater.inflate(int, ViewGroup, boolean)` at all (event `LAUNCHER_NAMES_HIDDEN` in the
  module log says it did).

### Colour roles confirmed on the car (Android Auto 18.0)

| Role | What it paints |
|---|---|
| `OutlineVariant` | Ring around the rail's app icons and around the map's round button |
| `Surface` | Background of dashboard cards |
| `SurfaceVariant` | Round icon plates inside cards |
| `SurfaceHigh` | The round floating button on the map |
| `OnSurface` | Card titles, the map button's icon, the active page dot |
| `OnSurfaceVariant` | Icons inside cards, media skip buttons |
| `TextPrimary` | Song title and artist on the media card |

The other 39 roles have not been seen in use.

Android Auto has a second source of colour that the roles never reach: a tonal palette in its
resources, used from layouts through theme attributes `palette_{p,s,t,n,nv,e}_{tone}`, which
point at colour resources named `baseline_palette_*`, `dynamic_palette_*` and
`neutral_palette_*`. Found in the 18.0 resources and matched to the owner's screenshots:

| Colour resource | Value | What it paints |
|---|---|---|
| `baseline_palette_n_95` | `#F2F0F0` | App labels, the clock, the rail's microphone and launcher icons |
| `baseline_palette_n_15` | `#252626` | Launcher background behind the wallpaper |
| `neutral_palette_p_40` | `#00629F` | Focus border around the map on the dashboard |

`runtime/ResourceColors.java` and `installResourceColorHooks()` override these by hooking
`TypedArray.getColor/getColorStateList` and `Resources.getColor/getColorStateList`; the "More
colours" card on the Themes page sets them. Built and unit-tested, not confirmed on the car.

The launcher's focus ring is `?attr/palette_p_85` in the theme. On the car it was a light pink
while the role finder had `Primary` set to pure red, so it appears to be a light tint derived
from `Primary`; this is an inference from one screenshot. "More colours" has a focus ring row
(group `focus_ring`: `baseline_palette_p_85`, `dynamic_palette_p_85`) to settle it: if the ring
does not change on the car, it is computed from `Primary` and the row should be removed. The media card's background and play
button are not themed by either mechanism (probably taken from the album art).

### View ids seen in the layout capture (Android Auto 18.0)

Rail window: `rail_container` > `rail`; hotseat items `hotseat_one`..`hotseat_four` with
`large_icon_background` and `large_icon`; `assistant_icon`; `launcher_and_dashboard_icon`;
status bar `status_bar_card_view` > `status_bar_container` (a ConstraintLayout) holding
`icon_row` (a horizontal LinearLayout with `signal_icon`, a FrameLayout, and `battery_level`,
an ImageView), `clock`, `notification_count_wrapper` and `dnd_icon`.
Launcher: `grid_background` (the wallpaper, 80% opaque over `#252626`); `AutoTunedRecyclerView`
`tuned_recycler_view` > `GridRowContainerView` (four items) > `GridItemView`, each holding
`grid_item_image_container` (117x117 `grid_item_image`) and `grid_item_text_container` (173x52,
with CarTextViews `grid_item_title` and `grid_item_text`). Focus is handled by `FocusInterceptor` views.

### How Android Auto 18.0 loads app icons (read from the decompiled code)

Launcher, rail hotseat and dashboard cards all go through one funnel: Android Auto's icon class
(`GhIcon`, built from a ComponentName) -> an in-memory cache ("GH.AppIconCache", an LruCache of
24 keyed by component and size; no disk cache) -> "GH.IconLoader", which calls
`pm.getResourcesForApplication(pkg).getDrawableForDensity(iconRes, density, null)`. So in the
module's `Resources.getDrawableForDensity` hook, `resources.getResourcePackageName(id)` is the
app's package; `iconOwner()` in the module does this. `PackageItemInfo.loadIcon`,
`LauncherActivityInfo` and `getActivityIcon` are not used for app icons;
`getApplicationIcon(String | ApplicationInfo)` is a fallback. After loading, an adaptive icon's
background and foreground layers are drawn into a bitmap, and from there on only bitmaps travel,
with no package attached. Android Auto swaps its own dialer, calendar and settings components
for the phone's apps before loading, so those icons belong to the phone's apps. Notification
small icons and media art arrive as Icon or Bitmap objects and cannot be tied to an app.
Per-app choices are stored in `icons.per_app` (`GlyphIcons.parsePerApp`, `effectiveColors`).

### Day and night in Android Auto 18.0 (read from the decompiled code, nothing built yet)

- The car's night state arrives as a car sensor event and is filtered by Android Auto's own
  developer setting `car_day_night_mode` (car / day / night / auto). The owner can use that
  setting to test day and night without waiting for dusk.
- It reaches the UI as `Configuration.uiMode` night bits on a per-window context
  (`createDisplayContext(...).createConfigurationContext(...)`), kept current while running.
  The application context's `uiMode` is the phone's, not the car's.
- The role enum (`lfv`): methods taking a Context pick dark or light from that context's
  `uiMode`; two methods without a Context always answer dark and two always light (same
  signatures, so only telling them apart by calling the original and comparing works).
  Light is also gated by a flag ("GM3"): when it is off, everything is dark whatever the car
  says. Whether it is on for the owner is not known.
- Palette colour resources (`baseline_/dynamic_/neutral_palette_*`) have no night variants; day
  and night differ one level up, in which tone a theme attribute points at. A hook on
  `Resources`/`TypedArray` can read night from that Resources' `uiMode`.
- On a change Android Auto rebuilds the window's context and either re-creates the root view or
  dispatches a configuration change; which one per window (dashboard, launcher) was not traced.
- Verdict: colours can follow day and night without a restart for role colours and resource
  colours (answered per call from `uiMode`); colours the module sets itself on views (status
  bar) only refresh if that view is inflated again. Compose screens may keep a colour scheme
  when the flag above is off. All of this needs the car to confirm.

### The dashboard's windows in Android Auto 18.0 (read from the decompiled code, nothing built yet)

- "Dashboard" and "GhFacetBar" are not activities: each is a fragment inside an
  `android.app.Presentation`, with a transparent decor (background colour 0, translucent
  format) and the title set on the window attributes before `show()`. A hook after
  `Dialog.show()` can match `getWindow().getAttributes().getTitle()` without obfuscated names.
- The Dashboard window is exactly the cards column (405x697 on the owner's car). Its content
  is a Compose view; cards have about 12dp between them and 20dp corners. Nothing opaque sits
  behind the cards inside that window.
- The map region fills the whole main area and belongs to the navigation app; the Dashboard
  window floats over part of it. There is no background layer behind the map or in the gutter
  around the cards column.
- The launcher's wallpaper view (`grid_background`) exists only in the launcher's layout.
- So a wallpaper on the dashboard can only be an image inside the Dashboard window: visible in
  the gaps between cards and at their corners, or through the cards if the `Surface` role is
  given some transparency. Nothing can be shown behind the map.

## Reverse-engineering material

Working out Android Auto's internals needs its decompiled code and resources. Those were
uploaded by the owner into one session and are not in this repository, because they are
Google's. A session that needs them asks the owner to upload `gearhead-code.zip` (the five
`classes*.dex` files) and `gearhead-res.zip` (`resources.arsc`, `res/`, `AndroidManifest.xml`)
again. To read them: put all of it into one zip and run jadx on that zip (jadx 1.5.1 from its
GitHub releases; about three minutes, 26,000 files); the session's permission check may ask the
owner to allow running it.

### Status bar settings need a restart (looked at 2026-11-18, left as is)

The status bar hooks are installed only when a setting exists at process start, and settings are
applied once, when the status bar layout is inflated. Re-applying on a preference change would
need: the inflate hook installed for everyone (a cost on every inflate for users with no
setting), weak references to the inflated status bar roots, and the original colour, tint, size,
visibility and background of every changed view so that a removed setting can be put back. That
is not a small change and cannot be checked without the car, so it has not been done.

## Open questions for the owner

- What "the system_theme too" referred to when they said it does not work.
- What they find ugly about the status bar.
- Whether the red clock in their role finder screenshot came from the `Primary` role or from a
  clock colour they set on the Status bar page.
- Feedback on the reorganised app (wr25): what still looks confusing or cheap.

## Backlog, most valuable first

1. Polish the reorganised app after the owner's feedback: each feature page should feel
   finished (previews, less text, consistent spacing), not a moved card.
2. Get the owner's result for "More colours" and fix what it shows. If labels change in places
   they should not, narrow the override from the palette colour to specific views.
3. (Done, untested on device) Live preview of a theme in the theme editor. Design reviewer list items 2-9 (cut text, page previews, wallpaper editor order, Home/Look tiles, action consistency, wording, colour picker) are still open.
4. Per-app icon overrides, hiding and renaming apps in the launcher.
5. Day and night themes.
6. Wallpaper on the dashboard, not only the launcher.
7. Launcher grid size, custom font, density override. All uncertain.

## Working agreement between sessions

- One writer per area at a time. Before starting, `git fetch origin claude/companion-features`
  and work on top of it. Before pushing, fetch and merge again; never force-push.
- A worker's task is one small change. It ends with: build and tests pass, a commit, a push,
  this file updated, and a short note of what was done and how the owner can test it.
- Workers do not bump the version.
- Releases are automatic (the owner's decision, 2026-11-18): once a change has been reviewed,
  the review is clean, and the build and tests pass, whoever is running the chain publishes
  the next `wrN` without asking, then tells the owner what is in it and how to test it. A
  serious review finding holds the release until it is fixed.
- Hand-offs between sessions (plan to worker, worker to reviewer, findings back to worker)
  are carried by the session that created them. A session that has the Claude Code Remote
  `send_message` tool can reply to that session with `session_id` "@parent"; the Reviewer has
  done so. Do not assume a hand-off happened because the owner asked a worker for it: either
  send the message yourself and say that you did, or say plainly that you cannot.
- If a task turns out to need a decision from the owner, stop and ask rather than guess.
- Do not open pull requests unless the owner asks.

## Roles

The session that created the others is the **dispatcher**. The owner was explicit about this
(2026-11-18): it hands every piece of work to the right session and looks after the chain. It
does not write code, plans, ideas or designs itself. Its own work is limited to: routing and
relaying, checking results, the release step after a clean review, and keeping this file
current. If the right session cannot do a task (for example the Hooks worker lacks the
reverse-engineering material), it says so to the owner instead of quietly doing the task.


- **Manager**: talks with the owner, keeps this file and the backlog current, writes briefs
  for the other sessions, checks their results.
- **Planner**: turns an idea into a short ordered task list with what each task touches and
  how it will be verified. Writes no code.
- **App worker**: changes to the manager app's screens and logic (`*.kt` outside `runtime/`).
- **Hooks worker**: changes to the module and anything that depends on Android Auto's
  internals (`runtime/`, `discovery/`).
- **Reviewer**: reads a diff for correctness bugs and rule violations before release. Changes
  nothing.
- **Release**: bumps the version, updates the changelog line, pushes, confirms the release.
- **Design reviewer**: looks at the owner's screenshots of the app and says, concretely, what
  to change. Changes nothing.
