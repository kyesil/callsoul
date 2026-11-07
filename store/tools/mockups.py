"""
Play Store screenshots and feature graphic: each raw emulator capture in graphics/raw/<lang>/ goes
into a phone frame on a glass grey background, under a one-line caption.

    python store/tools/mockups.py
"""
import functools
import os

from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
GRAPHICS = os.path.abspath(os.path.join(HERE, "..", "graphics"))
RAW = os.path.join(GRAPHICS, "raw")
SHOTS = os.path.join(GRAPHICS, "screenshots")
FONTS = r"C:\Windows\Fonts"
BOLD = os.path.join(FONTS, "seguisb.ttf")
REGULAR = os.path.join(FONTS, "segoeui.ttf")

W, H = 1080, 1920
INK = (28, 33, 38)
SOFT_INK = (88, 96, 106)

CAPTIONS = {
    "tr": {
        "1-home": "Favoriler, ikon menüsü, kaydırarak arama",
        "2-contact": "Şık profil, Google Kişiler'in tüm alanları",
        "3-labels": "Etiketle, rehberi çöplüğe çevirme",
        "4-call": "Bluetooth bağlıyken bile tek dokunuşla hoparlör",
        "4-speaker": "Hoparlör, Bluetooth bağlıyken bile tek dokunuşta",
        "5-pill": "Uygulamalarda gez, arama hapıyla tek dokunuşla dön",
        "6-spam": "Spamı gör, tek dokunuşla bildir",
        "7-settings": "Yüzü kapalıyken sessiz, WhatsApp, hazır SMS",
        "8-stats": "İstatistikler ve akıllı arama",
        "8b-stats-year": "Haftalık, aylık ve yıllık istatistikler",
        "8c-stats-profile": "Kimle ne kadar konuştuğunu gör",
    },
    "en": {
        "1-home": "Favourites, icon shortcuts, swipe to call",
        "2-contact": "A clean profile, every Google Contacts field",
        "3-labels": "Label numbers, keep contacts clean",
        "4-call": "One-tap speaker, even on Bluetooth",
        "4-speaker": "Speaker in one tap, Bluetooth or not",
        "5-pill": "Roam your apps, one tap back to the call",
        "6-spam": "See spam, report it in one tap",
        "7-settings": "Silent face down, quick SMS, WhatsApp calls",
        "8-stats": "Statistics and smart search",
        "8b-stats-year": "Weekly, monthly and yearly statistics",
        "8c-stats-profile": "See who you talk to, and for how long",
    },
}
# The call pill's styles, each caught on the home screen.
PILL_TOP = 1330
PILL_TILES = ["5-ring-h", "5-talk-h", "5-ring-v", "5-talk-v"]
PILL_LABELS = {
    "tr": ["Gelen arama", "Konuşmada", "Dikey, gelen arama", "Dikey, konuşmada"],
    "en": ["Incoming call", "In a call", "Upright, incoming", "Upright, in a call"],
}
# Screens on one subject go on one image, the first in front of the rest.
COLLAGES = {
    "1-home": ["1-home", "1b-menu", "2-swipe"],
    "2-contact": ["2-profile", "2-editor"],
    "3-labels": ["3b-dialog", "3-labels"],
    "4-call": ["2-incoming", "4-speaker"],
    "5-pill": ["5-ring-h", "5-talk-h", "5-ring-v", "5-talk-v"],
    "6-spam": ["6-spam", "6b-end-spam", "6c-question"],
    "7-settings": ["7-settings", "7b-settings"],
    "8-stats": ["8-stats", "8c-search"],
    "8b-stats-year": ["8-stats", "8b-stats-year"],
    "8c-stats-profile": ["8-stats", "2-profile"],
}
FEATURE = {
    "tr": "Telefon ve rehber, sade ve hızlı",
    "en": "Phone and contacts, calm and fast",
}


@functools.lru_cache(maxsize=None)
def _glass(w, h):
    """The icon's grey glass: bright towards the top left, deeper towards the bottom right."""
    img = Image.new("RGB", (w, h))
    px = img.load()
    cx, cy, r = w * 0.3, h * 0.15, (w * w + h * h) ** 0.5
    light, mid, deep = (247, 249, 251), (214, 219, 224), (170, 177, 186)
    for y in range(h):
        for x in range(0, w, 2):
            t = min(1.0, (((x - cx) ** 2 + (y - cy) ** 2) ** 0.5) / r)
            if t < 0.5:
                k = t / 0.5
                c = tuple(int(light[i] + (mid[i] - light[i]) * k) for i in range(3))
            else:
                k = (t - 0.5) / 0.5
                c = tuple(int(mid[i] + (deep[i] - mid[i]) * k) for i in range(3))
            px[x, y] = c
            if x + 1 < w:
                px[x + 1, y] = c
    return img


