"""Renders block models to a labelled contact sheet, without starting the game. Pure Python.

Run from the repository root: python tools/preview_models.py OUT.png [--scale N] [--columns N] [--views N] PATH...
Each PATH is a model under models/block (cylinder/block), a whole folder (cylinder), or several models drawn in one cell,
joined with + (cylinder/block+cylinder/piston). A bare @x,y,z after a model moves it, in pixels. Create's textures are read
from the folder named by --create-textures, or drawn flat grey if it is not given.
"""
import json
import math
import os
import struct
import sys
import zlib

ROOT = "src/main/resources/assets/create_engines"
BLOCK_MODELS = os.path.join(ROOT, "models", "block")


def read_png(path):
    with open(path, "rb") as fh:
        data = fh.read()
    pos, palette, alpha, idat = 8, None, None, b""
    while pos < len(data):
        size = struct.unpack(">I", data[pos:pos + 4])[0]
        kind = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + size]
        pos += 12 + size
        if kind == b"IHDR":
            width, height, depth, colour = struct.unpack(">IIBB", body[:10])
        elif kind == b"PLTE":
            palette = [tuple(body[i:i + 3]) for i in range(0, len(body), 3)]
        elif kind == b"tRNS":
            alpha = body
        elif kind == b"IDAT":
            idat += body
    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[colour]
    bpp = max(1, channels * depth // 8)
    stride = math.ceil(width * channels * depth / 8)
    raw = zlib.decompress(idat)
    rows, previous, offset = [], bytearray(stride), 0
    for _ in range(height):
        kind = raw[offset]
        line = bytearray(raw[offset + 1:offset + 1 + stride])
        offset += 1 + stride
        for i in range(stride):
            a = line[i - bpp] if i >= bpp else 0
            b = previous[i]
            c = previous[i - bpp] if i >= bpp else 0
            if kind == 1:
                line[i] = (line[i] + a) & 255
            elif kind == 2:
                line[i] = (line[i] + b) & 255
            elif kind == 3:
                line[i] = (line[i] + (a + b) // 2) & 255
            elif kind == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[i] = (line[i] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        previous = line
        pixels = []
        for x in range(width):
            if colour == 3:
                index = (line[x * depth // 8] >> (8 - depth - (x * depth) % 8)) & ((1 << depth) - 1)
                pixels.append(palette[index] + ((alpha[index] if alpha and index < len(alpha) else 255),))
            elif colour == 2:
                pixels.append(tuple(line[x * 3:x * 3 + 3]) + (255,))
            elif colour == 6:
                pixels.append(tuple(line[x * 4:x * 4 + 4]))
            elif colour == 0:
                pixels.append((line[x],) * 3 + (255,))
            else:
                pixels.append((line[x * 2],) * 3 + (line[x * 2 + 1],))
        rows.append(pixels)
    return rows


def write_png(path, rows):
    raw = b"".join(b"\x00" + bytes(v for p in row for v in p[:3]) for row in rows)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    with open(path, "wb") as fh:
        fh.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", len(rows[0]), len(rows), 8, 2, 0, 0, 0)) +
                 chunk(b"IDAT", zlib.compress(raw, 6)) + chunk(b"IEND", b""))


_textures = {}


def texture(name, create_root):
    if name in _textures:
        return _textures[name]
    namespace, _, rest = name.partition(":")
    path = None
    if namespace == "create_engines":
        path = os.path.join(ROOT, "textures", rest + ".png")
    elif namespace == "create" and create_root:
        path = os.path.join(create_root, "assets", "create", "textures", rest + ".png")
    rows = read_png(path) if path and os.path.exists(path) else [[(128, 128, 128, 255)]]
    # An animated strip draws its first frame
    if path and os.path.exists(path + ".mcmeta") and len(rows) > len(rows[0]):
        rows = rows[:len(rows[0])]
    _textures[name] = rows
    return rows


def load_model(path):
    with open(os.path.join(BLOCK_MODELS, path + ".json")) as fh:
        data = json.load(fh)
    if "elements" not in data and "parent" in data:
        return load_model(data["parent"].split("block/", 1)[1])
    return data


SHADE = {"up": 1.0, "north": 0.8, "south": 0.8, "east": 0.6, "west": 0.6, "down": 0.5}


def quad(face, f, t):
    """The four corners of a face in the order (u0v0, u1v0, u1v1, u0v1), and the default uv rectangle."""
    x1, y1, z1 = f
    x2, y2, z2 = t
    if face == "north":
        return [(x2, y2, z1), (x1, y2, z1), (x1, y1, z1), (x2, y1, z1)], (16 - x2, 16 - y2, 16 - x1, 16 - y1)
    if face == "south":
        return [(x1, y2, z2), (x2, y2, z2), (x2, y1, z2), (x1, y1, z2)], (x1, 16 - y2, x2, 16 - y1)
    if face == "east":
        return [(x2, y2, z2), (x2, y2, z1), (x2, y1, z1), (x2, y1, z2)], (16 - z2, 16 - y2, 16 - z1, 16 - y1)
    if face == "west":
        return [(x1, y2, z1), (x1, y2, z2), (x1, y1, z2), (x1, y1, z1)], (z1, 16 - y2, z2, 16 - y1)
    if face == "up":
        return [(x1, y2, z1), (x2, y2, z1), (x2, y2, z2), (x1, y2, z2)], (x1, z1, x2, z2)
    return [(x1, y1, z2), (x2, y1, z2), (x2, y1, z1), (x1, y1, z1)], (x1, 16 - z2, x2, 16 - z1)


FONT = {
    "A": "010101111101101", "B": "110101110101110", "C": "011100100100011", "D": "110101101101110", "E": "111100110100111",
    "F": "111100110100100", "G": "011100101101011", "H": "101101111101101", "I": "111010010010111", "J": "001001001101010",
    "K": "101101110101101", "L": "100100100100111", "M": "101111111101101", "N": "110101101101101", "O": "010101101101010",
    "P": "110101110100100", "Q": "010101101110011", "R": "110101110101101", "S": "011100010001110", "T": "111010010010010",
    "U": "101101101101111", "V": "101101101101010", "W": "101101111111101", "X": "101101010101101", "Y": "101101010010010",
    "Z": "111001010100111", "0": "010101101101010", "1": "010110010010111", "2": "110001010100111", "3": "110001010001110",
    "4": "101101111001001", "5": "111100110001110", "6": "011100110101010", "7": "111001010010010", "8": "010101010101010",
    "9": "010101011001110", "_": "000000000000111", "/": "001001010100100", "+": "000010111010000", "-": "000000111000000",
}


def label(image, text, x, y):
    for ch in text.upper():
        glyph = FONT.get(ch)
        if glyph:
            for n, bit in enumerate(glyph):
                if bit == "1":
                    for sy in range(2):
                        for sx in range(2):
                            image[y + n // 3 * 2 + sy][x + n % 3 * 2 + sx] = (240, 240, 240)
        x += 8


def render(models, size, yaw_deg, create_root, pitch_deg=30.0, background=(66, 72, 62)):
    """Draws the models into a size x size image, as the game draws a block in an inventory slot."""
    yaw, pitch = math.radians(yaw_deg), math.radians(pitch_deg)
    cy, sy, cp, sp = math.cos(yaw), math.sin(yaw), math.cos(pitch), math.sin(pitch)
    image = [[background] * size for _ in range(size)]
    depth = [[-1e9] * size for _ in range(size)]
    scale = size / 34.0

    def project(p):
        x, y, z = p[0] - 8, p[1] - 8, p[2] - 8
        rx = x * cy + z * sy
        rz = -x * sy + z * cy
        ry = y * cp - rz * sp
        rd = y * sp + rz * cp
        return size / 2 + rx * scale, size / 2 - ry * scale, rd

    for path, offset in models:
        data = load_model(path)
        textures = data.get("textures", {})
        for element in data.get("elements", []):
            f = [element["from"][k] + offset[k] for k in range(3)]
            t = [element["to"][k] + offset[k] for k in range(3)]
            for face, spec in element["faces"].items():
                corners, default_uv = quad(face, f, t)
                pts = [project(p) for p in corners]
                # Facing the camera: the signed area of the quad on screen
                area = (pts[1][0] - pts[0][0]) * (pts[3][1] - pts[0][1]) - (pts[1][1] - pts[0][1]) * (pts[3][0] - pts[0][0])
                if area <= 0:
                    continue
                name = spec["texture"]
                while name.startswith("#"):
                    name = textures.get(name[1:], "missing")
                sprite = texture(name, create_root)
                sw, sh = len(sprite[0]), len(sprite)
                uv = spec.get("uv")
                if uv is None:
                    corners, uv = quad(face, element["from"], element["to"])
                    pts = [project((c[0] + offset[0], c[1] + offset[1], c[2] + offset[2])) for c in corners]
                u0, v0, u1, v1 = uv
                ax, ay = pts[1][0] - pts[0][0], pts[1][1] - pts[0][1]
                bx, by = pts[3][0] - pts[0][0], pts[3][1] - pts[0][1]
                det = ax * by - ay * bx
                if abs(det) < 1e-6:
                    continue
                shade = SHADE[face]
                da, db = pts[1][2] - pts[0][2], pts[3][2] - pts[0][2]
                x0 = max(0, int(min(p[0] for p in pts)))
                x1 = min(size - 1, int(max(p[0] for p in pts)) + 1)
                y0 = max(0, int(min(p[1] for p in pts)))
                y1 = min(size - 1, int(max(p[1] for p in pts)) + 1)
                for py in range(y0, y1 + 1):
                    row, zrow = image[py], depth[py]
                    for px in range(x0, x1 + 1):
                        dx, dy = px + 0.5 - pts[0][0], py + 0.5 - pts[0][1]
                        s = (dx * by - dy * bx) / det
                        q = (ax * dy - ay * dx) / det
                        if s < 0 or s > 1 or q < 0 or q > 1:
                            continue
                        z = pts[0][2] + da * s + db * q
                        if z <= zrow[px]:
                            continue
                        tx = (u0 + (u1 - u0) * s) * sw / 16
                        ty = (v0 + (v1 - v0) * q) * sh / 16
                        pixel = sprite[min(sh - 1, max(0, int(ty)))][min(sw - 1, max(0, int(tx)))]
                        if pixel[3] < 128:
                            continue
                        zrow[px] = z
                        row[px] = (int(pixel[0] * shade), int(pixel[1] * shade), int(pixel[2] * shade))
    return image


def parse_cell(spec):
    models = []
    for part in spec.split("+"):
        path, _, at = part.partition("@")
        offset = tuple(float(v) for v in at.split(",")) if at else (0.0, 0.0, 0.0)
        models.append((path, offset))
    return models


def main():
    args = sys.argv[1:]
    out = args.pop(0)
    cell, columns, views, create_root = 300, 5, 1, None
    specs = []
    while args:
        arg = args.pop(0)
        if arg == "--scale":
            cell = int(args.pop(0))
        elif arg == "--columns":
            columns = int(args.pop(0))
        elif arg == "--views":
            views = int(args.pop(0))
        elif arg == "--create-textures":
            create_root = args.pop(0)
        else:
            specs.append(arg)
    cells = []
    for spec in specs:
        if os.path.isdir(os.path.join(BLOCK_MODELS, spec)):
            for name in sorted(os.listdir(os.path.join(BLOCK_MODELS, spec))):
                if name.endswith(".json"):
                    cells.append(spec + "/" + name[:-5])
        else:
            cells.append(spec)
    yaws = [225, 45, 135, 315][:views]
    total = len(cells) * len(yaws)
    rows_count = math.ceil(total / columns)
    sheet = [[(40, 44, 40)] * (columns * cell) for _ in range(rows_count * cell)]
    index = 0
    for spec in cells:
        for yaw in yaws:
            image = render(parse_cell(spec), cell - 2, yaw, create_root)
            ox, oy = (index % columns) * cell, (index // columns) * cell
            for y, row in enumerate(image):
                sheet[oy + y][ox:ox + len(row)] = row
            label(sheet, spec.replace("_", "-")[:(cell - 8) // 8], ox + 4, oy + 4)
            index += 1
    write_png(out, sheet)
    print("wrote", out, len(sheet[0]), "x", len(sheet))


main()
