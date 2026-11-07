"""
Short store texts kept in one place, written to store/listing/<lang>/ and checked against Play's
limits (title 30, short description 80, full description 4000 characters).

    python store/tools/texts.py
"""
import os

HERE = os.path.dirname(os.path.abspath(__file__))
LISTING = os.path.abspath(os.path.join(HERE, "..", "listing"))
NOTES = os.path.abspath(os.path.join(HERE, "..", "release-notes"))

TITLE = "Better Call Soul"
SHORT = {
    "tr-TR": "Telefon ve rehber bir arada: etiketler, spam uyarısı, kaydırarak arama.",
    "en-US": "Phone and contacts in one: labels, spam warnings and swipe to call.",
    "ar": "الهاتف وجهات الاتصال معًا: تسميات وتحذير من المزعج واتصال بالسحب.",
    "ru-RU": "Звонилка и контакты вместе: метки, антиспам и звонок смахиванием.",
}
RELEASE = {
    "tr-TR": "İlk sürüm: telefon ve rehber bir arada, kaydırarak arama ve cevaplama, numara etiketleri, bulut spam uyarısı, arama hapı, dört dil.",
    "en-US": "First release: phone and contacts in one, swipe to call and answer, number labels, cloud spam warnings, a floating call pill, four languages.",
    "ar": "الإصدار الأول: الهاتف وجهات الاتصال معًا، الاتصال والرد بالسحب، تسميات الأرقام، تحذير من المكالمات المزعجة، فقاعة المكالمة، أربع لغات.",
    "ru-RU": "Первая версия: звонилка и контакты вместе, звонок и ответ смахиванием, метки номеров, облачный антиспам, плашка вызова, четыре языка.",
}

for lang in SHORT:
    folder = os.path.join(LISTING, lang)
    os.makedirs(folder, exist_ok=True)
    for name, text, limit in [("title.txt", TITLE, 30), ("short_description.txt", SHORT[lang], 80)]:
        assert len(text) <= limit, (lang, name, len(text))
        with open(os.path.join(folder, name), "w", encoding="utf-8", newline="\n") as f:
            f.write(text + "\n")
    full = open(os.path.join(folder, "full_description.txt"), encoding="utf-8").read()
    assert len(full) <= 4000, (lang, "full", len(full))
    assert len(RELEASE[lang]) <= 500
    os.makedirs(NOTES, exist_ok=True)
    with open(os.path.join(NOTES, lang + ".txt"), "w", encoding="utf-8", newline="\n") as f:
        f.write(RELEASE[lang] + "\n")
    print(f"{lang}: short {len(SHORT[lang])}/80, full {len(full)}/4000")
