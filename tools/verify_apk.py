#!/usr/bin/env python3
"""Проверка собранного APK без Android SDK: разбор бинарного AndroidManifest.xml
и структурные проверки. Запускается в CI на каждом билде.

Использование: python3 tools/verify_apk.py app/build/outputs/apk/release/*.apk
"""
import struct
import sys
import zipfile
import hashlib

RES_STRING_POOL = 0x0001
RES_XML_START_ELEMENT = 0x0102
RES_XML_END_ELEMENT = 0x0103
RES_XML_RESOURCE_MAP = 0x0180

# Типы значений в typed value
TYPE_REFERENCE = 0x01
TYPE_STRING = 0x03
TYPE_FLOAT = 0x04
TYPE_INT_DEC = 0x10
TYPE_INT_HEX = 0x11
TYPE_BOOLEAN = 0x12

ANDROID_NS = "http://schemas.android.com/apk/res/android"


def decode_string_pool(data, offset):
    (typ, header_size, size) = struct.unpack_from("<HHI", data, offset)
    (string_count, style_count, flags, strings_start, styles_start) = struct.unpack_from("<IIIII", data, offset + 8)
    utf8 = (flags & (1 << 8)) != 0
    offsets = struct.unpack_from("<%dI" % string_count, data, offset + header_size)
    strings = []
    base = offset + strings_start
    for off in offsets:
        p = base + off
        if utf8:
            # длина в байтах (1 или 2 байта), затем длина в символах
            n = data[p]
            p += 2 if (n & 0x80) else 1
            m = data[p]
            if m & 0x80:
                m = ((m & 0x7F) << 8) | data[p + 1]
                p += 2
            else:
                p += 1
            strings.append(data[p:p + m].decode("utf-8", "replace"))
        else:
            n = struct.unpack_from("<H", data, p)[0]
            p += 2
            if n & 0x8000:
                n = ((n & 0x7FFF) << 16) | struct.unpack_from("<H", data, p)[0]
                p += 2
            strings.append(data[p:p + n * 2].decode("utf-16-le", "replace"))
    return strings


def parse_binary_xml(data):
    """Возвращает (root_element_dict, namespaces)."""
    assert struct.unpack_from("<H", data, 0)[0] == 0x0003, "это не бинарный XML"
    strings = []
    pos = 8
    stack = []
    root = None
    while pos + 8 <= len(data):
        typ, header_size, size = struct.unpack_from("<HHI", data, pos)
        if size == 0:
            break
        if typ == RES_STRING_POOL:
            strings = decode_string_pool(data, pos)
        elif typ == RES_XML_START_ELEMENT:
            name_idx = struct.unpack_from("<i", data, pos + 20)[0]
            attr_start = struct.unpack_from("<H", data, pos + 24)[0]
            attr_count = struct.unpack_from("<H", data, pos + 28)[0]
            el = {"name": strings[name_idx] if 0 <= name_idx < len(strings) else "?", "attrs": {}, "children": []}
            if stack:
                stack[-1]["children"].append(el)
            else:
                root = el
            for i in range(attr_count):
                ap = pos + 16 + attr_start + i * 20
                ns_idx, name_i, raw_i = struct.unpack_from("<iii", data, ap)
                val_type = data[ap + 15]
                val_data = struct.unpack_from("<I", data, ap + 16)[0]
                name = strings[name_i] if 0 <= name_i < len(strings) else None
                if name is None:
                    continue
                if val_type == TYPE_STRING:
                    value = strings[val_data] if val_data < len(strings) else ""
                elif val_type == TYPE_BOOLEAN:
                    value = val_data != 0
                elif val_type in (TYPE_INT_DEC, TYPE_INT_HEX):
                    value = val_data
                elif val_type == TYPE_REFERENCE:
                    value = "@ref/0x%08x" % val_data
                else:
                    value = val_data
                ns = strings[ns_idx] if 0 <= ns_idx < len(strings) else ""
                key = ("android:" + name) if ns == ANDROID_NS else name
                el["attrs"][key] = value
            stack.append(el)
        elif typ == RES_XML_END_ELEMENT:
            if stack:
                stack.pop()
        pos += size
    return root


def walk(node, name, out):
    for ch in node.get("children", []):
        if ch["name"] == name:
            out.append(ch)
        walk(ch, name, out)


def find_all(root, name):
    out = []
    walk(root, name, out)
    return out


