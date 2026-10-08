#!/usr/bin/env python3
"""Generate the background illustrations for the built-in scenes.

Procedural, layered silhouette art (no source images), written as WebP into
app/src/main/res/drawable-nodpi/bg_<scene>.webp. Portrait 720x1280; the interesting part sits in
the upper ~60% because the scene screen fades the lower part into the UI.

Usage:  python tools/generate_scene_art.py [output_dir]
Needs:  numpy, pillow
"""
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

W, H = 720, 1280
YY, XX = np.mgrid[0:H, 0:W].astype(np.float32)
rng = np.random.default_rng(11)


def rgb(color):
    if isinstance(color, str):
        return np.array([int(color[i:i + 2], 16) for i in (1, 3, 5)], dtype=np.float32) / 255
    return np.asarray(color, dtype=np.float32)


def mix(a, b, t):
    return rgb(a) * (1 - t) + rgb(b) * t


def fbm(octaves=5, base=4, seed=None):
    """Fractal noise in 0..1, shape (H, W)."""
    r = np.random.default_rng(seed) if seed is not None else rng
    out = np.zeros((H, W), np.float32)
    total = 0.0
    for o in range(octaves):
        size = base * 2 ** o
        grid = (r.random((size, max(2, size * W // H))) * 255).astype(np.uint8)
        layer = np.asarray(Image.fromarray(grid).resize((W, H), Image.BICUBIC), np.float32) / 255
        out += layer * 0.5 ** o
        total += 0.5 ** o
    out /= total
    return (out - out.min()) / (out.max() - out.min() + 1e-6)


class Canvas:
    def __init__(self):
        self.a = np.zeros((H, W, 3), np.float32)

    def gradient(self, stops):
        pos = [p for p, _ in stops]
        cols = np.array([rgb(c) for _, c in stops])
        ys = np.linspace(0, 1, H)
        for ch in range(3):
            self.a[:, :, ch] = np.interp(ys, pos, cols[:, ch])[:, None]

    @staticmethod
    def mask(draw, blur=0):
        m = Image.new('L', (W, H), 0)
        draw(ImageDraw.Draw(m))
        if blur:
            m = m.filter(ImageFilter.GaussianBlur(blur))
        return np.asarray(m, np.float32) / 255

    def paint(self, m, color, alpha=1.0):
        m = m[..., None] * alpha
        c = rgb(color) if not isinstance(color, np.ndarray) or color.ndim == 1 else color
        self.a = self.a * (1 - m) + c * m

    def poly(self, pts, color, alpha=1.0, blur=0):
        self.paint(self.mask(lambda d: d.polygon([tuple(map(float, p)) for p in pts], fill=255), blur), color, alpha)

    def rect(self, box, color, alpha=1.0, blur=0):
        self.paint(self.mask(lambda d: d.rectangle(box, fill=255), blur), color, alpha)

    def ellipse(self, box, color, alpha=1.0, blur=0):
        self.paint(self.mask(lambda d: d.ellipse(box, fill=255), blur), color, alpha)

    def glow(self, x, y, r, color, strength=1.0):
        g = np.exp(-((XX - x) ** 2 + (YY - y) ** 2) / (2 * r * r))
        self.a += rgb(color) * g[..., None] * strength

    def shade(self, factor):
        self.a *= factor[..., None]

    def grain(self, amount=0.025):
        self.a += rng.normal(0, amount, (H, W, 1)).astype(np.float32)

    def vignette(self, strength=0.55):
        d = ((XX - W / 2) / (W / 2)) ** 2 + ((YY - H * 0.4) / (H * 0.75)) ** 2
        self.a *= (1 - strength * np.clip(d, 0, 1.5))[..., None]

    def save(self, path):
        Image.fromarray((np.clip(self.a, 0, 1) * 255).astype(np.uint8)).save(path, 'WEBP', quality=80, method=6)
        print(f"  {path.name:<18} {path.stat().st_size / 1024:6.0f} KB")


def ridge(y_base, amplitude, roughness=6, seed=None):
    """Hill silhouette: list of points across the width, closed at the bottom."""
    r = np.random.default_rng(seed) if seed is not None else rng
    xs = np.linspace(-10, W + 10, 60)
    ys = np.zeros_like(xs)
    for k in range(1, roughness + 1):
        ys += r.normal() / k * np.sin(2 * np.pi * k * xs / W * 0.7 + r.uniform(0, 6.28))
    ys = y_base - amplitude * ys / (np.abs(ys).max() + 1e-6)
    return list(zip(xs, ys)) + [(W + 10, H + 10), (-10, H + 10)]


def stars(c, count, y_max, color='#ffffff'):
    for _ in range(count):
        x, y = rng.uniform(0, W), rng.uniform(0, y_max) ** 1.0
        s = rng.uniform(0.6, 1.8)
        c.ellipse((x - s, y - s, x + s, y + s), color, alpha=rng.uniform(0.3, 0.9) * (1 - y / y_max) ** 0.5)


# ------------------------------------------------------------------------------------- scenes

def house_row(c, baseline, count_range, width_range, height_range, color, window_color, window_chance, scale=1.0):
    x = -20
    while x < W:
        w = rng.uniform(*width_range)
        h = rng.uniform(*height_range)
        roof = w * rng.uniform(0.35, 0.6)
        c.poly([(x, baseline), (x, baseline - h), (x + w / 2, baseline - h - roof), (x + w, baseline - h), (x + w, baseline)], color)
        if rng.random() < 0.5:  # chimney
            cx = x + w * rng.uniform(0.6, 0.8)
            c.rect((cx, baseline - h - roof * 0.9, cx + 8 * scale, baseline - h - roof * 0.3), color)
        for row in range(int(h // (34 * scale))):
            for col in range(max(1, int(w // (30 * scale)))):
                if rng.random() < window_chance:
                    wx = x + 10 * scale + col * 28 * scale
                    wy = baseline - h + 14 * scale + row * 34 * scale
                    if wx + 10 * scale < x + w - 6 * scale:
                        c.rect((wx, wy, wx + 10 * scale, wy + 14 * scale), window_color)
                        c.glow(wx + 5 * scale, wy + 7 * scale, 14 * scale, window_color, 0.25)
        x += w + rng.uniform(-6, 10)


def town(out):
    c = Canvas()
    c.gradient([(0, '#090d22'), (0.3, '#262352'), (0.52, '#7a3f5c'), (0.6, '#d9733e'), (0.64, '#f2a65a'), (1, '#1a1222')])
    stars(c, 140, H * 0.4)
    c.glow(560, 190, 22, '#fff3d6', 1.2)
    c.glow(560, 190, 90, '#9fb3ff', 0.25)
    c.poly(ridge(H * 0.6, 40, seed=1), mix('#d9733e', '#3a2440', 0.6))
    c.poly(ridge(H * 0.63, 25, seed=2), mix('#d9733e', '#2a1b33', 0.75))
    # distant town with church
    house_row(c, H * 0.68, None, (40, 80), (40, 90), '#21172c', '#ffb85c', 0.25, scale=0.7)
    c.rect((300, H * 0.42, 352, H * 0.68), '#21172c')
    c.poly([(292, H * 0.42), (326, H * 0.31), (360, H * 0.42)], '#21172c')
    c.rect((322, H * 0.29, 330, H * 0.315), '#21172c')
    c.ellipse((316, H * 0.45, 336, H * 0.475), '#ffcf7a')
    c.glow(326, H * 0.46, 30, '#ffb85c', 0.4)
    c.rect((0, H * 0.68, W, H), '#140e1c', alpha=1)
    # near houses framing the street
    house_row(c, H * 0.86, None, (110, 170), (150, 260), '#0c0912', '#ffb24d', 0.35, scale=1.4)
    c.glow(W / 2, H * 0.7, 300, '#ff9a4a', 0.12)
    for _ in range(3):  # chimney smoke haze
        x = rng.uniform(80, 640)
        c.ellipse((x - 120, H * 0.5, x + 120, H * 0.56), '#c9a8b8', alpha=0.06, blur=30)
    c.grain()
    c.vignette()
    c.save(out / 'bg_town.webp')


def tavern(out):
    c = Canvas()
    c.gradient([(0, '#120a05'), (0.35, '#3a2210'), (0.7, '#2a170b'), (1, '#120a05')])
    for x in range(0, W, 48):  # wall planks
        c.rect((x, 0, x + 46, H), mix('#4a2c14', '#2b1709', rng.uniform(0, 1)), alpha=0.5)
        c.rect((x + 46, 0, x + 48, H), '#120a05', alpha=0.6)
    wood = fbm(5, 3, seed=5)
    c.shade(0.85 + 0.3 * wood)
    # beams
    c.rect((0, H * 0.08, W, H * 0.115), '#1a0e06')
    c.rect((0, H * 0.115, W, H * 0.12), '#0b0603')
    for x in (30, 650):
        c.rect((x, 0, x + 42, H), '#1a0e06')
    c.poly([(72, H * 0.115), (72, H * 0.17), (170, H * 0.115)], '#1a0e06')
    c.poly([(650, H * 0.115), (650, H * 0.17), (552, H * 0.115)], '#1a0e06')
    # fireplace
    stone = '#5a5048'
    c.rect((190, H * 0.36, 530, H * 0.72), stone)
    for row in range(14):  # stones
        y = H * 0.36 + row * (H * 0.36 / 14)
        offset = 0 if row % 2 else 30
        for x in range(190 - offset, 530, 60):
            c.rect((max(190, x + 3), y + 3, min(530, x + 57), y + H * 0.36 / 14 - 3),
                   mix('#6d625a', '#3f3732', rng.uniform(0, 1)))
    c.rect((170, H * 0.345, 550, H * 0.375), '#24150a')  # mantel
    c.rect((250, H * 0.47, 470, H * 0.72), '#0a0503')
    c.ellipse((250, H * 0.43, 470, H * 0.52), '#0a0503')
    c.glow(360, H * 0.68, 120, '#ff7a1a', 1.1)
    c.glow(360, H * 0.66, 50, '#ffd27a', 0.9)
    for _ in range(9):  # flames
        x = rng.uniform(285, 435)
        h = rng.uniform(60, 140)
        w = rng.uniform(18, 34)
        c.poly([(x - w, H * 0.71), (x + rng.normal(0, 8), H * 0.71 - h), (x + w, H * 0.71)], '#ff9a2a', alpha=0.55, blur=6)
    c.glow(360, H * 0.6, 420, '#ff8a3a', 0.28)
    # mantel candles + bottles
    for x in (210, 250, 470, 505):
        c.rect((x, H * 0.322, x + 10, H * 0.345), '#e8d8b0')
        c.glow(x + 5, H * 0.317, 16, '#ffcf7a', 0.9)
    for i, x in enumerate(range(80, 170, 26)):
        h = rng.uniform(40, 60)
        c.rect((x, H * 0.3 - h, x + 18, H * 0.3), ['#2d4a2a', '#5a2a1a', '#2a3550', '#4a3a1a'][i % 4])
        c.rect((x + 6, H * 0.3 - h - 18, x + 12, H * 0.3 - h), '#20140a')
    c.rect((60, H * 0.3, 190, H * 0.31), '#1a0e06')
    # lanterns
    for x, y in ((150, H * 0.24), (590, H * 0.26)):
        c.rect((x - 1, H * 0.12, x + 1, y - 22), '#0b0603')
        c.rect((x - 14, y - 22, x + 14, y + 18), '#2a1a0a')
        c.rect((x - 9, y - 16, x + 9, y + 12), '#ffd27a')
        c.glow(x, y, 45, '#ffb35a', 0.8)
        c.glow(x, y, 180, '#ff9a3a', 0.15)
    # tables and mugs in the foreground
    c.poly([(0, H * 0.84), (260, H * 0.8), (300, H * 0.86), (0, H * 0.92)], '#1a0e06')
    c.poly([(W, H * 0.82), (450, H * 0.8), (420, H * 0.87), (W, H * 0.92)], '#1a0e06')
    for x, y in ((120, H * 0.805), (190, H * 0.795), (560, H * 0.79)):
        c.rect((x, y - 34, x + 26, y), '#2a1a0a')
        c.rect((x + 26, y - 26, x + 34, y - 10), '#2a1a0a')
        c.rect((x, y - 38, x + 26, y - 32), '#e8dcc0')
    c.rect((0, H * 0.9, W, H), '#0d0703')
    c.grain()
    c.vignette(0.6)
    c.save(out / 'bg_tavern.webp')


def dungeon(out):
    c = Canvas()
    c.gradient([(0, '#0b0d12'), (0.5, '#1d222b'), (1, '#090a0d')])
    rough = fbm(6, 4, seed=7)
    bh, bw = 44, 92
    for row in range(int(H / bh) + 1):
        offset = (row % 2) * bw / 2
        for col in range(-1, int(W / bw) + 2):
            x, y = col * bw - offset, row * bh
            c.rect((x + 3, y + 3, x + bw - 3, y + bh - 3), mix('#3a404c', '#23272f', rng.uniform(0, 1)))
    c.shade(0.7 + 0.5 * rough)
    # arch
    c.rect((240, H * 0.36, 480, H * 0.76), '#4a4f5a')
    c.ellipse((240, H * 0.26, 480, H * 0.46), '#4a4f5a')
    c.rect((262, H * 0.37, 458, H * 0.76), '#050608')
    c.ellipse((262, H * 0.285, 458, H * 0.45), '#050608')
    c.glow(360, H * 0.55, 80, '#3b6aa8', 0.18)
    for i in range(7):  # stairs into the dark
        y = H * 0.76 - i * 16
        c.rect((262 + i * 6, y - 4, 458 - i * 6, y), '#1a1d24', alpha=0.9 - i * 0.1)
    # torches
    for x in (150, 570):
        y = H * 0.44
        c.rect((x - 6, y, x + 6, y + 60), '#2a1c10')
        c.poly([(x - 16, y), (x + 16, y), (x + 8, y + 14), (x - 8, y + 14)], '#3a2a1a')
        c.poly([(x - 12, y), (x + rng.normal(0, 3), y - 46), (x + 12, y)], '#ffb040', blur=3)
        c.poly([(x - 6, y), (x, y - 26), (x + 6, y)], '#fff0b0', blur=2)
        c.glow(x, y - 16, 28, '#ffd27a', 0.9)
        c.glow(x, y - 10, 150, '#ff7a20', 0.45)
        c.glow(x, y, 330, '#ff6a10', 0.12)
    # keep the doorway dark even with torchlight around it
    c.paint(Canvas.mask(lambda d: (d.rectangle((262, H * 0.37, 458, H * 0.72), fill=255),
                                   d.ellipse((262, H * 0.285, 458, H * 0.45), fill=255)), 6), '#030405', 0.75)
    # chains
    for x0, length in ((90, 0.3), (630, 0.22), (420, 0.12)):
        for k in range(int(length * H / 22)):
            y = k * 22
            if k % 2:
                c.ellipse((x0 - 3, y, x0 + 3, y + 26), '#6a6e78')
                c.ellipse((x0 - 1, y + 5, x0 + 1, y + 21), '#15171c')
            else:
                c.ellipse((x0 - 8, y, x0 + 8, y + 26), '#6a6e78')
                c.ellipse((x0 - 5, y + 5, x0 + 5, y + 21), '#15171c')
    c.rect((0, H * 0.76, W, H), '#0d0f13')
    for i in range(1, 12):  # floor slabs
        c.rect((0, H * 0.76 + i * i * 4, W, H * 0.76 + i * i * 4 + 2), '#000000', alpha=0.5)
    c.ellipse((-200, H * 0.72, W + 200, H * 0.86), '#7a8aa0', alpha=0.08, blur=40)  # floor mist
    c.grain(0.03)
    c.vignette(0.65)
    c.save(out / 'bg_dungeon.webp')


def market(out):
    c = Canvas()
    c.gradient([(0, '#3a6ea8'), (0.35, '#8ab4d8'), (0.55, '#f2d7a8'), (0.7, '#d8a878'), (1, '#4a3626')])
    c.glow(600, 170, 60, '#fff4d0', 1.0)
    c.glow(600, 170, 260, '#ffd890', 0.3)
    for i in range(4):  # clouds
        x, y = rng.uniform(0, W), rng.uniform(80, 360)
        c.ellipse((x - 140, y - 25, x + 140, y + 25), '#ffffff', alpha=0.35, blur=18)
    house_row(c, H * 0.62, None, (60, 110), (80, 160), mix('#8a6a5a', '#b8a090', 0.5), '#5a4030', 0.2, scale=0.8)
    c.rect((0, H * 0.62, W, H), '#7a6450')
    stones = fbm(6, 8, seed=3)
    c.shade(np.where(YY > H * 0.62, 0.8 + 0.35 * stones, 1.0))
    # bunting
    flag_colors = ['#c0392b', '#f1c40f', '#2e86c1', '#27ae60', '#e67e22', '#8e44ad']
    for y0, sag in ((H * 0.2, 60), (H * 0.3, 45)):
        xs = np.linspace(-20, W + 20, 26)
        ys = y0 + sag * (1 - ((xs - W / 2) / (W / 2 + 20)) ** 2)
        c.poly(list(zip(xs, ys)) + list(zip(xs[::-1], ys[::-1] + 2)), '#3a2a1a')
        for i in range(len(xs) - 1):
            x, y = xs[i], ys[i]
            c.poly([(x + 2, y + 1), (x + 24, ys[i + 1] + 1), (x + 13, y + 34)], flag_colors[i % len(flag_colors)])
    # stalls
    stripes = [('#c0392b', '#f5ecd8'), ('#2e6da4', '#f5ecd8'), ('#2e8b57', '#f2d06b'), ('#d35400', '#f5ecd8')]
    for i, (x, w) in enumerate(((-30, 250), (240, 240), (500, 260))):
        top, bottom = H * 0.47, H * 0.54
        a, b = stripes[i % len(stripes)]
        for k, sx in enumerate(range(int(x), int(x + w), 28)):
            col = a if k % 2 == 0 else b
            c.poly([(sx, top), (sx + 28, top), (sx + 32, bottom), (sx - 4, bottom)], col)
            c.ellipse((sx - 4, bottom - 14, sx + 32, bottom + 14), col)
        c.rect((x + 10, bottom, x + 18, H * 0.72), '#4a3020')
        c.rect((x + w - 18, bottom, x + w - 10, H * 0.72), '#4a3020')
        c.rect((x + 10, bottom + 16, x + w - 10, H * 0.72), '#000000', alpha=0.25)
        c.rect((x, H * 0.66, x + w, H * 0.72), '#6a4428')
        for _ in range(16):  # produce
            px, py = rng.uniform(x + 20, x + w - 20), rng.uniform(H * 0.645, H * 0.665)
            r = rng.uniform(7, 11)
            c.ellipse((px - r, py - r, px + r, py + r), rng.choice(['#c0392b', '#e67e22', '#f1c40f', '#7cb342', '#8e44ad']))
    for x in (40, 600, 300):  # crates
        c.rect((x, H * 0.75, x + 90, H * 0.82), '#7a5530')
        c.rect((x, H * 0.785, x + 90, H * 0.79), '#4a3018')
    c.grain(0.02)
    c.vignette(0.4)
    c.save(out / 'bg_market.webp')


def tree(c, x, base, height, color, kind):
    if kind == 'pine':
        c.rect((x - height * 0.02, base - height * 0.25, x + height * 0.02, base), color)
        tiers = 5
        for t in range(tiers):
            y = base - height * 0.15 - t * height * 0.17
            w = height * (0.32 - t * 0.05)
            c.poly([(x - w, y), (x, y - height * 0.3), (x + w, y)], color)
    else:
        c.rect((x - height * 0.03, base - height * 0.5, x + height * 0.03, base), color)
        for _ in range(6):
            cx, cy = x + rng.normal(0, height * 0.12), base - height * rng.uniform(0.55, 0.85)
            r = height * rng.uniform(0.14, 0.22)
            c.ellipse((cx - r, cy - r, cx + r, cy + r), color)


def forest(out):
    c = Canvas()
    c.gradient([(0, '#0b2420'), (0.25, '#1f4a3c'), (0.5, '#8fbf9f'), (0.62, '#5a8a6a'), (1, '#0a1a12')])
    c.glow(180, -40, 300, '#fff6c8', 0.5)
    for layer, (base, color, size, count) in enumerate([
        (H * 0.52, mix('#8fbf9f', '#4a7a62', 0.3), 200, 26),
        (H * 0.58, mix('#8fbf9f', '#2e5a44', 0.55), 300, 18),
        (H * 0.65, mix('#5a8a6a', '#1a3628', 0.7), 420, 12),
        (H * 0.74, '#0f2218', 600, 7),
    ]):
        for _ in range(count):
            tree(c, rng.uniform(-40, W + 40), base + rng.uniform(-20, 30), size * rng.uniform(0.7, 1.1), color,
                 'pine' if rng.random() < 0.8 else 'oak')
        c.rect((0, base, W, H), color)
        c.ellipse((-200, base - 60, W + 200, base + 40), '#c8e8d0', alpha=0.18 - layer * 0.04, blur=35)  # fog
    for i in range(6):  # god rays
        x = 120 + i * 70 + rng.uniform(-20, 20)
        ray = Canvas.mask(lambda d: d.polygon([(x, -10), (x + 40, -10), (x + 260, H * 0.75), (x + 140, H * 0.75)], fill=255), 25)
        c.a += rgb('#fff2b8') * (ray * 0.2 * (1 - YY / H))[..., None]
    for x in (30, 680):  # big foreground trunks
        c.rect((x - 40, 0, x + 40, H), '#08130d')
    c.poly([(-20, H), (-20, H * 0.85), (120, H * 0.82), (260, H * 0.9), (W, H * 0.86), (W, H)], '#07110b')
    for _ in range(26):  # fireflies
        x, y = rng.uniform(40, 680), rng.uniform(H * 0.55, H * 0.85)
        c.glow(x, y, 3, '#e8ff9a', 1.0)
        c.glow(x, y, 14, '#c8ff6a', 0.25)
    c.grain(0.02)
    c.vignette(0.5)
    c.save(out / 'bg_forest.webp')


def cave(out):
    c = Canvas()
    c.gradient([(0, '#05070c'), (0.5, '#0c1018'), (1, '#04050a')])
    # opening to a moonlit sky
    opening = Canvas.mask(lambda d: d.polygon(
        [(240, 0), (480, 0), (520, 60), (500, 150), (430, 210), (330, 220), (230, 160), (210, 70)], fill=255), 8)
    sky = Canvas()
    sky.gradient([(0, '#1a2c55'), (0.2, '#3a5a8a'), (1, '#3a5a8a')])
    c.a = c.a * (1 - opening[..., None]) + sky.a * opening[..., None]
    for _ in range(25):
        x, y = rng.uniform(240, 480), rng.uniform(0, 180)
        if opening[int(y), int(x)] > 0.9:
            c.ellipse((x - 1, y - 1, x + 1, y + 1), '#ffffff', alpha=0.8)
    for i in range(4):  # moonlight shafts
        x = 290 + i * 40
        ray = Canvas.mask(lambda d: d.polygon([(x, 150), (x + 30, 150), (x + 120, H * 0.8), (x - 40, H * 0.8)], fill=255), 30)
        c.a += rgb('#9fc0ff') * (ray * 0.07)[..., None]
    rock = fbm(6, 5, seed=9)
    walls = np.clip(np.abs(XX - W / 2) / (W / 2), 0, 1) ** 1.5
    c.a += (rgb('#2a3448') * ((rock * 0.6 + 0.2) * walls * 0.9)[..., None])
    # stalactites and stalagmites
    for _ in range(26):
        x, length, w = rng.uniform(-20, W + 20), rng.uniform(60, 300), rng.uniform(14, 40)
        if 220 < x < 500 and length > 120:
            continue
        c.poly([(x - w, -5), (x + w, -5), (x + rng.normal(0, 6), length)], mix('#0a0d14', '#1f2635', rng.uniform(0, 1)))
    for _ in range(14):
        x, height, w = rng.uniform(-20, W + 20), rng.uniform(60, 260), rng.uniform(20, 50)
        c.poly([(x - w, H * 0.86), (x + rng.normal(0, 6), H * 0.86 - height), (x + w, H * 0.86)], mix('#06080d', '#151b27', rng.uniform(0, 1)))
    # crystals
    for cx, cy, color in ((120, H * 0.7, '#3ae0ff'), (600, H * 0.66, '#b46aff'), (480, H * 0.78, '#3ae0ff')):
        c.glow(cx, cy, 160, color, 0.22)
        for _ in range(7):
            ang = rng.uniform(-0.7, 0.7) - math.pi / 2
            length, w = rng.uniform(40, 110), rng.uniform(8, 16)
            tip = (cx + math.cos(ang) * length, cy + math.sin(ang) * length)
            px, py = -math.sin(ang) * w, math.cos(ang) * w
            c.poly([(cx + px, cy + py), (tip[0] + px * 0.6, tip[1] + py * 0.6), (tip[0] + math.cos(ang) * w, tip[1] + math.sin(ang) * w),
                    (tip[0] - px * 0.6, tip[1] - py * 0.6), (cx - px, cy - py)], color, alpha=0.75)
        c.glow(cx, cy - 40, 40, '#ffffff', 0.25)
    # still water with reflections
    c.rect((0, H * 0.86, W, H), '#060a12')
    for i in range(18):
        y = H * 0.87 + i * 9
        c.rect((rng.uniform(150, 300), y, rng.uniform(400, 560), y + 2), '#6a8ac0', alpha=0.15 * (1 - i / 18))
    c.grain(0.025)
    c.vignette(0.6)
    c.save(out / 'bg_cave.webp')


def title(out):
    """Main menu: a tavern at night. The sky (top third) stays clear for the app's title."""
    c = Canvas()
    c.gradient([(0, '#060818'), (0.3, '#141634'), (0.52, '#2e2142'), (0.6, '#3a2436'), (1, '#120c0a')])
    stars(c, 170, H * 0.45)
    c.glow(150, 210, 26, '#fff4dc', 1.3)
    c.glow(150, 210, 110, '#9fb3ff', 0.22)
    house_row(c, H * 0.62, None, (40, 80), (50, 110), '#1c1424', '#ffb85c', 0.18, scale=0.7)
    c.rect((0, H * 0.62, W, H), '#140e12')

    wall, timber, roof = '#3a2414', '#160d07', '#120906'
    left, right, top, bottom = 110, 610, H * 0.40, H * 0.86
    c.poly([(80, top), (360, H * 0.24), (640, top)], roof)                       # roof
    c.rect((470, H * 0.25, 510, H * 0.33), roof)                                  # chimney
    for i in range(4):
        x, y = 490 + i * 14, H * 0.24 - i * 34
        c.ellipse((x - 40, y - 22, x + 40, y + 22), '#b8a8b8', alpha=0.05, blur=14)
    c.rect((left, top, right, bottom), wall)
    plaster = fbm(5, 6, seed=21)
    c.shade(np.where((XX > left) & (XX < right) & (YY > top) & (YY < bottom), 0.85 + 0.3 * plaster, 1.0))
    for y in (top, H * 0.585, bottom - 6):                                       # timber frame
        c.rect((left - 6, y, right + 6, y + 12), timber)
    for x in (left - 6, 240, 470, right - 6):
        c.rect((x, top, x + 14, bottom), timber)
    for x0, x1 in ((left, 240), (470, right)):
        c.poly([(x0 + 6, H * 0.585), (x0 + 18, H * 0.585), (x1, top + 12), (x1 - 12, top + 12)], timber)

    def window(x0, y0, x1, y1):
        c.rect((x0 - 6, y0 - 6, x1 + 6, y1 + 6), timber)
        c.rect((x0, y0, x1, y1), '#ffc070')
        c.glow((x0 + x1) / 2, (y0 + y1) / 2, 45, '#ffb35a', 0.55)
        c.rect(((x0 + x1) / 2 - 3, y0, (x0 + x1) / 2 + 3, y1), timber)
        c.rect((x0, (y0 + y1) / 2 - 3, x1, (y0 + y1) / 2 + 3), timber)

    window(275, H * 0.455, 345, H * 0.53)
    window(375, H * 0.455, 445, H * 0.53)
    window(140, H * 0.64, 205, H * 0.71)
    window(515, H * 0.64, 580, H * 0.71)
    door = Canvas.mask(lambda d: (d.rectangle((300, H * 0.68, 420, bottom), fill=255),
                                  d.ellipse((300, H * 0.645, 420, H * 0.715), fill=255)))
    c.paint(door, '#ffcf80')
    c.glow(360, H * 0.79, 70, '#fff0c0', 0.6)
    c.paint(Canvas.mask(lambda d: (d.rectangle((292, H * 0.68, 300, bottom), fill=255),
                                    d.rectangle((420, H * 0.68, 428, bottom), fill=255))), timber)
    for x in (265, 455):                                                          # wall lanterns
        y = H * 0.70
        c.rect((x - 8, y - 14, x + 8, y + 12), '#2a1a0a')
        c.rect((x - 5, y - 10, x + 5, y + 8), '#ffd27a')
        c.glow(x, y, 26, '#ffd27a', 0.9)
        c.glow(x, y, 120, '#ff9a3a', 0.18)

    c.rect((610, H * 0.44, 690, H * 0.448), '#1a0f07')                            # sign bracket
    for x in (628, 672):
        c.rect((x, H * 0.448, x + 2, H * 0.47), '#3a2a1a')
    c.rect((604, H * 0.47, 696, H * 0.535), '#5a3818')
    c.rect((610, H * 0.476, 690, H * 0.529), '#6e4520')
    c.rect((636, H * 0.484, 662, H * 0.52), '#e8b85c')                            # tankard on the sign
    c.rect((662, H * 0.49, 670, H * 0.512), '#e8b85c')
    c.rect((633, H * 0.481, 665, H * 0.489), '#fff0c8')

    c.glow(360, H * 0.9, 260, '#ff9a3a', 0.16)                                    # light spilling onto the street
    stones = fbm(6, 10, seed=22)
    c.shade(np.where(YY > bottom, 0.75 + 0.4 * stones, 1.0))
    c.ellipse((230, H * 0.865, 490, H * 0.93), '#ffcf80', alpha=0.12, blur=30)
    for _ in range(18):                                                           # drifting embers
        x, y = rng.normal(360, 120), rng.uniform(H * 0.6, H * 0.85)
        c.glow(x, y, 2.5, '#ffd27a', 0.9)
    c.grain(0.025)
    c.vignette(0.55)
    c.save(out / 'bg_title.webp')


def app_background(out):
    """Behind every screen: warm darkness with faint candle-glow and wood grain, kept low-contrast."""
    c = Canvas()
    c.gradient([(0, '#120d0a'), (0.5, '#17100c'), (1, '#0d0907')])
    grain = np.asarray(Image.fromarray((fbm(5, 3, seed=31) * 255).astype(np.uint8)).resize((W // 8, H)).resize((W, H), Image.BICUBIC),
                       np.float32) / 255
    c.shade(0.9 + 0.18 * grain)
    c.glow(W * 0.85, H * 0.08, 380, '#ff9a3a', 0.07)
    c.glow(W * 0.1, H * 0.95, 420, '#c0501a', 0.06)
    c.glow(W * 0.5, H * 0.45, 600, '#3a2430', 0.05)
    c.grain(0.018)
    c.vignette(0.5)
    c.save(out / 'bg_app.webp')


def main():
    out = Path(sys.argv[1] if len(sys.argv) > 1 else "app/src/main/res/drawable-nodpi")
    out.mkdir(parents=True, exist_ok=True)
    print(f"Writing to {out}/")
    # New pictures go at the end: each one draws from the shared random generator.
    for fn in (town, tavern, dungeon, market, forest, cave, title, app_background):
        fn(out)


if __name__ == "__main__":
    main()
