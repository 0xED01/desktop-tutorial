/**
 * Deterministic fixed-step simulation. One World = one attempt at a level.
 * reset() rebuilds every entity & trigger from the immutable blueprint, so no
 * trap state can leak between attempts.
 */
import { Act, Ent, FIXED_DT, Fx, InputState, Player, Rect, TILE, Trig } from './types';
import { Blueprint } from './levelLoader';

export const PHYS = {
  speed: 300,
  accelGround: 3200,
  accelAir: 2300,
  decelGround: 3800,
  gravity: 2500,
  jumpV: 860,
  maxFall: 1050,
  coyote: 0.1,
  buffer: 0.12,
  cutMul: 0.45,
};

export interface WorldState {
  time: number;
  p: Player;
  ents: Ent[];
  trigs: Trig[];
  queue: { t: number; act: Act }[];
  gsign: number;         // 1 normal, -1 flipped
  reverseT: number;
  speedMul: number; speedT: number;
  freezeT: number;
  toggle: boolean;
  dark: number;
  mirror: boolean;
  events: string[];
  won: boolean;
  checkpoint: { x: number; y: number; g: number; id: string } | null;
  history: number[];     // shadow history (x,y pairs)
  portalCd: number;
  jumps: number;
  coins: number;
  fakeGoals: number;
  shield?: boolean;      // rewarded-ad protection (one hit)
  invulT?: number;
}

function overlap(a: Rect, b: Rect): boolean {
  return a.x < b.x + b.w && a.x + a.w > b.x && a.y < b.y + b.h && a.y + a.h > b.y;
}

export function makePlayer(x: number, y: number): Player {
  return {
    x, y, w: 30, h: 30, vx: 0, vy: 0, grounded: false, wasGrounded: false, coyote: 0, buffer: 0,
    jumpHeld: false, jumping: false, facing: 1, groundEnt: null, launchT: 0, alive: true, deathT: 0,
    deathCause: '', landT: 0, jumpT: 0, blinkT: 2, surpriseT: 0, winT: 0, surface: 'normal',
  };
}

export class World {
  bp: Blueprint;
  s!: WorldState;
  fx: Fx[] = [];
  /** quick lookup */
  private byId = new Map<string, Ent>();
  private prevJump = false;

  constructor(bp: Blueprint, cp: WorldState['checkpoint'] = null) {
    this.bp = bp;
    this.reset(cp);
  }

  reset(cp: WorldState['checkpoint'] = null) {
    const bp = this.bp;
    const ents: Ent[] = bp.ents.map(e => {
      const c: Ent = { ...e, props: { ...e.props }, st: {} };
      if (e.move) c.move = { ...e.move, path: e.move.path, seg: 0, t: 0, fwd: true, done: false, waitT: 0 };
      if (e.track) c.track = { ...e.track, t: 0 };
      if (e.flee) c.flee = { ...e.flee, idx: 0, moving: false };
      if (e.crumble) c.crumble = { ...e.crumble, t: 0, state: 'idle', goneT: 0 };
      if (e.blink) c.blink = { ...e.blink };
      if (e.drop) c.drop = { ...e.drop, state: 'idle', vy: 0, t: 0 };
      if (e.shy) c.shy = { ...e.shy, done: false };
      if (e.looks) c.looks = { ...e.looks };
      return c;
    });
    const trigs: Trig[] = bp.trigs.map(t => ({ ...t, fired: false, inside: false, standT: 0 }));
    const sx = cp ? cp.x : bp.start.x, sy = cp ? cp.y : bp.start.y;
    this.s = {
      time: 0, p: makePlayer(sx, sy), ents, trigs, queue: [], gsign: cp ? cp.g : 1, reverseT: 0, speedMul: 1, speedT: 0,
      freezeT: 0, toggle: false, dark: 0, mirror: false, events: [], won: false, checkpoint: cp, history: [], portalCd: 0,
      jumps: 0, coins: 0, fakeGoals: 0,
    };
    // mark checkpoint as already-active
    if (cp) for (const e of ents) if (e.id === cp.id) e.st.on = true;
    this.reindex();
    this.prevJump = true; // a held jump from the previous attempt shouldn't auto-jump
    for (const t of trigs) if (t.on === 'start') this.fire(t);
  }

  private reindex() {
    this.byId.clear();
    for (const e of this.s.ents) this.byId.set(e.id, e);
  }

  snapshot(): { s: WorldState; prevJump: boolean } {
    return { s: cloneState(this.s), prevJump: this.prevJump };
  }
  restore(snap: { s: WorldState; prevJump: boolean }) {
    this.s = cloneState(snap.s);
    this.prevJump = snap.prevJump;
    this.reindex();
  }

