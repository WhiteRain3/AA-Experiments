# ORBIT control hints around the speed circle (4-hints)

The owner wants to always see what each control does. These six options put small hints around ORBIT's speed circle (bottom left), one per pad direction that has an action. A direction with nothing assigned gets nothing on screen. Every hint stays left of the free area (x 288–992, y 43–677), and the only darkening is ORBIT's soft plates behind its own parts. Nothing dims the whole screen while Waze is navigating.

The actions come from MyPlay's button table on `claude/myplay` (MyPlayHomePad.kt, ButtonTable.kt, MyPlayEngine.kt), as relayed by the dispatcher:
- **Pad up** opens Maps (Waze full screen).
- **Pad left and right** share one setting ("side") and open the Dial of worlds.
- **Pad down** does nothing.
- **Centre push** (knob or wheel): 1 press = Music panel (or select), 2 quick = next song, 3 quick = previous song.
- The box sends no holds, so no hold actions are drawn.

| File | Option |
|---|---|
| [4-hints-a-ring.png](4-hints-a-ring.png) | A: a thin hint ring with icon discs, always on |
| [4-hints-b-labels.png](4-hints-b-labels.png) | B: the same ring with words (could be a setting next to A) |
| [4-hints-c-wake.png](4-hints-c-wake.png) | C: hints sleep as faint ticks, wake with words for 4 s on any press or knob turn, then fade (the box can't sense touch, only presses) |
| [4-hints-d-gestures.png](4-hints-d-gestures.png) | D: push gestures as dots (1 Music, 2 next, 3 previous) plus ring mode, where the arrows move the ring and pad right on the song stop = next |
| [4-hints-e-corners.png](4-hints-e-corners.png) | E (optional): the corner buttons by the knob. Only mapped buttons get a hint; "Waze → Home" is drawn dashed and tagged PROPOSAL. Which corners the 2 working buttons sit at is a guess |
| [4-hints-f-remap.png](4-hints-f-remap.png) | F: the hints redraw from the owner's mapping (Music, Maps, Dial, Apps, Calls or nothing) |

To redraw: `node render-hints.js` in this folder (source in `src/hints.js`). The street, song and artist names are the same invented examples as the other ORBIT sheets.
