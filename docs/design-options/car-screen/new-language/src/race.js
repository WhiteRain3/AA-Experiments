/* =================================================================
   LANGUAGE 3 · RACE   (a racing-game style HUD around the edges; Waze map untouched in the middle)
   Original drawing: slanted plates, chamfered artwork, italic Inter (free font, OFL).
   ================================================================= */
PAL.race = { bg: '#0a0c10', road: '#181d26', park: '#0f1a18', river: '#0b1622', rc: '#04060a', rl: '#38e1ff', mk: '#fff' };
PAL.raceNight = { bg: '#040507', road: '#0d1016', park: '#08100e', river: '#060b11', rc: '#000', rl: '#1c8ea3', mk: '#cfd6dc' };
const RT = {
  dark: { acc: '#38e1ff', ink: '#f2f6fa', sub: '#9fb0c0', plate: 'rgba(8,11,16,.86)', line: 'rgba(255,255,255,.16)', map: 'race', amber: '#ffb347', mus: '#b58cff' },
  night: { acc: '#22a6bf', ink: '#c3ccd4', sub: '#6c7c89', plate: 'rgba(2,3,5,.9)', line: 'rgba(255,255,255,.08)', map: 'raceNight', amber: '#cf923d', mus: '#7f62b3' }
};
const RW = [['nav', 'Navigate', '#38e1ff', 'Home · 18 min'], ['music', 'Music', '#b58cff', 'Silver Hours'], ['phone', 'Calls', '#4fe3a1', 'Jonas called'], ['launcher', 'Apps', '#ff7aa8', '10 apps'], ['gauge', 'Car', '#e6e9ee', 'Tyres ok · 412 km'], ['gear', 'Settings', '#8a97a6', 'Night look: auto']];
const RART = 'linear-gradient(150deg,#13294a 0%,#1f5f8f 38%,#f0606f 72%,#ffc07a 100%)';
const IT = 'font-family:Inter,system-ui,sans-serif;font-style:italic;font-weight:800;letter-spacing:-.01em;font-variant-numeric:tabular-nums';
const CAPS = 'font-family:Inter,system-ui,sans-serif;font-style:italic;font-weight:800;text-transform:uppercase;letter-spacing:.14em';
const SK = 'skewX(-12deg)';
const cham = c => `clip-path:polygon(${c}px 0,100% 0,100% calc(100% - ${c}px),calc(100% - ${c}px) 100%,0 100%,0 ${c}px)`;
/* a slanted plate: skewed background, upright content */
function pl(x, y, w, h, inner, o = {}) {
  const t = o.t || RT.dark;
  const ring = o.ring ? `<div style="position:absolute;inset:-11px;transform:${SK};border:6px solid ${t.acc};box-shadow:0 0 0 5px #04060a"></div>` : '';
  const edge = o.edge ? `box-shadow:inset 9px 0 0 ${o.edge};` : '';
  return box(`left:${x}px;top:${y}px;width:${w}px;height:${h}px;color:${t.ink}`,
    `${ring}<div style="position:absolute;inset:0;transform:${SK};background:${o.bg || t.plate};border:${o.border || `1px solid ${t.line}`};${edge}"></div><div style="position:absolute;inset:0;display:flex;align-items:center;${o.pad || 'padding:0 26px'};gap:${o.gap ?? 16}px">${inner}</div>`);
}
const chevs = (n, col, size = 22) => `<span style="display:inline-flex;margin-left:-6px">${Array.from({ length: n }, (_, i) => S('chev', size, `color:${col};opacity:${.35 + .3 * i};margin-left:-${size * .45}px`)).join('')}</span>`;
const vign = box('inset:0;background:radial-gradient(ellipse at 0% 0%,rgba(4,6,10,.8),transparent 42%),radial-gradient(ellipse at 100% 0%,rgba(4,6,10,.7),transparent 34%),radial-gradient(ellipse at 0% 100%,rgba(4,6,10,.8),transparent 40%),radial-gradient(ellipse at 100% 100%,rgba(4,6,10,.8),transparent 42%)');
/* generic hazard placeholder: stands for whatever Waze draws on the map (we never cover or restyle it) */
const hazard = (x, y) => box(`left:${x}px;top:${y}px;width:46px;height:46px;border-radius:50%;background:#e9edf2;display:grid;place-items:center;box-shadow:0 2px 8px #000a`, '<svg width="26" height="26" viewBox="0 0 24 24"><path d="M12 3 22 20H2z" fill="#5b6572"/><path d="M12 9v5M12 16.5v.5" stroke="#e9edf2" stroke-width="2.4" stroke-linecap="round"/></svg>');