def glass_background(w, h):
    return _glass(w, h).copy()


def wrap(draw, text, font, width):
    lines, line = [], ""
    for word in text.split():
        trial = (line + " " + word).strip()
        if draw.textlength(trial, font=font) <= width or not line:
            line = trial
        else:
            lines.append(line)
            line = word
    lines.append(line)
    return lines


def rounded(size, radius):
    mask = Image.new("L", size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, size[0] - 1, size[1] - 1), radius=radius, fill=255)
    return mask


def phone(screen, height):
    """The capture inside a slim graphite frame, with a soft shadow, as RGBA."""
    bezel, radius = 16, 64
    sh = height - 2 * bezel
    sw = round(screen.width * sh / screen.height)
    screen = screen.convert("RGB").resize((sw, sh), Image.LANCZOS)
    fw, fh = sw + 2 * bezel, sh + 2 * bezel
    pad = 110
    out = Image.new("RGBA", (fw + 2 * pad, fh + 2 * pad), (0, 0, 0, 0))
    shadow = Image.new("RGBA", out.size, (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle((pad, pad + 24, pad + fw, pad + fh + 24), radius=radius, fill=(20, 26, 34, 90))
    out = Image.alpha_composite(out, shadow.filter(ImageFilter.GaussianBlur(28)))
    frame = Image.new("RGBA", (fw, fh), (21, 24, 28, 255))
    frame.paste(screen, (bezel, bezel), rounded((sw, sh), radius - bezel))
    # A thin light rim, as on polished metal.
    ImageDraw.Draw(frame).rounded_rectangle((1, 1, fw - 2, fh - 2), radius=radius, outline=(120, 128, 138, 255), width=2)
    out.paste(frame, (pad, pad), rounded((fw, fh), radius))
    return out, pad


def pill_grid(canvas, lang):
    """Each style of the pill in a tile, close enough to read it, with the home screen around it."""
    gap, margin, top = 30, 60, 390
    tile_w = (W - 2 * margin - gap) // 2
    tile_h = 640
    view_w = 800
    view_h = round(view_w * tile_h / tile_w)
    label = ImageFont.truetype(REGULAR, 38)
    for i, (part, text) in enumerate(zip(PILL_TILES, PILL_LABELS[lang])):
        raw = Image.open(os.path.join(RAW, lang, part + ".png"))
        # The pill sits on the right, low enough to have the app icons around it.
        left = raw.width - view_w
        view = raw.convert("RGB").crop((left, PILL_TOP, raw.width, PILL_TOP + view_h)).resize((tile_w, tile_h), Image.LANCZOS)
        x = margin + (i % 2) * (tile_w + gap)
        y = top + (i // 2) * (tile_h + 92)
        shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
        ImageDraw.Draw(shadow).rounded_rectangle((x, y + 12, x + tile_w, y + tile_h + 12), radius=44, fill=(20, 26, 34, 80))
        canvas.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(20)))
        canvas.paste(view, (x, y), rounded((tile_w, tile_h), 44))
        ImageDraw.Draw(canvas).text((x + tile_w / 2, y + tile_h + 22), text, font=label, fill=SOFT_INK, anchor="ma")


def cards(raw):
    """The rounded cards of a settings screen, top to bottom, as (top, bottom)."""
    image = raw.convert("RGB")
    background = image.getpixel((8, 1400))
    found, start = [], None
    for y in range(150, image.height - 140):
        pixel = image.getpixel((46, y))
        inside = sum(abs(pixel[i] - background[i]) for i in range(3)) > 6
        if inside and start is None:
            start = y
        elif not inside and start is not None:
            if y - start > 120:
                found.append((start - 12, y + 12))
            start = None
    return found


def tile(canvas, raw, box, x, y, width):
    """A piece of a screen on the background, rounded, with a soft shadow; returns where it ends."""
    height = round(width * (box[3] - box[1]) / (box[2] - box[0]))
    view = raw.convert("RGB").crop(box).resize((width, height), Image.LANCZOS)
    shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle((x, y + 12, x + width, y + height + 12), radius=40, fill=(20, 26, 34, 80))
    canvas.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(20)))
    canvas.paste(view, (x, y), rounded((width, height), 40))
    return y + height


def home_tiles(canvas, images):
    home, menu, swipe = images
    bottom = tile(canvas, home, (0, 650, 1280, 1540), 60, 380, 960) + 30
    tile(canvas, menu, (430, 1400, 1240, 2480), 60, bottom, 465)
    tile(canvas, swipe, (0, 640, 800, 1706), 555, bottom, 465)


