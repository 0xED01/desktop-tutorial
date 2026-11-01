// Builds the HTML5 runtime (used by the Android APK and for browser testing) into web/dist.
import { build } from 'esbuild';
import fs from 'node:fs';
import path from 'node:path';
const pkg = JSON.parse(fs.readFileSync('package.json', 'utf8'));
const out = 'web/dist';
fs.rmSync(out, { recursive: true, force: true });
fs.mkdirSync(out, { recursive: true });
await build({
  entryPoints: ['web/src/main.ts'], bundle: true, minify: true, format: 'iife', target: ['es2019', 'chrome70'],
  tsconfig: 'tsconfig.json', outfile: path.join(out, 'game.js'), define: { __VERSION__: JSON.stringify(pkg.version) }, logLevel: 'warning',
});
fs.copyFileSync('web/index.html', path.join(out, 'index.html'));
const audioSrc = 'TrustNothing/assets/resources/audio';
for (const sub of ['music', 'sfx']) {
  const from = path.join(audioSrc, sub), to = path.join(out, 'audio', sub);
  fs.mkdirSync(to, { recursive: true });
  if (fs.existsSync(from)) for (const f of fs.readdirSync(from)) if (f.endsWith('.ogg')) fs.copyFileSync(path.join(from, f), path.join(to, f));
}
const size = fs.statSync(path.join(out, 'game.js')).size;
console.log(`web build ok: ${out}/game.js ${(size / 1024).toFixed(0)} KB`);
