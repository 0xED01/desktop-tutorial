/**
 * GameManager + UIManager (engine-agnostic). Platform layers feed it
 * frame(dt), pointer(...) and key(...) and implement IRenderer/IAudio/IStorage.
 */
import { IRenderer, mix, rgba } from './render';
import { World } from './world';
import { Blueprint, loadLevel } from './levelLoader';
import { FIXED_DT, Fx, InputState, LevelDef, TILE } from './types';
import { THEMES, Theme, Particles, drawBackground, drawEnt, drawPlayer, drawTiles, tileRuns, drawDark, INK } from './draw';
import {
  ACHIEVEMENTS, AchievementManager, AdService, CHAPTERS, DEATH_QUOTES, FAKE_GOAL_QUOTES, IAudio, IStorage, MockAdHost,
  MockAdService, STREAK10_QUOTES, STREAK30_QUOTES, SaveManager, musicForLevel,
} from './services';
import { ACTIONS, STEP } from './solver';

type Screen = 'menu' | 'levels' | 'settings' | 'help' | 'ach' | 'play' | 'ending' | 'memorial';

interface Btn { id: string; x: number; y: number; w: number; h: number; cb: () => void; }

export interface GameDeps {
  renderer: IRenderer;
  audio: IAudio;
  storage: IStorage;
  levels: LevelDef[];
  solutions?: Record<string, number[]>;
  platform: string;
  version: string;
  ads?: AdService;
  exitApp?: () => void;
}

const H = 720;

export class Game implements MockAdHost {
  r: IRenderer; audio: IAudio; save: SaveManager; ach: AchievementManager; ads: AdService;
  levels: LevelDef[];
  screen: Screen = 'menu';
  safe = { l: 0, r: 0, t: 0, b: 0 };
  time = 0;

  // play state
  levelId = 1;
  bp: Blueprint | null = null;
  world: World | null = null;
  runs: ReturnType<typeof tileRuns> = [];
  theme: Theme = THEMES[0];
  acc = 0;
  camX = 0; camY = 0; shakeT = 0; shakeV = 0;
  particles = new Particles();
  input: InputState = { left: false, right: false, jump: false };
  keys = { left: false, right: false, jump: false };
  touches = new Map<number, 'left' | 'right' | 'jump' | 'ui'>();
  paused = false;
  deadT = -1; deathQuote = ''; deathFace = 0;
  slowT = 0;
  wonT = -1;
  visitDeaths = 0; streak = 0; sessionFake = 0;
  sayText = ''; sayT = 0;
  fakeWinT = -1; fakeEndT = -1;
  hintText = ''; showHint = false;
  shieldCharges = 0;
  demo: number[] | null = null; demoIdx = 0; demoFrame = 0;
  levelTime = 0;

  // ui
  btns: Btn[] = [];
  pressed: string | null = null;
  pressedPtr = -1;
  chapterView = 0;
  toast: { name: string; desc: string; t: number } | null = null;
  confirm: { text: string; yes: () => void } | null = null;
  mockAd: { placement: string; t: number; done: (ok: boolean) => void } | null = null;
  endingT = 0;
  fps = 60; fpsAcc = 0; fpsN = 0;
  saveTimer = 0;
  lastMusic = '';

  constructor(private deps: GameDeps) {
    this.r = deps.renderer; this.audio = deps.audio; this.levels = deps.levels;
    this.save = new SaveManager(deps.storage);
    this.ach = new AchievementManager(this.save);
    this.ads = deps.ads || new MockAdService(this);
    this.applyAudio();
    this.goMenu();
  }

  get W() { return this.r.width; }
  get settings() { return this.save.data.settings; }

  applyAudio() {
    const s = this.settings;
    this.audio.setVolumes(s.music, s.sfx, s.musicOn, s.sfxOn);
  }
  music(track: string) { if (this.lastMusic !== track) { this.lastMusic = track; this.audio.playMusic(track); } }
  sfx(n: string) { this.audio.playSfx(n); }

  /* ================= navigation ================= */
  goMenu() { this.screen = 'menu'; this.world = null; this.paused = false; this.music('menu'); }
  goLevels() { this.screen = 'levels'; this.world = null; this.paused = false; this.chapterView = Math.floor((Math.min(80, this.save.data.unlocked) - 1) / 10); this.music('menu'); }

  startLevel(id: number) {
    const def = this.levels.find(l => l.id === id);
    if (!def) return;
    this.levelId = id;
    this.bp = loadLevel(def);
    this.theme = THEMES[def.theme ?? Math.floor((id - 1) / 10)] || THEMES[0];
    this.runs = [];
    this.world = new World(this.bp);
    this.runs = tileRuns(this.world);
    this.screen = 'play';
    this.paused = false; this.deadT = -1; this.wonT = -1; this.fakeWinT = -1; this.fakeEndT = -1;
    this.visitDeaths = 0; this.streak = 0; this.sessionFake = 0; this.showHint = false; this.levelTime = 0;
    this.particles.list.length = 0;
    this.acc = 0;
    this.demo = null;
    this.prepAttempt();
    this.snapCamera();
    this.music(def.music || musicForLevel(id));
    if (def.intro) this.say(def.intro, 3.2);
  }

  private prepAttempt() {
    const w = this.world!;
    if (this.shieldCharges > 0) w.s.shield = true;
    this.consumeFx();
  }

  respawn() {
    if (!this.world) return;
    const cp = this.world.s.checkpoint;
    this.world.reset(cp);
    this.deadT = -1; this.slowT = 0;
    this.fakeWinT = -1; this.fakeEndT = -1;
    this.prepAttempt();
    this.sfx('respawn');
    this.snapCamera(true);
  }

  restartLevel() { if (this.world) { this.world.reset(null); this.deadT = -1; this.fakeWinT = -1; this.fakeEndT = -1; this.prepAttempt(); this.snapCamera(); } }

  /* ================= input ================= */
  key(code: string, down: boolean) {
    this.audio.unlock?.();
    const k = code.toLowerCase();
    if (k === 'arrowleft' || k === 'keya') this.keys.left = down;
    else if (k === 'arrowright' || k === 'keyd') this.keys.right = down;
    else if (k === 'space' || k === 'arrowup' || k === 'keyw' || k === 'keyk' || k === 'keyj') this.keys.jump = down;
    else if (down && k === 'keyr' && this.screen === 'play') { this.restartLevel(); }
    else if (down && (k === 'escape' || k === 'keyp')) { if (this.screen === 'play' && this.wonT < 0) this.paused = !this.paused; else if (this.screen !== 'menu') this.goMenu(); }
    else if (down && k === 'enter' && this.screen === 'play' && this.wonT > 0.6) this.nextLevel();
    else if (down && this.settings.debug && this.screen === 'play' && k === 'bracketright') this.startLevel(Math.min(80, this.levelId + 1));
    else if (down && this.settings.debug && this.screen === 'play' && k === 'bracketleft') this.startLevel(Math.max(1, this.levelId - 1));
    else if (down && this.settings.debug && this.screen === 'play' && k === 'keyo') this.startDemo();
  }

