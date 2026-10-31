"""Writes the mod's own block textures under src/main/resources/assets/create_engines/textures/block/.

Run from the repository root: python tools/generate_textures.py
Each texture is 16x16 and drawn here from a seeded random generator, so reruns give the same pixels. They are the metals
and finishes of an engine (cast iron, brushed steel, blackened steel, brass, enamel, ceramic, a read-out screen), used by
tools/generate_models.py in place of the Create and vanilla textures the first models borrowed. The casings, shafts and
pipes keep Create's own textures, so the engine still looks like part of a Create factory.
"""
import os
import random
import struct
import zlib

OUT = "src/main/resources/assets/create_engines/textures/block"
SIZE = 16


def write_png(path, pixels):
    """pixels is SIZE rows of SIZE (r, g, b, a) tuples."""
    raw = b"".join(b"\x00" + b"".join(bytes(px) for px in row) for row in pixels)

    def chunk(kind, data):
        body = kind + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", SIZE, SIZE, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9))
    png += chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as fh:
        fh.write(png)


def clamp(value):
    return max(0, min(255, int(round(value))))


def shade(colour, amount):
    return tuple(clamp(c + amount) for c in colour) + (255,)


def draw(name, painter, seed):
    rng = random.Random(seed)
    pixels = [[painter(x, y, rng) for x in range(SIZE)] for y in range(SIZE)]
    write_png(os.path.join(OUT, name + ".png"), pixels)
    print("wrote", name)


def edge(x, y):
    """How far into the tile a pixel is: 0 on the rim, so rims can be drawn a little darker."""
    return min(x, y, SIZE - 1 - x, SIZE - 1 - y)


def cast_iron(x, y, rng):
    base = (78, 80, 86)
    amount = rng.uniform(-7, 7)
    if rng.random() < 0.06:
        amount += 14
    if rng.random() < 0.05:
        amount -= 16
    if edge(x, y) == 0:
        amount -= 10
    return shade(base, amount)


def steel(x, y, rng):
    base = (178, 182, 190)
    # Brushed along the tile: each row has its own sheen
    row = random.Random(1000 + y).uniform(-9, 9)
    amount = row + rng.uniform(-3, 3)
    if edge(x, y) == 0:
        amount -= 22
    elif x == 1 or y == 1:
        amount += 8
    return shade(base, amount)


def black_oxide(x, y, rng):
    base = (38, 40, 46)
    amount = rng.uniform(-4, 5)
    if rng.random() < 0.04:
        amount += 12
    if edge(x, y) == 0:
        amount -= 6
    return shade(base, amount)


def brass(x, y, rng):
    base = (204, 164, 72)
    # A soft diagonal sheen across the plate, and a few fine scratches
    amount = 10 * ((x + y) % 8 - 4) / 4 + rng.uniform(-6, 6)
    if rng.random() < 0.05:
        amount += 22
    if edge(x, y) == 0:
        amount -= 28
    elif edge(x, y) == 1:
        amount += 6
    return shade(base, amount)


def enamel_red(x, y, rng):
    base = (156, 36, 32)
    amount = rng.uniform(-3, 3)
    if y in (2, 3):
        amount += 18  # the highlight a glossy coat catches
    if edge(x, y) == 0:
        amount -= 24
    return shade(base, amount)


def ceramic(x, y, rng):
    base = (234, 230, 218)
    amount = rng.uniform(-3, 3)
    if x in (3, 4):
        amount += 8
    if x in (11, 12):
        amount -= 10
    if edge(x, y) == 0:
        amount -= 14
    return shade(base, amount)


def screen(x, y, rng):
    base = (14, 30, 22)
    amount = 6 if y % 2 == 0 else 0
    amount += rng.uniform(-2, 2)
    if edge(x, y) == 0:
        amount -= 6
    return shade(base, amount)


def rivet_plate(x, y, rng):
    """Cast flywheel face and similar: dark iron with a ring of rivets."""
    base = (66, 68, 74)
    amount = rng.uniform(-5, 5)
    if (x, y) in {(3, 3), (12, 3), (3, 12), (12, 12), (7, 2), (8, 2), (7, 13), (8, 13), (2, 7), (2, 8), (13, 7), (13, 8)}:
        amount += 30
    if edge(x, y) == 0:
        amount -= 10
    return shade(base, amount)


def main():
    draw("cast_iron", cast_iron, 1)
    draw("steel", steel, 2)
    draw("black_oxide", black_oxide, 3)
    draw("brass", brass, 4)
    draw("enamel_red", enamel_red, 5)
    draw("ceramic", ceramic, 6)
    draw("screen", screen, 7)
    draw("rivet_plate", rivet_plate, 8)


main()
