/**
 * Automated test suite (node tools/run-ts.mjs tests/run-all.ts)
 *  1. all 80 levels load + pass structural validation
 *  2. every level has a recorded solver route that replays to a real win
 *  3. reset/respawn leaves no residual trap state (fresh vs reset world identical)
 *  4. checkpoint respawn works
 *  5. trap preset coverage (25 required + >=15 original are used in levels)
 *  6. save manager persistence / corruption recovery
 *  7. achievements / death statistics
 *  8. ad service contract (mock requires explicit user completion)
 *  9. Game state machine smoke test with a headless renderer (menu → play → death → retry → win → save)
 */
import fs from 'node:fs';
import { LEVELS } from '../TrustNothing/assets/scripts/levels/index';
import { loadLevel } from '../TrustNothing/assets/scripts/core/levelLoader';
import { validateLevel } from '../TrustNothing/assets/scripts/core/levelValidator';
import { replay, ACTIONS, STEP } from '../TrustNothing/assets/scripts/core/solver';
import { World } from '../TrustNothing/assets/scripts/core/world';
import { PRESETS, REQUIRED_TRAPS, ORIGINAL_TRAPS } from '../TrustNothing/assets/scripts/core/presets';
import { SaveManager, IStorage, MockAdService, AchievementManager, DEATH_QUOTES } from '../TrustNothing/assets/scripts/core/services';
import { Game } from '../TrustNothing/assets/scripts/core/game';
import { IRenderer } from '../TrustNothing/assets/scripts/core/render';

let pass = 0, fail = 0;
const failures: string[] = [];
function check(name: string, cond: boolean, detail = '') {
  if (cond) pass++; else { fail++; failures.push(name + (detail ? ' — ' + detail : '')); }
}

const sols: Record<string, number[]> = JSON.parse(fs.readFileSync('tests/solutions.json', 'utf8'));

// 1-2. levels
check('exactly 80 levels', LEVELS.length === 80, String(LEVELS.length));
const ids = LEVELS.map(l => l.id);
check('level ids are 1..80 in order', ids.every((v, i) => v === i + 1));
const names = new Set(LEVELS.map(l => l.name));
check('level names unique', names.size === 80, String(names.size));
const mapSigs = new Set(LEVELS.map(l => l.map.join('\n')));
check('level maps unique', mapSigs.size === 80);
const usedPresets = new Set<string>();
let routeTime = 0;
for (const def of LEVELS) {
  const bp = loadLevel(def);
  const v = validateLevel(def, bp);
  check(`L${def.id} valid`, v.ok, v.errors.join('; '));
  for (const s of Object.values(def.defs || {})) usedPresets.add(s.t);
  const sol = sols[def.id];
  check(`L${def.id} has recorded route`, !!sol);
  if (sol) {
    const r = replay(bp, sol);
    check(`L${def.id} route wins`, r.won, r.died ? 'died at frame ' + r.frame : 'no win');
    routeTime += r.frame / 60;
  }
}

// level 80 must go through the fake grand ending and the hidden tunnel
{
  const def = LEVELS[79];
  const w = new World(loadLevel(def));
  const seen = new Set<string>();
  for (const a of sols[80] || []) for (let k = 0; k < STEP; k++) { w.step(ACTIONS[a]); for (const f of w.fx) seen.add(f.type === 'sfx' ? 'sfx:' + f.name : f.type); w.fx.length = 0; }
  check('L80 route triggers fake ending', seen.has('fakeEnding'));
  check('L80 route reaches real win', seen.has('win'));
  check('L80 flagged final', !!def.final);
}
// level 40 twist: real door is behind the start, reached after the button teleport
check('L40 has goal left of start', (() => { const bp = loadLevel(LEVELS[39]); const g = bp.ents.find(e => e.kind === 'goal' && e.props.real)!; return g.x < bp.start.x; })());

