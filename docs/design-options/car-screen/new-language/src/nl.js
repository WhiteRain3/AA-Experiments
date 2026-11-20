// Three original design languages for MyPlay's whole car screen. Everything is drawn from the same few helpers.
const W = 1280, H = 720;
const S = (id, size, style = '') => `<svg style="width:${size}px;height:${size}px;flex:none;${style}"><use href="#${id}"/></svg>`;
const box = (style, inner = '', cls = '') => `<div class="${cls}" style="position:absolute;${style}">${inner}</div>`;
const dim = a => box(`inset:0;background:rgba(6,8,13,${a})`);

/* ---------- map (invented street map; same drawing for every language, only the ink changes) ---------- */
const PAL = {
  teal: { bg: '#0b1318', road: '#16232b', park: '#0e2420', river: '#08202c', rc: '#04070a', rl: '#6ee7a8', mk: '#fff' },
  dark: { bg: '#0e121b', road: '#1b2231', park: '#10221d', river: '#0a1a2c', rc: '#06080d', rl: '#2dd4bf', mk: '#fff' },
  amber: { bg: '#050505', road: '#1d1707', park: '#0a0904', river: '#120e04', rc: '#000', rl: '#ffb000', mk: '#ffb000' }
};
function mapSVG(cx, cy, pal) {
  const P = PAL[pal], dx = cx - 640, dy = cy - 420;
  let roads = '';
  for (let k = -8; k <= 8; k++) roads += `<rect x="${640 + k * 130 - 5}" y="-900" width="10" height="2600" fill="${P.road}"/>`;
  for (let k = -9; k <= 9; k++) roads += `<rect x="-1000" y="${420 + k * 105 - 5}" width="3400" height="10" fill="${P.road}"/>`;
  const parks = [[770, 245, 120, 95], [250, 350, 100, 80], [1030, 490, 105, 75]].map(p => `<rect x="${p[0]}" y="${p[1]}" width="${p[2]}" height="${p[3]}" rx="14" fill="${P.park}"/>`).join('');
  const river = `<path d="M-700 640 C -200 560, 150 760, 520 640 S 1100 780, 1700 640" fill="none" stroke="${P.river}" stroke-width="60"/>`;
  const route = 'M640 460 L640 210 L1030 210 L1030 -300';
  return `<svg class="map" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}"><rect width="${W}" height="${H}" fill="${P.bg}"/><g transform="translate(${dx} ${dy})">${river}${parks}${roads}<path d="${route}" fill="none" stroke="${P.rc}" stroke-width="22" stroke-linejoin="round" stroke-linecap="round"/><path d="${route}" fill="none" stroke="${P.rl}" stroke-width="12" stroke-linejoin="round" stroke-linecap="round"/></g><g transform="translate(${cx} ${cy})"><circle r="40" fill="${P.rl}" fill-opacity=".22"/><circle r="26" fill="${pal === 'amber' ? '#000' : '#fff'}" stroke="${P.mk}" stroke-width="${pal === 'amber' ? 4 : 0}"/><path d="M0 -15 L11 12 L0 6 L-11 12z" fill="${pal === 'amber' ? '#ffb000' : '#0e121b'}"/></g></svg>`;
}
const UNUSED = '<div class="unused"></div>';
const ART = 'linear-gradient(135deg,#4cc9f0,#8f5bff 60%,#ff5a8a)';
const statusTR = (acc, mono) => box('left:960px;top:34px;width:220px;text-align:right', `<div class="t ${mono ? 'mono' : ''}" style="font-size:46px;font-weight:800;line-height:1">12:34</div><div class="t" style="display:flex;gap:10px;justify-content:flex-end;align-items:center;margin-top:8px;font-size:28px;font-weight:700;color:${acc}">${S('signal', 30)}${S('battery', 32)}87%</div>`);

/* =================================================================
   LANGUAGE 1 · ORBIT   (one round hub, six worlds on a dial)
   ================================================================= */
