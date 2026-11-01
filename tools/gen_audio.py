#!/usr/bin/env python3
"""
Original chiptune music + SFX generator for 别信这关 / Trust Nothing.
Everything is synthesised from code (no samples) -> free for commercial use.
Output: TrustNothing/assets/resources/audio/{music,sfx}/*.ogg (and .wav masters are not kept).
Usage: python3 tools/gen_audio.py
"""
import numpy as np, os, subprocess, wave, random, tempfile

SR = 44100
ROOT = os.path.join(os.path.dirname(__file__), '..', 'TrustNothing', 'assets', 'resources', 'audio')

# ------------------------------------------------------------------ oscillators
def midi_hz(m): return 440.0 * 2 ** ((m - 69) / 12)

def pulse(freq, n, duty=0.5, phase0=0.0):
    t = (np.arange(n) * freq / SR + phase0) % 1.0
    return np.where(t < duty, 1.0, -1.0)

def tri(freq, n):
    t = (np.arange(n) * freq / SR) % 1.0
    return 4 * np.abs(t - 0.5) - 1

def sine(freq, n):
    return np.sin(2 * np.pi * freq * np.arange(n) / SR)

_noise_state = np.random.default_rng(1234)
def noise(n):
    return _noise_state.uniform(-1, 1, n)

def env(n, a=0.005, d=0.08, s=0.6, r=0.03):
    e = np.ones(n) * s
    na, nd, nr = int(a * SR), int(d * SR), int(r * SR)
    na = min(na, n); e[:na] = np.linspace(0, 1, na, endpoint=False) if na else e[:na]
    nd2 = min(nd, max(0, n - na)); e[na:na + nd2] = np.linspace(1, s, nd2, endpoint=False) if nd2 else e[na:na + nd2]
    nr = min(nr, n); e[n - nr:] *= np.linspace(1, 0, nr) if nr else 1
    return e

def lowpass(x, alpha):
    # one-pole low-pass (alpha 0..1, smaller = darker) – softens square waves
    y = np.empty_like(x); acc = 0.0
    for i in range(len(x)):
        acc += alpha * (x[i] - acc); y[i] = acc
    return y

def lowpass_fast(x, alpha):
    from numpy import frompyfunc  # noqa (kept simple; use scipy-free IIR via cumulative trick)
    # vectorised approximation: repeated moving average
    k = max(1, int(1 / alpha))
    ker = np.ones(k) / k
    return np.convolve(x, ker, mode='same')

# ------------------------------------------------------------------ music
SCALES = {'major': [0, 2, 4, 5, 7, 9, 11], 'minor': [0, 2, 3, 5, 7, 8, 10], 'dorian': [0, 2, 3, 5, 7, 9, 10]}

def chord_notes(root, quality):
    return [root, root + (3 if quality == 'm' else 4), root + 7]

class Track:
    def __init__(self, name, bpm, key, scale, prog, style, seed, bars_mult=2):
        self.name, self.bpm, self.key, self.scale, self.prog, self.style, self.seed = name, bpm, key, scale, prog, style, seed
        self.bars_mult = bars_mult

def parse_chord(c, key):
    names = {'C': 0, 'C#': 1, 'Db': 1, 'D': 2, 'Eb': 3, 'E': 4, 'F': 5, 'F#': 6, 'Gb': 6, 'G': 7, 'Ab': 8, 'A': 9, 'Bb': 10, 'B': 11}
    q = 'm' if c.endswith('m') else ''
    base = c[:-1] if q else c
    return names[base], q