// 3. reset integrity: play 3 seconds of random-ish input that triggers traps, reset, compare with fresh world
for (const id of [1, 9, 13, 19, 34, 40, 50, 64, 72, 80]) {
  const bp = loadLevel(LEVELS[id - 1]);
  const a = new World(bp);
  for (let i = 0; i < 400; i++) a.step({ left: false, right: true, jump: i % 50 < 12 });
  a.reset(null);
  const b = new World(bp);
  const sa = JSON.stringify({ e: a.s.ents.map(e => [e.id, e.x, e.y, e.vis, e.solid, e.anim, e.look, e.deadly]), t: a.s.trigs.map(t => t.fired), q: a.s.queue.length, g: a.s.gsign, r: a.s.reverseT, p: [a.s.p.x, a.s.p.y], m: a.s.mirror, d: a.s.dark, tg: a.s.toggle });
  const sb = JSON.stringify({ e: b.s.ents.map(e => [e.id, e.x, e.y, e.vis, e.solid, e.anim, e.look, e.deadly]), t: b.s.trigs.map(t => t.fired), q: b.s.queue.length, g: b.s.gsign, r: b.s.reverseT, p: [b.s.p.x, b.s.p.y], m: b.s.mirror, d: b.s.dark, tg: b.s.toggle });
  check(`L${id} reset restores pristine state`, sa === sb);
  check(`L${id} player controllable after reset`, a.s.p.alive && !a.s.won && a.s.freezeT <= 0);
}

// 4. checkpoint respawn
{
  const bp = loadLevel(LEVELS[60]); // L61 has a checkpoint
  const w = new World(bp);
  const cp = w.s.ents.find(e => e.kind === 'checkpoint' && !e.props.fake)!;
  w.s.p.x = cp.x; w.s.p.y = cp.y + cp.h - 30;
  w.step({ left: false, right: false, jump: false });
  check('checkpoint activates on touch', !!w.s.checkpoint);
  const saved = w.s.checkpoint;
  w.kill('test');
  w.reset(saved);
  check('respawn at checkpoint', Math.abs(w.s.p.x - saved!.x) < 1 && w.s.p.alive);
  check('checkpoint stays active after respawn', w.s.ents.find(e => e.id === cp.id)!.st.on === true);
}

// 5. trap coverage
const missingReq = REQUIRED_TRAPS.filter(t => !PRESETS[t]);
check('25 required traps implemented', REQUIRED_TRAPS.length === 25 && missingReq.length === 0, missingReq.join(','));
check('>=15 original traps implemented', ORIGINAL_TRAPS.length >= 15 && ORIGINAL_TRAPS.every(t => PRESETS[t]));
const unusedReq = REQUIRED_TRAPS.filter(t => !usedPresets.has(t) && !['fakeWin', 'jumpTrap'].includes(t));
check('every required trap used in a level', unusedReq.length === 0, unusedReq.join(','));
const usedOriginal = ORIGINAL_TRAPS.filter(t => usedPresets.has(t));
check('>=15 original traps used in levels', usedOriginal.length >= 15, `${usedOriginal.length}: ${usedOriginal.join(',')}`);
// fakeWin / jumpTrap are also used through the generic action/trigger system
const allActs = LEVELS.flatMap(l => [...(l.triggers || []).flatMap(t => t.do.map(a => a.a)), ...Object.values(l.defs || {}).flatMap((d: any) => (d.then || []).concat(d.do || []).map((a: any) => a.a))]);
check('fake-win sequence used', allActs.includes('fakeWin') && allActs.includes('fakeEnding'));
check('jump-triggered traps used', LEVELS.some(l => (l.triggers || []).some(t => t.on === 'jump')) && LEVELS.some(l => Object.values(l.defs || {}).some((d: any) => d.on === 'jump')));

// 6. save manager
class MemStore implements IStorage { m = new Map<string, string>(); get(k: string) { return this.m.get(k) ?? null; } set(k: string, v: string) { this.m.set(k, v); } }
{
  const st = new MemStore();
  const s1 = new SaveManager(st);
  s1.data.unlocked = 12; s1.data.totalDeaths = 77; s1.data.cleared[3] = { deaths: 4, best: 1, perfect: false }; s1.data.settings.music = 0.3;
  s1.markDirty(); s1.flush();
  const s2 = new SaveManager(st);
  check('save persists progress', s2.data.unlocked === 12 && s2.data.totalDeaths === 77 && s2.data.cleared[3]?.deaths === 4);
  check('save persists settings', s2.data.settings.music === 0.3);
  st.set('trustnothing.save.v1', '{corrupted');
  const s3 = new SaveManager(st);
  check('corrupted save falls back to backup', s3.data.unlocked === 12);
  s3.reset();
  check('reset progress keeps settings', s3.data.unlocked === 1 && s3.data.settings.music === 0.3);
}

// 7. achievements
{
  const sm = new SaveManager(new MemStore());
  const am = new AchievementManager(sm);
  am.unlock('first_death'); am.unlock('first_death');
  check('achievement unlock once', am.queue.length === 1 && am.has('first_death'));
  check('>=30 death quotes', DEATH_QUOTES.length >= 30, String(DEATH_QUOTES.length));
}

