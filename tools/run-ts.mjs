// Bundle a TypeScript entry with esbuild and run it in Node (used for tests/tools).
import { build } from 'esbuild';
import { pathToFileURL } from 'node:url';
import path from 'node:path';
import fs from 'node:fs';
const entry = process.argv[2];
if (!entry) { console.error('usage: node tools/run-ts.mjs <file.ts> [args]'); process.exit(2); }
const outDir = path.resolve('node_modules/.cache/run-ts');
fs.mkdirSync(outDir, { recursive: true });
const out = path.join(outDir, path.basename(entry).replace(/\.ts$/, '') + '.mjs');
await build({ entryPoints: [entry], bundle: true, platform: 'node', format: 'esm', outfile: out, target: 'node20', logLevel: 'warning', sourcemap: 'inline', tsconfig: 'tsconfig.json' });
process.argv.splice(2, 1);
await import(pathToFileURL(out).href + '?t=' + Date.now());
