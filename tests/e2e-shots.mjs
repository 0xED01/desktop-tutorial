// Quick visual smoke test: serves web/dist, takes screenshots of key screens.
import { createRequire } from 'node:module';
const require = createRequire(import.meta.url);
let pw; try { pw = require('playwright'); } catch { pw = require(require('node:child_process').execSync('npm root -g').toString().trim() + '/playwright'); }
const { chromium } = pw;
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
const dist = path.resolve('web/dist');
const outDir = process.argv[2] || 'shots';
fs.mkdirSync(outDir, { recursive: true });
const types = { '.html': 'text/html', '.js': 'application/javascript', '.ogg': 'audio/ogg' };
const srv = http.createServer((req, res) => {
  const f = path.join(dist, decodeURIComponent(req.url.split('?')[0]).replace(/^\/$/, '/index.html'));
  if (!fs.existsSync(f)) { res.writeHead(404); return res.end(); }
  res.writeHead(200, { 'content-type': types[path.extname(f)] || 'application/octet-stream' }); fs.createReadStream(f).pipe(res);
}).listen(0);
const port = srv.address().port;
const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1280, height: 720 } });
const errors = [];
page.on('pageerror', e => errors.push(String(e)));
page.on('console', m => { if (m.type() === 'error') errors.push(m.text()); });
await page.goto(`http://localhost:${port}/`);
await page.waitForTimeout(800);
await page.screenshot({ path: `${outDir}/menu.png` });
const levels = (process.argv[3] || '1').split(',').map(Number);
for (const id of levels) {
  await page.evaluate((id) => window.__game.startLevel(id), id);
  await page.waitForTimeout(400);
  await page.screenshot({ path: `${outDir}/level${id}.png` });
}
await page.evaluate(() => window.__game.goLevels());
await page.waitForTimeout(200);
await page.screenshot({ path: `${outDir}/levels.png` });
console.log('errors:', errors.length ? errors : 'none');
await browser.close(); srv.close();
