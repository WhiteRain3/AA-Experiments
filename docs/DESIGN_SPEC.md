# Manager app redesign spec

Written by the Design reviewer session on 2026-11-18 (read from the branch at 4824354, wr31),
after the owner said: "the design of the app is very weird, why even have those experimental
(which all of them do work) separate? I believe there are some choices that are duplicating. Too
much text for no reason, no clear distinction. I have to read a wall of text to understand. you
could move them under some question mark (a tip)."

Layout and wording only. No setter, key or stored value changes.

## The four rules

- **R1.** No "(experimental)" anywhere. Pages group by what a setting changes. Honest caveats
  ("Not tried on a car yet", unit unknown, may do nothing) stay, but only inside tips (R3).
- **R2.** One home per setting (see "Duplicates and the single home").
- **R3.** No paragraph on a page. A page is a header (title plus the one-line
  `FeaturePage.subtitle`), then sections. All explanation lives in a "?" tip.
- **R4.** One save pattern, one reset pattern (below).

## Shared components (build once, in AppShellUi.kt; every page uses only these)

- **A. TipIcon(title, text).** A 24dp "?" icon inside a 48dp touch target. Tap opens a
  ModalBottomSheet: title (titleMedium), text (bodyMedium, at most about three short sentences),
  one "Got it" text button. Used at section level (beside the heading) and at row level (right
  after the label). Replaces HowThisWorks (delete it once every page is moved). Caveat
  convention: the useful sentence first, the caveat second, e.g. "Hides the app names under the
  launcher icons. Not tried on a car yet: the rows may close up or keep a gap." Do not delete a
  caveat, move it.
- **B. SettingsSection(title, tip?, content).** Heading (titleSmall, bold, primary colour) with
  an optional TipIcon on the right, then one card (surfaceContainer, 22dp corners as
  FeatureTile) holding rows separated by thin dividers. Section spacing 20dp, row minimum
  height 56dp, 16dp card padding. No text inside a card except row labels, values and support
  lines (at most one short line, only for live state such as "Not in this Android Auto
  version").
- **C. Row types**, all the same height and left edge:
  - SwitchRow(label, tip?, checked, onChange). Label left, Switch right, whole row toggleable.
  - ColourRow(label, tip?, colour?, onPick, onReset). Swatch left (40dp), label, value on the
    right ("#RRGGBB" or "Default" in onSurfaceVariant), then the reset icon if a value is set.
  - SliderRow(label, valueText, tip?, value, range, onChangeFinished, onReset). Label left and
    value right on one line, slider below, reset icon beside the value if not default.
  - LinkRow(label, state text, chevron) for opening a sub-screen or dialog.
- **D. ResetIcon.** A 48dp icon button (Icons.Default.Restore) shown only when the row differs
  from default. Content description "Back to default". One word everywhere: "Default" (never
  "Android Auto's own", "Remove", "Turn off", "Remove colours", "Clear", "Re-scan" for
  resetting).
- **E. PageMenu.** A MoreVert menu in FeaturePageScaffold's header, per page, with "Reset
  <page> to default" (confirm dialog "Put everything on this page back to default?" Reset /
  Cancel) plus any page-specific items (e.g. "Scan again" on Colours). The only place a
  page-wide reset lives.
- **F. PreviewCard** (top of a page). Rounded card, 16:9 or purpose-built, showing the current
  saved result (CarScreenMock for colours, wallpaper thumbnail, glyph sample row, status bar
  sample). Never contains text beyond a one-line state ("3 colours set", "Custom image").
