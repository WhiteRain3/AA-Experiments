/* =================================================================
   ORBIT · control hints around the speed circle (4-hints-*.png)
   Actions come from MyPlay's button table (claude/myplay): pad up = Maps, pad left/right ("side", one setting) = Dial,
   pad down = nothing, centre push: 1 = select / Music, 2 quick = next song, 3 quick = previous song. No holds.
   ================================================================= */
const FREE = { x: 288, y: 43, w: 704, h: 634 };
const SC = { x: 140, y: 580, r: 84 };           // speed circle
const HR = 114;                                  // hint ring radius
const ACT = {
  maps: { icon: 'nav', label: 'Maps', col: '#6ee7a8' },
  dial: { icon: 'dial', label: 'Dial', col: '#e8edf8' },
  music: { icon: 'music', label: 'Music', col: '#a78bfa' },
  apps: { icon: 'launcher', label: 'Apps', col: '#ff8fb1' },
  calls: { icon: 'phone', label: 'Calls', col: '#5fd39a' }
};
const ANG = { up: -90, right: 0, down: 90, left: 180 };
const DIALSVG = size => `<svg width="${size}" height="${size}" viewBox="0 0 24 24" style="flex:none"><circle cx="12" cy="12" r="8.5" fill="none" stroke="currentColor" stroke-width="2.4" stroke-dasharray="7.2 1.7"/><circle cx="12" cy="12" r="3" fill="currentColor"/></svg>`;
const ic = (id, size, col) => id === 'dial' ? `<span style="color:${col};display:flex">${DIALSVG(size)}</span>` : S(id, size, `color:${col}`);
const at = (deg, r = HR) => [SC.x + r * Math.cos(deg * Math.PI / 180), SC.y + r * Math.sin(deg * Math.PI / 180)];
const arc = (deg, span, r, col, w) => { const [x1, y1] = at(deg - span / 2, r), [x2, y2] = at(deg + span / 2, r); return `<path d="M${x1} ${y1} A${r} ${r} 0 0 1 ${x2} ${y2}" fill="none" stroke="${col}" stroke-width="${w}" stroke-linecap="round"/>`; };
/* soft plate: a dark halo that hugs one part (ORBIT's "soft" shade; never a full-screen dim) */
const soft = (x, y, rx, ry, a = .78) => box(`left:${x - rx}px;top:${y - ry}px;width:${rx * 2}px;height:${ry * 2}px;border-radius:50%;background:radial-gradient(closest-side,rgba(6,8,13,${a}),rgba(6,8,13,${a * .6}) 60%,transparent)`);
const freeBox = () => box(`left:${FREE.x}px;top:${FREE.y}px;width:${FREE.w}px;height:${FREE.h}px;border:2px dashed rgba(255,255,255,.22);border-radius:10px`, `<span style="position:absolute;left:12px;top:8px;font-size:18px;color:rgba(255,255,255,.4)">free area · Waze only (x 288–992, y 43–677)</span>`);

