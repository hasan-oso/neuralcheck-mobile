#!/usr/bin/env python3
"""Diagnostic printer — order 2026-09-23 (القرار ٧٣، البند ١ب), semantics changed 2026-10-09.

Until 2026-10-09 this tool GENERATED app/guard/deviceProfileKeyMapping.expected.properties from a real
R8 mapping.txt (a snapshot of R8's letters a..f). That file now states the SOURCE names, each field
against itself (user decision 2026-10-09, item 5: the release must emit `primary` ... `layerE_sensorSig`),
and must NOT be regenerated from a mapping — that would write back whatever R8 did, which is exactly what
the guard exists to refuse. So this tool no longer writes anything: it prints what a mapping.txt says
about net.neuralcheck.util.DeviceProfileKey, for a human looking at a build.

Usage:
    python3 tools/guards/extract_device_profile_key_mapping.py <path-to-mapping.txt>

Output: whether the class is in the file, its (renamed) name, and every field line under it. A field with
NO line was kept under its own name (see proguard_mapping.py for how that was measured).
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from proguard_mapping import find_class_field_mapping  # noqa: E402

CLASS_NAME = "net.neuralcheck.util.DeviceProfileKey"


def main(argv: list[str]) -> int:
    if len(argv) != 2:
        print(f"usage: {argv[0]} <mapping.txt>", file=sys.stderr)
        return 2
    mapping_path = argv[1]

    fields = find_class_field_mapping(mapping_path, CLASS_NAME)
    if fields is None:
        print(f"{CLASS_NAME}: NOT in {mapping_path}")
        return 1

    header = re.compile(r'^' + re.escape(CLASS_NAME) + r'\s*->\s*([\w.$]+):\s*$')
    renamed_to = None
    with open(mapping_path, encoding='utf-8') as f:
        for line in f:
            m = header.match(line)
            if m:
                renamed_to = m.group(1)
                break
    print(f"{CLASS_NAME} -> {renamed_to}")
    if not fields:
        print("  no field line: every field kept its own name")
    for field_name, new_name in fields.items():
        print(f"  {field_name} -> {new_name}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
