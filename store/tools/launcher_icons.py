"""
Builds the app's home screen icons from the square artwork in store/graphics/concepts/:
an adaptive icon layer (108 dp at 4x) per choice, a small preview for Settings, and the Play icon.

    python store/tools/launcher_icons.py
"""
import os

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
CONCEPTS = os.path.join(HERE, "..", "graphics", "concepts")
RES = os.path.join(HERE, "..", "..", "android", "app", "src", "main", "res", "drawable-nodpi")
ICONS = {"glass": "icon-c2c", "clear": "icon-c3c", "green": "icon-green4", "blue": "icon-blue3"}
LAYER = 432
# Launchers show the middle 72 of the 108 dp (288 of 432 px) and cut it to their own rounded square.
VIEW = 288
# The picture's edge lands this far past the launcher's, so the glass rim starts right at the cut.
OVER = 2
# The glass icon is a tight rounded square on white (corner radius as a share of its half width): its
# outer band is bent to the launcher's shape. The others already round their rim inside the picture.
CORNER = {"glass": 109 / 512}
SQUIRCLE = 4.0


def tile_radius(theta, corner):
    """Distance from the middle to the edge of a rounded square of half width 1, along angle theta."""
    c, s = np.abs(np.cos(theta)), np.abs(np.sin(theta))
    edge = 1 / np.maximum(np.maximum(c, s), 1e-9)
    if not corner:
        return edge
    # Rays that leave through a rounded corner: solve |t*(c,s) - (k,k)| = corner, with k = 1 - corner.
    k = 1 - corner
    b = k * (c + s)
    rounded = b + np.sqrt(np.maximum(b * b - (2 * k * k - corner * corner), 0))
    return np.where((edge * c > k) & (edge * s > k), rounded, edge)


def squircle_radius(theta):
    c, s = np.abs(np.cos(theta)), np.abs(np.sin(theta))
    return (c ** SQUIRCLE + s ** SQUIRCLE) ** (-1 / SQUIRCLE)


def plain_layer(art):
    inner = np.asarray(art.resize((VIEW + 2 * OVER, VIEW + 2 * OVER), Image.LANCZOS))
    pad = (LAYER - inner.shape[0]) // 2
    return Image.fromarray(np.pad(inner, ((pad, pad), (pad, pad), (0, 0)), mode="edge"))


def layer(art, corner):
    """
    The picture laid out so its edge follows the launcher's squircle: the middle stays as drawn, and only
    the outer band is bent outward toward the picture's own edge, so the rim shows all the way round,
    corners too. Past the edge it repeats the rim's outermost pixels.
    """
    import cv2
    src = np.asarray(art)
    half_src = src.shape[0] / 2
    half = VIEW / 2 + OVER
    y, x = np.mgrid[0:LAYER, 0:LAYER].astype(np.float64)
    dx, dy = (x + 0.5 - LAYER / 2) / half, (y + 0.5 - LAYER / 2) / half
    r, theta = np.hypot(dx, dy), np.arctan2(dy, dx)
    mask_edge, art_edge = squircle_radius(theta), tile_radius(theta, corner)
    t = r / mask_edge
    blend = np.clip((t - 0.72) / 0.28, 0, 1)
    blend = blend * blend * (3 - 2 * blend)
    # Inside the band the ray stretches from the mask's edge to the art's; past the edge it stays on the rim.
    scale = 1 + (art_edge / mask_edge - 1) * blend
    source = np.minimum(r * scale, art_edge * 0.995)
    map_x = (np.cos(theta) * source * half_src + half_src - 0.5).astype(np.float32)
    map_y = (np.sin(theta) * source * half_src + half_src - 0.5).astype(np.float32)
    return Image.fromarray(cv2.remap(src, map_x, map_y, cv2.INTER_LANCZOS4, borderMode=cv2.BORDER_REPLICATE))

if __name__ == "__main__":
    os.makedirs(RES, exist_ok=True)
    for name, source in ICONS.items():
        path = os.path.join(CONCEPTS, source + ".png")
        if not os.path.exists(path):
            print("missing", source)
            continue
        art = Image.open(path).convert("RGB")
        (layer(art, CORNER[name]) if name in CORNER else plain_layer(art)).save(os.path.join(RES, f"launcher_{name}_layer.png"), optimize=True)
        art.resize((192, 192), Image.LANCZOS).save(os.path.join(RES, f"launcher_{name}.png"), optimize=True)
        print(name)
    Image.open(os.path.join(CONCEPTS, ICONS["glass"] + ".png")).convert("RGB").resize((512, 512), Image.LANCZOS) \
        .save(os.path.join(HERE, "..", "graphics", "icon-512.png"))
