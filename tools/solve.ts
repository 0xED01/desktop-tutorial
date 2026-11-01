// Usage: npm run solve -- [levelId|all] [--save]
import { LEVELS } from '../TrustNothing/assets/scripts/levels/index';
import { loadLevel } from '../TrustNothing/assets/scripts/core/levelLoader';
import { validateLevel } from '../TrustNothing/assets/scripts/core/levelValidator';
import { solve, replay } from '../TrustNothing/assets/scripts/core/solver';
import fs from 'node:fs';

const arg = process.argv[2] || 'all';
const save = process.argv.includes('--save');
const file = 'tests/solutions.json';
const sols: Record<string, number[]> = fs.existsSync(file) ? JSON.parse(fs.readFileSync(file, 'utf8')) : {};
const ids = arg === 'all' ? LEVELS.map(l => l.id) : arg.split(',').flatMap(a => a.includes('-') ? (([x, y]) => Array.from({ length: y - x + 1 }, (_, i) => x + i))(a.split('-').map(Number)) : [Number(a)]);
let fails = 0;
for (const id of ids) {
  const def = LEVELS.find(l => l.id === id);
  if (!def) { console.log(`#${id} missing`); fails++; continue; }
  const bp = loadLevel(def);
  const v = validateLevel(def, bp);
  if (!v.ok) { console.log(`#${id} ${def.name} INVALID: ${v.errors.join('; ')}`); fails++; continue; }
  if (!process.argv.includes('--force') && sols[id] && replay(bp, sols[id]).won) { console.log(`#${id} ${def.name} ok (cached)`); continue; }
  const t0 = Date.now();
  const r = solve(bp, { maxExpand: Number(process.env.MAXEXP || 300000), weight: Number(process.env.WEIGHT || 2.5) });
  const dt = ((Date.now() - t0) / 1000).toFixed(1);
  if (r.ok) {
    const rp = replay(bp, r.actions);
    console.log(`#${id} ${def.name} SOLVED in ${(r.frames / 60).toFixed(1)}s game time, ${r.expanded} nodes, ${dt}s, deaths explored ${r.deathsSeen}, replay=${rp.won}`);
    sols[id] = r.actions;
  } else { console.log(`#${id} ${def.name} UNSOLVED (${r.reason}, ${r.expanded} nodes, ${dt}s)`); fails++; }
  if (v.warnings.length) console.log('   warn: ' + v.warnings.join('; '));
}
if (save) fs.writeFileSync(file, JSON.stringify(sols));
console.log(fails ? `${fails} problem(s)` : 'all good');