function hBase(o = {}) {
  let h = mapSVG(640, 400, 'teal');
  h += soft(110, 150, 190, 170) + soft(SC.x, SC.y, 190, 190) + soft(1110, 560, 200, 210) + soft(1100, 70, 150, 90, .6);
  if (o.free !== false) h += freeBox();
  h += box('left:44px;top:46px;width:132px;height:132px;border-radius:50%;background:#6ee7a8;color:#06251a;display:grid;place-items:center', S('turn', 78));
  h += box('left:44px;top:190px;width:240px', '<div class="t" style="font-size:54px;font-weight:800;line-height:1">350 m</div><div class="t" style="font-size:26px;color:#c4cbdc;margin-top:6px">Hauptstraße</div>');
  h += box('left:1000px;top:46px;width:180px;text-align:right', `<div class="t" style="font-size:44px;font-weight:800;line-height:1">12:34</div><div class="t" style="font-size:24px;color:#c4cbdc;margin-top:6px">arrive 14:32</div>`);
  h += box('left:1000px;top:448px;width:190px;text-align:right', `<div class="t" style="font-size:30px;font-weight:800;white-space:nowrap">${o.title || 'Night Drive'}</div><div class="t" style="font-size:22px;color:#c4cbdc">Blue Harbor</div>`);
  const Cc = 2 * Math.PI * 72;
  h += box('left:1042px;top:530px;width:152px;height:152px', `<svg width="152" height="152"><circle cx="76" cy="76" r="72" fill="rgba(8,12,20,.7)" stroke="#2c2646" stroke-width="7"/><circle cx="76" cy="76" r="72" fill="none" stroke="#a78bfa" stroke-width="7" stroke-linecap="round" stroke-dasharray="${Cc * .42} ${Cc}" transform="rotate(-90 76 76)"/></svg>`);
  h += box('left:1054px;top:542px;width:128px;height:128px;border-radius:50%;display:grid;place-items:center;color:#fff;background:' + ART, S('music', 54), o.ringSong ? 'ring' : '');
  /* speed circle */
  h += box(`left:${SC.x - SC.r}px;top:${SC.y - SC.r}px;width:${SC.r * 2}px;height:${SC.r * 2}px;border-radius:50%;background:rgba(8,12,20,.9);border:3px solid rgba(255,255,255,.16);display:flex;flex-direction:column;align-items:center;justify-content:center`,
    `<div style="font-size:64px;font-weight:800;line-height:.95">${o.speed || 48}</div><div style="font-size:20px;color:#aab3c8;margin-top:2px">km/h</div>`);
  return h;
}
/* one hint disc on the ring */
function hChip(dir, act, o = {}) {
  const [x, y] = at(ANG[dir], o.r || HR), d = o.d || 50, A = ACT[act];
  const op = o.op ?? 1;
  let h = box(`left:${x - d / 2}px;top:${y - d / 2}px;width:${d}px;height:${d}px;border-radius:50%;background:rgba(8,12,20,.94);border:${o.dashed ? `3px dashed ${A.col}` : `2.5px solid ${A.col}`};display:grid;place-items:center;opacity:${op}`, ic(A.icon, d * .54, A.col));
  if (o.label) {
    const L = o.labelPos || (dir === 'up' ? 'above' : 'below');
    const ly = L === 'above' ? y - d / 2 - 34 : y + d / 2 + 4;
    h += box(`left:${x - 50}px;top:${ly}px;width:100px;text-align:center;font-size:21px;font-weight:800;color:${A.col};opacity:${op}`, `<span class="t">${o.label === true ? A.label : o.label}</span>`);
  }
  return h;
}
function hRing(map, o = {}) {
  let svg = `<circle cx="${SC.x}" cy="${SC.y}" r="${HR}" fill="none" stroke="rgba(255,255,255,${o.track ?? .1})" stroke-width="3"/>`;
  for (const [dir, act] of Object.entries(map)) if (act) svg += arc(ANG[dir], 46, HR, ACT[act].col, 6);
  let h = `<svg style="position:absolute;left:0;top:0;opacity:${o.op ?? 1}" width="${W}" height="${H}">${svg}</svg>`;
  for (const [dir, act] of Object.entries(map)) if (act) h += hChip(dir, act, { ...o, label: o.labels });
  return h;
}
const DEF = { up: 'maps', left: 'dial', right: 'dial', down: null };
const pips = (n, col) => `<span style="display:inline-flex;gap:3px;margin-right:6px">${Array.from({ length: n }, () => `<i style="display:block;width:10px;height:10px;border-radius:50%;background:${col}"></i>`).join('')}</span>`;
function gestureRow(o = {}) {
  const item = (n, id, lab) => `<span style="display:inline-flex;align-items:center;margin-right:14px">${pips(n, '#e8edf8')}${S(id, 26, 'color:#e8edf8')}${lab ? `<span style="margin-left:5px">${lab}</span>` : ''}</span>`;
  return box(`left:14px;top:${o.y || 682}px;width:280px;font-size:20px;font-weight:700;color:#c4cbdc;display:flex;align-items:center;opacity:${o.op ?? 1}`, `<span class="t" style="display:flex;align-items:center">${item(1, 'music', '')}${item(2, 'next', '')}${item(3, 'prev', '')}</span>`);
}

