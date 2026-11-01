/**
 * HTML5 / Android-WebView platform layer for 别信这关.
 * Implements IRenderer (Canvas2D), IAudio (WebAudio), IStorage (localStorage)
 * and feeds touch / keyboard input into the shared core Game.
 * The same file runs in desktop browsers for testing and inside the APK.
 */
import { Game } from '../../TrustNothing/assets/scripts/core/game';
import { IRenderer, Align } from '../../TrustNothing/assets/scripts/core/render';
import { IAudio, IStorage } from '../../TrustNothing/assets/scripts/core/services';
import { LEVELS } from '../../TrustNothing/assets/scripts/levels/index';
import SOLUTIONS from '../../tests/solutions.json';

declare const __VERSION__: string;
declare const AndroidBridge: { exit(): void; vibrate?(ms: number): void } | undefined;

const FONT = '"PingFang SC","Noto Sans CJK SC","Source Han Sans SC","Microsoft YaHei","Droid Sans Fallback",sans-serif';

class CanvasRenderer implements IRenderer {
  ctx: CanvasRenderingContext2D;
  width = 1280; height = 720;
  ks = 1;
  private fontCache = new Map<string, string>();
  constructor(public canvas: HTMLCanvasElement) {
    this.ctx = canvas.getContext('2d', { alpha: false })!;
  }
  resize(cssW: number, cssH: number, dpr: number) {
    this.height = 720;
    this.width = Math.max(960, Math.round(720 * cssW / cssH));
    const pxW = Math.round(cssW * dpr), pxH = Math.round(cssH * dpr);
    this.canvas.width = pxW; this.canvas.height = pxH;
    this.canvas.style.width = cssW + 'px'; this.canvas.style.height = cssH + 'px';
    this.ks = pxH / 720;
    // if the screen is narrower than 960/720 (rare), letterbox horizontally by scaling width
    if (720 * cssW / cssH < 960) this.ks = pxW / 960;
  }
  begin() { const c = this.ctx; c.setTransform(this.ks, 0, 0, this.ks, 0, 0); c.globalAlpha = 1; c.textBaseline = 'middle'; c.lineJoin = 'round'; c.lineCap = 'round'; }
  end() {}
  clear(color: string) { const c = this.ctx; c.save(); c.setTransform(1, 0, 0, 1, 0, 0); c.fillStyle = color; c.fillRect(0, 0, this.canvas.width, this.canvas.height); c.restore(); }
  save() { this.ctx.save(); }
  restore() { this.ctx.restore(); }
  translate(x: number, y: number) { this.ctx.translate(x, y); }
  scale(sx: number, sy: number) { this.ctx.scale(sx, sy); }
  rotate(a: number) { this.ctx.rotate(a); }
  alpha(a: number) { this.ctx.globalAlpha *= a; }
  private paint(fill: string | null, stroke?: string | null, lw = 2) {
    const c = this.ctx;
    if (fill) { c.fillStyle = fill; c.fill(); }
    if (stroke) { c.strokeStyle = stroke; c.lineWidth = lw; c.stroke(); }
  }
  rect(x: number, y: number, w: number, h: number, fill: string | null, stroke?: string | null, lw?: number) {
    const c = this.ctx;
    if (fill && !stroke) { c.fillStyle = fill; c.fillRect(x, y, w, h); return; }
    c.beginPath(); c.rect(x, y, w, h); this.paint(fill, stroke, lw);
  }
  roundRect(x: number, y: number, w: number, h: number, r: number, fill: string | null, stroke?: string | null, lw?: number) {
    const c = this.ctx; r = Math.min(r, w / 2, h / 2);
    c.beginPath();
    c.moveTo(x + r, y); c.arcTo(x + w, y, x + w, y + h, r); c.arcTo(x + w, y + h, x, y + h, r); c.arcTo(x, y + h, x, y, r); c.arcTo(x, y, x + w, y, r); c.closePath();
    this.paint(fill, stroke, lw);
  }
  circle(x: number, y: number, r: number, fill: string | null, stroke?: string | null, lw?: number) {
    const c = this.ctx; c.beginPath(); c.arc(x, y, Math.max(0, r), 0, Math.PI * 2); this.paint(fill, stroke, lw);
  }
  poly(pts: number[], fill: string | null, stroke?: string | null, lw?: number) {
    const c = this.ctx; c.beginPath(); c.moveTo(pts[0], pts[1]);
    for (let i = 2; i < pts.length; i += 2) c.lineTo(pts[i], pts[i + 1]);
    c.closePath(); this.paint(fill, stroke, lw);
  }
  line(x1: number, y1: number, x2: number, y2: number, color: string, lw: number) {
    const c = this.ctx; c.beginPath(); c.moveTo(x1, y1); c.lineTo(x2, y2); c.strokeStyle = color; c.lineWidth = lw; c.stroke();
  }
  private font(size: number, bold?: boolean) {
    const k = (bold ? 'b' : 'n') + size;
    let f = this.fontCache.get(k);
    if (!f) { f = `${bold ? '700' : '400'} ${size}px ${FONT}`; this.fontCache.set(k, f); }
    return f;
  }
  text(s: string, x: number, y: number, size: number, color: string, align: Align = 'left', bold?: boolean) {
    const c = this.ctx; c.font = this.font(size, bold); c.fillStyle = color; c.textAlign = align; c.fillText(s, x, y);
  }
  measure(s: string, size: number, bold?: boolean) { const c = this.ctx; c.font = this.font(size, bold); return c.measureText(s).width; }
}

