#!/usr/bin/env python3
"""Rasterises the Text Hub launcher mark into the legacy mipmap densities.

The design is identical to res/drawable/ic_launcher_foreground.xml: a teal padlock whose body is
a block of text lines, on a dark slate plate. Pure stdlib (zlib + struct), 3x3 supersampled.
"""
import struct
import zlib
from pathlib import Path

RES = Path(__file__).resolve().parents[1] / "app/src/main/res"
DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}

# design canvas
UNIT = 108.0


def lerp(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def rr_inside(x, y, x0, y0, x1, y1, r):
    """True when (x, y) is inside a rounded rectangle."""
    cx = min(max(x, x0 + r), x1 - r)
    cy = min(max(y, y0 + r), y1 - r)
    if x0 + r <= x <= x1 - r and y0 <= y <= y1:
        return True
    if y0 + r <= y <= y1 - r and x0 <= x <= x1:
        return True
    dx, dy = x - cx, y - cy
    return dx * dx + dy * dy <= r * r


def ring_arch(x, y, cx, cy, outer, inner, limit_y):
    if y > limit_y:
        return False
    dx, dy = x - cx, y - cy
    d2 = dx * dx + dy * dy
    return inner * inner <= d2 <= outer * outer


def sample(x, y, round_icon):
    """Returns (r, g, b, a) for a point in design space."""
    # --- background
    if round_icon:
        bg_hit = (x - 54.0) ** 2 + (y - 54.0) ** 2 <= 54.0 ** 2
    else:
        bg_hit = 0.0 <= x <= UNIT and 0.0 <= y <= UNIT and rr_inside(x, y, 0, 0, UNIT, UNIT, 26)
    if not bg_hit:
        return (0, 0, 0, 0)

    t = (x + y) / (2 * UNIT)
    bg = lerp((0x12, 0x1A, 0x22), (0x0B, 0x0F, 0x14), t / 0.55) if t < 0.55 \
        else lerp((0x0B, 0x0F, 0x14), (0x07, 0x21, 0x1F), (t - 0.55) / 0.45)
    color = bg

    # --- shackle, body, text lines (same geometry as the vector drawable)
    if ring_arch(x, y, 54, 52, 15, 9, 52):
        color = (0x2D, 0xD4, 0xBF)
    if rr_inside(x, y, 32, 48, 76, 82, 9):
        color = lerp((0x1A, 0xC9, 0xB6), (0x0E, 0x9C, 0x8C), (y - 48) / 34)
        if (41 <= x <= 63 and 57 <= y <= 61) or (41 <= x <= 57 and 65 <= y <= 69) \
                or (41 <= x <= 63 and 73 <= y <= 77):
            color = (0x08, 0x20, 0x1D)
    return (int(color[0]), int(color[1]), int(color[2]), 255)


def render(size, round_icon):
    scale = UNIT / size
    offsets = (0.17, 0.5, 0.83)
    rows = []
    for py in range(size):
        row = bytearray()
        for px in range(size):
            r = g = b = a = 0
            for oy in offsets:
                for ox in offsets:
                    sr, sg, sb, sa = sample((px + ox) * scale, (py + oy) * scale, round_icon)
                    r += sr * sa
                    g += sg * sa
                    b += sb * sa
                    a += sa
            if a == 0:
                row += bytes((0, 0, 0, 0))
            else:
                # r/g/b are premultiplied sums, a is the coverage sum: undo both and clamp.
                clamp = lambda v: 0 if v < 0 else (255 if v > 255 else int(round(v)))
                row += bytes((clamp(r / a), clamp(g / a), clamp(b / a), clamp(a / 9)))
        rows.append(bytes(row))
    return rows


def write_png(path, size, rows):
    raw = b"".join(b"\x00" + row for row in rows)

    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data +
                struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9))
    png += chunk(b"IEND", b"")
    path.write_bytes(png)


for density, size in DENSITIES.items():
    folder = RES / f"mipmap-{density}"
    folder.mkdir(parents=True, exist_ok=True)
    write_png(folder / "ic_launcher.png", size, render(size, False))
    write_png(folder / "ic_launcher_round.png", size, render(size, True))
    print(f"{density}: {size}x{size} written")