function rTurn(t, o = {}) {
  let h = pl(36, 30, 540, 160, `<div style="width:118px;height:118px;flex:none;display:grid;place-items:center;color:#05080c;position:relative">
      <div style="position:absolute;inset:0;transform:${SK};background:${t.acc}"></div><div style="position:relative">${S('turn', 78)}</div></div>
      <div><div style="${IT};font-size:92px;line-height:.95">${o.dist || '350'}<span style="font-size:44px;margin-left:6px">m</span></div><div style="${IT};font-weight:700;font-size:32px;color:${t.sub};margin-top:6px">Fernmoor Road</div></div>`, { t, pad: 'padding:0 22px 0 24px', gap: 26 });
  h += pl(60, 202, 330, 50, `<span style="${CAPS};font-size:18px;color:${t.sub}">then</span>${S('turn', 30, `color:${t.ink};transform:scaleX(-1)`)}<span style="${IT};font-size:26px;font-weight:700">Quay Street</span>`, { t, pad: 'padding:0 20px', gap: 12 });
  return h;
}
function rEta(t) {
  let h = pl(820, 30, 344, 120, `<div><div style="${CAPS};font-size:18px;color:${t.acc}">arrive</div><div style="${IT};font-size:62px;line-height:1">14:32</div></div>
      <div style="margin-left:auto;text-align:right;${IT};font-weight:700;font-size:26px;line-height:1.35">18 min<br><span style="color:${t.sub}">9.4 km</span></div>`, { t, pad: 'padding:0 28px 0 30px' });
  h += box(`left:930px;top:160px;width:228px;text-align:right;color:${t.ink};display:flex;justify-content:flex-end;align-items:center;gap:10px`, `<span class="t" style="${IT};font-size:30px">12:34</span><span class="t" style="color:${t.sub};display:flex;gap:6px">${S('signal', 26)}${S('battery', 28)}</span>`);
  return h;
}
/* compact top-right tab for pages: next turn + speed stay visible */
function rMini(t, o = {}) {
  const over = (o.speed || 48) > (o.limit || 50) + 9;
  return pl(764, 30, 400, 84, `${S('turn', 46, `color:${t.acc}`)}<span style="${IT};font-size:42px;white-space:nowrap">350<span style="font-size:24px"> m</span></span>
      <span style="margin-left:auto;${IT};font-size:42px;color:${over ? t.amber : t.ink}">${o.speed || 48}</span><span style="${CAPS};font-size:13px;color:${t.sub};line-height:1.3;white-space:nowrap">km/h<br>hint ${o.limit || 50}</span>`, { t, pad: 'padding:0 24px', gap: 12 });
}
function rSpeed(t, o = {}) {
  const sp = o.speed || 48, lim = o.limit || 50, over = sp > lim + 9;
  const limBox = `<div style="width:108px;flex:none;text-align:center">
      <div style="height:96px;position:relative;display:grid;place-items:center">
        <div style="position:absolute;inset:0;transform:${SK};border:6px solid ${over ? t.amber : t.ink};background:${over ? t.amber : 'transparent'};opacity:${over ? .92 : .9}"></div>
        <div style="position:relative;${IT};font-size:52px;color:${over ? '#1a1006' : t.ink}">${lim}</div></div>
      <div style="${CAPS};font-size:14px;color:${over ? t.amber : t.sub};margin-top:8px;letter-spacing:.1em">limit · hint</div></div>`;
  return pl(36, 512, 392, 176, `<div><div style="${IT};font-size:118px;line-height:.9;color:${over ? t.amber : t.ink}">${sp}</div><div style="${CAPS};font-size:18px;color:${t.sub};margin-top:6px">km/h</div></div><div style="margin-left:auto"></div>${limBox}`, { t, pad: 'padding:0 30px 0 34px' });
}
const segBar = (n, on, col, w = 18) => `<div style="display:flex;gap:5px">${Array.from({ length: n }, (_, i) => `<i style="display:block;width:${w}px;height:8px;transform:${SK};background:${i < on ? col : 'rgba(255,255,255,.16)'}"></i>`).join('')}</div>`;
function rTrack(t, o = {}) {
  const art = `<div style="width:124px;height:124px;flex:none;position:relative">${o.ring ? `<div style="position:absolute;inset:-10px;background:${t.acc};${cham(26)}"></div><div style="position:absolute;inset:-5px;background:#04060a;${cham(24)}"></div>` : ''}<div style="position:absolute;inset:0;background:${RART};${cham(22)};display:grid;place-items:center;color:#fff;opacity:${o.paused ? .45 : 1}">${S('music', 52)}</div></div>`;
  return pl(724, 530, 440, 158, `${art}<div style="min-width:0;flex:1"><div style="${IT};font-size:36px;white-space:nowrap">${o.title || 'Silver Hours'}</div><div style="${IT};font-weight:600;font-size:24px;color:${t.sub};margin:2px 0 14px">${o.paused ? 'paused for call' : (o.artist || 'Lumen Coast')}</div>
      <div style="display:flex;align-items:center;gap:12px">${segBar(8, o.restart ? 0 : 4, t.mus, 15)}<span style="${CAPS};font-size:13px;color:${t.sub};letter-spacing:.08em;white-space:nowrap">hold ▸▸</span></div></div>`, { t, pad: 'padding:0 22px 0 26px', gap: 22 });
}
/* the world gate: six slim slanted ticks on the left edge, current world lit */
function rGate(t, lit = 0) {
  return RW.map((w, i) => box(`left:${44 + i * -0}px;top:${282 + i * 36}px;width:58px;height:30px;color:${i === lit ? '#05080c' : t.sub}`,
    `<div style="position:absolute;inset:0;transform:${SK};background:${i === lit ? w[2] : t.plate};border:1px solid ${t.line}"></div><div style="position:absolute;inset:0;display:grid;place-items:center">${S(w[0], 20)}</div>`)).join('');
}
function r3Home(o = {}) {
  const t = RT[o.theme || 'dark'];
  let h = mapSVG(560, 430, t.map) + vign + hazard(760, 300);
  h += o.call ? rCallPlate(t) + pl(820, 30, 344, 84, `${S('turn', 46, `color:${t.acc}`)}<span style="${IT};font-size:44px">350<span style="font-size:24px"> m</span></span><span style="margin-left:auto;${IT};font-size:30px;color:${t.sub}">14:32</span>`, { t, pad: 'padding:0 26px' }) : rTurn(t, o) + rEta(t);
  if (!o.call) h += rGate(t, o.gate ?? 0);
  h += rSpeed(t, o) + rTrack(t, { ...o, paused: o.call });
  if (o.toast) h += pl(784, 204, 380, 62, `${S('home', 32, `color:${t.acc}`)}<span style="${IT};font-size:26px;white-space:nowrap">Route home · 18 min</span>`, { t, edge: t.acc });
  if (o.notif) h += pl(780, 200, 384, 74, `${S('chat', 34, `color:${t.acc}`)}<div style="min-width:0"><div style="${CAPS};font-size:14px;color:${t.sub}">message · Priya</div><div style="${IT};font-weight:700;font-size:26px;white-space:nowrap">Running 5 min late</div></div>`, { t, edge: t.acc, gap: 14 });
  return h + UNUSED;
}
function rCallPlate(t) {
  const btn = (x, col, id, lab, ring, fill) => box(`left:${x}px;top:0;width:180px;height:72px`, `${ring ? `<div style="position:absolute;inset:-10px;transform:${SK};border:6px solid ${t.acc};box-shadow:0 0 0 5px #04060a"></div>` : ''}<div style="position:absolute;inset:0;transform:${SK};background:${fill ? col : 'transparent'};border:4px solid ${col}"></div><div style="position:absolute;inset:0;display:flex;align-items:center;justify-content:center;gap:10px;color:${fill ? '#04120b' : col};${IT};font-size:28px">${S(id, 30)}${lab}</div>`);
  return pl(36, 30, 740, 196, `<div style="width:124px;height:124px;flex:none;background:linear-gradient(150deg,#2b3a52,#141a26);${cham(22)};display:grid;place-items:center;${IT};font-size:52px">JV</div>
      <div><div style="${CAPS};font-size:18px;color:#4fe3a1;display:flex;align-items:center;gap:8px">${S('phone', 22)}incoming call</div><div style="${IT};font-size:56px;line-height:1.05;margin-top:6px">Jonas Vell</div><div style="${IT};font-weight:600;font-size:24px;color:${t.sub}">mobile</div></div>
      <div style="margin-left:auto;width:190px;height:164px;position:relative">${btn(0, '#4fe3a1', 'phone', 'Answer', true, true).replace('top:0', 'top:2px')}${btn(-14, '#ff6b7a', 'x', 'Decline').replace('top:0', 'top:94px')}</div>`, { t, edge: '#4fe3a1', pad: 'padding:0 30px 0 36px', gap: 26 });
}
/* worlds slide in from the left edge as a column of slanted plates; map stays live on the right */
function rList(rows, lit, head, t, sub = true) {
  let h = box('left:0;top:0;width:520px;height:720px;background:linear-gradient(90deg,rgba(4,6,10,.92) 70%,transparent)');
  h += box(`left:56px;top:34px;${CAPS};font-size:20px;color:${t.sub}`, head);
  rows.forEach((r, i) => {
    const on = i === lit, col = r[2] || t.acc;
    if (r[3] && r[3].length > 10 && !on) r = [r[0], r[1], r[2], ''];
    h += pl(48, 78 + i * 72, on ? 450 : 420, 62, `${S(r[0], 36, `color:${on ? '#05080c' : col}`)}<span style="${IT};font-size:34px;white-space:nowrap;color:${on ? '#05080c' : t.ink}">${r[1]}</span>${sub && r[3] ? `<span style="margin-left:auto;${IT};font-weight:600;font-size:21px;white-space:nowrap;color:${on ? '#05080ccc' : t.sub}">${r[3]}</span>` : ''}`,
      { t, bg: on ? col : t.plate, edge: on ? null : col, ring: on, pad: 'padding:0 24px 0 30px', gap: 18 });
  });
  return h;
}
function r3Worlds(lit = 0, o = {}) {
  const t = RT.dark;
  let h = mapSVG(560, 430, t.map) + vign + hazard(760, 300);
  h += rList(RW, lit, 'worlds · turn to choose', t);
  h += rMini(t) + rTrack(t) + box(`left:56px;top:532px;${CAPS};font-size:18px;color:${t.sub};line-height:1.8`, 'push · open &nbsp;&nbsp; back · close<br>hold · next song');
  return h + UNUSED;
}
function r3Dest() {
  const t = RT.dark;
  let h = mapSVG(560, 430, t.map) + vign + hazard(760, 300);
  h += rList([['home', 'Home', t.acc, '18 min'], ['work', 'Work', t.acc, '31 min'], ['pin', 'Larkspur Market', t.acc, '9 min'], ['search', 'Search…', t.acc, 'say it']], 0, 'navigate · where to?', t);
  h += rMini(t) + rTrack(t);
  return h + UNUSED;
}
function r3Music(o = {}) {
  const t = RT.dark;
  let h = mapSVG(560, 430, t.map) + dim(.8);
  h += box(`left:76px;top:60px;width:360px;height:360px;background:${RART};${cham(48)}`, `<div style="position:absolute;inset:0;display:grid;place-items:center;color:#fff">${S('music', 130)}</div><div style="position:absolute;left:0;right:0;bottom:0;height:120px;background:linear-gradient(transparent,rgba(0,0,0,.55))"></div>`);
  h += box(`left:76px;top:400px;width:360px;color:${t.ink}`, `<div style="padding:0 8px">${segBar(14, 6, t.mus, 17)}</div>`);
  const b = (id, x, w, hgt, y, big, ring) => box(`left:${x}px;top:${y}px;width:${w}px;height:${hgt}px`, `${ring ? `<div style="position:absolute;inset:-11px;transform:${SK};border:6px solid ${t.acc};box-shadow:0 0 0 5px #04060a"></div>` : ''}<div style="position:absolute;inset:0;transform:${SK};background:${big ? t.mus : 'rgba(14,18,26,.96)'};border:2px solid ${big ? t.mus : t.line}"></div><div style="position:absolute;inset:0;display:grid;place-items:center;color:${big ? '#140a26' : t.ink}">${S(id, big ? 56 : 40)}</div>`);
  h += b('prev', 60, 92, 76, 448) + b(o.paused ? 'play' : 'pause', 170, 132, 96, 438, true, o.focus === 'play') + b('next', 322, 92, 76, 448, false, o.focus === 'next') + b('list', 432, 76, 76, 448);
  h += box(`left:76px;top:568px;color:${t.ink}`, `<div style="${IT};font-size:48px">${o.title || 'Silver Hours'}</div><div style="${IT};font-weight:600;font-size:28px;color:${t.sub}">Lumen Coast · Tidewater</div>`);
  h += box(`left:560px;top:150px;${CAPS};font-size:20px;color:${t.sub}`, 'up next');
  [['Copper Mile', 'Lumen Coast', '3:41'], ['Low Sun Over Brinmoor', 'The Halyards', '4:05'], ['Cassette Weather', 'Odette Ray', '2:58'], ['Saltglass', 'Lumen Coast', '3:20']].forEach((r, i) =>
    h += pl(560 + i * 0, 190 + i * 98, 600, 84, `<span style="${IT};font-size:24px;color:${t.sub};width:22px">${i + 1}</span><div style="min-width:0"><div style="${IT};font-size:32px;white-space:nowrap">${r[0]}</div><div style="${IT};font-weight:600;font-size:21px;color:${t.sub}">${r[1]}</div></div><span style="margin-left:auto;${IT};font-weight:600;font-size:22px;color:${t.sub}">${r[2]}</span>`, { t, edge: i === 0 ? t.mus : null, pad: 'padding:0 28px 0 32px', gap: 18 }));
  h += rMini(t);
  return h + UNUSED;
}
function r3Apps(focus = 4) {
  const t = RT.dark;
  const apps = [['radio', 'Radio'], ['mic', 'Podcasts'], ['chat', 'Messages'], ['calendar', 'Calendar'], ['sun', 'Weather'], ['camera', 'Dashcam'], ['folder', 'Files'], ['pin', 'Parking'], ['list', 'Playlists'], ['bolt', 'Charging']];
  let h = mapSVG(560, 430, t.map) + dim(.82);
  h += box(`left:56px;top:40px;${CAPS};font-size:20px;color:#ff7aa8`, 'apps · 10');
  apps.forEach((a, i) => {
    const c = Math.floor(i / 5), r = i % 5, on = i === focus;
    h += pl(56 + c * 420 - r * 0, 84 + r * 100, 380, 82, `${S(a[0], 42, `color:${on ? '#2a0716' : '#ff7aa8'}`)}<span style="${IT};font-size:36px;color:${on ? '#2a0716' : t.ink}">${a[1]}</span>`, { t, bg: on ? '#ff7aa8' : 'rgba(14,18,26,.94)', ring: on, pad: 'padding:0 28px 0 34px', gap: 20 });
  });
  h += rMini(t);
  h += box(`left:900px;top:150px;width:260px;${CAPS};font-size:16px;color:${t.sub};line-height:1.9;text-align:right`, 'turn · down the column,<br>then the next<br>push · open<br>back · worlds');
  return h + UNUSED;
}
/* ---------- extra panels for the sheet ---------- */
function rStates() {
  const t = RT.dark, sc = .46;
  const crop = (o, cap) => `<div style="flex:1"><div style="position:relative;width:${430 * sc}px;height:${200 * sc}px;overflow:hidden;border-radius:8px;border:1px solid #232a3b;background:#0a0c10"><div style="position:absolute;left:0;top:0;transform:scale(${sc});transform-origin:0 0"><div style="position:absolute;left:-28px;top:-506px;width:1280px;height:720px">${mapSVG(560, 430, o.theme === 'night' ? 'raceNight' : 'race')}${vign}${rSpeed(RT[o.theme || 'dark'], o)}</div></div></div><div style="font-size:13px;color:#9aa3b8;margin-top:6px;line-height:1.4">${cap}</div></div>`;
  return `<div class="style"><h3>Speed and the limit hint</h3><div style="display:flex;gap:14px">${crop({ speed: 48 }, '<b style="color:#e3e8f5">Normal.</b> White outline; "hint" always printed.')}${crop({ speed: 61 }, '<b style="color:#e3e8f5">More than 9 over.</b> Plate fills calm amber. No sound, no flashing.')}</div>
    <h3>Rules for speed</h3>· The limit comes from OpenStreetMap and is only ever called a hint.<br>· The speed number is plain: no needle, no rev bar, no redline, no best, no score, no lap or sector time, nothing that grows when you go faster.<br>· Amber fades in over 600 ms and fades out the same way. Red is used only for Decline.<br>· No limit known: the plate shows a dash, still labelled hint.</div>`;
}
function rZones() {
  const z = (l, tp, w, h, lab) => `<div style="position:absolute;left:${l}px;top:${tp}px;width:${w}px;height:${h}px;transform:skewX(-12deg);background:#38e1ff22;border:2px solid #38e1ff"></div><div style="position:absolute;left:${l + 8}px;top:${tp + 4}px;font-size:11px;font-weight:700;color:#bfefff;width:${w}px">${lab}</div>`;
  const s = 0.36;
  return `<div class="style"><h3>Where the HUD may sit</h3><div style="position:relative;width:${1280 * s}px;height:${720 * s}px;border-radius:8px;overflow:hidden;border:1px solid #232a3b;background:#0a0c10">
    <div style="position:absolute;left:${300 * s}px;top:${210 * s}px;width:${540 * s}px;height:${290 * s}px;border:2px dashed #6c7c89;border-radius:6px;display:grid;place-items:center;text-align:center;font-size:12px;color:#9fb0c0">Waze only: map, arrow,<br>police and hazard icons</div>
    ${z(36 * s, 30 * s, 540 * s, 160 * s, 'turn · street')}${z(820 * s, 30 * s, 344 * s, 120 * s, 'arrival')}${z(36 * s, 512 * s, 392 * s, 176 * s, 'speed · hint')}${z(724 * s, 530 * s, 440 * s, 158 * s, 'song')}
    <div style="position:absolute;left:${1200 * s}px;top:0;width:${80 * s}px;height:100%;background:repeating-linear-gradient(45deg,#06070a,#06070a 5px,#0b0d12 5px,#0b0d12 10px)"></div></div>
    <div style="margin-top:10px">Four corner plates, nothing else on the map. Calls and messages replace a corner plate for a moment; the world list and pages slide in from the left edge and close with Back.</div>
    <h3>Night variant</h3>Same layout. Accent drops from #38e1ff to #22a6bf, white text to 75 %, plates go nearly opaque so the map glows less, and the amber is a deeper, duller tone.</div>`;
}

