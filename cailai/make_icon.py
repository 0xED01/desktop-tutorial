# Generates the launcher icon PNGs (red-orange rounded square with "财").
import os, sys
from PIL import Image, ImageDraw, ImageFont

FONT = "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc"
SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
out = sys.argv[1] if len(sys.argv) > 1 else "res"

base = 1024
img = Image.new("RGBA", (base, base), (0, 0, 0, 0))
d = ImageDraw.Draw(img)
top, bot = (255, 122, 69), (232, 67, 30)
for y in range(base):
    t = y / base
    d.line([(0, y), (base, y)], fill=tuple(int(top[i] + (bot[i] - top[i]) * t) for i in range(3)) + (255,))
mask = Image.new("L", (base, base), 0)
ImageDraw.Draw(mask).rounded_rectangle([0, 0, base - 1, base - 1], radius=230, fill=255)
img.putalpha(mask)
font = ImageFont.truetype(FONT, 640)
d = ImageDraw.Draw(img)
d.text((base / 2, base / 2 + 20), "财", font=font, fill=(255, 248, 225), anchor="mm")

for name, px in SIZES.items():
    p = os.path.join(out, f"mipmap-{name}")
    os.makedirs(p, exist_ok=True)
    img.resize((px, px), Image.LANCZOS).save(os.path.join(p, "ic_launcher.png"))
