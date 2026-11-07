"""
The app icon, drawn once and written twice: as Android vector drawables for the adaptive launcher
icon, and as PNGs for Google Play. One dome of grey glass with a graphite handset inside, a crescent
of reflected light along its upper edge and a water drop resting on it.

    python store/tools/icon.py
"""
import os
import re

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", ".."))
RES = os.path.join(REPO, "android", "app", "src", "main", "res")
OUT = os.path.join(REPO, "store", "graphics")

# Material "call" glyph, 24x24, scaled into the middle of the 108 canvas.
HANDSET = (
    "M6.6,10.8c1.4,2.8 3.8,5.1 6.6,6.6l2.2,-2.2c0.3,-0.3 0.7,-0.4 1,-0.2 1.1,0.4 2.3,0.6 3.6,0.6 "
    "0.6,0 1,0.4 1,1V20c0,0.6 -0.4,1 -1,1C10.6,21 3,13.4 3,4c0,-0.6 0.4,-1 1,-1h3.5c0.6,0 1,0.4 1,1 "
    "0,1.2 0.2,2.4 0.6,3.6 0.1,0.3 0,0.7 -0.2,1L6.6,10.8z"
)
HANDSET_SCALE = 1.9
HANDSET_SHIFT = round(54 - 12 * HANDSET_SCALE, 2)

# The whole icon is one dome of grey glass, lit from the top left.
GLASS_CENTER, GLASS_RADIUS = (44, 36), 82
GLASS_LIGHT, GLASS_MID, GLASS_DEEP = "#F7F9FB", "#C5CBD2", "#7A828C"
INK_TOP, INK_BOTTOM = "#4A515A", "#1A1F24"

# A crescent of reflected light along the upper left, just inside the edge: the space between an
# outer circle and a slightly shifted inner one, fading out towards the middle.
REFLECT_OUTER = (54, 54, 31)
REFLECT_INNER = (57, 57, 30)
REFLECT_FROM, REFLECT_TO = (32, 32), (58, 58)
REFLECT_COLOR = "#E0FFFFFF"

# A water drop resting on the glass, in the open corner above the handset: a teardrop, dark at the
# top where it bends the light away, bright at the bottom where it gathers it, with a small glint.
DROP = (73.0, 41.0, 3.7)  # centre and radius of the round bottom; the point rises 2r above it
DROP_TOP, DROP_BOTTOM = "#381A1F24", "#C0FFFFFF"
SPARK = (71.6, 39.3, 0.95)
SPARK_COLOR = "#F2FFFFFF"


def drop_path(cx, cy, r):
    k = 0.5523 * r
    return (f"M{cx:g},{cy - 2 * r:g}"
            f"C{cx + 0.35 * r:g},{cy - 1.3 * r:g} {cx + r:g},{cy - 0.7 * r:g} {cx + r:g},{cy:g}"
            f"C{cx + r:g},{cy + k:g} {cx + k:g},{cy + r:g} {cx:g},{cy + r:g}"
            f"C{cx - k:g},{cy + r:g} {cx - r:g},{cy + k:g} {cx - r:g},{cy:g}"
            f"C{cx - r:g},{cy - 0.7 * r:g} {cx - 0.35 * r:g},{cy - 1.3 * r:g} {cx:g},{cy - 2 * r:g}z")


def circle_path(cx, cy, r):
    return f"M{cx - r:g},{cy:g}a{r:g},{r:g} 0,1 1,{2 * r:g},0a{r:g},{r:g} 0,1 1,{-2 * r:g},0z"


# ---------- Android vector drawables ----------

HEAD = ('<?xml version="1.0" encoding="utf-8"?>\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    xmlns:aapt="http://schemas.android.com/aapt"\n'
        '    android:width="108dp"\n    android:height="108dp"\n'
        '    android:viewportWidth="108"\n    android:viewportHeight="108">\n')


