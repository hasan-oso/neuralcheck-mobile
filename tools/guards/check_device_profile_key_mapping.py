#!/usr/bin/env python3
"""Standalone reference implementation + test harness for the
DeviceProfileKey ProGuard-name guard (order 2026-09-23، القرار ٧٣، البند
١ب; semantics changed 2026-10-09, see below). The Gradle-wired guard that
actually runs during a real `assembleLaunchRelease` reimplements this SAME
comparison natively in Kotlin inside app/build.gradle.kts
(`checkDeviceProfileKeyMapping` task) — no Python dependency at Android
build time. This script exists so the guard's LOGIC can be proven correct
without invoking Gradle/R8/building an APK at all — see the fixtures under
tools/guards/testdata/ and the results the task report cites.

SEMANTICS SINCE 2026-10-09 (user decision 5: "the R8 names (a..f) in
deviceProfiles are the first step in fixing the device key"): the guard used
to FREEZE R8's letters (primary=a ... layerE_sensorSig=f) because the server
read `deviceProfileKey.a`. DeviceProfileKey now carries @SerializedName with
the source names, so the release must emit `primary`, `layerA_keystore`, ...
`layerE_sensorSig`. The expected file now lists each source name against
ITSELF, and the guard rejects any mapping in which R8 gave a field another
name. A field R8 kept has no line in mapping.txt (see proguard_mapping.py), so
"class present, no line for the field" passes and "class absent" is rejected.

Usage:
    python3 tools/guards/check_device_profile_key_mapping.py \
        app/guard/deviceProfileKeyMapping.expected.properties \
        <path-to-a-mapping.txt>

Exit 0 and "OK" on a match. Exit 1 and a per-field diff on any mismatch —
this is the rejection this order requires: "يرفض البناء إن اختلف اسم أيّ
حقل".
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from proguard_mapping import find_class_field_mapping  # noqa: E402

CLASS_NAME = "net.neuralcheck.util.DeviceProfileKey"


def load_expected(properties_path: str) -> dict[str, str]:
    expected: dict[str, str] = {}
    for line in Path(properties_path).read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        key, _, value = line.partition("=")
        expected[key.strip()] = value.strip()
    return expected


def main(argv: list[str]) -> int:
    if len(argv) != 3:
        print(f"usage: {argv[0]} <expected.properties> <mapping.txt>", file=sys.stderr)
        return 2
    expected_path, mapping_path = argv[1], argv[2]

    expected = load_expected(expected_path)
    actual = find_class_field_mapping(mapping_path, CLASS_NAME)

    if actual is None:
        print(f"REJECTED: class {CLASS_NAME} not found in {mapping_path}", file=sys.stderr)
        return 1

    problems = []
    for field_name, expected_name in expected.items():
        # No line under the class block = R8 kept the field's own name.
        actual_name = actual.get(field_name, field_name)
        if actual_name != expected_name:
            problems.append(f"  {field_name}: expected -> {expected_name}, actual -> {actual_name}")
    extra = set(actual) - set(expected)
    for field_name in sorted(extra):
        problems.append(f"  {field_name}: unexpected field not in {expected_path} (actual -> {actual[field_name]})")

    if problems:
        print(f"REJECTED: {CLASS_NAME} name mismatch against {expected_path}:", file=sys.stderr)
        for p in problems:
            print(p, file=sys.stderr)
        return 1

    print(f"OK: all {len(expected)} {CLASS_NAME} field names match {expected_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
