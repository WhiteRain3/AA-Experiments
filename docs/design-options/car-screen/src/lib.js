// Draws the car-screen layouts. Every picture is built from the same pieces so the three layouts compare fairly.
const W = 1280, H = 720, VISIBLE = 1200;
let ALPHA = .86;
const pos = r => `left:${r[0]}px;top:${r[1]}px;width:${r[2]}px;height:${r[3]}px`;
const ic = (id, extra = '') => `<svg class="ico" ${extra}><use href="#${id}"/></svg>`;

/* ---------- map (original drawing: grid streets, a park, a river, a route) ---------- */
function mapSVG(cx, cy, light) {
  const P = light ? { bg: '#d9dccf', road: '#f7f4ea', park: '#a9cf9f', river: '#8fbbdc', rc: '#ffffff', rl: '#2f6fe0' } : { bg: '#0e121b', road: '#1b2231', park: '#10221d', river: '#0a1a2c', rc: '#06080d', rl: '#6ee7a8' };
  const dx = cx - 640, dy = cy - 420;
  let roads = '';
  for (let k = -8; k <= 8; k++) { const x = 640 + k * 130; roads += `<rect x="${x - 5}" y="-900" width="10" height="2600" fill="${P.road}"/>`; }
  for (let k = -9; k <= 9; k++) { const y = 420 + k * 105; roads += `<rect x="-1000" y="${y - 5}" width="3400" height="10" fill="${P.road}"/>`; }
  let blocks = '';
  const parks = [[770, 245, 120, 95], [250, 350, 100, 80], [1030, 490, 105, 75]];
  parks.forEach(p => blocks += `<rect x="${p[0]}" y="${p[1]}" width="${p[2]}" height="${p[3]}" rx="14" fill="${P.park}"/>`);
  const river = `<path d="M-700 640 C -200 560, 150 760, 520 640 S 1100 780, 1700 640" fill="none" stroke="${P.river}" stroke-width="60"/>`;
  const route = `M640 460 L640 210 L1030 210 L1030 -300`;
  return `<svg class="map" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}">
    <rect width="${W}" height="${H}" fill="${P.bg}"/>
    <g transform="translate(${dx} ${dy})">${river}${blocks}${roads}
      <path d="${route}" fill="none" stroke="${P.rc}" stroke-width="22" stroke-linejoin="round" stroke-linecap="round"/>
      <path d="${route}" fill="none" stroke="${P.rl}" stroke-width="12" stroke-linejoin="round" stroke-linecap="round"/></g>
    <g transform="translate(${cx} ${cy})"><circle r="40" fill="rgba(110,231,168,.25)"/><circle r="26" fill="#fff"/><path d="M0 -15 L11 12 L0 6 L-11 12z" fill="#0e121b"/></g></svg>`;
}

/* ---------- pieces ---------- */
const turnCard = (r, compact) => `<div class="glass" style="${pos(r)};background:rgba(12,15,22,${ALPHA})"><div class="turn"><div class="big">${ic('turn')}</div><div><div class="d">350 m</div><div class="s">Hauptstraße</div></div></div></div>`;
const etaCard = r => `<div class="glass" style="${pos(r)};background:rgba(12,15,22,${ALPHA})"><div class="eta">14:32<small>18 min</small></div></div>`;
const statusCard = (r, glass = true) => `<div class="${glass ? 'glass' : ''}" style="${glass ? pos(r) + ';background:rgba(12,15,22,' + ALPHA + ')' : 'position:absolute;' + pos(r)}"><div class="stat"><span>12:34</span>${ic('signal')}<span class="bt">${ic('battery')}87%</span></div></div>`;
const mediaCompact = (r, focus) => `<div class="glass ${focus ? 'focus' : ''}" style="${pos(r)};background:rgba(12,15,22,${ALPHA})"><div class="media"><div class="art">${ic('music')}</div><div style="min-width:0"><div class="t">Night Drive</div><div class="a">Blue Harbor</div><div class="prog"><i></i></div></div></div></div>`;
const mediaExpanded = (r, focusPlay) => `<div class="glass" style="${pos(r)};background:rgba(12,15,22,${ALPHA})"><div class="xl"><div class="artbig">${ic('music')}</div><div><div class="t">Night Drive</div><div class="a">Blue Harbor</div><div class="prog" style="height:10px;margin-top:16px"><i></i></div></div><div class="ctrl" style="justify-content:center"><div class="btn">${ic('prev')}</div><div class="btn play ${focusPlay ? 'focus' : ''}">${ic('play')}</div><div class="btn">${ic('next')}</div></div></div></div>`;
const RAIL = ['launcher', 'nav', 'music', 'phone'];
const btn = (id, on, focus) => `<div class="btn ${on ? 'on' : ''} ${focus ? 'focus' : ''}">${ic(id)}</div>`;
const railCol = (r, focusIdx) => `<div class="rail" style="${pos(r)}">${RAIL.map((id, i) => btn(id, i === 1, i === focusIdx)).join('')}</div>`;
const railCentres = x => RAIL.map((_, i) => [x, 191 + 34 + i * 90]);

