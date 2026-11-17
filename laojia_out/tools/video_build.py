"""Build a narrated 1974-vs-2025 vertical video from a script JSON.

usage:
  python3 video_build.py <script.json> <workdir> stills    # preview frames only
  python3 video_build.py <script.json> <workdir> render <out.mp4>

<workdir> must hold new.jpg (2025 image) and warp.png (1974 image aligned to it, RGBA).
"""
import os, sys, json, math, re, ssl, time, wave, asyncio, subprocess, urllib.parse
import numpy as np, cv2
from PIL import Image, ImageDraw, ImageFont, ImageFilter
from scipy.signal import fftconvolve

SCRIPT, WORK, MODE = sys.argv[1], sys.argv[2], sys.argv[3]
SC = json.load(open(SCRIPT))
FPS, W, H = 30, 1080, 1920
PW, PH, PY = 1080, 1350, 290
SR = 44100
os.makedirs(os.path.join(WORK, 'vo'), exist_ok=True)
os.makedirs(os.path.join(WORK, 'fonts'), exist_ok=True)
P = lambda *a: os.path.join(WORK, *a)

# ---------------------------------------------------------------- fonts
def all_text():
    s = ''
    for k in ('hook', 'swipe', 'outro'):
        s += ''.join(SC[k]['top'] + SC[k]['bottom'])
    for p in SC['places']:
        s += ''.join(p['cap'])
    return ''.join(sorted(set(s + '0123456789·，？：' + '19742025')))

def fetch_fonts():
    txt = all_text()
    for fam, wt, name in [('Noto Serif SC', 900, 'serif.ttf'), ('Noto Sans SC', 700, 'sansb.ttf')]:
        url = 'https://fonts.googleapis.com/css2?family=' + urllib.parse.quote(fam) + f':wght@{wt}&text=' + urllib.parse.quote(txt)
        css = subprocess.run(['curl', '-sS', '-m', '60', url], capture_output=True, text=True).stdout
        u = re.search(r'url\((.*?)\)', css).group(1)
        subprocess.run(['curl', '-sS', '-m', '60', '-o', P('fonts', name), u], check=True)

# ---------------------------------------------------------------- narration
def tts(text, out):
    import edge_tts, edge_tts.communicate as c
    c._SSL_CTX = ssl.create_default_context(cafile='/root/.ccr/ca-bundle.crt')
    for k in range(6):
        try:
            asyncio.run(edge_tts.Communicate(text, 'zh-CN-YunxiNeural', rate='+0%').save(out + '.mp3'))
            if os.path.getsize(out + '.mp3') > 1000: break
        except Exception as e:
            time.sleep(3 + 3 * k)
    else:
        raise SystemExit('tts failed: ' + text)
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', out + '.mp3', '-ar', str(SR), '-ac', '2', out], check=True)

def readwav(p):
    w = wave.open(p); a = np.frombuffer(w.readframes(w.getnframes()), np.int16).reshape(-1, 2).astype(np.float32) / 32768
    e = np.abs(a).max(1); idx = np.where(e > 0.01)[0]
    return a[max(0, idx[0] - 600): idx[-1] + 1800]

def voice(key, text):
    for a, b in SC.get('say', {}).items(): text = text.replace(a, b)
    f = P('vo', key + '.wav'); meta = P('vo', key + '.txt')
    if not (os.path.exists(f) and os.path.exists(meta) and open(meta).read() == text):
        tts(text, f); open(meta, 'w').write(text)
    return readwav(f)

# ---------------------------------------------------------------- timeline
FULL = (2360, 1330, 2250); FULL_IN = (2360, 1330, 2700)
clips = []          # (start_time, audio)
keys = []           # camera keyframes (t, (cx,cy,w), hop)
olds = []           # (a,b,c,d) oldness windows; 1 = show 1974
caps = []           # (layer_name, a, b, c, d)
marks = []          # (cx, cy, arrive, end) place markers
SWIPE = None

