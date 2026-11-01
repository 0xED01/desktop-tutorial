/** World rendering (engine-agnostic, via IRenderer). */
import { IRenderer, mix, rgba } from './render';
import { World } from './world';
import { Ent, Player, TILE } from './types';

export interface Theme { bg: string; bg2: string; ground: string; top: string; accent: string; deco: string; }
export const THEMES: Theme[] = [
  { bg: '#f6f1e7', bg2: '#efe4cf', ground: '#3d405b', top: '#81b29a', accent: '#f2a541', deco: '#e7d8bb' },
  { bg: '#e9f3f5', bg2: '#d6e9ee', ground: '#2f4858', top: '#5fb3c7', accent: '#33a1fd', deco: '#c6dfe6' },
  { bg: '#eef0fb', bg2: '#dde1f6', ground: '#3a3f6b', top: '#9b8cff', accent: '#8e7dff', deco: '#d0d4ef' },
  { bg: '#fbf0e6', bg2: '#f4dcc8', ground: '#5b3a29', top: '#e9a46a', accent: '#ff7b54', deco: '#efd2bb' },
  { bg: '#eaf6ee', bg2: '#d3ecdc', ground: '#22543d', top: '#5cc58a', accent: '#2ec4b6', deco: '#c8e4d1' },
  { bg: '#fdf1f3', bg2: '#f6dbe1', ground: '#4a2c3a', top: '#e58aa4', accent: '#ef476f', deco: '#efd0d8' },
  { bg: '#eeeeee', bg2: '#dedede', ground: '#2b2b2f', top: '#8d8d99', accent: '#ff595e', deco: '#d6d6d6' },
  { bg: '#f1ecff', bg2: '#ded3fb', ground: '#241e4e', top: '#b39cff', accent: '#ffd166', deco: '#d8ccf4' },
];

export const INK = '#20202a';
export const SPIKE = '#1f1f29';

export interface Particle { x: number; y: number; vx: number; vy: number; life: number; max: number; color: string; size: number; g: number; }

export class Particles {
  list: Particle[] = [];
  burst(x: number, y: number, color: string, n: number, speed = 260) {
    if (this.list.length > 260) this.list.splice(0, this.list.length - 200);
    for (let i = 0; i < n; i++) {
      const a = Math.random() * Math.PI * 2, v = speed * (0.3 + Math.random() * 0.7);
      this.list.push({ x, y, vx: Math.cos(a) * v, vy: Math.sin(a) * v - 120, life: 0, max: 0.5 + Math.random() * 0.4, color, size: 3 + Math.random() * 5, g: 900 });
    }
  }
  confetti(w: number, n: number) {
    const cols = ['#ef476f', '#ffd166', '#06d6a0', '#118ab2', '#8e7dff', '#ff7b54'];
    for (let i = 0; i < n; i++) this.list.push({ x: Math.random() * w, y: -20 - Math.random() * 200, vx: (Math.random() - 0.5) * 120, vy: 80 + Math.random() * 160, life: 0, max: 2.5 + Math.random(), color: cols[i % cols.length], size: 5 + Math.random() * 6, g: 60 });
  }
  update(dt: number) {
    for (const p of this.list) { p.life += dt; p.vy += p.g * dt; p.x += p.vx * dt; p.y += p.vy * dt; }
    this.list = this.list.filter(p => p.life < p.max);
  }
  draw(r: IRenderer) {
    for (const p of this.list) {
      const k = 1 - p.life / p.max;
      r.save(); r.alpha(Math.max(0, k));
      r.rect(p.x - p.size / 2, p.y - p.size / 2, p.size, p.size, p.color);
      r.restore();
    }
  }
}

export function colorOf(name: string, th: Theme): string {
  switch (name) {
    case 'player': return INK;
    case 'spike': return SPIKE;
    case 'gold': return '#f6c343';
    case 'portal': return '#9b5de5';
    case 'brick': return '#b5654a';
    case 'red': return '#e63946';
    case 'blue': return '#3a86ff';
    case 'green': return '#2a9d8f';
    case 'cloud': return '#ffffff';
    case 'glass': return '#8ecae6';
    default: return th.ground;
  }
}