/* ---------- the three layouts ---------- */
// Each returns the pieces for state s (0 map only, 1 map + media, 2 media expanded).
const LAYOUTS = {
  a: {
    title: 'A · Left rail and a card column',
    lines: [
      ['IDEA', 'A narrow rail on the left; cards stack in a column beside it; clock and battery in a pill top right. The map fills everything. The column grows to the right and the route centre follows it.'],
      ['KNOB', '5 stops: the 4 rail buttons and the media card. Next turn, ETA and the clock are for reading only, so they never take a stop and one turn of the knob stays short.'],
      ['TRADE-OFF', 'Calm and familiar to read, but the column takes the left third whenever media is open, so the map shrinks to about half the screen and the route sits right of centre.']
    ],
    free: [[118, 24, 1072, 676], [468, 24, 722, 676], [638, 24, 552, 676]],
    parts(s, f) {
      const o = [railCol([0, 0, 96, 720], s === 0 ? 1 : -1), statusCard([890, 20, 300, 70]), turnCard([118, 24, 330, 140])];
      if (s === 1) { o.push(etaCard([118, 180, 330, 84]), mediaCompact([118, 280, 330, 132], f)); }
      if (s === 2) o.push(mediaExpanded([118, 180, 500, 516], true));
      return o.join('');
    },
    stops: s => [...railCentres(48), s === 1 ? [118, 280] : [118, 180]].slice(0, s === 0 ? 4 : 5),
    infoRects: s => [[118, 24, 330, 140], [890, 20, 300, 70], ...(s === 1 ? [[118, 180, 330, 84]] : [])],
    ghosts: [[118, 180, 330, 132], [118, 424, 330, 132]], selRect: [118, 280, 330, 132], handles: [[118, 280], [448, 280], [118, 412], [448, 412]], handPos: [380, 360],
    places: [['left:12%;top:14%;width:26%;height:26%'], ['left:12%;top:38%;width:26%;height:26%'], ['left:12%;top:64%;width:26%;height:26%']], placeSel: 1
  },
  b: {
    title: 'B · Bottom dock, cards float at the corners',
    lines: [
      ['IDEA', 'A low dock of big symbols along the bottom with clock and battery on its right. Next turn floats top left. Media pops up bottom right and grows into a tall card on the right, so the route slides left.'],
      ['KNOB', '6 stops: the 5 dock buttons, always in the same place along the bottom edge, and the media card. Next turn, ETA and the clock are for reading only.'],
      ['TRADE-OFF', 'Keeps the map widest, which suits a wide 7-inch screen, but the dock costs 92 px of height all the time and the media card sits far from the dock buttons.']
    ],
    free: [[0, 0, 1200, 628], [0, 0, 830, 628], [0, 0, 720, 628]],
    parts(s, f) {
      const dock = `<div class="bar" style="${pos([0, 628, 1200, 92])};gap:22px;padding-left:28px">${['launcher', 'nav', 'music', 'phone', 'sun'].map((id, i) => btn(id, i === 1, s === 0 && i === 2)).join('')}<div style="margin-left:auto;padding-right:30px;display:flex">${statusCard([0, 0, 10, 10], false).replace('position:absolute;left:0px;top:0px;width:10px;height:10px', 'height:92px')}</div></div>`;
      const o = [dock, turnCard([24, 24, 330, 140])];
      if (s === 1) { o.push(etaCard([24, 536, 300, 80]), mediaCompact([848, 488, 332, 128], f)); }
      if (s === 2) o.push(mediaExpanded([740, 24, 440, 592], true));
      return o.join('');
    },
    stops: s => { const d = [0, 1, 2, 3, 4].map(i => [62 + i * 90, 674]); return s === 0 ? d : [...d, s === 1 ? [848, 488] : [740, 24]]; },
    infoRects: s => [[24, 24, 330, 140], [900, 640, 280, 70], ...(s === 1 ? [[24, 536, 300, 80]] : [])],
    ghosts: [[24, 488, 332, 128], [848, 24, 332, 128]], selRect: [848, 488, 332, 128], handles: [[848, 488], [1180, 488], [848, 616], [1180, 616]], handPos: [1060, 560],
    places: [['left:6%;top:60%;width:34%;height:26%'], ['left:60%;top:60%;width:34%;height:26%'], ['left:60%;top:12%;width:34%;height:26%']], placeSel: 1
  },
  c: {
    title: 'C · Top ribbon, right rail, one stage card',
    lines: [
      ['IDEA', 'Navigation lives in a thin ribbon across the top (next turn, ETA, clock). The rail is on the right edge, next to the unused strip. Only ONE card at a time floats bottom left and grows into a half-screen panel.'],
      ['KNOB', '5 stops: the 4 rail buttons and the stage card. The ribbon is never a stop. The route centre moves up when the stage card is small and right when it grows.'],
      ['TRADE-OFF', 'The most map and the fewest things competing for attention, but only one extra card at a time, and the ribbon is the only place for turn information, so its text must stay large.']
    ],
    free: [[0, 72, 1104, 648], [0, 72, 1104, 440], [660, 72, 436, 648]],
    parts(s, f) {
      const ribbon = `<div class="bar" style="${pos([0, 0, 1104, 76])};background:linear-gradient(180deg,rgba(9,11,17,.94),rgba(9,11,17,.78));padding:0 26px;gap:30px"><div style="display:flex;align-items:center;gap:16px"><div class="big" style="width:54px;height:54px;border-radius:15px;background:#6ee7a8;color:#06251a;display:grid;place-items:center"><svg style="width:40px;height:40px"><use href="#turn"/></svg></div><span style="font-size:40px;font-weight:800">350 m</span><span style="font-size:27px;color:#aab3c8">Hauptstraße</span></div><div style="margin-left:auto;font-size:34px;font-weight:800">14:32 <small style="font-size:24px;color:#aab3c8;font-weight:600">18 min</small></div><div style="display:flex;align-items:center;gap:14px;font-size:32px;font-weight:800"><span>12:34</span><svg style="width:30px;height:30px"><use href="#signal"/></svg><span style="color:#6ee7a8;display:flex;align-items:center;gap:6px"><svg style="width:32px;height:32px"><use href="#battery"/></svg>87%</span></div></div>`;
      const o = [ribbon, railCol([1104, 0, 96, 720], s === 0 ? 1 : -1)];
      if (s === 1) o.push(mediaCompact([24, 528, 380, 168], f));
      if (s === 2) o.push(mediaExpanded([24, 96, 620, 600], true));
      return o.join('');
    },
    stops: s => [...railCentres(1152), ...(s === 0 ? [] : [s === 1 ? [24, 528] : [24, 96]])],
    infoRects: s => [[0, 0, 1104, 76]],
    ghosts: [[716, 528, 380, 168], [24, 96, 380, 168]], selRect: [24, 528, 380, 168], handles: [[24, 528], [404, 528], [24, 696], [404, 696]], handPos: [250, 620],
    places: [['left:6%;top:60%;width:34%;height:26%'], ['left:6%;top:14%;width:34%;height:26%'], ['left:58%;top:60%;width:34%;height:26%']], placeSel: 0
  }
};

