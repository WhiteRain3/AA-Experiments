// Draws the car-screen layouts. Every picture is built from the same pieces so the three layouts compare fairly.
const W = 1280, H = 720, VISIBLE = 1200;
const pos = r => `left:${r[0]}px;top:${r[1]}px;width:${r[2]}px;height:${r[3]}px`;
const ic = (id, extra = '') => `<svg class="ico" ${extra}><use href="#${id}"/></svg>`;

/* ---------- map (original drawing: grid streets, a park, a river, a route) ---------- */
function mapSVG(cx, cy) {
  const dx = cx - 640, dy = cy - 420;
  let roads = '';
  for (let k = -8; k <= 8; k++) { const x = 640 + k * 130; roads += `<rect x="${x - 5}" y="-900" width="10" height="2600" fill="#1b2231"/>`; }
  for (let k = -9; k <= 9; k++) { const y = 420 + k * 105; roads += `<rect x="-1000" y="${y - 5}" width="3400" height="10" fill="#1b2231"/>`; }
  let blocks = '';
  const parks = [[770, 245, 120, 95], [250, 350, 100, 80], [1030, 490, 105, 75]];
  parks.forEach(p => blocks += `<rect x="${p[0]}" y="${p[1]}" width="${p[2]}" height="${p[3]}" rx="14" fill="#10221d"/>`);
  const river = `<path d="M-700 640 C -200 560, 150 760, 520 640 S 1100 780, 1700 640" fill="none" stroke="#0a1a2c" stroke-width="60"/>`;
  const route = `M640 460 L640 210 L1030 210 L1030 -300`;
  return `<svg class="map" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}">
    <rect width="${W}" height="${H}" fill="#0e121b"/>
    <g transform="translate(${dx} ${dy})">${river}${blocks}${roads}
      <path d="${route}" fill="none" stroke="#06080d" stroke-width="22" stroke-linejoin="round" stroke-linecap="round"/>
      <path d="${route}" fill="none" stroke="#6ee7a8" stroke-width="12" stroke-linejoin="round" stroke-linecap="round"/></g>
    <g transform="translate(${cx} ${cy})"><circle r="40" fill="rgba(110,231,168,.25)"/><circle r="26" fill="#fff"/><path d="M0 -15 L11 12 L0 6 L-11 12z" fill="#0e121b"/></g></svg>`;
}

/* ---------- pieces ---------- */
const turnCard = (r, compact) => `<div class="glass" style="${pos(r)}"><div class="turn"><div class="big">${ic('turn')}</div><div><div class="d">350 m</div><div class="s">Hauptstraße</div></div></div></div>`;
const etaCard = r => `<div class="glass" style="${pos(r)}"><div class="eta">14:32<small>18 min</small></div></div>`;
const statusCard = (r, glass = true) => `<div class="${glass ? 'glass' : ''}" style="${glass ? pos(r) : 'position:absolute;' + pos(r)}"><div class="stat"><span>12:34</span>${ic('signal')}<span class="bt">${ic('battery')}87%</span></div></div>`;
const mediaCompact = (r, focus) => `<div class="glass ${focus ? 'focus' : ''}" style="${pos(r)}"><div class="media"><div class="art">${ic('music')}</div><div style="min-width:0"><div class="t">Night Drive</div><div class="a">Blue Harbor</div><div class="prog"><i></i></div></div></div></div>`;
const mediaExpanded = (r, focusPlay) => `<div class="glass" style="${pos(r)}"><div class="xl"><div class="artbig">${ic('music')}</div><div><div class="t">Night Drive</div><div class="a">Blue Harbor</div><div class="prog" style="height:10px;margin-top:16px"><i></i></div></div><div class="ctrl" style="justify-content:center"><div class="btn">${ic('prev')}</div><div class="btn play ${focusPlay ? 'focus' : ''}">${ic('play')}</div><div class="btn">${ic('next')}</div></div></div></div>`;
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
  let h = mapSVG(cx, cy) + L.parts(s, o.focus !== false);
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
    <div class="cell"><div class="frame">${panel(L, 1, { stops: true })}</div><div class="cap"><span>Knob stops</span>numbers = turn order · ring = focus</div>
      <div class="legend"><span>turn = next stop</span><span>press = open</span><span>back = close</span><span><i style="background:#6ee7a8"></i>focus ring 7 px</span><span><i style="border:2px dashed #7c86a3"></i>reading only, no stop</span></div></div>
    <div class="cell" style="width:330px">${phone(L)}<div class="cap"><span>Edit the picture</span>tap a card, drag it to a dashed slot</div></div>
    <div class="cell"><div class="frame">${shiftPanel(L)}</div><div class="cap"><span>How the route centre moves</span>1 → 2 → 3 as cards appear and grow</div></div>`;
}
