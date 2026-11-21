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
# Launchers show between 66 and about 80 percent of the layer; the picture gets 76 percent, and the
# rim around it repeats its edge, so whatever a launcher shows of the rim blends in.
ART = 328


def layer(art):
    inner = np.asarray(art.resize((ART, ART), Image.LANCZOS))
    pad = (LAYER - ART) // 2
    return Image.fromarray(np.pad(inner, ((pad, LAYER - ART - pad), (pad, LAYER - ART - pad), (0, 0)), mode="edge"))


if __name__ == "__main__":
    os.makedirs(RES, exist_ok=True)
    for name, source in ICONS.items():
        path = os.path.join(CONCEPTS, source + ".png")
        if not os.path.exists(path):
            print("missing", source)
            continue
        art = Image.open(path).convert("RGB")
        layer(art).save(os.path.join(RES, f"launcher_{name}_layer.png"), optimize=True)
        art.resize((192, 192), Image.LANCZOS).save(os.path.join(RES, f"launcher_{name}.png"), optimize=True)
        print(name)
    Image.open(os.path.join(CONCEPTS, ICONS["glass"] + ".png")).convert("RGB").resize((512, 512), Image.LANCZOS) \
        .save(os.path.join(HERE, "..", "graphics", "icon-512.png"))
