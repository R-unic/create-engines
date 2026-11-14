"""Derives the side textures of the engine-cased cogwheels from Create's andesite ones, recoloured into the casing's steel.

Run once from the repository root with the folder that holds Create's andesite_encased_cogwheel_side.png and
andesite_encased_cogwheel_side_connected.png (extract them from Create's jar):
    python tools/derive_cog_casing.py path/to/create/textures/block
The slot the cog shows through is kept exactly where Create draws it, so Create's models fit; only the colours change,
by brightness, onto the casing steel ramp. The result is committed, so this does not need running again.
"""
import os
import struct
import sys
import zlib

OUT = "src/main/resources/assets/create_engines/textures/block"
STEEL = [tuple(int(code[i:i + 2], 16) for i in (0, 2, 4)) for code in
         ("353b39", "404543", "494c4a", "505351", "585b58", "60635f", "686c68", "707570", "797e7b", "828784", "8f9691",
          "9aa49d", "a8b3ab", "b4c1b8")]


def read_png(path):
    data = open(path, "rb").read()
    pos, palette, alpha, idat, width, height, depth, kind = 8, [], [], b"", 0, 0, 8, 3
    while pos < len(data):
        length, tag = struct.unpack(">I4s", data[pos:pos + 8])
        body = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if tag == b"IHDR":
            width, height, depth, kind = struct.unpack(">IIBB", body[:10])
        elif tag == b"PLTE":
            palette = [tuple(body[i:i + 3]) for i in range(0, len(body), 3)]
        elif tag == b"tRNS":
            alpha = list(body)
        elif tag == b"IDAT":
            idat += body
    assert depth == 8 and kind in (3, 6), (depth, kind)
    step = 1 if kind == 3 else 4
    raw = zlib.decompress(idat)
    stride = width * step
    rows, prev = [], bytearray(stride)
    for y in range(height):
        base = y * (stride + 1)
        filt, line = raw[base], bytearray(raw[base + 1:base + 1 + stride])
        for i in range(stride):
            a = line[i - step] if i >= step else 0
            b = prev[i]
            c = prev[i - step] if i >= step else 0
            if filt == 1:
                line[i] = (line[i] + a) & 255
            elif filt == 2:
                line[i] = (line[i] + b) & 255
            elif filt == 3:
                line[i] = (line[i] + (a + b) // 2) & 255
            elif filt == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[i] = (line[i] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        prev = line
        if kind == 3:
            rows.append([palette[v] + (alpha[v] if v < len(alpha) else 255,) for v in line])
        else:
            rows.append([tuple(line[i:i + 4]) for i in range(0, stride, 4)])
    return rows


def write_png(path, rows):
    raw = b"".join(b"\x00" + b"".join(bytes(p) for p in row) for row in rows)

    def chunk(kind, body):
        return struct.pack(">I", len(body)) + kind + body + struct.pack(">I", zlib.crc32(kind + body) & 0xFFFFFFFF)

    with open(path, "wb") as fh:
        fh.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", len(rows[0]), len(rows), 8, 6, 0, 0, 0)) +
                 chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def recolour(rows):
    light = [sum(p[:3]) / 3 for row in rows for p in row if p[3]]
    low, high = min(light), max(light)
    result = []
    for row in rows:
        out = []
        for p in row:
            if not p[3]:
                out.append(p)
                continue
            t = (sum(p[:3]) / 3 - low) / max(1, high - low)
            out.append(STEEL[1 + round(t * 11)] + (p[3],))
        result.append(out)
    return result


for name in ("side", "side_connected"):
    rows = read_png(os.path.join(sys.argv[1], "andesite_encased_cogwheel_%s.png" % name))
    write_png(os.path.join(OUT, "engine_cased_cogwheel_%s.png" % name), recolour(rows))
    print("wrote", name, len(rows[0]), "x", len(rows))
