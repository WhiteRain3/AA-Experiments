# Test report: 1.0.0-wr33

- **Date:** 2026-11-18, about 17:00 to 18:15
- **Build:** 1.0.0-wr33 (the round was started on wr32; wr33 was published during it and installed through the app's own updater, as the standing rule says). wr36 was offered at the very end and was **not** installed.
- **Phone:** Android 16, Android Auto 18.0.664264
- **Car screen:** Desktop Head Unit 2.0 with `scripts/dhu-audi.ini` (1280x720, 213 dpi, no touch, rotary). The head unit drew Android Auto 1200 px wide with the unused strip split left and right.
- **Tester:** Claude (Tester session). No code was changed.

## Fix first

1. **The Status bar page shows old values, and the next change then wipes what you set.**
   The page shows the values from when the app was started, not what is stored now.
   How to reproduce:
   1. Open Look > Status bar > Clock. Switch **Bold** on (or move **Size**). The page shows the new value and the car shows it after the restart.
   2. Go back to the Look tab and open Status bar again. Bold shows **off** and Size shows **100%**. The car still shows the bold clock.
   3. Now change anything else on the page (I switched "Do not disturb icon" off). After the restart the car has lost Bold, the clock size and the background colour and roundness.
   - Also seen with the Show switches (Signal and Battery switched off, page reopened, both shown as on again) and with "Show as a percentage".
   - "Refresh status" on Home does not help. Closing the app completely and opening it again does: the page then shows the true values.
   - The same thing happened after "Load a look": the car got my status bar back, the page showed everything as Default until the app was closed and reopened. Screenshot: `app-05`.
2. **Map border width does nothing visible.** At 2 and at 12 the border is the same thickness, about 8 pixels (`car-06`, `car-07`). The colour works.
3. **The battery number is pushed out of view when the signal and battery icons are also shown.** Only a sliver of the number is visible at the right edge (`car-10`). With the signal icon hidden it fits (`car-09`).
4. **Colour row labels are squeezed into a narrow column** and break in the middle of words: "Launc / her / backg / round" on Colours, "Back / groun / d / colou / r" on App icons (`app-01`, `app-02`). It happens on rows that have a "?" icon.
5. **The Show switches on the Status bar page are not lined up.** Each switch sits at a different distance from the right edge (`app-03`).

## Verdicts: the handoff's "not confirmed" list

All of these are head unit results. See "What the head unit cannot tell us" at the end.

| Item | Verdict | What I saw |
|---|---|---|
| Labels and rail icons colour ("More colours") | Works | Magenta: microphone, launcher button, clock, signal and battery icons. Green (my look): launcher labels too. Nothing else on the dashboard cards changed. |
| Launcher background colour | Works | Blue tinted the whole wallpaper behind the app grid (`car-05`). |
| Knob focus ring colour | Works | Orange ring on the rail and in the launcher (`car-03`, `car-05`). It is not worked out from Primary. It does **not** colour the dashboard's focus (see next row). |
| Map border colour | Works | Red border around the map when the knob is on it (`car-06`). It also colours the focus outline on the media card (`car-08`). |
| Map border width | Does not work | Same thickness at 2 and 12. |
| Status bar background colour | Works | Solid blue (`car-04`). |
| Status bar corner roundness | Works | 0 gave square corners; no old outline was drawn on top. |
| "Show background" off / reset | Not tested as a switch | After "Reset status bar to default" I could not see a background on the red rail; I did not check this on a dark rail. |
| Clock size and Bold | Works | 140% and bold fit inside the background and inside the 107 px rail. They stayed after a day/night switch. |
| Clock colour | Works | Cyan clock while "Labels and rail icons" was magenta: **the Status bar clock colour wins.** |
| Status bar icon size | Partly | 140% works. With a background set, the "4G" is cut off at the left edge (`car-04`). Without a background it is not cut. |
| Status bar icons colour | Partly | The signal icon turned yellow. The battery icon stayed in the labels colour (magenta). |
| Hide signal / battery (Show switches) | Works | Icons hidden and shown as set, apart from the page problem in "Fix first" 1. |
| Battery as a number | Partly | Works when the signal icon is hidden. Cut off when signal and battery icons are both shown. |
| Battery number coloured by level | Works | 95% showed in the "High" green (`car-09`). Mid and low levels not tested (the phone was at 95%). |
| Rail strip colour | Works | Solid red rail on the dashboard and in the launcher. |
| Rail strip wallpaper strength | Cannot tell | The rail was a solid colour at 25%; I did not test with the wallpaper set to cover the whole screen. |
| Glyph figure size | Partly | 150% works, and as the tip warns, figures are cut off by the round plate (`car-05`). Small sizes not tested. |
| Launcher "Show app names" off | Works | Names gone, rows close up with no gap, four rows fit instead of three and a bit. The focus box shrinks to fit. |
| Style packs and backup (look file) | Works, with gaps | Save and Load both worked; the car screen matched the start afterwards (`car-12`, `car-13`). Not carried by the file: map border width (I set it back to 2 by hand). Glyph size and app names **were** back to my values after loading. |
| Styles: Apply and Undo | Works | "Neon Sunset" applied in one tap with no question asked. Undo on Home put colours, extra colours and icon colours back exactly. |
| Reset colours to default (menu) | Works | Everything on the page went to Default; the border width stayed, and the dialog says so. |
| Reset status bar to default (menu) | Works | Car went back to Android Auto's own status bar. |
| Day and night | Works on the head unit | `day` and `night` made no visible difference to any colour I had set, in the launcher or on the dashboard. Nothing was repainted. |
| In-app update | Works | wr32 to wr33: offered on Home, downloaded, installed with one system prompt. |
| Restart button | Works | Android Auto restarted every time. |
| Crash safe mode, quick settings tile, wallpaper darken/blur/editor, seed colour, theme copy/paste, single-app icons, Quick switches' effect | Not tested this round | |

## Other things that changed and maybe should not have

- With an icon background colour set, three launcher icons came out inverted (a coloured figure on a white plate) while the rest were white on the colour. One of them is YT Music (`car-05`).
- The dashboard focus outline (map and cards) follows "Map border > Border colour", not "Knob focus ring". Two settings for what looks like one thing.

## Reading the pages as a new user

**Colours** (`app-01`)
- Clear overall. The preview does not react to "Text and backgrounds" colours.
- Labels squeezed (Fix first 4).
- "Text and backgrounds" and "Launcher" headings sit tight against their card; headings that have a "?" have more space. Uneven.
- Tips are useful and honest. Several still say "Not tried on a car yet".
- Applying a style replaces 46 colours in one tap without asking. Undo exists, but only on Home.
- After picking a colour the page jumps back towards the top.
- The colour picker's hex box uses the normal keyboard with word suggestions; a number-and-letters field without suggestions would be safer. With the keyboard open, "Use colour" is hidden behind it.

**Wallpaper** (`app-04`)
- Still the old layout: two paragraphs, a "?" sitting alone on its own line twice, a Save button, "New image" wrapping onto two lines.
- The rail strip colour row shows no value, so you cannot read which colour is set.
- The Look tab calls it "Car wallpaper", Home calls it "Wallpaper".

**App icons** (`app-02`)
- Clear. The preview always shows dark plates, also when the background is see-through.
- Label squeezed on "Background colour".

**Status bar** (`app-03`, `app-05`)
- Switches not lined up (Fix first 5); stale values (Fix first 1).
- "Battery" under Show and "Show as a percentage" under Battery are confusing together: I could not tell from the page whether the number replaces the icon or sits next to it.
- The preview bar showed a red dot and a sample "74%" that I could not relate to my settings.
- Tips are useful. The clock colour tip says it is not known which of the two clock colours wins; on the head unit the Status bar one wins.

**Quick switches** (`app-06`)
- Still the old layout with a paragraph under each switch and no "?" tips. Both were on; I left them alone.

**Wording**
- "Override saved and runtime profile published. Android Auto was asked to restart." (after moving the border width) is jargon.
- "Extra colours saved" uses the old name for "Text and backgrounds".
- Home says "Themes"; the Look tab says "Colours" for the same page.
- I saw no "saved" message after "Save look to file" (it may have gone before I looked).

## Smaller observations

- After one restart Home showed "Checking… Looking at the installed Android Auto" for at least two minutes instead of "Ready". Seen once.
- After one restart the module log had `CONTROL_HEAD_UNIT_STARTED` about a dozen times in ten seconds, from five Android Auto processes.
- With "Restart automatically after a change" on, every single colour pick or slider move restarts Android Auto. Setting up a look means many restarts in a row.
- Loading a look that contains a preset you already have did not create a duplicate ("main" was still there once).

## The knob on the head unit

Rotating through the rail and the launcher worked: the ring moved launcher button > microphone > the three app icons > status bar, and item by item through the launcher, in the colour set.

Several times the ring disappeared and the knob stopped doing anything; the layout capture then showed no focused element. Reconnecting the head unit always brought it back. I first blamed status bar settings (icon size, clock size) and then disproved that: it also happened with my own unchanged look. It may be linked to me using the phone at the same time, or to the head unit itself. **I cannot say whether the real car does this.**

## Log lines

I did not collect per-feature log events this round; the phone's log had been cleared by the time I went back for them. The only lines noted are the repeated `CONTROL_HEAD_UNIT_STARTED` above. For the border width, the next round should read the log right after changing it.

## What the head unit cannot tell us

- Anything about the wireless adapter or the "parked" signal (Quick switches).
- Where the unused strip sits: the head unit centres it, the car has it on the right.
- How the real knob behaves, including the focus loss above.
- Sound routing.
- Real night driving: the head unit's `day`/`night` command is a stand-in for the car's sensor.

## What was changed on the phone, and its state now

- Look saved before testing: `Download/aa-look (1).json` (13.9 MB).
- Look loaded back from that file at the end. Car dashboard and launcher match the screenshots from the start. Map border width set back to 2 by hand.
- "Undo last change" on Home now says "before loading a look file". Pressing it would bring back my test colours. The Undo that was there before the round ("before applying a theme") is gone.
- Files added to Downloads: `aa-look (1).json`, `aa-screen-layout (2).txt`.
- "Desktop Head Unit mode" and "Restart automatically after a change" were already on and were not touched.

## Screenshots

Dashboard pictures have the map area covered, because it shows a location. Launcher pictures are cropped to the top row. App pictures have the phone's own status bar cropped off.

| File | Shows |
|---|---|
| `car-01-start-dashboard.png` | Dashboard before any change |
| `car-02-start-launcher-top-row.png` | Launcher before any change |
| `car-03-test-dashboard.png` | Test values: red rail, blue square status bar, cyan bold clock, magenta rail icons, orange ring |
| `car-04-test-rail-zoom.png` | Rail enlarged: "4G" cut off, battery icon not yellow, figures cut by the plates |
| `car-05-test-launcher-top-row.png` | Launcher: names hidden, blue tint, orange ring, 150% figures, one inverted icon |
| `car-06-map-border-width-12.png` | Map border, red, width 12 |
| `car-07-map-border-width-2.png` | Map border, white, width 2: same thickness |
| `car-08-card-focus-red.png` | Media card focus outline takes the map border colour |
| `car-09-battery-colour-by-level.png` | Battery number in the "High" green |
| `car-10-battery-number-cut-off.png` | Battery number pushed out when both icons are shown |
| `car-11-restored-status-bar.png` | Status bar after loading the look back |
| `car-12-restored-dashboard.png` | Dashboard after loading the look back |
| `car-13-restored-launcher-top-row.png` | Launcher after loading the look back |
| `app-01-colours-labels-squeezed.png` | Colours page |
| `app-02-app-icons-label-squeezed.png` | App icons page |
| `app-03-status-bar-switches-misaligned.png` | Status bar page |
| `app-04-wallpaper-old-layout.png` | Wallpaper page |
| `app-05-status-bar-reopened-shows-defaults.png` | Status bar page reopened: Size 100%, Bold off, while the car showed 140% bold |
| `app-06-quick-switches.png` | Quick switches page |