/* ---------- one picture of the screen ---------- */
function freeCentre(L, s) { const f = L.free[s]; return [f[0] + f[2] / 2, f[1] + f[3] * 0.64]; }
function panel(L, s, o = {}, scale = 0.4) {
  const [cx, cy] = freeCentre(L, s);
  const f = L.free[s];
  ALPHA = L.alpha || .86;
  let h = mapSVG(cx, cy, o.light) + L.parts(s, o.focus !== false);
  h += `<div class="unused"><i>unused strip, about 80 px</i></div>`;
  if (o.free) h += `<div class="free" style="${pos(f)}"><span>free area for the map</span></div><div class="cross" style="left:${cx}px;top:${cy}px"></div>`;
  if (o.stops) {
    L.stops(s).forEach((p, i) => h += `<div class="stopn" style="left:${p[0]}px;top:${p[1]}px">${i + 1}</div>`);
    L.infoRects(s).forEach(r => h += `<div class="infobox" style="${pos(r)}"></div>`);
  }
  if (o.edit) {
    L.ghosts.forEach(g => h += `<div class="ghost" style="${pos(g)}"></div>`);
    h += `<div class="sel" style="position:absolute;${pos(L.selRect)};border-radius:28px;z-index:5"></div>`;
    L.handles.forEach(p => h += `<div class="handle" style="left:${p[0]}px;top:${p[1]}px"></div>`);
    h += `<div class="hand" style="left:${L.handPos[0]}px;top:${L.handPos[1]}px">${ic('tapx', 'style="width:96px;height:96px"')}</div>`;
  }
  return `<div class="screen" style="transform:scale(${scale})">${h}</div>`;
}