const O = '#6ee7a8';
function o1Home(o = {}) {
  const title = o.title || 'Night Drive', artist = o.artist || 'Blue Harbor', R = 88, Cc = 2 * Math.PI * R;
  let h = mapSVG(520, 430, 'teal');
  h += box('inset:0;background:radial-gradient(ellipse at 12% 8%,rgba(6,8,13,.78),transparent 46%),radial-gradient(ellipse at 92% 94%,rgba(6,8,13,.78),transparent 44%)');
  h += box('left:44px;top:40px;width:132px;height:132px;border-radius:50%;background:#6ee7a8;color:#06251a;display:grid;place-items:center', S('turn', 78));
  h += box('left:198px;top:46px', '<div class="t" style="font-size:62px;font-weight:800;line-height:1">350 m</div><div class="t" style="font-size:28px;color:#c4cbdc;margin-top:8px">Hauptstraße</div>');
  h += statusTR(O);
  h += box('left:44px;top:484px;width:192px;height:192px;border-radius:50%;background:rgba(8,12,20,.86);border:2px solid rgba(255,255,255,.14)',
    `<svg width="192" height="192" style="position:absolute;left:-2px;top:-2px"><circle cx="96" cy="96" r="${R}" fill="none" stroke="#24323a" stroke-width="9"/><circle cx="96" cy="96" r="${R}" fill="none" stroke="#6ee7a8" stroke-width="9" stroke-linecap="round" stroke-dasharray="${Cc * (o.route ? .93 : .62)} ${Cc}" transform="rotate(-90 96 96)"/></svg><div style="position:absolute;inset:0;display:grid;place-content:center;text-align:center"><div style="font-size:46px;font-weight:800;line-height:1">14:32</div><div style="font-size:24px;color:#aab3c8;margin-top:4px">18 min</div></div>`, o.focusHub ? 'ring' : '');
  h += box('left:640px;top:568px;width:392px;text-align:right', `<div class="t" style="font-size:36px;font-weight:800">${title}</div><div class="t" style="font-size:26px;color:#c4cbdc">${artist}</div>`);
  h += box('left:1042px;top:542px;width:152px;height:152px', `<svg width="152" height="152"><circle cx="76" cy="76" r="72" fill="rgba(8,12,20,.7)" stroke="#2c2646" stroke-width="7"/><circle cx="76" cy="76" r="72" fill="none" stroke="#a78bfa" stroke-width="7" stroke-linecap="round" stroke-dasharray="${2 * Math.PI * 72 * (o.flash ? .02 : .38)} 999" transform="rotate(-90 76 76)"/></svg>`);
  h += box('left:1054px;top:554px;width:128px;height:128px;border-radius:50%;display:grid;place-items:center;color:#fff;background:' + ART, S('music', 54), o.focusMusic ? 'ring' : '');
  if (o.toast) h += box('left:470px;top:40px;width:460px;height:76px;border-radius:38px;background:rgba(8,12,20,.9);border:2px solid #6ee7a8;display:flex;align-items:center;justify-content:center;gap:14px;font-size:30px;font-weight:700', S('home', 38, 'color:#6ee7a8') + 'Heading home · 18 min');
  return h + UNUSED;
}
const SEC = [['nav', 'Navigate', '110,231,168', '#6ee7a8', 'Home · 18 min'], ['music', 'Music', '167,139,250', '#a78bfa', 'Night Drive'], ['phone', 'Calls', '95,211,154', '#5fd39a', 'Anna K. called'], ['launcher', 'Apps', '255,143,177', '#ff8fb1', '10 apps'], ['gauge', 'Car', '255,194,74', '#ffc24a', 'All good'], ['gear', 'Settings', '125,150,200', '#9db4e8', 'Look, sound']];
const sector = (cx, cy, r1, r2, a0, a1) => { const p = (r, a) => [cx + r * Math.cos(a * Math.PI / 180), cy + r * Math.sin(a * Math.PI / 180)]; const A = p(r2, a0), B = p(r2, a1), C = p(r1, a1), D = p(r1, a0); return `M${A} A${r2} ${r2} 0 0 1 ${B} L${C} A${r1} ${r1} 0 0 0 ${D}Z`; };
function o1Radial(lit = 0) {
  const cx = 600, cy = 370, r1 = 132, r2 = 276;
  let h = mapSVG(640, 430, 'teal') + dim(.7);
  let svg = '', labels = '';
  SEC.forEach((s, i) => {
    const c = -90 + 60 * i, on = i === lit, a = c * Math.PI / 180;
    svg += `<path d="${sector(cx, cy, r1, r2, c - 28.5, c + 28.5)}" fill="${on ? `rgba(${s[2]},.30)` : 'rgba(14,19,30,.92)'}" stroke="${on ? s[3] : 'rgba(255,255,255,.14)'}" stroke-width="${on ? 5 : 2}"/>`;
    labels += box(`left:${cx + 205 * Math.cos(a)}px;top:${cy + 205 * Math.sin(a)}px;transform:translate(-50%,-50%);text-align:center;display:flex;flex-direction:column;align-items:center;gap:6px;color:${on ? '#fff' : '#aab3c8'}`, S(s[0], on ? 62 : 52, `color:${on ? s[3] : '#aab3c8'}`) + `<div style="font-size:27px;font-weight:${on ? 800 : 600}">${s[1]}</div>`);
  });
  h += `<svg style="position:absolute;left:0;top:0" width="${W}" height="${H}">${svg}</svg>` + labels;
  const s = SEC[lit];
  h += box(`left:${cx - 120}px;top:${cy - 120}px;width:240px;height:240px;border-radius:50%;background:rgba(8,12,20,.95);border:3px solid ${s[3]};display:flex;flex-direction:column;align-items:center;justify-content:center;gap:6px`, S(s[0], 76, `color:${s[3]}`) + `<div style="font-size:34px;font-weight:800">${s[1]}</div><div style="font-size:23px;color:#aab3c8">${s[4]}</div>`);
  h += box('left:0;right:80px;top:672px;text-align:center;font-size:24px;color:#8b93a7', 'turn: choose &nbsp;·&nbsp; push: open &nbsp;·&nbsp; hold: back');
  return h + UNUSED;
}
function o1Music(o = {}) {
  const title = o.title || 'Night Drive';
  let h = mapSVG(640, 430, 'teal') + dim(.62);
  h += box(`left:140px;top:110px;width:320px;height:320px;border-radius:78px;overflow:hidden;background:${ART}`, `<div style="position:absolute;inset:0;display:grid;place-items:center;color:#fff">${S('music', 128)}</div><div style="position:absolute;left:0;right:0;bottom:0;padding:50px 28px 22px;background:linear-gradient(180deg,rgba(6,8,13,0),rgba(6,8,13,.88))"><div style="font-size:38px;font-weight:800">${title}</div><div style="font-size:26px;color:#c4cbdc">Blue Harbor</div><div class="prog" style="margin-top:12px"><i style="width:38%;background:#fff"></i></div></div>`, o.focus === 'art' ? 'ring' : '');
  const btn = (id, x, y, d, big, ring) => box(`left:${x - d / 2}px;top:${y - d / 2}px;width:${d}px;height:${d}px;border-radius:50%;background:${big ? '#6ee7a8' : 'rgba(14,19,30,.92)'};color:${big ? '#06251a' : '#e8edf8'};border:2px solid ${big ? '#6ee7a8' : 'rgba(255,255,255,.2)'};display:grid;place-items:center`, S(id, big ? 46 : 36), ring ? 'ring' : '');
  h += btn('prev', 165, 548, 76) + btn(o.paused ? 'play' : 'pause', 262, 586, 100, true, o.focus === 'play') + btn('next', 376, 586, 76, false, o.focus === 'next') + btn('list', 472, 548, 76);
  h += box('left:640px;top:110px;width:480px', '<div style="font-size:22px;letter-spacing:.1em;color:#8b93a7;margin-bottom:14px">UP NEXT</div>' + [['Midnight Run', 'Blue Harbor'], ['Glass Roads', 'Kite & Co.'], ['Slow Harbour', 'Lumen']].map((r, i) => `<div style="display:flex;align-items:center;gap:20px;margin-bottom:22px"><div style="width:72px;height:72px;border-radius:50%;background:${['linear-gradient(135deg,#ffc24a,#ff5a8a)', 'linear-gradient(135deg,#4cc9f0,#5fd39a)', 'linear-gradient(135deg,#8f5bff,#4cc9f0)'][i]};flex:none"></div><div><div style="font-size:34px;font-weight:700">${r[0]}</div><div style="font-size:25px;color:#aab3c8">${r[1]}</div></div></div>`).join(''));
  return h + UNUSED;
}
function o1Call() {
  let h = mapSVG(640, 430, 'teal') + dim(.74);
  h += `<svg style="position:absolute;left:0;top:0" width="${W}" height="${H}"><circle cx="600" cy="300" r="236" fill="none" stroke="rgba(95,211,154,.14)" stroke-width="4"/><circle cx="600" cy="300" r="200" fill="none" stroke="rgba(95,211,154,.28)" stroke-width="4"/></svg>`;
  h += box('left:480px;top:180px;width:240px;height:240px;border-radius:50%;background:linear-gradient(135deg,#3b4a63,#1d2433);border:3px solid #5fd39a;display:grid;place-items:center;font-size:96px;font-weight:800', 'AK');
  h += box('left:300px;top:446px;width:600px;text-align:center', '<div style="font-size:54px;font-weight:800">Anna K.</div><div style="font-size:28px;color:#aab3c8;margin-top:4px">incoming call</div>');
  const b = (x, col, id, lab, ring) => box(`left:${x - 64}px;top:556px;width:128px;height:128px;border-radius:50%;background:${col};color:#06120c;display:grid;place-items:center`, S(id, 56), ring ? 'ring' : '') + box(`left:${x - 100}px;top:690px;width:200px;text-align:center;font-size:24px;color:#c4cbdc`, lab);
  h += b(430, '#ff6b6b', 'x', 'Decline') + b(770, '#5fd39a', 'phone', 'Answer', true);
  return h + UNUSED;
}
const APPS10 = [['radio', 'Radio'], ['mic', 'Podcasts'], ['chat', 'Messages'], ['calendar', 'Calendar'], ['sun', 'Weather'], ['camera', 'Camera'], ['folder', 'Files'], ['gauge', 'Car'], ['gear', 'Settings'], ['phone', 'Phone']];
function o1Apps(focus = 4) {
  let h = mapSVG(640, 430, 'teal') + dim(.72);
  const pos = [[420, 190], [600, 190], [780, 190], [330, 360], [510, 360], [690, 360], [870, 360], [420, 530], [600, 530], [780, 530]];
  APPS10.forEach((a, i) => {
    const [x, y] = pos[i], on = i === focus;
    h += box(`left:${x - 64}px;top:${y - 64}px;width:128px;height:128px;border-radius:50%;background:${on ? 'rgba(255,143,177,.22)' : 'rgba(14,19,30,.92)'};border:3px solid ${on ? '#ff8fb1' : 'rgba(255,255,255,.14)'};display:grid;place-items:center;color:${on ? '#ff8fb1' : '#dfe5f3'}`, S(a[0], 58), on ? 'ring' : '');
    h += box(`left:${x - 80}px;top:${y + 72}px;width:160px;text-align:center;font-size:25px;font-weight:${on ? 800 : 600};color:${on ? '#fff' : '#aab3c8'}`, a[1]);
  });
  h += box('left:60px;top:40px;font-size:34px;font-weight:800', 'Apps') + box('left:60px;top:84px;font-size:24px;color:#8b93a7', 'turn: move around the honeycomb · push: open');
  return h + UNUSED;
}
function o1Dest() {
  let h = mapSVG(640, 430, 'teal') + dim(.55);
  h += box('left:140px;top:84px;font-size:38px;font-weight:800', 'Where to?');
  [['home', 'Home', '18 min · 9.4 km'], ['work', 'Work', '31 min · 22 km'], ['pin', 'Marktplatz', '9 min · 3.1 km']].forEach((r, i) => {
    h += box(`left:140px;top:${170 + i * 124}px;width:540px;height:104px;border-radius:52px;background:rgba(8,12,20,.93);border:3px solid ${i === 0 ? '#6ee7a8' : 'rgba(255,255,255,.14)'};display:flex;align-items:center;gap:22px;padding:0 24px 0 14px`, `<div style="width:76px;height:76px;border-radius:50%;background:${i === 0 ? '#6ee7a8' : '#1a2230'};color:${i === 0 ? '#06251a' : '#dfe5f3'};display:grid;place-items:center">${S(r[0], 40)}</div><div style="flex:1"><div style="font-size:38px;font-weight:800">${r[1]}</div><div style="font-size:24px;color:#aab3c8">${r[2]}</div></div>`, i === 0 ? 'ring' : '');
  });
  return h + UNUSED;
}

