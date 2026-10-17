#!/usr/bin/env python3
"""Рисует иконку NORY: чёрный скруглённый квадрат и металлический знак «cn».

Запуск: python3 tools/make_icon.py
Кладёт PNG во все mipmap-* и обновляет adaptive foreground.
"""
import math
import os
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app/src/main/res")

# Геометрия знака в системе координат 108×108 (как у adaptive icon)
STROKE = 7.0
C_CENTER = (44.0, 54.0)
C_R = 16.0
GAP_FROM, GAP_TO = 330.0, 35.0      # разрыв кольца справа (сверху вниз)
N_END = (81.0, 66.0)
N_TOP = 46.0

def bez(p0, p1, p2, p3, steps=64):
    out = []
    for i in range(steps + 1):
        t = i / steps
        mt = 1 - t
        x = mt**3*p0[0] + 3*mt*mt*t*p1[0] + 3*mt*t*t*p2[0] + t**3*p3[0]
        y = mt**3*p0[1] + 3*mt*mt*t*p1[1] + 3*mt*t*t*p2[1] + t**3*p3[1]
        out.append((x, y))
    return out

def glyph_points():
    pts = []
    steps = 160
    # дуга: от верхнего-правого края разрыва по кругу вниз до нижнего-правого края
    for i in range(steps + 1):
        a = math.radians(GAP_FROM + (GAP_TO - GAP_FROM) * i / steps)
        pts.append((C_CENTER[0] + C_R * math.cos(a), C_CENTER[1] + C_R * math.sin(a)))
    # нижний конец «c» плавно переходит в арку «n»
    start = pts[-1]
    pts += bez(start, (60.5, 61.5), (62.0, 48.5), (69.5, 46.2))[1:]
    pts += bez((69.5, 46.2), (75.5, 44.6), (80.0, 48.6), (80.0, 55.0))[1:]
    pts.append((80.0, 67.0))
    return pts

def rounded_mask(size, radius_px):
    m = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(m)
    d.rounded_rectangle([0, 0, size - 1, size - 1], radius=radius_px, fill=255)
    return m

def circle_mask(size):
    m = Image.new("L", (size, size), 0)
    ImageDraw.Draw(m).ellipse([0, 0, size - 1, size - 1], fill=255)
    return m

def metallic(size):
    """Диагональный металлический градиент с бликом."""
    w = h = size
    base = Image.new("RGB", (w, h))
    px = base.load()
    stops = [(0.00, (255, 255, 255)), (0.18, (226, 232, 242)), (0.36, (150, 159, 175)),
             (0.50, (206, 213, 225)), (0.64, (120, 129, 145)), (0.80, (198, 206, 219)),
             (1.00, (236, 240, 248))]
    for y in range(h):
        for x in range(w):
            t = ((x / w) * 0.55 + (y / h) * 0.45)
            for i in range(len(stops) - 1):
                a, ca = stops[i]
                b, cb = stops[i + 1]
                if a <= t <= b:
                    k = (t - a) / (b - a)
                    px[x, y] = tuple(int(ca[j] + (cb[j] - ca[j]) * k) for j in range(3))
                    break
            else:
                px[x, y] = stops[-1][1]
    return base

def draw_glyph_layer(size, points, scale, offset=(0, 0)):
    """Возвращает маску знака в пикселях размера size.

    Штрих рисуем «штампами» кружков вдоль пути — так не остаётся швов между
    сегментами и линия выглядит гладкой на любом размере.
    """
    s = size / 108.0
    m = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(m)
    w = max(2, int(round(STROKE * s)))
    r = w / 2
    xy = [(p[0] * s + offset[0], p[1] * s + offset[1]) for p in points]
    # сглаживаем путь и ставим точку через каждые 0.35 px
    dense = []
    for i in range(len(xy) - 1):
        (x0, y0), (x1, y1) = xy[i], xy[i + 1]
        dist = math.hypot(x1 - x0, y1 - y0)
        n = max(1, int(dist / 0.35))
        for k in range(n):
            t = k / n
            dense.append((x0 + (x1 - x0) * t, y0 + (y1 - y0) * t))
    dense.append(xy[-1])
    for (x, y) in dense:
        d.ellipse([x - r, y - r, x + r, y + r], fill=255)
    return m

def icon(size, round_icon=False):
    """Полная иконка (фон + знак) в пикселях size."""
    ss = 4
    S = size * ss
    # фон: вертикальный градиент
    bg = Image.new("RGB", (S, S), (9, 10, 12))
    d = ImageDraw.Draw(bg)
    for y in range(S):
        t = y / (S - 1)
        r = int(26 + (7 - 26) * t)
        g = int(28 + (8 - 28) * t)
        b = int(34 + (10 - 34) * t)
        d.line([(0, y), (S, y)], fill=(r, g, b))
    # лёгкий блик сверху
    glow = Image.new("L", (S, S), 0)
    ImageDraw.Draw(glow).ellipse([-S * 0.35, -S * 0.75, S * 1.15, S * 0.35], fill=42)
    glow = glow.filter(ImageFilter.GaussianBlur(S * 0.09))
    bg = Image.composite(Image.new("RGB", (S, S), (70, 78, 92)), bg, glow)

    mask = circle_mask(S) if round_icon else rounded_mask(S, int(S * 0.225))
    out = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    out.paste(bg, (0, 0), mask)

    # тонкая светлая кромка
    if not round_icon:
        edge = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        ImageDraw.Draw(edge).rounded_rectangle(
            [int(S*0.006), int(S*0.006), S - int(S*0.006) - 1, S - int(S*0.006) - 1],
            radius=int(S * 0.223), outline=(255, 255, 255, 34), width=max(1, int(S * 0.0035)))
        out = Image.alpha_composite(out, edge)

    pts = glyph_points()
    s = S / 108.0
    glyph = Image.new("L", (S, S), 0)
    # тень под знаком
    sh = draw_glyph_layer(S, pts, s, offset=(0, int(1.6 * s)))
    sh = sh.filter(ImageFilter.GaussianBlur(int(1.4 * s)))
    shadow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    shadow.putalpha(sh.point(lambda v: int(v * 0.55)))
    out = Image.alpha_composite(out, shadow)

    gmask = draw_glyph_layer(S, pts, s)
    metal = metallic(S).convert("RGBA")
    metal.putalpha(gmask)
    out = Image.alpha_composite(out, metal)
    return out.resize((size, size), Image.LANCZOS)

def foreground(size):
    """Знак без фона для adaptive icon (108dp)."""
    ss = 4
    S = size * ss
    pts = glyph_points()
    s = S / 108.0
    gmask = draw_glyph_layer(S, pts, s)
    metal = metallic(S).convert("RGBA")
    metal.putalpha(gmask)
    return metal.resize((size, size), Image.LANCZOS)

DENS = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}

for dname, k in DENS.items():
    folder = os.path.join(RES, f"mipmap-{dname}")
    os.makedirs(folder, exist_ok=True)
    s = int(48 * k)
    icon(s).save(os.path.join(folder, "ic_launcher.png"))
    icon(s, round_icon=True).save(os.path.join(folder, "ic_launcher_round.png"))
    foreground(int(108 * k)).save(os.path.join(folder, "ic_launcher_foreground.png"))

# Большая картинка для репозитория (посмотреть, как выглядит)
icon(512).save(os.path.join(ROOT, "docs-icon.png"))
print("иконки готовы")