/* ---------- the "route centre moves" diagram ---------- */
function shiftPanel(L, scale = 0.4) {
  const cols = ['#6ee7a8', '#ffc24a', '#6bb8ff'], names = ['1 map only', '2 map + media', '3 card expanded'];
  let h = `<svg class="map" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}"><rect width="${W}" height="${H}" fill="#0e121b"/>`;
  L.free.forEach((f, i) => { h += `<rect x="${f[0] + i * 6}" y="${f[1] + i * 6}" width="${f[2] - 12}" height="${f[3] - 12}" rx="12" fill="${cols[i]}" fill-opacity=".08" stroke="${cols[i]}" stroke-width="5" stroke-dasharray="22 12"/>`; });
  const pts = L.free.map((_, i) => freeCentre(L, i));
  h += `<polyline points="${pts.map(p => p.join(',')).join(' ')}" fill="none" stroke="#fff" stroke-width="5" stroke-dasharray="3 14" stroke-linecap="round"/>`;
  pts.forEach((p, i) => { h += `<circle cx="${p[0]}" cy="${p[1]}" r="26" fill="${cols[i]}" stroke="#0e121b" stroke-width="5"/><text x="${p[0]}" y="${p[1] + 11}" text-anchor="middle" font-size="30" font-weight="800" fill="#06251a" font-family="system-ui">${i + 1}</text>`; });
  h += `</svg><div class="unused"><i>unused strip, about 80 px</i></div>`;
  return `<div class="screen" style="transform:scale(${scale})">${h}</div>`;
}

/* ---------- phone: "Edit the picture" ---------- */
function phone(L) {
  const chips = [['turn', 1], ['clock', 1], ['music', 1], ['battery', 1], ['sun', 0]].map(c => `<div class="chip ${c[1] ? 'on' : ''}"><i></i>${ic(c[0])}</div>`).join('');
  const places = L.places.map((p, i) => `<div class="pl ${i === L.placeSel ? 'on' : ''}"><i style="position:absolute;${p[0]}"></i></div>`).join('');
  return `<div class="phone"><div class="top"><div class="circ">${ic('back')}</div><b>Car screen</b><div class="circ">${ic('q')}</div></div>
    <div class="mini">${panel(L, 1, { edit: true, focus: false }, 298 / 1280)}</div>
    <div class="sec">Cards on the screen</div><div class="chips">${chips}</div>
    <div class="sec">Size</div><div class="sizes"><div class="s"></div><div class="m"></div><div class="l"></div></div>
    <div class="sec">Place</div><div class="places">${places}</div></div>`;
}

function build(id) {
  const L = LAYOUTS[id];
  document.getElementById('title').textContent = L.title;
  document.getElementById('lines').innerHTML = L.lines.map(l => `<div><b>${l[0]}</b>${l[1]}</div>`).join('');
  const names = ['Map only', 'Map + media card', 'Card expanded'];
  document.getElementById('row1').innerHTML = [0, 1, 2].map(s => `<div class="cell"><div class="frame">${panel(L, s, { free: true })}</div><div class="cap"><b>${s + 1}</b><span>${names[s]}</span>dashed mint box = where the map centres the route</div></div>`).join('');
  document.getElementById('row2').innerHTML = `
    <div class="cell"><div class="frame">${panel(L, 1, { stops: true, light: !!L.light })}</div><div class="cap"><span>Knob stops${L.light ? ' on a light map' : ''}</span>numbers = turn order · ring = focus${L.light ? ' · text stays readable' : ''}</div>
      <div class="legend"><span>turn = next stop</span><span>press = open</span><span>back = close</span><span><i style="background:#6ee7a8"></i>focus ring 7 px</span><span><i style="border:2px dashed #7c86a3"></i>reading only, no stop</span></div></div>
    <div class="cell" style="width:330px">${phone(L)}<div class="cap"><span>Edit the picture</span>tap a card, drag it to a dashed slot</div></div>
    <div class="cell"><div class="frame">${shiftPanel(L)}</div><div class="cap"><span>How the route centre moves</span>1 → 2 → 3 as cards appear and grow</div></div>`;
}

