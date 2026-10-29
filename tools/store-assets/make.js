// Renders the Google Play images into store/: the 512×512 app icon and the 1024×500 feature
// graphic, from the same bell as the launcher icon. Run from the repo root:
//   node tools/store-assets/make.js
// Needs Playwright (the global @playwright/cli install works, like Mind's guide screenshots).
const fs = require('fs');
const path = require('path');

function loadPlaywright() {
    try {
        return require('playwright');
    } catch {
        const global = path.join(process.env.APPDATA || '', 'npm/node_modules/@playwright/cli/node_modules/playwright');
        return require(global);
    }
}

const { chromium } = loadPlaywright();
const OUT = path.resolve(__dirname, '../../store');
const RED = '#D93025';
// Material "notifications" bell (Apache 2.0), as in res/drawable/ic_launcher_foreground.xml.
const BELL = 'M12,22c1.1,0 2,-0.9 2,-2h-4c0,1.1 0.89,2 2,2zM18,16v-5c0,-3.07 -1.64,-5.64 -4.5,-6.32V4c0,-0.83 -0.67,-1.5 -1.5,-1.5s-1.5,0.67 -1.5,1.5v0.68C7.63,5.36 6,7.92 6,11v5l-2,2v1h16v-1l-2,-2z';

const bell = size => `<svg width="${size}" height="${size}" viewBox="0 0 24 24"><path fill="#fff" d="${BELL}"/></svg>`;

const icon = `
<body style="margin:0;width:512px;height:512px;background:${RED};display:flex;align-items:center;justify-content:center">
  ${bell(300)}
</body>`;

const feature = `
<body style="margin:0;width:1024px;height:500px;background:linear-gradient(135deg,#b3261e,${RED} 55%,#ef5350);
  display:flex;align-items:center;gap:56px;padding:0 80px;box-sizing:border-box;font-family:'Segoe UI',Roboto,Arial,sans-serif;color:#fff">
  <div style="flex:0 0 auto;width:220px;height:220px;border-radius:52px;background:rgba(255,255,255,.14);display:flex;align-items:center;justify-content:center">
    ${bell(150)}
  </div>
  <div>
    <div style="font-size:84px;font-weight:800;letter-spacing:-1px">Dio Alert</div>
    <div style="font-size:34px;font-weight:600;margin-top:8px;opacity:.95">Повітряні тривоги у вашій області</div>
    <div style="font-size:26px;margin-top:22px;opacity:.9">🛩️ Дрони &nbsp;·&nbsp; 🚀 Ракети &nbsp;·&nbsp; ✅ Відбій</div>
  </div>
</body>`;

(async () => {
    fs.mkdirSync(OUT, { recursive: true });
    // The installed Chrome: no Playwright browser download needed.
    const browser = await chromium.launch({ channel: 'chrome' });
    for (const [name, html, width, height] of [['icon-512.png', icon, 512, 512], ['feature-graphic.png', feature, 1024, 500]]) {
        const page = await browser.newPage({ viewport: { width, height } });
        await page.setContent(html);
        await page.screenshot({ path: path.join(OUT, name) });
        await page.close();
        console.log(path.join(OUT, name));
    }
    await browser.close();
})();
