// Renders the ORBIT control-hint options (4-hints-*.png).
// Run: node render-hints.js   (needs Node + Playwright)
const { chromium } = require('playwright');
const fs = require('fs'), path = require('path');
(async () => {
  const sprite = fs.readFileSync(path.join(__dirname, '../../src/sprite.svg'), 'utf8');
  const lib = fs.readFileSync(path.join(__dirname, 'src/nl.js'), 'utf8') + '\n' + fs.readFileSync(path.join(__dirname, 'src/hints.js'), 'utf8');
  const shell = fs.readFileSync(path.join(__dirname, 'src/nl.html'), 'utf8');
  const browser = await chromium.launch();
  for (const id of ['a', 'b', 'c', 'd', 'e', 'f']) {
    const html = shell.replace('<!--SPRITE-->', () => sprite).replace('/*LIB*/', () => lib).replace("build('__LANG__')", `buildHints('${id}')`);
    const page = await browser.newPage({ viewport: { width: 1680, height: 900 }, deviceScaleFactor: 1.25 });
    page.on('pageerror', e => console.error(id, e.message));
    await page.setContent(html, { waitUntil: 'load' });
    const file = await page.evaluate(i => HINTS[i].file, id);
    await page.screenshot({ path: path.join(__dirname, `${file}.png`), fullPage: true });
    await page.close();
  }
  await browser.close();
})();