  targets(t?: string): Ent[] {
    if (!t) return [];
    const out: Ent[] = [];
    for (const name of t.split(',')) {
      const n = name.trim();
      for (const e of this.s.ents) if (e.group === n || e.id === n) out.push(e);
    }
    return out;
  }

  /* ---------------- collision helpers ---------------- */

  private tileSolid(cx: number, cy: number): boolean {
    const bp = this.bp;
    if (cx < 0 || cx >= bp.cols) return true;              // side walls
    if (cy < 0 || cy >= bp.rows) return false;              // open top/bottom
    return bp.tiles[cy * bp.cols + cx] !== 0;
  }

  entSolid(e: Ent): boolean {
    if (e.kind !== 'block') return false;
    if (e.st.waitClear) return false;
    if (e.phase) return e.vis && (e.phase === 'red') === !this.s.toggle;
    if (e.crumble && e.crumble.state === 'gone') return false;
    if (e.blink && !this.blinkOn(e)) return false;
    return e.solid && (e.vis || e.props.revealOnTouch);
  }

  blinkOn(e: Ent): boolean {
    const b = e.blink!;
    const per = b.on + b.off;
    const t = ((this.s.time + b.phase) % per + per) % per;
    return t < b.on;
  }

  /** returns list of solid rects overlapping r */
  private solidsIn(r: Rect, out: { r: Rect; e: Ent | null }[]) {
    out.length = 0;
    const x1 = Math.floor(r.x / TILE), x2 = Math.floor((r.x + r.w - 0.001) / TILE);
    const y1 = Math.floor(r.y / TILE), y2 = Math.floor((r.y + r.h - 0.001) / TILE);
    for (let cy = y1; cy <= y2; cy++) for (let cx = x1; cx <= x2; cx++) {
      if (this.tileSolid(cx, cy)) out.push({ r: { x: cx * TILE, y: cy * TILE, w: TILE, h: TILE }, e: null });
    }
    for (const e of this.s.ents) if (this.entSolid(e) && overlap(r, e)) out.push({ r: e, e });
    return out;
  }

  private tmp: { r: Rect; e: Ent | null }[] = [];

  overlapsSolid(r: Rect): boolean {
    return this.solidsIn(r, this.tmp).length > 0;
  }

  /* ---------------- main step ---------------- */

  step(input: InputState) {
    const s = this.s;
    const dt = FIXED_DT;
    s.time += dt;
    const p = s.p;

    // timed actions
    if (s.queue.length) {
      const due = s.queue.filter(q => q.t <= s.time);
      if (due.length) {
        s.queue = s.queue.filter(q => q.t > s.time);
        for (const q of due) this.doAct(q.act);
      }
    }

    if (s.reverseT > 0) s.reverseT -= dt;
    if (s.speedT > 0) { s.speedT -= dt; if (s.speedT <= 0) s.speedMul = 1; }
    if (s.portalCd > 0) s.portalCd -= dt;
    if (s.invulT && s.invulT > 0) s.invulT -= dt;

    this.updateEnts(dt);

    if (!p.alive) { p.deathT += dt; return; }
    if (s.won) { p.winT += dt; return; }

    p.blinkT -= dt; if (p.blinkT < -0.15) p.blinkT = 2 + ((s.time * 7.3) % 2);
    if (p.surpriseT > 0) p.surpriseT -= dt;
    if (p.landT > 0) p.landT -= dt;
    if (p.jumpT > 0) p.jumpT -= dt;

    if (s.freezeT > 0) {
      s.freezeT -= dt;
      this.prevJump = input.jump;
      this.processEvents();
      return;
    }

    this.playerPhysics(input, dt);
    if (!p.alive) return;

    // shadow history
    if (this.bp.ents.some(e => e.kind === 'shadow')) {
      s.history.push(Math.round(p.x), Math.round(p.y));
      if (s.history.length > 1200) s.history.splice(0, 2);
    }

    this.interactions();
    this.evalTriggers();
    this.processEvents();
    if (p.alive) this.hazards();
  }

  /* ---------------- player ---------------- */

