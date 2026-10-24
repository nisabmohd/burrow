"""Converts one Phosphor icon into Burrow's three vector drawables (docs/ICONS.md):
ic_<name>_regular.xml, ic_<name>_fill.xml, ic_<name>_duotone.xml — paths only, viewport 256, white,
duotone's light layer as fillAlpha.

usage: python3 tools/phosphor_icon.py <meaning_name> <PhosphorName> <path to @phosphor-icons/react>
e.g.   python3 tools/phosphor_icon.py hand_tap HandTap ~/some/node_modules/@phosphor-icons/react
Reads dist/defs/<PhosphorName>.es.js (the path data shipped with @phosphor-icons/react, MIT).
"""
import os, re, sys

OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "app", "src", "main", "res", "drawable")

def weights(js):
    out = {}
    for m in re.finditer(r'\[\s*"(\w+)",(.*?)\n  \]', js, re.S):
        name, body = m.group(1), m.group(2)
        paths = []
        for p in re.finditer(r'"path",\s*\{\s*d:\s*"([^"]+)"(?:,\s*opacity:\s*"([\d.]+)")?', body):
            paths.append((p.group(1), p.group(2)))
        out[name] = paths
    return out

def drawable(name, weight, paths):
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        f'<!-- Phosphor Icons ({weight}), MIT. Tint it: Icon(painterResource(R.drawable.ic_{name}_{weight}), tint = …) -->',
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        '    android:width="24dp" android:height="24dp" android:viewportWidth="256" android:viewportHeight="256">',
    ]
    for d, alpha in paths:
        a = f' android:fillAlpha="{alpha}"' if alpha else ""
        lines.append(f'    <path android:fillColor="@android:color/white"{a} android:pathData="{d}" />')
    lines.append("</vector>")
    return "\n".join(lines) + "\n"

def main(name, phosphor, pkg):
    js = open(os.path.join(os.path.expanduser(pkg), "dist", "defs", f"{phosphor}.es.js")).read()
    w = weights(js)
    for weight in ("regular", "fill", "duotone"):
        if not w.get(weight):
            sys.exit(f"{phosphor}: no {weight} paths")
        path = os.path.join(OUT, f"ic_{name}_{weight}.xml")
        open(path, "w").write(drawable(name, weight, w[weight]))
        print("wrote", os.path.relpath(path))

if __name__ == "__main__":
    main(*sys.argv[1:4])