const TRACKS = ['menu', 'level_a', 'level_b', 'hard', 'final', 'victory', 'victory_short'];
const SFX = ['jump', 'land', 'death', 'spike', 'crumble', 'wall', 'trap', 'portal', 'click', 'fake', 'fakewin', 'win', 'coin', 'spring', 'checkpoint',
  'bump', 'flee', 'warp', 'toggle', 'retract', 'thud', 'respawn', 'achievement', 'shield'];

class WebAudioManager implements IAudio {
  ctx: AudioContext | null = null;
  buffers = new Map<string, AudioBuffer>();
  musicGain!: GainNode; sfxGain!: GainNode;
  current: { name: string; src: AudioBufferSourceNode } | null = null;
  wanted = '';
  vol = { music: 0.6, sfx: 0.8, mOn: true, sOn: true };
  unlocked = false;
  lastPlay = new Map<string, number>();
  constructor(private base: string) {
    try {
      const AC = (window as any).AudioContext || (window as any).webkitAudioContext;
      this.ctx = new AC({ latencyHint: 'interactive' });
      this.musicGain = this.ctx!.createGain(); this.sfxGain = this.ctx!.createGain();
      this.musicGain.connect(this.ctx!.destination); this.sfxGain.connect(this.ctx!.destination);
    } catch { this.ctx = null; }
    this.loadAll();
  }
  private async load(name: string, file: string) {
    if (!this.ctx) return;
    try {
      const res = await fetch(this.base + file);
      const arr = await res.arrayBuffer();
      const buf = await new Promise<AudioBuffer>((ok, bad) => this.ctx!.decodeAudioData(arr, ok, bad));
      this.buffers.set(name, buf);
      if (this.wanted === name && !this.current) this.playMusic(name);
    } catch (e) { console.warn('audio load failed', file, e); }
  }
  private loadAll() {
    for (const s of SFX) this.load('sfx_' + s, `sfx/${s}.ogg`);
    for (const t of TRACKS) this.load('mus_' + t, `music/${t}.ogg`);
  }
  unlock() {
    if (!this.ctx || this.unlocked) return;
    this.ctx.resume().then(() => { this.unlocked = true; if (this.wanted && !this.current) this.playMusic(this.wanted); }).catch(() => {});
  }
  playSfx(name: string) {
    if (!this.ctx || !this.vol.sOn) return;
    const b = this.buffers.get('sfx_' + name); if (!b) return;
    const now = this.ctx.currentTime;
    if ((this.lastPlay.get(name) || -1) > now - 0.03) return; // de-dupe bursts
    this.lastPlay.set(name, now);
    const src = this.ctx.createBufferSource(); src.buffer = b; src.connect(this.sfxGain); src.start();
  }
  playMusic(track: string) {
    this.wanted = track;
    if (!this.ctx) return;
    if (this.current && this.current.name === track) return;
    this.stopMusic(true);
    const b = this.buffers.get('mus_' + track);
    if (!b || !this.vol.mOn) return;
    const src = this.ctx.createBufferSource(); src.buffer = b;
    src.loop = track !== 'victory_short';
    src.connect(this.musicGain); src.start();
    this.current = { name: track, src };
    if (!src.loop) src.onended = () => { if (this.current && this.current.src === src) { this.current = null; } };
  }
  stopMusic(keepWanted = false) {
    if (this.current) { try { this.current.src.stop(); } catch { /* */ } this.current = null; }
    if (!keepWanted) this.wanted = '';
  }
  setVolumes(music: number, sfx: number, musicOn: boolean, sfxOn: boolean) {
    this.vol = { music, sfx, mOn: musicOn, sOn: sfxOn };
    if (!this.ctx) return;
    this.musicGain.gain.value = music * 0.55;
    this.sfxGain.gain.value = sfx * 0.9;
    if (!musicOn) this.stopMusic(true);
    else if (this.wanted && !this.current) this.playMusic(this.wanted);
  }
  suspend() { this.ctx?.suspend(); }
  resume() { if (this.unlocked) this.ctx?.resume(); }
}