def build_timeline():
    global SWIPE, T_END
    t = 0.0
    # hook: 1974, slow push-in
    v = voice('hook', SC['hook']['vo']); dv = len(v) / SR
    d = max(4.6, 0.5 + dv + 0.7)
    clips.append((0.5, v)); keys.extend([(0.0, FULL_IN, 0), (d, FULL, 0)])
    olds.append((-1, -1, d, d)); caps.append(('hook', 0.3, 0.9, d - 0.5, d))
    t = d
    # swipe
    v = voice('swipe', SC['swipe']['vo']); dv = len(v) / SR
    d = max(5.8, 0.4 + dv + 0.9)
    clips.append((t + 0.4, v)); keys.append((t + d, FULL, 0))
    SWIPE = (t, t + 0.4, t + d - 1.2, t + d)   # swipe runs between [1] and [2]
    caps.append(('swipe', t + 0.2, t + 0.8, t + d - 0.5, t + d))
    t += d
    # places
    prev = FULL
    for i, p in enumerate(SC['places']):
        cam = (p['cx'], p['cy'], p['w'])
        dist = math.hypot(cam[0] - prev[0], cam[1] - prev[1])
        move = 1.5 + min(1.0, dist / 2000)
        hop = 0.0 if i == 0 else min(0.9, math.log(1 + dist / max(cam[2], prev[2])) * 0.7)
        keys.append((t + move, cam, hop))
        a1 = voice(f'p{i}_now', p['now']); a2 = voice(f'p{i}_then', p['then'])
        s1 = t + move + 0.15; e1 = s1 + len(a1) / SR
        s2 = e1 + 0.45; e2 = s2 + len(a2) / SR
        clips.append((s1, a1)); clips.append((s2, a2))
        end = e2 + 0.8
        keys.append((end, cam, 0))
        olds.append((s2 - 0.45, s2 + 0.35, end - 0.1, end + 0.9))
        marks.append((p['cx'], p['cy'], t + move - 0.1, end, p.get('mark_old')))
        caps.append((f'p{i}', t + move - 0.3, t + move + 0.2, end - 0.1, end + 0.4))
        prev = cam; t = end
    # outro
    v = voice('outro', SC['outro']['vo']); dv = len(v) / SR
    move = 1.8
    keys.append((t + move, FULL, 0.15))
    s = t + move + 0.2; clips.append((s, v))
    end = s + dv + 1.8
    keys.append((end, FULL, 0))
    olds.append((t + move, t + move + 1.0, end + 5, end + 6))
    caps.append(('outro', t + move - 0.2, t + move + 0.6, end + 5, end + 6))
    T_END = end

# ---------------------------------------------------------------- drawing
CREAM = (244, 236, 220); AMBER = (232, 176, 84); TEAL = (124, 206, 170)
BG = np.array([14, 16, 15], np.float32)

def font(n): return ImageFont.truetype(P('fonts', 'serif.ttf'), n)
def fontb(n): return ImageFont.truetype(P('fonts', 'sansb.ttf'), n)

def layer(fn):
    im = Image.new('RGBA', (W, H), (0, 0, 0, 0)); fn(ImageDraw.Draw(im))
    a = np.array(im); sh = np.array(Image.fromarray(a[..., 3]).filter(ImageFilter.GaussianBlur(6)), np.float32) / 255 * 0.8
    out = np.zeros((H, W, 4), np.float32); al = a[..., 3:4].astype(np.float32) / 255
    out[..., :3] = a[..., :3] * al; out[..., 3] = np.maximum(al[..., 0], sh)
    return out

def center(d, y, text, f, fill):
    if not text: return
    w = d.textlength(text, font=f)
    if w > W - 140:   # keep inside the safe width
        f = font(int(f.size * (W - 140) / w)); w = d.textlength(text, font=f)
    d.text(((W - w) / 2, y), text, font=f, fill=fill)

def top_layer(lines, big=88):
    return layer(lambda d: (center(d, 400, lines[0], font(big), AMBER if lines[0].startswith('1974') else CREAM),
                            center(d, 520, lines[1], font(46), AMBER if not lines[0].startswith('1974') else CREAM)))

def bottom_layer(lines, size=64):
    def f(d):
        if lines[1]:
            center(d, 1325, lines[0], font(size), CREAM); center(d, 1418, lines[1], font(int(size * 0.86)), AMBER)
        else:
            center(d, 1385, lines[0], font(size), CREAM)
    return layer(f)