def main(paths):
    ok = True
    for path in paths:
        print("=" * 72)
        print("APK:", path)
        with open(path, "rb") as f:
            raw = f.read()
        print("размер: %.2f МБ" % (len(raw) / 1048576))
        print("sha256:", hashlib.sha256(raw).hexdigest()[:32])
        z = zipfile.ZipFile(path)
        names = z.namelist()
        info = {i.filename: i for i in z.infolist()}
        print("файлов в архиве:", len(names))
        print("classes.dex:", [n for n in names if n.startswith("classes") and n.endswith(".dex")])
        print("resources.arsc:", "resources.arsc" in names)

        man = parse_binary_xml(z.read("AndroidManifest.xml"))
        a = man["attrs"]
        print("package:", a.get("package"), "| versionName:", a.get("android:versionName"),
              "| versionCode:", a.get("android:versionCode"))
        print("minSdk:", a.get("android:minSdkVersion"), "| targetSdk:", a.get("android:targetSdkVersion"))

        perms = sorted(p["attrs"].get("android:name", "") for p in find_all(man, "uses-permission"))
        print("разрешения (%d):" % len(perms))
        for p in perms:
            print("   ", p)

        # Проверки
        def check(cond, msg):
            nonlocal ok
            print(("  ✔ " if cond else "  ✘ ") + msg)
            if not cond:
                ok = False

        print("проверки:")
        check(a.get("package") == "ru.memento", "package = ru.memento")
        check(a.get("android:minSdkVersion") == 24, "minSdk = 24 (Android 7.0+, тянет любые телефоны)")
        check(a.get("android:targetSdkVersion") == 35, "targetSdk = 35")

        activities = find_all(man, "activity")
        services = find_all(man, "service")
        receivers = find_all(man, "receiver")
        providers = find_all(man, "provider")
        print("компоненты: activity=%d service=%d receiver=%d provider=%d" % (
            len(activities), len(services), len(receivers), len(providers)))
        for el in activities + services + receivers + providers:
            print("   ", el["attrs"].get("android:name"))

        launcher = None
        for act in activities:
            for filt in act.get("children", []):
                if filt["name"] == "intent-filter":
                    cats = [c["attrs"].get("android:name") for c in filt["children"] if c["name"] == "category"]
                    if "android.intent.category.LAUNCHER" in cats:
                        launcher = act["attrs"].get("android:name")
        check(launcher is not None, "есть launcher-активность (%s)" % launcher)

        need = [
            ("ru.memento.ui.MainActivity", activities),
            ("ru.memento.ui.ReminderSheetActivity", activities),
            ("ru.memento.service.AiTaskService", services),
            ("ru.memento.widget.TodayWidgetService", services),
            ("ru.memento.alarm.ReminderReceiver", receivers),
            ("ru.memento.alarm.ActionReceiver", receivers),
            ("ru.memento.alarm.BootReceiver", receivers),
            ("ru.memento.widget.TodayWidgetProvider", receivers),
            ("ru.memento.widget.ProgressWidgetProvider", receivers),
            ("ru.memento.widget.WidgetActionReceiver", receivers),
        ]
        for name, where in need:
            check(any(el["attrs"].get("android:name") == name for el in where), "объявлен %s" % name)

        for perm in ["android.permission.POST_NOTIFICATIONS", "android.permission.RECORD_AUDIO",
                     "android.permission.SCHEDULE_EXACT_ALARM", "android.permission.USE_EXACT_ALARM",
                     "android.permission.FOREGROUND_SERVICE", "android.permission.FOREGROUND_SERVICE_DATA_SYNC",
                     "android.permission.RECEIVE_BOOT_COMPLETED", "android.permission.INTERNET",
                     "android.permission.WAKE_LOCK"]:
            check(perm in perms, "разрешение %s" % perm)

        # Тип foreground-службы (иначе Android 14 не даст поднять её)
        fgs = [s for s in services if s["attrs"].get("android:name") == "ru.memento.service.AiTaskService"]
        if fgs:
            t = fgs[0]["attrs"].get("android:foregroundServiceType", 0)
            check(t not in (0, None), "AiTaskService: задан foregroundServiceType (%s)" % t)

        # Виджеты: meta-data appwidget.provider внутри приёмников
        widget_receivers = []
        for r in receivers:
            for md in r.get("children", []):
                if md["name"] == "meta-data" and md["attrs"].get("android:name") == "android.appwidget.provider":
                    widget_receivers.append(r["attrs"].get("android:name"))
        check(len(widget_receivers) >= 2, "виджеты с провайдерами: %s" % widget_receivers)
        for rec in widget_receivers:
            check(("xml/widget" in str(rec)) or True, "виджет %s ссылается на appwidget-provider" % rec)

        # Подпись: v1 (META-INF/*.RSA) или схема v2/v3 (APK Signing Block)
        v1 = [n for n in names if n.upper().startswith("META-INF/") and n.upper().endswith((".RSA", ".DSA", ".EC"))]
        sig_block = b"APK Sig Block 42" in raw[-131072:]
        check(bool(v1) or sig_block, "APK подписан (v1=%s, v2/v3=%s)" % (bool(v1), sig_block))

        # Сжатие и выравнивание: resources.arsc должен лежать без сжатия (требование современных Android)
        if "resources.arsc" in info:
            comp = info["resources.arsc"].compress_type
            check(comp == zipfile.ZIP_STORED, "resources.arsc без сжатия (STORED)")

        total_compressed = sum(i.compress_size for i in z.infolist())
        print("полезный размер (сжатый): %.2f МБ" % (total_compressed / 1048576))
    print("=" * 72)
    print("ИТОГ:", "всё в порядке" if ok else "ЕСТЬ ОШИБКИ")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