class LocalStore implements IStorage {
  mem = new Map<string, string>();
  get(k: string) { try { return localStorage.getItem(k); } catch { return this.mem.get(k) ?? null; } }
  set(k: string, v: string) { try { localStorage.setItem(k, v); } catch { this.mem.set(k, v); } }
}

function boot() {
  const canvas = document.getElementById('game') as HTMLCanvasElement;
  const renderer = new CanvasRenderer(canvas);
  const audio = new WebAudioManager('audio/');
  const isAndroid = typeof AndroidBridge !== 'undefined';
  const game = new Game({
    renderer, audio, storage: new LocalStore(), levels: LEVELS, solutions: SOLUTIONS as any,
    platform: isAndroid ? 'Android' : 'Web', version: __VERSION__,
    exitApp: isAndroid ? () => AndroidBridge!.exit() : undefined,
  });
  (window as any).__game = game; // test hook (Playwright)

  const probe = document.getElementById('safe') as HTMLDivElement;
  let nativeSafe = { l: 0, t: 0, r: 0, b: 0 };
  (window as any).__setSafe = (l: number, t: number, r: number, b: number) => { nativeSafe = { l, t, r, b }; layout(); };
  const layout = () => {
    const w = window.innerWidth, h = window.innerHeight;
    renderer.resize(w, h, Math.min(window.devicePixelRatio || 1, 2));
    const cs = getComputedStyle(probe);
    const k = 720 / h;
    const extra = 16; // keep away from rounded corners / gesture bars
    const css = { l: parseFloat(cs.paddingLeft) || 0, r: parseFloat(cs.paddingRight) || 0, t: parseFloat(cs.paddingTop) || 0, b: parseFloat(cs.paddingBottom) || 0 };
    game.safe = {
      l: Math.max(css.l, nativeSafe.l) * k + extra, r: Math.max(css.r, nativeSafe.r) * k + extra,
      t: Math.max(css.t, nativeSafe.t) * k, b: Math.max(css.b, nativeSafe.b) * k + 6,
    };
  };
  layout();
  window.addEventListener('resize', layout);
  window.addEventListener('orientationchange', () => setTimeout(layout, 200));

  const toLogical = (cx: number, cy: number) => {
    const rect = canvas.getBoundingClientRect();
    const s = renderer.ks / (canvas.width / rect.width);
    return { x: (cx - rect.left) / s, y: (cy - rect.top) / s };
  };
  const onTouch = (type: 'down' | 'move' | 'up') => (ev: TouchEvent) => {
    ev.preventDefault();
    for (const t of Array.from(ev.changedTouches)) { const p = toLogical(t.clientX, t.clientY); game.pointer(type, t.identifier, p.x, p.y); }
  };
  canvas.addEventListener('touchstart', onTouch('down'), { passive: false });
  canvas.addEventListener('touchmove', onTouch('move'), { passive: false });
  canvas.addEventListener('touchend', onTouch('up'), { passive: false });
  canvas.addEventListener('touchcancel', onTouch('up'), { passive: false });
  let mouseDown = false;
  canvas.addEventListener('mousedown', e => { mouseDown = true; const p = toLogical(e.clientX, e.clientY); game.pointer('down', -1, p.x, p.y); });
  window.addEventListener('mousemove', e => { if (!mouseDown) return; const p = toLogical(e.clientX, e.clientY); game.pointer('move', -1, p.x, p.y); });
  window.addEventListener('mouseup', e => { if (!mouseDown) return; mouseDown = false; const p = toLogical(e.clientX, e.clientY); game.pointer('up', -1, p.x, p.y); });
  window.addEventListener('keydown', e => { if (e.repeat) return; game.key(e.code, true); if (['Space', 'ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'].includes(e.code)) e.preventDefault(); });
  window.addEventListener('keyup', e => game.key(e.code, false));
  document.addEventListener('visibilitychange', () => {
    if (document.hidden) { game.save.markDirty(); game.save.flush(); audio.suspend(); if (game.screen === 'play' && game.wonT < 0) game.paused = true; game.keys = { left: false, right: false, jump: false }; game.touches.clear(); }
    else audio.resume();
  });
  (window as any).__onAndroidPause = () => { game.save.markDirty(); game.save.flush(); audio.suspend(); if (game.screen === 'play' && game.wonT < 0) game.paused = true; game.touches.clear(); };
  (window as any).__onAndroidResume = () => { audio.resume(); };
  (window as any).__onAndroidBack = () => { game.key('Escape', true); return true; };

  let last = performance.now();
  const loop = (now: number) => {
    const dt = (now - last) / 1000; last = now;
    game.frame(dt);
    requestAnimationFrame(loop);
  };
  requestAnimationFrame(loop);
  const ld = document.getElementById('loading'); if (ld) ld.remove();
}

boot();