  pointer(type: 'down' | 'move' | 'up', id: number, x: number, y: number) {
    this.audio.unlock?.();
    if (type === 'down') {
      const b = this.hit(x, y);
      if (b) { this.pressed = b.id; this.pressedPtr = id; this.touches.set(id, 'ui'); return; }
      if (this.screen === 'play' && this.world && !this.paused && !this.mockAd && !this.confirm) {
        if (this.deadT >= 0 && this.deadT > 0.15) { this.respawn(); this.touches.set(id, 'ui'); return; }
        this.touches.set(id, this.zoneAt(x, y));
      }
    } else if (type === 'move') {
      const t = this.touches.get(id);
      if (t === 'left' || t === 'right') { const z = this.zoneAt(x, y); if (z === 'left' || z === 'right') this.touches.set(id, z); }
    } else {
      const t = this.touches.get(id);
      this.touches.delete(id);
      if (t === 'ui' && this.pressedPtr === id) {
        const b = this.hit(x, y);
        if (b && b.id === this.pressed) { this.sfx('click'); b.cb(); }
        this.pressed = null; this.pressedPtr = -1;
      }
    }
  }

  private zoneAt(x: number, y: number): 'left' | 'right' | 'jump' | 'ui' {
    const W = this.W;
    const lh = this.settings.leftHanded;
    const moveSide = lh ? x > W * 0.55 : x < W * 0.45;
    const jumpSide = lh ? x < W * 0.4 : x > W * 0.6;
    if (moveSide) {
      const c = this.ctrl();
      const mid = (c.lx + c.rx) / 2;
      return x < mid ? 'left' : 'right';
    }
    if (jumpSide) return 'jump';
    void y;
    return 'jump';
  }

  ctrl() {
    const W = this.W, s = this.safe;
    const lh = this.settings.leftHanded;
    const baseL = lh ? W - s.r - 350 : s.l + 30;
    return { lx: baseL + 85, rx: baseL + 255, my: H - s.b - 100, jx: lh ? s.l + 140 : W - s.r - 140, jy: H - s.b - 110, rr: 66, jr: 82 };
  }

  private hit(x: number, y: number): Btn | null {
    for (let i = this.btns.length - 1; i >= 0; i--) { const b = this.btns[i]; if (x >= b.x && x <= b.x + b.w && y >= b.y && y <= b.y + b.h) return b; }
    return null;
  }

  private collectInput(): InputState {
    let l = this.keys.left, rr = this.keys.right, j = this.keys.jump;
    for (const t of this.touches.values()) { if (t === 'left') l = true; else if (t === 'right') rr = true; else if (t === 'jump') j = true; }
    return { left: l, right: rr, jump: j };
  }

  /* ================= mock ad host ================= */
  openMockAd(placement: string, onDone: (completed: boolean) => void) {
    this.mockAd = { placement, t: 0, done: onDone };
    this.paused = true;
  }
  requestAd(placement: 'hint' | 'shield') {
    this.ads.showRewardedAd(placement, {
      onRewardedAdCompleted: () => {
        if (placement === 'hint') { this.hintText = this.levels.find(l => l.id === this.levelId)?.hint || '多试几次！'; this.showHint = true; }
        else { this.shieldCharges = 1; if (this.world) this.world.s.shield = true; this.say('获得护盾：可抵挡一次尖刺/锯片伤害（掉落无效）', 2.5); }
        this.paused = false;
      },
      onRewardedAdFailed: () => { this.paused = false; this.say('没关系，直接再来一次也可以！', 1.8); },
    });
  }

  say(text: string, dur = 2.2) { this.sayText = text; this.sayT = dur; }

  /* ================= frame ================= */
  frame(dtReal: number) {
    const dt = Math.min(0.1, Math.max(0, dtReal));
    this.time += dt;
    runPending();
    this.fpsAcc += dt; this.fpsN++;
    if (this.fpsAcc > 0.5) { this.fps = Math.round(this.fpsN / this.fpsAcc); this.fpsAcc = 0; this.fpsN = 0; }
    if (this.toast) { this.toast.t += dt; if (this.toast.t > 3) this.toast = null; }
    if (!this.toast && this.ach.queue.length) { const a = this.ach.queue.shift()!; this.toast = { name: a.name, desc: a.desc, t: 0 }; this.sfx('achievement'); }
    if (this.mockAd) this.mockAd.t += dt;
    this.saveTimer += dt;
    if (this.saveTimer > 2) { this.saveTimer = 0; this.save.flush(); }

    if (this.screen === 'play' && this.world) this.updatePlay(dt);
    if (this.screen === 'ending') this.endingT += dt;
    this.particles.update(dt);
    this.render();
  }

  private updatePlay(dt: number) {
    const w = this.world!;
    if (this.paused) return;
    this.save.data.playTime += dt;
    this.levelTime += dt;
    if (this.sayT > 0) this.sayT -= dt;
    if (this.shakeT > 0) this.shakeT -= dt;
    let scale = 1;
    if (this.slowT > 0) { this.slowT -= dt; scale = 0.3; }
    this.acc += dt * scale;
    let steps = 0;
    while (this.acc >= FIXED_DT && steps < 6) {
      this.acc -= FIXED_DT; steps++;
      let inp = this.collectInput();
      if (this.demo) {
        const a = this.demo[this.demoIdx];
        if (a === undefined) { this.demo = null; } else {
          inp = ACTIONS[a];
          if (++this.demoFrame >= STEP) { this.demoFrame = 0; this.demoIdx++; }
        }
      }
      w.step(inp);
      this.consumeFx();
      if (this.deadT >= 0 || this.wonT >= 0) { /* keep simulating for anims */ }
    }
    if (steps >= 6) this.acc = 0;
    if (this.deadT >= 0) {
      this.deadT += dt;
      if (this.deadT > 1.15) this.respawn();
    }
    if (this.fakeWinT >= 0) { this.fakeWinT += dt; if (this.fakeWinT > 1.8) this.fakeWinT = -1; }
    if (this.fakeEndT >= 0) { this.fakeEndT += dt; if (this.fakeEndT > 4.4) this.fakeEndT = -1; }
    if (this.wonT >= 0) {
      this.wonT += dt;
      if (this.levelId === 80 && this.wonT > 1.6 && this.screen === 'play') { this.screen = 'ending'; this.endingT = 0; this.music('victory'); this.particles.confetti(this.W, 120); }
    }
    this.updateCamera(dt);
  }

  private consumeFx() {
    const w = this.world; if (!w) return;
    for (const f of w.fx) this.onFx(f);
    w.fx.length = 0;
  }