/** Precomputed static tile runs per row for cheap drawing. */
export function tileRuns(w: World): { x: number; y: number; w: number; t: number; top: boolean; bottom: boolean }[] {
  const bp = w.bp; const out: { x: number; y: number; w: number; t: number; top: boolean; bottom: boolean }[] = [];
  for (let y = 0; y < bp.rows; y++) {
    let x = 0;
    while (x < bp.cols) {
      const t = bp.tiles[y * bp.cols + x];
      if (!t) { x++; continue; }
      const top = y === 0 ? true : !bp.tiles[(y - 1) * bp.cols + x];
      let x2 = x;
      while (x2 + 1 < bp.cols && bp.tiles[y * bp.cols + x2 + 1] === t && (y === 0 ? true : !bp.tiles[(y - 1) * bp.cols + x2 + 1]) === top) x2++;
      out.push({ x: x * TILE, y: y * TILE, w: (x2 - x + 1) * TILE, t, top, bottom: y === bp.rows - 1 });
      x = x2 + 1;
    }
  }
  return out;
}

export function drawBackground(r: IRenderer, th: Theme, camX: number, camY: number, time: number) {
  r.clear(th.bg);
  const W = r.width, H = r.height;
  // soft bands
  r.rect(0, H * 0.62, W, H * 0.38, th.bg2);
  // parallax hills
  for (let layer = 0; layer < 2; layer++) {
    const par = layer === 0 ? 0.15 : 0.3;
    const col = layer === 0 ? th.deco : mix(th.deco, th.bg2, 0.4);
    const base = H * (layer === 0 ? 0.66 : 0.76);
    const off = -(camX * par) % 480;
    for (let i = -1; i < W / 480 + 2; i++) {
      const x = off + i * 480 + (layer * 200);
      r.circle(x + 240, base + 160, 220 - layer * 40, col);
    }
  }
  // floating squares (decorative)
  for (let i = 0; i < 8; i++) {
    const x = ((i * 337 - camX * 0.08) % (W + 200) + W + 200) % (W + 200) - 100;
    const y = 80 + ((i * 131) % 300) + Math.sin(time * 0.6 + i) * 8 - camY * 0.05;
    r.save(); r.translate(x, y); r.rotate(time * 0.2 + i); r.alpha(0.35);
    r.roundRect(-10 - (i % 3) * 4, -10 - (i % 3) * 4, 20 + (i % 3) * 8, 20 + (i % 3) * 8, 4, null, th.deco, 3);
    r.restore();
  }
}

export function drawTiles(r: IRenderer, runs: ReturnType<typeof tileRuns>, th: Theme, x0: number, x1: number) {
  for (const t of runs) {
    if (t.x + t.w < x0 || t.x > x1) continue;
    const col = t.t === 2 ? '#b5654a' : t.t === 3 ? '#ffffff' : th.ground;
    if (t.t === 3) {
      r.roundRect(t.x + 1, t.y + 2, t.w - 2, TILE - 10, 8, '#ffffff', th.ground, 3);
      continue;
    }
    r.rect(t.x, t.y, t.w, TILE + 0.5 + (t.bottom ? 320 : 0), col);
    if (t.t === 2) {
      for (let k = 0; k < t.w; k += TILE) { r.line(t.x + k, t.y, t.x + k, t.y + TILE, mix(col, '#000000', 0.25), 2); r.line(t.x + k, t.y + TILE / 2, t.x + k + TILE, t.y + TILE / 2, mix(col, '#000000', 0.25), 2); }
    }
    if (t.top) { r.rect(t.x, t.y, t.w, 7, th.top); r.rect(t.x, t.y + 7, t.w, 2, mix(th.top, '#000000', 0.2)); }
  }
}

