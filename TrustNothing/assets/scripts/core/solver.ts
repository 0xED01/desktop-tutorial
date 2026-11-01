/**
 * Automatic route finder — proves every level has a real, repeatable solution.
 * Best-first search over input sequences on the deterministic simulation.
 * Each decision holds one of 6 inputs for STEP frames.
 */
import { World } from './world';
import { Blueprint } from './levelLoader';
import { InputState, TILE } from './types';

export const STEP = 6;
export const ACTIONS: InputState[] = [
  { left: false, right: false, jump: false },
  { left: true, right: false, jump: false },
  { left: false, right: true, jump: false },
  { left: false, right: false, jump: true },
  { left: true, right: false, jump: true },
  { left: false, right: true, jump: true },
];

export interface SolveResult { ok: boolean; actions: number[]; expanded: number; frames: number; reason?: string; deathsSeen: number; }

interface Node { snap: ReturnType<World['snapshot']>; acts: number[]; g: number; f: number; wp: number; }

class Heap {
  a: Node[] = [];
  push(n: Node) { const a = this.a; a.push(n); let i = a.length - 1; while (i > 0) { const p = (i - 1) >> 1; if (a[p].f <= a[i].f) break; [a[p], a[i]] = [a[i], a[p]]; i = p; } }
  pop(): Node | undefined {
    const a = this.a; if (!a.length) return undefined; const top = a[0]; const last = a.pop()!;
    if (a.length) { a[0] = last; let i = 0; for (;;) { const l = 2 * i + 1, r = l + 1; let m = i; if (l < a.length && a[l].f < a[m].f) m = l; if (r < a.length && a[r].f < a[m].f) m = r; if (m === i) break; [a[m], a[i]] = [a[i], a[m]]; i = m; } }
    return top;
  }
  get size() { return this.a.length; }
}

function key(w: World): string {
  const s = w.s, p = s.p;
  let k = `${Math.round(p.x / 6)},${Math.round(p.y / 6)},${Math.round(p.vy / 120)},${Math.round(p.vx / 100)},${p.grounded ? 1 : 0},${s.gsign},${s.reverseT > 0 ? 1 : 0},${s.toggle ? 1 : 0},${s.freezeT > 0 ? Math.ceil(s.freezeT * 10) : 0},${s.queue.length ? Math.ceil((s.queue.reduce((m, q) => Math.min(m, q.t), 1e9) - s.time) * 10) + ':' + s.queue.length : 0},${s.checkpoint ? s.checkpoint.id : ''}`;
  let bits = '';
  for (const t of s.trigs) bits += t.fired ? '1' : '0';
  k += bits;
  for (const e of s.ents) {
    if (e.move || e.track || e.flee || e.drop || e.st.tween || e.shy || e.kind === 'shadow') k += `|${Math.round(e.x / 8)},${Math.round(e.y / 8)}`;
    if (e.crumble) k += e.crumble.state![0];
    if (e.blink) k += w.blinkOn(e) ? 'B' : 'b';
    if (!e.vis) k += 'h';
  }
  return k;
}

function heuristic(w: World, goalIds: string[], route: [number, number][], wp: number): number {
  const p = w.s.p;
  if (wp < route.length) {
    const [cx, cy] = route[wp];
    let h = Math.abs(cx * TILE + 20 - (p.x + p.w / 2)) / 300 + Math.abs(cy * TILE + 20 - (p.y + p.h / 2)) / 700;
    for (let i = wp; i + 1 < route.length; i++) h += Math.abs(route[i + 1][0] - route[i][0]) * TILE / 300 + Math.abs(route[i + 1][1] - route[i][1]) * TILE / 700;
    return h + 50 * (route.length - wp);
  }
  let best = Infinity;
  for (const e of w.s.ents) {
    if (!goalIds.includes(e.id)) continue;
    const dx = Math.abs(e.x + e.w / 2 - (p.x + p.w / 2));
    const dy = Math.abs(e.y + e.h / 2 - (p.y + p.h / 2));
    best = Math.min(best, dx / 300 + dy / 700);
  }
  return best === Infinity ? 0 : best;
}

export function solve(bp: Blueprint, opts: { maxExpand?: number; maxTime?: number; weight?: number; startCp?: any } = {}): SolveResult {
  const maxExpand = opts.maxExpand ?? 250000;
  const maxTime = opts.maxTime ?? 120; // seconds of game time
  const W = opts.weight ?? 2.5;
  const world = new World(bp, opts.startCp || null);
  world.fx.length = 0;
  const goalIds = world.s.ents.filter(e => e.kind === 'goal' && e.props.real).map(e => e.id);
  if (!goalIds.length) return { ok: false, actions: [], expanded: 0, frames: 0, reason: 'no real goal', deathsSeen: 0 };
  const heap = new Heap();
  const seen = new Set<string>();
  heap.push({ snap: world.snapshot(), acts: [], g: 0, f: 0, wp: 0 });
  const route: [number, number][] = (bp.def as any).route || [];
  let expanded = 0, deaths = 0;
  while (heap.size) {
    const n = heap.pop()!;
    if (++expanded > maxExpand) break;
    for (let ai = 0; ai < ACTIONS.length; ai++) {
      world.restore(n.snap);
      const inp = ACTIONS[ai];
      let dead = false, won = false;
      for (let f = 0; f < STEP; f++) {
        world.step(inp);
        if (world.s.won) { won = true; break; }
        if (!world.s.p.alive) { dead = true; break; }
      }
      world.fx.length = 0;
      const acts = n.acts.concat(ai);
      if (won) return { ok: true, actions: acts, expanded, frames: acts.length * STEP, deathsSeen: deaths };
      if (dead) { deaths++; continue; }
      const g = n.g + STEP / 60;
      if (g > maxTime) continue;
      let wp = n.wp;
      if (wp < route.length) {
        const p = world.s.p;
        if (Math.abs(route[wp][0] * TILE + 20 - (p.x + 15)) < 50 && Math.abs(route[wp][1] * TILE + 20 - (p.y + 15)) < 60) wp++;
      }
      const k = key(world) + '#' + wp;
      if (seen.has(k)) continue;
      seen.add(k);
      heap.push({ snap: world.snapshot(), acts, g, f: g + W * heuristic(world, goalIds, route, wp), wp });
    }
  }
  return { ok: false, actions: [], expanded, frames: 0, reason: expanded > maxExpand ? 'search limit' : 'exhausted', deathsSeen: deaths };
}

/** Replays a recorded solution; returns true if it wins. */
export function replay(bp: Blueprint, actions: number[]): { won: boolean; died: boolean; frame: number } {
  const w = new World(bp);
  let frame = 0;
  for (const a of actions) {
    for (let f = 0; f < STEP; f++) {
      w.step(ACTIONS[a]); frame++;
      if (w.s.won) return { won: true, died: false, frame };
      if (!w.s.p.alive) return { won: false, died: true, frame };
    }
  }
  // let a final fall/settle play out
  for (let f = 0; f < 60; f++) { w.step(ACTIONS[0]); if (w.s.won) return { won: true, died: false, frame }; }
  return { won: false, died: false, frame };
}

export function tileOf(px: number) { return Math.floor(px / TILE); }