  private onFx(f: Fx) {
    switch (f.type) {
      case 'sfx': this.sfx(f.name); break;
      case 'shake': if (this.settings.shake) { this.shakeT = 0.25; this.shakeV = Math.max(this.shakeV * (this.shakeT > 0 ? 1 : 0), f.v); } break;
      case 'burst': this.particles.burst(f.x, f.y, f.color === 'player' ? INK : f.color === 'spike' ? '#1f1f29' : f.color === 'gold' ? '#ffd166' : f.color === 'portal' ? '#9b5de5' : f.color === 'brick' ? '#b5654a' : this.theme.ground, f.n); break;
      case 'say': this.say(f.text, f.dur); break;
      case 'fakeWin': this.fakeWinT = 0; this.particles.confetti(this.W, 60); this.save.data.fakeGoals++; this.save.markDirty(); this.checkFooled(); break;
      case 'fakeEnding': this.fakeEndT = 0; this.music('victory'); setTimeoutSafe(() => { if (this.screen === 'play') this.music(musicForLevel(this.levelId)); }, 4400, this); break;
      case 'fakeGoal': {
        this.sessionFake++; this.save.data.fakeGoals++; this.save.markDirty();
        this.checkFooled();
        const q = FAKE_GOAL_QUOTES[Math.min(this.sessionFake - 1, FAKE_GOAL_QUOTES.length - 1)];
        if (this.sessionFake > 1) this.say(q, 2);
        break;
      }
      case 'checkpoint': this.say('检查点已激活', 1.2); break;
      case 'surprise': break;
      case 'death': this.onDeath(); break;
      case 'win': this.onWin(); break;
    }
    if (f.type === 'sfx' && f.name === 'coin') { this.save.data.baitCoins++; if (this.save.data.baitCoins >= 10) this.ach.unlock('greedy'); }
    if (f.type === 'sfx' && f.name === 'shield') { this.shieldCharges = Math.max(0, this.shieldCharges - 1); this.say('护盾替你挡了一下！', 1.5); }
  }

  private checkFooled() {
    if (this.save.data.fakeGoals >= 5) this.ach.unlock('fooled5');
    if (this.save.data.fakeGoals >= 20) this.ach.unlock('fooled20');
  }

  private onDeath() {
    const d = this.save.data;
    this.deadT = 0; this.slowT = 0.22;
    this.deathFace = Math.floor(Math.random() * 4);
    this.visitDeaths++; this.streak++;
    d.totalDeaths++; d.levelDeaths[this.levelId] = (d.levelDeaths[this.levelId] || 0) + 1;
    this.save.markDirty();
    this.ach.unlock('first_death');
    if (d.totalDeaths >= 100) this.ach.unlock('deaths100');
    if (d.totalDeaths >= 500) this.ach.unlock('deaths500');
    if (d.totalDeaths >= 1000) this.ach.unlock('deaths1000');
    if (this.streak === 10) { this.ach.unlock('streak10'); this.deathQuote = pick(STREAK10_QUOTES); }
    else if (this.streak === 30) { this.ach.unlock('streak30'); this.deathQuote = pick(STREAK30_QUOTES); }
    else {
      let q = pick(DEATH_QUOTES);
      if (q === this.deathQuote) q = pick(DEATH_QUOTES);
      this.deathQuote = q;
    }
    this.sayT = 0;
  }

  private onWin() {
    const d = this.save.data, id = this.levelId;
    this.wonT = 0;
    const perfect = this.visitDeaths === 0;
    const prev = d.cleared[id];
    d.cleared[id] = { deaths: (prev?.deaths || 0) + this.visitDeaths, best: prev ? Math.min(prev.best, this.visitDeaths) : this.visitDeaths, perfect: perfect || !!prev?.perfect };
    d.unlocked = Math.max(d.unlocked, Math.min(80, id + 1));
    d.jumps += this.world!.s.jumps;
    this.streak = 0;
    this.ach.unlock('first_clear');
    if (perfect) this.ach.unlock('perfect');
    if (Object.values(d.cleared).filter(c => c.perfect).length >= 10) this.ach.unlock('perfect10');
    for (let c = 0; c < 8; c++) { let all = true; for (let k = c * 10 + 1; k <= c * 10 + 10; k++) if (!d.cleared[k]) all = false; if (all) this.ach.unlock('ch' + (c + 1)); }
    if (id === 80) { d.trueEnding = true; this.ach.unlock('true_end'); }
    if (Object.keys(d.cleared).length >= 80) this.ach.unlock('all80');
    this.save.markDirty(); this.save.flush();
    this.particles.confetti(this.W, 70);
    this.music('victory_short');
  }

  nextLevel() {
    if (this.levelId >= 80) { this.screen = 'ending'; this.endingT = 0; return; }
    this.startLevel(this.levelId + 1);
  }

  startDemo() {
    const sol = this.deps.solutions?.[this.levelId];
    if (!sol) { this.say('没有录制的通关路线', 1.5); return; }
    this.world!.reset(null); this.deadT = -1; this.wonT = -1; this.consumeFx();
    this.demo = sol; this.demoIdx = 0; this.demoFrame = 0; this.acc = 0;
    this.say('自动演示：系统求解的通关路线', 2);
  }

  /* ================= camera ================= */
  private snapCamera(soft = false) {
    const t = this.camTarget();
    if (!soft) { this.camX = t.x; this.camY = t.y; }
  }
  private camTarget() {
    const w = this.world!, p = w.s.p, bp = this.bp!;
    const W = this.W;
    let x = p.x + p.w / 2 - W * 0.42 + p.facing * 40;
    if (bp.width <= W) x = (bp.width - W) / 2; else x = Math.max(0, Math.min(bp.width - W, x));
    // keep 150px of extended ground below the map so touch controls never cover the action
    const yMax = bp.height - H + 150;
    let y = (w.s.gsign > 0 ? p.y - H * 0.55 : p.y - H * 0.4);
    y = Math.max(Math.min(0, yMax), Math.min(yMax, y));
    return { x, y };
  }
  private updateCamera(dt: number) {
    const t = this.camTarget();
    const k = 1 - Math.pow(0.0008, dt);
    this.camX += (t.x - this.camX) * k;
    this.camY += (t.y - this.camY) * k;
  }

  /* ================= rendering ================= */
  private render() {
    const r = this.r;
    this.btns = [];
    r.begin();
    switch (this.screen) {
      case 'menu': this.drawMenu(); break;
      case 'levels': this.drawLevels(); break;
      case 'settings': this.drawSettings(); break;
      case 'help': this.drawHelp(); break;
      case 'ach': this.drawAch(); break;
      case 'play': this.drawPlay(); break;
      case 'ending': this.drawEnding(); break;
      case 'memorial': this.drawMemorial(); break;
    }
    if (this.mockAd) this.drawMockAd();
    if (this.confirm) this.drawConfirm();
    if (this.toast) this.drawToast();
    if (this.settings.debug) r.text(`${this.fps} FPS · ${this.deps.platform}`, this.W - this.safe.r - 10, H - 14, 14, '#888888', 'right');
    r.end();
  }

