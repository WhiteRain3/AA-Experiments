// Renders src/direction-4-merged.html: one overview PNG and one PNG per phone screen. Run: node render-merged.js
const { chromium } = require('playwright');
const fs = require('fs'), path = require('path');
(async () => {
  const sprite = fs.readFileSync(path.join(__dirname, 'src/sprite.svg'), 'utf8');
  const html = fs.readFileSync(path.join(__dirname, 'src/direction-4-merged.html'), 'utf8').replace('<!--SPRITE-->', sprite);
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1340, height: 1000 }, deviceScaleFactor: 2 });
  await page.setContent(html, { waitUntil: 'load' });
  await page.screenshot({ path: path.join(__dirname, 'merged-overview.png') });
  const names = ['merged-1-customise.png', 'merged-2-battery.png', 'merged-3-behind-the-scenes.png'];
  const phones = await page.$$('.phone');
  for (let i = 0; i < phones.length; i++) await phones[i].screenshot({ path: path.join(__dirname, names[i]) });
  await browser.close();
})();