function spikeTris(r: IRenderer, e: Ent, k: number, fill: string, stroke: string | null) {
  const n = Math.max(1, Math.round((e.dir === 'u' || e.dir === 'd' ? e.w : e.h) / TILE)) * 2;
  const pts: number[] = [];
  if (e.dir === 'u' || e.dir === 'd') {
    const sw = e.w / n, base = e.dir === 'u' ? e.y + e.h : e.y, tip = e.dir === 'u' ? base - e.h * 0.8 * k : base + e.h * 0.8 * k;
    for (let i = 0; i < n; i++) { pts.length = 0; pts.push(e.x + i * sw, base, e.x + i * sw + sw / 2, tip, e.x + (i + 1) * sw, base); r.poly(pts, fill, stroke, 2); }
  } else {
    const sh = e.h / n, base = e.dir === 'r' ? e.x : e.x + e.w, tip = e.dir === 'r' ? base + e.w * 0.8 * k : base - e.w * 0.8 * k;
    for (let i = 0; i < n; i++) { pts.length = 0; pts.push(base, e.y + i * sh, tip, e.y + i * sh + sh / 2, base, e.y + (i + 1) * sh); r.poly(pts, fill, stroke, 2); }
  }
}

export function drawEnt(r: IRenderer, w: World, e: Ent, th: Theme, time: number, debug: boolean) {
  const s = w.s;
  if (e.kind === 'zone') { if (e.vis) drawZone(r, e, th, time); return; }
  if (!e.vis && !(debug && (e.kind === 'spike' || e.kind === 'block'))) return;
  r.save();
  if (!e.vis) r.alpha(0.18);
  if (e.shake > 0) r.translate((Math.random() - 0.5) * 4, (Math.random() - 0.5) * 3);
  switch (e.kind) {
    case 'block': {
      let a = 1;
      if (e.crumble && e.crumble.state === 'gone') { r.restore(); return; }
      if (e.anim < 1) a = e.anim;
      const solidNow = w.entSolid(e) || (e.props.ghost) || (!e.solid && !e.phase && !e.blink);
      let fill = colorOf(e.look, th);
      if (e.look === 'ground') fill = th.ground;
      if (e.phase) {
        const on = (e.phase === 'red') === !s.toggle;
        const c = e.phase === 'red' ? '#e63946' : '#3a86ff';
        if (on) { r.rect(e.x, e.y, e.w, e.h, c, mix(c, '#000000', 0.35), 3); r.line(e.x + 6, e.y + 8, e.x + e.w - 6, e.y + 8, rgba('#ffffff', 0.5), 3); }
        else { r.alpha(0.6); r.rect(e.x + 3, e.y + 3, e.w - 6, e.h - 6, null, c, 2); for (let k = 8; k < e.w; k += 12) r.line(e.x + k, e.y + e.h - 6, e.x + k + 6, e.y + 6, rgba(c, 0.4), 2); }
        break;
      }
      if (e.blink) {
        const on = w.blinkOn(e);
        const b = e.blink, per = b.on + b.off, t = ((s.time + b.phase) % per + per) % per;
        const warn = on && t > b.on - 0.35;
        if (!on) { r.alpha(0.35); r.rect(e.x + 2, e.y + 2, e.w - 4, e.h - 4, null, '#4ea8de', 2); break; }
        r.alpha(warn ? (Math.sin(time * 40) > 0 ? 0.4 : 0.9) : 0.9);
        r.rect(e.x, e.y, e.w, e.h, '#a9def9', '#4ea8de', 3);
        r.line(e.x + 6, e.y + 6, e.x + 16, e.y + 6, '#ffffff', 3);
        break;
      }
      r.save();
      if (e.props.tell) { r.alpha(0.55 + Math.sin(time * 3 + e.x) * 0.08); }
      if (a < 1) { r.translate(e.x + e.w / 2, e.y + e.h / 2); r.scale(0.6 + 0.4 * a, 0.6 + 0.4 * a); r.translate(-(e.x + e.w / 2), -(e.y + e.h / 2)); r.alpha(a); }
      void solidNow;
      switch (e.look) {
        case 'cloud': r.roundRect(e.x + 1, e.y + 2, e.w - 2, Math.min(e.h, TILE) - 10, 8, '#ffffff', th.ground, 3); break;
        case 'brick': {
          r.rect(e.x, e.y, e.w, e.h, '#b5654a', '#7d3c2a', 2);
          for (let yy = 0; yy < e.h; yy += 20) { r.line(e.x, e.y + yy, e.x + e.w, e.y + yy, '#7d3c2a', 2); for (let xx = (yy / 20) % 2 ? 20 : 0; xx < e.w; xx += 40) r.line(e.x + xx, e.y + yy, e.x + xx, e.y + yy + 20, '#7d3c2a', 2); }
          if (e.crumble && e.crumble.state === 'shaking') { r.line(e.x + 8, e.y + 4, e.x + 20, e.y + 22, '#3d1d12', 2); r.line(e.x + 20, e.y + 22, e.x + 30, e.y + 36, '#3d1d12', 2); }
          break;
        }
        case 'ice': r.rect(e.x, e.y, e.w, e.h, '#d7f3ff', '#7cc6e8', 2); r.line(e.x + 8, e.y + 10, e.x + 22, e.y + 6, '#ffffff', 3); break;
        case 'mud': r.rect(e.x, e.y, e.w, e.h, '#7a5c3e', '#4f3826', 2); for (let xx = 8; xx < e.w; xx += 18) r.circle(e.x + xx, e.y + 8 + ((xx * 7) % 9), 4, '#5e452d'); break;
        case 'red': case 'blue': case 'green': case 'gold': {
          const c = colorOf(e.look, th);
          r.rect(e.x, e.y, e.w, e.h, c, mix(c, '#000000', 0.35), 3);
          r.line(e.x + 6, e.y + 8, e.x + e.w - 6, e.y + 8, rgba('#ffffff', 0.45), 3);
          break;
        }
        case 'glass': r.rect(e.x, e.y, e.w, e.h, '#a9def9', '#4ea8de', 3); break;
        default: {
          r.rect(e.x, e.y, e.w, e.h, th.ground);
          r.rect(e.x, e.y, e.w, 7, th.top); r.rect(e.x, e.y + 7, e.w, 2, mix(th.top, '#000000', 0.2));
          if (debug && e.props.ghost) r.rect(e.x, e.y, e.w, e.h, rgba('#ff00ff', 0.25));
        }
      }
      r.restore();
      if (e.crumble && e.crumble.state === 'shaking' && e.look !== 'brick') { r.line(e.x + 10, e.y + 2, e.x + 18, e.y + 18, rgba('#000000', 0.5), 2); }
      break;
    }
    case 'spike': {
      const k = Math.max(0.05, e.anim);
      if (e.look === 'paperSpike') { spikeTris(r, e, 1, '#5a5a6e', '#ffffff'); }
      else spikeTris(r, e, k, SPIKE, null);
      break;
    }
    case 'saw': {
      const cx = e.x + e.w / 2, cy = e.y + e.h / 2, rad = e.w / 2;
      if (e.props.mimic) {
        const angry = e.look === 'saw';
        const hop = angry ? Math.abs(Math.sin(time * 14)) * 5 : 0;
        r.save(); r.translate(0, -hop);
        r.roundRect(e.x - 4, e.y - 4, e.w + 8, e.h + 4, 6, '#6d4c41', null);
        r.rect(e.x, e.y, e.w, e.h, angry ? '#c0392b' : '#d7a86e', '#6d4c41', 2);
        if (angry) {
          r.rect(e.x + 6, e.y + 12, 8, 8, '#ffffff'); r.rect(e.x + e.w - 14, e.y + 12, 8, 8, '#ffffff');
          r.rect(e.x + 9, e.y + 15, 3, 3, INK); r.rect(e.x + e.w - 11, e.y + 15, 3, 3, INK);
          r.rect(e.x + 5, e.y + 28, e.w - 10, 18, '#1b1b1b');
          for (let k = 0; k < 4; k++) { const tx = e.x + 6 + k * 7; r.poly([tx, e.y + 28, tx + 3.5, e.y + 35, tx + 7, e.y + 28], '#ffffff', null); r.poly([tx, e.y + 46, tx + 3.5, e.y + 39, tx + 7, e.y + 46], '#ffffff', null); }
        } else { r.rect(e.x + 6, e.y + 6, e.w - 12, 18, '#c4935a'); r.rect(e.x + 6, e.y + 30, e.w - 12, 20, '#c4935a'); r.circle(e.x + e.w - 9, e.y + e.h / 2 + 4, 3, '#ffd166'); }
        r.roundRect(e.x + 2, e.y - 22, e.w - 4, 14, 3, '#2a9d8f'); r.text(angry ? 'EAT' : 'EXIT', e.x + e.w / 2, e.y - 15, 10, '#ffffff', 'center', true);
        r.restore();
        break;
      }
      if (e.look === 'boulder') {
        r.save(); r.translate(cx, cy); r.rotate(e.x / rad);
        r.circle(0, 0, rad, '#8d6e63', '#4e342e', 4);
        r.line(-rad * 0.5, -rad * 0.2, rad * 0.2, rad * 0.4, '#5d4037', 4); r.line(rad * 0.1, -rad * 0.6, rad * 0.5, -rad * 0.1, '#5d4037', 4);
        r.restore();
      } else {
        r.save(); r.translate(cx, cy); r.rotate(time * 9);
        const pts: number[] = [];
        for (let i = 0; i < 16; i++) { const a = i / 16 * Math.PI * 2, rr = i % 2 ? rad * 0.78 : rad; pts.push(Math.cos(a) * rr, Math.sin(a) * rr); }
        r.poly(pts, '#5c5c66', SPIKE, 2);
        r.circle(0, 0, rad * 0.25, '#d9d9e0');
        r.restore();
      }
      break;
    }
    case 'goal': {
      const fl = !!e.props.flip;
      r.save();
      if (fl) { r.translate(0, e.y * 2 + e.h); r.scale(1, -1); }
      const opened = s.won && e.props.real;
      r.roundRect(e.x - 4, e.y - 4, e.w + 8, e.h + 4, 6, '#6d4c41', null);
      r.rect(e.x, e.y, e.w, e.h, opened ? '#1b1b1b' : '#d7a86e', '#6d4c41', 2);
      if (!opened) { r.rect(e.x + 6, e.y + 6, e.w - 12, 18, '#c4935a'); r.rect(e.x + 6, e.y + 30, e.w - 12, 20, '#c4935a'); r.circle(e.x + e.w - 9, e.y + e.h / 2 + 4, 3, '#ffd166'); }
      // exit sign
      r.roundRect(e.x + 2, e.y - 22, e.w - 4, 14, 3, '#2a9d8f'); r.text('EXIT', e.x + e.w / 2, e.y - 15, 10, '#ffffff', 'center', true);
      r.restore();
      break;
    }
    case 'checkpoint': {
      const on = !!e.st.on;
      const fl = !!e.props.flip;
      r.save();
      if (fl) { r.translate(0, e.y * 2 + e.h); r.scale(1, -1); }
      r.rect(e.x + 2, e.y, 4, e.h, '#5c5c66');
      const wave = Math.sin(time * 6) * 3;
      r.poly([e.x + 6, e.y + 2, e.x + 30 + wave, e.y + 11, e.x + 6, e.y + 22], on ? '#06d6a0' : '#ef476f', mix(on ? '#06d6a0' : '#ef476f', '#000000', 0.3), 2);
      r.rect(e.x - 4, e.y + e.h - 6, 16, 6, '#5c5c66');
      r.restore();
      break;
    }
    case 'coin': {
      const bob = Math.sin(time * 4 + e.x) * 3;
      const sx = Math.abs(Math.cos(time * 3 + e.x * 0.1));
      r.save(); r.translate(e.x + 10, e.y + 10 + bob); r.scale(0.3 + 0.7 * sx, 1);
      r.circle(0, 0, 10, '#ffd166', '#c9971c', 3); r.rect(-2, -5, 4, 10, '#c9971c');
      r.restore();
      break;
    }
    case 'spring': {
      const b = e.st.boing > 0 ? e.st.boing : 0;
      if (e.st.boing > 0) e.st.boing -= 1 / 60;
      if (e.dir === 'u') {
        r.rect(e.x + 4, e.y + 8, e.w - 8, 8, '#5c5c66');
        r.roundRect(e.x, e.y - b * 30, e.w, 8, 3, '#ef476f', '#9d1a3a', 2);
        for (let k = 8; k < e.w - 4; k += 8) r.line(e.x + k, e.y + 8, e.x + k + 4, e.y + 1 - b * 30, '#9d1a3a', 2);
      } else {
        r.roundRect(e.x, e.y, e.w, e.h, 3, '#ef476f', '#9d1a3a', 2);
      }
      break;
    }
    case 'portal': {
      const cx = e.x + e.w / 2, cy = e.y + e.h / 2;
      const c = e.props.hue === 1 ? '#f77f00' : '#9b5de5';
      r.save(); r.translate(cx, cy); r.scale(0.55, 1);
      for (let i = 3; i >= 0; i--) { r.circle(0, 0, 30 - i * 6 + Math.sin(time * 5 + i) * 2, i % 2 ? mix(c, '#ffffff', 0.4) : c); }
      r.restore();
      break;
    }
    case 'sign': {
      r.rect(e.x + 17, e.y + 18, 6, 22, '#6d4c41');
      r.roundRect(e.x + 2, e.y + 2, 36, 22, 4, '#e9c46a', '#6d4c41', 2);
      r.text(e.props.lie && debug ? '谎' : '!', e.x + 20, e.y + 13, 14, '#6d4c41', 'center', true);
      const p = s.p;
      if (Math.abs(p.x - e.x) < 4 * TILE && Math.abs(p.y - e.y) < 4 * TILE) {
        const txt = e.props.text as string;
        const tw = Math.max(80, r.measure(txt, 18, true) + 24);
        r.roundRect(e.x + 20 - tw / 2, e.y - 44, tw, 34, 8, '#ffffff', INK, 2);
        r.poly([e.x + 14, e.y - 10, e.x + 26, e.y - 10, e.x + 20, e.y - 2], '#ffffff', null);
        r.text(txt, e.x + 20, e.y - 27, 18, INK, 'center', true);
      }
      break;
    }
    case 'button': {
      if (e.look === 'none') r.rect(e.x, e.y + 6, e.w, 4, '#5c5c66');
      else { r.rect(e.x - 2, e.y + 6, e.w + 4, 4, '#5c5c66'); r.roundRect(e.x + 2, e.y - 2, e.w - 4, 10, 3, '#ffd166', '#c9971c', 2); }
      break;
    }
    case 'laser': {
      const on = w.blinkOn(e);
      const b = e.blink!, per = b.on + b.off, t = ((s.time + b.phase) % per + per) % per;
      const v = !!e.props.vertical;
      // emitters
      if (v) { r.rect(e.x + e.w / 2 - 10, e.y, 20, 10, '#5c5c66'); r.rect(e.x + e.w / 2 - 10, e.y + e.h - 10, 20, 10, '#5c5c66'); }
      else { r.rect(e.x, e.y + e.h / 2 - 10, 10, 20, '#5c5c66'); r.rect(e.x + e.w - 10, e.y + e.h / 2 - 10, 10, 20, '#5c5c66'); }
      if (on) {
        if (v) { r.rect(e.x + e.w / 2 - 6, e.y + 10, 12, e.h - 20, '#ff006e'); r.rect(e.x + e.w / 2 - 2, e.y + 10, 4, e.h - 20, '#ffd6e8'); }
        else { r.rect(e.x + 10, e.y + e.h / 2 - 6, e.w - 20, 12, '#ff006e'); r.rect(e.x + 10, e.y + e.h / 2 - 2, e.w - 20, 4, '#ffd6e8'); }
      } else if (t > per - 0.45) {
        r.alpha(Math.sin(time * 50) > 0 ? 0.6 : 0.2);
        if (v) r.rect(e.x + e.w / 2 - 1, e.y + 10, 2, e.h - 20, '#ff006e'); else r.rect(e.x + 10, e.y + e.h / 2 - 1, e.w - 20, 2, '#ff006e');
      }
      break;
    }
    case 'shadow': {
      r.alpha(0.55 + Math.sin(time * 10) * 0.1);
      r.roundRect(e.x, e.y, 30, 30, 6, '#5a189a');
      r.rect(e.x + 7, e.y + 9, 6, 6, '#ff4d6d'); r.rect(e.x + 18, e.y + 9, 6, 6, '#ff4d6d');
      break;
    }
  }
  r.restore();
}