  /* ---------- widgets ---------- */
  private button(id: string, x: number, y: number, w: number, h: number, label: string, cb: () => void, style: 'primary' | 'ghost' | 'danger' | 'light' = 'primary', size = 26) {
    const r = this.r;
    const down = this.pressed === id;
    r.save();
    if (down) { r.translate(x + w / 2, y + h / 2); r.scale(0.95, 0.95); r.translate(-(x + w / 2), -(y + h / 2)); }
    const fill = style === 'primary' ? INK : style === 'danger' ? '#e63946' : style === 'light' ? '#ffffff' : null;
    const tc = style === 'ghost' || style === 'light' ? INK : '#ffffff';
    r.roundRect(x + 3, y + 5, w, h, 14, rgba('#000000', 0.12));
    r.roundRect(x, y, w, h, 14, fill, INK, 3);
    r.text(label, x + w / 2, y + h / 2 + 1, size, tc, 'center', true);
    r.restore();
    this.btns.push({ id, x, y, w, h, cb });
  }
  private iconBtn(id: string, x: number, y: number, s: number, icon: 'pause' | 'retry' | 'back' | 'left' | 'right' | 'play' | 'home', cb: () => void) {
    const r = this.r;
    const down = this.pressed === id;
    r.save();
    r.translate(x + s / 2, y + s / 2);
    if (down) r.scale(0.9, 0.9);
    r.roundRect(-s / 2, -s / 2, s, s, 12, rgba('#ffffff', 0.85), INK, 3);
    const k = s / 56;
    switch (icon) {
      case 'pause': r.rect(-10 * k, -12 * k, 7 * k, 24 * k, INK); r.rect(3 * k, -12 * k, 7 * k, 24 * k, INK); break;
      case 'retry': {
        const pts: number[] = [];
        for (let i = 0; i <= 12; i++) { const a = -0.6 + i / 12 * 4.6; pts.push(Math.cos(a) * 12 * k, Math.sin(a) * 12 * k); }
        for (let i = 0; i < pts.length - 2; i += 2) r.line(pts[i], pts[i + 1], pts[i + 2], pts[i + 3], INK, 4 * k);
        r.poly([10 * k, -16 * k, 16 * k, -4 * k, 3 * k, -5 * k], INK, null);
        break;
      }
      case 'back': case 'left': r.poly([8 * k, -13 * k, -9 * k, 0, 8 * k, 13 * k], INK, null); break;
      case 'right': case 'play': r.poly([-8 * k, -13 * k, 9 * k, 0, -8 * k, 13 * k], INK, null); break;
      case 'home': r.poly([-13 * k, 0, 0, -13 * k, 13 * k, 0], INK, null); r.rect(-9 * k, 0, 18 * k, 12 * k, INK); break;
    }
    r.restore();
    this.btns.push({ id, x, y, w: s, h: s, cb });
  }
  private panel(x: number, y: number, w: number, h: number) {
    const r = this.r;
    r.roundRect(x + 6, y + 8, w, h, 22, rgba('#000000', 0.15));
    r.roundRect(x, y, w, h, 22, '#fffdf8', INK, 4);
  }
  private dim(a = 0.45) { this.r.rect(0, 0, this.W, H, rgba('#14141c', a)); }

  /* ---------- menu ---------- */
  private drawMenu() {
    const r = this.r, W = this.W, t = this.time;
    drawBackground(r, THEMES[(Math.floor(t / 8)) % 8], t * 40, 0, t);
    // ground strip with a little demo scene
    r.rect(0, H - 120, W, 120, INK);
    r.rect(0, H - 120, W, 8, '#81b29a');
    const px = W * 0.5 + Math.sin(t * 0.8) * W * 0.3;
    const jumpPh = (t * 0.8) % (Math.PI * 2);
    const spikeX = W * 0.5 + 40;
    const near = Math.abs(px - spikeX) < 120;
    const jy = near ? -Math.abs(Math.sin((px - spikeX + 120) / 240 * Math.PI)) * 110 : 0;
    r.save(); r.translate(spikeX, H - 120);
    if (near) r.poly([-20, 0, 0, -34, 20, 0], '#1f1f29', null);
    r.restore();
    drawPlayer(r, { x: px - 15, y: H - 150 + jy, w: 30, h: 30, vx: Math.cos(t * 0.8) * 300, vy: jy ? -300 : 0, grounded: !jy, wasGrounded: true, coyote: 0, buffer: 0, jumpHeld: false, jumping: false, facing: Math.cos(t * 0.8) >= 0 ? 1 : -1, groundEnt: null, launchT: 0, alive: true, deathT: 0, deathCause: '', landT: 0, jumpT: 0, blinkT: (t % 3) - 0.1, surpriseT: near ? 0.3 : 0, winT: 0, surface: 'normal' }, 1, t, 0, false, false);
    void jumpPh;
    // title
    const ty = 150 + Math.sin(t * 2) * 4;
    r.save(); r.translate(W / 2, ty); r.rotate(Math.sin(t * 1.3) * 0.015);
    r.text('别信这关', 6, 8, 104, rgba('#000000', 0.15), 'center', true);
    r.text('别信这关', 0, 0, 104, INK, 'center', true);
    r.restore();
    r.text('TRUST  NOTHING', W / 2, ty + 78, 26, '#ef476f', 'center', true);
    const bw = 300, bh = 64, bx = W / 2 - bw / 2;
    const d = this.save.data;
    const next = this.nextUnfinished();
    this.button('start', bx, 270, bw, bh, d.unlocked > 1 || d.cleared[1] ? `继续 · 第 ${next} 关` : '开始游戏', () => this.startLevel(next));
    this.button('levels', bx, 270 + 78, bw, bh, '选择关卡', () => this.goLevels(), 'light');
    const half = (bw - 14) / 2;
    this.button('settings', bx, 270 + 156, half, bh, '设置', () => { this.screen = 'settings'; }, 'light', 24);
    this.button('help', bx + half + 14, 270 + 156, half, bh, '游戏说明', () => { this.screen = 'help'; }, 'light', 24);
    this.button('ach', bx, 270 + 234, half, 52, '成就', () => { this.screen = 'ach'; }, 'ghost', 22);
    if (d.trueEnding && Object.keys(d.cleared).length >= 80) this.button('memorial', bx + half + 14, 270 + 234, half, 52, '纪念页', () => { this.screen = 'memorial'; }, 'ghost', 22);
    else this.button('quit', bx + half + 14, 270 + 234, half, 52, this.deps.exitApp ? '退出' : '关于', () => { if (this.deps.exitApp) { this.save.flush(); this.deps.exitApp(); } else this.say('', 0); }, 'ghost', 22);
    r.text(`v${this.deps.version} · 死亡 ${d.totalDeaths} 次 · 已通关 ${Object.keys(d.cleared).length}/80`, W / 2, H - 40, 18, '#ffffff', 'center');
  }

  nextUnfinished(): number {
    const d = this.save.data;
    for (let i = 1; i <= d.unlocked; i++) if (!d.cleared[i]) return i;
    return Math.min(80, d.unlocked);
  }

