"""原点报价助手 · 竖屏使用教程视频合成（1080x1920 / 30fps）。

输入：Robolectric 截图目录（frames.json + fNN.png + pdf_page.png）
输出：MP4 视频、封面图、配音稿
用法：python3 make_video.py <截图目录> <字体目录> <输出目录>
"""
import json
import math
import subprocess
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

SHOTS, FONTS, OUT = (Path(p) for p in sys.argv[1:4])
TTS_PY, TTS_MODEL = sys.argv[4], sys.argv[5]  # Piper 所在的 python 与中文语音模型
WORK = OUT / "_audio"
WORK.mkdir(parents=True, exist_ok=True)
SR = 44100

# 每个画面的旁白（显示在配音稿里的文字）
NARRATION = {
    "intro": "原点报价助手，专为五金机电企业打造，几分钟做好一张专业报价单。",
    "f00": "首页工作台，客户、商品、报价一目了然。",
    "f01": "第一步，添加客户。点击底部“客户”。",
    "f02": "再点右下角的“添加客户”。",
    "f03": "填写公司名称、联系人和电话，然后保存。",
    "f04": "客户添加成功，支持快速搜索。",
    "f05": "第二步，进入商品页，点击“添加商品”。",
    "f06": "录入名称、型号、规格和单价，保存。",
    "f07": "商品库建好，报价时直接选用。",
    "f08": "第三步，回到工作台，点击“新建报价单”。",
    "f09": "报价编号自动生成。点这里选择客户。",
    "f10": "直接点选刚才添加的客户。",
    "f11": "第四步，从商品库添加商品。",
    "f12": "可以一次勾选多个商品，点击添加。",
    "f13": "第五步，数量和单价随意修改，金额自动计算。",
    "f14": "第六步，点击“保存报价”。",
    "f15": "报价单已生成，PDF 同步做好。",
    "pdf": "这是生成的 A4 报价单，排版清晰，金额自动转大写。",
    "f16": "第七步，可以把 PDF 保存到手机。",
    "f17": "第八步，点击“分享”，选择微信，直接发给客户。",
    "outro": "客户管理、商品库、一键报价、PDF 导出，全部离线可用。原点软件出品。",
}


def speech_text(s):
    """送给语音合成的文本：去掉引号，字母数字之间加空格以便正确朗读"""
    return s.replace("“", "").replace("”", "").replace("A4", "A 4").replace("PDF", "P D F")
OUT.mkdir(parents=True, exist_ok=True)

W, H, FPS = 1080, 1920, 30
XFADE = 0.3
GREEN = (31, 157, 107)
GREEN_DARK = (22, 122, 82)
GREEN_SOFT = (232, 246, 239)
TEXT = (31, 35, 41)
GRAY = (100, 106, 115)
LIGHT = (143, 149, 158)


def font(size, bold=False):
    return ImageFont.truetype(str(FONTS / ("NotoSansSC-Bold.otf" if bold else "NotoSansSC-Regular.otf")), size)


def text_center(d, y, s, f, fill):
    w = d.textlength(s, font=f)
    d.text(((W - w) / 2, y), s, font=f, fill=fill)


def background():
    bg = Image.new("RGB", (W, H), "white")
    d = ImageDraw.Draw(bg)
    for y in range(H):
        k = y / H
        d.line([(0, y), (W, y)], fill=tuple(int(255 + (g - 255) * k) for g in GREEN_SOFT))
    return bg


BG = background()