  private playerPhysics(inp: InputState, dt: number) {
    const s = this.s, p = s.p, g = s.gsign;
    let left = inp.left, right = inp.right;
    if (s.reverseT > 0) { const t = left; left = right; right = t; }
    const dirIn = (right ? 1 : 0) - (left ? 1 : 0);

    // carry on moving platform
    if (p.groundEnt) {
      const ge = this.byId.get(p.groundEnt);
      if (ge && (ge.dx || ge.dy)) { this.moveX(ge.dx); this.moveY(ge.dy, true); }
    }

    const surf = p.grounded ? p.surface : 'normal';
    let maxV = PHYS.speed * s.speedMul * (surf === 'mud' ? 0.5 : 1);
    let accel = p.grounded ? PHYS.accelGround : PHYS.accelAir;
    let decel = p.grounded ? PHYS.decelGround : PHYS.accelAir * 0.8;
    if (surf === 'ice') { accel = 700; decel = 180; }
    if (p.launchT > 0) { p.launchT -= dt; accel *= 0.35; decel = 0; }

    if (dirIn !== 0) {
      p.facing = dirIn;
      const target = dirIn * maxV;
      if (Math.abs(p.vx) > maxV && Math.sign(p.vx) === dirIn) {
        p.vx -= Math.sign(p.vx) * decel * 0.25 * dt; // keep spring momentum
      } else {
        const a = Math.sign(target - p.vx) === Math.sign(p.vx) || p.vx === 0 ? accel : accel + decel;
        p.vx += Math.sign(target - p.vx) * Math.min(Math.abs(target - p.vx), a * dt);
      }
    } else {
      const d = Math.min(Math.abs(p.vx), decel * dt);
      p.vx -= Math.sign(p.vx) * d;
    }

    // wind
    for (const e of s.ents) if (e.kind === 'zone' && e.props.zone === 'wind' && overlap(p, e)) {
      p.vx += e.props.fx * dt; p.vy += e.props.fy * dt * g;
    }

    // jump input
    const pressed = inp.jump && !this.prevJump;
    this.prevJump = inp.jump;
    if (pressed) p.buffer = PHYS.buffer; else if (p.buffer > 0) p.buffer -= dt;
    if (p.grounded) p.coyote = PHYS.coyote; else if (p.coyote > 0) p.coyote -= dt;

    if (p.buffer > 0 && p.coyote > 0) {
      const jv = PHYS.jumpV * (surf === 'mud' ? 0.72 : 1);
      p.vy = -jv * g;
      p.grounded = false; p.coyote = 0; p.buffer = 0; p.jumping = true; p.jumpT = 0.18; p.groundEnt = null;
      s.jumps++;
      this.fx.push({ type: 'sfx', name: 'jump' });
      this.onJump();
    }
    if (p.jumping && !inp.jump && p.vy * g < 0) { p.vy *= PHYS.cutMul; p.jumping = false; }
    if (p.vy * g >= 0) p.jumping = false;

    p.vy += PHYS.gravity * g * dt;
    if (p.vy * g > PHYS.maxFall) p.vy = PHYS.maxFall * g;

    p.wasGrounded = p.grounded;
    this.moveX(p.vx * dt);
    p.grounded = false; p.groundEnt = null;
    this.moveY(p.vy * dt, false);

    // ground probe (so standing still keeps grounded)
    if (!p.grounded) {
      const probe = { x: p.x, y: g > 0 ? p.y + p.h : p.y - 1, w: p.w, h: 1 };
      const hits = this.solidsIn(probe, this.tmp);
      if (hits.length && p.vy * g >= 0) { p.grounded = true; this.setGround(hits); }
    }

    if (p.grounded && !p.wasGrounded) {
      p.landT = 0.12;
      this.fx.push({ type: 'sfx', name: 'land' });
      this.onLand();
    }

    // out of world
    if (p.y > this.bp.height + 120 || p.y < -160) this.kill('fall');
  }

  private setGround(hits: { r: Rect; e: Ent | null }[]) {
    const p = this.s.p;
    p.surface = 'normal';
    for (const h of hits) {
      if (h.e) { p.groundEnt = h.e.id; if (h.e.surface) p.surface = h.e.surface; }
    }
  }

  private moveX(dx: number) {
    if (!dx) return;
    const p = this.s.p;
    p.x += dx;
    const hits = this.solidsIn(p, this.tmp);
    if (!hits.length) return;
    for (const h of hits) {
      if (h.e && h.e.props.revealOnTouch) this.reveal(h.e);
      if (dx > 0) p.x = Math.min(p.x, h.r.x - p.w); else p.x = Math.max(p.x, h.r.x + h.r.w);
    }
    if (p.launchT <= 0 || true) p.vx = 0;
  }

  private moveY(dy: number, carry: boolean) {
    if (!dy) return;
    const s = this.s, p = s.p, g = s.gsign;
    p.y += dy;
    const hits = this.solidsIn(p, this.tmp);
    if (!hits.length) return;
    for (const h of hits) {
      if (h.e && h.e.props.revealOnTouch) this.reveal(h.e);
      if (dy > 0) p.y = Math.min(p.y, h.r.y - p.h); else p.y = Math.max(p.y, h.r.y + h.r.h);
    }
    if (carry) return;
    if (dy * g > 0) { p.grounded = true; this.setGround(hits); }
    else if (p.vy * g < 0) { this.fx.push({ type: 'sfx', name: 'bump' }); }
    p.vy = 0;
  }

