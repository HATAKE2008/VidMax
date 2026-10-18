#!/usr/bin/env python3
"""Download Tabler icons and convert them to Android VectorDrawable XML.

Standard library only. For every requested icon:
  * downloads outline/<name>.svg (filled/heart.svg for "heart-filled"),
  * drops the Tabler background path (M0 0h24v24H0z),
  * emits a 24dp / 24x24 vector drawable where every remaining shape is a
    stroked path (fillColor #00000000, strokeColor #FF000000, width 2, round
    caps/joins). "heart-filled" is emitted as a single filled path instead.

Shapes without a native `d` (circle / ellipse / line / polyline / polygon /
rect) are converted to path data. Path data is never invented: only Tabler's
own geometry is transcribed.
"""

import os
import re
import sys
import urllib.request
import xml.etree.ElementTree as ET

CDN = "https://cdn.jsdelivr.net/npm/@tabler/icons@latest/icons"
OUT_DIR = os.path.join("app", "src", "main", "res", "drawable")

# Requested icon names (outline style unless noted).
ICONS = [
    "adjustments", "align-box-bottom", "apps", "arrow-down", "arrow-left",
    "arrow-right", "arrow-up", "arrows-exchange", "arrows-maximize",
    "arrows-shuffle", "arrows-sort", "aspect-ratio", "bell", "bookmark-plus",
    "bookmarks", "brand-github", "brand-telegram", "brightness", "calendar",
    "camera", "chart-bar", "checkbox", "check", "chevron-down", "chevron-left",
    "circle",
    "chevron-right", "circle-check", "circle-dot", "circle-plus", "clipboard",
    "clock", "color-picker", "copy", "cpu", "crop", "device-mobile-rotated",
    "dots-vertical",
    "download", "folder", "folder-open", "folder-symlink", "folders", "gauge",
    "grip-vertical", "hand-click", "headphones", "headphones-off", "heart",
    "heart-filled", "history", "info-circle", "language", "layout-grid",
    "layout-rows", "link", "link-off", "list", "lock", "lock-open", "menu-2",
    "minimize", "minus", "moon", "movie", "music", "palette", "pencil",
    "playlist", "playlist-add", "plus", "refresh", "rocket", "rotate",
    "search", "send", "server", "settings", "share", "sort-ascending-letters",
    "subtitles", "tool", "trash", "typography", "user-circle", "volume", "x",
    "zoom-in", "zoom-out", "player-skip-back", "player-skip-forward",
    "repeat", "repeat-once",
]

# Icons that are filled (fill-based drawable, no stroke).
FILLED = {"heart-filled": "filled/heart"}

# Fallbacks for names that do not exist upstream (searched once).
FALLBACK = {"align-box-bottom": "align-box-bottom-center"}

BACKGROUND = "m00h24v24h0z"


def _strip_ns(tag):
    return tag.split("}", 1)[1] if "}" in tag else tag


def _num(value, default=0.0):
    if value is None:
        return default
    return float(value)


def _fmt(v):
    if abs(v - round(v)) < 1e-9:
        return str(int(round(v)))
    return ("%.4f" % v).rstrip("0").rstrip(".")


def _to_path_data(elem):
    """Convert one SVG shape element to a path `d` string (or None)."""
    tag = _strip_ns(elem.tag)
    a = {k.split("}")[-1]: v for k, v in elem.attrib.items()}

    if tag == "path":
        return a.get("d")

    if tag == "circle":
        cx, cy, r = _num(a.get("cx")), _num(a.get("cy")), _num(a.get("r"))
        return ("M%s,%s a%s,%s 0 1 0 %s,0 a%s,%s 0 1 0 %s,0 z"
                % (_fmt(cx - r), _fmt(cy), _fmt(r), _fmt(r), _fmt(2 * r),
                   _fmt(r), _fmt(r), _fmt(-2 * r)))

    if tag == "ellipse":
        cx, cy = _num(a.get("cx")), _num(a.get("cy"))
        rx, ry = _num(a.get("rx")), _num(a.get("ry"))
        return ("M%s,%s a%s,%s 0 1 0 %s,0 a%s,%s 0 1 0 %s,0 z"
                % (_fmt(cx - rx), _fmt(cy), _fmt(rx), _fmt(ry), _fmt(2 * rx),
                   _fmt(rx), _fmt(ry), _fmt(-2 * rx)))

    if tag == "line":
        return "M%s,%s L%s,%s" % (
            _fmt(_num(a.get("x1"))), _fmt(_num(a.get("y1"))),
            _fmt(_num(a.get("x2"))), _fmt(_num(a.get("y2"))))

    if tag in ("polyline", "polygon"):
        pts = re.findall(r"-?\d*\.?\d+", a.get("points", ""))
        coords = [(_num(pts[i]), _num(pts[i + 1])) for i in range(0, len(pts) - 1, 2)]
        if not coords:
            return None
        d = "M%s,%s" % (_fmt(coords[0][0]), _fmt(coords[0][1]))
        d += "".join(" L%s,%s" % (_fmt(x), _fmt(y)) for x, y in coords[1:])
        return d + (" z" if tag == "polygon" else "")

    if tag == "rect":
        x, y = _num(a.get("x")), _num(a.get("y"))
        w, h = _num(a.get("width")), _num(a.get("height"))
        rx = _num(a.get("rx"), _num(a.get("ry")))
        ry = _num(a.get("ry"), rx)
        if rx <= 0 or ry <= 0:
            return "M%s,%s h%s v%s h%s z" % (
                _fmt(x), _fmt(y), _fmt(w), _fmt(h), _fmt(-w))
        rx = min(rx, w / 2.0)
        ry = min(ry, h / 2.0)
        return ("M%s,%s h%s a%s,%s 0 0 1 %s,%s v%s a%s,%s 0 0 1 %s,%s "
                "h%s a%s,%s 0 0 1 %s,%s v%s a%s,%s 0 0 1 %s,%s z" % (
                    _fmt(x + rx), _fmt(y), _fmt(w - 2 * rx), _fmt(rx), _fmt(ry),
                    _fmt(rx), _fmt(ry), _fmt(h - 2 * ry), _fmt(rx), _fmt(ry),
                    _fmt(-rx), _fmt(ry), _fmt(-(w - 2 * rx)), _fmt(rx), _fmt(ry),
                    _fmt(-rx), _fmt(-ry), _fmt(-(h - 2 * ry)), _fmt(rx), _fmt(ry),
                    _fmt(rx), _fmt(-ry)))

    return None