def pill(d, x, y, s, f, fg="white", bg=GREEN):
    tw = d.textlength(s, font=f)
    h = f.size + 22
    d.rounded_rectangle([x, y, x + tw + 44, y + h], radius=h // 2, fill=bg)
    d.text((x + 22, y + 8), s, font=f, fill=fg)
    return tw + 44


def progress(img, frac):
    d = ImageDraw.Draw(img)
    d.rectangle([0, H - 8, W, H], fill=(214, 236, 225))
    d.rectangle([0, H - 8, int(W * frac), H], fill=GREEN)


# ---------------- 手机外框 ----------------
SCALE = 0.84
SCR_W, SCR_H = int(720 * SCALE), int(1560 * SCALE)
BEZEL, STATUS = 16, 40
PH_W, PH_H = SCR_W + BEZEL * 2, SCR_H + STATUS + BEZEL * 2
PH_X, PH_Y = (W - PH_W) // 2, H - PH_H - 62
SCR_X, SCR_Y = PH_X + BEZEL, PH_Y + BEZEL + STATUS


def phone_shadow():
    sh = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(sh).rounded_rectangle([PH_X + 10, PH_Y + 30, PH_X + PH_W - 10, PH_Y + PH_H + 10], 60, fill=(20, 60, 40, 70))
    return sh.filter(ImageFilter.GaussianBlur(28))


SHADOW = phone_shadow()


def phone_layer(shot_path):
    """返回 RGBA 图层：带外框、状态栏的手机界面"""
    layer = SHADOW.copy()
    d = ImageDraw.Draw(layer)
    d.rounded_rectangle([PH_X, PH_Y, PH_X + PH_W, PH_Y + PH_H], radius=58, fill=(28, 31, 36))
    screen = Image.new("RGBA", (SCR_W, SCR_H + STATUS), "white")
    sd = ImageDraw.Draw(screen)
    sd.text((30, 8), "9:41", font=font(22, True), fill=TEXT)
    bx = SCR_W - 66  # 电池
    sd.rounded_rectangle([bx, 13, bx + 34, 29], 4, outline=TEXT, width=2)
    sd.rectangle([bx + 4, 17, bx + 26, 25], fill=TEXT)
    sd.rectangle([bx + 36, 18, bx + 39, 24], fill=TEXT)
    for i in range(4):  # 信号
        h = 5 + i * 4
        sd.rectangle([bx - 46 + i * 7, 29 - h, bx - 42 + i * 7, 29], fill=TEXT)
    shot = Image.open(shot_path).convert("RGB").resize((SCR_W, SCR_H), Image.LANCZOS)
    screen.paste(shot, (0, STATUS))
    mask = Image.new("L", screen.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, SCR_W - 1, SCR_H + STATUS - 1], radius=44, fill=255)
    layer.paste(screen, (SCR_X, PH_Y + BEZEL), mask)
    return layer


def header(img, pill_text, heading, sub):
    d = ImageDraw.Draw(img)
    text_center(d, 52, "原点报价助手 · 使用教程", font(26), LIGHT)
    fp = font(34, True)
    pw = d.textlength(pill_text, font=fp) + 44
    pill(d, (W - pw) / 2, 112, pill_text, fp)
    fh = font(66, True)
    text_center(d, 186, heading, fh, TEXT)
    fs = font(38)
    if d.textlength(sub, font=fs) > W - 100:
        fs = font(34)
    text_center(d, 290, sub, fs, GRAY)


# ---------------- 场景 ----------------
class Scene:
    def __init__(self, dur, render, key):
        self.dur, self.render, self.key = dur, render, key
        self.voice = NARRATION.get(key, "")


def ease(x):
    x = max(0.0, min(1.0, x))
    return 1 - (1 - x) ** 3


def intro_scene():
    def render(t):
        img = BG.copy()
        a = ease(t / 0.8)
        dy = int((1 - a) * 40)
        layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        d = ImageDraw.Draw(layer)
        cx, cy = W // 2, 650 + dy
        d.rounded_rectangle([cx - 130, cy - 130, cx + 130, cy + 130], radius=60, fill=GREEN)
        d.ellipse([cx - 64, cy - 64, cx + 64, cy + 64], outline="white", width=16)
        d.ellipse([cx - 22, cy - 22, cx + 22, cy + 22], fill="white")
        d.ellipse([cx + 42, cy - 76, cx + 70, cy - 48], fill=(184, 240, 214))
        text_center(d, 850 + dy, "原点报价助手", font(96, True), TEXT)
        text_center(d, 1000 + dy, "五金 · 机械设备 · 工业配件", font(42), GRAY)
        text_center(d, 1068 + dy, "手机上几分钟做好一张专业报价单", font(42), GRAY)
        b = ease((t - 0.6) / 0.6)
        fpill = font(36, True)
        pw = d.textlength("使用教程", font=fpill) + 44
        pill(d, (W - pw) / 2, 1200 + dy, "使用教程", fpill)
        text_center(d, 1740, "原点软件", font(36, True), GREEN_DARK)
        layer.putalpha(layer.getchannel("A").point(lambda v: int(v * a)))
        img.paste(layer, (0, 0), layer)
        return img
    return Scene(3.2, render, "intro")


