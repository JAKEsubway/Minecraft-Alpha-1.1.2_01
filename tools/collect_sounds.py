#!/usr/bin/env python3
"""Collect Minecraft Alpha sound files into an APK assets folder.

usage: collect_sounds.py SOURCE OUT_DIR

SOURCE may be any of:
  - a .minecraft folder (uses resources/ or assets/ inside it)
  - a resources folder (contains sound/, newsound/, music/ ...)
  - an assets folder from the current launcher (indexes/pre-1.6.json or legacy.json + objects/)
  - assets/virtual/legacy from older launchers
Writes OUT_DIR/mcres/<path> and OUT_DIR/mcres/index.txt ("size path" per line).
"""
import json, os, shutil, sys

PREFIXES = ("sound/", "newsound/", "music/", "newmusic/", "streaming/")  # what Alpha loads (sound3/ is for later versions)
EXTS = (".ogg", ".mus", ".wav")

def wanted(path):
    p = path.replace("\\", "/")
    return p.startswith(PREFIXES) and p.lower().endswith(EXTS)

def from_tree(root):
    for d, _, files in os.walk(root):
        for f in files:
            full = os.path.join(d, f)
            rel = os.path.relpath(full, root).replace(os.sep, "/")
            if wanted(rel):
                yield rel, full

def from_index(assets):
    for name in ("pre-1.6.json", "legacy.json"):
        idx = os.path.join(assets, "indexes", name)
        if os.path.isfile(idx):
            objs = json.load(open(idx)).get("objects", {})
            for rel, o in objs.items():
                h = o["hash"]
                full = os.path.join(assets, "objects", h[:2], h)
                if wanted(rel) and os.path.isfile(full):
                    yield rel, full
            return
    return

def find(src):
    src = os.path.expanduser(src)
    cands = [src, os.path.join(src, "resources"), os.path.join(src, "assets", "virtual", "legacy"),
             os.path.join(src, "virtual", "legacy")]
    for c in cands:
        if os.path.isdir(c) and any(os.path.isdir(os.path.join(c, p.rstrip("/"))) for p in PREFIXES):
            return list(from_tree(c)), c
    for c in (src, os.path.join(src, "assets")):
        got = list(from_index(c))
        if got:
            return got, c
    return [], None

def main():
    src, out = sys.argv[1], sys.argv[2]
    files, where = find(src)
    if not files:
        sys.exit("no Minecraft sound files found in " + src +
                 " (launch Alpha once with the official launcher, then pass your .minecraft folder)")
    dest = os.path.join(out, "mcres")
    shutil.rmtree(dest, ignore_errors=True)
    lines, total = [], 0
    for rel, full in sorted(files):
        d = os.path.join(dest, rel)
        os.makedirs(os.path.dirname(d), exist_ok=True)
        shutil.copyfile(full, d)
        size = os.path.getsize(d)
        total += size
        lines.append("%d %s" % (size, rel))
    with open(os.path.join(dest, "index.txt"), "w") as f:
        f.write("\n".join(lines) + "\n")
    print("packed %d sound files (%.1f MB) from %s" % (len(lines), total / 1048576.0, where))

if __name__ == "__main__":
    main()