  private reveal(e: Ent) {
    if (!e.vis) {
      e.vis = true; e.anim = 0;
      this.fx.push({ type: 'sfx', name: 'wall' });
      this.s.p.surpriseT = 0.8;
    }
  }

  private onJump() {
    const s = this.s, p = s.p;
    if (this.bp.ents.some(e => e.phase)) { s.toggle = !s.toggle; this.fx.push({ type: 'sfx', name: 'toggle' }); this.checkToggleClear(); }
    for (const t of s.trigs) if (t.on === 'jump' && (!t.once || !t.fired) && t.zone && overlap(p, t.zone)) this.fire(t);
  }

  private checkToggleClear() {
    const p = this.s.p;
    for (const e of this.s.ents) if (e.phase && overlap(p, e)) e.st.waitClear = true;
  }

  private onLand() {
    const s = this.s, p = s.p;
    for (const t of s.trigs) if (t.on === 'land' && (!t.once || !t.fired) && t.zone && overlap(p, t.zone)) this.fire(t);
  }

  /* ---------------- entities ---------------- */

  private updateEnts(dt: number) {
    const s = this.s, p = s.p;
    for (const e of s.ents) {
      const ox = e.x, oy = e.y;
      if (e.anim < 1) e.anim = Math.min(1, e.anim + dt / Math.max(0.01, e.animDur));
      if (e.shake > 0) e.shake -= dt;
      if (e.looks && e.looks.until !== undefined && s.time >= e.looks.until && e.looks.after) { e.look = e.looks.after; e.looks.until = undefined; }
      if (e.st.waitClear && !overlap(p, e)) e.st.waitClear = false;

      // tween (moveBy)
      const tw = e.st.tween;
      if (tw) {
        tw.t += dt;
        const k = Math.min(1, tw.t / tw.dur);
        e.x = tw.fx + (tw.tx - tw.fx) * k; e.y = tw.fy + (tw.ty - tw.fy) * k;
        if (k >= 1) e.st.tween = null;
      }

      const m = e.move;
      if (m && m.active && !m.done && m.path.length > 1) {
        if (m.waitT! > 0) m.waitT! -= dt;
        else {
          let dist = m.speed * dt;
          while (dist > 0 && !m.done) {
            const a = m.path[m.seg!], bIdx = m.fwd ? m.seg! + 1 : m.seg! - 1;
            const b = m.path[bIdx];
            const tx = e.x0 + b[0], ty = e.y0 + b[1];
            const ddx = tx - e.x, ddy = ty - e.y;
            const len = Math.hypot(ddx, ddy);
            void a;
            if (len <= dist) {
              e.x = tx; e.y = ty; dist -= len;
              m.seg = bIdx;
              if (m.wait) { m.waitT = m.wait; dist = 0; }
              const last = m.path.length - 1;
              if (m.fwd && m.seg === last) {
                if (m.mode === 'once') m.done = true;
                else if (m.mode === 'loop') { m.seg = 0; e.x = e.x0 + m.path[0][0]; e.y = e.y0 + m.path[0][1]; }
                else m.fwd = false;
              } else if (!m.fwd && m.seg === 0) m.fwd = true;
            } else { e.x += ddx / len * dist; e.y += ddy / len * dist; dist = 0; }
          }
        }
      }

      const tr = e.track;
      if (tr && tr.active) {
        tr.t! += dt;
        if (tr.t! > tr.dur) tr.active = false;
        else {
          const cx = p.x + p.w / 2 - e.w / 2, cy = p.y + p.h / 2 - e.h / 2;
          const v = tr.speed * dt;
          if (tr.axis !== 'y') e.x += Math.max(-v, Math.min(v, cx - e.x));
          if (tr.axis !== 'x') e.y += Math.max(-v, Math.min(v, cy - e.y));
        }
      }

      const fl = e.flee;
      if (fl && e.vis && p.alive) {
        if (fl.moving) {
          const spot = fl.spots[fl.idx!];
          const tx = e.x0 + spot[0], ty = e.y0 + spot[1];
          const ddx = tx - e.x, ddy = ty - e.y, len = Math.hypot(ddx, ddy), v = fl.speed * dt;
          if (len <= v) { e.x = tx; e.y = ty; fl.moving = false; fl.idx!++; }
          else { e.x += ddx / len * v; e.y += ddy / len * v; }
        } else if (fl.idx! < fl.spots.length) {
          const ddx = Math.abs(p.x + p.w / 2 - (e.x + e.w / 2)), ddy = Math.abs(p.y + p.h / 2 - (e.y + e.h / 2));
          if (ddx < fl.dist && ddy < 6 * TILE) { fl.moving = true; this.fx.push({ type: 'sfx', name: 'flee' }); p.surpriseT = 0.6; }
        }
      }

      const cr = e.crumble;
      if (cr) {
        const standing = p.alive && p.groundEnt === e.id;
        if (cr.state === 'idle' && standing) { cr.state = 'shaking'; cr.t = 0; }
        if (cr.state === 'shaking') {
          cr.t! += dt; e.shake = 0.05;
          if (cr.t! >= cr.delay) {
            cr.state = 'gone'; cr.goneT = 0;
            this.fx.push({ type: 'sfx', name: 'crumble' });
            this.fx.push({ type: 'burst', x: e.x + e.w / 2, y: e.y + e.h / 2, color: e.look, n: 10 });
          }
        } else if (cr.state === 'gone') {
          cr.goneT! += dt;
          if (cr.respawn > 0 && cr.goneT! > cr.respawn && !overlap(p, e)) { cr.state = 'idle'; e.anim = 0; }
        }
      }

      const dr = e.drop;
      if (dr && e.vis) {
        if (dr.state === 'idle') {
          const below = s.gsign > 0 ? p.y > e.y : p.y < e.y;
          if (p.alive && below && p.x + p.w > e.x - dr.range && p.x < e.x + e.w + dr.range) { dr.state = 'fall'; dr.t = 0; e.shake = dr.delay!; this.fx.push({ type: 'sfx', name: 'trap' }); }
        } else if (dr.state === 'fall') {
          dr.t! += dt;
          if (dr.t! > dr.delay!) {
            dr.vy = Math.min(dr.speed, dr.vy! + 3000 * dt);
            e.y += dr.vy! * dt * s.gsign;
            // stop on solid tile
            const probe = { x: e.x + 2, y: e.y, w: e.w - 4, h: e.h };
            const cy = Math.floor((s.gsign > 0 ? e.y + e.h : e.y) / TILE);
            const x1 = Math.floor(probe.x / TILE), x2 = Math.floor((probe.x + probe.w) / TILE);
            let hit = false;
            for (let cx = x1; cx <= x2; cx++) if (cy >= 0 && cy < this.bp.rows && this.bp.tiles[cy * this.bp.cols + cx]) hit = true;
            if (hit) {
              e.y = s.gsign > 0 ? cy * TILE - e.h : (cy + 1) * TILE;
              dr.state = 'done';
              this.fx.push({ type: 'shake', v: 5 }, { type: 'sfx', name: 'thud' });
            }
            if (e.y > this.bp.height + 200 || e.y < -400) dr.state = 'done';
          }
        }
      }

      const sh = e.shy;
      if (sh && !sh.done) {
        const d = Math.abs(p.x + p.w / 2 - (e.x + e.w / 2));
        if (d < sh.dist + e.w / 2 && Math.abs(p.y - e.y) < 4 * TILE) {
          sh.done = true;
          e.st.tween = { fx: e.x, fy: e.y, tx: e.x + sh.dx, ty: e.y, t: 0, dur: Math.abs(sh.dx) / sh.speed };
          this.fx.push({ type: 'sfx', name: 'flee' });
        }
      }

      if (e.kind === 'shadow' && e.vis) {
        const lagFrames = Math.round(e.props.lag / FIXED_DT);
        if (e.st.t === undefined) e.st.t = 0;
        e.st.t++;
        const idx = s.history.length / 2 - lagFrames;
        if (e.st.t > lagFrames && idx >= 0) { e.x = s.history[idx * 2]; e.y = s.history[idx * 2 + 1]; }
        else { e.x = this.bp.start.x; e.y = this.bp.start.y; }
      }

      e.dx = e.x - ox; e.dy = e.y - oy;

      // moving solid pushes the player
      if ((e.dx || e.dy) && p.alive && this.entSolid(e) && overlap(p, e)) {
        if (Math.abs(e.dy) >= Math.abs(e.dx)) p.y = e.dy > 0 ? e.y + e.h : e.y - p.h;
        else p.x = e.dx > 0 ? e.x + e.w : e.x - p.w;
        if (this.overlapsSolid(p)) this.kill('crush');
      }
    }
  }