  /* ---------- level select ---------- */
  private drawLevels() {
    const r = this.r, W = this.W, s = this.safe;
    const ch = this.chapterView;
    const th = THEMES[ch];
    drawBackground(r, th, this.time * 20, 0, this.time);
    this.iconBtn('back', s.l + 24, 24, 56, 'back', () => this.goMenu());
    r.text(`第 ${['一', '二', '三', '四', '五', '六', '七', '八'][ch]} 章`, W / 2, 56, 26, th.accent, 'center', true);
    r.text(CHAPTERS[ch].name, W / 2, 100, 44, INK, 'center', true);
    if (ch > 0) this.iconBtn('chp', W / 2 - 340, 60, 60, 'left', () => { this.chapterView--; });
    if (ch < 7) this.iconBtn('chn', W / 2 + 280, 60, 60, 'right', () => { this.chapterView++; });
    const d = this.save.data;
    const cw = 150, chh = 150, gap = 26;
    const gx = W / 2 - (5 * cw + 4 * gap) / 2, gy = 180;
    for (let i = 0; i < 10; i++) {
      const id = ch * 10 + i + 1;
      const x = gx + (i % 5) * (cw + gap), y = gy + Math.floor(i / 5) * (chh + gap);
      const unlocked = id <= d.unlocked || this.settings.debug;
      const c = d.cleared[id];
      const down = this.pressed === 'lv' + id;
      r.save();
      if (down) { r.translate(x + cw / 2, y + chh / 2); r.scale(0.94, 0.94); r.translate(-(x + cw / 2), -(y + chh / 2)); }
      r.roundRect(x + 4, y + 6, cw, chh, 18, rgba('#000000', 0.12));
      r.roundRect(x, y, cw, chh, 18, unlocked ? (c ? '#ffffff' : mix(th.accent, '#ffffff', 0.75)) : '#d9d9de', INK, 3);
      if (unlocked) {
        r.text(String(id), x + cw / 2, y + 58, 50, INK, 'center', true);
        const nm = this.levels.find(l => l.id === id)?.name || '';
        r.text(nm.length > 7 ? nm.slice(0, 7) + '…' : nm, x + cw / 2, y + 104, 17, '#555566', 'center');
        if (c) {
          r.text(c.perfect ? '★ 无伤' : '✓ 已通关', x + cw / 2, y + 130, 16, c.perfect ? '#e09f3e' : '#2a9d8f', 'center', true);
        } else if (d.levelDeaths[id]) r.text(`死亡 ${d.levelDeaths[id]}`, x + cw / 2, y + 130, 15, '#e63946', 'center');
      } else {
        r.roundRect(x + cw / 2 - 18, y + 62, 36, 30, 6, '#8d8d99');
        r.circle(x + cw / 2, y + 62, 12, null, '#8d8d99', 6);
      }
      r.restore();
      if (unlocked) this.btns.push({ id: 'lv' + id, x, y, w: cw, h: chh, cb: () => this.startLevel(id) });
    }
    r.text(`本章死亡 ${Array.from({ length: 10 }, (_, i) => d.levelDeaths[ch * 10 + i + 1] || 0).reduce((a, b) => a + b, 0)} 次`, W / 2, H - 40, 20, '#666677', 'center');
  }

  /* ---------- settings ---------- */
  private drawSettings() {
    const r = this.r, W = this.W, s = this.settings;
    drawBackground(r, THEMES[1], this.time * 20, 0, this.time);
    const pw = 720, ph = 600, px = W / 2 - pw / 2, py = 60;
    this.panel(px, py, pw, ph);
    r.text('设置', W / 2, py + 50, 40, INK, 'center', true);
    const row = (i: number) => py + 120 + i * 72;
    const toggle = (id: string, i: number, label: string, val: boolean, cb: () => void) => {
      r.text(label, px + 60, row(i), 26, INK, 'left', true);
      const x = px + pw - 200, y = row(i) - 24;
      this.button(id, x, y, 140, 48, val ? '开' : '关', () => { cb(); this.applyAudio(); this.save.markDirty(); }, val ? 'primary' : 'ghost', 22);
    };
    const slider = (id: string, i: number, label: string, val: number, set: (v: number) => void) => {
      r.text(label, px + 60, row(i), 26, INK, 'left', true);
      const x = px + 300, y = row(i) - 10, w = 220;
      r.roundRect(x, y, w, 20, 10, '#e6e6ea', INK, 2);
      r.roundRect(x, y, Math.max(20, w * val), 20, 10, '#ef476f', null);
      r.text(`${Math.round(val * 100)}%`, x + w + 50, row(i), 22, INK, 'center');
      this.button(id + '-', x - 64, y - 14, 48, 48, '-', () => { set(Math.max(0, Math.round((val - 0.1) * 10) / 10)); this.applyAudio(); this.save.markDirty(); }, 'light', 28);
      this.button(id + '+', x + w + 90, y - 14, 48, 48, '+', () => { set(Math.min(1, Math.round((val + 0.1) * 10) / 10)); this.applyAudio(); this.save.markDirty(); this.sfx('jump'); }, 'light', 28);
    };
    toggle('mus', 0, '背景音乐', s.musicOn, () => { s.musicOn = !s.musicOn; });
    slider('mv', 1, '音乐音量', s.music, v => { s.music = v; });
    toggle('sfx', 2, '音效', s.sfxOn, () => { s.sfxOn = !s.sfxOn; });
    slider('sv', 3, '音效音量', s.sfx, v => { s.sfx = v; });
    toggle('shake', 4, '镜头震动', s.shake, () => { s.shake = !s.shake; });
    toggle('lh', 5, '左手模式（跳跃键在左）', s.leftHanded, () => { s.leftHanded = !s.leftHanded; });
    toggle('dbg', 6, '测试模式（显示机关/跳关）', s.debug, () => { s.debug = !s.debug; });
    this.button('reset', px + 60, py + ph - 76, 220, 52, '重置进度', () => { this.confirm = { text: '确定清空所有关卡进度和死亡统计吗？\n（设置会保留）', yes: () => { this.save.reset(); this.say('进度已重置', 1.5); } }; }, 'danger', 22);
    this.button('sback', px + pw - 280, py + ph - 76, 220, 52, this.world ? '返回游戏' : '返回', () => { this.save.flush(); if (this.world) { this.screen = 'play'; } else this.goMenu(); }, 'primary', 22);
  }

  /* ---------- help ---------- */
  private drawHelp() {
    const r = this.r, W = this.W;
    drawBackground(r, THEMES[2], this.time * 20, 0, this.time);
    const pw = 860, ph = 600, px = W / 2 - pw / 2, py = 60;
    this.panel(px, py, pw, ph);
    r.text('游戏说明', W / 2, py + 50, 40, INK, 'center', true);
    const lines = [
      '目标：控制小方块走进真正的出口门。',
      '操作：左下角 ◀ ▶ 移动，右下角 ▲ 跳跃；可以同时按。',
      '      轻点跳跃=小跳，长按=大跳。键盘：←→ / A D 移动，空格跳跃，R 重来。',
      '规则：这个世界里——地板、门、提示牌、金币、存档点……都可能骗你。',
      '      每次失败都会告诉你一点真相。记住它，下次就骗不到你了。',
      '每一关都一定有真实、可重复的通关方法，不存在随机必死。',
      '检查点：真正的检查点碰到后会变绿。死亡后从最近的检查点复活。',
      '提示：暂停菜单中可以自愿观看（测试模拟）广告获得提示或护盾，',
      '      不看广告也完全可以正常通关。',
    ];
    lines.forEach((l, i) => r.text(l, px + 50, py + 120 + i * 44, 22, i % 3 === 0 ? INK : '#444455', 'left', i === 0 || i === 3 || i === 5));
    this.button('hback', W / 2 - 110, py + ph - 76, 220, 52, '我知道了', () => this.goMenu(), 'primary', 22);
  }