/* =================================================================
   LANGUAGE 2 · DECK   (a fanned hand of colour-coded cards, flipped with the knob)
   ================================================================= */
const CARD = { NAV: ['#2dd4bf', '#0e2a2c', 'nav', 'NAVIGATE'], PHONE: ['#4ade80', '#10281a', 'phone', 'PHONE'], CAR: ['#fbbf24', '#2a2210', 'gauge', 'CAR'], APPS: ['#fb7185', '#2b141b', 'launcher', 'APPS'], MUSIC: ['#a78bfa', '#1d1836', 'music', 'MUSIC'] };
const ORDER = ['NAV', 'PHONE', 'CAR', 'APPS', 'MUSIC'];
function d2Hud(o = {}) {
  let h = box('inset:0;background:radial-gradient(ellipse at 10% 10%,rgba(6,8,13,.8),transparent 48%)');
  h += box('left:56px;top:40px;display:flex;align-items:center;gap:26px', `<div style="color:#2dd4bf">${S('turn', 136)}</div><div><div class="t" style="font-size:92px;font-weight:800;line-height:.95">350 m</div><div class="t" style="font-size:32px;color:#c4cbdc;margin-top:6px">Hauptstraße</div></div>`);
  h += box('left:58px;top:216px;display:flex;gap:20px;align-items:baseline', '<div class="t" style="font-size:40px;font-weight:800">14:32</div><div class="t" style="font-size:30px;color:#aab3c8">18 min · 9.4 km</div>');
  h += statusTR('#2dd4bf');
  if (o.toast) h += box('left:500px;top:40px;width:440px;height:80px;border-radius:14px;background:#0e2a2c;border-top:8px solid #2dd4bf;display:flex;align-items:center;justify-content:center;gap:14px;font-size:32px;font-weight:800', S('home', 40, 'color:#2dd4bf') + 'Heading home · 18 min');
  return h;
}
const pillRow = (x, y, w, ids, o = {}) => box(`left:${x}px;top:${y}px;width:${w}px;height:88px;border-radius:44px;background:rgba(10,12,20,.9);border:2px solid rgba(255,255,255,.18);display:flex;align-items:center;justify-content:center;gap:8px`, ids.map(id => {
  const big = id === 'play' || id === 'pause', ring = (o.focusBtn === 'play' && big) || (o.focusBtn === id);
  return `<div class="${ring ? 'ring' : ''}" style="width:${big ? 76 : 68}px;height:${big ? 76 : 68}px;border-radius:50%;flex:none;display:grid;place-items:center;background:${big ? '#a78bfa' : '#1a2030'};color:${big ? '#140c2e' : '#e8edf8'}">${S(id, big ? 40 : 34)}</div>`;
}).join(''));
function d2Fan(front, o = {}) {
  const fi = ORDER.indexOf(front);
  let h = '';
  ORDER.forEach((name, i) => {
    const d = (fi - i + 5) % 5, c = CARD[name];
    const base = `left:810px;top:250px;width:340px;height:440px;transform-origin:100% 100%;transform:rotate(${-9 * d}deg);z-index:${10 - d};border-radius:16px;background:${c[1]};border-top:8px solid ${c[0]};box-shadow:0 10px 30px rgba(0,0,0,.6)`;
    if (d === 0) return;
    h += box(base, `<div style="display:flex;align-items:center;gap:10px;padding:16px 18px;color:${c[0]};font-weight:800;font-size:28px;letter-spacing:.06em">${S(c[2], 34)}${c[3]}</div>`);
  });
  const c = CARD[front], ring = o.focus === 'front' ? 'ring' : '';
  if (front === 'MUSIC') {
    const title = o.title || 'Night Drive';
    h += box(`left:810px;top:252px;width:340px;height:340px;z-index:20;border-radius:20px;overflow:hidden;background:${ART};border-top:8px solid #a78bfa`, `<div style="position:absolute;inset:0;display:grid;place-items:center;color:#fff">${S('music', 120)}</div><div style="position:absolute;left:0;right:0;bottom:0;padding:48px 20px 16px;background:linear-gradient(180deg,rgba(6,8,13,0),rgba(6,8,13,.9))"><div style="font-size:34px;font-weight:800">${title}</div><div style="font-size:25px;color:#c4cbdc">Blue Harbor</div><div class="prog" style="margin-top:10px"><i style="width:38%;background:#a78bfa"></i></div></div>`, ring);
    h += pillRow(810, 604, 340, ['prev', 'pause', 'next'], o).replace('position:absolute;', 'position:absolute;z-index:30;');
  } else if (front === 'APPS') {
    h += box(`left:810px;top:250px;width:340px;height:440px;z-index:20;border-radius:16px;background:${c[1]};border-top:8px solid ${c[0]}`, `<div style="display:flex;align-items:center;gap:10px;padding:16px 18px;color:${c[0]};font-weight:800;font-size:28px;letter-spacing:.06em">${S('launcher', 34)}APPS</div><div style="display:grid;grid-template-columns:repeat(3,1fr);gap:12px;padding:8px 18px">${['radio', 'mic', 'chat', 'calendar', 'sun', 'camera'].map(s => `<div style="height:92px;border-radius:14px;background:rgba(251,113,133,.16);color:#fb7185;display:grid;place-items:center">${S(s, 42)}</div>`).join('')}</div><div style="padding:18px;font-size:26px;color:#c4cbdc">push to open all 10</div>`, ring);
  } else if (front === 'NAV') {
    h += box(`left:810px;top:250px;width:340px;height:440px;z-index:20;border-radius:16px;background:${c[1]};border-top:8px solid ${c[0]}`, `<div style="display:flex;align-items:center;gap:10px;padding:16px 18px;color:${c[0]};font-weight:800;font-size:28px;letter-spacing:.06em">${S('nav', 34)}NAVIGATE</div>${[['home', 'Home', '18 min'], ['work', 'Work', '31 min'], ['pin', 'Marktplatz', '9 min']].map((r, i) => `<div style="margin:10px 18px;height:96px;border-radius:12px;background:${i === 0 ? '#2dd4bf' : 'rgba(45,212,191,.12)'};color:${i === 0 ? '#06251f' : '#dfe5f3'};display:flex;align-items:center;gap:16px;padding:0 16px">${S(r[0], 44)}<div><div style="font-size:32px;font-weight:800">${r[1]}</div><div style="font-size:24px;opacity:.8">${r[2]}</div></div></div>`).join('')}`, ring);
  }
  return h;
}
function d2Home(front = 'MUSIC', o = {}) { return mapSVG(520, 440, 'dark') + d2Hud(o) + d2Fan(front, o) + UNUSED; }
function d2Music(o = {}) {
  let h = mapSVG(520, 440, 'dark') + dim(.6);
  h += box(`left:100px;top:70px;width:400px;height:400px;border-radius:20px;overflow:hidden;background:${ART};border-top:8px solid #a78bfa`, `<div style="position:absolute;inset:0;display:grid;place-items:center;color:#fff">${S('music', 150)}</div><div style="position:absolute;left:0;right:0;bottom:0;padding:60px 26px 20px;background:linear-gradient(180deg,rgba(6,8,13,0),rgba(6,8,13,.92))"><div style="font-size:42px;font-weight:800">${o.title || 'Night Drive'}</div><div style="font-size:28px;color:#c4cbdc">Blue Harbor</div><div class="prog" style="margin-top:12px"><i style="width:38%;background:#a78bfa"></i></div></div>`, o.focus === 'art' ? 'ring' : '');
  h += pillRow(100, 500, 400, ['prev', 'pause', 'next', 'list'], { focusBtn: o.focus });
  h += box('left:560px;top:60px;width:600px;height:600px;border-radius:16px;background:#1d1836;border-top:8px solid #a78bfa', `<div style="padding:22px 30px;color:#a78bfa;font-weight:800;font-size:28px;letter-spacing:.06em">UP NEXT</div>${[['Midnight Run', '3:12'], ['Glass Roads', '4:01'], ['Slow Harbour', '2:48'], ['Paper Moons', '3:30']].map((r, i) => `<div style="display:flex;align-items:center;gap:22px;padding:0 30px;height:104px;border-top:1px solid rgba(255,255,255,.08)"><div style="font-size:30px;color:#6b6890;width:36px">${i + 1}</div><div style="flex:1;font-size:40px;font-weight:700">${r[0]}</div><div style="font-size:28px;color:#aab3c8">${r[1]}</div></div>`).join('')}`);
  return h + UNUSED;
}
function d2Call() {
  let h = mapSVG(520, 440, 'dark') + dim(.72);
  h += box('left:380px;top:100px;width:780px;height:520px;border-radius:16px;background:#10281a;border-top:8px solid #4ade80', `<div style="padding:26px 40px;color:#4ade80;font-weight:800;font-size:28px;letter-spacing:.06em;display:flex;gap:12px;align-items:center">${S('phone', 34)}INCOMING CALL</div><div style="padding:0 40px"><div style="font-size:86px;font-weight:800;line-height:1">Anna K.</div><div style="font-size:32px;color:#aab3c8;margin-top:8px">mobile</div></div>`);
  h += box('left:420px;top:380px;width:700px;height:96px;border-radius:12px;background:#4ade80;color:#06210f;display:flex;align-items:center;justify-content:center;gap:16px;font-size:40px;font-weight:800', S('phone', 46) + 'Accept', 'ring');
  h += box('left:420px;top:496px;width:700px;height:96px;border-radius:12px;border:3px solid #fb7185;color:#fb7185;display:flex;align-items:center;justify-content:center;gap:16px;font-size:40px;font-weight:800', S('x', 40) + 'Decline');
  return h + UNUSED;
}
function d2Apps(focus = 4) {
  let h = mapSVG(520, 440, 'dark') + dim(.7);
  h += box('left:80px;top:50px;width:1100px;height:620px;border-radius:16px;background:#2b141b;border-top:8px solid #fb7185', `<div style="padding:20px 30px;color:#fb7185;font-weight:800;font-size:28px;letter-spacing:.06em">APPS</div>`);
  const hues = ['#fb7185', '#fbbf24', '#4ade80', '#2dd4bf', '#a78bfa'];
  APPS10.forEach((a, i) => {
    const col = i % 5, row = Math.floor(i / 5), on = i === focus, c = hues[i % 5];
    h += box(`left:${110 + col * 214}px;top:${130 + row * 260}px;width:196px;height:236px;border-radius:14px;background:${on ? c : 'rgba(255,255,255,.06)'};color:${on ? '#17090c' : c};display:flex;flex-direction:column;align-items:center;justify-content:center;gap:16px`, S(a[0], 74) + `<div style="font-size:29px;font-weight:800;color:${on ? '#17090c' : '#e8edf8'}">${a[1]}</div>`, on ? 'ring' : '');
  });
  return h + UNUSED;
}
function d2Dest() {
  let h = mapSVG(520, 440, 'dark') + dim(.5);
  h += box('left:80px;top:60px;width:600px;height:600px;border-radius:16px;background:#0e2a2c;border-top:8px solid #2dd4bf', `<div style="padding:22px 34px;color:#2dd4bf;font-weight:800;font-size:28px;letter-spacing:.06em">WHERE TO?</div>`);
  [['home', 'Home', '18 min · 9.4 km'], ['work', 'Work', '31 min · 22 km'], ['pin', 'Marktplatz', '9 min · 3.1 km'], ['pin', 'Search…', 'say it, or choose']].forEach((r, i) => {
    h += box(`left:110px;top:${140 + i * 122}px;width:540px;height:104px;border-radius:12px;background:${i === 0 ? '#2dd4bf' : 'rgba(45,212,191,.12)'};color:${i === 0 ? '#06251f' : '#e8edf8'};display:flex;align-items:center;gap:22px;padding:0 24px`, S(r[0], 50) + `<div><div style="font-size:40px;font-weight:800">${r[1]}</div><div style="font-size:25px;opacity:.8">${r[2]}</div></div>`, i === 0 ? 'ring' : '');
  });
  return h + UNUSED;
}