  /* ---------------- interactions ---------------- */

  private interactions() {
    const s = this.s, p = s.p;
    for (const e of s.ents) {
      if (!e.vis) continue;
      switch (e.kind) {
        case 'goal':
          if (overlap(p, { x: e.x + 8, y: e.y + 10, w: e.w - 16, h: e.h - 10 })) {
            if (e.props.real) {
              s.won = true; p.vx = 0; p.vy = 0;
              this.fx.push({ type: 'win' }, { type: 'sfx', name: 'win' });
              return;
            } else if (!e.st.used) {
              e.st.used = true; s.fakeGoals++;
              this.fx.push({ type: 'fakeGoal' });
            }
          }
          break;
        case 'spring':
          if (overlap(p, e)) {
            const d = e.dir;
            if (d === 'u' || d === 'd') {
              const up = d === 'u' ? -1 : 1;
              p.vy = up * e.props.vy * (d === 'u' ? s.gsign : 1);
              if (e.props.vx) p.vx = e.props.vx;
            } else {
              p.vx = (d === 'r' ? 1 : -1) * e.props.vx || (d === 'r' ? 1 : -1) * e.props.vy;
              p.vy = -Math.abs(e.props.vy) * 0.35 * s.gsign;
            }
            p.grounded = false; p.groundEnt = null; p.launchT = 0.3; p.jumping = false; p.jumpT = 0.25;
            e.st.boing = 0.2;
            this.fx.push({ type: 'sfx', name: 'spring' });
            if (e.props.hidden && !e.st.seen) { e.st.seen = true; p.surpriseT = 0.8; }
          }
          break;
        case 'coin':
          if (e.props.deco && overlap(p, e)) { e.vis = false; s.coins++; this.fx.push({ type: 'sfx', name: 'coin' }, { type: 'burst', x: e.x + 10, y: e.y + 10, color: 'gold', n: 6 }); }
          break;
        case 'checkpoint':
          if (!e.props.fake && !e.st.on && overlap(p, e)) {
            e.st.on = true;
            s.checkpoint = { x: e.x - 3, y: e.y + e.h - 30, g: s.gsign, id: e.id };
            if (s.gsign < 0) s.checkpoint.y = e.y;
            this.fx.push({ type: 'checkpoint' }, { type: 'sfx', name: 'checkpoint' });
          }
          break;
        case 'portal':
          if (s.portalCd <= 0 && overlap(p, e)) {
            p.x = e.props.to[0]; p.y = e.props.to[1]; p.vy = 0;
            if (e.props.gravity) s.gsign = e.props.gravity;
            s.portalCd = 0.5;
            this.fx.push({ type: 'sfx', name: 'portal' }, { type: 'burst', x: p.x + 15, y: p.y + 15, color: 'portal', n: 14 });
          }
          break;
      }
    }
  }