  /* ---------- achievements ---------- */
  private drawAch() {
    const r = this.r, W = this.W;
    drawBackground(r, THEMES[3], this.time * 20, 0, this.time);
    const pw = Math.min(W - 60, 1100), ph = 640, px = W / 2 - pw / 2, py = 40;
    this.panel(px, py, pw, ph);
    const got = ACHIEVEMENTS.filter(a => this.ach.has(a.id)).length;
    r.text(`成就 ${got}/${ACHIEVEMENTS.length}`, W / 2, py + 44, 36, INK, 'center', true);
    const colW = (pw - 80) / 2;
    ACHIEVEMENTS.forEach((a, i) => {
      const col = i % 2, rowI = Math.floor(i / 2);
      const x = px + 40 + col * colW, y = py + 90 + rowI * 46;
      const has = this.ach.has(a.id);
      r.roundRect(x, y, colW - 20, 40, 10, has ? '#fff3d6' : '#f0f0f3', has ? '#e09f3e' : '#c9c9d0', 2);
      r.text(has ? '★' : '☆', x + 22, y + 20, 22, has ? '#e09f3e' : '#aaaab5', 'center', true);
      r.text(a.hidden && !has ? '？？？' : a.name, x + 44, y + 20, 19, INK, 'left', true);
      r.text(a.hidden && !has ? '隐藏成就' : a.desc, x + 200, y + 20, 15, '#666677', 'left');
    });
    this.iconBtn('aback', px + 20, py + 16, 52, 'back', () => this.goMenu());
  }

  /* ---------- play ---------- */
  private drawPlay() {
    const r = this.r, W = this.W, w = this.world!, s = w.s, th = this.theme;
    let sx = 0, sy = 0;
    if (this.shakeT > 0) { const k = this.shakeT / 0.25; sx = (Math.random() - 0.5) * this.shakeV * 2 * k; sy = (Math.random() - 0.5) * this.shakeV * 2 * k; }
    drawBackground(r, th, this.camX, this.camY, this.time);
    r.save();
    if (s.mirror) { r.translate(W, 0); r.scale(-1, 1); }
    r.translate(-Math.round(this.camX) + sx, -Math.round(this.camY) + sy);
    const x0 = this.camX - 80, x1 = this.camX + W + 80;
    const dbg = this.settings.debug;
    for (const e of s.ents) if (e.kind === 'block' && e.props.clip && e.x + e.w > x0 && e.x < x1) drawEnt(r, w, e, th, this.time, dbg);
    drawTiles(r, this.runs, th, x0, x1);
    for (const e of s.ents) if (!(e.kind === 'block' && e.props.clip) && e.x + e.w > x0 && e.x < x1) drawEnt(r, w, e, th, this.time, dbg);
    if (dbg) {
      for (const t of s.trigs) if (t.zone) { r.save(); r.alpha(t.fired ? 0.15 : 0.35); r.rect(t.zone.x, Math.max(t.zone.y, 0), t.zone.w, Math.min(t.zone.h, this.bp!.height), null, t.on === 'jump' ? '#ff00ff' : t.on === 'land' ? '#00aaff' : '#ff8800', 2); r.restore(); }
    }
    if (!(s.p.alive === false && s.p.deathT > 0.5)) drawPlayer(r, s.p, s.gsign, this.time, this.deathFace, !!s.shield, s.reverseT > 0);
    this.particles.draw(r);
    r.restore();

    if (s.dark > 0) {
      let pcx = s.p.x + 15 - this.camX, pcy = s.p.y + 15 - this.camY;
      if (s.mirror) pcx = W - pcx;
      drawDark(r, pcx, pcy, s.dark);
    }
    if (s.reverseT > 0) { r.save(); r.alpha(0.12 + Math.sin(this.time * 10) * 0.05); r.rect(0, 0, W, H, '#f77f00'); r.restore(); r.text(s.reverseT > 60 ? "⇄ 操作反转中" : `⇄ 操作反转 ${s.reverseT.toFixed(1)}s`, W / 2, 140, 26, '#d35400', 'center', true); }
    if (s.gsign < 0) r.text('重力反转中', W / 2, H - 30, 18, '#7b2cbf', 'center', true);

    this.drawHud();
    if (!this.demo) this.drawControls();

    if (this.sayT > 0 && this.sayText) {
      r.save(); r.alpha(Math.min(1, this.sayT * 3));
      const tw = Math.min(W - 80, r.measure(this.sayText, 24, true) + 50);
      r.roundRect(W / 2 - tw / 2, 96, tw, 52, 16, '#ffffff', INK, 3);
      r.text(this.sayText, W / 2, 123, 24, INK, 'center', true);
      r.restore();
    }
    if (this.fakeWinT >= 0) this.drawFakeWin();
    if (this.fakeEndT >= 0) this.drawFakeEnding();
    if (this.deadT >= 0) this.drawDeath();
    if (this.wonT >= 0) this.drawWin();
    if (this.paused && !this.mockAd) this.drawPause();
    if (this.showHint) this.drawHint();
  }

  private drawHud() {
    const r = this.r, W = this.W, s = this.safe, d = this.save.data;
    const def = this.levels.find(l => l.id === this.levelId)!;
    r.roundRect(s.l + 20, 18, 330, 62, 16, rgba('#ffffff', 0.85), INK, 3);
    r.text(`第 ${this.levelId} 关`, s.l + 40, 40, 22, INK, 'left', true);
    r.text(def.name, s.l + 40, 64, 17, '#555566', 'left');
    r.text('死亡', s.l + 250, 38, 15, '#e63946', 'center', true);
    r.text(String(d.levelDeaths[this.levelId] || 0), s.l + 250, 62, 24, '#e63946', 'center', true);
    const cp = this.world!.s.checkpoint;
    if (cp) { r.roundRect(s.l + 362, 30, 120, 38, 12, '#d8f3dc', '#2a9d8f', 2); r.text('✓ 检查点', s.l + 422, 49, 17, '#2a9d8f', 'center', true); }
    if (this.shieldCharges > 0) { r.circle(s.l + 510, 49, 16, null, '#4cc9f0', 4); }
    this.iconBtn('pause', W - s.r - 80, 18, 60, 'pause', () => { if (this.wonT < 0) this.paused = true; });
    this.iconBtn('retry', W - s.r - 152, 18, 60, 'retry', () => { this.restartLevel(); });
    if (this.settings.debug) {
      this.button('dprev', W - s.r - 430, 22, 80, 50, '上一关', () => this.startLevel(Math.max(1, this.levelId - 1)), 'light', 16);
      this.button('dnext', W - s.r - 340, 22, 80, 50, '下一关', () => this.startLevel(Math.min(80, this.levelId + 1)), 'light', 16);
      this.button('ddemo', W - s.r - 250, 22, 90, 50, this.demo ? '演示中' : '自动演示', () => this.startDemo(), 'light', 16);
    }
  }

  private drawControls() {
    const r = this.r, c = this.ctrl();
    const inp = this.collectInput();
    const pad = (x: number, y: number, rad: number, on: boolean, icon: 'l' | 'r' | 'j') => {
      r.save();
      r.alpha(on ? 0.85 : 0.5);
      r.circle(x, y, rad, on ? INK : rgba('#ffffff', 0.9), INK, 4);
      const col = on ? '#ffffff' : INK;
      const k = rad / 66;
      if (icon === 'l') r.poly([x + 14 * k, y - 22 * k, x - 18 * k, y, x + 14 * k, y + 22 * k], col, null);
      else if (icon === 'r') r.poly([x - 14 * k, y - 22 * k, x + 18 * k, y, x - 14 * k, y + 22 * k], col, null);
      else r.poly([x - 24 * k, y + 14 * k, x, y - 20 * k, x + 24 * k, y + 14 * k], col, null);
      r.restore();
    };
    pad(c.lx, c.my, c.rr, inp.left, 'l');
    pad(c.rx, c.my, c.rr, inp.right, 'r');
    pad(c.jx, c.jy, c.jr, inp.jump, 'j');
  }