/* =====================================================================
   Layouts D, E, F: the map really fills the whole visible screen and the
   bar floats OVER it (see-through scrim, or separate floating pills).
   ===================================================================== */
const pill = (id, cx, cy, w, h, on, focus) => `<div class="pb ${on ? 'on' : ''} ${focus ? 'focus' : ''}" style="left:${cx - w / 2}px;top:${cy - h / 2}px;width:${w}px;height:${h}px;border-radius:${h / 2}px;background:rgba(12,15,22,.72)"><svg class="ico"><use href="#${id}"/></svg></div>`;
const banner = r => `<div class="glass" style="${pos(r)};background:rgba(12,15,22,${ALPHA})"><div class="turn" style="padding:0 24px;gap:20px"><div class="big" style="width:64px;height:64px;border-radius:18px"><svg style="width:46px;height:46px"><use href="#turn"/></svg></div><span class="d" style="font-size:44px;font-weight:800">350 m</span><span class="s" style="margin:0;font-size:27px;color:#aab3c8">Hauptstraße</span></div></div>`;
const colX = (x) => RAIL.map((_, i) => [x, 225 + i * 90]);

LAYOUTS.d = {
  alpha: .6, light: true,
  title: 'D · See-through rail on the left, small cards beside it',
  lines: [
    ['IDEA', 'The map runs edge to edge, under the rail too. The rail has no panel, only a soft dark fade from the left edge so the symbols stay readable; each button is its own see-through disc. Next turn and media sit as small cards at the top-left and bottom-left; the clock and ETA sit at the right.'],
    ['KNOB', '5 stops: the 4 rail discs and the media card. The focus ring has a dark edge on both sides, so it shows on a light map as well as a dark one (see the light-map picture below).'],
    ['TRADE-OFF', 'Closest to what the owner showed: the map is never cut. Text is less safe over a busy map, so cards keep a dark base (60% here) and the rail fade is part of the design, not an option.']
  ],
  free: [[110, 24, 1080, 676], [110, 24, 1080, 520], [638, 24, 552, 676]],
  parts(s, f) {
    const scrim = `<div style="position:absolute;left:0;top:0;width:210px;height:720px;background:linear-gradient(90deg,rgba(6,8,13,.8),rgba(6,8,13,.45) 55%,rgba(6,8,13,0))"></div>`;
    const rail = colX(52).map((p, i) => pill(RAIL[i], p[0], p[1], 76, 76, i === 1, s === 0 && i === 1)).join('');
    const o = [scrim, rail, statusCard([890, 20, 300, 70]), turnCard([118, 24, 330, 140])];
    if (s === 1) o.push(etaCard([880, 630, 310, 70]), mediaCompact([118, 556, 330, 132], f));
    if (s === 2) o.push(mediaExpanded([118, 150, 500, 546], true));
    return o.join('');
  },
  stops: s => [...colX(52), s === 1 ? [118, 556] : [118, 150]].slice(0, s === 0 ? 4 : 5),
  infoRects: s => [[118, 24, 330, 140], [890, 20, 300, 70], ...(s === 1 ? [[880, 630, 310, 70]] : [])],
  ghosts: [[880, 556, 310, 132], [118, 190, 330, 132]], selRect: [118, 556, 330, 132], handles: [[118, 556], [448, 556], [118, 688], [448, 688]], handPos: [330, 640],
  places: [['left:8%;top:62%;width:30%;height:26%'], ['left:62%;top:62%;width:30%;height:26%'], ['left:8%;top:30%;width:30%;height:26%']], placeSel: 0
};