  private hazards() {
    const s = this.s, p = s.p;
    const hb = { x: p.x + 4, y: p.y + 4, w: p.w - 8, h: p.h - 6 };
    for (const e of s.ents) {
      if (!e.vis || !e.deadly) continue;
      if (e.kind === 'spike') {
        if (e.anim < 0.4) continue;
        const k = e.anim;
        let r: Rect;
        switch (e.dir) {
          case 'u': r = { x: e.x + 5, y: e.y + e.h - e.h * 0.62 * k, w: e.w - 10, h: e.h * 0.62 * k }; break;
          case 'd': r = { x: e.x + 5, y: e.y, w: e.w - 10, h: e.h * 0.62 * k }; break;
          case 'l': r = { x: e.x + e.w - e.w * 0.62 * k, y: e.y + 5, w: e.w * 0.62 * k, h: e.h - 10 }; break;
          default: r = { x: e.x, y: e.y + 5, w: e.w * 0.62 * k, h: e.h - 10 };
        }
        if (overlap(hb, r)) return this.kill('spike');
      } else if (e.kind === 'saw' && e.props.rect) {
        if (overlap(hb, { x: e.x + 3, y: e.y + 4, w: e.w - 6, h: e.h - 4 })) return this.kill('saw');
      } else if (e.kind === 'saw' || e.kind === 'shadow') {
        const cx = e.x + e.w / 2, cy = e.y + e.h / 2, rad = e.w / 2 - 3;
        const nx = Math.max(hb.x, Math.min(cx, hb.x + hb.w)), ny = Math.max(hb.y, Math.min(cy, hb.y + hb.h));
        if ((nx - cx) ** 2 + (ny - cy) ** 2 < rad * rad) return this.kill(e.kind === 'shadow' ? 'shadow' : 'saw');
      } else if (e.kind === 'block') {
        if (overlap(hb, e)) return this.kill('crush');
      }
    }
    for (const e of s.ents) {
      if (e.kind === 'laser' && e.vis && this.blinkOn(e)) {
        const r = e.props.vertical ? { x: e.x + e.w / 2 - 6, y: e.y, w: 12, h: e.h } : { x: e.x, y: e.y + e.h / 2 - 6, w: e.w, h: 12 };
        if (overlap(hb, r)) return this.kill('laser');
      }
    }
  }

