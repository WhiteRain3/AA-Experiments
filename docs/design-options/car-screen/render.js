// Renders the three car-screen layouts to PNG. Run: node render.js   (needs Node + Playwright)
const { chromium } = require('playwright');
const fs = require('fs'), path = require('path');
const names = { a: 'layout-a-left-rail', b: 'layout-b-bottom-dock', c: 'layout-c-top-ribbon' };
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
  await browser.close();
})();