function drawZone(r: IRenderer, e: Ent, th: Theme, time: number) {
  const z = e.props.zone;
  r.save(); r.alpha(0.22);
  if (z === 'gravity') {
    r.rect(e.x, e.y, e.w, e.h, '#9b5de5');
    r.alpha(1.6);
    for (let yy = 20; yy < e.h; yy += 60) for (let xx = 20; xx < e.w; xx += 40) {
      const o = ((time * 60) % 60);
      const y = e.y + ((yy - o + e.h) % e.h);
      const up = e.props.v !== 1;
      r.poly(up ? [e.x + xx - 8, y + 8, e.x + xx, y - 4, e.x + xx + 8, y + 8] : [e.x + xx - 8, y - 4, e.x + xx, y + 8, e.x + xx + 8, y - 4], '#7b2cbf', null);
    }
  } else if (z === 'reverse') {
    r.rect(e.x, e.y, e.w, e.h, '#f77f00');
    r.alpha(1.8);
    for (let yy = 20; yy < e.h; yy += 80) r.text('⇄', e.x + e.w / 2, e.y + yy, 22, '#d35400', 'center', true);
  } else if (z === 'wind') {
    r.alpha(1.4);
    const dir = Math.sign(e.props.fx) || 1;
    for (let i = 0; i < Math.max(3, e.w * e.h / 6000); i++) {
      const lx = e.x + (((i * 97 + time * 260 * dir) % e.w) + e.w) % e.w;
      const ly = e.y + ((i * 53) % e.h);
      r.line(lx, ly, lx + 24 * dir, ly, '#90a4ae', 2);
    }
  } else if (z === 'speed') {
    r.rect(e.x, e.y, e.w, e.h, '#06d6a0');
    r.alpha(1.8);
    for (let xx = 10; xx < e.w; xx += 30) { const y = e.y + e.h - 20; r.poly([e.x + xx, y - 8, e.x + xx + 10, y, e.x + xx, y + 8], '#049f76', null); }
  }
  r.restore();
  void th;
}

