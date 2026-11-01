// Browser end-to-end test of the exact bundle shipped in the APK (web/dist).
// Usage: node tests/e2e.mjs [screenshotDir]
import { createRequire } from 'node:module';
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
const require = createRequire(import.meta.url);
let pw; try { pw = require('playwright'); } catch { pw = require(require('node:child_process').execSync('npm root -g').toString().trim() + '/playwright'); }
const { chromium } = pw;

const dist = path.resolve('web/dist');
const shots = process.argv[2] || 'tests/e2e-shots';
fs.mkdirSync(shots, { recursive: true });
const types = { '.html': 'text/html', '.js': 'application/javascript', '.ogg': 'audio/ogg' };
const srv = http.createServer((req, res) => {
  const f = path.join(dist, decodeURIComponent(req.url.split('?')[0]).replace(/^\/$/, '/index.html'));
  if (!fs.existsSync(f)) { res.writeHead(404); return res.end(); }
  res.writeHead(200, { 'content-type': types[path.extname(f)] || 'application/octet-stream' }); fs.createReadStream(f).pipe(res);
}).listen(0);
const url = `http://localhost:${srv.address().port}/`;

let pass = 0, fail = 0; const fails = [];
const check = (n, c, d = '') => { if (c) pass++; else { fail++; fails.push(n + (d ? ' — ' + d : '')); } };
const errors = [];

const browser = await chromium.launch({ args: ['--autoplay-policy=no-user-gesture-required'] });
// 20:9 phone in landscape, touch enabled (like a typical Android device)
const ctx = await browser.newContext({ viewport: { width: 915, height: 412 }, deviceScaleFactor: 2.625, hasTouch: true, isMobile: true });
const page = await ctx.newPage();
page.on('pageerror', e => errors.push(String(e)));
page.on('console', m => { if (m.type() === 'error') errors.push(m.text()); });
await page.goto(url);
await page.waitForTimeout(1500);

const G = (fn, arg) => page.evaluate(fn, arg);
const state = () => G(() => { const g = window.__game; return { screen: g.screen, level: g.levelId, dead: g.deadT, won: g.wonT, paused: g.paused, x: g.world?.s.p.x, alive: g.world?.s.p.alive, deaths: g.save.data.totalDeaths, unlocked: g.save.data.unlocked }; });
// logical (1280-ish x 720) -> CSS px
const toCss = async (x, y) => G(([x, y]) => { const r = document.getElementById('game').getBoundingClientRect(); const k = window.innerHeight / 720; return [r.left + x * k, r.top + y * k]; }, [x, y]);
const tap = async (x, y) => { const [cx, cy] = await toCss(x, y); await page.touchscreen.tap(cx, cy); await page.waitForTimeout(120); };
const btn = async (id) => { const b = await G((id) => window.__game.btns.find(b => b.id === id), id); if (!b) throw new Error('button not found: ' + id); await tap(b.x + b.w / 2, b.y + b.h / 2); };

// 1. boot
let s = await state();
check('boots to menu', s.screen === 'menu');
await page.screenshot({ path: `${shots}/01-menu-phone.png` });
check('logical width adapts to 20:9', (await G(() => window.__game.W)) > 1500);

// audio decoded
const audioInfo = await G(() => { const a = window.__game.audio; return { ctx: !!a.ctx, n: a.buffers ? a.buffers.size : 0 }; });
check('audio context created', audioInfo.ctx);
check('all audio files decoded (24 sfx + 7 music)', audioInfo.n === 31, String(audioInfo.n));

// 2. level select via touch
await btn('levels');
s = await state();
check('level select opens by tap', s.screen === 'levels');
await page.screenshot({ path: `${shots}/02-levels.png` });
await btn('lv1');
s = await state();
check('tap level 1 starts game', s.screen === 'play' && s.level === 1);
await page.waitForTimeout(300);
await page.screenshot({ path: `${shots}/03-level1.png` });