def linear_fill(x0, y0, x1, y1, start, end, indent="        "):
    return (f'{indent}<aapt:attr name="android:fillColor">\n'
            f'{indent}    <gradient android:type="linear" android:startX="{x0:g}" android:startY="{y0:g}"'
            f' android:endX="{x1:g}" android:endY="{y1:g}"\n'
            f'{indent}        android:startColor="{start}" android:endColor="{end}" />\n'
            f'{indent}</aapt:attr>\n')


def write_vectors():
    background = (
        HEAD
        + '    <!-- One dome of grey glass, brightest towards the top left. -->\n'
        + '    <path android:pathData="M0,0h108v108h-108z">\n'
        + '        <aapt:attr name="android:fillColor">\n'
        + f'            <gradient android:type="radial" android:centerX="{GLASS_CENTER[0]}"'
        + f' android:centerY="{GLASS_CENTER[1]}" android:gradientRadius="{GLASS_RADIUS}"\n'
        + f'                android:startColor="{GLASS_LIGHT}" android:centerColor="{GLASS_MID}"'
        + f' android:endColor="{GLASS_DEEP}" />\n'
        + '        </aapt:attr>\n'
        + '    </path>\n'
        + '</vector>\n'
    )
    handset = (
        f'    <group android:scaleX="{HANDSET_SCALE:g}" android:scaleY="{HANDSET_SCALE:g}"\n'
        f'        android:translateX="{HANDSET_SHIFT:g}" android:translateY="{HANDSET_SHIFT:g}">\n'
        f'        <path android:pathData="{HANDSET}">\n'
        + linear_fill(12, 3, 12, 21, INK_TOP, INK_BOTTOM, indent="            ")
        + '        </path>\n'
        '    </group>\n'
    )
    ox, oy, orad = REFLECT_OUTER
    ix, iy, irad = REFLECT_INNER
    dx, dy, dr = DROP
    sx, sy, sr = SPARK
    foreground = (
        HEAD + handset
        + '    <!-- Light reflected along the upper left edge of the glass. -->\n'
        + f'    <path android:fillType="evenOdd" android:pathData="{circle_path(ox, oy, orad)}{circle_path(ix, iy, irad)}">\n'
        + linear_fill(*REFLECT_FROM, *REFLECT_TO, REFLECT_COLOR, "#00FFFFFF")
        + '    </path>\n'
        + '    <!-- A water drop on the glass. -->\n'
        + f'    <path android:pathData="{drop_path(dx, dy, dr)}">\n'
        + linear_fill(dx, dy - 2 * dr, dx, dy + dr, DROP_TOP, DROP_BOTTOM)
        + '    </path>\n'
        + f'    <path android:fillColor="{SPARK_COLOR}" android:pathData="{circle_path(sx, sy, sr)}" />\n'
        + '</vector>\n'
    )
    monochrome = (
        HEAD
        + f'    <group android:scaleX="{HANDSET_SCALE:g}" android:scaleY="{HANDSET_SCALE:g}"\n'
        + f'        android:translateX="{HANDSET_SHIFT:g}" android:translateY="{HANDSET_SHIFT:g}">\n'
        + f'        <path android:fillColor="#FFFFFFFF" android:pathData="{HANDSET}" />\n'
        + '    </group>\n'
        + '</vector>\n'
    )
    drawable = os.path.join(RES, "drawable")
    for name, body in [("ic_launcher_background", background), ("ic_launcher_foreground", foreground),
                       ("ic_launcher_monochrome", monochrome)]:
        with open(os.path.join(drawable, name + ".xml"), "w", encoding="utf-8", newline="\n") as f:
            f.write(body)
    with open(os.path.join(RES, "mipmap-anydpi", "ic_launcher.xml"), "w", encoding="utf-8", newline="\n") as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n'
                '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
                '    <background android:drawable="@drawable/ic_launcher_background" />\n'
                '    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n'
                '    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />\n'
                '</adaptive-icon>\n')


# ---------- PNG rendering ----------

