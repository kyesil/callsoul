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
    "de-DE": "Telefon und Kontakte in einem: Labels, Spam-Warnungen und Wischen zum Anrufen.",
    "es-ES": "Teléfono y contactos en uno: etiquetas, avisos de spam y desliza para llamar.",
    "es-419": "Teléfono y contactos en uno: etiquetas, avisos de spam y desliza para llamar.",
    "fr-FR": "Téléphone et contacts réunis : libellés, alertes spam et balayage pour appeler.",
    "it-IT": "Telefono e contatti in uno: etichette, avvisi di spam e scorri per chiamare.",
    "pt-BR": "Telefone e contatos juntos: marcadores, alertas de spam e deslize para ligar.",
    "id": "Telepon dan kontak jadi satu: label, peringatan spam, dan geser untuk memanggil.",
}
RELEASE = {
    "tr-TR": "İlk sürüm: telefon ve rehber bir arada, kaydırarak arama ve cevaplama, numara etiketleri, bulut spam uyarısı, arama hapı, on dil.",
    "en-US": "First release: phone and contacts in one, swipe to call and answer, number labels, cloud spam warnings, a floating call pill, ten languages.",
    "ar": "الإصدار الأول: الهاتف وجهات الاتصال معًا، الاتصال والرد بالسحب، تسميات الأرقام، تحذير من المكالمات المزعجة، فقاعة المكالمة، عشر لغات.",
    "ru-RU": "Первая версия: звонилка и контакты вместе, звонок и ответ смахиванием, метки номеров, облачный антиспам, плашка вызова, десять языков.",
    "de-DE": "Erste Version: Telefon und Kontakte in einem, Anrufen und Annehmen per Wischen, Labels für Nummern, Spam-Warnungen aus der Cloud, eine schwebende Anrufkapsel, zehn Sprachen.",
    "es-ES": "Primera versión: teléfono y contactos en uno, desliza para llamar y responder, etiquetas para números, avisos de spam en la nube, una píldora de llamada flotante, diez idiomas.",
    "fr-FR": "Première version : téléphone et contacts réunis, balayer pour appeler et répondre, libellés pour les numéros, alertes spam dans le cloud, une pastille d'appel flottante, dix langues.",
    "it-IT": "Prima versione: telefono e contatti in uno, scorri per chiamare e rispondere, etichette per i numeri, avvisi di spam dal cloud, una pillola di chiamata mobile, dieci lingue.",
    "pt-BR": "Primeira versão: telefone e contatos juntos, deslize para ligar e atender, marcadores para números, alertas de spam na nuvem, uma cápsula de chamada flutuante, dez idiomas.",
    "id": "Versi pertama: telepon dan kontak jadi satu, geser untuk memanggil dan menjawab, label untuk nomor, peringatan spam dari cloud, pil panggilan mengambang, sepuluh bahasa.",
    "es-419": "Primera versión: teléfono y contactos en uno, desliza para llamar y responder, etiquetas para números, avisos de spam en la nube, una píldora de llamada flotante, diez idiomas.",
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
