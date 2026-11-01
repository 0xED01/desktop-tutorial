/**
 * ASCII level loader. Map legend (one char = one 40px tile):
 *   .  or space   empty
 *   #             ground (solid)
 *   B             brick  (solid, decorative variant)
 *   =             floating platform (solid, cloud look)
 *   S             player start (bottom of tile)
 *   G             real goal door
 *   C             real checkpoint
 *   ^ v < >       static spikes (up / down / left / right)
 *   o             decorative coin (harmless, no trigger)
 *   any other     must be declared in level.defs → preset
 * Regions: same chars that touch horizontally form a run; runs with identical
 * span on consecutive rows merge into a rectangle.
 */
import { Act, Ent, LevelDef, Rect, TILE, Trig, TrigSpec } from './types';
import { PRESETS, PresetCtx } from './presets';

export interface Blueprint {
  def: LevelDef;
  cols: number; rows: number;
  width: number; height: number;
  tiles: Uint8Array;     // 0 empty, 1 ground, 2 brick, 3 cloud
  start: { x: number; y: number };
  ents: Ent[];
  trigs: Trig[];
  errors: string[];
}

const SPLIT_DEFAULT = new Set(['crumble', 'memory', 'toggle']);
const BUILTIN = new Set(['.', ' ', '#', 'B', '=', 'S', 'G', 'C', '^', 'v', '<', '>', 'o']);

export function regionsOf(map: string[], ch: string): Rect[] {
  const rows = map.length;
  const used: boolean[][] = map.map(r => Array.from(r, () => false));
  const out: Rect[] = [];
  for (let y = 0; y < rows; y++) {
    const row = map[y];
    for (let x = 0; x < row.length; x++) {
      if (row[x] !== ch || used[y][x]) continue;
      let x2 = x;
      while (x2 + 1 < row.length && row[x2 + 1] === ch && !used[y][x2 + 1]) x2++;
      let y2 = y;
      outer: while (y2 + 1 < rows) {
        const nr = map[y2 + 1];
        for (let k = x; k <= x2; k++) if (nr[k] !== ch || used[y2 + 1][k]) break outer;
        if (nr[x - 1] === ch || nr[x2 + 1] === ch) break; // spans must match exactly
        y2++;
      }
      for (let yy = y; yy <= y2; yy++) for (let k = x; k <= x2; k++) used[yy][k] = true;
      out.push({ x: x * TILE, y: y * TILE, w: (x2 - x + 1) * TILE, h: (y2 - y + 1) * TILE });
    }
  }
  return out;
}

function range(v: number | [number, number] | undefined, max: number): [number, number] {
  if (v === undefined) return [0, max - 1];
  if (typeof v === 'number') return [v, v];
  return [Math.min(v[0], v[1]), Math.max(v[0], v[1])];
}

export function zoneFromCols(x: number | [number, number] | undefined, y: number | [number, number] | undefined, cols: number, rows: number): Rect {
  const [x1, x2] = range(x, cols);
  const [y1, y2] = range(y, rows);
  return { x: x1 * TILE, y: y1 * TILE - (y === undefined ? 400 : 0), w: (x2 - x1 + 1) * TILE, h: (y2 - y1 + 1) * TILE + (y === undefined ? 800 : 0) };
}

export function blankEnt(): Ent {
  return {
    id: '', group: '', kind: 'block', look: 'ground', x: 0, y: 0, w: TILE, h: TILE, x0: 0, y0: 0,
    vis: true, solid: false, deadly: false, dir: 'u', anim: 1, animDur: 0.1, alpha: 1, shake: 0, dx: 0, dy: 0, props: {}, st: {},
  };
}