export function drawPlayer(r: IRenderer, p: Player, gsign: number, time: number, deathFace: number, shield: boolean, reverse: boolean) {
  const cx = p.x + p.w / 2, cy = p.y + p.h / 2;
  r.save();
  r.translate(cx, cy);
  if (gsign < 0) r.scale(1, -1);
  let sx = 1, sy = 1, rot = 0;
  if (!p.alive) {
    const t = Math.min(1, p.deathT / 0.35);
    sx = 1 + t * 0.4; sy = 1 - t * 0.5; rot = t * 0.4 * p.facing;
  } else if (p.winT > 0) {
    const b = Math.abs(Math.sin(p.winT * 10)) * 10;
    r.translate(0, -b);
    sy = 1 + Math.sin(p.winT * 20) * 0.08;
  } else {
    if (p.jumpT > 0) { const k = p.jumpT / 0.18; sx = 1 - 0.22 * k; sy = 1 + 0.28 * k; }
    else if (p.landT > 0) { const k = p.landT / 0.12; sx = 1 + 0.3 * k; sy = 1 - 0.25 * k; }
    else if (!p.grounded) { const v = Math.min(1, Math.abs(p.vy) / 900); sx = 1 - 0.1 * v; sy = 1 + 0.12 * v; }
    if (p.grounded && Math.abs(p.vx) > 40) { rot = (p.vx / 300) * 0.12 * gsign; sy *= 1 + Math.sin(time * 26) * 0.04; }
  }
  r.rotate(rot);
  r.scale(sx, sy);
  // shadow-ish outline + body
  if (shield) r.circle(0, 0, 26, null, '#4cc9f0', 3);
  r.roundRect(-15, -15, 30, 30, 7, INK);
  r.rect(-15, 8, 30, 7, '#2e2e3c');
  const look = p.facing * 3;
  // face
  if (!p.alive) {
    const f = deathFace % 4;
    if (f === 0) { // X X
      r.line(-10, -8, -3, -1, '#ffffff', 3); r.line(-3, -8, -10, -1, '#ffffff', 3);
      r.line(3, -8, 10, -1, '#ffffff', 3); r.line(10, -8, 3, -1, '#ffffff', 3);
    } else if (f === 1) { // swirl-ish
      r.circle(-6, -4, 5, null, '#ffffff', 2); r.circle(7, -4, 5, null, '#ffffff', 2); r.circle(-6, -4, 1.5, '#ffffff'); r.circle(7, -4, 1.5, '#ffffff');
    } else if (f === 2) { // -_-
      r.line(-11, -4, -2, -4, '#ffffff', 3); r.line(3, -4, 12, -4, '#ffffff', 3);
    } else { // O O shocked
      r.circle(-6, -5, 5, '#ffffff'); r.circle(7, -5, 5, '#ffffff'); r.circle(-6, -5, 2, INK); r.circle(7, -5, 2, INK);
    }
    r.roundRect(-5, 3, 10, 7, 3, '#ff6b81');
  } else if (p.winT > 0) {
    r.line(-11, -3, -7, -8, '#ffffff', 3); r.line(-7, -8, -3, -3, '#ffffff', 3);
    r.line(3, -3, 7, -8, '#ffffff', 3); r.line(7, -8, 11, -3, '#ffffff', 3);
    r.poly([-6, 2, 6, 2, 0, 8], '#ff6b81', null);
  } else if (p.surpriseT > 0) {
    r.circle(-6 + look, -5, 6, '#ffffff'); r.circle(7 + look, -5, 6, '#ffffff');
    r.circle(-6 + look, -5, 2, INK); r.circle(7 + look, -5, 2, INK);
    r.circle(look, 6, 3.5, '#ff6b81');
  } else {
    const blink = p.blinkT < 0;
    if (blink) { r.line(-10 + look, -4, -3 + look, -4, '#ffffff', 3); r.line(3 + look, -4, 10 + look, -4, '#ffffff', 3); }
    else {
      r.rect(-10 + look, -9, 7, 9, '#ffffff'); r.rect(3 + look, -9, 7, 9, '#ffffff');
      const py = p.vy * gsign > 200 ? -3 : p.vy * gsign < -200 ? -8 : -6;
      r.rect(-8 + look + p.facing, py, 3, 4, INK); r.rect(5 + look + p.facing, py, 3, 4, INK);
    }
    if (reverse) { r.line(-4 + look, 5, 4 + look, 8, '#ff6b81', 2); r.line(4 + look, 5, -4 + look, 8, '#ff6b81', 2); }
    else r.line(-3 + look, 6, 3 + look, 6, '#ffffff', 2);
  }
  r.restore();
  if (p.alive && p.surpriseT > 0) {
    r.save(); r.alpha(Math.min(1, p.surpriseT * 3));
    const y = gsign > 0 ? p.y - 22 : p.y + p.h + 22;
    r.text('!', cx + 14, y, 26, '#ef476f', 'center', true);
    r.restore();
  }
}

