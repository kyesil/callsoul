"""
Moves the handset of each icon to the optical centre: halfway between the middle of its box and its
centre of mass lands on the middle of the canvas. The hole it leaves is inpainted from the glass around it.

    python store/tools/center_icons.py
"""
import os

import cv2
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
CONCEPTS = os.path.join(HERE, "..", "graphics", "concepts")
# Source -> output, and whether the handset is light on a dark slab.
ICONS = {"icon-c2n": ("icon-c2c", False), "icon-c3n": ("icon-c3c", False), "icon-green3": ("icon-green4", True), "icon-blue2": ("icon-blue3", True)}

for source, (target, light) in ICONS.items():
    img = cv2.imread(os.path.join(CONCEPTS, source + ".png"))
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    core = (gray > 200) if light else (gray < 70)
    core[:120, :] = core[-120:, :] = False
    core[:, :120] = core[:, -120:] = False
    core = core.astype(np.uint8)
    # Keep the biggest blob: the handset, not stray highlights.
    count, labels, stats, _ = cv2.connectedComponentsWithStats(core)
    biggest = 1 + int(np.argmax(stats[1:, cv2.CC_STAT_AREA]))
    core = (labels == biggest).astype(np.uint8)
    ys, xs = np.nonzero(core)
    box = ((xs.min() + xs.max()) / 2, (ys.min() + ys.max()) / 2)
    mass = (xs.mean(), ys.mean())
    optical = ((box[0] + mass[0]) / 2, (box[1] + mass[1]) / 2)
    h, w = gray.shape
    dx, dy = int(round(w / 2 - optical[0])), int(round(h / 2 - optical[1]))
    # The handset with its soft rim and shadow.
    region = cv2.dilate(core, np.ones((5, 5), np.uint8), iterations=6)
    hole = cv2.dilate(region, np.ones((5, 5), np.uint8), iterations=3)
    clean = cv2.inpaint(img, hole * 255, 9, cv2.INPAINT_TELEA)
    shift = np.float32([[1, 0, dx], [0, 1, dy]])
    moved = cv2.warpAffine(img, shift, (w, h), borderMode=cv2.BORDER_REPLICATE)
    alpha = cv2.warpAffine(region.astype(np.float32), shift, (w, h))
    alpha = cv2.GaussianBlur(alpha, (0, 0), 3)[..., None]
    out = (moved * alpha + clean * (1 - alpha)).astype(np.uint8)
    cv2.imwrite(os.path.join(CONCEPTS, target + ".png"), out)
    print(target, "moved", dx, dy)
