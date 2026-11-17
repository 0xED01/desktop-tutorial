"""Re-align from the marking board's points.
usage: python3 sync.py <points.json> <frame-version> <workdir>
points.json: [{"id":..,"old":{"x","y"},"new":{"x","y"}}, ...] in aligned-frame (2025 image) pixels.
Updates pairs_base.json, ../laojia_compare.html, the three exported images, and writes
<workdir>/biaodian.html, <workdir>/laojia.html and <workdir>/db_next.json."""
import os, re, sys, json, base64, subprocess
import numpy as np
from scipy.interpolate import RBFInterpolator
from PIL import Image
here = os.path.dirname(os.path.abspath(__file__)); out = os.path.dirname(here)
pts = json.load(open(sys.argv[1])); ver = sys.argv[2]; work = sys.argv[3]
html_path = os.path.join(out, 'laojia_compare.html')
P = json.load(open(os.path.join(here, 'pairs_base.json'))); A = np.array(P, float)
f = RBFInterpolator(A[:, 2:4], A[:, 0:2], kernel='thin_plate_spline', smoothing=0)
res = [p for p in P if p[4] == 1]; user = [p for p in P if p[4] == 0]
for m in pts:
    if not m.get('old') or not m.get('new'): print('skip incomplete', m['id']); continue
    o = (m['old']['x'], m['old']['y']); n = (m['new']['x'], m['new']['y'])
    hit = [p for p in user if abs(p[2] - n[0]) < 0.05 and abs(p[3] - n[1]) < 0.05] if o == n else []
    if hit: res.append(hit[0]); continue
    b = f(np.array([o]))[0]; res.append([round(float(b[0]), 3), round(float(b[1]), 3), n[0], n[1], 0])
    print('changed/new', m['id'], 'shift', round(float(np.hypot(n[0] - o[0], n[1] - o[1])), 1))
json.dump(res, open(os.path.join(here, 'pairs_base.json'), 'w')); print('pairs', len(res))
t = open(html_path).read()
t, k = re.subn(r'PRESET_PAIRS=\[\[.*?\]\], OLD_MAP0', lambda _: 'PRESET_PAIRS=' + json.dumps(res) + ', OLD_MAP0', t, count=1); assert k == 1
open(html_path, 'w').write(t); open(os.path.join(work, 'laojia.html'), 'w').write(t)
subprocess.run(['node', os.path.join(here, 'getwarp.js'), html_path, os.path.join(work, 'warp.png')], check=True)
Image.open(os.path.join(work, 'warp.png')).convert('L').save(os.path.join(work, 'mark_old.jpg'), quality=85)
new_b64 = re.search(r'NEW_SRC="(data:image/jpeg;base64,[^"]+)"', t).group(1)
m = open(os.path.join(here, 'mark_tpl.html')).read().replace('__FRAME__', 'aligned-' + ver)
m = m.replace('__OLD__', 'data:image/jpeg;base64,' + base64.b64encode(open(os.path.join(work, 'mark_old.jpg'), 'rb').read()).decode()).replace('__NEW__', new_b64)
open(os.path.join(work, 'biaodian.html'), 'w').write(m)
subprocess.run(['node', os.path.join(here, 'export.js'), html_path, out], check=True)
json.dump({'frame': 'aligned-' + ver, 'points': [{'id': q['id'], 'new': q['new'], 'old': q['new']} for q in pts if q.get('old') and q.get('new')]}, open(os.path.join(work, 'db_next.json'), 'w'))
