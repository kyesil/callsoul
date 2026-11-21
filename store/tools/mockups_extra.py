"""
The ad-free / open-source / WhatsApp-calls graphics: a feature graphic with the glass tile icon and
three screenshots (intro, WhatsApp calls in the log, how long it rang). Uses mockups.py's look.

    python store/tools/mockups_extra.py
"""
import os

from PIL import Image, ImageDraw, ImageFilter, ImageFont

from mockups import BOLD, GRAPHICS, H, INK, RAW, REGULAR, SHOTS, SOFT_INK, W, glass_background, place, rounded, tile, width_of, wrap

ICON = os.path.join(GRAPHICS, "concepts", "icon-c2c.png")
SYMBOLS = r"C:\Windows\Fonts\segmdl2.ttf"
PHONE, VIDEO = "\uE717", "\uE714"
GREEN = (37, 163, 102)
DEEP_GREEN = (18, 124, 74)

TEXT = {
    "tr": {
        "tagline": "Reklamsız · Açık kaynak",
        "wa": "WhatsApp aramaları da burada",
        "intro": "Reklamsız ve açık kaynak",
        "points": ["Reklam yok, takip yok", "Açık kaynak, GPL-3.0", "WhatsApp aramaları arama geçmişinde"],
        "whatsapp": "WhatsApp aramaları da arama geçmişinde",
        "wa_labels": ["Görüntülü", "Sesli", "Cevapsız"],
        "ring": "Ne kadar çaldığını gör: bildirimde ve geçmişte",
        "ring_note": "Kişinin fotoğrafı ve tek dokunuşla geri arama bildirimde",
        "icons": "Dört simgeden birini seç",
        "call": "Arayan hakkında her şey bir bakışta",
        "stats": "İstatistikler: haftalık, aylık, yıllık ve hepsi",
        "pill": "Arama hapı işini bölmez",
        "pill_labels": ["Gelen arama", "Konuşmada"],
        "t9": "T9 ve numara arama",
        "spam": "Spam uyarısı ve engelleme",
    },
    "en": {
        "tagline": "Ad-free · Open source",
        "wa": "WhatsApp calls are here too",
        "intro": "Ad-free and open source",
        "points": ["No ads, no trackers", "Open source, GPL-3.0", "WhatsApp calls in your call log"],
        "whatsapp": "WhatsApp calls in your call log too",
        "wa_labels": ["Video", "Voice", "Missed"],
        "ring": "See how long it rang, in the notification and the log",
        "ring_note": "The caller's photo and one-tap call back, right in the notification",
        "icons": "Pick one of four icons",
        "call": "Everything about the caller at a glance",
        "stats": "Statistics: weekly, monthly, yearly and all",
        "pill": "The call pill never gets in your way",
        "pill_labels": ["Incoming", "In a call"],
        "t9": "T9 and number search",
        "spam": "Spam alerts and blocking",
    },
}


def squircle(size, n=4.0):
    big = size * 3
    mask = Image.new("L", (big, big), 0)
    px, r = mask.load(), big / 2
    for y in range(big):
        yy = abs((y + 0.5 - r) / r) ** n
        if yy > 1:
            continue
        w = int(r * (1 - yy) ** (1 / n))
        for x in range(int(r - w), int(r + w)):
            px[x, y] = 255
    return mask.resize((size, size), Image.LANCZOS)


def shadowed(canvas, image, mask, x, y, blur=20, offset=12, alpha=90):
    shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    shape = Image.new("RGBA", image.size, (20, 26, 34, alpha))
    shadow.paste(shape, (x, y + offset), mask)
    canvas.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(blur)))
    canvas.paste(image, (x, y), mask)


def app_icon(canvas, size, x, y):
    icon = Image.open(ICON).convert("RGB").resize((size, size), Image.LANCZOS)
    shadowed(canvas, icon, squircle(size), x, y)