  private drawDeath() {
    const r = this.r, W = this.W;
    const t = this.deadT;
    const a = Math.min(1, t / 0.2);
    r.save(); r.alpha(a * 0.35); r.rect(0, 0, W, H, '#14141c'); r.restore();
    r.save(); r.alpha(a);
    const y = H / 2 - 40 - (1 - a) * 30;
    const tw = Math.min(W - 80, r.measure(this.deathQuote, 34, true) + 80);
    r.roundRect(W / 2 - tw / 2, y - 44, tw, 88, 20, '#ffffff', INK, 4);
    r.text(this.deathQuote, W / 2, y, 34, INK, 'center', true);
    r.text(`本关死亡 ${this.save.data.levelDeaths[this.levelId] || 0} 次 · 点击屏幕立即重来`, W / 2, y + 74, 20, '#ffffff', 'center', true);
    r.restore();
  }

  private drawWin() {
    const r = this.r, W = this.W, t = this.wonT;
    if (t < 0.5 || this.levelId === 80) return;
    const a = Math.min(1, (t - 0.5) / 0.25);
    r.save(); r.alpha(a);
    this.dim(0.4);
    const pw = 560, ph = 400, px = W / 2 - pw / 2, py = H / 2 - ph / 2 + (1 - a) * 40;
    this.panel(px, py, pw, ph);
    r.text('通关！', W / 2, py + 64, 56, INK, 'center', true);
    const dd = this.visitDeaths;
    const evals = dd === 0 ? '★ 完美无伤 ★ 你是不是来过？' : dd < 5 ? '不错！只被骗了几次。' : dd < 15 ? '经验丰富的受害者。' : dd < 40 ? '坚持就是胜利！' : '你和这关的故事，可以写一本书了。';
    r.text(evals, W / 2, py + 130, 24, dd === 0 ? '#e09f3e' : '#555566', 'center', true);
    r.text(`本次死亡 ${dd} 次   用时 ${this.levelTime.toFixed(1)} 秒`, W / 2, py + 180, 22, INK, 'center');
    this.button('wnext', px + 60, py + ph - 150, pw - 120, 60, this.levelId % 10 === 0 && this.levelId < 80 ? `进入第 ${Math.floor(this.levelId / 10) + 1} 章` : '下一关', () => this.nextLevel());
    this.button('wretry', px + 60, py + ph - 76, (pw - 140) / 2, 52, '再玩一次', () => this.startLevel(this.levelId), 'light', 22);
    this.button('wmenu', px + 80 + (pw - 140) / 2, py + ph - 76, (pw - 140) / 2, 52, '关卡选择', () => this.goLevels(), 'light', 22);
    r.restore();
  }

  private drawFakeWin() {
    const r = this.r, W = this.W, t = this.fakeWinT;
    const a = Math.min(1, t / 0.15);
    r.save(); r.alpha(a * (t > 1.5 ? Math.max(0, (1.8 - t) / 0.3) : 1));
    if (t < 1.0) {
      r.rect(0, H / 2 - 90, W, 180, rgba('#ffd166', 0.92));
      r.text('通 关 ！', W / 2, H / 2 - 10, 80, INK, 'center', true);
      r.text('恭喜你完成了本关！', W / 2, H / 2 + 56, 24, INK, 'center', true);
    } else {
      const g = Math.sin(t * 60) * 6;
      r.rect(0, H / 2 - 90, W, 180, rgba('#1f1f29', 0.92));
      r.text('……才 怪', W / 2 + g, H / 2, 80, '#ef476f', 'center', true);
    }
    r.restore();
  }

  private drawFakeEnding() {
    const r = this.r, W = this.W, t = this.fakeEndT;
    r.save(); r.alpha(Math.min(1, t / 0.4) * (t > 4 ? Math.max(0, (4.4 - t) / 0.4) : 1));
    r.rect(0, 0, W, H, '#14141c');
    if (t < 3.0) {
      const lines = ['别信这关', '— 全剧终 —', '', '感谢你的游玩', '策划：一群坏心眼的方块', '程序：同上', '美术：还是同上', '', 'THE END'];
      lines.forEach((l, i) => r.text(l, W / 2, H - t * 160 + i * 56, i === 0 ? 56 : 26, i === 0 ? '#ffd166' : '#ffffff', 'center', i < 2));
    } else {
      const g = Math.sin(t * 70) * 5;
      r.text('等等……', W / 2 + g, H / 2 - 30, 60, '#ef476f', 'center', true);
      r.text('谁说这是真结局了？', W / 2 - g, H / 2 + 40, 30, '#ffffff', 'center', true);
    }
    r.restore();
  }

  private drawPause() {
    const r = this.r, W = this.W;
    this.dim(0.5);
    const pw = 460, ph = 540, px = W / 2 - pw / 2, py = H / 2 - ph / 2;
    this.panel(px, py, pw, ph);
    r.text('暂停', W / 2, py + 52, 40, INK, 'center', true);
    const bx = px + 60, bw = pw - 120;
    this.button('presume', bx, py + 100, bw, 56, '继续', () => { this.paused = false; });
    this.button('pretry', bx, py + 168, bw, 56, '从头重来', () => { this.paused = false; this.restartLevel(); }, 'light', 24);
    this.button('plevels', bx, py + 236, bw, 56, '关卡选择', () => this.goLevels(), 'light', 24);
    this.button('pset', bx, py + 304, (bw - 12) / 2, 56, '设置', () => { this.screen = 'settings'; }, 'light', 22);
    this.button('pmenu', bx + (bw + 12) / 2, py + 304, (bw - 12) / 2, 56, '主菜单', () => this.goMenu(), 'light', 22);
    r.text('—— 自愿观看广告（可跳过）——', W / 2, py + 396, 16, '#888899', 'center');
    this.button('phint', bx, py + 416, (bw - 12) / 2, 52, '获得提示', () => this.requestAd('hint'), 'ghost', 20);
    this.button('pshield', bx + (bw + 12) / 2, py + 416, (bw - 12) / 2, 52, '获得护盾', () => this.requestAd('shield'), 'ghost', 20);
    r.text(this.ads.isTestMode ? '当前为测试版：广告为模拟界面，无真实广告' : '', W / 2, py + ph - 26, 14, '#aaaab5', 'center');
  }

  private drawHint() {
    const r = this.r, W = this.W;
    const pw = Math.min(W - 80, 760), ph = 150, px = W / 2 - pw / 2, py = 170;
    this.panel(px, py, pw, ph);
    r.text('提示', px + 40, py + 36, 22, '#2a9d8f', 'left', true);
    r.text(this.hintText, W / 2, py + 82, 22, INK, 'center', true);
    this.button('hintok', px + pw - 140, py + ph - 50, 110, 40, '知道了', () => { this.showHint = false; }, 'primary', 18);
  }