/* =================================================================
   LANGUAGE 3 · VERBS   (a quiet amber HUD; you drive by commands)
   ================================================================= */
const AM = '#ffb000';
const mn = 'mono';
function v3Home(o = {}) {
  const title = (o.title || 'NIGHT DRIVE'), artist = 'BLUE HARBOR';
  let h = mapSVG(520, 410, 'amber');
  h += box('inset:0;background:radial-gradient(ellipse at 8% 10%,rgba(0,0,0,.85),transparent 50%)');
  h += box('left:56px;top:34px;display:flex;align-items:flex-end;gap:22px', `<div style="color:${AM}">${S('turn', 130)}</div><div class="${mn} t" style="color:${AM};font-size:150px;font-weight:800;line-height:.82">350</div><div class="${mn} t" style="color:${AM};font-size:60px;font-weight:800;line-height:1;margin-bottom:4px">m</div>`);
  h += box('left:60px;top:196px;height:3px;width:360px;background:#ffb000');
  h += box('left:60px;top:214px', `<div class="${mn} t" style="font-size:36px;letter-spacing:.14em;color:#f4efe4">HAUPTSTRASSE</div>`);
  h += box('left:960px;top:34px;width:220px;text-align:right', `<div class="${mn} t" style="font-size:60px;font-weight:800;color:#f4efe4;line-height:1">12:34</div><div class="${mn} t" style="font-size:30px;color:${AM};margin-top:6px">BAT 87%</div>`);
  if (o.toast) h += box('left:460px;top:34px;width:480px;height:84px;background:#000;border:3px solid #ffb000;display:flex;align-items:center;justify-content:center;font-size:34px;letter-spacing:.1em;color:#ffb000', `<span class="${mn}">ROUTE HOME · 18 MIN</span>`);
  h += box('left:0;top:640px;width:1200px;height:80px;background:#000;border-top:3px solid #ffb000;display:flex;align-items:center;padding:0 34px;gap:30px;white-space:nowrap', `<span class="${mn}" style="font-size:26px;color:#ffb000;display:flex;align-items:center;gap:12px">${S('play', 28)}${title} — ${artist}</span><span class="${mn}" style="font-size:24px;color:#a98319">1:42 / 3:56</span><span style="flex:1"></span><span class="${mn}" style="font-size:26px;color:#f4efe4">14:32 · 18 MIN · 9.4 KM</span>`);
  return h + UNUSED;
}
const VERBS = [['next', 'Play next song'], ['pause', 'Pause'], ['home', 'Navigate home'], ['phone', 'Call Anna'], ['radio', 'Radio'], ['launcher', 'All apps…']];
function v3Palette(lit = 0, rows = VERBS, head = 'COMMANDS') {
  let h = mapSVG(640, 410, 'amber') + dim(.6);
  h += box('left:50px;top:60px;width:740px;height:600px;background:#000;border:2px solid #7a5a00;border-top:6px solid #ffb000');
  h += box('left:84px;top:84px;width:672px;display:flex;justify-content:space-between;font-size:24px;letter-spacing:.14em;color:#a98319', `<span class="${mn}">${head}</span><span class="${mn}">HOLD = VOICE</span>`);
  rows.forEach((r, i) => {
    const on = i === lit;
    h += box(`left:84px;top:${130 + i * 76}px;width:672px;height:68px;display:flex;align-items:center;gap:22px;padding:0 14px;background:${on ? '#ffb000' : 'transparent'};color:${on ? '#000' : '#f4efe4'};border-bottom:${on ? 0 : '1px solid #2a2208'}`, `<div style="width:46px;height:46px;border:2px solid ${on ? '#000' : '#ffb000'};display:grid;place-items:center;color:${on ? '#000' : '#ffb000'}">${S(r[0], 30)}</div><span class="${mn}" style="font-size:36px;font-weight:700;flex:1">${r[1]}</span>${on ? `<span class="${mn}" style="font-size:24px;font-weight:800">PUSH</span>` : ''}`, on ? 'ring' : '');
  });
  h += box('left:84px;top:604px;width:672px;font-size:22px;color:#a98319;letter-spacing:.1em', `<span class="${mn}">TURN: MOVE · PUSH: RUN · BACK: CLOSE</span>`);
  return h + UNUSED;
}
function v3Music(o = {}) {
  const title = o.title || 'NIGHT DRIVE';
  let h = mapSVG(640, 410, 'amber') + dim(.8);
  h += box(`left:70px;top:90px;width:320px;height:320px;border:3px solid #ffb000;background:linear-gradient(135deg,#ffb000,#5a3d00 70%,#1a1100);display:grid;place-items:center;color:#000`, S('music', 140), o.focus === 'art' ? 'ring' : '');
  const btns = [['prev', 'PREV'], [o.paused ? 'play' : 'pause', o.paused ? 'PLAY' : 'PAUSE'], ['next', 'NEXT'], ['list', 'QUEUE']];
  h += btns.map((b, i) => { const on = (o.focus === 'play' && i === 1) || (o.focus === 'next' && i === 2); return box(`left:${70 + i * 82}px;top:446px;width:74px;height:74px;border:2px solid ${on ? '#ffb000' : '#7a5a00'};background:${on ? '#ffb000' : '#000'};color:${on ? '#000' : '#ffb000'};display:grid;place-items:center`, S(b[0], 40), on ? 'ring' : '') + box(`left:${70 + i * 82 - 4}px;top:526px;width:82px;text-align:center;font-size:19px;letter-spacing:.08em;color:#a98319`, `<span class="${mn}">${b[1]}</span>`); }).join('');
  h += box('left:450px;top:96px;width:700px', `<div class="${mn}" style="font-size:22px;letter-spacing:.16em;color:#a98319">NOW PLAYING</div><div class="${mn}" style="font-size:72px;font-weight:800;color:#f4efe4;line-height:1.05;margin-top:10px">${title}</div><div class="${mn}" style="font-size:38px;color:${AM};margin-top:12px">BLUE HARBOR</div><div style="height:6px;background:#2a2208;margin-top:34px;position:relative"><i style="position:absolute;left:0;top:0;bottom:0;width:43%;background:#ffb000"></i></div><div class="${mn}" style="display:flex;justify-content:space-between;font-size:26px;color:#a98319;margin-top:10px"><span>1:42</span><span>3:56</span></div><div class="${mn}" style="font-size:22px;letter-spacing:.16em;color:#a98319;margin-top:36px">UP NEXT</div>${['MIDNIGHT RUN', 'GLASS ROADS', 'SLOW HARBOUR'].map((t, i) => `<div class="${mn}" style="font-size:32px;color:#f4efe4;margin-top:10px">${i + 1}  ${t}</div>`).join('')}`);
  return h + UNUSED;
}
function v3Call() {
  let h = mapSVG(640, 410, 'amber') + dim(.35);
  h += box('left:0;top:0;width:1200px;height:200px;background:#000;border-bottom:4px solid #ffb000;border-top:4px solid #ffb000');
  h += box('left:44px;top:34px;width:132px;height:132px;border:3px solid #ffb000;color:#ffb000;display:grid;place-items:center', S('phone', 80));
  h += box('left:210px;top:30px', `<div class="${mn}" style="font-size:24px;letter-spacing:.16em;color:#a98319">INCOMING CALL</div><div class="${mn}" style="font-size:86px;font-weight:800;color:#f4efe4;line-height:1.05">ANNA K.</div><div class="${mn}" style="font-size:28px;color:#ffb000">MOBILE</div>`);
  h += box('left:780px;top:36px;width:380px;height:70px;background:#ffb000;color:#000;display:flex;align-items:center;justify-content:space-between;padding:0 22px;font-size:34px;font-weight:800', `<span class="${mn}">PUSH</span><span class="${mn}">ANSWER</span>`);
  h += box('left:780px;top:118px;width:380px;height:62px;border:3px solid #ffb000;color:#ffb000;display:flex;align-items:center;justify-content:space-between;padding:0 22px;font-size:30px;font-weight:800', `<span class="${mn}">BACK</span><span class="${mn}">DECLINE</span>`);
  return h + UNUSED;
}
function v3Apps(focus = 4) {
  let h = mapSVG(640, 410, 'amber') + dim(.9);
  h += box('left:60px;top:40px;font-size:24px;letter-spacing:.16em;color:#a98319', `<span class="${mn}">ALL APPS · 10</span>`);
  h += box('left:60px;top:78px;width:1080px;height:3px;background:#ffb000');
  APPS10.forEach((a, i) => {
    const col = Math.floor(i / 5), row = i % 5, on = i === focus;
    h += box(`left:${60 + col * 560}px;top:${110 + row * 106}px;width:520px;height:92px;display:flex;align-items:center;gap:22px;padding:0 20px;background:${on ? '#ffb000' : 'transparent'};color:${on ? '#000' : '#f4efe4'};border-bottom:${on ? 0 : '1px solid #2a2208'}`, `<span class="${mn}" style="font-size:28px;color:${on ? '#000' : '#a98319'};width:44px">${String(i + 1).padStart(2, '0')}</span>${S(a[0], 40, 'color:' + (on ? '#000' : '#ffb000'))}<span class="${mn}" style="font-size:44px;font-weight:800;text-transform:uppercase">${a[1]}</span>`, on ? 'ring' : '');
  });
  h += box('left:60px;top:650px;font-size:22px;letter-spacing:.1em;color:#a98319', `<span class="${mn}">PAD: MOVE · PUSH: OPEN · BACK: CLOSE</span>`);
  return h + UNUSED;
}
const DESTS = [['home', 'Home · 18 min'], ['work', 'Work · 31 min'], ['pin', 'Marktplatz · 9 min'], ['mic', 'Search…']];

