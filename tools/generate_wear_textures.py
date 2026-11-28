"""Flat item textures: the wear mode's head gaskets and Service Kit, and the piston ring.

Standalone on purpose: generate_textures.py rewrites every texture when it runs, and these four are new. Run from the repo root with
the pure-Python build (C:/Python315/python.exe) so the PNG bytes match the rest:

    C:/Python315/python.exe tools/generate_wear_textures.py
"""
import math
import os
import struct
import zlib

ROOT = "src/main/resources/assets/create_engines/textures/item"


def ramp(*codes):
    return [tuple(int(code[i:i + 2], 16) for i in (0, 2, 4)) for code in codes]


# Dark to light, in Create's own colours
COPPER = ramp("3d161e", "5b2924", "793b2b", "904931", "9a5038", "a75a40", "b26247", "c26b4c", "c87456", "d67b5b", "e3826c", "f0a184")
BRASS = ramp("592424", "724731", "9e6947", "af794f", "bf8e55", "c79758", "cea05a", "d7aa5e", "e4b763", "eec168", "f7cb6c", "fbdc7d")
CARDBOARD = ramp("4a3822", "62482a", "7a5a34", "8f6b3d", "a58150", "bb9c6c", "cfb78c", "e0cba3")
RUBBER = ramp("0d0f10", "14181a", "1a1f22", "222227", "2a3136", "3c4c52", "4f5f66")
STOCK = ramp("2e2e2e", "444444", "595959", "6e6e6e", "838383", "989898", "adadad", "c2c2c2")
RED = ramp("3d161e", "592424", "7a2a2d", "93333a", "a93f4b", "b84e55", "c8625f")


def hashed(x, y, seed):
    n = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def write_png(name, rows):
    raw = b"".join(b"\x00" + b"".join(bytes(p) for p in row) for row in rows)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    path = os.path.join(ROOT, name + ".png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as fh:
        fh.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0)) +
                 chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))
    print("wrote", name)


CLEAR = (0, 0, 0, 0)


def gasket(colours, seed, bolts, mid):
    """A flat ring seen from above: a dark rim, a lit inside, a hole in the middle and, for the better ones, bolt holes."""
    rows = []
    last = len(colours) - 1
    for y in range(16):
        row = []
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            r = math.hypot(dx, dy)
            if r > 7.4 or r < 3.0:
                row.append(CLEAR)
                continue
            lit = -dx * 0.5 - dy * 0.5  # light from the top left
            shade = mid + lit * 0.35 + (hashed(x, y, seed) - 0.5) * 1.2
            if r > 6.4 or r < 4.0:
                shade -= 2.2  # the rim of the ring
            if bolts and abs(r - 5.2) < 0.7 and (abs(dx) < 1.2 or abs(dy) < 1.2) and r > 4.4:
                shade -= 3.2  # a bolt hole on each side
            index = max(0, min(last, round(shade)))
            row.append(colours[index] + (255,))
        rows.append(row)
    return rows


def kit(body=BRASS, band=RED):
    """A small brass tin with a red band and a spanner mark: the Service Kit. The creative one is the same tin in purple."""
    rows = []
    for y in range(16):
        row = []
        for x in range(16):
            if not (1 <= x <= 14 and 3 <= y <= 13):
                row.append(CLEAR)
                continue
            edge = x in (1, 14) or y in (3, 13)
            lit = 7 - (x - 1) * 0.12 - (y - 3) * 0.2 + (hashed(x, y, 5) - 0.5) * 1.2
            if edge:
                lit -= 3.2
            colour = body
            if 6 <= y <= 9:
                colour, lit = band, 3.6 + (hashed(x, y, 6) - 0.5) - (2 if edge else 0)
            # a cross of two light strokes in the red band
            if y in (7, 8) and 4 <= x <= 11 or (x in (7, 8) and 5 <= y <= 10):
                colour, lit = body, 9.0
            row.append(colour[max(0, min(len(colour) - 1, round(lit)))] + (255,))
        rows.append(row)
    return rows


PURPLE = ramp("1f0d33", "2e1650", "42206e", "57308c", "6c40a6", "8253bd", "9868d0", "ae80df", "c398ea", "d6b2f2", "e6ccf8", "f4e6fd")
PINK = ramp("3d0d2b", "5c1642", "80215c", "a32f76", "c0428e", "d65ba5", "e87abb")


# Hand-placed: two rings lying one on the other seen from above and in front, the upper one split. k outline, h lit, m mid, s shade
PISTON_RINGS = (
    "................",
    ".....kkkkk......",
    "...kkhhhhhk.k...",
    "..khhkkkkkk.kk..",
    ".khkk.......khk.",
    ".khk.........mk.",
    ".khmkk.....kkmk.",
    ".kkhmmkkkkkmmkk.",
    ".khkhmmmmmmmkmk.",
    ".kmkkkkkkkkkksk.",
    ".khmk......kmsk.",
    ".khmmkkkkkkmmsk.",
    "..kkmmmmmmmsskk.",
    "....kksssssk....",
    "......kkkkk.....",
    "................",
)


def piston_rings():
    colours = {".": CLEAR, "k": STOCK[0] + (255,), "h": STOCK[7] + (255,), "m": STOCK[5] + (255,), "s": STOCK[3] + (255,)}
    return [[colours[c] for c in row] for row in PISTON_RINGS]


write_png("piston_ring", piston_rings())
write_png("stock_gasket", gasket(STOCK, 10, False, 3.5))
write_png("cardboard_gasket", gasket(CARDBOARD, 11, False, 4.0))
write_png("rubber_gasket", gasket(RUBBER, 12, False, 3.5))
write_png("copper_gasket", gasket(COPPER, 13, True, 7.5))
write_png("service_kit", kit())
write_png("creative_service_kit", kit(PURPLE, PINK))
