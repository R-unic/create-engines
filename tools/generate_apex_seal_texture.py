"""Flat item texture for the wear mode's apex seals: a Wankel rotor's three tip seals, laid out as the corners of a triangle.

Standalone on purpose, like generate_wear_textures.py: generate_textures.py rewrites every texture when it runs, and this one is new. Run
from the repo root with the pure-Python build (C:/Python315/python.exe) so the PNG bytes match the rest:

    C:/Python315/python.exe tools/generate_apex_seal_texture.py
"""
import os
import struct
import zlib

ROOT = "src/main/resources/assets/create_engines/textures/item"


def ramp(*codes):
    return [tuple(int(code[i:i + 2], 16) for i in (0, 2, 4)) for code in codes]


# Dark to light: Create's iron, and its brass for the tips
IRON = ramp("1a1f22", "2a3136", "3c4c52", "4f5f66", "63777e", "7a8f96", "93a7ad", "b0c0c4")
BRASS = ramp("592424", "724731", "9e6947", "af794f", "bf8e55", "c79758", "cea05a", "d7aa5e", "e4b763", "eec168")
CLEAR = (0, 0, 0, 0)


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


def line(points, a, b):
    """Pixels of a thin straight line from a to b."""
    (x0, y0), (x1, y1) = a, b
    steps = max(abs(x1 - x0), abs(y1 - y0))
    for i in range(steps + 1):
        t = i / steps if steps else 0
        points.add((round(x0 + (x1 - x0) * t), round(y0 + (y1 - y0) * t)))


def seals():
    """Three curved-bar seals meeting at a triangle's corners, each corner a brass tip."""
    corners = [(8, 2), (2, 13), (13, 13)]
    bars = set()
    for a, b in ((corners[0], corners[1]), (corners[1], corners[2]), (corners[2], corners[0])):
        line(bars, a, b)
    tips = set()
    for cx, cy in corners:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                if abs(dx) + abs(dy) <= 1:
                    tips.add((cx + dx, cy + dy))
    rows = []
    for y in range(16):
        row = []
        for x in range(16):
            if (x, y) in tips:
                lit = 6.5 - (x + y) * 0.08 + (hashed(x, y, 21) - 0.5) * 1.4
                row.append(BRASS[max(0, min(len(BRASS) - 1, round(lit)))] + (255,))
            elif (x, y) in bars:
                lit = 4.0 - (x + y - 16) * 0.12 + (hashed(x, y, 22) - 0.5) * 1.4
                row.append(IRON[max(0, min(len(IRON) - 1, round(lit)))] + (255,))
            else:
                row.append(CLEAR)
        rows.append(row)
    return rows


write_png("apex_seals", seals())
