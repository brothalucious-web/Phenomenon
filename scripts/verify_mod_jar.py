#!/usr/bin/env python3
"""Fail the CI build if the remapped mod JAR has a broken Mixin refmap link."""
import json
import sys
import zipfile
from pathlib import Path

jars = sorted(Path("build/libs").glob("phenomenon-*.jar"))
jars = [path for path in jars if not path.name.endswith("-sources.jar")]
if not jars:
    raise SystemExit("ERROR: no remapped Phenomenon JAR found in build/libs")

for jar in jars:
    with zipfile.ZipFile(jar) as archive:
        names = set(archive.namelist())
        if "phenomenon.mixins.json" not in names:
            raise SystemExit(f"ERROR: {jar} is missing phenomenon.mixins.json")
        config = json.loads(archive.read("phenomenon.mixins.json"))
        refmap = config.get("refmap")
        if not refmap:
            raise SystemExit(f"ERROR: {jar} mixin config does not declare a refmap")
        if refmap not in names:
            raise SystemExit(
                f"ERROR: {jar} declares refmap {refmap!r}, but that file is absent. "
                f"Available refmaps: {[n for n in names if 'refmap' in n.lower()]}"
            )
        mappings = json.loads(archive.read(refmap))
        class_mappings = mappings.get("mappings", {})
        mixin_mapping = class_mappings.get("com/phenomenon/mixin/client/KeyboardInputMixin")
        if not mixin_mapping or "tick" not in mixin_mapping:
            raise SystemExit(
                f"ERROR: {refmap} does not contain the KeyboardInputMixin tick mapping"
            )
        print(f"Verified {jar}: {refmap} exists and contains the keyboard mixin mapping")
