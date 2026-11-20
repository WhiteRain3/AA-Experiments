// Renders the six car-screen layouts and the customisation sheet to PNG. Run: node render.js   (needs Node + Playwright)
const { chromium } = require('playwright');
const fs = require('fs'), path = require('path');
const names = { a: 'layout-a-left-rail', b: 'layout-b-bottom-dock', c: 'layout-c-top-ribbon', d: 'layout-d-see-through-rail', e: 'layout-e-floating-pills-bottom', f: 'layout-f-floating-column-right' };
(async () => {
  const sprite = fs.readFileSync(path.join(__dirname, '../src/sprite.svg'), 'utf8');
  const lib = fs.readFileSync(path.join(__dirname, 'src/lib.js'), 'utf8');
  const page0 = fs.readFileSync(path.join(__dirname, 'src/car.html'), 'utf8');
  const browser = await chromium.launch();
  for (const id of Object.keys(names)) {
    const html = page0.replace('<!--SPRITE-->', () => sprite).replace('/*LIB*/', () => lib).replace('__LAYOUT__', id);
    const page = await browser.newPage({ viewport: { width: 1680, height: 1100 }, deviceScaleFactor: 1.5 });
    page.on('pageerror', e => console.error(id, e.message));
    await page.setContent(html, { waitUntil: 'load' });
    await page.screenshot({ path: path.join(__dirname, names[id] + '.png'), fullPage: true });
    await page.close();
  }
  {
    const html = fs.readFileSync(path.join(__dirname, 'src/car.html'), 'utf8').replace('build(LAYOUT)', 'buildSheet()').replace('<!--SPRITE-->', () => sprite).replace('/*LIB*/', () => lib).replace('__LAYOUT__', 'a');
    const page = await browser.newPage({ viewport: { width: 1680, height: 1100 }, deviceScaleFactor: 1.5 });
    page.on('pageerror', e => console.error('options', e.message));
    await page.setContent(html, { waitUntil: 'load' });
    await page.screenshot({ path: path.join(__dirname, 'customisation-system.png'), fullPage: true });
    await page.close();
  }
  const extra = [['buildMusic(1)', 'music-variant-1-artwork-box.png', 1330], ['buildMusic(2)', 'music-variant-2-large-artwork.png', 1330], ['buildMusicPlacement()', 'music-card-with-presets.png', 1680]];
  for (const [call, out, width] of extra) {
    const html = fs.readFileSync(path.join(__dirname, 'src/car.html'), 'utf8').replace('build(LAYOUT)', call).replace('<!--SPRITE-->', () => sprite).replace('/*LIB*/', () => lib).replace('__LAYOUT__', 'a').replace('width:1680px;background:#0b0d12', `width:${width}px;background:#0b0d12`);
    const page = await browser.newPage({ viewport: { width, height: 1000 }, deviceScaleFactor: 1.5 });
    page.on('pageerror', e => console.error(out, e.message));
    await page.setContent(html, { waitUntil: 'load' });
    await page.screenshot({ path: path.join(__dirname, out), fullPage: true });
    await page.close();
  }
  await browser.close();
})();