  kill(cause: string) {
    const p = this.s.p;
    if (!p.alive || this.s.won) return;
    if ((this.s.invulT || 0) > 0 && cause !== 'fall' && cause !== 'crush') return;
    if (this.s.shield && cause !== 'fall' && cause !== 'crush') {
      this.s.shield = false; this.s.invulT = 1.5; p.vy = -600 * this.s.gsign;
      this.fx.push({ type: 'sfx', name: 'shield' }, { type: 'burst', x: p.x + 15, y: p.y + 15, color: 'portal', n: 16 });
      return;
    }
    p.alive = false; p.deathCause = cause; p.deathT = 0;
    this.fx.push({ type: 'death', cause }, { type: 'sfx', name: 'death' }, { type: 'shake', v: 9 },
      { type: 'burst', x: p.x + p.w / 2, y: p.y + p.h / 2, color: 'player', n: 22 });
    for (const t of this.s.trigs) if (t.on === 'death') this.fire(t);
  }

  /* ---------------- triggers ---------------- */

  private evalTriggers() {
    const s = this.s, p = s.p;
    for (const t of s.trigs) {
      if (t.once && t.fired) continue;
      switch (t.on) {
        case 'enter': case 'inside': case 'leave': {
          if (!t.zone) break;
          const inside = overlap(p, t.zone);
          if (t.on === 'enter' && inside && !t.inside) this.fire(t);
          else if (t.on === 'inside' && inside) this.fire(t);
          else if (t.on === 'leave' && !inside && t.inside) this.fire(t);
          t.inside = inside;
          break;
        }
        case 'touch': {
          const es = this.targets(t.target);
          if (es.some(e => e.vis && overlap(p, e))) this.fire(t);
          break;
        }
        case 'stand': {
          const es = this.targets(t.target);
          const on = p.grounded && es.some(e => e.id === p.groundEnt);
          if (on) { t.standT = (t.standT || 0) + FIXED_DT; if (t.standT >= t.delay) { this.fireNow(t); } }
          else t.standT = 0;
          break;
        }
      }
    }
  }

  private processEvents() {
    let guard = 0;
    while (this.s.events.length && guard++ < 20) {
      const ev = this.s.events.shift()!;
      for (const t of this.s.trigs) if (t.on === 'event' && t.event === ev && (!t.once || !t.fired)) this.fire(t);
    }
  }

  private fire(t: Trig) {
    t.fired = true;
    for (const a of t.actions) this.schedule(a, t.delay);
  }
  private fireNow(t: Trig) {
    t.fired = true;
    for (const a of t.actions) this.schedule(a, 0);
  }

  private schedule(a: Act, extra: number) {
    const d = (a.d || 0) + extra;
    if (d <= 0) this.doAct(a);
    else this.s.queue.push({ t: this.s.time + d, act: a });
  }