/* ---------- the six options ---------- */
const optA = () => hBase() + hRing(DEF) + UNUSED;
const optB = () => hBase() + hRing(DEF, { labels: true }) + UNUSED;
const optC = state => hBase() + (state === 'idle' ? hRing(DEF, { op: 0, track: 0 }) + idleTicks() : hRing(DEF, { labels: true, op: state === 'fade' ? .35 : 1 })) + UNUSED;
function idleTicks() { // tiny ticks where hints live, so the owner knows they exist
  let svg = '';
  for (const [dir, act] of Object.entries(DEF)) if (act) svg += arc(ANG[dir], 14, HR, 'rgba(255,255,255,.35)', 5);
  return `<svg style="position:absolute;left:0;top:0" width="${W}" height="${H}">${svg}</svg>`;
}
const optD = () => hBase() + hRing(DEF) + gestureRow() + UNUSED;
function optDring() { // knob turned: ORBIT's ring is on the song stop; the pad now moves the ring
  let h = hBase({ ringSong: true });
  let svg = `<circle cx="${SC.x}" cy="${SC.y}" r="${HR}" fill="none" stroke="rgba(255,255,255,.1)" stroke-width="3"/>`;
  h += `<svg style="position:absolute;left:0;top:0" width="${W}" height="${H}">${svg}</svg>`;
  for (const dir of ['up', 'left', 'right', 'down']) { const [x, y] = at(ANG[dir]); h += box(`left:${x - 22}px;top:${y - 22}px;width:44px;height:44px;border-radius:50%;background:rgba(8,12,20,.94);border:2px solid #8b93a7;display:grid;place-items:center;color:#c4cbdc;font-size:22px;font-weight:800`, { up: '▲', left: '◀', right: '▶', down: '▼' }[dir]); }
  h += box('left:282px;top:684px;font-size:0'); // spacer
  h += box('left:1000px;top:396px;width:190px;text-align:right', `<span class="t" style="display:inline-flex;align-items:center;gap:8px;padding:6px 14px;border-radius:22px;background:rgba(8,12,20,.94);border:2px solid #a78bfa;font-size:20px;font-weight:800;color:#e8edf8">▶ ${S('next', 22, 'color:#a78bfa')} next</span>`);
  return h + UNUSED;
}
function optE() {
  let h = hBase() + hRing(DEF);
  const [x, y] = at(-45, HR + 6);
  h += box(`left:${x - 25}px;top:${y - 25}px;width:50px;height:50px;border-radius:50%;background:rgba(8,12,20,.94);border:3px dashed #6ee7a8;display:grid;place-items:center`, S('home', 26, 'color:#6ee7a8'));
  h += box(`left:${x - 140}px;top:${y - 96}px;width:200px;text-align:right`, `<div class="t" style="font-size:14px;font-weight:800;letter-spacing:.12em;color:#ffc24a">PROPOSAL</div><div class="t" style="font-size:22px;font-weight:800;color:#6ee7a8;white-space:nowrap">Waze → Home</div>`);
  return h + UNUSED;
}
const optF = map => hBase() + hRing(map, { labels: true }) + UNUSED;

/* knob diagram for the corner sheet */
function knobDiagram() {
  return `<svg width="300" height="300" viewBox="0 0 300 300"><rect x="20" y="20" width="260" height="260" rx="40" fill="#10131b" stroke="#2a3144" stroke-width="2"/>
  <circle cx="150" cy="150" r="70" fill="#1a1f2b" stroke="#3a4256" stroke-width="3"/><circle cx="150" cy="150" r="30" fill="#252b3a"/>
  <path d="M150 92l-10 14h20z M150 208l-10-14h20z M92 150l14-10v20z M208 150l-14-10v20z" fill="#8b93a7"/>
  <rect x="200" y="40" width="56" height="40" rx="10" fill="none" stroke="#6ee7a8" stroke-width="3" stroke-dasharray="6 4"/><text x="228" y="66" text-anchor="middle" font-size="14" font-weight="800" fill="#6ee7a8">Home?</text>
  <rect x="44" y="40" width="56" height="40" rx="10" fill="none" stroke="#5b6377" stroke-width="2"/><text x="72" y="66" text-anchor="middle" font-size="11" fill="#8b93a7">no action</text>
  </svg><div style="width:300px;font-size:14px;line-height:1.45;color:#9aa3b8;margin-top:6px">The 2 working corner buttons near the knob. Which two corners they are is drawn as a guess; the hint goes to the matching diagonal on the ring. A button with no action gets no hint on screen.</div>`;
}

