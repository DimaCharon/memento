#!/usr/bin/env python3
"""Локальная проверка: все ли ресурсы, на которые ссылаются layout/values, существуют.
Ловит опечатки до CI. Возвращает 1, если есть висячие ссылки."""
import glob
import os
import re
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app/src/main/res")

# --- что реально есть ---
have = {"drawable": set(), "mipmap": set(), "layout": set(), "anim": set(),
        "xml": set(), "color": set(), "string": set(), "dimen": set(),
        "style": set(), "array": set(), "plurals": set(), "attr": set(),
        "bool": set(), "integer": set(), "id": set()}

for folder in ("drawable", "mipmap-anydpi-v26", "mipmap-mdpi", "layout", "anim", "xml", "color"):
    for f in glob.glob(os.path.join(RES, folder, "*")):
        base = os.path.basename(f)
        name = base.split(".")[0]
        kind = folder.split("-")[0]
        have[kind].add(name)

VALUE_TAGS = {
    "string": "string", "color": "color", "dimen": "dimen", "style": "style",
    "string-array": "array", "integer-array": "array", "array": "array",
    "plurals": "plurals", "attr": "attr", "bool": "bool", "integer": "integer",
    "item": None,
}
for f in glob.glob(os.path.join(RES, "values*", "*.xml")):
    try:
        tree = ET.parse(f)
    except ET.ParseError as e:
        print("XML битый:", f, e)
        sys.exit(1)
    for el in tree.getroot():
        kind = VALUE_TAGS.get(el.tag)
        if el.tag not in VALUE_TAGS:
            continue
        name = el.get("name")
        if kind and name:
            have[kind].add(name)
        # color selectors внутри <selector> в res/color уже собраны по файлам

# --- на что ссылаются ---
REF = re.compile(r'@(?:\+)?(?:(\w+):)?(\w+)/([\w.]+)')
problems = []
for f in glob.glob(os.path.join(RES, "**", "*.xml"), recursive=True):
    if "/values" in f:
        continue
    text = open(f, encoding="utf-8").read()
    for m in REF.finditer(text):
        pkg, kind, name = m.group(1), m.group(2), m.group(3)
        if pkg == "android":
            continue
        if kind in ("id",):
            continue
        if pkg is None and kind not in have:
            continue
        pool = have.get(kind, set())
        if name not in pool:
            problems.append("%s: @%s/%s не найден" % (os.path.relpath(f, ROOT), kind, name))

# стили, на которые ссылаются как parent="..." внутри styles
for f in glob.glob(os.path.join(RES, "values*", "*.xml")):
    text = open(f, encoding="utf-8").read()
    for m in re.finditer(r'parent="(?:@style/)?([\w.]+)"', text):
        name = m.group(1)
        if name and not name.startswith("android:") and name not in have["style"] and not re.match(r'^(Theme|Widget|ThemeOverlay|TextAppearance)\.(Material|AppCompat|Base)', name):
            problems.append("%s: parent=%s не найден" % (os.path.basename(f), name))

# Угол градиента обязан быть кратен 45: иначе drawable не грузится на Android 8 и ниже,
# а приложение падает при запуске (Resources$NotFoundException при инфляции разметки).
for f in glob.glob(os.path.join(RES, "drawable*", "*.xml")):
    text = open(f, encoding="utf-8").read()
    for m in re.finditer(r'android:angle="(-?\d+)"', text):
        angle = int(m.group(1))
        if angle % 45 != 0:
            problems.append(
                "%s: android:angle=%d должен быть кратен 45 (иначе сбой на Android 8 и ниже)"
                % (os.path.relpath(f, ROOT), angle)
            )

if problems:
    print("ПРОБЛЕМЫ (%d):" % len(problems))
    for p in sorted(set(problems)):
        print("  -", p)
    sys.exit(1)
print("Все ссылки на ресурсы корректны.")
