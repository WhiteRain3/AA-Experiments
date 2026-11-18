# Test report: 1.0.0-wr37

- **Date:** 2026-11-18, about 20:45 to 21:30
- **Build:** 1.0.0-wr37 (already installed; the app's own check said "Up to date, checked 20:48")
- **Phone:** Android 16, Android Auto 18.0.664264
- **Car screen:** Desktop Head Unit 2.0 with `scripts/dhu-audi.ini` (1280x720, 213 dpi, no touch, rotary). This round the head unit ran on a hidden Windows desktop so its window never showed; screenshots and knob commands worked the same.
- **Tester:** Claude (Tester session). No code was changed.
- **Scope:** the seven checks asked for, then things not covered in the wr33 round.

## Fix first

1. **With all three shown (signal, battery icon, number) everything is shrunk to fit, and at icon size 140% the number cannot be read.** Nothing is cut off any more, but the row is drawn at 52% (icons at 100%) or 63% (icons at 140%) of the size asked for. "Number size: 100% of the clock" then gives a number about a third of the clock's height (`car-02`), and at 140% the "84%" is a red smudge (`car-03`). Also, 140% icons look about the same size as 100% icons in this situation, so the slider seems to do nothing. Log: `STATUS_ICON_ROW_FIT scale 0.5214724` and `0.6296296`.
2. **Two launcher icons changed during the round and did not come back: YT Music and Messenger.** At the start (first restart of the round) YT Music was a filled disc with the play ring cut out, and Messenger a filled speech bubble with the bolt cut out. At the end, with every setting reading the same as at the start (icon colour #FFB000, background Transparent, figure size 100%), YT Music is a small thin ring with a triangle and Messenger is only a small bolt (`car-07`; Messenger described in words because it is in the second row). Two more restarts did not change it. With a see-through background the old filled versions were easier to recognise; a bare bolt is hard to read as Messenger. I do not know why the start was different: see "Things that only showed after a second restart".
3. **The Colours page still moves after a colour pick.** It no longer jumps to the top, but the page slides by 281 px (about a tenth of the screen) every time. Twice out of two tries, same distance. With "Rail and launcher ring" picked near the bottom, the row I had just changed ended up hidden behind the restart banner.
4. **After Undo, the "Applied" mark is on the wrong style.** Apply Neon Sunset, then apply Motorsport, then tap Undo in the message. The message says the look is back to before Motorsport (so Neon Sunset), but Motorsport's button still reads "Applied" and Neon Sunset's reads "Apply".
5. **After "Load a look", the wallpaper editor shows values that were never set.** At the start the editor showed Darken 25%, Blur 15%. I tested with Darken 68%, Blur off. After loading the look back, the car wallpaper looks as it did at the start, but the editor shows Darken 40%, Blur 10%. Pressing Save there would presumably apply 40/10; I did not press it.

## The seven checks

| # | Check | Verdict | What I saw |
|---|---|---|---|
| 1 | Map outline width 2, 6, 12 | Works | About 3, 8 and 16 pixels thick (`car-01`). Colour and rounded corners unchanged. 6 is the same as Android Auto's own thickness. Log: `MAP_BORDER_APPLIED px 3 / 8 / 16`. The very first change of the round needed a second restart (see below). |
| 2 | Signal, battery icon and number together | Works, but small | The whole "84%" is visible (`car-02`). It is much smaller than when shown alone ("Fix first" 1). |
| 3 | Icons 140% with a background colour | Partly | "4G" is not cut (`car-03`). The icons are barely bigger than at 100% and the number is unreadable ("Fix first" 1). |
| 4 | Yellow icon colour reaches the battery icon | Works | Signal and battery icons both yellow (`car-03`). With no icon colour set, the battery icon takes the Labels colour (`car-04`). |
| 5 | Glyph icons with a background colour, whole launcher | Works | All 14 icons: amber figure on a blue plate, none inverted (`car-05`; the other rows were looked at, not published). YT Music's figure is small but clear. See "Fix first" 2 for the same icons on a see-through background. |
| 6 | Colours page does not jump after a pick | Partly | No jump to the top; a 281 px slide remains ("Fix first" 3). On the Status bar page a colour pick did not move the page at all. |
| 7 | Status bar values, Reset, leave and come back | Works | After setting values and reopening the page: all shown correctly (140%, #FFFF00, #0044CC, switches). After "Reset status bar to default": defaults shown, still defaults after reopening. Then last round's bug steps: Bold on, signal off, leave, come back (shown correctly), switch "Do not disturb icon" off, leave, come back, restart: the car kept the bold clock and the hidden signal icon (`car-04`). After "Load a look" the page was also right without closing the app. |

## Also confirmed fixed from the wr33 report

- Colour row labels are on one line ("Labels and rail icons", "Launcher background", "Background colour") (`app-02`).
- The Show switches are lined up (`app-01`).
- "Save look to file" now says "Look saved to aa-look (2).json."
- The look file now carries the map outline width: it went back to "Default" on load.
- The colour picker's hex box no longer gets mangled by word suggestions in my use, and "Use colour" was reachable.
- The Wallpaper page is short and clear now; "Rail strip > Strip colour" shows its value ("Default").
- The "Focus outlines" section names ("Map and card outline", "Rail and launcher ring", "Map outline width") are clear.

## Things not covered last round

| Item | Verdict | What I saw |
|---|---|---|
| Wallpaper editor: Darken, Blur, Cover | Works | Darken 68%, Blur off, "Whole screen": the car showed a darker, sharp image that also runs behind the rail. Log: `CAR_WALLPAPER_SERVED`, `RAIL_WALLPAPER_APPLIED`. |
| Rail strip wallpaper strength | Works at 80% | With "Whole screen" the wallpaper was visible behind the rail. Other strengths not compared. |
| Theme editor: start from one colour | Works, subtle | Green seed, Generate, "Apply to car": the Home/Work icon plates and rail icon plates turned grey-green. Icons and focus ring kept their own colours, as the editor says. |
| Single app: "Its own icon" | Works | Waze showed its real blue icon in the launcher while the others stayed single-colour (`car-06`). The entry was removed again by loading the look. |
| Applying a style: message with Undo | Works | "Motorsport applied." with Undo stayed about 10 seconds, then went. Undo in it worked and listed what it put back. It still says "extra colours" (old name). |
| "Rail and launcher ring" and "Map and card outline" colours | Works | Green ring on the rail, magenta outline on the map at width 12. The focus ring on the media card's play button stayed white: neither setting colours it. |
| Auto-restart off | Works | "Restart automatically after a change" was off this round (not by me). Every change showed "Changes saved. Restart Android Auto to see them." with a Restart button; much nicer for making several changes. |
| Battery number colour by level (mid, low), "keep the red low-battery warning" | Not tested | The phone was at 84%. |
| Quick settings tile | Not tested | It lives in the phone's notification shade, which I am not allowed to open. |
| Crash safe mode | Not tested | I did not want to crash Android Auto on purpose without asking. |
| Quick switches' effect | Cannot tell | Needs the real car. |

## Things that only showed after a second restart

Two things behaved as if the first restart of the round still ran older module code:

- Width 12 set, restart: border still the old 8 px, no `MAP_BORDER_APPLIED` line. Second restart: 16 px, log line present. Later changes (6, 2, 12 again, also with only a 1 second wait) all applied after one restart.
- The launcher at that first restart had the "filled" YT Music and Messenger icons; from later restarts on they were the "figure only" versions.

I cannot tell whether this is the update to wr37 taking effect one restart late, a one-time migration, or something else. **Worth checking on the next update: does the first restart after updating already run the new module?**

## Smaller observations

- Background corner roundness "Default" with a background colour gave square corners on the head unit (`car-03`). I expected Android Auto's own rounded plate. Not compared with other values this round.
- The status bar preview on the page draws the icons as plain yellow blocks, and the sample number is tiny (`app-01`).
- The theme editor is still the old layout: "Apply to car" wraps onto two lines and overlaps the edge of "Cancel" (`app-03`).
- Home showed "Checking… Looking at the installed Android Auto" after most restarts when I looked 15 to 20 seconds later.
- After a restart started from a sub-page, I later found the app on Home. This may be caused by my own tooling (it re-launches the app's main screen to regain focus), so I do not count it as a bug.
- One tap on the map outline width slider while it was half behind the restart banner set it to 0 dp. Probably my tap landing on the edge; not repeated.
- The icon size slider moves 5% per tap near the right end; I needed a drag to reach 140%.

## Log lines

```
MAP_BORDER_APPLIED key=width px=16   (12 dp; px=8 for 6 dp; px=3 for 2 dp)
STATUS_BAR_APPLIED key=layout
STATUS_ICON_ROW_FIT key=status_bar scale=0.5214724   (signal + battery + number, icons 100%)
STATUS_ICON_SIZE key=status_bar
STATUS_BAR_PLATE key=plate class=...CoolwalkCardView
STATUS_ICON_ROW_FIT key=status_bar scale=0.6296296   (same, icons 140%, blue background)
GLYPH_ICON_SERVED key=Resources.getDrawableForDensity
CAR_WALLPAPER_SERVED key=getDrawable as=DRAWABLE
RAIL_WALLPAPER_APPLIED key=rail
```

## What the head unit cannot tell us

- Whether the fitted status bar row is readable on the real screen at arm's length.
- Where the unused strip sits, the real knob, the "parked" signal, the wireless adapter, sound routing.
- Real day and night switching.

## What was changed on the phone, and its state now

- Look saved before testing: `Download/aa-look (2).json` (13.9 MB).
- Look loaded back from that file at the end. Dashboard and launcher match the start (colours, wallpaper, status bar with the red battery number, map outline), **except the YT Music and Messenger icons** ("Fix first" 2).
- The wallpaper editor now shows Darken 40%, Blur 10% ("Fix first" 5); the wallpaper on the car looks as before.
- "Undo last change" on Home now refers to "before loading a look file".
- File added to Downloads: `aa-look (2).json`.
- "Desktop Head Unit mode" and "Restart automatically after a change" were not touched.

## Screenshots

Launcher pictures are cropped to the top row. App pictures have the phone's own status bar cropped off. No dashboard pictures are included because the map shows a location.

| File | Shows |
|---|---|
| `car-01-map-outline-width-2-6-12.png` | Top-left corner of the map with outline width 2, 6 and 12 (enlarged) |
| `car-02-signal-battery-number.png` | Rail, enlarged: signal, battery icon and number together |
| `car-03-icons-140-yellow-blue-background.png` | Rail, enlarged: icons 140%, yellow, blue background; number unreadable |
| `car-04-after-reset-bold-kept.png` | Rail, enlarged: bold clock and hidden signal kept after another change |
| `car-05-glyph-icons-blue-background-top-row.png` | Launcher top row with a blue icon background |
| `car-06-single-app-own-icon.png` | Waze with its own icon |
| `car-07-launcher-top-row-start-and-end.png` | Top row at the start (above) and after loading the look back (below): YT Music differs |
| `app-01-status-bar-page.png` | Status bar page: switches lined up, folded sections with summaries |
| `app-02-colours-labels-and-focus-outlines.png` | Colours page: labels on one line, Focus outlines section |
| `app-03-theme-editor.png` | Theme editor |