const HINTS = {
  a: { file: '4-hints-a-ring', title: 'Hints A · hint ring, icons only',
    idea: 'A thin ring around the speed circle with one small disc for each pad direction that does something: up = Maps, left and right = the Dial of worlds. Down does nothing, so its segment is simply missing. The ring is always there, quiet, and each disc takes the colour of the world it opens.',
    frames: [[optA(), 1, 'Home, Waze navigating. The hints stay left of the free area; only the soft plates darken behind ORBIT\'s parts.']] },
  b: { file: '4-hints-b-labels', title: 'Hints B · ring with words',
    idea: 'Same ring, but every disc carries its word. Words help in the first weeks, icons are enough later; a setting could switch between A and B. Left and right share one setting, so they share one word.',
    frames: [[optB(), 1, 'Labels sit above (up) or under (sides) the disc so nothing reaches into the free area.']] },
  c: { file: '4-hints-c-wake', title: 'Hints C · hints wake up, then fade',
    idea: 'The hints sleep as three faint ticks. Any pad press or knob turn wakes them with words for 4 seconds, then they fade back to ticks. (The box sends presses, not touches, so "touching" the pad cannot be sensed; the first press is the trigger, and it still does its job.)',
    frames: [[optC('idle'), .4, 'Asleep: three faint ticks show where the hints live.'], [optC('awake'), .4, 'A press or turn: hints bloom with words (300 ms).'], [optC('fade'), .4, 'After 4 s they fade (600 ms) back to ticks.']] },
  d: { file: '4-hints-d-gestures', title: 'Hints D · push gestures and ring mode',
    idea: 'The centre push has three meanings, shown as a row of dots under the circle: one press = Music, two quick = next song, three quick = previous song. No holds are shown, because the box sends none. When the knob is turned, ORBIT\'s ring appears on its stops; the hint discs turn grey and become plain arrows (the pad now moves the ring), and on the song stop a small tag says pad right = next song.',
    frames: [[optD(), .6, 'Home: pad hints on the ring, push gestures as dots underneath.'], [optDring(), .6, 'Knob turned, ring on the song stop: arrows move the ring, pad right = next.']] },
  e: { file: '4-hints-e-corners', title: 'Hints E · optional: the two corner buttons',
    idea: 'Only 2 of the 4 corner buttons near the knob are registered, and both are unmapped today. A corner hint is drawn only for a button that has an action, so the picture never shows empty or dead buttons. Here one is given a proposal, Waze → Home, drawn with a dashed edge and a PROPOSAL tag until the owner sets it.',
    frames: [[optE(), .8, 'The proposal sits diagonally on the ring, matching where the button is on the knob.']], side: knobDiagram() },
  f: { file: '4-hints-f-remap', title: 'Hints F · the hints follow the owner\'s mapping',
    idea: 'Each pad direction can be set to Music, Maps, Dial, Apps, Calls or nothing. The ring redraws from the setting: a direction set to nothing loses its disc and its arc, so the owner always sees exactly what each direction does.',
    frames: [[optF({ up: 'calls', left: null, right: null, down: 'music' }), .6, 'Example 1: up = Calls, down = Music, sides = nothing.'], [optF({ up: 'maps', left: 'apps', right: 'apps', down: 'calls' }), .6, 'Example 2: up = Maps, sides = Apps, down = Calls.']] }
};
function buildHints(id) {
  const P = HINTS[id];
  document.getElementById('title').textContent = P.title;
  document.getElementById('idea').textContent = P.idea;
  const cells = P.frames.map(f => `<div style="flex:none">${frameOf(f[0], f[2] === undefined ? f[1] : f[1], '#6ee7a8')}<div class="cap">${f[2]}</div></div>`).join('');
  document.getElementById('body').innerHTML = `<div class="row" style="flex-wrap:wrap">${cells}${P.side ? `<div style="flex:none">${P.side}</div>` : ''}</div>`;
}