LANGS.race = {
  name: 'RACE', file: 'race', acc: '#38e1ff',
  title: 'Language 3 · RACE: a racing-game HUD around the edges of the map',
  idea: 'The map is the track and MyPlay is the HUD drawn on the glass. Four slanted plates sit in the corners and nowhere else: the next turn top-left (with the street after it), arrival top-right, speed and the limit hint bottom-left, the song bottom-right. The middle belongs to Waze, so its arrow and police and hazard icons are never covered. A slim gate of six ticks on the left edge shows which world you are in; push and the six worlds slide in from that edge as big slanted plates (Navigate, Music, Calls, Apps, Car, Settings). Turn to move, push to open, Back to go back, hold for the next song, as in every language. It borrows the feel of a racing game (italic numbers, slanted plates, chevrons, one electric accent) but nothing that rewards speed: no rev bar, no score, no lap times.',
  shapes: ['Parallelogram plates slanted 12°, upright text inside', 'Artwork and avatars are squares with two chamfered corners', 'Progress is a row of slanted segments, never a speed bar', 'Focus ring: a heavy cyan outline that follows the slant'],
  type: [['Hero numbers', '92 px (distance), 118 px (speed), Inter ExtraBold Italic'], ['Names', '34 to 56 px bold italic'], ['Secondary', '24 to 32 px'], ['Labels', '14 to 20 px caps italic, wide spacing']],
  motion: 'Plates slide in along their own slant from the nearest edge (200 ms, ease-out) and fade; the focus ring glides between plates. No shake, no speed lines, no zoom, no flashing. A new song wipes the artwork once.',
  colour: [['#38e1ff', 'Accent / Navigate'], ['#b58cff', 'Music'], ['#4fe3a1', 'Calls'], ['#ff7aa8', 'Apps'], ['#ffb347', 'Limit hint only'], ['#0a0c10', 'Carbon']], colourText: 'Carbon-black plates, white italic type and one electric cyan accent; each world keeps a tint for its edge stripe. Amber is reserved for the limit hint so it always means the same thing.',
  shots: [['Home: map running, music playing, a turn coming', () => r3Home()], ['Reaching worlds: push, they slide in from the left edge', () => r3Worlds(3)], ['Music: chamfered artwork box, floating slanted buttons', () => r3Music({ focus: 'play' })], ['Incoming call: replaces the turn plate, map stays live', () => r3Home({ call: true })], ['App list: two columns of big slanted plates', () => r3Apps(4)], ['Night variant: dimmer accent, a message, over the hint', () => r3Home({ theme: 'night', speed: 62, notif: true })]],
  row3: () => [stylePanel(LANGS.race), rStates(), rZones()],
  stripNext: { title: 'Play next song: 1 step (hold the knob, from any screen)', frames: [[r3Home(), 'Home, song playing.', 'hold', 'HOLD'], [r3Home({ title: 'Copper Mile', restart: true }), 'Next song; the plate wipes once.']] },
  stripNav: { title: 'Start navigation home: 3 pushes', frames: [[r3Home(), 'Home.', 'push', 'PUSH'], [r3Worlds(0), 'Worlds slide in on Navigate.', 'push', 'PUSH'], [r3Dest(), 'Where to? Home is first.', 'push', 'PUSH'], [r3Home({ toast: true }), 'Route started.']] }
};
