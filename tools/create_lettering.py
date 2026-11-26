"""The "CREATE" lettering that goes above the wordmark: docs/images/create_lettering.png, a drawn picture on transparent.

`place()` is what the title cards, thumbnail and listing tile use. The picture has a soft shadow round the letters, so sizes
and positions are measured on the letters themselves and the shadow hangs outside them. Needs Pillow (the Python 3.12
install here has it).
"""
from pathlib import Path

from PIL import Image

PICTURE = Path(__file__).resolve().parents[1] / "docs" / "images" / "create_lettering.png"
SOLID = 250  # alpha above this is a letter or its outline; the shadow is fainter


def _open():
    """The picture, and the box round its letters."""
    picture = Image.open(PICTURE).convert("RGBA")
    return picture, picture.getchannel("A").point(lambda a: 255 if a > SOLID else 0).getbbox()


def height(width):
    """How tall the letters are when `width` pixels wide."""
    _, box = _open()
    return round((box[3] - box[1]) * width / (box[2] - box[0]))


def place(canvas, width, top, left=None):
    """Draws the lettering on `canvas` with the letters `width` pixels wide and their top at `top`, starting at `left` or
    else centred across the canvas."""
    picture, box = _open()
    scale = width / (box[2] - box[0])
    scaled = picture.resize((round(picture.width * scale), round(picture.height * scale)), Image.LANCZOS)
    if left is None:
        left = (canvas.width - width) // 2
    canvas.alpha_composite(scaled, (left - round(box[0] * scale), top - round(box[1] * scale)))
