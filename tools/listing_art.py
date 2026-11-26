"""Makes the golden listing picture from the wordmark: docs/images/listing_gold.png, 1024x1024.

    python tools/listing_art.py [output.png]

A warm gold-to-bronze tile with a soft light behind the title, the wordmark (docs/images/wordmark.png) large across the middle,
and the "CREATE" lettering (tools/create_lettering.py) directly above it and the mod's icon above that. Downscale it to 512x512 for a site's icon slot.
Needs Pillow (the Python 3.12 install here has it).
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, str(Path(__file__).resolve().parent))
import create_lettering  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
IMAGES = ROOT / "docs" / "images"
SIZE = 1024


def gradient():
    top, bottom = (255, 214, 102), (150, 84, 28)
    image = Image.new("RGB", (SIZE, SIZE))
    draw = ImageDraw.Draw(image)
    for y in range(SIZE):
        t = y / (SIZE - 1)
        draw.line((0, y, SIZE, y), fill=tuple(round(a + (b - a) * t) for a, b in zip(top, bottom)))
    return image.convert("RGBA")


def glow(strength, radius, centre):
    layer = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    x, y = centre
    ImageDraw.Draw(layer).ellipse((x - radius, y - radius, x + radius, y + radius), fill=(255, 244, 200, strength))
    return layer.filter(ImageFilter.GaussianBlur(radius / 2))


def vignette():
    layer = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    ImageDraw.Draw(layer).rectangle((0, 0, SIZE, SIZE), fill=(60, 20, 0, 150))
    hole = Image.new("L", (SIZE, SIZE), 0)
    ImageDraw.Draw(hole).ellipse((-120, -120, SIZE + 120, SIZE + 120), fill=255)
    hole = hole.filter(ImageFilter.GaussianBlur(150))
    layer.putalpha(Image.eval(hole, lambda v: 150 - v * 150 // 255))
    return layer


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else IMAGES / "listing_gold.png"
    tile = gradient()
    tile.alpha_composite(glow(190, 330, (SIZE // 2, 580)))
    tile.alpha_composite(vignette())

    # the wordmark, with a soft dark shadow under it so the gold letters lift off the gold tile
    mark = Image.open(IMAGES / "wordmark.png").convert("RGBA")
    width = 900
    mark = mark.resize((width, round(mark.height * width / mark.width)), Image.LANCZOS)
    x, y = (SIZE - mark.width) // 2, 520
    pad = 60  # room for the blur, so its edge is not cut into a rectangle
    shadow = Image.new("RGBA", (mark.width + 2 * pad, mark.height + 2 * pad), (40, 14, 0, 0))
    shadow.putalpha(Image.new("L", shadow.size, 0))
    shadow.paste((40, 14, 0, 255), (pad, pad), mark.getchannel("A").point(lambda v: v * 150 // 255))
    shadow = shadow.filter(ImageFilter.GaussianBlur(14))
    tile.alpha_composite(shadow, (x + 6 - pad, y + 22 - pad))
    tile.alpha_composite(mark, (x, y))

    create_lettering.place(tile, 560, 345)
    icon = Image.open(IMAGES / "icon.png").convert("RGBA").resize((170, 170), Image.LANCZOS)
    tile.alpha_composite(icon, ((SIZE - 170) // 2, 150))

    out.parent.mkdir(parents=True, exist_ok=True)
    tile.convert("RGB").save(out)
    print(out)


if __name__ == "__main__":
    main()