- **G.** Keep ChangesWaitingBanner as is (the single "Changes saved. Restart Android Auto to
  see them" plus Restart). It is the only restart prompt. Remove "Changes show after it
  restarts" wording from every page.

## The one save and reset pattern

- Switch, slider (on release), colour pick (on "Use colour"): applied immediately through the
  existing setter, then the banner appears. No Save, Apply, Update, Turn on or Turn off buttons
  on these pages. Status bar rebuilds its Builder from the current state on each change (always
  start from `Config.toBuilder()`).
- Exceptions that keep an explicit Cancel/Apply because they are draft editors with a preview:
  the Theme editor ("Apply to car"), the Wallpaper editor ("Save"), and the small per-app icon
  dialog. Everything else is immediate.
- Reset: ResetIcon per row; "Reset <page> to default" in PageMenu. Reset writes exactly what
  "Turn off" / "Android Auto's own" / "Remove" write today (null or empty).
- If an immediate write cannot be made safe for a page (for example a half-valid combination),
  say so and keep that one page's Save, but still use the same wording ("Save") and the same
  reset icons.
- Undo: `model.undoThen` / `canUndo` keep working; wrap immediate writes in undoThen where they
  already are. Do not add a new undo entry per slider tick (one per release).

## Look tab

Five rows plus the keep/share row: Colours, Wallpaper, App icons, Status bar, then "Share and
keep: Style packs and backup". Launcher is removed as a page (merged into App icons). The intro
paragraph goes; header subtitle is "How the car screen looks". Each tile shows one thumbnail
(colour strip / wallpaper image / glyph / mini bar) and a one-word state: "Default", "3
colours", "On".

## Page: Colours (was "Themes and colours"; FeaturePage.THEMES)

Title "Colours", subtitle "Recolour the car screen". Replaces ThemeColorsCard + StylesCard +
ExtraColorsCard + MapBorderCard (four stacked cards). Sections in order:

1. **PreviewCard**: CarScreenMock of the saved colours. Under it one line: active theme name or
   "N colours set" / "Default". Two buttons: [Edit colours] (opens ThemeEditor) and a text
   button [Default] (today's "Remove colours", with a confirm). If the colour roles have not
   been found yet (roleClass == null), the preview card is replaced by an empty-state card:
   "Colours need a one-time scan" + [Scan] + TipIcon ("Reads the installed Android Auto to find
   its colour slots. Takes a little while."). "Scan again" moves to PageMenu.
2. **Styles** (tip: today's StylesCard paragraph in full: what a style sets, what stays, that
   the status bar and extra-colour parts are not yet confirmed, that Undo is on Home).
   Horizontal scroll row of style cards (mock + name + [Apply]); description line only if
   short. Built-in styles live only here.
3. **Text and backgrounds** (was "More colours (experimental)"):
   - ColourRow "Labels and rail icons" [tip: a fixed list Android Auto reads for app labels,
     the clock and the rail's own icons; used in many places so other text may change; not
     tried on a car yet].
   - ColourRow "Launcher background" [tip: not tried on a car yet].
   - ColourRow "Knob focus ring" [tip: a guess; Android Auto may work its colour out from the
     theme's Primary colour, in which case this does nothing; if so change Primary in the
     theme].
   - Immediate on pick; ResetIcon per row (replaces the "Android Auto's own" buttons and Save).
4. **Map border**: ColourRow "Border colour" (MAP_FOCUS, was "Focus border around the map")
   and SliderRow "Border width" (value text "Default" or the number; range
   MapBorderFlag.MIN..MAX; write on release via model.setMapBorderWidth; reset via
   setMapBorderWidth(null)). Section tip: width unit not known (may not be dp), not tried on a
   car yet, needs a restart. If status is MISSING / NOT_INTEGER / UNKNOWN: show the width row
   disabled with a single support line ("Not in this Android Auto version" / "Can't be changed
   in this version" / "Checking…"). Removed: "Now: 2", the second "Width: 2" label, the Apply
   button, the paragraph, "(experimental)".

**Theme editor** (stays a draft editor):

- Tabs become "Start", "Colours", "Keep".
- Start: "Start from one colour" (base colour + Dark/Light + Generate, "Use my wallpaper's
  colour") and "My themes" (saved themes only: name, four-swatch strip, Load, Delete). Remove
  the built-in list from the editor (it duplicates Styles on the page). To edit a built-in:
  apply the style on the page, then Edit colours.
- Colours: filter field; "These change the car screen" group first, "Not seen in use yet"
  second; the explanatory line becomes a TipIcon on the group heading. Hints (Themes.hint) show
  as the row's second line only for the first group; for the second group they go in the row
  tip.
- Keep: name field + [Keep]; Export / Import / Copy / Paste become LinkRows or a "Share"
  overflow, not four outlined buttons.
- The top intro paragraph goes (tip on the title).

## Page: Wallpaper (FeaturePage.WALLPAPER)

Subtitle "Your own image on the car screen".

1. **PreviewCard**: thumbnail of the car-screen composition with the rail strip shaded, or an
   empty-state tile "Choose an image" (dashed border, tappable) when nothing is set. Under it
   one line "Custom image" / "Not set". Buttons: [Edit] primary if an image exists; [Choose
   image] / [New image]; text button [Default] (= Remove). Replaces the three equal buttons.
2. **Rail strip**: ColourRow "Strip colour", SliderRow "Wallpaper strength" (percent).
   Immediate (replaces RailLookCard's Save). Tip: today's RailLookCard paragraph plus its
   HowThisWorks text (starting point is 80% over dark grey; a colour without a whole-screen
   wallpaper paints the strip in that colour; not tried on a car yet).

Section tip on the "Car wallpaper" heading: the "Use phone's wallpaper" requirement and the
not-tried-on-every-car caveat.

**Wallpaper editor** (draft editor, keeps Cancel/Save): order = preview, Fill/Fit/Centre/Rotate,
Darken, Blur, Cover (chips shortened to "Whole screen" / "Main area"), then a collapsed
"Advanced" section: Rail separation (thickness + colour chips + one swatch), Rail alignment
(and draw the nudge in the preview), Car screen size (prefill 1200x723 and rail 107 left;
remove the sentence about the 800x480 desktop head unit; put "Your car's values" in a tip).
Move all grey help lines into tips.

## Page: App icons (FeaturePage.ICONS). Absorbs Launcher

Subtitle "Icons on the car screen".

1. **PreviewCard**: a row of five sample glyphs in the chosen colours (or the normal icon
   placeholder when off).
2. **Glyph icons**: SwitchRow "Single-colour icons" (on = setGlyphIcons(colors, size); off =
   setGlyphIcons(null); keep colour and size state locally while off). Tip: today's intro line
   + "Apps without a themed icon keep their normal one". When on: ColourRow "Icon colour",
   ColourRow "Background colour" [tip: opacity 0% shows the glyph alone], SliderRow "Figure
   size" [tip: how big the figure is inside its plate; not tried on a car yet: above 100% parts
   may be cut]. Immediate; no Update / Turn on / Turn off.
3. **Single apps**: rows = app name + choice text ("Its own icon", "Glyph", "Glyph, own
   colours") with chevron (tap = existing edit dialog) and ResetIcon (= today's "Remove"); last
   row LinkRow "Add an app". Section tip: today's HowThisWorks text for PerAppIconsCard (works
   with glyphs on or off; which icons are covered; phone/calendar/settings shortcuts belong to
   the phone's apps; not tried on a car; restart).
4. **Launcher**: SwitchRow "Show app names" (inverted from hideNames: on = names shown; off
   writes LauncherTweaks hideNames=true with the same encode). Tip: today's LauncherCard line +
   caveat (names hidden, not removed; rows may keep a gap; not tried on a car).

## Page: Status bar (FeaturePage.STATUS_BAR)

Subtitle "Clock, signal and battery". One PreviewCard at the top (a mock bar showing clock,
signal, battery with the current settings; build later if costly). Then sections, collapsed by
default except the first:

1. **Show**: five SwitchRows, label = the part ("Clock", "Signal", "Battery", "Do not disturb
   icon", "Notification count"). Switch on = shown (inverted from the stored hide set; store
   the complement). Removes the "Hide" heading and the "Hide: X" wording.
2. **Clock**: SliderRow "Size" (percent), SwitchRow "Bold", ColourRow "Colour".
3. **Battery**: SwitchRow "Show as a percentage". When on: SliderRow "Number size" (percent of
   the clock), ColourRow "Number colour", SwitchRow "Colour by level"; when that is on, a
   nested group "Levels" with SliderRow "High from" (%), ColourRow "High", ColourRow "In
   between" (value "Number colour" when default), SliderRow "Low below" (%), ColourRow "Low",
   ColourRow "Charging". Tip on the heading: the number is the one status bar change that
   updates while Android Auto runs.
4. **Icons**: SliderRow "Size", ColourRow "Colour" (signal and battery icons). Tip: not known
   whether a larger row is cut off by the background or touches the clock.
5. **Background**: SwitchRow "Show background" (inverted from noPlate: on = shown). When on:
   ColourRow "Colour" (tip: lower opacity in the picker for see-through), SliderRow "Corner
   roundness" (0 = square; disabled until a colour is set, with the single support line "Pick a
   colour first"; tip: not tried on a car; Android Auto may still draw its own outline in the
   old shape).

The page-level caveat (the day/night repaint note and restart) is a TipIcon on the page header,
not a paragraph. Save and "Turn off" are replaced by immediate writes plus PageMenu "Reset
status bar to default" (= setStatusBarConfig(null)).

## Home (HomeScreen)

Order: SafeModeBanner, ChangesWaitingBanner, hero (status + Restart; keep), update tile when
available, Undo tile when available, then one summary card "Your look" and one card "Flags".

- Hero: keep. Add a "Details" chevron label so it is clear the card opens System status. The
  Restart button shows its result (requested / done) in place of silent feedback.
- "Your look" (replaces the 2x2 Look grid): one card with a LinkRow per page, each with the
  page thumbnail and state: Colours, Wallpaper, App icons, Status bar.
- "Flags" card: LinkRows "Browse flags" (state "None changed" / "N changed", opens the Flags
  tab), "Quick switches" (state: how many of the two are on), "Presets" (state: count).
- Delete the "More > System status" tile (the hero already opens it; Settings lists it too).
- No paragraph text on Home.

## Flags side

- **Quick switches** (subtitle "Several flags behind one switch"): one SettingsSection "Quick
  switches"; each FlagSwitchRow becomes a SwitchRow with the existing title and its note moved
  into the row's TipIcon (parked apps note; sharper picture note, including "turning it off
  puts your earlier values back" and the not-known caveats). The red "Not in this Android Auto
  version: ..." line stays as the row's single support line (it is live state). Section tip:
  "These use the normal flag overrides, so they also show in Flags." Their single home is this
  page; do not add them to the Settings tab.
- **Presets**: SettingsSection "Saved presets": each preset is a row (name, "N overrides ·
  active now" or "N changes if applied", [Apply], overflow Delete). Heading tip: "Applying a
  preset replaces the current set; experiments it does not mention go back to default." Below:
  LinkRows "Save current as preset", "Export", "Import". Empty state: "No presets yet" as the
  section's only row. The intro paragraph goes. Single entry point: the Home Flags card (remove
  the Presets row from Settings).
- **Restart** (FeaturePage.CONTROLS, title "Restart", subtitle "When Android Auto restarts"):
  SettingsSection "Restart": SwitchRow "Restart after a change" (tip: restarts Android Auto
  each time an override or preset is applied; it closes on the car screen, so use while parked;
  only reacts while it is running); SwitchRow "Desktop Head Unit mode" (tip: for testing on a
  PC only; shows the head unit server button; leave off for the car). Remove the Restart and
  "Head unit server" buttons from this page (Restart is on Home and in the banner; keep a "Head
  unit server" LinkRow only when DHU mode is on).

## Settings side

- **Settings tab**: keeps its row list; remove the Presets row; Updates, Restart, System
  status, Screen layout capture, Diagnostics, Backup & recovery, Media startup stay. Row
  subtitles are one line, state not explanation.
- **Updates**: SettingsSection "This app": row "Installed" with the version; when an update
  exists a PreviewCard-like row ("1.0.0-wrN available, size") with [Download and install] and
  the release notes behind a "What's new" LinkRow (a sheet), not inline. [Check for updates]
  LinkRow with last-checked time as its state ("Up to date, checked 14:02" replaces the
  sentence). SwitchRow "Check when the app opens". The privacy sentence ("Checking contacts
  github.com... nothing about you or the phone is sent") moves to the section TipIcon; keep it
  verbatim, it is an owner rule.
- **Screen layout capture**: one section "Capture": row state "Nothing captured yet" or the
  list as LinkRows (name, size); buttons [Capture now] primary, LinkRows "Save to file",
  "Delete captures". The long paragraph (what is recorded, never text such as messages or song
  titles, the file stays on the phone) becomes the section TipIcon; keep verbatim.
- **System status**: not part of this redesign. Only move inline paragraphs into tips, if any.

## Keep and share

**Style packs and backup**: SettingsSection "Look file": LinkRow "Save look to file", LinkRow
"Load a look" (keeps its confirmation dialog). The intro paragraph (what the file contains)
becomes the section TipIcon. The Load dialog text stays (it is a decision, not decoration) but
is cut to: "This replaces your colours, icon colours, status bar and wallpaper. Themes and
presets in the file are added, presets are not applied. Save your current look first if you
may want it back." Do not drop the facts.

## Duplicates and the single home

1. Built-in themes: Colours page "Styles" and the theme editor's Themes tab -> Colours page
   "Styles" only.
2. Clock colour: Status bar "Colour" (Clock) and Colours "Labels and rail icons" (which also
   touches the clock) -> keep both rows, each with its own honest tip; do not claim which wins
   (open question). Name them so they read as different things: "Clock colour" vs "Labels and
   rail icons".
3. Map border: Colours "Map border" (colour + width) and the raw flag in Flags -> the Colours
   page is the friendly home; the Flags list keeps the raw flag (same stored value).
4. Presets: Home tile, Presets page, Settings row -> Home Flags card + page only.
5. Quick switches: Home tile + page -> Home Flags card + page only (also visible as raw flags).
6. System status: Home hero, Home "More" tile, Settings row -> hero + Settings row.
7. Restart: Home hero button, banner button, Restart page buttons -> hero + banner. The Restart
   page keeps behaviours only.
8. Glyph colours: Icons page, and themes carry glyphColors -> the Icons page is where you edit;
   saved themes only store them (no editor UI).
9. Reset wording: "Android Auto's own", "Default", "Remove", "Turn off", "Remove colours",
   "Clear all" -> ResetIcon + "Default" + PageMenu reset.
10. Save wording: Save / Apply / Update / Turn on / Apply to car / Keep -> immediate; only the
    two editors keep an explicit button.

## Build order

Each piece is reviewable on its own; build and tests pass before each commit; no version bump.

1. Shared components in AppShellUi.kt: TipIcon (+ bottom sheet), SettingsSection, SwitchRow,
   ColourRow, SliderRow, LinkRow, ResetIcon, PageMenu in FeaturePageScaffold. No page changes
   yet except replacing HowThisWorks with TipIcon internally.
2. Colours page (the page the owner was looking at): one page from four cards; Styles; "Text
   and backgrounds"; "Map border"; remove "(experimental)"; immediate writes; remove the
   built-in list from the theme editor and rename its tabs.
3. Status bar page: sections, inverted Show / Background switches, immediate writes through
   Config.toBuilder(), PageMenu reset. (It has about 17 state variables; keep them but move the
   Save logic into a single apply() called on each change; sliders on release.)
4. App icons page: glyph switch + rows + Single apps + merged Launcher; delete the LAUNCHER
   FeaturePage and its Look tile.
5. Wallpaper page: PreviewCard + Rail strip section, immediate writes; then the wallpaper
   editor reorder + Advanced + 1200x723 defaults.
6. Home: "Your look" summary card + Flags card; remove the duplicate tiles; restart feedback.
7. Look tab tiles with thumbnails and one-word states; the same short names everywhere.
8. Quick switches, Presets, Restart, Updates, Screen layout capture, Look file: section/row
   conversion and moving paragraphs into tips; remove the Presets row from Settings.
9. Polish: colour picker (old vs new swatch, recent colours), status bar mock preview if not
   done earlier, empty states.

## Risks and decisions the worker should not guess

- Immediate apply writes more often than Save did. Keep it to: switch change, slider release,
  colour confirmed. If model.setStatusBarConfig or setResourceColors triggers a heavy re-sync
  per call, debounce 400 ms; do not change what is stored.
- Inverted switches (Show vs Hide, Show app names vs hideNames, Show background vs noPlate):
  the on-screen meaning flips, stored values do not. Add a small unit test for each mapping (a
  pure function in core/).
- A caveat is never deleted. When moving a paragraph into a tip, copy the sentences, trimming
  only repetition ("Restart Android Auto after...", which the banner already says). Anything
  saying "Not tried on a car yet" stays in a tip next to its control.
- Nothing is renamed in storage, setters, or the FlagSwitch titles. Only visible strings
  change.
- Plain English: "colour slot" not "role" in the app; "base colour" not "seed"; "single-colour
  icon" is fine next to "glyph".
- Accessibility: every "?" and reset icon has a content description ("About <label>", "Back to
  default"); touch targets 48dp.
- Not covered: the Flags list, Settings sub-pages (Media startup, Backup & recovery,
  Diagnostics, About) and System status. They are technical screens and were not redesigned.
  The Design reviewer has not seen a screenshot of wr31, only its code; once the owner sends
  screenshots of the rebuilt Colours page, Status bar and Home it will check spacing and
  contrast.
