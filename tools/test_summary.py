#!/usr/bin/env python3
"""Сводка юнит-тестов из отчётов Gradle — печатается в логе сборки."""
import glob
import sys
import xml.etree.ElementTree as ET

tests = fails = errors = skipped = 0
bad = []
for f in glob.glob("app/build/test-results/**/*.xml", recursive=True):
    root = ET.parse(f).getroot()
    tests += int(root.get("tests", 0))
    fails += int(root.get("failures", 0))
    errors += int(root.get("errors", 0))
    skipped += int(root.get("skipped", 0))
    for case in root.iter("testcase"):
        if case.find("failure") is not None or case.find("error") is not None:
            bad.append(f"{case.get('classname')} :: {case.get('name')}")

print(f"тестов: {tests}, упало: {fails}, ошибок: {errors}, пропущено: {skipped}")
for b in bad[:25]:
    print("  ПРОВАЛ:", b)
if tests == 0:
    print("ОТЧЁТОВ НЕТ — тесты не запускались")
    sys.exit(1)