// 8. ad contract
{
  let opened: ((ok: boolean) => void) | null = null;
  const ads = new MockAdService({ openMockAd: (_p, done) => { opened = done; } });
  let completed = 0, failed = 0;
  ads.showRewardedAd('hint', { onRewardedAdCompleted: () => completed++, onRewardedAdFailed: () => failed++ });
  check('mock ad does not auto-complete', completed === 0 && failed === 0 && !!opened);
  opened!(false);
  check('closing mock ad reports failure (no reward)', failed === 1 && completed === 0);
  check('mock ad is flagged test mode', ads.isTestMode);
}

// 9. Game state machine smoke test (headless renderer)
{
  const calls = { n: 0 };
  const r: IRenderer = {
    width: 1280, height: 720, begin() {}, end() {}, clear() { calls.n++; }, save() {}, restore() {}, translate() {}, scale() {}, rotate() {}, alpha() {},
    rect() { calls.n++; }, roundRect() { calls.n++; }, circle() { calls.n++; }, poly() { calls.n++; }, line() { calls.n++; }, text() { calls.n++; }, measure: (s: string, z: number) => s.length * z * 0.6,
  };
  const sfx: string[] = []; const music: string[] = [];
  const audio = { playSfx: (n: string) => sfx.push(n), playMusic: (t: string) => music.push(t), stopMusic() {}, setVolumes() {} };
  const st = new MemStore();
  const g = new Game({ renderer: r, audio, storage: st, levels: LEVELS, solutions: sols, platform: 'test', version: 'test' });
  g.frame(0.016);
  check('menu renders', g.screen === 'menu' && calls.n > 10);
  check('menu music plays', music[0] === 'menu');
  g.startLevel(1);
  g.frame(0.016);
  check('level starts', g.screen === 'play' && !!g.world);
  // run into the level-1 hidden spike to die
  g.keys.right = true;
  for (let i = 0; i < 600 && g.deadT < 0; i++) g.frame(1 / 60);
  check('death detected', g.deadT >= 0 && g.save.data.totalDeaths === 1);
  check('death quote shown', g.deathQuote.length > 0);
  g.keys.right = false;
  for (let i = 0; i < 90; i++) g.frame(1 / 60);
  check('auto respawn after death', g.deadT < 0 && g.world!.s.p.alive);
  // play recorded solution through the real Game loop (demo mode)
  g.startDemo();
  for (let i = 0; i < 60 * 30 && g.wonT < 0; i++) g.frame(1 / 60);
  check('level 1 cleared via game loop', g.wonT >= 0);
  check('level 2 unlocked + saved', g.save.data.unlocked >= 2 && JSON.parse(st.get('trustnothing.save.v1')!).unlocked >= 2);
  check('win sfx played', sfx.includes('win'));
  // pause / resume
  g.startLevel(2); g.frame(0.016);
  g.paused = true; const x0 = g.world!.s.p.x; g.keys.right = true; for (let i = 0; i < 30; i++) g.frame(1 / 60);
  check('pause freezes simulation', g.world!.s.p.x === x0);
  g.paused = false; for (let i = 0; i < 30; i++) g.frame(1 / 60);
  check('resume continues', g.world!.s.p.x > x0);
  g.keys.right = false;
  // many deaths don't leak entities/particles (performance)
  const entCount = g.world!.s.ents.length;
  for (let k = 0; k < 40; k++) { g.world!.kill('test'); g.frame(1 / 60); g.respawn(); g.frame(1 / 60); }
  check('repeated deaths keep entity count stable', g.world!.s.ents.length === entCount);
  check('particles bounded', g.particles.list.length < 300);
  check('streak achievement after 30+ deaths', g.ach.has('streak30'));
  // every screen renders without throwing
  for (const scr of ['levels', 'settings', 'help', 'ach', 'ending', 'memorial', 'menu'] as const) { g.screen = scr; let ok = true; try { g.frame(0.016); } catch (e) { ok = false; } check(`screen ${scr} renders`, ok); }
  // every level renders its first frame
  let renderOk = true;
  for (const l of LEVELS) { try { g.startLevel(l.id); for (let i = 0; i < 3; i++) g.frame(1 / 60); } catch (e: any) { renderOk = false; failures.push(`render L${l.id}: ${e.message}`); } }
  check('all 80 levels render', renderOk);
}

console.log(`\n${pass} passed, ${fail} failed. Recorded routes total ${(routeTime / 60).toFixed(1)} min of play.`);
if (fail) { console.log('FAILURES:\n  ' + failures.join('\n  ')); process.exit(1); }