LAYOUTS.e = {
  alpha: .6, light: true,
  title: 'E · Floating pills along the bottom',
  lines: [
    ['IDEA', 'The map fills the screen, including the bottom. The apps are five separate see-through pills floating in a row with the map visible between them. Next turn floats top-left, clock top-right. Media sits bottom-right and rises into a panel on the right.'],
    ['KNOB', '6 stops: the 5 pills, always in the same row along the bottom, and the media card. Pills are wide (96 px) so the ring has room and a knob press is never ambiguous.'],
    ['TRADE-OFF', 'The lightest look and the most visible map, but the pills sit over the part of the map the driver may look at most; a quiet-pill option (shrink to just symbols after a few seconds) would be worth having.']
  ],
  free: [[0, 0, 1200, 620], [0, 0, 845, 620], [0, 0, 720, 620]],
  parts(s, f) {
    const ids = ['launcher', 'nav', 'music', 'phone', 'sun'];
    const row = ids.map((id, i) => pill(id, 600 + (i - 2) * 112, 670, 96, 72, i === 1, s === 0 && i === 2)).join('');
    const o = [row, turnCard([24, 24, 330, 140]), statusCard([890, 20, 300, 70])];
    if (s === 1) o.push(etaCard([24, 630, 290, 72]), mediaCompact([862, 500, 328, 128], f));
    if (s === 2) o.push(mediaExpanded([740, 120, 450, 508], true));
    return o.join('');
  },
  stops: s => { const d = [0, 1, 2, 3, 4].map(i => [600 + (i - 2) * 112, 670]); return s === 0 ? d : [...d, s === 1 ? [862, 500] : [740, 120]]; },
  infoRects: s => [[24, 24, 330, 140], [890, 20, 300, 70], ...(s === 1 ? [[24, 630, 290, 72]] : [])],
  ghosts: [[24, 500, 312, 128], [878, 140, 312, 128]], selRect: [862, 500, 328, 128], handles: [[862, 500], [1190, 500], [862, 628], [1190, 628]], handPos: [1060, 570],
  places: [['left:6%;top:56%;width:34%;height:26%'], ['left:60%;top:56%;width:34%;height:26%'], ['left:60%;top:14%;width:34%;height:26%']], placeSel: 1
};

LAYOUTS.f = {
  alpha: .6, light: true,
  title: 'F · Floating pill column on the right, banner on top',
  lines: [
    ['IDEA', 'The map fills everything. The apps are a column of separate see-through discs on the right edge. Next turn is one wide banner at the top centre, clock top-left, ETA top-right. One card (media) floats bottom-left and grows into a see-through panel from the left.'],
    ['KNOB', '5 stops: the 4 discs on the right and the media card. Banner, clock and ETA are reading only. The discs sit next to the unused strip, so the ring is never cut off by the screen edge.'],
    ['TRADE-OFF', 'Good for a right-hand-drive-style reach and keeps the top banner at eye level, but the map is covered at the top and on both sides; the route centre sits low and right of the middle.']
  ],
  free: [[0, 130, 1090, 590], [0, 130, 1090, 400], [560, 130, 530, 590]],
  parts(s, f) {
    const col = colX(1140).map((p, i) => pill(RAIL[i], p[0], p[1], 76, 76, i === 1, s === 0 && i === 1)).join('');
    const o = [col, banner([330, 20, 470, 96]), statusCard([24, 20, 296, 70]), etaCard([820, 20, 270, 70])];
    if (s === 1) o.push(mediaCompact([24, 540, 350, 150], f));
    if (s === 2) o.push(mediaExpanded([24, 130, 520, 560], true));
    return o.join('');
  },
  stops: s => [...colX(1140), s === 1 ? [24, 540] : [24, 130]].slice(0, s === 0 ? 4 : 5),
  infoRects: s => [[330, 20, 470, 96], [24, 20, 296, 70], [820, 20, 270, 70]],
  ghosts: [[716, 540, 350, 150], [24, 150, 350, 150]], selRect: [24, 540, 350, 150], handles: [[24, 540], [374, 540], [24, 690], [374, 690]], handPos: [250, 620],
  places: [['left:6%;top:60%;width:34%;height:26%'], ['left:52%;top:60%;width:34%;height:26%'], ['left:6%;top:24%;width:34%;height:26%']], placeSel: 0
};

/* =====================================================================
   The customisation sheet: every option, and the values that give each preset
   ===================================================================== */