def badge(text, col, x):
    def f(d):
        fb = fontb(46); w = d.textlength(text, font=fb)
        d.rounded_rectangle([x, PY + 26, x + w + 36, PY + 92], radius=12, fill=(10, 12, 11, 190))
        d.text((x + 18, PY + 30), text, font=fb, fill=col)
    return layer(f)

def ease(t): return 0.5 - 0.5 * math.cos(math.pi * min(1, max(0, t)))
def ramp(t, a, b): return ease((t - a) / (b - a)) if b > a else float(t >= a)
def win(t, a, b, c, d): return ramp(t, a, b) * (1 - ramp(t, c, d))

def cam(t):
    for (t0, a, _), (t1, b, hop) in zip(keys, keys[1:]):
        if t0 <= t <= t1:
            k = ease((t - t0) / (t1 - t0)) if t1 > t0 else 1
            lw = math.log(a[2]) + (math.log(b[2]) - math.log(a[2])) * k + hop * math.sin(math.pi * k)
            return a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, math.exp(lw)
    return keys[-1][1]

def oldness(t):
    if SWIPE and SWIPE[0] <= t < SWIPE[3]: return None
    return max([win(t, *w) if w[0] >= 0 else (1 - ramp(t, w[2], w[3])) for w in olds] + [0])

def view(img, cx, cy, w):
    w = min(w, 4096 * 0.98); s = PW / w
    M = np.float32([[s, 0, PW / 2 - cx * s], [0, s, PH / 2 - cy * s]])
    return cv2.warpAffine(img, M, (PW, PH), flags=cv2.INTER_AREA if s < 1 else cv2.INTER_CUBIC,
                          borderMode=cv2.BORDER_CONSTANT, borderValue=tuple(map(float, BG)))

def comp(c, L, a):
    if a <= 0.001: return
    al = L[..., 3:4] * a; c[:] = c * (1 - al) + L[..., :3] * al

def setup_images():
    global NEW, OLD
    NEW = cv2.cvtColor(cv2.imread(P('new.jpg')), cv2.COLOR_BGR2RGB).astype(np.float32)
    wo = cv2.imread(P('warp.png'), cv2.IMREAD_UNCHANGED)
    g = wo[..., 0].astype(np.float32); a = wo[..., 3].astype(np.float32) / 255
    og = np.clip((g - 18) * 1.08, 0, 255)
    OLD = np.clip(np.stack([og * 1.02, og, og * 0.95], -1), 0, 255)
    OLD = OLD * a[..., None] + BG * (1 - a[..., None])

def setup_layers():
    global L
    L = {'hook': (top_layer(SC['hook']['top'], 96), bottom_layer(SC['hook']['bottom'])),
         'swipe': (top_layer(SC['swipe']['top']), bottom_layer(SC['swipe']['bottom'])),
         'outro': (top_layer(SC['outro']['top']), bottom_layer(SC['outro']['bottom']))}
    for i, p in enumerate(SC['places']):
        L[f'p{i}'] = (None, bottom_layer(p['cap']))
    L['b74'] = badge('1974', AMBER, 110); L['b25'] = badge('2025', TEAL, 110); L['b74r'] = badge('1974', AMBER, W - 260)