def chat_badge(size):
    """A generic green chat bubble with a call badge on its corner: not any app's logo."""
    s = size * 3
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    pad = s * 0.06
    body = (pad, pad, s - pad, s - pad * 3.2)
    d.rounded_rectangle(body, radius=s * 0.26, fill=GREEN)
    # The bubble's tail, low on the left.
    d.polygon([(s * 0.2, body[3] - 4), (s * 0.12, s - pad * 0.6), (s * 0.42, body[3] - 4)], fill=GREEN)
    # Three dots: a chat.
    r, cy = s * 0.055, (body[1] + body[3]) / 2
    for i in (-1, 0, 1):
        cx = s * 0.45 + i * s * 0.17
        d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=(255, 255, 255))
    # The call badge on the top right corner.
    br = s * 0.2
    bx, by = s - br - pad * 0.2, br + pad * 0.2
    d.ellipse((bx - br - 8, by - br - 8, bx + br + 8, by + br + 8), fill=(255, 255, 255))
    d.ellipse((bx - br, by - br, bx + br, by + br), fill=DEEP_GREEN)
    glyph = ImageFont.truetype(SYMBOLS, int(br * 1.15))
    d.text((bx, by), PHONE, font=glyph, fill=(255, 255, 255), anchor="mm")
    return img.resize((size, size), Image.LANCZOS)


def symbol_badge(size, char, color):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.ellipse((0, 0, size - 1, size - 1), fill=color)
    d.text((size / 2, size / 2), char, font=ImageFont.truetype(SYMBOLS, int(size * 0.5)), fill=(255, 255, 255), anchor="mm")
    return img