// 3. touch controls: hold right button with a real touch
const c = await G(() => window.__game.ctrl());
const x0 = (await state()).x;
const [rx, ry] = await toCss(c.rx, c.my);
const cdp = await ctx.newCDPSession(page);
await cdp.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [{ x: rx, y: ry, id: 1 }] });
await page.waitForTimeout(500);
const x1 = (await state()).x;
check('touch-hold right moves player right', x1 > x0 + 60, `${x0} -> ${x1}`);
// simultaneous jump (second finger)
const [jx, jy] = await toCss(c.jx, c.jy);
const yBefore = await G(() => window.__game.world.s.p.y);
await cdp.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [{ x: rx, y: ry, id: 1 }, { x: jx, y: jy, id: 2 }] });
await page.waitForTimeout(150);
const yAir = await G(() => window.__game.world.s.p.y);
check('move + jump multi-touch works', yAir < yBefore - 20, `${yBefore} -> ${yAir}`);
await cdp.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] });
await page.waitForTimeout(600);

// 4. die on the hidden spike via keyboard, then tap to retry
await page.keyboard.down('ArrowRight');
for (let i = 0; i < 60; i++) { await page.waitForTimeout(100); s = await state(); if (s.dead >= 0) break; }
await page.keyboard.up('ArrowRight');
check('player can die (hidden spike)', s.dead >= 0);
await page.waitForTimeout(250);
await page.screenshot({ path: `${shots}/04-death.png` });
check('death counted', (await state()).deaths >= 1);
await tap(640, 300);
await page.waitForTimeout(150);
s = await state();
check('tap retries immediately', s.dead < 0 && s.alive);

// 5. pause / resume via HUD button
await btn('pause');
s = await state();
check('pause button pauses', s.paused);
await page.screenshot({ path: `${shots}/05-pause.png` });
await btn('presume');
check('resume continues', !(await state()).paused);

// 6. win level 1 using the recorded route through the real loop
await G(() => window.__game.startDemo());
for (let i = 0; i < 100; i++) { await page.waitForTimeout(100); s = await state(); if (s.won >= 0) break; }
check('level 1 completed', s.won >= 0);
await page.waitForTimeout(900);
await page.screenshot({ path: `${shots}/06-win.png` });
check('level 2 unlocked', (await state()).unlocked >= 2);

// 7. settings persistence: lower music volume, reload page
await G(() => { const g = window.__game; g.screen = 'settings'; });
await page.waitForTimeout(100);
await btn('mv-'); await btn('mv-');
const vol = await G(() => window.__game.save.data.settings.music);
await page.screenshot({ path: `${shots}/07-settings.png` });
await btn('sback');
await page.reload();
await page.waitForTimeout(1200);
const after = await G(() => ({ v: window.__game.save.data.settings.music, u: window.__game.save.data.unlocked, d: window.__game.save.data.totalDeaths }));
check('volume setting persists after restart', Math.abs(after.v - vol) < 0.001 && vol < 0.6, `${vol} vs ${after.v}`);
check('progress persists after restart', after.u >= 2 && after.d >= 1);

// 8. render sample levels from each chapter and measure frame time
const fpsSamples = [];
for (const id of [11, 25, 37, 44, 52, 63, 69, 72, 80]) {
  await G((id) => window.__game.startLevel(id), id);
  await page.waitForTimeout(700);
  fpsSamples.push(await G(() => window.__game.fps));
  await page.screenshot({ path: `${shots}/level-${String(id).padStart(2, '0')}.png` });
}
check('no frame-rate collapse (>=40fps headless)', Math.min(...fpsSamples) >= 40, fpsSamples.join(','));

// 9. memorial / ending screens render
await G(() => { const g = window.__game; g.screen = 'ending'; g.endingT = 5; });
await page.waitForTimeout(300);
await page.screenshot({ path: `${shots}/08-ending.png` });
await G(() => { window.__game.screen = 'ach'; });
await page.waitForTimeout(200);
await page.screenshot({ path: `${shots}/09-achievements.png` });

check('no JS errors in console', errors.length === 0, errors.join(' | '));
console.log(`E2E: ${pass} passed, ${fail} failed`);
if (fail) console.log('FAILURES:\n  ' + fails.join('\n  '));
await browser.close(); srv.close();
process.exit(fail ? 1 : 0);
