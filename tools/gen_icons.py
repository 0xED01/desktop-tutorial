#!/usr/bin/env python3
"""Draws the app icon (dark cube with eyes + spike) at all Android densities. Original artwork."""
from PIL import Image, ImageDraw
import os
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
RES = os.path.join(ROOT, 'android', 'app', 'src', 'main', 'res')
SIZES = {'mdpi': 48, 'hdpi': 72, 'xhdpi': 96, 'xxhdpi': 144, 'xxxhdpi': 192}

def draw(n, round_=False):
    S = 512
    im = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    if round_:
        d.ellipse([0, 0, S - 1, S - 1], fill=(246, 241, 231, 255))
    else:
        d.rounded_rectangle([0, 0, S - 1, S - 1], radius=110, fill=(246, 241, 231, 255))
    # ground
    d.rectangle([0, 380, S, S], fill=(61, 64, 91, 255)) if not round_ else d.chord([0, 0, S - 1, S - 1], 30, 150, fill=(61, 64, 91, 255))
    d.rectangle([0, 372, S, 392], fill=(129, 178, 154, 255)) if not round_ else None
    # spike
    d.polygon([(330, 380), (380, 280), (430, 380)], fill=(31, 31, 41, 255))
    # cube
    d.rounded_rectangle([110, 190, 300, 380], radius=34, fill=(32, 32, 42, 255))
    d.rectangle([130, 240, 175, 300], fill=(255, 255, 255, 255))
    d.rectangle([215, 240, 260, 300], fill=(255, 255, 255, 255))
    d.rectangle([155, 262, 172, 290], fill=(32, 32, 42, 255))
    d.rectangle([240, 262, 257, 290], fill=(32, 32, 42, 255))
    d.ellipse([180, 320, 220, 350], fill=(255, 107, 129, 255))
    # exclamation
    d.rectangle([318, 120, 344, 200], fill=(239, 71, 111, 255))
    d.ellipse([316, 212, 346, 242], fill=(239, 71, 111, 255))
    return im.resize((n, n), Image.LANCZOS)

for dens, n in SIZES.items():
    out = os.path.join(RES, f'mipmap-{dens}')
    os.makedirs(out, exist_ok=True)
    draw(n).save(os.path.join(out, 'ic_launcher.png'))
    draw(n, True).save(os.path.join(out, 'ic_launcher_round.png'))
draw(512).save(os.path.join(ROOT, 'docs', 'icon-512.png')) if os.path.isdir(os.path.join(ROOT, 'docs')) else None
print('icons ok')
