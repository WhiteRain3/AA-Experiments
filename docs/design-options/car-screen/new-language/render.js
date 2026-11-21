// Renders the design languages (one sheet each, plus the home screen at full size).
// Run: node render.js          (all)   needs Node + Playwright
//      node render.js race     (only the ones named: 1 2 3 race)
const { chromium } = require('playwright');
const fs = require('fs'), path = require('path');
// file names: 3-verbs is the earlier third language, 3-race the racing HUD; both are kept
const files = { 1: '1-orbit', 2: '2-deck', 3: '3-verbs', race: '3-race' };
const only = process.argv.slice(2);
(async () => {
  const sprite = fs.readFileSync(path.join(__dirname, '../../src/sprite.svg'), 'utf8');
  const lib = fs.readFileSync(path.join(__dirname, 'src/nl.js'), 'utf8') + '\n' + fs.readFileSync(path.join(__dirname, 'src/race.js'), 'utf8');
  const shell = fs.readFileSync(path.join(__dirname, 'src/nl.html'), 'utf8');
  const browser = await chromium.launch();
  for (const id of Object.keys(files).filter(k => !only.length || only.includes(k))) {
    for (const mode of ['sheet', 'home']) {
      const html = shell.replace('<!--SPRITE-->', () => sprite).replace('/*LIB*/', () => lib).replace("build('__LANG__')", `build('${id}','${mode}')`);
      const page = await browser.newPage({ viewport: { width: mode === 'home' ? 1360 : 1680, height: 900 }, deviceScaleFactor: mode === 'home' ? 1 : 1.5 });
      page.on('pageerror', e => console.error(id, mode, e.message));
      await page.setContent(html, { waitUntil: 'load' });
      if (mode === 'sheet') await page.screenshot({ path: path.join(__dirname, `${files[id]}.png`), fullPage: true });
      else await (await page.$('#full')).screenshot({ path: path.join(__dirname, `${files[id]}-home.png`) });
      await page.close();
    }
  }
  await browser.close();
})();
