#!/usr/bin/env python3
"""Builds the Text Hub launcher icon from one supplied source image.

    python3 tools/make_app_icon.py [source.png]

The source image is the app icon as the designer supplied it. It may be:

  * a full-bleed design (the artwork sits on a painted background), or
  * a design with transparent margins already.

This script turns either into everything Android needs, without distorting the artwork:

  * `res/drawable-nodpi/ic_launcher_background.png` - the adaptive-icon background: a clean
    reconstruction of the source's background (flat or softly gradient), at the 108 dp canvas.
  * `res/drawable-nodpi/ic_launcher_foreground.png` - the **mark** (the artwork with its
    background knocked out), scaled to the 72 dp safe zone, on transparency.
  * `res/drawable-nodpi/ic_launcher_monochrome.png` - the same mark as one colour, for
    Android 13+ themed icons.
  * `res/mipmap-*/ic_launcher.png` + `ic_launcher_round.png` - the legacy icons for API < 26,
    composited from the two layers, with rounded corners (square) and a circle (round).

Nothing is stretched: every scale is a proportional fit. Pure stdlib (zlib + struct) - no Pillow,
no network.

The source image stays in the repository at `tools/assets/app-icon-source.png`, so the icon is
reproducible: replace that file and re-run this script to ship a different icon.
"""
import os
import struct
import sys
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
SOURCE = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "tools/assets/app-icon-source.png"

# Adaptive-icon canvas: 108 dp. xxxhdpi is 4x, so 432 px is the natural raster size.
CANVAS = 432
# The safe zone every launcher keeps: the central 72 dp of the 108 dp canvas.
SAFE = 288  # 72 / 108 * 432
# How much of the 108 dp canvas the artwork's bounding box should span. Smaller than the full
# safe zone so the mark sits with comfortable breathing room on its plate instead of filling it.
MARK_FRACTION = 0.58

DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}

# The vector layers this script replaces; leaving them next to the PNGs of the same name would be a
# duplicate resource.
SUPERSEDED_VECTORS = (
    "ic_launcher_background.xml",
    "ic_launcher_foreground.xml",
    "ic_launcher_monochrome.xml",
)


# --------------------------------------------------------------------- PNG decoding

def read_png(path):
    """Minimal PNG reader: 8/16-bit greyscale, RGB, palette, with or without alpha."""
    data = path.read_bytes()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("not a PNG: " + str(path))
    pos = 8
    idat = bytearray()
    palette = None
    while pos < len(data):
        (length,) = struct.unpack(">I", data[pos:pos + 4])
        tag = data[pos + 4:pos + 8]
        chunk = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if tag == b"IHDR":
            width, height, depth, colour, compression, filt, interlace = struct.unpack(">IIBBBBB", chunk)
            if compression != 0 or interlace != 0:
                raise ValueError("unsupported PNG (compression or interlacing)")
        elif tag == b"PLTE":
            palette = [tuple(chunk[i:i + 3]) for i in range(0, len(chunk), 3)]
        elif tag == b"IDAT":
            idat += chunk
        elif tag == b"IEND":
            break
    raw = zlib.decompress(bytes(idat))
    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[colour]
    if depth not in (8, 16):
        raise ValueError("unsupported bit depth " + str(depth))
    step = channels * (depth // 8)
    stride = width * step
    out = bytearray()
    previous = bytearray(stride)
    sp = 0
    for _ in range(height):
        f = raw[sp]
        sp += 1
        line = bytearray(raw[sp:sp + stride])
        sp += stride
        if f == 1:
            for i in range(step, stride):
                line[i] = (line[i] + line[i - step]) & 0xFF
        elif f == 2:
            for i in range(stride):
                line[i] = (line[i] + previous[i]) & 0xFF
        elif f == 3:
            for i in range(stride):
                left = line[i - step] if i >= step else 0
                line[i] = (line[i] + ((left + previous[i]) >> 1)) & 0xFF
        elif f == 4:
            for i in range(stride):
                a = line[i - step] if i >= step else 0
                b = previous[i]
                c = previous[i - step] if i >= step else 0
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                best = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + best) & 0xFF
        elif f != 0:
            raise ValueError("unsupported PNG filter " + str(f))
        out += line
        previous = line

    pixels = []
    for y in range(height):
        row = []
        base = y * stride
        for x in range(width):
            off = base + x * step
            if depth == 16:
                # 16-bit samples: keep the high byte, which is all an icon needs.
                vals = [out[off + i * 2] for i in range(channels)]
            else:
                vals = [out[off + i] for i in range(channels)]
            if colour == 3:
                rgb = palette[vals[0]]
                row.append((rgb[0], rgb[1], rgb[2], 255))
            elif colour in (0, 4):
                g = vals[0]
                a = vals[1] if channels == 2 else 255
                row.append((g, g, g, a))
            elif colour in (2, 6):
                a = vals[3] if channels == 4 else 255
                row.append((vals[0], vals[1], vals[2], a))
            else:
                raise ValueError("unsupported colour type")
        pixels.append(row)
    return width, height, pixels