def _download(url):
    req = urllib.request.Request(url, headers={"User-Agent": "vidmax-icon-sync"})
    with urllib.request.urlopen(req, timeout=30) as resp:
        return resp.read().decode("utf-8")


def _build_paths(svg_text):
    """Return the list of path `d` strings, skipping the background path."""
    root = ET.fromstring(svg_text)
    paths = []
    for elem in root.iter():
        if _strip_ns(elem.tag) not in ("path", "circle", "ellipse", "line",
                                       "polyline", "polygon", "rect"):
            continue
        d = _to_path_data(elem)
        if not d:
            continue
        if re.sub(r"\s+", "", d).lower() == BACKGROUND:
            continue
        paths.append(d)
    return paths


def _write_drawable(out_name, paths, filled):
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        '    android:width="24dp"',
        '    android:height="24dp"',
        '    android:viewportWidth="24"',
        '    android:viewportHeight="24">',
    ]
    for d in paths:
        if filled:
            lines.append('    <path')
            lines.append('        android:fillColor="#FF000000"')
            lines.append('        android:pathData="%s" />' % d)
        else:
            lines.append('    <path')
            lines.append('        android:fillColor="#00000000"')
            lines.append('        android:strokeColor="#FF000000"')
            lines.append('        android:strokeWidth="2"')
            lines.append('        android:strokeLineCap="round"')
            lines.append('        android:strokeLineJoin="round"')
            lines.append('        android:pathData="%s" />' % d)
    lines.append('</vector>')
    lines.append('')
    with open(os.path.join(OUT_DIR, out_name + ".xml"), "w", encoding="utf-8") as f:
        f.write("\n".join(lines))


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    missing, failed, substituted, written = [], [], [], 0

    for name in ICONS:
        filled = name in FILLED
        source = FILLED.get(name, "outline/" + name)
        requested = name

        try:
            svg = _download("%s/%s.svg" % (CDN, source))
        except Exception:
            # Retry once with the closest existing Tabler name.
            alt = FALLBACK.get(name)
            if alt:
                try:
                    svg = _download("%s/outline/%s.svg" % (CDN, alt))
                    substituted.append("%s -> %s" % (name, alt))
                    source = "outline/" + alt
                except Exception:
                    print("MISSING %s" % name)
                    missing.append(name)
                    continue
            else:
                print("MISSING %s" % name)
                missing.append(name)
                continue

        try:
            paths = _build_paths(svg)
        except Exception as exc:  # noqa: BLE001
            print("FAILED %s (%s)" % (name, exc))
            failed.append(name)
            continue

        if not paths:
            print("FAILED %s (no convertible paths)" % name)
            failed.append(name)
            continue

        out_name = "ic_tabler_" + name.replace("-", "_")
        _write_drawable(out_name, paths, filled)
        written += 1
        flag = " [filled]" if filled else ""
        sub = ""
        if substituted and substituted[-1].startswith(name + " "):
            sub = " (%s)" % source
        print("OK %s%s%s" % (out_name, flag, sub))

    print("\n--- summary ---")
    print("written: %d" % written)
    print("substituted: %s" % (", ".join(substituted) if substituted else "none"))
    print("missing: %s" % (", ".join(missing) if missing else "none"))
    print("failed: %s" % (", ".join(failed) if failed else "none"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
