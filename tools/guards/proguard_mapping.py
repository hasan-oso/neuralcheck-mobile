#!/usr/bin/env python3
"""Shared ProGuard/R8 mapping.txt field-rename parser.

Used by both extract_device_profile_key_mapping.py (a diagnostic printer since
2026-10-09; it used to generate app/guard/deviceProfileKeyMapping.expected.properties
— see that file's own header) and check_device_profile_key_mapping.py (the
standalone CLI used to test the guard's logic against arbitrary mapping.txt
files without invoking Gradle — see that file's own header). One parser, not
two hand-copies (rule 24: a literal a distinction is built on lives in one
exported place).

mapping.txt shape (R8/ProGuard):
    net.neuralcheck.util.DeviceProfileKey -> cf.i:
    # {"id":"sourceFile","fileName":"DeviceFingerprint.kt"}
        java.lang.String primary -> a
        java.lang.String layerA_keystore -> b
        ...
        10:25:void <init>(java.lang.String,...):49:49 -> <init>

A class header line has no leading whitespace and ends "-> <newName>:".
A field-rename line is 4-space indented, "<type> <fieldName> -> <newName>",
no parentheses and no leading "N:M:" line-number prefix (which method
lines carry). A method-rename line and a class header line are neither
matched nor treated as errors — simply skipped while scanning a class
block, the same way a human reading the file would.

A field whose name R8 KEPT has NO line in the class block. Measured on the
release mapping of 62507f4 (2026-10-07): the kept class
net.neuralcheck.data.remote.model.DeviceInfo lists only `deviceProfileKey ->
deviceProfileKey` (its TYPE was renamed, so the line is needed) and none of its
String fields, and the whole file has 0 lines of the form
`java.lang.String x -> x`. So "present class block, no line for the field" means
"kept under its own name" — and only that: it cannot tell a kept field from one
R8 removed (the usage.txt of the same build is where removals are listed).
"""
from __future__ import annotations

import re

_CLASS_HEADER_RE = re.compile(r'^([\w.$]+)\s*->\s*[\w.$]+:\s*$')
_FIELD_RE = re.compile(r'^\s{4}[\w.\[\]]+\s+(\w+)\s*->\s*([\w$]+)\s*$')


def find_class_field_mapping(mapping_path: str, class_name: str) -> dict[str, str] | None:
    """Returns None when `class_name` has no header line in mapping_path;
    otherwise {declaredFieldName: newName} for every field-rename line found
    directly under that header's block — an EMPTY dict when the class is
    present and no field line follows it (every field kept its name).
    The two outcomes are different on purpose: the caller must not read
    "class absent" as "nothing renamed" (rule 22)."""
    fields: dict[str, str] = {}
    found = False
    in_class = False
    with open(mapping_path, encoding='utf-8') as f:
        for line in f:
            if line.startswith('#'):
                continue
            header = _CLASS_HEADER_RE.match(line)
            if header:
                in_class = header.group(1) == class_name
                if in_class:
                    found = True
                continue
            if not in_class:
                continue
            m = _FIELD_RE.match(line)
            if m:
                field_name, new_name = m.groups()
                fields[field_name] = new_name
    return fields if found else None


def extract_class_field_mapping(mapping_path: str, class_name: str) -> dict[str, str]:
    """Kept for the diagnostic printer: {declaredFieldName: newName}, empty dict
    when the class is absent OR present with no field line (use
    find_class_field_mapping to tell those apart)."""
    return find_class_field_mapping(mapping_path, class_name) or {}