const PRESETS = {
  a: { name: 'A · Left rail + column', under: 0, style: 'solid', side: 'left', cards: 'column', alpha: 14, set: 'all', hide: 'never' },
  b: { name: 'B · Bottom dock', under: 0, style: 'dock', side: 'bottom', cards: 'corners', alpha: 14, set: 'all', hide: 'never' },
  c: { name: 'C · Top ribbon', under: 0, style: 'ribbon', side: 'top', cards: 'stage', alpha: 14, set: 'one', hide: 'never' },
  d: { name: 'D · See-through rail', under: 1, style: 'glass', side: 'left', cards: 'corners', alpha: 40, set: 'all', hide: 'never' },
  e: { name: 'E · Pills along the bottom', under: 1, style: 'pills', side: 'bottom', cards: 'corners', alpha: 40, set: 'all', hide: 'media' },
  f: { name: 'F · Pill column on the right', under: 1, style: 'pills', side: 'right', cards: 'banner', alpha: 40, set: 'all', hide: 'never' }
};
const STYLE_NAMES = { solid: 'Solid rail', glass: 'See-through rail', pills: 'Floating pills', dock: 'Bottom dock', ribbon: 'Top ribbon' };
const SIDE_ARROW = { left: 'M15 5l-7 7 7 7', right: 'M9 5l7 7-7 7', bottom: 'M5 9l7 7 7-7', top: 'M5 15l7-7 7 7' };
// tiny pictograms, 84 x 48: map = grey ground, the bar in mint
function pictoBar(style, side) {
  const g = `<rect x="1" y="1" width="82" height="46" rx="6" fill="#1b2231" stroke="#2f3650"/>`;
  const m = '#6ee7a8', f = 'fill="' + m + '"';
  const shapes = {
    solid: `<rect x="1" y="1" width="14" height="46" ${f}/>`,
    glass: `<rect x="1" y="1" width="18" height="46" fill="url(#gfade)"/><circle cx="8" cy="12" r="4" ${f}/><circle cx="8" cy="24" r="4" ${f}/><circle cx="8" cy="36" r="4" ${f}/>`,
    pills: `<rect x="14" y="38" width="12" height="7" rx="3.5" ${f}/><rect x="30" y="38" width="12" height="7" rx="3.5" ${f}/><rect x="46" y="38" width="12" height="7" rx="3.5" ${f}/><rect x="62" y="38" width="12" height="7" rx="3.5" ${f}/>`,
    dock: `<rect x="1" y="38" width="82" height="9" ${f}/>`,
    ribbon: `<rect x="1" y="1" width="82" height="9" ${f}/>`
  };
  let body = shapes[style];
  if (style === 'pills' && side === 'right') body = [8, 18, 28, 38].map(y => `<rect x="70" y="${y - 4}" width="8" height="8" rx="4" ${f}/>`).join('');
  return `<svg width="84" height="48" viewBox="0 0 84 48"><defs><linearGradient id="gfade" x1="0" x2="1"><stop offset="0" stop-color="#6ee7a8" stop-opacity=".55"/><stop offset="1" stop-color="#6ee7a8" stop-opacity="0"/></linearGradient></defs>${g}${body}</svg>`;
}
function pictoCards(kind) {
  const g = `<rect x="1" y="1" width="82" height="46" rx="6" fill="#1b2231" stroke="#2f3650"/>`, f = 'fill="#ffc24a"';
  const k = {
    column: `<rect x="20" y="5" width="22" height="10" rx="3" ${f}/><rect x="20" y="18" width="22" height="7" rx="3" ${f}/><rect x="20" y="28" width="22" height="12" rx="3" ${f}/>`,
    corners: `<rect x="5" y="5" width="22" height="10" rx="3" ${f}/><rect x="58" y="32" width="22" height="10" rx="3" ${f}/><rect x="58" y="5" width="22" height="6" rx="3" ${f}/>`,
    stage: `<rect x="5" y="30" width="24" height="12" rx="3" ${f}/>`,
    banner: `<rect x="24" y="4" width="36" height="9" rx="3" ${f}/><rect x="4" y="30" width="24" height="12" rx="3" ${f}/><rect x="5" y="5" width="16" height="7" rx="3" ${f}/><rect x="64" y="5" width="16" height="7" rx="3" ${f}/>`
  };
  return `<svg width="84" height="48" viewBox="0 0 84 48">${g}${k[kind]}</svg>`;
}
const chk = on => on ? `<svg width="34" height="34" viewBox="0 0 24 24"><circle cx="12" cy="12" r="11" fill="#6ee7a8"/><path d="M7 12.5l3.2 3.2L17 9" fill="none" stroke="#06251a" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"/></svg>`
  : `<svg width="34" height="34" viewBox="0 0 24 24"><circle cx="12" cy="12" r="11" fill="#2b3144"/><path d="M8 8l8 8M16 8l-8 8" stroke="#8b93a7" stroke-width="2.4" stroke-linecap="round"/></svg>`;