# --------------------------------------------------------------------- PNG writing

def write_png(path, width, height, rows):
    """rows: list of rows, each a bytes object of width*4 RGBA samples."""
    raw = b"".join(b"\x00" + row for row in rows)

    def chunk(tag, payload):
        return (struct.pack(">I", len(payload)) + tag + payload +
                struct.pack(">I", zlib.crc32(tag + payload) & 0xFFFFFFFF))

    blob = b"\x89PNG\r\n\x1a\n"
    blob += chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    blob += chunk(b"IDAT", zlib.compress(raw, 9))
    blob += chunk(b"IEND", b"")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(blob)


# ----------------------------------------------------------------------- helpers

def median(values):
    s = sorted(values)
    return s[len(s) // 2]


def clamp(v):
    return 0 if v < 0 else (255 if v > 255 else int(v))


def smoothstep(a, b, x):
    if x <= a:
        return 0.0
    if x >= b:
        return 1.0
    t = (x - a) / (b - a)
    return t * t * (3.0 - 2.0 * t)


# ------------------------------------------------------------------------ analysis

def analyze(pixels, w, h):
    """Decide how to build the layers from the supplied image.

    Returns either ("transparent_margins", None) when the source already has clear margins, or
    ("full_bleed", info) where info carries the background reference colour, the background
    gradient endpoints, the matte function, and the mark's bounding square.
    """
    opaque = [(x, y) for y in range(h) for x in range(w) if pixels[y][x][3] > 200]
    if not opaque:
        raise ValueError("the source image is entirely transparent")

    # A source that already has clear transparent margins (>= 20 % of the pixels transparent)
    # keeps its own alpha; a full-bleed source needs its painted background separated from the
    # mark. This single rule covers both without trusting any one corner.
    transparent_count = sum(
        1 for y in range(h) for x in range(w) if pixels[y][x][3] <= 200
    )
    if transparent_count / float(w * h) >= 0.20:
        return "transparent_margins", None

    corners = []
    for (cx, cy) in ((0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)):
        for y in range(max(0, cy - 2), min(h, cy + 3)):
            for x in range(max(0, cx - 2), min(w, cx + 3)):
                if pixels[y][x][3] > 200:
                    corners.append((x, y))

    # Full-bleed: separate the painted background from the mark by luminance contrast. The
    # background reference is the median colour of the four corner blocks (the corners are
    # background almost by definition in an app icon).
    ref = tuple(
        median([pixels[y][x][i] for (x, y) in corners]) for i in range(3)
    )
    bg_light = sum(ref) / 3.0 > 128.0

    if not bg_light:
        # Dark background: the mark is the brighter part -> key on the brightest channel.
        key = lambda p: max(p[0], p[1], p[2])
        xs = sorted(key(pixels[y][x][:3]) for (x, y) in opaque)
        ceiling = xs[int(len(xs) * 0.79)]          # ~just below the minority (mark) pixels
        t0 = ceiling + 16.0
        t1 = ceiling + 55.0

        def matte(p):
            return smoothstep(t0, t1, key(p))
    else:
        # Light background: the mark is the darker part -> key on the darkest channel.
        key = lambda p: min(p[0], p[1], p[2])
        xs = sorted(key(pixels[y][x][:3]) for (x, y) in opaque)
        floor = xs[int(len(xs) * 0.21)]
        t0 = floor - 55.0
        t1 = floor - 16.0

        def matte(p):
            return smoothstep(t1, t0, key(p))

    # Soft vertical gradient: the mediaan colour of the top rows and the bottom rows.
    def row_median(y):
        return tuple(
            median([pixels[y][x][i] for x in range(0, w) if pixels[y][x][3] > 200]) for i in range(3)
        )
    top = tuple(sum([row_median(y)[i] for y in range(0, min(4, h))]) // min(4, h) for i in range(3))
    bottom = tuple(sum([row_median(y)[i] for y in range(max(0, h - 4), h)]) // max(1, min(4, h)) for i in range(3))

    # The mark's extent, from the matte.
    minx = miny = 10 ** 9
    maxx = maxy = -1
    for (x, y) in opaque:
        if matte(pixels[y][x][:3]) > 0.5:
            minx = min(minx, x)
            maxx = max(maxx, x)
            miny = min(miny, y)
            maxy = max(maxy, y)
    if maxx < minx:
        raise ValueError("no contrasting mark found in the image")
    mw = maxx - minx + 1
    mh = maxy - miny + 1
    cx = (minx + maxx) / 2.0
    cy = (miny + maxy) / 2.0
    # A square crop around the mark with breathing room, clamped to the image.
    side = max(mw, mh) * 1.28
    x0 = cx - side / 2.0
    y0 = cy - side / 2.0
    x0 = min(max(x0, 0.0), w - side)
    y0 = min(max(y0, 0.0), h - side)
    side = min(side, float(w), float(h))

    return "full_bleed", {
        "ref": ref,
        "top": top,
        "bottom": bottom,
        "matte": matte,
        "crop": (x0, y0, side),
    }


# ------------------------------------------------------------------ layer builders

def build_background(kind, info, px, w, h):
    """The adaptive background: a clean copy of the source's background, full canvas."""
    if kind == "transparent_margins":
        # No painted background in the source: fill the adaptive background with a flat slate that
        # matches Text Hub's dark surface, so a launcher that shows the background layer alone
        # (e.g. before the artwork is loaded) still looks coherent.
        return _flat(21, 32, 48, CANVAS)
    ref, top, bottom = info["ref"], info["top"], info["bottom"]
    rows = []
    for y in range(CANVAS):
        t = y / (CANVAS - 1)
        row = bytearray()
        for x in range(CANVAS):
            r = top[0] + (bottom[0] - top[0]) * t
            g = top[1] + (bottom[1] - top[1]) * t
            b = top[2] + (bottom[2] - top[2]) * t
            row += bytes((clamp(r + 0.5), clamp(g + 0.5), clamp(b + 0.5), 255))
        rows.append(bytes(row))
    return rows


def _bilinear(px, w, h, fx, fy):
    """Straight (non-premultiplied) bilinear sample of an RGBA pixel grid."""
    if fx < 0 or fy < 0 or fx >= w or fy >= h:
        return (0, 0, 0, 0)
    x0i = min(int(fx), w - 2)
    y0i = min(int(fy), h - 2)
    tx = fx - x0i
    ty = fy - y0i
    p00 = px[y0i][x0i]
    p01 = px[y0i][x0i + 1]
    p10 = px[y0i + 1][x0i]
    p11 = px[y0i + 1][x0i + 1]

    def mix(a, b, t):
        return a + (b - a) * t
    return tuple(mix(mix(p00[i], p01[i], tx), mix(p10[i], p11[i], tx), ty) for i in range(4))


def build_foreground(kind, info, px, w, h):
    """The mark, background knocked out, scaled to the 72 dp safe zone, on transparency."""
    if kind == "transparent_margins":
        # The source already carries its own transparency: fit the whole image into the mark box
        # and keep its alpha as-is (no matte - there is no painted background to knock out).
        target = CANVAS * MARK_FRACTION
        scale = target / max(w, h)
        off_x = (CANVAS - w * scale) / 2.0
        off_y = (CANVAS - h * scale) / 2.0

        def at(dx, dy):
            fx = (dx - off_x) / scale
            fy = (dy - off_y) / scale
            if fx < 0 or fy < 0 or fx >= w or fy >= h:
                return (0, 0, 0, 0)
            return _bilinear(px, w, h, fx, fy)

        def identity(_p):
            return 1.0

        fg = _rasterize(at, identity, monochrome=False)
        mono = _rasterize(at, identity, monochrome=True)
        return fg, mono

    # full-bleed: sample the square crop around the mark, apply the matte.
    x0, y0, side = info["crop"]
    target = CANVAS * MARK_FRACTION
    scale = target / side
    off = (CANVAS - target) / 2.0

    def at(dx, dy):
        fx = x0 + (dx - off) / scale
        fy = y0 + (dy - off) / scale
        if fx < x0 or fy < y0 or fx > x0 + side - 1 or fy > y0 + side - 1:
            return (0, 0, 0, 0)
        return _bilinear(px, w, h, fx, fy)

    return _rasterize(at, info["matte"], monochrome=False), _rasterize(at, info["matte"], monochrome=True)


def _rasterize(at, matte, monochrome):
    rows = []
    for y in range(CANVAS):
        row = bytearray()
        for x in range(CANVAS):
            r, g, b, a = at(x, y)
            a = int(a)
            if a <= 0:
                row += bytes((0, 0, 0, 0))
                continue
            a_mark = matte((r, g, b))
            alpha = clamp(a * a_mark)
            if alpha <= 0:
                row += bytes((0, 0, 0, 0))
                continue
            if monochrome:
                row += bytes((255, 255, 255, alpha))
            else:
                row += bytes((clamp(r), clamp(g), clamp(b), alpha))
        rows.append(bytes(row))
    return rows


def composite(background, foreground, size, circular=False, corner_radius=None):
    """Background + foreground, downscaled to `size`, clamped by a mask."""
    if background is None:
        background = _flat(30, 40, 54, CANVAS)  # a neutral slate, only for transparency sources
    scale = CANVAS / float(size)
    offsets = (1.0 / 3.0, 0.5, 5.0 / 3.0)
    rows = []
    centre = (size - 1) / 2.0
    radius = size / 2.0
    rr = corner_radius if corner_radius is not None else 0.0
    for py in range(size):
        row = bytearray()
        for px_ in range(size):
            if circular:
                dx = px_ - centre
                dy = py - centre
                if dx * dx + dy * dy > radius * radius:
                    row += bytes((0, 0, 0, 0))
                    continue
            elif rr > 0:
                cx = min(max(px_, rr), (size - 1) - rr)
                cy = min(max(py, rr), (size - 1) - rr)
                dx = px_ - cx
                dy = py - cy
                if dx * dx + dy * dy > rr * rr:
                    row += bytes((0, 0, 0, 0))
                    continue
            sr = sg = sb = sa = 0.0
            for oy in offsets:
                for ox in offsets:
                    bx = (px_ + ox) * scale
                    by = (py + oy) * scale
                    x0 = min(int(bx), CANVAS - 1)
                    y0 = min(int(by), CANVAS - 1)
                    x1 = min(x0 + 1, CANVAS - 1)
                    y1 = min(y0 + 1, CANVAS - 1)
                    tx = bx - x0
                    ty = by - y0
                    for layer in (background, foreground):
                        p00 = layer[y0][x0 * 4:x0 * 4 + 4]
                        p01 = layer[y0][x1 * 4:x1 * 4 + 4]
                        p10 = layer[y1][x0 * 4:x0 * 4 + 4]
                        p11 = layer[y1][x1 * 4:x1 * 4 + 4]

                        def mix(a, b, t):
                            return a + (b - a) * t

                        top = tuple(mix(p00[i], p01[i], tx) for i in range(4))
                        bot = tuple(mix(p10[i], p11[i], tx) for i in range(4))
                        p = tuple(mix(top[i], bot[i], ty) for i in range(4))
                        if layer is background:
                            sr, sg, sb, sa = p
                        else:
                            fa = p[3] / 255.0
                            ba = sa / 255.0
                            out_a = fa + ba * (1 - fa)
                            if out_a <= 0:
                                continue
                            sr = (p[0] * fa + sr * ba * (1 - fa)) / out_a
                            sg = (p[1] * fa + sg * ba * (1 - fa)) / out_a
                            sb = (p[2] * fa + sb * ba * (1 - fa)) / out_a
                            sa = out_a * 255.0
            if sa <= 0:
                row += bytes((0, 0, 0, 0))
            else:
                row += bytes((clamp(sr + 0.5), clamp(sg + 0.5), clamp(sb + 0.5), clamp(sa + 0.5)))
        rows.append(bytes(row))
    return rows


def _flat(r, g, b, size):
    row = bytes((clamp(r), clamp(g), clamp(b), 255)) * size
    return [row for _ in range(size)]


# ------------------------------------------------------------------------------ main

def main():
    if not SOURCE.is_file():
        raise SystemExit("source image not found: " + str(SOURCE))
    w, h, px = read_png(SOURCE)
    print("source:", SOURCE, "(%dx%d)" % (w, h))

    kind, info = analyze(px, w, h)
    print("mode:", kind)

    nodpi = RES / "drawable-nodpi"
    for name in SUPERSEDED_VECTORS:
        stale = RES / "drawable" / name
        if stale.is_file():
            stale.unlink()
            print("removed superseded vector layer:", stale.relative_to(ROOT))

    background = build_background(kind, info, px, w, h)
    foreground, monochrome = build_foreground(kind, info, px, w, h)

    write_png(nodpi / "ic_launcher_background.png", CANVAS, CANVAS, background)
    write_png(nodpi / "ic_launcher_foreground.png", CANVAS, CANVAS, foreground)
    write_png(nodpi / "ic_launcher_monochrome.png", CANVAS, CANVAS, monochrome)
    print("adaptive layers written to drawable-nodpi (%dpx canvas, %dpx safe zone)" % (CANVAS, SAFE))

    for density, size in DENSITIES.items():
        folder = RES / ("mipmap-" + density)
        corner = (26.0 / 108.0) * size
        square = composite(background, foreground, size, circular=False, corner_radius=corner)
        rounded = composite(background, foreground, size, circular=True)
        write_png(folder / "ic_launcher.png", size, size, square)
        write_png(folder / "ic_launcher_round.png", size, size, rounded)
        print("%-9s %3dx%-3d ic_launcher.png + ic_launcher_round.png" % (density, size, size))

    print("done. The manifest keeps pointing at @mipmap/ic_launcher and @mipmap/ic_launcher_round.")


if __name__ == "__main__":
    main()