def feature_graphic(lang):
    t = TEXT[lang]
    w, h = 1024, 500
    canvas = glass_background(w, h).convert("RGBA")
    app_icon(canvas, 250, 80, 110)
    d = ImageDraw.Draw(canvas)
    d.text((385, 112), "Better Call Soul", font=ImageFont.truetype(BOLD, 62), fill=INK)
    d.text((388, 200), t["tagline"], font=ImageFont.truetype(REGULAR, 38), fill=SOFT_INK)
    # The WhatsApp line in a white pill, with the chat badge.
    font = ImageFont.truetype(BOLD, 30)
    text_w = d.textlength(t["wa"], font=font)
    px, py, ph = 385, 280, 92
    pill_w = int(ph + 18 + text_w + 34)
    pill = Image.new("RGBA", (pill_w, ph), (0, 0, 0, 0))
    ImageDraw.Draw(pill).rounded_rectangle((0, 0, pill_w - 1, ph - 1), radius=ph // 2, fill=(255, 255, 255, 235))
    shadowed(canvas, pill, rounded((pill_w, ph), ph // 2), px, py, blur=14, offset=6, alpha=60)
    badge = chat_badge(ph - 14)
    canvas.alpha_composite(badge, (px + 8, py + 7))
    ImageDraw.Draw(canvas).text((px + ph + 10, py + ph / 2), t["wa"], font=font, fill=INK, anchor="lm")
    suffix = "" if lang == "tr" else "-" + lang
    canvas.convert("RGB").save(os.path.join(GRAPHICS, f"feature-graphic{suffix}.png"))


def caption(canvas, text):
    d = ImageDraw.Draw(canvas)
    font = ImageFont.truetype(BOLD, 72)
    lines = wrap(d, text, font, W - 160)
    y = 150 if len(lines) == 1 else 110
    for line in lines:
        d.text((W / 2, y), line, font=font, fill=INK, anchor="ma")
        y += 92
    return y


def save(canvas, lang, name):
    folder = os.path.join(SHOTS, lang)
    os.makedirs(folder, exist_ok=True)
    canvas.convert("RGB").save(os.path.join(folder, name + ".png"))


def intro(lang):
    t = TEXT[lang]
    canvas = glass_background(W, H).convert("RGBA")
    app_icon(canvas, 200, (W - 200) // 2, 90)
    d = ImageDraw.Draw(canvas)
    lines = wrap(d, t["intro"], ImageFont.truetype(BOLD, 68), W - 140)
    y = 330
    for line in lines:
        d.text((W / 2, y), line, font=ImageFont.truetype(BOLD, 68), fill=INK, anchor="ma")
        y += 86
    y += 30
    icons = [symbol_badge(64, "\uE8F8", (204, 62, 62)), symbol_badge(64, "\uE943", (52, 84, 140)), chat_badge(64)]
    font = ImageFont.truetype(REGULAR, 42)
    for icon, text in zip(icons, t["points"]):
        canvas.alpha_composite(icon, (150, y))
        d.text((240, y + 32), text, font=font, fill=INK, anchor="lm")
        y += 92
    raw = Image.open(os.path.join(RAW, lang, "24-home-fav.png"))
    height = H - y - 10 + 400
    place(canvas, raw, height, (W - width_of(height)) // 2, y + 40)
    save(canvas, lang, "0-intro")


def legend(canvas, y, items):
    """Icon and word pairs in one centred row."""
    d = ImageDraw.Draw(canvas)
    font = ImageFont.truetype(REGULAR, 40)
    widths = [76 + 14 + d.textlength(text, font=font) for _, text in items]
    gap = 50
    x = (W - sum(widths) - gap * (len(items) - 1)) / 2
    for (icon, text), w in zip(items, widths):
        canvas.alpha_composite(icon, (int(x), y))
        d.text((x + 90, y + 38), text, font=font, fill=INK, anchor="lm")
        x += w + gap


def whatsapp(lang):
    t = TEXT[lang]
    canvas = glass_background(W, H).convert("RGBA")
    top = caption(canvas, t["whatsapp"]) + 40
    raw = Image.open(os.path.join(RAW, lang, "21-recents-wa.png"))
    # The day's calls: phone and WhatsApp, voice and video, answered and missed, in one list.
    bottom = tile(canvas, raw, (24, 770, 1056, 1585), 60, top, 960) + 34
    bottom = tile(canvas, raw, (24, 1695, 1056, 2140), 60, bottom, 960)
    items = [(symbol_badge(76, VIDEO, GREEN), t["wa_labels"][0]), (symbol_badge(76, PHONE, GREEN), t["wa_labels"][1]),
             (symbol_badge(76, PHONE, (214, 64, 64)), t["wa_labels"][2])]
    legend(canvas, bottom + (H - bottom) // 2 - 38, items)
    save(canvas, lang, "1-whatsapp")


def ring(lang):
    t = TEXT[lang]
    canvas = glass_background(W, H).convert("RGBA")
    top = caption(canvas, t["ring"]) + 40
    notice = Image.open(os.path.join(RAW, lang, "20-missed-notif.png"))
    raw = Image.open(os.path.join(RAW, lang, "21-recents-wa.png"))
    icons = Image.open(os.path.join(RAW, lang, "23-icon-settings.png"))
    y = tile(canvas, notice, (42, 570, 1038, 985), 60, top, 960) + 34
    y = tile(canvas, raw, (24, 770, 1056, 945), 60, y, 960) + 20
    y = tile(canvas, raw, (24, 1860, 1056, 2025), 60, y, 960) + 56
    # And the new home screen icons, as Settings offers them.
    d = ImageDraw.Draw(canvas)
    d.text((W / 2, y), t["icons"], font=ImageFont.truetype(BOLD, 50), fill=INK, anchor="ma")
    y += 80
    tile(canvas, icons, (31, 1334, 1053, 1892), 60, y, 960)
    save(canvas, lang, "1b-ring")


def pill(lang):
    t = TEXT[lang]
    canvas = glass_background(W, H).convert("RGBA")
    y = caption(canvas, t["pill"]) + 40
    d = ImageDraw.Draw(canvas)
    label = ImageFont.truetype(REGULAR, 40)
    # The pill over the home screen, ringing and in a call, side by side.
    ring = Image.open(os.path.join(RAW, lang, "22-pill-ring.png"))
    talk = Image.open(os.path.join(RAW, lang, "22-pill-home.png"))
    box, width = (560, 560, 1080, 980), 450
    for i, (raw, text) in enumerate(((ring, t["pill_labels"][0]), (talk, t["pill_labels"][1]))):
        x = 60 + i * (width + 60)
        bottom = tile(canvas, raw, box, x, y, width)
        d.text((x + width / 2, bottom + 18), text, font=label, fill=SOFT_INK, anchor="ma")
    y = bottom + 100
    # Then the keypad as it is: matches above, keys below, in one piece.
    d.text((W / 2, y), t["t9"], font=ImageFont.truetype(BOLD, 50), fill=INK, anchor="ma")
    y += 80
    keypad = Image.open(os.path.join(RAW, lang, "25-t9.png"))
    width = 700
    tile(canvas, keypad, (24, 252, 1056, 1690), (W - width) // 2, y, width)
    save(canvas, lang, "2-pill")


def stacked(raw, boxes):
    """Pieces of one screen, one under the other: skips a line the picture doesn't need."""
    parts = [raw.convert("RGB").crop(box) for box in boxes]
    out = Image.new("RGB", (parts[0].width, sum(p.height for p in parts)))
    y = 0
    for part in parts:
        out.paste(part, (0, y))
        y += part.height
    return out


def spam(lang):
    """The warning on the incoming screen, then the three ways to turn calls away, with their lists."""
    t = TEXT[lang]
    canvas = glass_background(W, H).convert("RGBA")
    y = caption(canvas, t["spam"]) + 40
    shot = lambda name: Image.open(os.path.join(RAW, lang, name + ".png"))
    incoming, top, bottom = shot("30-spam-incoming"), shot("31-spam-top"), shot("31-spam-bottom")
    # The country card without its "your country code" line: the emulator's SIM is American.
    # The English capture stopped scrolling a few pixels lower down the page.
    dy = {"en": 21}.get(lang, 0)
    abroad = stacked(bottom, [(26, 836 + dy, 1054, 1182 + dy), (26, 1252 + dy, 1054, 1450 + dy)])
    x, width, gap = 120, 840, 22
    for raw, box in (
        (incoming, (0, 190, 1080, 440)),
        (top, (26, 1110, 1054, 1604)),
        (abroad, (0, 0) + abroad.size),
        (bottom, (26, 1620 + dy, 1054, 2160 + dy)),
    ):
        y = tile(canvas, raw, box, x, y, width) + gap
    save(canvas, lang, "3-spam")


def two_phones(lang, name, caption_text, first, second, height=1320):
    canvas = glass_background(W, H).convert("RGBA")
    caption(canvas, caption_text)
    images = [Image.open(os.path.join(RAW, lang, part + ".png")) for part in (first, second)]
    step = W - 24 - width_of(height) - 24
    place(canvas, images[0], height, 24, 390)
    place(canvas, images[1], height, 24 + step, 500)
    save(canvas, lang, name)


def call_screens(lang):
    # Side by side, so that neither phone hides the other's screen; both start below the caption.
    t = TEXT[lang]
    canvas = glass_background(W, H).convert("RGBA")
    top = caption(canvas, t["call"]) + 40
    height = 1180
    gap = (W - 2 * width_of(height)) // 3
    place(canvas, Image.open(os.path.join(RAW, lang, "26-incoming.png")), height, gap, top)
    place(canvas, Image.open(os.path.join(RAW, lang, "26-incall.png")), height, 2 * gap + width_of(height), min(top + 240, H - height - 40))
    save(canvas, lang, "0-call")


if __name__ == "__main__":
    for lang in TEXT:
        feature_graphic(lang)
        intro(lang)
        whatsapp(lang)
        ring(lang)
        pill(lang)
        call_screens(lang)
        spam(lang)
        two_phones(lang, "8-stats", TEXT[lang]["stats"], "27-stats", "27-stats-list")
        print(lang)
