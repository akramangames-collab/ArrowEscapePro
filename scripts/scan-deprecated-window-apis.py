#!/usr/bin/env python3
"""Trace Android window API references inside resolved dependency AAR bytecode."""
import io
import json
import os
import subprocess
import tempfile
import zipfile
from pathlib import Path

TOKENS = (
    b"layoutInDisplayCutoutMode",
    b"setStatusBarColor",
    b"setNavigationBarColor",
)
root = Path.home() / ".gradle" / "caches" / "modules-2" / "files-2.1"
hits = []
for aar in root.rglob("*.aar"):
    try:
        with zipfile.ZipFile(aar) as z:
            if "classes.jar" not in z.namelist():
                continue
            jar_bytes = z.read("classes.jar")
        with zipfile.ZipFile(io.BytesIO(jar_bytes)) as jar:
            for name in jar.namelist():
                if not name.endswith(".class"):
                    continue
                data = jar.read(name)
                found = [t.decode() for t in TOKENS if t in data]
                if found:
                    parts = aar.parts
                    # Cache layout ends in group/artifact/version/hash/file.aar.
                    coord = "/".join(parts[-5:-2]) if len(parts) >= 5 else str(aar)
                    class_name = name[:-6].replace("/", ".")
                    item = {
                        "coordinate": coord,
                        "aar": aar.name,
                        "class": class_name,
                        "references": found,
                    }
                    if "layoutInDisplayCutoutMode" in found:
                        try:
                            with tempfile.NamedTemporaryFile(suffix=".jar") as tmp:
                                tmp.write(jar_bytes)
                                tmp.flush()
                                p = subprocess.run(
                                    ["javap", "-classpath", tmp.name, "-c", "-p", class_name],
                                    text=True, capture_output=True, timeout=15
                                )
                                if p.returncode == 0:
                                    item["javap"] = p.stdout
                        except (OSError, subprocess.SubprocessError):
                            pass
                    hits.append(item)
    except (OSError, zipfile.BadZipFile, KeyError):
        pass

hits.sort(key=lambda x: (x["coordinate"], x["class"]))
out = Path(os.environ.get("WINDOW_API_SCAN_OUT", "deprecated-window-api-inputs.json"))
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text(json.dumps(hits, indent=2) + "\n")
print(json.dumps(hits, indent=2))
