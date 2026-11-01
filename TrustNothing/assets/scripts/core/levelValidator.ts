/**
 * Structural validation of level data (runs in tests and at boot in debug).
 */
import { Blueprint, loadLevel } from './levelLoader';
import { LevelDef, TILE } from './types';
import { World } from './world';
import { PRESETS } from './presets';

export interface ValidationReport { id: number; ok: boolean; errors: string[]; warnings: string[]; }

const KNOWN_ACTS = new Set(['show', 'hide', 'pop', 'solid', 'unsolid', 'deadly', 'safe', 'move', 'stop', 'moveBy', 'track', 'emit', 'shake', 'say', 'sfx',
  'gravity', 'reverse', 'fakeWin', 'fakeEnding', 'speed', 'teleport', 'look', 'wind', 'dark', 'mirror', 'surprise', 'crumble', 'goalReal', 'freeze', 'mspeed', 'mrev']);
const KNOWN_ON = new Set(['enter', 'inside', 'leave', 'jump', 'land', 'event', 'start', 'touch', 'stand', 'death']);

export function validateLevel(def: LevelDef, bp: Blueprint = loadLevel(def)): ValidationReport {
  const errors = bp.errors.slice();
  const warnings: string[] = [];
  if (!def.name) errors.push('missing name');
  if (!def.hint) warnings.push('missing hint');
  if (bp.rows < 10 || bp.rows > 40) errors.push(`rows ${bp.rows} out of range`);
  if (bp.cols < 20 || bp.cols > 240) errors.push(`cols ${bp.cols} out of range`);

  const goals = bp.ents.filter(e => e.kind === 'goal' && e.props.real);
  if (!goals.length) errors.push('no real goal');

  for (const [ch, spec] of Object.entries(def.defs || {})) {
    if (ch.length !== 1) errors.push(`def key '${ch}' must be a single char`);
    if (!PRESETS[spec.t]) errors.push(`def '${ch}': unknown preset ${spec.t}`);
    if (spec.delay !== undefined && (typeof spec.delay !== 'number' || spec.delay < 0)) errors.push(`def '${ch}': bad delay`);
    if (spec.dur !== undefined && (typeof spec.dur !== 'number' || spec.dur <= 0)) errors.push(`def '${ch}': bad dur`);
  }
  const groups = new Set(bp.ents.map(e => e.group)); const ids = new Set(bp.ents.map(e => e.id));
  for (const t of bp.trigs) {
    if (!KNOWN_ON.has(t.on)) errors.push(`trigger ${t.id}: unknown on '${t.on}'`);
    if ((t.on === 'touch' || t.on === 'stand') && !t.target) errors.push(`trigger ${t.id}: '${t.on}' needs target`);
    if (t.on === 'event' && !t.event) errors.push(`trigger ${t.id}: event trigger without event`);
    if (t.delay < 0) errors.push(`trigger ${t.id}: negative delay`);
    for (const a of t.actions) {
      if (!KNOWN_ACTS.has(a.a)) errors.push(`trigger ${t.id}: unknown action '${a.a}'`);
      if (a.t) for (const n of a.t.split(',')) if (!groups.has(n.trim()) && !ids.has(n.trim())) errors.push(`trigger ${t.id}: action ${a.a} target '${n}' not found`);
      if (a.d !== undefined && a.d < 0) errors.push(`trigger ${t.id}: negative action delay`);
    }
    if (t.target) for (const n of t.target.split(',')) if (!groups.has(n.trim()) && !ids.has(n.trim())) errors.push(`trigger ${t.id}: target '${n}' not found`);
  }
  // events emitted must have listeners (warn only)
  const listened = new Set(bp.trigs.filter(t => t.on === 'event').map(t => t.event));
  for (const t of bp.trigs) for (const a of t.actions) if (a.a === 'emit' && a.ev && a.ev !== 'fakegoal' && !listened.has(a.ev)) warnings.push(`event '${a.ev}' has no listener`);

  // spawn safety: start must not overlap solid/hazard, and idling 2 s must not kill
  if (!errors.length) {
    const w = new World(bp);
    if (w.overlapsSolid(w.s.p)) errors.push('spawn inside solid');
    const sx = Math.floor((bp.start.x + 15) / TILE), sy = Math.floor((bp.start.y + 31) / TILE);
    if (sy >= bp.rows || !bp.tiles[sy * bp.cols + sx]) {
      const groundEnt = bp.ents.some(e => e.kind === 'block' && e.solid && e.x <= bp.start.x + 15 && e.x + e.w >= bp.start.x + 15 && Math.abs(e.y - (bp.start.y + 30)) < 2);
      if (!groundEnt) warnings.push('start not on static ground');
    }
    for (let i = 0; i < 120; i++) w.step({ left: false, right: false, jump: false });
    if (!w.s.p.alive) errors.push(`idle at spawn dies (${w.s.p.deathCause}) — spawn must be safe`);
    // reset determinism: two fresh worlds must be identical after the same inputs
    const a = new World(bp), b = new World(bp);
    for (let i = 0; i < 90; i++) { a.step({ left: false, right: true, jump: i % 30 < 10 }); }
    b.reset(); for (let i = 0; i < 90; i++) { b.step({ left: false, right: true, jump: i % 30 < 10 }); }
    a.reset(); for (let i = 0; i < 90; i++) { a.step({ left: false, right: true, jump: i % 30 < 10 }); }
    if (Math.abs(a.s.p.x - b.s.p.x) > 0.001 || Math.abs(a.s.p.y - b.s.p.y) > 0.001 || a.s.trigs.some((t, i) => t.fired !== b.s.trigs[i].fired)) errors.push('reset is not deterministic');
  }
  return { id: def.id, ok: errors.length === 0, errors, warnings };
}
