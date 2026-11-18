# F-Auto: what it is and what a fork for the owner's Audi would take

Planner report, 2026-11-18. Source read: https://github.com/malashhab/F-Auto at `7f87c1c`
(2026-11-12). It was cloned read-only, and nothing was built or run. Everything below comes from
reading its code and its own documents. Where the source cannot answer a question, this report
says "not known".

## In one paragraph

F-Auto is a small personal re-skin of a much larger open-source project called AARE. AARE is
real, substantial work: about 33,000 lines of Kotlin and 165 unit tests, with its own Android Auto
protocol stack and a desktop head unit for testing on a PC. It already handles a rotary knob.

The biggest risk is not the knob or the screen. It is whether the Audi accepts a phone that is
not Google's Android Auto. AARE's notes say a Skoda MIB3, which is from the same family as Audi's
systems, refused it. If the Audi refuses it too, the only known way through is to present Google's
own certificate, taken from the Android Auto app. That is a decision for the owner, not a
technical one (see section 6).

## 1. What it is in fact

**How it connects.** F-Auto speaks Android Auto's protocol from the phone itself, and Google's
Android Auto app is not involved.
- Over USB, or wirelessly the same way Android Auto does it: Bluetooth first, then the phone
  joins the adapter's Wi-Fi, then a network connection to the car.
- It has a special "official phone message order" mode for cheaper adapters. That mode is chosen
  automatically when the Bluetooth name starts with "Kowan" or "AA-Wireless".
- It reconnects automatically up to 5 times.

**What it draws.** The whole car screen is its own Compose UI, drawn into a virtual display that
is video-encoded and sent to the car.
- Two layouts: "Classic", which is AARE's original, and "Dusk", which F-Auto added.
- Six colour schemes, built on standard Material colour roles.
- Media from any app with a standard media service.
- Navigation from apps built on Google's Car App Library (Waze and OsmAnd, for example).
- Messages read from notifications, phone calls over the car's normal Bluetooth hands-free link,
  a web browser screen, a car data screen, and car-side settings.

**Who wrote what.**
- Almost all of the code is AARE by prototux and contributors: about 230 commits in the recent
  history, with active merges up to 2026-11-10.
- F-Auto's own part is 7 commits by one author on 2026-11-12. They add tuning for one Toyota
  Fortuner and one Kowan KA05 adapter, the Dusk layout and phone screens, and a "Connect now"
  button.
- The package name is still AARE's (`net.prototux.aare`).

**What is solid.**
- The protocol stack (`aap/`), with loopback tests against a simulated head unit, including knob
  turns.
- Wireless bootstrap, with tests.
- Video encoding, the media bridge, and the host for Car App Library templates.
- The documentation is unusually honest and careful (`docs/certificates.md`, `docs/decisions.md`).

**What is rough.**
- F-Auto's own additions (the Dusk layout and its rail) are touch-first: a collapsible rail with a
  tap handle, and no visible focus ring.
- Many strings are English only.
- It was tested on one car and one adapter, and the author says so.
- I found only one leftover "stub" comment, so nothing obvious is half-built. That said, "not
  known" applies to everything that has not been tried on a VW-group car.

**Licence.** GPL-3.0-or-later, the same as our current fork. A fork must:
- keep the licence and the credits to AARE and F-Auto;
- publish its source along with every APK released (a public GitHub repository does this);
- mark what it changed.

Our own code can be moved in, because it is under the same licence. Third-party logos are not
allowed, as before. The app and package name should be changed so it does not pose as AARE.

**Activity.** AARE upstream is very active (many merged PRs in November 2026). F-Auto is a few
days old and maintained by one person. A fork should follow AARE, not F-Auto.

## 2. The owner's car: a knob, no touch, 1200 x 723

**What already works for a knob.**
- AARE turns each knob detent into "focus next/previous" (`car/CarSession.kt`, input section).
  This is the same one-dimensional movement Android Auto uses, not up/down/left/right.
- The car's other keys (Back, Home, media, Phone, voice, D-pad tilts, and probably the knob
  press, which would arrive as Enter or D-pad centre) are passed on or handled.
- There is a visible focus ring, a 3 dp border in the accent colour (`ui/car/CarFocus.kt`).
  It is used on the app-template screens (lists, grids, maps, keyboard, tabs) and on media
  screens.

**What is missing for a knob-only screen.**
- **The home screen and rail of both layouts** (`HybridShell.kt` for Classic, `DuskUIShell.kt` for
  Dusk) have about 13 tappable things each, with no focus ring. The knob would move focus
  invisibly there.
- The same is true of the phone, settings, notifications, browser, car-data and call screens.
- Touch-only things need a knob alternative:
  - Dusk's collapse handle;
  - pinch-zoom on the map;
  - any slider;
  - the "music card size" setting, which is a drag.