def app_scene(fr):
    layer = phone_layer(SHOTS / fr["file"])
    title = fr["title"]
    if title.startswith("第"):
        p, h = title.split("·")
        p, h = p.strip(), h.strip()
    else:
        p, h = ("首页" if fr["file"] == "f00.png" else "完成"), title
    hold = fr["hold"]
    tap = fr["tap"]
    static = BG.copy()
    static.paste(layer, (0, 0), layer)
    header(static, p, h, fr["sub"])

    def render(t):
        img = static.copy()
        if tap:
            x = SCR_X + tap[0] * SCALE
            y = SCR_Y + tap[1] * SCALE
            start = 0.35
            if t > start:
                ov = Image.new("RGBA", (W, H), (0, 0, 0, 0))
                d = ImageDraw.Draw(ov)
                ph = ((t - start) % 1.1) / 1.1
                r = 30 + 50 * ease(ph)
                d.ellipse([x - r, y - r, x + r, y + r], outline=GREEN + (int(220 * (1 - ph)),), width=6)
                k = ease((t - start) / 0.25)
                r0 = 30 * k
                d.ellipse([x - r0, y - r0, x + r0, y + r0], fill=GREEN + (120,), outline=(255, 255, 255, 230), width=4)
                img.paste(ov, (0, 0), ov)
        return img
    return Scene(hold, render, fr["file"][:-4])


def pdf_scene():
    page = Image.open(SHOTS / "pdf_page.png").convert("RGB")
    base_w = 900
    base_h = int(page.height * base_w / page.width)
    static = BG.copy()
    header(static, "效果", "专业 A4 PDF 报价单", "中文排版 · 金额大写 · 可直接发给客户")

    def render(t):
        img = static.copy()
        s = 1.0 + 0.04 * ease(t / 3.5)
        w, h = int(base_w * s), int(base_h * s)
        x, y = (W - w) // 2, 420
        sh = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        ImageDraw.Draw(sh).rectangle([x + 8, y + 18, x + w - 8, y + h + 8], fill=(20, 60, 40, 60))
        sh = sh.filter(ImageFilter.GaussianBlur(22))
        img.paste(sh, (0, 0), sh)
        im = page.resize((w, h), Image.BILINEAR)
        img.paste(im, (x, y))
        # 底部渐隐，避免与进度条冲突
        fade_h = 160
        if y + h > H - fade_h:
            grad = Image.new("L", (W, fade_h))
            gd = ImageDraw.Draw(grad)
            for i in range(fade_h):
                gd.line([(0, i), (W, i)], fill=int(255 * i / fade_h))
            img.paste(BG.crop((0, H - fade_h, W, H)), (0, H - fade_h), grad)
        return img
    return Scene(3.8, render, "pdf")