export function loadLevel(def: LevelDef): Blueprint {
  const errors: string[] = [];
  const map = def.map.slice();
  const rows = map.length;
  const cols = Math.max(...map.map(r => r.length));
  for (let i = 0; i < rows; i++) {
    map[i] = map[i].padEnd(cols, '.');
  }
  const tiles = new Uint8Array(cols * rows);
  const ents: Ent[] = [];
  const trigs: Trig[] = [];
  let start = { x: 0, y: 0 }; let starts = 0;
  const ids = new Set<string>();
  const defs = def.defs || {};

  const add = (char: string, index: number, p: Partial<Ent> & { kind: Ent['kind'] }, region: Rect): Ent => {
    const e = blankEnt();
    Object.assign(e, { x: region.x, y: region.y, w: region.w, h: region.h }, p);
    e.props = Object.assign({}, p.props || {});
    e.st = {};
    e.group = (defs[char] && defs[char].group) || char;
    let id = p.id || `${char}${index}`;
    if (ids.has(id)) { let n = 1; while (ids.has(`${id}_${n}`)) n++; id = `${id}_${n}`; }
    ids.add(id);
    e.id = id; e.x0 = e.x; e.y0 = e.y;
    ents.push(e);
    return e;
  };

  // built-ins
  for (let y = 0; y < rows; y++) for (let x = 0; x < cols; x++) {
    const c = map[y][x];
    if (c === '#') tiles[y * cols + x] = 1;
    else if (c === 'B') tiles[y * cols + x] = 2;
    else if (c === '=') tiles[y * cols + x] = 3;
    else if (defs[c]) continue;
    else if (c === 'S') { start = { x: x * TILE + 5, y: y * TILE + TILE - 30 }; starts++; }
    else if (!BUILTIN.has(c) && !defs[c]) errors.push(`undefined char '${c}' at ${x},${y}`);
  }
  if (starts !== 1) errors.push(`expected exactly one S, found ${starts}`);

  const builtinSpikes: Record<string, Ent['dir']> = { '^': 'u', 'v': 'd', '<': 'l', '>': 'r' };
  for (const ch of Object.keys(builtinSpikes)) {
    if (defs[ch]) continue;
    regionsOf(map, ch).forEach((r, i) => add('_' + ch, i, { kind: 'spike', look: 'spike', dir: builtinSpikes[ch], deadly: true }, r));
  }
  if (!defs['G']) regionsOf(map, 'G').forEach((r, i) => add('G', i, { kind: 'goal', look: 'door', x: r.x + 2, y: r.y + r.h - 56, w: 36, h: 56, props: { real: true } }, r));
  if (!defs['C']) regionsOf(map, 'C').forEach((r, i) => add('C', i, { kind: 'checkpoint', look: 'flag', x: r.x + 8, y: r.y + r.h - 60, w: 24, h: 60, props: { fake: false } }, r));
  if (!defs['o']) regionsOf(map, 'o').forEach((r, i) => add('o', i, { kind: 'coin', look: 'coin', x: r.x + 10, y: r.y + 10, w: 20, h: 20, props: { deco: true } }, r));

  // defs
  for (const ch of Object.keys(defs)) {
    const spec = defs[ch];
    const preset = PRESETS[spec.t];
    if (!preset) { errors.push(`def '${ch}': unknown preset '${spec.t}'`); continue; }
    let regs = regionsOf(map, ch);
    const split = spec.split ?? SPLIT_DEFAULT.has(spec.t);
    if (split) regs = regs.flatMap(r => { const out: Rect[] = []; for (let yy = 0; yy < r.h; yy += TILE) for (let xx = 0; xx < r.w; xx += TILE) out.push({ x: r.x + xx, y: r.y + yy, w: TILE, h: TILE }); return out; });
    if (regs.length === 0 && !spec.virtual) errors.push(`def '${ch}' (${spec.t}) not placed on map`);
    regs.forEach((region, index) => {
      const ctx: PresetCtx = {
        char: ch, region, index, spec, rows, cols,
        addEnt: (p) => add(ch, index, p, region),
        addTrig: (t) => {
          trigs.push({ id: `${ch}${index}_t${trigs.length}`, delay: 0, once: true, fired: false, inside: false, ...t } as Trig);
        },
        zone: (nearDefault = 1) => {
          if (spec.at !== undefined) return zoneFromCols(spec.at, spec.aty, cols, rows);
          const n = (spec.near ?? nearDefault) * TILE;
          const z = { x: region.x - n, y: region.y - 400, w: region.w + 2 * n, h: region.h + 800 };
          if (spec.aty !== undefined) { const [a, b] = range(spec.aty, rows); z.y = a * TILE; z.h = (b - a + 1) * TILE; }
          return z;
        },
      };
      try { preset.build(ctx); } catch (err: any) { errors.push(`def '${ch}': ${err.message || err}`); }
    });
  }

  // explicit triggers
  (def.triggers || []).forEach((t: TrigSpec, i) => {
    const needsZone = t.on === 'enter' || t.on === 'inside' || t.on === 'leave' || t.on === 'jump' || t.on === 'land';
    trigs.push({
      id: `T${i}`, on: t.on, zone: needsZone || t.x !== undefined ? zoneFromCols(t.x, t.y, cols, rows) : undefined,
      target: t.target, event: t.event, delay: t.delay || 0, once: t.once !== false, actions: t.do as Act[], fired: false, inside: false,
    });
  });

  return { def, cols, rows, width: cols * TILE, height: rows * TILE, tiles, start, ents, trigs, errors };
}
