// Renders the three design languages (one sheet each, plus the home screen at full size).
// Run: node render.js   (needs Node + Playwright)
const { chromium } = require('playwright');
const fs = require('fs'), path = require('path');
const files = { 1: 'orbit', 2: 'deck', 3: 'verbs' };
(async () => {
  const sprite = fs.readFileSync(path.join(__dirname, '../../src/sprite.svg'), 'utf8');
  const lib = fs.readFileSync(path.join(__dirname, 'src/nl.js'), 'utf8');
  const shell = fs.readFileSync(path.join(__dirname, 'src/nl.html'), 'utf8');
  const browser = await chromium.launch();
  for (const id of Object.keys(files)) {
    for (const mode of ['sheet', 'home']) {
      const html = shell.replace('<!--SPRITE-->', () => sprite).replace('/*LIB*/', () => lib).replace("build('__LANG__')", `build('${id}','${mode}')`);
      const page = await browser.newPage({ viewport: { width: mode === 'home' ? 1360 : 1680, height: 900 }, deviceScaleFactor: mode === 'home' ? 1 : 1.5 });
      page.on('pageerror', e => console.error(id, mode, e.message));
      await page.setContent(html, { waitUntil: 'load' });
      if (mode === 'sheet') await page.screenshot({ path: path.join(__dirname, `${id}-${files[id]}.png`), fullPage: true });
      else await (await page.$('#full')).screenshot({ path: path.join(__dirname, `${id}-${files[id]}-home.png`) });
      await page.close();
    }
  }
  await browser.close();
})();