export function drawDark(r: IRenderer, cx: number, cy: number, rad: number) {
  const W = r.width, H = r.height;
  const c = '#0b0b12';
  r.rect(0, 0, W, Math.max(0, cy - rad), c);
  r.rect(0, cy + rad, W, Math.max(0, H - cy - rad), c);
  r.rect(0, cy - rad, Math.max(0, cx - rad), rad * 2, c);
  r.rect(cx + rad, cy - rad, Math.max(0, W - cx - rad), rad * 2, c);
  // corners of the square → circle (annulus wedge polygons)
  const seg = 24;
  for (let i = 0; i < seg; i++) {
    const a0 = i / seg * Math.PI * 2, a1 = (i + 1) / seg * Math.PI * 2;
    const pts: number[] = [cx + Math.cos(a0) * rad, cy + Math.sin(a0) * rad, cx + Math.cos(a1) * rad, cy + Math.sin(a1) * rad];
    // project to square edge
    const proj = (a: number) => { const ca = Math.cos(a), sa = Math.sin(a); const k = rad / Math.max(Math.abs(ca), Math.abs(sa)); return [cx + ca * k, cy + sa * k]; };
    const p1 = proj(a1), p0 = proj(a0);
    pts.push(p1[0], p1[1], p0[0], p0[1]);
    r.poly(pts, c, null);
  }
  r.save(); r.alpha(0.45);
  for (let i = 0; i < seg; i++) {
    const a0 = i / seg * Math.PI * 2, a1 = (i + 1) / seg * Math.PI * 2, r2 = rad * 0.8;
    r.poly([cx + Math.cos(a0) * r2, cy + Math.sin(a0) * r2, cx + Math.cos(a1) * r2, cy + Math.sin(a1) * r2, cx + Math.cos(a1) * rad, cy + Math.sin(a1) * rad, cx + Math.cos(a0) * rad, cy + Math.sin(a0) * rad], c, null);
  }
  r.restore();
}
