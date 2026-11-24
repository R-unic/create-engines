"""Typesets "CREATE" in Minecraft's own title-screen font, because Create ships no lettering.

    python tools/create_lettering.py [output.png]      writes docs/images/create_lettering.png

Minecraft's logo texture (assets/minecraft/textures/gui/title/minecraft.png) has the letters C, R, A and T and an E, so each
letter is cut out with its outline and kept in the logo's own grey stone, then set in the order CREATE. `sized(width)` is what the title cards, thumbnail and listing tile place directly above the wordmark. Needs Pillow, numpy and scipy (the Python 3.12 install here has them) and a Gradle build having unpacked the client resources.
"""
import io
import sys
import zipfile
from pathlib import Path

import numpy as np
from PIL import Image
from scipy import ndimage as ndi

ROOT = Path(__file__).resolve().parents[1]
IMAGES = ROOT / "docs" / "images"

ORDER = "CREATE"
# the letters are cut from the logo's own letters (MINECRAFT has C, R, E, A and T): the x-ranges of each letter's face in the
# 1024x256 texture, found by labelling the lit pixels (E is used twice)
SPANS = {"E": (359, 466), "C": (475, 578), "R": (587, 694), "A": (695, 811), "T": (911, 1014)}
OUTLINE = 9
LEAN_PER_COLUMN = -0.0001  # the logo's letters lean their bottoms toward its middle by this many columns per row for each column away
GAP = 10   # outlines are 9 px thick, so the faces end up 12 px apart and no outline cuts into a neighbour


def title_texture():
    """The game's own title-screen logo texture, from the resources jar the Gradle build unpacked."""
    sys.path.insert(0, str(ROOT / "tools" / "trailer"))
    import mcfont
    with zipfile.ZipFile(mcfont._find_jar()) as jar:
        return Image.open(io.BytesIO(jar.read("assets/minecraft/textures/gui/title/minecraft.png"))).convert("RGBA")


def letter(texture, ch, target=None):
    """One letter with its own black outline and extrusion, recoloured, cropped to its pixels."""
    a = np.array(texture).astype(int)
    if ch == "T":  # the logo's T has its stem's foot curl left, which reads as a J: carry the stem straight down instead
        for y in range(134, 167):
            a[y] = np.roll(a[133], round((y - 133) * 0.28), axis=0)
    lit = (a[..., 3] > 200) & (a[..., :3].mean(axis=2) > 35)
    x0, x1 = SPANS[ch]
    box = np.zeros_like(lit)
    box[:, x0 - 3:x1 + 3] = True
    labels, _ = ndi.label(lit & box)
    biggest = np.argmax(ndi.sum(lit & box, labels, range(1, labels.max() + 1))) + 1
    face = labels == biggest
    pixels = np.where(face[..., None], a, 0).astype("uint8")  # the lit stone only: the outline is drawn round the whole word
    image = Image.fromarray(pixels, "RGBA")
    # The logo is in perspective: letters left of its middle lean one way and letters right of it the other, more the further out
    # they are (the E about -0.1 columns per row, the T about +0.27). A letter keeps the lean it was cut with unless `target`
    # asks for another, which is what lets it take the lean of the place it is set in the new word.
    left = lambda y: np.nonzero(face[y])[0].min()
    lean = (left(120) - left(40)) / 80
    if target is not None:
        d = lean - target
        image = image.transform(image.size, Image.AFFINE, (1, d, -d * 90, 0, 1, 0), resample=Image.BICUBIC)
    left, _, right, _ = image.getbbox()
    return image.crop((left, 0, right, image.height))  # every letter keeps the logo's own height, so they share a baseline


def lettering(text=ORDER):
    texture = title_texture()
    glyphs = [letter(texture, ch) for ch in text]
    # where each letter's middle falls in the word, then the lean the logo gives a letter at that distance from its middle
    # (fitted to the logo: about 0.0006 columns per row for each column out)
    widths = [g.width for g in glyphs]
    total = sum(widths) + GAP * (len(widths) - 1)
    centres, x = [], 0
    for w in widths:
        centres.append(x + w / 2 - total / 2)
        x += w + GAP
    glyphs = [letter(texture, ch, LEAN_PER_COLUMN * c) for ch, c in zip(text, centres)]
    pad = OUTLINE + 2
    width = sum(g.width for g in glyphs) + GAP * (len(glyphs) - 1) + 2 * pad
    word = Image.new("RGBA", (width, texture.height + 2 * pad), (0, 0, 0, 0))
    x = pad
    for g in glyphs:
        word.alpha_composite(g, (x, pad))
        x += g.width + GAP
    solid = np.array(word.getchannel("A")) > 0
    around = ndi.binary_dilation(solid, structure=np.ones((3, 3), bool), iterations=OUTLINE)
    image = Image.new("RGBA", word.size, (0, 0, 0, 0))
    image.paste((0, 0, 0, 255), (0, 0), Image.fromarray((around * 255).astype("uint8")))
    image.alpha_composite(word)
    return image.crop(image.getbbox())


def sized(width):
    """The lettering scaled to `width` pixels wide, on transparent."""
    image = lettering()
    return image.resize((width, round(image.height * width / image.width)), Image.LANCZOS)


if __name__ == "__main__":
    target = Path(sys.argv[1]) if len(sys.argv) > 1 else IMAGES / "create_lettering.png"
    lettering().save(target)
    print(target)