/* =================================================================
   Sheets
   ================================================================= */
const LANGS = {
  '1': {
    name: 'ORBIT', file: 'orbit', acc: O,
    title: 'Language 1 · ORBIT: one round hub and a dial of six worlds',
    idea: 'Nothing on this screen is a rectangle you have to find. The only permanent control is a round hub in the corner that doubles as the trip clock (its ring is your progress to the destination). Push it and a dial of six worlds opens around the map: Navigate, Music, Calls, Apps, Car, Settings. Turn to rotate the highlight, push to enter, hold to go back. Everything else on the home screen is information floating on the map: a round turn bubble, the song as a small round disc, the clock. Apps are a honeycomb of discs, calls are one big circle.',
    home: o => o1Home(o), shapes: ['Circles, rings and arcs only; the one square is the artwork, drawn as a very round box', 'Rings show progress (trip, song) instead of bars', 'The focus ring is always a circle or follows the round shape'],
    type: [['Hero numbers', '62 px bold (distance), 46 px (clock)'], ['Names', '36 to 38 px bold'], ['Secondary', '25 to 28 px'], ['Dial words', '27 px']],
    motion: 'Everything opens outward from the hub like a ripple; the dial turns with the knob (detents); closing collapses back into the hub.',
    colour: [['#6ee7a8', 'Navigate'], ['#a78bfa', 'Music'], ['#5fd39a', 'Calls'], ['#ff8fb1', 'Apps'], ['#ffc24a', 'Car']], colourText: 'Near-black glass over the map; each world has its own accent, and the accent of the world you are in tints the rings.',
    shots: [['Home: map running, music playing', o => o1Home({ focusHub: true })], ['Reaching apps: push the hub, the dial opens', () => o1Radial(3)], ['Music card: artwork box, floating buttons in an arc', () => o1Music({ focus: 'play' })], ['Incoming call', () => o1Call()], ['App list: a honeycomb of discs', () => o1Apps(4)]],
    stripNext: { title: 'Play next song: 1 step (pad right on Home)', frames: [[o1Home({ focusMusic: true }), 'Home. Pad right = next song.', 'pad', 'PAD →'], [o1Home({ title: 'Midnight Run', flash: true }), 'New song. The ring restarts.']] },
    stripNav: { title: 'Start navigation home: 3 pushes', frames: [[o1Home({ focusHub: true }), 'Home. Hub has focus.', 'push', 'PUSH'], [o1Radial(0), 'Dial opens on Navigate.', 'push', 'PUSH'], [o1Dest(), 'Home is already highlighted.', 'push', 'PUSH'], [o1Home({ route: true, toast: true }), 'Route started.']] }
  },
  '2': {
    name: 'DECK', file: 'deck', acc: '#2dd4bf',
    title: 'Language 2 · DECK: a fanned hand of colour-coded cards',
    idea: 'The screen has two layers: the map, and a hand of five big cards fanned out in the bottom-right corner like cards held in a hand: Navigate, Phone, Car, Apps, Music. The front card is the one you are using; the others show only a coloured corner with their name. Turning the knob fans the hand round to bring the next card forward; push opens the front card full screen; hold shuffles back to the map. The next turn is not a card at all but a large heads-up read-out in the top-left. There is no bar of icons anywhere: a world is a colour and a card, not a button.',
    home: o => d2Home('MUSIC', {}), shapes: ['Tall rectangles with small corners and a thick coloured top edge, like index cards', 'Cards overlap and fan; depth tells you what is current', 'Focus ring: a heavy outline around the whole card or the button'],
    type: [['Hero numbers', '92 px bold (next turn)'], ['Card titles', '34 to 42 px bold, caps for labels'], ['Secondary', '25 to 28 px'], ['Labels', '28 px caps, wide spacing']],
    motion: 'Turning swings the whole hand like a fan (about 250 ms); the front card lifts and its neighbours slide behind; opening a card scales it from the corner to full screen.',
    colour: [['#2dd4bf', 'Navigate'], ['#a78bfa', 'Music'], ['#4ade80', 'Phone'], ['#fbbf24', 'Car'], ['#fb7185', 'Apps']], colourText: 'The most colourful of the three: every world owns one hue, used as a flat tinted surface with a bright top edge; the map stays dark and neutral.',
    shots: [['Home: music card in front, the hand fanned behind it', () => d2Home('MUSIC', { focus: 'front' })], ['Reaching apps: turn the knob and the Apps card swings to the front', () => d2Home('APPS', { focus: 'front' })], ['Music card: artwork box, floating pill of buttons', () => d2Music({ focus: 'play' })], ['Incoming call: the Phone card takes over', () => d2Call()], ['App list: the Apps card opened full screen', () => d2Apps(4)]],
    stripNext: { title: 'Play next song: 3 steps (push, turn, push)', frames: [[d2Home('MUSIC', { focus: 'front' }), 'Music card has focus.', 'push', 'PUSH'], [d2Home('MUSIC', { focusBtn: 'play' }), 'Inside: focus on the buttons.', 'turn', 'TURN →'], [d2Home('MUSIC', { focusBtn: 'next' }), 'Focus on next.', 'push', 'PUSH'], [d2Home('MUSIC', { title: 'Midnight Run' }), 'New song.']] },
    stripNav: { title: 'Start navigation home: 1 turn and 2 pushes', frames: [[d2Home('MUSIC', { focus: 'front' }), 'Home. Music card in front.', 'turn', 'TURN ←'], [d2Home('NAV', { focus: 'front' }), 'Navigate card swings forward, Home is first.', 'push', 'PUSH'], [d2Dest(), 'Where to? Home is highlighted.', 'push', 'PUSH'], [d2Home('MUSIC', { toast: true }), 'Route started.']] }
  },
  '3': {
    name: 'VERBS', file: 'verbs', acc: '#ffb000',
    title: 'Language 3 · VERBS: a quiet amber HUD and a command list',
    idea: 'The driver does not hunt for apps; they say what they want. The home screen is almost empty: a huge next-turn read-out, the clock, and a one-line ticker at the bottom with the song and the trip. Push the knob and a command list opens, ordered by what makes sense right now (with music playing: play next, pause, then navigate home, call Anna, radio, all apps). Turn to move, push to run, hold for voice. The app list is a plain numbered list of names in big type, not a grid of icons. Calls take over the top of the screen with the actions printed on the physical keys. The whole thing is one ink, amber on black, like an instrument panel.',
    home: o => v3Home(o), shapes: ['Square corners and 2 to 3 px rules; no rounding anywhere', 'Frames and lines instead of filled cards; text is the main shape', 'The focus is a solid amber bar with black text (plus the heavy ring on boxes)'],
    type: [['Hero numbers', '150 px mono bold (distance)'], ['Commands and names', '36 to 44 px mono bold'], ['Ticker', '26 to 30 px mono'], ['Labels', '22 to 24 px caps, wide spacing']],
    motion: 'Almost none: the list opens with a quick wipe from the left, the highlight bar snaps between rows with no easing, so the driver always sees the result at once.',
    colour: [['#ffb000', 'Ink'], ['#f4efe4', 'Text'], ['#a98319', 'Quiet'], ['#7a5a00', 'Lines'], ['#000000', 'Ground']], colourText: 'One colour only, amber on black, including the map drawn in the same ink; white is used sparingly for the most important words. Red only for a declined call.',
    shots: [['Home: map in amber ink, ticker at the bottom', () => v3Home()], ['Reaching apps: the command list opens on push', () => v3Palette(0)], ['Music: artwork box, floating square buttons', () => v3Music({ focus: 'play' })], ['Incoming call: actions are printed on the keys', () => v3Call()], ['App list: numbered names in big type', () => v3Apps(4)]],
    stripNext: { title: 'Play next song: 2 pushes', frames: [[v3Home(), 'Home.', 'push', 'PUSH'], [v3Palette(0), 'Command list: "Play next song" is first.', 'push', 'PUSH'], [v3Home({ title: 'MIDNIGHT RUN' }), 'New song, list closed.']] },
    stripNav: { title: 'Start navigation home: 2 pushes and 1 turn', frames: [[v3Home(), 'Home.', 'push', 'PUSH'], [v3Palette(0), 'Command list.', 'turn', 'TURN ×2'], [v3Palette(2), '"Navigate home" highlighted.', 'push', 'PUSH'], [v3Home({ toast: true }), 'Route started.']] }
  }
};
const knobIcon = t => ({
  push: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="9" fill="none" stroke="#6ee7a8" stroke-width="2"/><circle cx="12" cy="12" r="4" fill="#6ee7a8"/></svg>',
  turn: '<svg viewBox="0 0 24 24"><path d="M5 12a7 7 0 0 1 12-5M19 3.5V8h-4.5M19 12a7 7 0 0 1-12 5M5 20.5V16h4.5" fill="none" stroke="#6ee7a8" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>',
  pad: '<svg viewBox="0 0 24 24"><path d="M12 3v4M12 17v4M3 12h4" stroke="#6ee7a8" stroke-width="2" stroke-linecap="round"/><path d="m16 8 5 4-5 4z" fill="#6ee7a8"/></svg>'
}[t]);
const frameOf = (html, scale, acc, extra = '') => `<div class="frame" ${extra} style="width:${W * scale}px;height:${H * scale}px"><div class="screen" style="transform:scale(${scale});--acc:${acc}">${html}</div></div>`;
function strip(s, acc) {
  const out = s.frames.map((f, i) => `<div class="step">${frameOf(f[0], 300 / W, acc)}<div class="cap"><span>${i + 1}</span>${f[1]}</div></div>${f[2] ? `<div class="arrow">${knobIcon(f[2])}<b>${f[3]}</b></div>` : ''}`).join('');
  return `<div class="h2">${s.title}</div><div class="strip">${out}</div>`;
}
function stylePanel(L) {
  return `<div class="style"><h3>Shape language</h3>${L.shapes.map(x => '· ' + x).join('<br>')}<h3>Type scale (system fonts only)</h3>${L.type.map(t => `<b style="color:#e8edf8">${t[0]}</b>: ${t[1]}`).join('<br>')}<h3>Motion</h3>${L.motion}<h3>Colour</h3>${L.colour.map(c => `<span class="sw" style="background:${c[0]}" title="${c[1]}"></span>`).join('')}<div style="margin-top:6px">${L.colourText}</div></div>`;
}
function build(id, mode) {
  const L = LANGS[id];
  document.getElementById('title').textContent = L.title;
  if (mode === 'home') {
    document.body.style.width = '1360px';
    document.getElementById('idea').textContent = '';
    document.getElementById('body').innerHTML = frameOf(L.shots[0][1](), 1, L.acc).replace('class="frame"', 'class="frame" id="full"');
    return;
  }
  document.getElementById('idea').textContent = L.idea;
  const sc = 512 / W;
  const cell = (s, i) => `<div class="cell">${frameOf(s[1](), sc, L.acc)}<div class="cap"><span>${i + 1}</span>${s[0]}</div></div>`;
  const row1 = L.shots.slice(0, 3).map(cell).join('');
  const row2 = L.shots.slice(3).map((s, i) => cell(s, i + 3)).join('') + stylePanel(L);
  document.getElementById('body').innerHTML = `<div class="row">${row1}</div><div class="row">${row2}</div><div class="h2" style="margin-top:14px">How the knob does two everyday jobs (screens are 300 px wide; arrows show the knob action between them)</div>${strip(L.stripNext, L.acc)}${strip(L.stripNav, L.acc)}`;
}
