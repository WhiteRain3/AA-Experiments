// Renders each src/direction-*.html to a PNG next to this file. Run: node render.js
const { chromium } = require('playwright');
const fs = require('fs'), path = require('path');
(async () => {
  const sprite = fs.readFileSync(path.join(__dirname, 'src/sprite.svg'), 'utf8');
  const browser = await chromium.launch();
  for (const f of fs.readdirSync(path.join(__dirname, 'src')).filter(n => /^direction-.*\.html$/.test(n))) {
    const html = fs.readFileSync(path.join(__dirname, 'src', f), 'utf8').replace('<!--SPRITE-->', sprite);
    const page = await browser.newPage({ viewport: { width: 940, height: 980 }, deviceScaleFactor: 2 });
    await page.setContent(html, { waitUntil: 'load' });
    await page.screenshot({ path: path.join(__dirname, f.replace(/\.html$/, '.png')) });
    await page.close();
  }
  await browser.close();
})();