def gen_melody(rng, tr, bars):
    """Phrase-based melody on 8th-note grid. Returns list of (start_step, len_steps, midi) per 8th."""
    steps_per_bar = 8
    scale = SCALES[tr.scale]
    key = tr.key
    notes = []
    def degree_to_midi(d):
        o, i = divmod(d, 7)
        return key + 60 + 12 * o + scale[i]
    def chord_degrees(ch_root):
        rel = (ch_root - key) % 12
        best = min(range(7), key=lambda i: (scale[i] - rel) % 12)
        return [best, best + 2, best + 4]
    def make_motif(bar_chords):
        m = []
        d = rng.choice(chord_degrees(bar_chords[0]))
        for b, ch in enumerate(bar_chords):
            cd = chord_degrees(ch)
            s = 0
            while s < steps_per_bar:
                strong = s % 4 == 0
                if strong:
                    d = min(cd + [c + 7 for c in cd] + [c - 7 for c in cd], key=lambda c: abs(c - d) + rng.random() * 2)
                else:
                    d += rng.choice([-1, 1, 1, 2, -2, 0])
                d = max(-2, min(9, d))
                ln = rng.choice([1, 1, 2, 2, 3]) if not strong else rng.choice([1, 2, 2, 3])
                ln = min(ln, steps_per_bar - s)
                if rng.random() < 0.12 and not strong:
                    m.append((b * steps_per_bar + s, ln, None))
                else:
                    m.append((b * steps_per_bar + s, ln, d))
                s += ln
        return m
    chords = [parse_chord(c, key)[0] for c in tr.prog]
    phrase_len = 2
    motifs = {}
    out = []
    for bar in range(0, bars, phrase_len):
        seg = [chords[(bar + i) % len(chords)] for i in range(phrase_len)]
        # A A' B A form per 8 bars
        form = (bar // phrase_len) % 4
        keyf = 'A' if form in (0, 1, 3) else 'B'
        if keyf not in motifs: motifs[keyf] = make_motif(seg)
        mot = motifs[keyf]
        for (s, ln, d) in mot:
            if d is None: continue
            dd = d
            if form == 1 and s >= 8: dd = d + rng.choice([0, 1, -1])  # variation
            out.append((bar * steps_per_bar + s, ln, degree_to_midi(dd)))
    return out

def render_track(tr):
    rng = random.Random(tr.seed)
    bars = len(tr.prog) * tr.bars_mult
    beat = 60.0 / tr.bpm
    step8 = beat / 2
    step16 = beat / 4
    total = int(round(bars * 4 * beat * SR))
    tail = int(0.5 * SR)
    mix = np.zeros(total + tail)

    def add(sig, start_s, vol):
        i = int(round(start_s * SR))
        n = len(sig)
        mix[i:i + n] += sig[:max(0, len(mix) - i)] * vol

    style = tr.style
    # ---- melody (pulse 25%) ----
    mel = gen_melody(rng, tr, bars)
    lead_duty = 0.25 if style != 'victory' else 0.5
    for (s, ln, m) in mel:
        dur = ln * step8 * 0.92
        n = int(dur * SR)
        f = midi_hz(m + 12 if style in ('hard', 'final') else m + 12)
        vib = 1 + 0.004 * np.sin(2 * np.pi * 5.5 * np.arange(n) / SR) * (np.arange(n) > SR * 0.12)
        t = np.cumsum(f * vib / SR) % 1.0
        sig = np.where(t < lead_duty, 1.0, -1.0)
        sig = lowpass_fast(sig, 0.25)
        add(sig * env(n, 0.004, 0.09, 0.55, 0.04), s * step8, 0.16)
        # echo
        add(sig * env(n, 0.004, 0.09, 0.55, 0.04), s * step8 + step8 * 1.5, 0.05)
    # ---- harmony arpeggio (pulse 12.5%) ----
    for bar in range(bars):
        root, q = parse_chord(tr.prog[bar % len(tr.prog)], tr.key)
        cn = chord_notes(root + 60, q)
        pattern = [0, 1, 2, 1] if style in ('menu', 'level_a', 'victory') else [0, 2, 1, 2]
        for k in range(16):
            if style == 'menu' and k % 2: continue
            m = cn[pattern[k % 4]] + (12 if k % 8 >= 4 else 0)
            n = int(step16 * 0.8 * SR)
            sig = lowpass_fast(pulse(midi_hz(m), n, 0.125), 0.3)
            add(sig * env(n, 0.002, 0.05, 0.3, 0.02), bar * 4 * beat + k * step16, 0.045)
    # ---- bass (triangle) ----
    for bar in range(bars):
        root, q = parse_chord(tr.prog[bar % len(tr.prog)], tr.key)
        r = root + 36 if root + 36 >= 33 else root + 48
        if style in ('hard', 'final'):
            pat = [(0, r), (1, r), (2, r + 12), (3, r), (4, r), (5, r + 7), (6, r + 12), (7, r)]
        elif style == 'menu':
            pat = [(0, r), (3, r + 7), (4, r + 12), (6, r + 7)]
        else:
            pat = [(0, r), (2, r + 7), (4, r + 12), (6, r + 7)]
        for (s8, m) in pat:
            n = int(step8 * 0.85 * SR)
            add(tri(midi_hz(m), n) * env(n, 0.003, 0.05, 0.75, 0.02), bar * 4 * beat + s8 * step8, 0.32)
    # ---- drums ----
    def kick():
        n = int(0.14 * SR); t = np.arange(n) / SR
        f = 120 * np.exp(-t * 25) + 45
        return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 18)
    def snare():
        n = int(0.14 * SR); t = np.arange(n) / SR
        return (noise(n) * 0.8 + sine(190, n) * 0.4) * np.exp(-t * 26)
    def hat(open_=False):
        n = int((0.09 if open_ else 0.035) * SR); t = np.arange(n) / SR
        x = noise(n); x = x - lowpass_fast(x, 0.5)
        return x * np.exp(-t * (30 if open_ else 90))
    K, S, Hh = kick(), snare(), hat()
    for bar in range(bars):
        b0 = bar * 4 * beat
        if style == 'menu':
            kicks, snares = [0, 2.5], [1, 3]
        elif style in ('hard', 'final'):
            kicks, snares = [0, 0.75, 1.5, 2, 2.75, 3.5], [1, 3]
        else:
            kicks, snares = [0, 1.5, 2], [1, 3]
        if bar % 4 == 3 and style != 'menu':
            snares = snares + [3.5, 3.75]
        for k in kicks: add(K, b0 + k * beat, 0.42)
        for s in snares: add(S, b0 + s * beat, 0.2)
        hstep = 0.5 if style in ('menu', 'victory') else 0.25
        h = 0.0
        while h < 4:
            add(Hh, b0 + h * beat, 0.05 if (h * 4) % 2 else 0.08)
            h += hstep
    # wrap tail for seamless loop
    out = mix[:total].copy()
    out[:tail] += mix[total:total + tail]
    peak = np.max(np.abs(out)) or 1
    out = out / peak * 0.85
    return out

