"""Add a drifting signature watermark to a finished video.

usage: python3 watermark.py <in.mp4> <out.mp4> <font.ttf> [name]

The mark sits inside the picture (not in the black bands, which re-uploaders crop),
and glides between the right and the left side every 12 s so it can't be covered
by one fixed sticker or blur box.
"""
import sys, subprocess, math
import numpy as np, cv2
from PIL import Image, ImageDraw, ImageFont, ImageFilter

src, out, font_path = sys.argv[1:4]
name = sys.argv[4] if len(sys.argv) > 4 else '福建小皮蛋'
W, H = 1080, 1920

def make_mark():
    f_small = ImageFont.truetype(font_path, 26); f_big = ImageFont.truetype(font_path, 40)
    tmp = ImageDraw.Draw(Image.new('RGBA', (10, 10)))
    w1 = tmp.textlength('抖音', font=f_small); w2 = tmp.textlength('@' + name, font=f_big)
    pw, ph = int(w1 + w2 + 22 + 26 + 30), 66
    im = Image.new('RGBA', (pw + 24, ph + 24), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    # soft shadow, then a translucent dark pill with a thin warm outline
    sh = Image.new('RGBA', im.size, (0, 0, 0, 0)); ImageDraw.Draw(sh).rounded_rectangle([12, 14, 12 + pw, 14 + ph], radius=ph // 2, fill=(0, 0, 0, 110))
    im = Image.alpha_composite(im, sh.filter(ImageFilter.GaussianBlur(6))); d = ImageDraw.Draw(im)
    d.rounded_rectangle([12, 12, 12 + pw, 12 + ph], radius=ph // 2, fill=(12, 14, 13, 150), outline=(232, 176, 84, 170), width=2)
    x = 12 + 22
    d.text((x, 12 + 18), '抖音', font=f_small, fill=(232, 176, 84, 235)); x += w1 + 12
    d.line([(x, 12 + 18), (x, 12 + ph - 18)], fill=(232, 176, 84, 120), width=2); x += 14
    d.text((x, 12 + 6), '@' + name, font=f_big, fill=(244, 236, 220, 240))
    a = np.array(im).astype(np.float32)
    return a[..., :3], a[..., 3:4] / 255

RGB, A = make_mark(); mh, mw = A.shape[:2]
Y = 1020                      # inside the picture, clear of the captions and Douyin's side buttons
XR, XL = W - 110 - mw, 90     # right and left resting spots (inside the phone-crop safe width)
PERIOD = 12.0

def ease(t): return 0.5 - 0.5 * math.cos(math.pi * min(1, max(0, t)))
def pos(t):
    k = (t % (2 * PERIOD)) / PERIOD       # 0..2
    side = ease((k - 0.85) / 0.3) if k < 1.5 else 1 - ease((k - 1.85) / 0.3)
    return int(round(XR + (XL - XR) * side))

fps = cv2.VideoCapture(src).get(cv2.CAP_PROP_FPS) or 30
cap = cv2.VideoCapture(src)
p = subprocess.Popen(['ffmpeg', '-y', '-loglevel', 'error', '-f', 'rawvideo', '-pix_fmt', 'bgr24', '-s', f'{W}x{H}', '-r', str(fps), '-i', '-',
                      '-i', src, '-map', '0:v', '-map', '1:a', '-c:v', 'libx264', '-preset', 'medium', '-crf', '18', '-pix_fmt', 'yuv420p',
                      '-profile:v', 'high', '-c:a', 'copy', '-shortest', '-movflags', '+faststart', out], stdin=subprocess.PIPE)
i = 0
while True:
    ok, fr = cap.read()
    if not ok: break
    t = i / fps
    fade = min(1, t / 0.8)
    x = pos(t)
    roi = fr[Y:Y + mh, x:x + mw].astype(np.float32)
    a = A * 0.92 * fade
    fr[Y:Y + mh, x:x + mw] = (roi * (1 - a) + RGB[..., ::-1] * a).astype(np.uint8)
    p.stdin.write(fr.tobytes()); i += 1
p.stdin.close(); p.wait(); print('done', i, 'frames')
