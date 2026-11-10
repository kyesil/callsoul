"""
The website's pictures: the same raw emulator captures as the store mockups, but without any text
on them (the page carries the captions) and without Android's status bar. The overview shows whole
screens; every other picture shows only the part of the screen the feature is about, cut out and
laid out in rows on the glass grey background.

    python store/tools/site.py

Writes server/public/assets/shots/<lang>-<name>.jpg for each language whose captures are in
graphics/raw/<lang>/.
"""
import os

from PIL import Image, ImageDraw, ImageFilter

from mockups import RAW, cards, glass_background, rounded

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.abspath(os.path.join(HERE, "..", "..", "server", "public", "assets", "shots"))

W = 1080
MARGIN, GAP = 48, 26
RADIUS = 34
# Android's status bar on the Pixel 9 Pro emulator, cut from whole screens.
STATUS = 140
WHOLE = (0, STATUS, 1280, 2856)

# Each picture is rows of tiles; a tile is (capture, box) or (capture, box, share of the width).
# Tiles in one row get the same height and fill the width between them.
PICTURES = {
    "0-overview": [[("1-home", WHOLE), ("2-profile", WHOLE), ("2-incoming", WHOLE)]],
    "1-home": [
        [("1-home", (0, 600, 1280, 1500))],
        [("1b-menu", (430, 1400, 1240, 2480)), ("2-swipe", (0, 640, 800, 1706))],
    ],
    "2-contact": [[("2-profile", (0, 180, 1280, 1500)), ("2-editor", (0, 140, 1280, 1460))]],
    "3-labels": [[("3b-dialog", (130, 680, 1150, 1320), 0.8)], [("3-labels", (0, 1080, 1280, 1700))]],
    "4-call": [[("2-incoming", (0, 200, 1280, 1100))], [("4-speaker", (0, 1840, 1280, 2760))]],
    "5-pill": [
        [("5-ring-h", (480, 1330, 1280, 2431)), ("5-talk-h", (480, 1330, 1280, 2431))],
        [("5-ring-v", (480, 1330, 1280, 2431)), ("5-talk-v", (480, 1330, 1280, 2431))],
    ],
    "6-spam": [
        [("6-spam", (0, 250, 1280, 1000))],
        [("6b-end-spam", (0, 1560, 1280, 1960))],
        [("6c-question", (0, 2020, 1280, 2560))],
    ],
    "7-settings": "settings",
    "8-stats": [[("8-stats", (0, 330, 1280, 1580))], [("8c-search", (0, 160, 1280, 1720), 0.8)]],
    "9-quick": [[("9-quick", (0, 280, 1280, 1420))]],
    "10-widgets": [[("10-widgets", (60, 540, 1280, 1700)), ("10b-widget-menu", (480, 280, 1280, 1300))]],
    "11-contact-more": [[("11c-profile-more", (0, 150, 1280, 1160))], [("11b-qr", (150, 782, 1130, 2170), 0.72)]],
    "12-manage": [
        [("12-labels", (0, 580, 1280, 1020))],
        [("12b-duplicates", (0, 620, 1280, 1500))],
        [("12c-blocked", (0, 330, 1280, 1500))],
    ],
    "13-call-card": [[("13-callcard", (0, 200, 1280, 1250))], [("13b-keypad", (0, 1280, 1280, 2760), 0.62)]],
    "14-settings-more": [[("14-looks", (0, 600, 1280, 1400))], [("14b-calls", (0, 700, 1280, 2330))]],
}
# Where a language's capture lays out differently.
OVERRIDES = {
    ("tr", "11c-profile-more"): (0, 150, 1280, 930),
}


def settings_rows(lang):
    """The same four cards as the store's settings picture, found on the captures."""
    top, more = (Image.open(os.path.join(RAW, lang, n + ".png")) for n in ("7-settings", "7b-settings"))
    first, second = cards(top), cards(more)
    return [
        [("7-settings", (20, first[1][0], 1260, first[1][1]))],
        [("7-settings", (20, first[3][0], 1260, first[3][1]))],
        [("7b-settings", (20, second[0][1] - 250, 1260, second[0][1]))],
        [("7b-settings", (20, second[1][0], 1260, second[1][0] + 640))],
    ]


def layout(lang, rows):
    """Each tile's capture, crop and place, and the canvas height they need."""
    placed, y = [], MARGIN
    room = W - 2 * MARGIN
    for row in rows:
        tiles = []
        for t in row:
            name, box = t[0], OVERRIDES.get((lang, t[0]), t[1])
            share = t[2] if len(t) > 2 else 1.0
            tiles.append((name, box, share))
        ratios = [(b[2] - b[0]) / (b[3] - b[1]) for _, b, _ in tiles]
        width = room - GAP * (len(tiles) - 1)
        if len(tiles) == 1:
            w = round(room * tiles[0][2])
            h = round(w / ratios[0])
            placed.append((tiles[0][0], tiles[0][1], (W - w) // 2, y, w, h))
        else:
            h = round(width / sum(ratios))
            x = MARGIN
            for (name, box, _), r in zip(tiles, ratios):
                w = round(h * r)
                placed.append((name, box, x, y, w, h))
                x += w + GAP
        y += h + GAP
    return placed, y - GAP + MARGIN


def picture(lang, name, rows):
    placed, height = layout(lang, rows)
    canvas = glass_background(W, height).convert("RGBA")
    shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(shadow)
    for _, _, x, y, w, h in placed:
        draw.rounded_rectangle((x, y + 10, x + w, y + h + 10), radius=RADIUS, fill=(20, 26, 34, 70))
    canvas.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(18)))
    for part, box, x, y, w, h in placed:
        raw = Image.open(os.path.join(RAW, lang, part + ".png")).convert("RGB")
        view = raw.crop(box).resize((w, h), Image.LANCZOS)
        canvas.paste(view, (x, y), rounded((w, h), RADIUS))
    out = canvas.convert("RGB")
    out = out.resize((900, round(out.height * 900 / W)), Image.LANCZOS)
    os.makedirs(OUT, exist_ok=True)
    out.save(os.path.join(OUT, f"{lang}-{name}.jpg"), quality=84, optimize=True, progressive=True)


def parts(rows):
    return {t[0] for row in rows for t in row}


if __name__ == "__main__":
    for lang in ("tr", "en"):
        if not os.path.isdir(os.path.join(RAW, lang)):
            continue
        for name, rows in PICTURES.items():
            if rows == "settings":
                rows = settings_rows(lang)
            missing = [p for p in parts(rows) if not os.path.exists(os.path.join(RAW, lang, p + ".png"))]
            if missing:
                print(lang, name, "missing", missing)
                continue
            picture(lang, name, rows)
            print(lang, name)