def outro_scene():
    items = [
        ("客户管理", "添加、修改、搜索客户"),
        ("商品库", "名称、型号、规格、单价一次录入"),
        ("一键报价", "数量单价随意改，金额自动计算"),
        ("PDF 导出", "保存到手机，微信直接发送"),
        ("完全离线", "无需注册，数据保存在本机"),
    ]

    def render(t):
        img = BG.copy()
        d = ImageDraw.Draw(img)
        a = ease(t / 0.5)
        text_center(d, 300, "原点报价助手", font(80, True), TEXT)
        text_center(d, 420, "让报价更快、更准、更专业", font(42), GRAY)
        for i, (k, v) in enumerate(items):
            b = ease((t - 0.3 - i * 0.22) / 0.4)
            if b <= 0:
                continue
            y = 590 + i * 190
            x = 150 + int((1 - b) * 40)
            card = Image.new("RGBA", (W, H), (0, 0, 0, 0))
            cd = ImageDraw.Draw(card)
            cd.rounded_rectangle([x, y, x + 780, y + 150], radius=28, fill=(255, 255, 255, int(255 * b)),
                                 outline=(224, 232, 228, int(255 * b)), width=2)
            cx, cy = x + 75, y + 75
            cd.ellipse([cx - 34, cy - 34, cx + 34, cy + 34], fill=GREEN + (int(255 * b),))
            cd.line([(cx - 15, cy + 1), (cx - 4, cy + 12), (cx + 17, cy - 12)], fill=(255, 255, 255, int(255 * b)), width=7, joint="curve")
            cd.text((x + 140, y + 26), k, font=font(42, True), fill=TEXT + (int(255 * b),))
            cd.text((x + 140, y + 86), v, font=font(32), fill=GRAY + (int(255 * b),))
            img.paste(card, (0, 0), card)
        c = ease((t - 1.6) / 0.6)
        if c > 0:
            fp = font(40, True)
            text_center(d, 1640, "原点软件 出品", fp, tuple(int(255 + (g - 255) * c) for g in GREEN_DARK))
        if a < 1:
            img = Image.blend(BG, img, a)
        return img
    return Scene(5.0, render, "outro")


frames = json.loads((SHOTS / "frames.json").read_text())
scenes = [intro_scene()]
for fr in frames:
    scenes.append(app_scene(fr))
    if fr["file"] == "f15.png":
        scenes.append(pdf_scene())
scenes.append(outro_scene())

VOICE_START = 0.35  # 画面出现后多久开始说话
jobs = [{"text": speech_text(sc.voice), "out": str(WORK / f"{i:02d}_{sc.key}.wav")} for i, sc in enumerate(scenes)]
(WORK / "jobs.json").write_text(json.dumps(jobs, ensure_ascii=False), encoding="utf-8")
subprocess.run([TTS_PY, "-I", str(Path(__file__).with_name("tts_batch.py")), TTS_MODEL, str(WORK / "jobs.json")], check=True)


def read_wav(path):
    import wave
    with wave.open(str(path)) as wf:
        sr, n = wf.getframerate(), wf.getnframes()
        x = np.frombuffer(wf.readframes(n), dtype=np.int16).astype(np.float32) / 32768
    t_new = np.arange(int(len(x) * SR / sr)) / SR
    return np.interp(t_new, np.arange(len(x)) / sr, x)


voices = [read_wav(j["out"]) for j in jobs]
for sc, v in zip(scenes, voices):
    sc.dur = max(sc.dur, VOICE_START + len(v) / SR + 0.4)

total = sum(s.dur for s in scenes)
print(f"{len(scenes)} 个场景，时长 {total:.1f} 秒")

out_mp4 = OUT / "原点报价助手_使用教程_竖屏.mp4"
silent_mp4 = WORK / "video_only.mp4"
cmd = [
    "ffmpeg", "-y", "-loglevel", "error",
    "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}", "-r", str(FPS), "-i", "-",
    "-c:v", "libx264", "-preset", "medium", "-crf", "20", "-pix_fmt", "yuv420p", str(silent_mp4),
]
proc = subprocess.Popen(cmd, stdin=subprocess.PIPE)

elapsed = 0.0
script_lines = []
prev = None
for idx, sc in enumerate(scenes):
    n = int(round(sc.dur * FPS))
    script_lines.append((elapsed, sc.voice))
    last = None
    for i in range(n):
        t = i / FPS
        img = sc.render(t)
        if prev is not None and t < XFADE:
            img = Image.blend(prev, img, t / XFADE)
        progress(img, (elapsed + t) / total)
        proc.stdin.write(img.tobytes())
        last = img
    prev = sc.render(sc.dur)
    if idx == 0:
        prev.save(OUT / "封面.png")
    elapsed += n / FPS