- **Mirrored phone apps (video, Google Maps) receive only touches and Back**
  (`ui/car/StandaloneAppScreen.kt`). With no touchscreen, YouTube on the car screen could not be
  controlled at all until knob and D-pad keys are forwarded into that app. That is feasible: the
  code already injects Back there.
- **A known open issue on Android 14 and later** (`docs/decisions.md`, "finding 6"): while the
  phone is locked, Android may refuse to move the car screen out of "touch mode". Then the knob
  has nothing to focus. The owner's phone runs Android 16. This is not known until tried, and it
  is the first thing to check in the car.

**Screen size.** The car asks for 1280 x 720 video, and AARE insets its UI to the area the car
says is visible. The owner's 1200 x 723 should fit. The layout sizes were tuned for an 8-inch
touch unit (tap targets of 76 dp), so a 7-inch screen will need its own size set. Not known how
it looks until a screenshot exists.

**What a knob-only UI takes.** These are App-type changes, all inside F-Auto's own Compose code,
with no Android Auto internals involved:
- a focus ring on every tappable element;
- a sensible focus order;
- a knob path for every touch-only gesture;
- key forwarding into mirrored apps;
- a compact size set for 1200 x 723.

## 3. Testing without the car: Google's Desktop Head Unit and AARE's own

- **Google's Desktop Head Unit: no**, with F-Auto's own certificate. It checks the phone's
  certificate and refuses (status -3, from AARE's own tests). It would only pass with Google's
  certificate (see section 6).
- **AARE's own desktop head unit: yes.** This is the route that matters.
  - It is in the repository (`headunit/`, started by `./headunit.sh`) and runs on a PC.
  - It needs Java 17, ffmpeg and adb, with the phone on USB debugging.
  - It can imitate the owner's car closely: `--res 1280x720 --dpi 213 --no-touch` presents a
    knob-only unit, and arrow keys or the mouse wheel act as the knob.
  - It can run **headless and scripted**: `--headless DIR` saves a picture of the car screen every
    few seconds, and `--script "rotate:3 key:66 park drive night"` drives it.
- So the owner's PC Tester can run a scripted session and send back screenshots of the car screen
  after each change. That gives us a "car screen we can see", which the current fork has never
  had.
- Not testable this way: the Audi's certificate check and the real wireless adapter.

## 4. Features and what they need on a rooted Android 16 phone

| Feature | Works without root? | Notes |
|---|---|---|
| Media (Spotify, podcasts, local players) | yes | Any app with a standard media service. **Google's YouTube Music refuses unofficial hosts.** |
| Navigation in the car-app style (Waze, OsmAnd…) | yes, partly | Waze checks who is hosting it. AARE gets around this with a permission for older library versions, and with an optional LSPosed module (`xposed/`, which the owner can run). |
| Google Maps | needs Shizuku or root | Not as a car app (Google blocks it). Runs the phone's Maps on a virtual display sized to the car. Usable while driving. |
| Video (YouTube app) and any phone app | needs Shizuku or root | "Apps on the car screen": runs the app on a car-sized virtual display. **Paused automatically when the car reports driving** (for use while parked or by a passenger). On a knob-only car it **cannot be controlled yet** (section 2). |
| Web browser | yes | Built in, paused while driving. YouTube in the browser is a fallback: it is a web view, so focus and video playback with a knob are not known. |
| Sound through the car | yes, with one consent tap per session | Records phone audio with screen-capture permission. Some apps forbid being recorded, so their sound is missing. **With root, everything is captured**, navigation voice included. The owner is rooted. |
| Calls | yes | Over the car's normal Bluetooth hands-free link. |
| Messages read aloud and quick replies | yes | Needs notification access. |
| Voice button | yes | Opens the phone's assistant. |

## 5. What carries over from our fork

**Carries over well.** All of these are plain Kotlin and Compose, the same technology as F-Auto.
- **Themes and colour roles.** F-Auto colours its screen with Material roles (primary, surface,
  onSurface, outlineVariant…). These are nearly the same names our theme editor already edits
  (`core/Themes.kt`). Our built-in looks map almost one to one, and this time every role would
  visibly do something, because we draw the whole screen.
- The colour picker (`ThemeUi.kt`, `ColoursUi.kt`).
- The wallpaper editor, with crop, darken and blur (`CarWallpaperEditor.kt`, `core/ImageBlur.kt`).
  The rail-slice workaround is not needed, because the wallpaper can simply be drawn behind
  everything.
- Look files, backups, styles and undo (`core/LookBundle.kt`), minus the parts about Android
  Auto's internals.
- Glyph icons. The pure icon maths in `runtime/GlyphIcons.java` can be reused as an ordinary
  function.
- The updater (`Updater.kt`, `core/UpdateCheck.kt`).
- The release workflow pattern (`.github/workflows/fork-release.yml`). It needs the new
  repository and a new signing key, or the same key if the owner wants it.
- The redesigned settings screens and shared components from `docs/DESIGN_SPEC.md`.

**Does not carry over.**
- The LSPosed module that changes Android Auto (`runtime/`).
- Flag overrides, presets of flags, flag discovery and catalogue scanning.
- The colour-role finder, resource colour hooks and status bar hooks.

None of these are needed, because F-Auto does not use Google's app. The status bar, battery
percentage, clock size and all the fine-grained controls from list 2 become ordinary settings in
our own drawing code. They are easier and more certain there than they ever were in the module.

## 6. Biggest risks and unknowns

1. **Whether the Audi accepts a non-Google phone.**
   - Factory units built on Google's receiver library check the phone's certificate.
   - AARE's table lists a Skoda MIB3 as refusing its own certificate. Audi uses the same family
     of systems.
   - The wireless adapter is believed to pass this check through to the car, not to decide it
     itself. That is an assumption in F-Auto's own notes.
   - If the Audi refuses (the connection log shows "status -3"), the only known way through is
     F-Auto's "Download certificate" button. It fetches Google's certificate and key, which a
     third party extracted from the Android Auto app and published on GitHub. AARE deliberately
     does not ship them, and F-Auto enables the download.
   - Using Google's key this way is against Google's terms and legally grey. Whether to do it is
     the owner's decision alone, and the plan does not assume it.
   - It also touches the owner's rule 2: the download is a network use beyond the updater, though
     only on request.
2. **Whether the owner's adapter cooperates.** The model is not known. F-Auto documents one
   adapter that gets stuck until it is re-paired from scratch.
3. **The knob while the phone is locked on Android 16** (section 2). Not known.
4. **Google's own apps.**
   - Google Maps works only through the mirrored route.
   - YouTube Music does not work as a car media app.
   - YouTube video works only mirrored, and only once knob input reaches it.
5. **Maintenance.** We would be maintaining a fork of a fast-moving project, rather than a small
   module. That means following AARE's changes, which is real ongoing work.

**What the owner should try first, parked, with F-Auto exactly as released.** The current app
stays installed as the daily driver.

1. Note the adapter's make and model, and its Bluetooth name.
2. Install F-Auto's release APK.
3. Turn off "Start Android Auto wirelessly" for Google's app.
4. Pair the phone with the adapter. If it misbehaves, forget the pairing and pair again from
   scratch.
5. Let it connect, then open *Advanced > Connection log* and send it. The stage it stops at says
   whether the problem is the adapter, the car starting Android Auto, or the certificate.
6. If F-Auto's screen appears on the car:
   - turn the knob and say whether anything visibly moves;
   - press the knob and say whether anything opens;
   - try once with the phone locked and once unlocked.
7. Do **not** press "Download certificate" until the owner has read risk 1 and decided.

## 7. First task list for a second fork

This would be a separate repository with its own release chain. The current app stays the daily
driver. Each step is small and can be checked without the car where possible.

0. **Decision gate (the owner).** Report the result of the parked test above. If the Audi
   refuses the certificate, the owner decides on risk 1 before anything below starts.
1. **Make the fork.**
   - Fork AARE upstream (not F-Auto), then bring in F-Auto's adapter compatibility mode and
     "Connect now" changes, which are small.
   - New app name and package; a credits screen; our release workflow with a new tag scheme.
   - The handoff file and roles copied over.
   - Verify: CI builds and AARE's 165 tests pass, and the first release installs next to the
     current app.
2. **A desktop head unit profile for the Audi.**
   - A script for the PC Tester that runs AARE's head unit as `--res 1280x720 --dpi 213
     --no-touch --headless` with a set knob script, and sends back the screenshots.
   - Verify: the Tester returns pictures of the car screen.
   - This step is what makes everything after it checkable.
3. **Knob pass 1: visible focus everywhere.**
   - A focus ring on every tappable element in the home screen, rail and phone screens, in the
     chosen layout.
   - Verify: a knob script steps through every element, and each screenshot shows exactly one
     ring.
4. **Knob pass 2: no dead ends.**
   - A knob path for every touch-only gesture, and Back always works.
   - Verify: scripted runs from every screen return home using only the knob and Back.
5. **Compact size set for 1200 x 723 at 213 dpi.**
   - Verify: screenshots, then the owner looks parked.
6. **Our look system on the car screen.**
   - Move the theme editor, colour picker and built-in looks in, mapped to the Material roles
     F-Auto draws with.
   - Verify: unit tests on the mapping, and screenshots per look.
7. **Wallpaper behind the whole car screen** (darken and blur from our editor).
   - Verify: screenshots.
8. **Knob control in mirrored apps (YouTube while parked).**
   - Forward knob turns and presses into the virtual display as D-pad keys.
   - Verify: a desktop head unit run with a mirrored app, then the owner tries YouTube parked.
9. **Status bar and fine controls from list 2** (clock size, battery percentage, plate,
   icon sizes) as ordinary settings.
   - Verify: unit tests and screenshots.
10. **Our updater, style files and backup.**
    - Verify: an update from one release to the next on the owner's phone.

Not known until step 0: everything after it depends on the Audi accepting the connection.