def rgba(hex_color):
    h = hex_color.lstrip("#")
    if len(h) == 8:
        a, r, g, b = (int(h[i:i + 2], 16) for i in range(0, 8, 2))
    else:
        a, (r, g, b) = 255, (int(h[i:i + 2], 16) for i in range(0, 6, 2))
    return np.array([r, g, b, a], dtype=np.float32) / 255.0


def path_points(d):
    """Flattens an SVG path (M L H V C S Z, absolute and relative) to polygons."""
    tokens = re.findall(r"[MmLlHhVvCcSsZz]|-?\d*\.?\d+(?:e-?\d+)?", d)
    polys, pts = [], []
    x = y = sx = sy = 0.0
    lc = None
    i, cmd = 0, None

    def num():
        nonlocal i
        v = float(tokens[i]); i += 1
        return v

    def cubic(p0, p1, p2, p3):
        for t in np.linspace(0, 1, 16)[1:]:
            mt = 1 - t
            pts.append((mt**3 * p0[0] + 3 * mt * mt * t * p1[0] + 3 * mt * t * t * p2[0] + t**3 * p3[0],
                        mt**3 * p0[1] + 3 * mt * mt * t * p1[1] + 3 * mt * t * t * p2[1] + t**3 * p3[1]))

    while i < len(tokens):
        if re.fullmatch(r"[A-Za-z]", tokens[i]):
            cmd = tokens[i]; i += 1
            if cmd in "Zz":
                if pts:
                    polys.append(pts)
                pts = []; x, y = sx, sy; lc = None
                continue
        rel = cmd.islower()
        c = cmd.upper()
        if c == "M":
            nx, ny = num(), num()
            x, y = (x + nx, y + ny) if rel else (nx, ny)
            if pts:
                polys.append(pts)
            pts = [(x, y)]; sx, sy = x, y
            cmd = "l" if rel else "L"; lc = None
        elif c == "L":
            nx, ny = num(), num()
            x, y = (x + nx, y + ny) if rel else (nx, ny)
            pts.append((x, y)); lc = None
        elif c == "H":
            nx = num(); x = x + nx if rel else nx; pts.append((x, y)); lc = None
        elif c == "V":
            ny = num(); y = y + ny if rel else ny; pts.append((x, y)); lc = None
        elif c in "CS":
            if c == "C":
                x1, y1 = num(), num()
                if rel:
                    x1, y1 = x + x1, y + y1
            else:
                x1, y1 = (2 * x - lc[0], 2 * y - lc[1]) if lc else (x, y)
            x2, y2, nx, ny = num(), num(), num(), num()
            if rel:
                x2, y2, nx, ny = x + x2, y + y2, x + nx, y + ny
            cubic((x, y), (x1, y1), (x2, y2), (nx, ny))
            lc = (x2, y2); x, y = nx, ny
    if pts:
        polys.append(pts)
    return polys


def polygon_mask(polys, size, transform):
    from PIL import ImageDraw
    img = Image.new("L", (size, size), 0)
    draw = ImageDraw.Draw(img)
    for poly in polys:
        draw.polygon([transform(p) for p in poly], fill=255)
    return np.asarray(img, dtype=np.float32) / 255.0


def ramp(t, stops):
    out = np.zeros(t.shape + (4,), np.float32)
    for (o0, c0), (o1, c1) in zip(stops, stops[1:]):
        seg = (t >= o0) & (t <= o1)
        k = ((t - o0) / max(o1 - o0, 1e-6))[..., None]
        out[seg] = (c0 + (c1 - c0) * k)[seg]
    return out


def grid(size, unit):
    ys, xs = np.mgrid[0:size, 0:size].astype(np.float32) / unit
    return xs, ys


def linear(size, start, end, stops, unit):
    """A linear gradient from start to end (in icon units), stops as [(offset, rgba)]."""
    xs, ys = grid(size, unit)
    dx, dy = end[0] - start[0], end[1] - start[1]
    return ramp(np.clip(((xs - start[0]) * dx + (ys - start[1]) * dy) / (dx * dx + dy * dy), 0, 1), stops)