  private drawMockAd() {
    const r = this.r, W = this.W, m = this.mockAd!;
    this.dim(0.75);
    const pw = 620, ph = 420, px = W / 2 - pw / 2, py = H / 2 - ph / 2;
    this.panel(px, py, pw, ph);
    r.roundRect(px + 20, py + 20, 220, 34, 8, '#ffd166', INK, 2);
    r.text('测试广告 · 模拟', px + 130, py + 37, 18, INK, 'center', true);
    r.text('这里是激励视频广告的占位界面', W / 2, py + 120, 26, INK, 'center', true);
    r.text('当前版本未接入任何真实广告平台，不产生任何收益。', W / 2, py + 165, 18, '#666677', 'center');
    r.text(`奖励：${m.placement === 'hint' ? '本关提示' : '一次护盾'}`, W / 2, py + 205, 22, '#2a9d8f', 'center', true);
    const left = Math.max(0, 3 - m.t);
    if (left > 0) r.text(`模拟播放中…… ${Math.ceil(left)}`, W / 2, py + 270, 24, '#888899', 'center', true);
    else this.button('adok', W / 2 - 150, py + 245, 300, 56, '模拟观看完成，领取奖励', () => { const d = m.done; this.mockAd = null; d(true); }, 'primary', 20);
    this.button('adclose', W / 2 - 150, py + 320, 300, 50, '关闭（放弃奖励）', () => { const d = m.done; this.mockAd = null; d(false); }, 'ghost', 20);
  }

  private drawConfirm() {
    const r = this.r, W = this.W, c = this.confirm!;
    this.dim(0.6);
    const pw = 600, ph = 300, px = W / 2 - pw / 2, py = H / 2 - ph / 2;
    this.panel(px, py, pw, ph);
    c.text.split('\n').forEach((l, i) => r.text(l, W / 2, py + 80 + i * 40, 24, INK, 'center', i === 0));
    this.button('cyes', px + 60, py + ph - 90, 220, 56, '确定', () => { const y = c.yes; this.confirm = null; y(); }, 'danger', 24);
    this.button('cno', px + pw - 280, py + ph - 90, 220, 56, '取消', () => { this.confirm = null; }, 'light', 24);
  }

  private drawToast() {
    const r = this.r, W = this.W, t = this.toast!;
    const k = t.t < 0.3 ? t.t / 0.3 : t.t > 2.6 ? Math.max(0, (3 - t.t) / 0.4) : 1;
    const y = -80 + k * 100;
    r.roundRect(W / 2 - 260, y, 520, 70, 18, '#fff3d6', '#e09f3e', 3);
    r.text('★ 成就解锁：' + t.name, W / 2, y + 24, 22, INK, 'center', true);
    r.text(t.desc, W / 2, y + 50, 16, '#666677', 'center');
  }

  /* ---------- ending / memorial ---------- */
  private drawEnding() {
    const r = this.r, W = this.W, t = this.endingT, d = this.save.data;
    drawBackground(r, THEMES[7], t * 30, 0, t);
    this.particles.draw(r);
    if (Math.random() < 0.05) this.particles.confetti(W, 6);
    const a = Math.min(1, t / 1.0);
    r.save(); r.alpha(a);
    r.text('真 · 通 关', W / 2, 120, 84, INK, 'center', true);
    r.text('你没有相信任何一关，于是你赢了。', W / 2, 200, 28, '#5a189a', 'center', true);
    const lines = [
      `总死亡次数：${d.totalDeaths}`,
      `被假终点欺骗：${d.fakeGoals} 次`,
      `游玩时间：${Math.floor(d.playTime / 60)} 分 ${Math.floor(d.playTime % 60)} 秒`,
      '隐藏成就解锁：真相只有一个',
    ];
    lines.forEach((l, i) => { if (t > 1 + i * 0.5) r.text(l, W / 2, 280 + i * 46, 26, i === 3 ? '#e09f3e' : INK, 'center', i === 3); });
    // celebrating cube
    drawPlayer(r, { x: W / 2 - 15, y: 500, w: 30, h: 30, vx: 0, vy: 0, grounded: true, wasGrounded: true, coyote: 0, buffer: 0, jumpHeld: false, jumping: false, facing: 1, groundEnt: null, launchT: 0, alive: true, deathT: 0, deathCause: '', landT: 0, jumpT: 0, blinkT: 1, surpriseT: 0, winT: t + 0.01, surface: 'normal' }, 1, t, 0, false, false);
    r.text('—— 感谢游玩《别信这关》——', W / 2, 590, 22, '#555566', 'center', true);
    r.restore();
    if (t > 3) {
      if (Object.keys(d.cleared).length >= 80) this.button('emem', W / 2 - 320, 630, 300, 60, '打开纪念页', () => { this.screen = 'memorial'; this.music('menu'); });
      this.button('emenu', W / 2 + (Object.keys(d.cleared).length >= 80 ? 20 : -150), 630, 300, 60, '回到主菜单', () => this.goMenu(), 'light');
    }
  }

  private drawMemorial() {
    const r = this.r, W = this.W, d = this.save.data;
    drawBackground(r, THEMES[7], this.time * 15, 0, this.time);
    const pw = Math.min(W - 60, 980), ph = 640, px = W / 2 - pw / 2, py = 40;
    this.panel(px, py, pw, ph);
    r.text('纪念页 · 别信这关', W / 2, py + 54, 40, INK, 'center', true);
    let worst = 1, worstN = 0;
    for (const [k, v] of Object.entries(d.levelDeaths)) if (v > worstN) { worstN = v; worst = Number(k); }
    const perfect = Object.values(d.cleared).filter(c => c.perfect).length;
    const rows = [
      ['通关关卡', `${Object.keys(d.cleared).length} / 80`],
      ['总死亡次数', String(d.totalDeaths)],
      ['最难忘的一关', `第 ${worst} 关「${this.levels.find(l => l.id === worst)?.name || ''}」死亡 ${worstN} 次`],
      ['无伤通关', `${perfect} 关`],
      ['被假终点骗', `${d.fakeGoals} 次`],
      ['吃下诱饵金币', `${d.baitCoins} 个`],
      ['总游玩时间', `${Math.floor(d.playTime / 3600)} 小时 ${Math.floor(d.playTime / 60) % 60} 分`],
      ['成就', `${ACHIEVEMENTS.filter(a => this.ach.has(a.id)).length} / ${ACHIEVEMENTS.length}`],
    ];
    rows.forEach(([k, v], i) => { r.text(k, px + 100, py + 130 + i * 52, 24, '#666677', 'left', true); r.text(v, px + 340, py + 130 + i * 52, 24, INK, 'left', true); });
    r.text('“这个世界没有一块地板值得完全信任——但你还是走到了最后。”', W / 2, py + ph - 100, 20, '#5a189a', 'center', true);
    this.button('mback', W / 2 - 120, py + ph - 70, 240, 52, '返回', () => this.goMenu(), 'primary', 22);
  }
}

function pick<T>(a: T[]): T { return a[Math.floor(Math.random() * a.length)]; }
function setTimeoutSafe(fn: () => void, ms: number, g: Game) {
  // engine-agnostic delayed call driven by game time (no platform timers)
  const start = g.time;
  const check = () => { if (g.time - start >= ms / 1000) fn(); else pending.push(check); };
  pending.push(check);
}
const pending: (() => void)[] = [];
export function runPending() { const p = pending.splice(0); for (const f of p) f(); }
void TILE;