proc.stdin.close()
proc.wait()

# ---------------- 音轨：配音 + 自制背景音乐 ----------------
n_total = int(elapsed * SR) + SR
voice_track = np.zeros(n_total, np.float32)
for (start, _), v in zip(script_lines, voices):
    i = int((start + VOICE_START) * SR)
    voice_track[i:i + len(v)] += v[: n_total - i]
peak = np.abs(voice_track).max() or 1
voice_track *= 0.85 / peak


def background_music(n):
    """轻柔的钢琴感和弦琶音：C - Am - F - G 循环，约 96 BPM"""
    t = np.arange(n) / SR
    out = np.zeros(n, np.float32)
    beat = 60 / 96
    chords = [[60, 64, 67, 72], [57, 60, 64, 69], [53, 57, 60, 65], [55, 59, 62, 67]]
    hz = lambda m: 440 * 2 ** ((m - 69) / 12)
    bar = beat * 4
    k = 0
    while k * beat * 0.5 < n / SR:
        st = k * beat * 0.5
        chord = chords[int(st // bar) % 4]
        note = chord[[0, 1, 2, 3, 2, 1, 2, 3][k % 8]] + 12
        i0 = int(st * SR)
        L = min(int(1.6 * SR), n - i0)
        tt = np.arange(L) / SR
        env = np.exp(-tt * 3.2) * np.minimum(1, tt / 0.008)
        f = hz(note)
        out[i0:i0 + L] += (np.sin(2 * np.pi * f * tt) + 0.3 * np.sin(4 * np.pi * f * tt)) * env * 0.16
        k += 1
    for c in range(int(n / SR / bar) + 1):  # 低音铺底
        chord = chords[c % 4]
        i0 = int(c * bar * SR)
        L = min(int(bar * SR), n - i0)
        if L <= 0:
            break
        tt = np.arange(L) / SR
        env = np.minimum(1, tt / 0.3) * np.minimum(1, (bar - tt) / 0.4)
        for m in (chord[0] - 12, chord[0], chord[2]):
            out[i0:i0 + L] += np.sin(2 * np.pi * hz(m) * tt) * env * 0.07
    fade = int(1.5 * SR)
    out[:fade] *= np.linspace(0, 1, fade)
    out[-fade * 2:] *= np.linspace(1, 0, fade * 2)
    return out / (np.abs(out).max() or 1)


music = background_music(n_total)
# 说话时把音乐压低（简单的闪避）
env = np.convolve((np.abs(voice_track) > 0.02).astype(np.float32), np.ones(int(0.25 * SR)) / int(0.25 * SR), mode="same")
duck = 1 - 0.55 * np.clip(env * 3, 0, 1)
mix = voice_track + music * 0.22 * duck
mix = np.clip(mix / max(1.0, np.abs(mix).max() / 0.95), -1, 1)
import wave
with wave.open(str(WORK / "mix.wav"), "wb") as wf:
    wf.setnchannels(1); wf.setsampwidth(2); wf.setframerate(SR)
    wf.writeframes((mix * 32767).astype(np.int16).tobytes())
subprocess.run([
    "ffmpeg", "-y", "-loglevel", "error", "-i", str(silent_mp4), "-i", str(WORK / "mix.wav"),
    "-c:v", "copy", "-c:a", "aac", "-b:a", "128k", "-ac", "2", "-shortest", "-movflags", "+faststart", str(out_mp4),
], check=True)

# 配音稿（去掉连续重复的句子）
lines, seen = [], None
for ts, v in script_lines:
    if v and v != seen:
        lines.append(f"[{int(ts // 60):02d}:{ts % 60:05.2f}]  {v}")
    seen = v
(OUT / "配音稿.txt").write_text(
    "原点报价助手 · 使用教程 配音稿\n"
    "（时间为视频中对应画面的开始时间；正式发布时可在剪映中用「文本朗读」重新生成配音）\n\n" + "\n".join(lines) + "\n",
    encoding="utf-8",
)
print("完成：", out_mp4)