function buildSheet() {
  document.getElementById('title').textContent = 'One customisation system, six presets';
  document.getElementById('lines').innerHTML = [
    ['IDEA', 'A layout is just a choice of values for eight options. The six pictures are presets: tap one in settings and the options jump to its values, then change any option on its own to make your own layout.'],
    ['HOW IT IS SET', 'Settings uses "Edit the picture": the car screen on the phone, tap the bar or a card and change that one thing. The options are the same eight everywhere.'],
    ['RULES THAT KEEP IT SAFE', 'The knob stays reachable in every combination. A dock can only sit at the bottom and a ribbon only at the top. Cards never sit in the free area. Cards keep a dark base even at the most see-through setting so text stays readable on any map.']
  ].map(l => `<div><b>${l[0]}</b>${l[1]}</div>`).join('');
  const ids = ['a', 'b', 'c', 'd', 'e', 'f'];
  const rows = [
    ['1', 'Map as the background', 'on, or off (plain dark)', () => chk(true)],
    ['2', 'Map goes under the bar', 'under it, or stops at its edge', p => `<div style="display:flex;flex-direction:column;align-items:center;gap:4px">${chk(p.under)}<small>${p.under ? 'under it' : 'beside it'}</small></div>`],
    ['3', 'Bar style', 'solid · see-through · pills · dock · ribbon', p => `<div style="display:flex;flex-direction:column;align-items:center;gap:5px">${pictoBar(p.style, p.side)}<small>${STYLE_NAMES[p.style]}</small></div>`],
    ['4', 'Bar side', 'left · right · bottom · top', p => `<div style="display:flex;flex-direction:column;align-items:center;gap:6px"><svg width="34" height="34" viewBox="0 0 24 24"><path d="${SIDE_ARROW[p.side]}" fill="none" stroke="#6ee7a8" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/></svg><small>${p.side}</small></div>`],
    ['5', 'Where cards sit', 'a slot layout; cards snap into slots', p => `<div style="display:flex;flex-direction:column;align-items:center;gap:5px">${pictoCards(p.cards)}<small>${{ column: 'column by the bar', corners: 'corners', stage: 'one stage card', banner: 'top banner + corner' }[p.cards]}</small></div>`],
    ['6', 'Card see-through amount', '0 to 100%, always a dark base', p => `<div style="width:120px"><div style="display:flex;justify-content:space-between;font-weight:800;font-size:18px"><span>${p.alpha}%</span></div><div style="height:10px;border-radius:5px;background:#2b3144;margin-top:6px;position:relative"><i style="position:absolute;left:0;top:0;bottom:0;width:${p.alpha}%;border-radius:5px;background:#6ee7a8"></i></div></div>`],
    ['7', 'Which cards, in what order', 'turn, ETA, media, clock; drag to order', p => `<div style="display:flex;flex-direction:column;align-items:center;gap:5px"><div style="display:flex;gap:6px;color:#6ee7a8"><svg width="26" height="26"><use href="#turn"/></svg><svg width="26" height="26"><use href="#clock"/></svg><svg width="26" height="26"><use href="#music"/></svg><svg width="26" height="26"><use href="#battery"/></svg></div><small>${p.set === 'one' ? 'one extra at a time' : 'all four'}</small></div>`],
    ['8', 'Hide after a while', 'never, or media/ETA after 5-60 s', p => `<div style="display:flex;flex-direction:column;align-items:center;gap:5px"><svg width="30" height="30"><use href="#${p.hide === 'never' ? 'clock' : 'update'}"/></svg><small>${p.hide === 'never' ? 'never' : 'media after 15 s'}</small></div>`]
  ];
  const thumbs = ids.map(id => `<div class="th"><div class="frame" style="width:208px;height:117px;border-radius:10px">${panel(LAYOUTS[id], 1, {}, 208 / 1280)}</div><div class="nm">${PRESETS[id].name}</div></div>`).join('');
  const body = rows.map(r => `<div class="or"><div class="lb"><b>${r[0]}</b><div><span>${r[1]}</span><small>${r[2]}</small></div></div>${ids.map(id => `<div class="vc">${r[3](PRESETS[id])}</div>`).join('')}</div>`).join('');
  document.getElementById('row1').innerHTML = `<div class="sheet"><div class="or head"><div class="lb"></div>${thumbs}</div>${body}</div>`;
  document.getElementById('row2').innerHTML = `<div style="font-size:14px;color:#9aa3b8;line-height:1.5;max-width:1500px">Presets A, B and C stop the map at the bar's edge (option 2 off) and keep their cards nearly opaque, as in the first round of pictures. D, E and F put the map under everything and float the bar over it. Changing one option on its own, for example turning option 2 on in preset A, gives a layout between the presets; the phone shows the result at once. These values are a proposal to review: the rail's right-hand side placement, the 15-second hide and the 40% default depend on what the owner finds readable in the car.</div>`;
}