def speaker_tiles(canvas, images):
    call = images[0]
    bottom = tile(canvas, call, (0, 160, 1280, 1060), 110, 380, 860) + 30
    tile(canvas, call, (30, 1800, 1250, 2720), 110, bottom, 860)


def spam_tiles(canvas, images):
    warning, talking, question = images
    bottom = tile(canvas, warning, (0, 250, 1280, 1000), 60, 390, 960) + 30
    bottom = tile(canvas, talking, (0, 1560, 1280, 1960), 60, bottom, 960) + 30
    tile(canvas, question, (0, 2020, 1280, 2560), 60, bottom, 960)


def settings_tiles(canvas, images):
    top, more = images
    first, second = cards(top), cards(more)
    y = 390
    for raw, box in (
        (top, (20, first[1][0], 1260, first[1][1])),
        (top, (20, first[3][0], 1260, first[3][1])),
        (more, (20, second[0][1] - 250, 1260, second[0][1])),
        (more, (20, second[1][0], 1260, second[1][0] + 640)),
    ):
        y = tile(canvas, raw, box, 60, y, 960) + 30


def place(canvas, raw, height, x, y):
    device, pad = phone(raw, height)
    # Through a layer, since a phone's shadow may run past the canvas edge.
    layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    layer.paste(device, (x - pad, y - pad))
    canvas.alpha_composite(layer)


def width_of(height):
    return round((height - 32) * 1280 / 2856) + 32


def screenshot(lang, name, caption):
    canvas = glass_background(W, H).convert("RGBA")
    draw = ImageDraw.Draw(canvas)
    font = ImageFont.truetype(BOLD, 72)
    lines = wrap(draw, caption, font, W - 160)
    y = 150 if len(lines) == 1 else 110
    for line in lines:
        draw.text((W / 2, y), line, font=font, fill=INK, anchor="ma")
        y += 92
    images = [Image.open(os.path.join(RAW, lang, part + ".png")) for part in COLLAGES.get(name, [name])]
    if name == "5-pill":
        pill_grid(canvas, lang)
    elif name == "1-home":
        home_tiles(canvas, images)
    elif name == "4-call":
        # Side by side, so that neither phone hides the other's screen.
        height = 1100
        gap = (W - 2 * width_of(height)) // 3
        place(canvas, images[0], height, gap, 400)
        place(canvas, images[1], height, 2 * gap + width_of(height), 620)
    elif name == "4-speaker":
        speaker_tiles(canvas, images)
    elif name == "6-spam":
        spam_tiles(canvas, images)
    elif name == "7-settings":
        settings_tiles(canvas, images)
    elif len(images) == 1:
        height = 1460
        place(canvas, images[0], height, (W - width_of(height)) // 2, 400)
    elif len(images) == 2:
        height = 1320
        step = W - 24 - width_of(height) - 24
        place(canvas, images[0], height, 24, 390)
        place(canvas, images[1], height, 24 + step, 500)
    else:
        height = 960
        step = (W - 30 - width_of(height)) // 2 - 12
        for i, image in enumerate(images):
            place(canvas, image, height, 27 + i * (step + 12), 560 + (90 if i == 1 else 0))
    folder = os.path.join(SHOTS, lang)
    os.makedirs(folder, exist_ok=True)
    canvas.convert("RGB").save(os.path.join(folder, name + ".png"))


def feature_graphic(lang):
    w, h = 1024, 500
    canvas = glass_background(w, h).convert("RGBA")
    logo = Image.open(os.path.join(GRAPHICS, "logo-glass.png")).resize((300, 300), Image.LANCZOS)
    shadow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).ellipse((96, 120, 396, 420), fill=(20, 26, 34, 80))
    canvas = Image.alpha_composite(canvas, shadow.filter(ImageFilter.GaussianBlur(22)))
    canvas.alpha_composite(logo, (96, 100))
    draw = ImageDraw.Draw(canvas)
    draw.text((450, 170), "Better Call Soul", font=ImageFont.truetype(BOLD, 64), fill=INK)
    draw.text((452, 262), FEATURE[lang], font=ImageFont.truetype(REGULAR, 38), fill=SOFT_INK)
    suffix = "" if lang == "tr" else "-" + lang
    canvas.convert("RGB").save(os.path.join(GRAPHICS, f"feature-graphic{suffix}.png"))


if __name__ == "__main__":
    for lang, captions in CAPTIONS.items():
        if not os.path.isdir(os.path.join(RAW, lang)):
            continue
        for name, caption in captions.items():
            parts = COLLAGES.get(name, [name])
            if all(os.path.exists(os.path.join(RAW, lang, part + ".png")) for part in parts):
                screenshot(lang, name, caption)
                print(lang, name)
        feature_graphic(lang)