def frame(t):
    cx, cy, w = cam(t)
    c = np.zeros((H, W, 3), np.float32) + BG
    vn = view(NEW, cx, cy, w)
    a = oldness(t)
    if a is None:
        x = int(round(ramp(t, SWIPE[1], SWIPE[2]) * PW))
        panel = view(OLD, cx, cy, w); panel[:, :x] = vn[:, :x]
        if 0 < x < PW: panel[:, max(0, x - 2):x + 2] = (240, 236, 226)
    elif a <= 0.001: panel = vn
    else: panel = vn * (1 - a) + view(OLD, cx, cy, w) * a
    c[PY:PY + PH] = panel
    # ring marking the place being talked about
    for mx, my, ta, te, mo in marks:
        k = win(t, ta, ta + 0.6, te - 0.5, te)
        if k <= 0.001: continue
        s = PW / min(w, 4096 * 0.98)
        if mo and a: mx, my = mx + (mo[0] - mx) * a, my + (mo[1] - my) * a
        x = PW / 2 + (mx - cx) * s; y = PY + PH / 2 + (my - cy) * s
        r = 58 + 70 * (1 - ramp(t, ta, ta + 0.7))
        tmp = c.copy()
        cv2.circle(tmp, (int(x * 4), int(y * 4)), int(r * 4), (20, 18, 14), 11, cv2.LINE_AA, 2)
        cv2.circle(tmp, (int(x * 4), int(y * 4)), int(r * 4), (84, 176, 232)[::-1], 5, cv2.LINE_AA, 2)
        c[:] = c * (1 - k * 0.95) + tmp * (k * 0.95)
    for i in range(40):
        k = (1 - i / 40) * 0.55
        c[PY + i] = c[PY + i] * (1 - k) + BG * k; c[PY + PH - 1 - i] = c[PY + PH - 1 - i] * (1 - k) + BG * k
    # scrims behind on-image text
    ts = max([win(t, *x[1:]) for x in caps if L[x[0]][0] is not None] + [0])
    bs = max([win(t, *x[1:]) for x in caps] + [0])
    if ts > 0.001:
        g = (np.clip(1 - np.arange(420) / 420, 0, 1) ** 1.3 * 0.82 * ts)[:, None, None]
        c[PY:PY + 420] = c[PY:PY + 420] * (1 - g) + BG * g
    if bs > 0.001:
        g = (np.clip(np.arange(520) / 520, 0, 1) ** 1.2 * 0.88 * bs)[:, None, None]
        c[PY + PH - 520:PY + PH] = c[PY + PH - 520:PY + PH] * (1 - g) + BG * g
    if a is None:
        comp(c, L['b25'], ramp(t, SWIPE[1], SWIPE[1] + 0.6)); comp(c, L['b74r'], 1)
    else:
        comp(c, L['b74'], a * ramp(t, 0.4, 1.0)); comp(c, L['b25'], 1 - a)
    for name, a1, b1, c1, d1 in caps:
        k = win(t, a1, b1, c1, d1)
        if L[name][0] is not None: comp(c, L[name][0], k)
        comp(c, L[name][1], k)
    k = ramp(t, 0, 0.5) * (1 - ramp(t, T_END - 0.7, T_END))
    return np.clip(c * k, 0, 255).astype(np.uint8)

