#!/usr/bin/env python3
"""Вторая проверка вёрстки — по нарисованным картинкам (screenshots/*.png).

Смотрим реальные пиксели: «+» строго по центру, четыре вкладки меню идут с равным шагом
и симметрично, галочки в списке видны и выстроены в колонку. Запускается в CI после тестов.
"""
import glob
import os
import sys

from PIL import Image

ok = True


def say(good, text):
    global ok
    print(("  OK   " if good else "  ПЛОХО ") + text)
    if not good:
        ok = False


def groups_of(flags, min_width=4, merge_gap=30):
    """Собирает подряд идущие True в группы: [(начало, конец, центр)]."""
    out, start = [], None
    for i, f in enumerate(flags):
        if f and start is None:
            start = i
        elif not f and start is not None:
            if i - start >= min_width:
                out.append([start, i - 1])
            start = None
    if start is not None and len(flags) - start >= min_width:
        out.append([start, len(flags) - 1])
    merged = []
    for g in out:
        if merged and g[0] - merged[-1][1] <= merge_gap:
            merged[-1][1] = g[1]
        else:
            merged.append(g)
    return [(a, b, (a + b) / 2) for a, b in merged]


def home():
    path = "screenshots/01-главная.png"
    if not os.path.exists(path):
        say(False, f"нет файла {path}")
        return
    img = Image.open(path).convert("RGB")
    w, h = img.size
    px = img.load()
    print(f"проверяю {path}: {w}×{h}")

    # 1) тело кнопки «+»: широкие светлые полосы внизу экрана
    fab_rows = []
    for y in range(int(h * 0.80), h):
        best = 0
        start = None
        for x in range(w):
            bright = sum(px[x, y]) > 520
            if bright and start is None:
                start = x
            elif not bright and start is not None:
                best = max(best, x - start if x - start > best else 0)
                start = None
            elif bright and start is None:
                pass
        fab_rows.append((y, best))
    rows = [y for y, b in fab_rows if b >= 80]
    if not rows:
        say(False, "не нашёл светлую кнопку «+» внизу экрана")
        return
    y0, y1 = rows[0], rows[-1]
    xs = []
    for y in range(y0, y1 + 1):
        run, start = None, None
        for x in range(w):
            bright = sum(px[x, y]) > 520
            if bright and start is None:
                start = x
            elif not bright and start is not None:
                if run is None or (x - 1 - start) > (run[1] - run[0]):
                    run = (start, x - 1)
                start = None
        if run and run[1] - run[0] >= 80:
            xs += [run[0], run[1]]
    if not xs:
        say(False, "не смог определить границы кнопки «+»")
        return
    fab_center = (min(xs) + max(xs)) / 2
    say(abs(fab_center - w / 2) <= 5, f"«+» по центру: x={fab_center:.1f}, центр экрана {w / 2:.1f}")
    say((y1 - y0) >= 100, f"«+» достаточно крупный: высота {y1 - y0}px")

    # 2) вкладки меню: светлые глифы в полосе панели, симметрия относительно «+»
    band = range(max(0, y0 - 60), min(h, y1 + 90))
    flags = [sum(1 for y in band if sum(px[x, y]) > 250) > 1 for x in range(w)]
    found = groups_of(flags, min_width=4, merge_gap=int(w * 0.025))
    tabs = [g for g in found if g[2] < fab_center - w * 0.06 or g[2] > fab_center + w * 0.06]
    print("     элементы панели:", [(int(a), int(b)) for a, b, _ in found])
    say(len(tabs) >= 4, f"вкладок по бокам от «+»: {len(tabs)} (нужно 4)")
    if len(tabs) >= 4:
        left = sorted([g[2] for g in tabs if g[2] < fab_center], reverse=True)
        right = sorted([g[2] for g in tabs if g[2] > fab_center])
        near = (fab_center - left[0], right[0] - fab_center)
        far = (fab_center - left[1], right[1] - fab_center)
        say(abs(near[0] - near[1]) <= 6, f"ближние вкладки симметричны: {near[0]:.0f} и {near[1]:.0f} px от центра")
        say(abs(far[0] - far[1]) <= 6, f"дальние вкладки симметричны: {far[0]:.0f} и {far[1]:.0f} px от центра")
        step_l = far[0] - near[0]
        step_r = far[1] - near[1]
        say(abs(step_l - step_r) <= 6, f"шаг вкладок одинаков: {step_l:.0f} и {step_r:.0f} px")

    # 3) галочки списка: светлые кольца в правой колонке карточки «Сегодня»
    col_from, col_to = int(w * 0.82), int(w * 0.95)
    rows_flags = []
    for y in range(int(h * 0.30), int(h * 0.72)):
        cnt = sum(1 for x in range(col_from, col_to) if sum(px[x, y]) > 250)
        rows_flags.append(cnt >= 2)
    rings = groups_of(rows_flags, min_width=6, merge_gap=20)
    print("     полосы галочек:", [(int(a), int(b)) for a, b, _ in rings])
    say(len(rings) >= 4, f"видимых галочек в списке: {len(rings)} (нужно 4-5)")


def not_blank(name, min_std=6.0):
    if not os.path.exists("screenshots/" + name):
        say(False, f"нет файла screenshots/{name}")
        return
    small = Image.open("screenshots/" + name).convert("L").resize((64, 128))
    data = list(small.tobytes())
    mean = sum(data) / len(data)
    std = (sum((v - mean) ** 2 for v in data) / len(data)) ** 0.5
    say(std > min_std, f"{name}: картинка не пустая (разброс яркости {std:.1f})")


if __name__ == "__main__":
    files = sorted(os.path.basename(p) for p in glob.glob("screenshots/*.png"))
    print("картинок для проверки:", len(files))
    if not files:
        print("ОШИБКА: скриншоты не созданы")
        sys.exit(1)
    home()
    for extra in ("07-новое-напоминание.png", "05-профиль.png", "02-виджеты.png"):
        not_blank(extra)
    print("ИТОГ:", "вёрстка ровная" if ok else "есть замечания")
    sys.exit(0 if ok else 1)