def jingle(notes, bpm=150, duty=0.5, bass=True):
    beat = 60 / bpm
    total = int((sum(d for _, d in notes) * beat + 0.8) * SR)
    mix = np.zeros(total)
    t = 0.0
    for m, d in notes:
        if m is not None:
            n = int(d * beat * SR * 0.95)
            sig = lowpass_fast(pulse(midi_hz(m), n, duty), 0.3) * env(n, 0.003, 0.1, 0.6, 0.05)
            i = int(t * SR); mix[i:i + n] += sig * 0.3
            sig2 = lowpass_fast(pulse(midi_hz(m - 12 + 7), n, 0.25), 0.3) * env(n, 0.003, 0.1, 0.5, 0.05)
            mix[i:i + n] += sig2 * 0.1
            if bass:
                b = tri(midi_hz(m - 24), n) * env(n, 0.003, 0.1, 0.7, 0.05); mix[i:i + n] += b * 0.3
        t += d * beat
    return mix / (np.max(np.abs(mix)) or 1) * 0.85

TRACKS = [
    Track('menu', 116, 0, 'major', ['C', 'Am', 'F', 'G', 'C', 'Am', 'Dm', 'G'], 'menu', 11),
    Track('level_a', 138, 7, 'major', ['G', 'D', 'Em', 'C', 'G', 'D', 'C', 'D'], 'level_a', 22),
    Track('level_b', 148, 2, 'dorian', ['Dm', 'C', 'Bb', 'C', 'Dm', 'F', 'G', 'Am'], 'level_b', 33),
    Track('hard', 164, 9, 'minor', ['Am', 'F', 'G', 'E', 'Am', 'F', 'Dm', 'E'], 'hard', 44),
    Track('final', 172, 4, 'minor', ['Em', 'C', 'D', 'B', 'Em', 'Am', 'C', 'B'], 'final', 55),
    Track('victory', 128, 0, 'major', ['C', 'G', 'Am', 'F', 'C', 'G', 'F', 'G'], 'victory', 66),
]