# ---------------------------------------------------------------- music + mix
def music_and_mix():
    n = int((T_END + 1) * SR); mus = np.zeros(n, np.float32)
    nf = lambda m: 440 * 2 ** ((m - 69) / 12)
    def piano(m, dur, vel):
        f = nf(m); tt = np.arange(int(dur * SR)) / SR; y = np.zeros_like(tt)
        for k in range(1, 9):
            fk = f * k * (1 + 0.0004 * k * k)
            if fk > 12000: break
            y += (vel / k ** 1.6) * np.sin(2 * np.pi * fk * tt) * np.exp(-tt * (0.9 + 0.55 * k))
        return (y * np.minimum(1, tt / 0.006)).astype(np.float32)
    def pad(ms, dur, vel):
        tt = np.arange(int(dur * SR)) / SR; y = np.zeros_like(tt)
        for m in ms: f = nf(m); y += np.sin(2 * np.pi * f * tt) + 0.3 * np.sin(2 * np.pi * 2 * f * tt + 0.5)
        return (vel * y * np.minimum(1, tt / 1.2) * np.minimum(1, (dur - tt) / 1.2) / len(ms)).astype(np.float32)
    def add(y, at):
        i = max(0, int(at * SR))
        if i >= n: return
        j = min(n, i + len(y)); mus[i:j] += y[: j - i]
    beat = 60 / 64; bar = 4 * beat
    prog = [(57, [57, 60, 64]), (53, [53, 57, 60]), (48, [55, 60, 64]), (55, [55, 59, 62]),
            (57, [57, 60, 64]), (53, [53, 57, 60]), (50, [53, 57, 62]), (52, [52, 56, 59])]
    melody = [76, 74, 72, 71, 72, 69, 71, 67, 76, 77, 76, 74, 72, 71, 69, 68]
    t0, i = 0.3, 0
    while t0 < T_END:
        root, ch = prog[i % 8]
        add(piano(root - 12, bar + 1.5, 0.55), t0)
        add(pad([x - 12 for x in ch] + ch, bar + 1.2, 0.05), t0 - 0.3)
        arp = [ch[0], ch[1], ch[2], ch[1] + 12, ch[2], ch[1], ch[0] + 12, ch[2]]
        for k, m in enumerate(arp): add(piano(m, 2.2, 0.20 if k % 2 else 0.26), t0 + k * beat / 2)
        if i >= 2:
            j = (2 * (i - 2)) % 16
            for k, m in enumerate(melody[j:j + 2]): add(piano(m, 2.6, 0.24), t0 + k * 2 * beat + 0.02)
        t0 += bar; i += 1
    irt = np.arange(int(2.4 * SR)) / SR
    ir = (np.random.default_rng(7).standard_normal(len(irt)) * np.exp(-irt * 2.6)).astype(np.float32); ir[0] = 0
    wet = fftconvolve(mus, ir)[:n] * 0.02
    m = np.stack([mus * 0.8 + wet, np.roll(mus, 220) * 0.8 + np.roll(wet, 530)], 1); m /= np.abs(m).max() + 1e-9
    tt = np.arange(n) / SR
    env = np.minimum(1, tt / 1.5) * np.clip((T_END - tt) / 2.5, 0, 1)
    duck = np.ones(n, np.float32)
    for s, v in clips:
        a, b = s - 0.25, s + len(v) / SR + 0.3
        duck = np.minimum(duck, np.clip(1 - np.minimum((tt - a) / 0.3, (b - tt) / 0.4), 0.42, 1).astype(np.float32))
    m = m * (env * duck * 0.42)[:, None]
    vo = np.zeros((n, 2), np.float32)
    for s, v in clips:
        i0 = int(s * SR); vo[i0:i0 + len(v)] += v[: n - i0]
    mix = (m + vo)[: int(T_END * SR)]; mix /= max(1.0, np.abs(mix).max() / 0.95)
    w = wave.open(P('mix.wav'), 'wb'); w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR)
    w.writeframes((mix * 32767).astype(np.int16).tobytes()); w.close()

# ---------------------------------------------------------------- main
fetch_fonts(); build_timeline(); setup_images(); setup_layers()
print('length', round(T_END, 1), 's')
if MODE == 'stills':
    ts = [float(x) for x in sys.argv[4].split(',')] if len(sys.argv) > 4 else None
    if ts is None:
        ts = [1.5, SWIPE[0] + 3]
        for name, a, b, c, d in caps:
            if name.startswith('p'): ts += [b + 0.5, c - 0.8]
        ts.append(T_END - 1.5)
    ims = [Image.fromarray(frame(t)).resize((270, 480)) for t in ts]
    sheet = Image.new('RGB', (280 * min(6, len(ims)), 490 * math.ceil(len(ims) / 6)), (40, 40, 40))
    for i, im in enumerate(ims): sheet.paste(im, ((i % 6) * 280, (i // 6) * 490))
    sheet.save(P('stills.png')); print('stills at', [round(t, 1) for t in ts])
else:
    out = sys.argv[4]
    music_and_mix()
    p = subprocess.Popen(['ffmpeg', '-y', '-loglevel', 'error', '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-s', f'{W}x{H}', '-r', str(FPS), '-i', '-',
                          '-i', P('mix.wav'), '-af', 'loudnorm=I=-14:TP=-1.5:LRA=11', '-ar', str(SR),
                          '-c:v', 'libx264', '-preset', 'medium', '-crf', '18', '-pix_fmt', 'yuv420p', '-profile:v', 'high',
                          '-c:a', 'aac', '-b:a', '192k', '-shortest', '-movflags', '+faststart', out], stdin=subprocess.PIPE)
    for i in range(int(T_END * FPS)): p.stdin.write(frame(i / FPS).tobytes())
    p.stdin.close(); p.wait(); print('done')
