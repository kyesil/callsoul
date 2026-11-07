"""
Builds the public pages from store/privacy-policy.md and store/data-deletion.md into the server,
which serves them at /privacy and /delete-data.

    python store/tools/privacy.py
"""
import html
import json
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
SOURCE = os.path.join(HERE, "..", "privacy-policy.md")
DELETION = os.path.join(HERE, "..", "data-deletion.md")
TARGET = os.path.join(HERE, "..", "..", "server", "src", "privacy-page.ts")

STYLE = """
body{max-width:720px;margin:0 auto;padding:24px 16px 48px;font:16px/1.6 system-ui,sans-serif;color:#1c2126;background:#fff}
h1{font-size:26px;line-height:1.25}h2{margin-top:40px;font-size:20px}
@media(prefers-color-scheme:dark){body{color:#e6e8ea;background:#14181c}a{color:#8ab4f8}}
"""


def inline(text):
    text = html.escape(text, quote=False)
    text = re.sub(r"\*\*(.+?)\*\*", r"<strong>\1</strong>", text)
    text = re.sub(r"`(.+?)`", r"<code>\1</code>", text)
    return re.sub(r"(https://[^\s<)]+)", r'<a href="\1">\1</a>', text)


def body(markdown):
    parts = []
    for line in markdown.splitlines():
        line = line.strip()
        if not line:
            continue
        if line.startswith("## "):
            parts.append(f"<h2>{inline(line[3:])}</h2>")
        elif line.startswith("# "):
            parts.append(f"<h1>{inline(line[2:])}</h1>")
        else:
            parts.append(f"<p>{inline(line)}</p>")
    return "\n".join(parts)


def page(source, title):
    with open(source, encoding="utf-8") as f:
        content = body(f.read())
    return (
        '<!doctype html><html lang="tr"><head><meta charset="utf-8">'
        '<meta name="viewport" content="width=device-width,initial-scale=1">'
        f"<title>{title}</title><style>{STYLE}</style></head><body>\n{content}\n</body></html>\n"
    )


with open(TARGET, "w", encoding="utf-8", newline="\n") as f:
    f.write("// Built from store/*.md by store/tools/privacy.py; edit those files instead.\n")
    privacy = page(SOURCE, "Better Call Soul: Gizlilik / Privacy")
    deletion = page(DELETION, "Better Call Soul: Veri Silme / Data Deletion")
    f.write(f"export const privacyPage = {json.dumps(privacy, ensure_ascii=False)};\n")
    f.write(f"export const deletionPage = {json.dumps(deletion, ensure_ascii=False)};\n")
print("ok")