# ------------------------------------------------------------------ sfx
def sweep(f0, f1, dur, shape='square', duty=0.5, curve='exp'):
    n = int(dur * SR); t = np.arange(n) / n
    f = f0 * (f1 / f0) ** t if curve == 'exp' else f0 + (f1 - f0) * t
    ph = np.cumsum(f / SR) % 1.0
    if shape == 'square': x = np.where(ph < duty, 1.0, -1.0)
    elif shape == 'tri': x = 4 * np.abs(ph - 0.5) - 1
    else: x = np.sin(2 * np.pi * ph)
    return x

def dec(n, k): return np.exp(-np.arange(n) / SR * k)

def cat(*xs): return np.concatenate(xs)

def notes_seq(ms, d, duty=0.5, vol=1.0):
    parts = []
    for m in ms:
        n = int(d * SR)
        parts.append(lowpass_fast(pulse(midi_hz(m), n, duty), 0.35) * env(n, 0.002, 0.04, 0.6, 0.02) * vol)
    return cat(*parts)

def make_sfx():
    S = {}
    n = lambda d: int(d * SR)
    x = sweep(280, 760, 0.12, 'square', 0.25); S['jump'] = lowpass_fast(x, 0.35) * env(len(x), 0.002, 0.05, 0.5, 0.03) * 0.5
    x = noise(n(0.07)); S['land'] = lowpass_fast(x, 0.08) * dec(len(x), 60) * 0.7
    a = sweep(700, 70, 0.55, 'square', 0.5); vib = 1 + 0.3 * np.sin(np.arange(len(a)) / SR * 2 * np.pi * 18)
    S['death'] = lowpass_fast(a * vib, 0.3) * env(len(a), 0.002, 0.2, 0.5, 0.1) * 0.5 + np.pad(lowpass_fast(noise(n(0.2)), 0.2) * dec(n(0.2), 15) * 0.5, (0, len(a) - n(0.2)))
    x = noise(n(0.16)); hi = x - lowpass_fast(x, 0.3)
    S['spike'] = hi * dec(len(x), 22) * 0.6 + sine(1760, len(x)) * dec(len(x), 30) * 0.35
    S['retract'] = S['spike'][::-1] * 0.6
    x = noise(n(0.4)); S['crumble'] = lowpass_fast(x, 0.05) * env(len(x), 0.01, 0.1, 0.6, 0.2) * (0.6 + 0.4 * np.sign(np.sin(np.arange(len(x)) / SR * 2 * np.pi * 22))) * 0.8
    t = np.arange(n(0.3)) / SR; S['wall'] = (np.sin(2 * np.pi * np.cumsum(90 * np.exp(-t * 6) + 30) / SR) * dec(len(t), 9) + lowpass_fast(noise(len(t)), 0.05) * dec(len(t), 12)) * 0.6
    S['thud'] = S['wall'] * 0.9
    S['trap'] = cat(notes_seq([84, 79], 0.07, 0.5, 0.6))
    a = sweep(300, 1200, 0.4, 'sine'); S['portal'] = a * (0.6 + 0.4 * np.sin(np.arange(len(a)) / SR * 2 * np.pi * 30)) * env(len(a), 0.01, 0.1, 0.7, 0.1) * 0.5
    x = pulse(1250, n(0.035), 0.5); S['click'] = lowpass_fast(x, 0.4) * dec(len(x), 70) * 0.35
    # sad trombone
    parts = []
    for i, m in enumerate([67, 66, 65, 64]):
        d = 0.18 if i < 3 else 0.5
        k = int(d * SR); ph = np.cumsum(midi_hz(m) * (1 + (0.02 * np.sin(np.arange(k) / SR * 2 * np.pi * 6) if i == 3 else np.zeros(k))) / SR) % 1
        parts.append(lowpass_fast(np.where(ph < 0.4, 1.0, -1.0), 0.15) * env(k, 0.01, 0.05, 0.7, 0.05))
    S['fake'] = cat(*parts) * 0.45
    S['fakewin'] = jingle([(72, 0.5), (76, 0.5), (79, 0.5), (84, 1.0)], 220) * 0.6
    S['win'] = jingle([(72, 0.33), (76, 0.33), (79, 0.33), (84, 0.66), (79, 0.33), (84, 1.2)], 200) * 0.7
    S['coin'] = notes_seq([95, 100], 0.07, 0.5, 0.4)
    k = n(0.32); t = np.arange(k) / SR; f = 220 + 160 * np.sin(t * 2 * np.pi * 9) * np.exp(-t * 6) + 300 * t
    S['spring'] = np.sin(2 * np.pi * np.cumsum(f) / SR) * env(k, 0.003, 0.1, 0.6, 0.08) * 0.55
    S['checkpoint'] = notes_seq([72, 76, 79, 84], 0.07, 0.5, 0.4)
    x = pulse(150, n(0.05), 0.5); S['bump'] = lowpass_fast(x, 0.2) * dec(len(x), 50) * 0.35
    x = noise(n(0.25)); S['flee'] = lowpass_fast(x, 0.15) * np.linspace(0.2, 1, len(x)) * env(len(x), 0.01, 0.05, 0.8, 0.05) * 0.5 + sweep(400, 1400, 0.25, 'tri') * 0.15
    a = sweep(200, 900, 0.3, 'tri'); S['warp'] = a * (0.5 + 0.5 * np.sin(np.arange(len(a)) / SR * 2 * np.pi * 40)) * env(len(a), 0.01, 0.1, 0.6, 0.08) * 0.45
    x = pulse(880, n(0.03), 0.25); S['toggle'] = lowpass_fast(x, 0.4) * dec(len(x), 90) * 0.3
    S['respawn'] = notes_seq([67, 74], 0.05, 0.25, 0.3)
    S['achievement'] = notes_seq([79, 84, 88, 91], 0.09, 0.25, 0.4)
    k = n(0.35); S['shield'] = (sine(1568, k) + sine(2349, k) * 0.5) * dec(k, 9) * 0.4
    return S

def write_ogg(path, x, q=4):
    x = np.clip(x, -1, 1)
    pcm = (x * 32767).astype(np.int16)
    with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as tf:
        tmp = tf.name
    with wave.open(tmp, 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm.tobytes())
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', tmp, '-c:a', 'libvorbis', '-q:a', str(q), path], check=True)
    os.remove(tmp)

def main():
    os.makedirs(os.path.join(ROOT, 'music'), exist_ok=True)
    os.makedirs(os.path.join(ROOT, 'sfx'), exist_ok=True)
    for tr in TRACKS:
        x = render_track(tr)
        write_ogg(os.path.join(ROOT, 'music', tr.name + '.ogg'), x, 3)
        print('music', tr.name, f'{len(x) / SR:.1f}s')
    vj = jingle([(67, 0.5), (72, 0.5), (76, 0.5), (79, 0.75), (76, 0.25), (79, 1.5)], 160)
    write_ogg(os.path.join(ROOT, 'music', 'victory_short.ogg'), vj, 3)
    for name, x in make_sfx().items():
        write_ogg(os.path.join(ROOT, 'sfx', name + '.ogg'), x, 4)
    print('sfx done')

if __name__ == '__main__':
    main()