  doAct(a: Act) {
    const s = this.s, p = s.p;
    const ts = this.targets(a.t);
    // staggered group action: each member (sorted by x, or reversed when x === -1) i*step later
    if (a.step && ts.length > 1 && a.a !== 'pop') {
      const sorted = ts.slice().sort((m, n) => (a.x === -1 ? n.x - m.x : m.x - n.x));
      sorted.forEach((e, i) => { const one: Act = { ...a, t: e.id, step: undefined, d: 0 }; if (i === 0) this.doAct(one); else s.queue.push({ t: s.time + i * a.step!, act: one }); });
      return;
    }
    switch (a.a) {
      case 'show': for (const e of ts) { e.vis = true; e.anim = 1; } break;
      case 'hide': for (const e of ts) { if (e.vis && e.kind === 'spike') this.fx.push({ type: 'sfx', name: 'retract' }); e.vis = false; } break;
      case 'pop': {
        if (a.step) {
          const sorted = ts.slice().sort((x, y) => (a.x === -1 ? y.x - x.x : x.x - y.x));
          sorted.forEach((e, i) => { if (i === 0) this.popEnt(e); else s.queue.push({ t: s.time + i * a.step!, act: { a: 'pop', t: e.id } }); });
        } else for (const e of ts) this.popEnt(e);
        break;
      }
      case 'solid': for (const e of ts) { e.solid = true; if (overlap(p, e)) e.st.waitClear = true; } break;
      case 'unsolid': for (const e of ts) e.solid = false; break;
      case 'deadly': for (const e of ts) e.deadly = true; break;
      case 'safe': for (const e of ts) e.deadly = false; break;
      case 'move': for (const e of ts) if (e.move) e.move.active = true; break;
      case 'stop': for (const e of ts) { if (e.move) e.move.active = false; if (e.track) e.track.active = false; } break;
      case 'moveBy': for (const e of ts) e.st.tween = { fx: e.x, fy: e.y, tx: e.x + (a.x || 0) * TILE, ty: e.y + (a.y || 0) * TILE, t: 0, dur: a.dur ?? 0.3 }; break;
      case 'track': for (const e of ts) if (e.track) { e.track.active = true; e.track.t = 0; } break;
      case 'emit': if (a.ev) s.events.push(a.ev); break;
      case 'shake': this.fx.push({ type: 'shake', v: a.v ?? 5 }); break;
      case 'say': this.fx.push({ type: 'say', text: a.text || '', dur: a.dur ?? 2.2 }); break;
      case 'sfx': this.fx.push({ type: 'sfx', name: a.name || 'trap' }); break;
      case 'gravity': {
        const ng = a.v === 0 || a.v === undefined ? -s.gsign : (a.v! > 0 ? 1 : -1);
        if (ng !== s.gsign) { s.gsign = ng; p.grounded = false; p.groundEnt = null; p.surpriseT = 0.5; }
        break;
      }
      case 'reverse': s.reverseT = a.dur ?? 3; break;
      case 'speed': s.speedMul = a.v ?? 1.8; s.speedT = a.dur ?? 2; break;
      case 'fakeWin': s.freezeT = a.dur ?? 1.6; p.vx = 0; p.vy = 0; this.fx.push({ type: 'fakeWin' }, { type: 'sfx', name: 'fakewin' }); break;
      case 'fakeEnding': s.freezeT = a.dur ?? 4.2; p.vx = 0; p.vy = 0; this.fx.push({ type: 'fakeEnding' }); break;
      case 'freeze': s.freezeT = a.dur ?? 1; break;
      case 'teleport': p.x = (a.x || 0) * TILE + 5; p.y = (a.y || 0) * TILE + 10; p.vx = 0; p.vy = 0; this.fx.push({ type: 'sfx', name: 'portal' }); break;
      case 'look': for (const e of ts) { e.look = (a.look as any) || e.look; if (a.look === 'spike' && e.kind === 'checkpoint') { e.kind = 'spike'; e.deadly = true; e.dir = 'u'; e.anim = 0; e.x -= 8; e.w = 40; e.y += e.h - 40; e.h = 40; e.animDur = 0.15; } } break;
      case 'wind': for (const e of ts) if (e.props.zone === 'wind') e.props.fx = (a.v || 0) * TILE; break;
      case 'dark': s.dark = a.v || 0; break;
      case 'mirror': s.mirror = !!a.v; break;
      case 'surprise': p.surpriseT = 0.8; this.fx.push({ type: 'surprise' }); break;
      case 'crumble': for (const e of ts) if (e.crumble) { e.crumble.state = 'shaking'; e.crumble.t = 0; } else { e.crumble = { delay: a.v ?? 0.2, respawn: 0, state: 'shaking', t: 0 }; } break;
      case 'mspeed': for (const e of ts) { if (e.move) e.move.speed = (a.v ?? 3) * TILE; if (e.track) e.track.speed = (a.v ?? 3) * TILE; } break;
      case 'mrev': for (const e of ts) if (e.move) { const m = e.move; if (m.mode === 'pingpong') { m.fwd = !m.fwd; m.seg = m.fwd ? Math.max(0, m.seg! - 1) : Math.min(m.path.length - 1, m.seg! + 1); } } break;
      case 'goalReal': for (const e of ts) if (e.kind === 'goal') e.props.real = a.v !== 0; break;
    }
  }

  private popEnt(e: Ent) {
    const p = this.s.p;
    e.vis = true; e.anim = 0;
    if (e.kind === 'block') {
      e.solid = true;
      if (overlap(p, e)) e.st.waitClear = true;
      this.fx.push({ type: 'burst', x: e.x + e.w / 2, y: e.y + e.h / 2, color: e.look, n: 6 });
    }
    if (e.kind === 'spike') this.fx.push({ type: 'burst', x: e.x + e.w / 2, y: e.y + e.h * (e.dir === 'd' ? 0 : 1), color: 'spike', n: 4 });
  }
}

/** Fast structural clone of the mutable world state (used by the solver). */
function cloneEnt(e: Ent): Ent {
  const c: Ent = { ...e, props: e.props, st: { ...e.st } };
  c.props = { ...e.props };
  if (e.st.tween) c.st.tween = { ...e.st.tween };
  if (e.move) c.move = { ...e.move };
  if (e.track) c.track = { ...e.track };
  if (e.flee) c.flee = { ...e.flee };
  if (e.crumble) c.crumble = { ...e.crumble };
  if (e.drop) c.drop = { ...e.drop };
  if (e.shy) c.shy = { ...e.shy };
  if (e.looks) c.looks = { ...e.looks };
  return c;
}
export function cloneState(s: WorldState): WorldState {
  return {
    ...s,
    p: { ...s.p },
    ents: s.ents.map(cloneEnt),
    trigs: s.trigs.map(t => ({ ...t })),
    queue: s.queue.slice(),
    events: s.events.slice(),
    checkpoint: s.checkpoint ? { ...s.checkpoint } : null,
    history: s.history.length ? s.history.slice() : s.history,
  };
}
