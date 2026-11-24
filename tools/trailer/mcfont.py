"""Draws text in Minecraft's own font, from the game's font bitmap, so the trailer's words look like the game's.

The glyph sheet (textures/font/ascii.png) and the character table (font/include/default.json) are read from the Minecraft
resources jar that the Gradle build already unpacked (build/moddev/artifacts/*client-extra*.jar). Only the plain ASCII glyphs
are drawn; anything else is skipped with a warning. A glyph's width is its rightmost lit column, and the advance one more
than that, as the game works it out; a space advances four.
"""
import json
import sys
import zipfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
SHEET = "assets/minecraft/textures/font/ascii.png"
TABLE = "assets/minecraft/font/include/default.json"

_glyphs = None


def _find_jar():
    jars = sorted((ROOT / "build" / "moddev" / "artifacts").glob("*client-extra*.jar"), key=lambda p: p.stat().st_mtime)
    if not jars:
        sys.exit("no Minecraft resources jar under build/moddev/artifacts: run a Gradle build once (./gradlew classes)")
    return jars[-1]


def _load():
    """Maps each character to (image, advance): the glyph cropped to its width, and how far the pen moves."""
    global _glyphs
    if _glyphs is not None:
        return _glyphs
    with zipfile.ZipFile(_find_jar()) as jar:
        table = json.loads(jar.read(TABLE).decode("utf-8"))
        with jar.open(SHEET) as handle:
            sheet = Image.open(handle).convert("RGBA")
            sheet.load()
    rows = next(p["chars"] for p in table["providers"] if p.get("file", "").endswith("font/ascii.png"))
    cell_w = sheet.width // len(rows[0])
    cell_h = sheet.height // len(rows)
    glyphs = {}
    for row, line in enumerate(rows):
        for col, char in enumerate(line):
            if char in ("\u0000", " "):
                continue
            cell = sheet.crop((col * cell_w, row * cell_h, (col + 1) * cell_w, (row + 1) * cell_h))
            alpha = cell.getchannel("A")
            width = 0
            for x in range(cell_w):
                if any(alpha.getpixel((x, y)) > 0 for y in range(cell_h)):
                    width = x + 1
            glyphs[char] = (cell, width + 1)
    glyphs[" "] = (None, 4)
    _glyphs = glyphs
    return glyphs


def width(text):
    glyphs = _load()
    return sum(glyphs[c][1] for c in text if c in glyphs)


def render(text, scale=4, color=(255, 255, 255), shadow=True, box=None, pad=0):
    """Returns an RGBA image of the text, enlarged by an integer scale with hard pixels.

    shadow draws the game's dark copy one pixel down and right. box, if given, is an (r, g, b, a) fill behind the text with pad
    (in output pixels) around it.
    """
    glyphs = _load()
    unknown = sorted({c for c in text if c not in glyphs})
    if unknown:
        print(f"mcfont: no glyph for {unknown} in {text!r}", file=sys.stderr)
    w, h = width(text) + 1, 9  # one extra pixel each way for the shadow
    base = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    x = 0
    for char in text:
        if char not in glyphs:
            continue
        cell, advance = glyphs[char]
        if cell is not None:
            if shadow:
                dark = Image.new("RGBA", cell.size, (color[0] // 4, color[1] // 4, color[2] // 4, 255))
                base.alpha_composite(Image.composite(dark, Image.new("RGBA", cell.size, (0, 0, 0, 0)), cell.getchannel("A")), (x + 1, 1))
            tint = Image.new("RGBA", cell.size, color + (255,))
            base.alpha_composite(Image.composite(tint, Image.new("RGBA", cell.size, (0, 0, 0, 0)), cell.getchannel("A")), (x, 0))
        x += advance
    base = base.resize((w * scale, h * scale), Image.NEAREST)
    if box is None:
        return base
    framed = Image.new("RGBA", (base.width + 2 * pad, base.height + 2 * pad), box)
    framed.alpha_composite(base, (pad, pad))
    return framed


if __name__ == "__main__":
    render(" ".join(sys.argv[2:]) or "Create: Engine Engineer", 6).save(sys.argv[1])