def radial(size, center, radius, stops, unit):
    xs, ys = grid(size, unit)
    return ramp(np.clip(np.sqrt((xs - center[0]) ** 2 + (ys - center[1]) ** 2) / radius, 0, 1), stops)


def disc(size, unit, cx, cy, r):
    xs, ys = grid(size, unit)
    return np.clip((r - np.sqrt((xs - cx) ** 2 + (ys - cy) ** 2)) * unit, 0, 1)


def flat(size, color):
    return np.broadcast_to(rgba(color), (size, size, 4)).copy()


def over(dst, src, mask):
    a = src[..., 3:4] * mask[..., None]
    dst[..., :3] = src[..., :3] * a + dst[..., :3] * (1 - a)
    dst[..., 3:4] = a + dst[..., 3:4] * (1 - a)


def render(size):
    """Draws the 108-unit icon at size x size pixels (supersampled 4x)."""
    big = size * 4
    unit = big / 108.0
    canvas = np.zeros((big, big, 4), np.float32)
    over(canvas, radial(big, GLASS_CENTER, GLASS_RADIUS,
                        [(0, rgba(GLASS_LIGHT)), (0.5, rgba(GLASS_MID)), (1, rgba(GLASS_DEEP))], unit),
         np.ones((big, big), np.float32))
    handset = polygon_mask(path_points(HANDSET), big,
                           lambda p: ((p[0] * HANDSET_SCALE + HANDSET_SHIFT) * unit,
                                      (p[1] * HANDSET_SCALE + HANDSET_SHIFT) * unit))
    ink = linear(big, (54, HANDSET_SHIFT + 3 * HANDSET_SCALE), (54, HANDSET_SHIFT + 21 * HANDSET_SCALE),
                 [(0, rgba(INK_TOP)), (1, rgba(INK_BOTTOM))], unit)
    over(canvas, ink, handset)
    crescent = np.clip(disc(big, unit, *REFLECT_OUTER) - disc(big, unit, *REFLECT_INNER), 0, 1)
    over(canvas, linear(big, REFLECT_FROM, REFLECT_TO, [(0, rgba(REFLECT_COLOR)), (1, rgba("#00FFFFFF"))], unit),
         crescent)
    dx, dy, dr = DROP
    over(canvas, linear(big, (dx, dy - 2 * dr), (dx, dy + dr), [(0, rgba(DROP_TOP)), (1, rgba(DROP_BOTTOM))], unit),
         polygon_mask(path_points(drop_path(dx, dy, dr)), big, lambda p: (p[0] * unit, p[1] * unit)))
    over(canvas, flat(big, SPARK_COLOR), disc(big, unit, *SPARK))
    img = Image.fromarray((np.clip(canvas, 0, 1) * 255).astype(np.uint8), "RGBA")
    return img.resize((size, size), Image.LANCZOS)


def write_pngs():
    os.makedirs(OUT, exist_ok=True)
    # Play shows the icon in its own rounded mask; the launcher shows the middle 72 of 108 units.
    full = render(720)
    crop = int(720 * 18 / 108)
    visible = full.crop((crop, crop, 720 - crop, 720 - crop))
    visible.resize((512, 512), Image.LANCZOS).convert("RGB").save(os.path.join(OUT, "icon-512.png"))
    # The logo on its own: the glass as a round lens, transparent outside.
    logo = visible.resize((432, 432), Image.LANCZOS)
    lens = Image.fromarray((disc(1728, 1728 / 72.0, 36, 36, 35.6) * 255).astype(np.uint8), "L")
    logo.putalpha(lens.resize((432, 432), Image.LANCZOS))
    logo.save(os.path.join(OUT, "logo-glass.png"))
    full.save(os.path.join(OUT, "icon-adaptive-preview.png"))


if __name__ == "__main__":
    write_vectors()
    write_pngs()
    print("icon written")
