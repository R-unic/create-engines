"""Writes the mod's own textures under src/main/resources/assets/create_engines/textures/.

Run from the repository root: python tools/generate_textures.py
Pure Python, because this machine has no Pillow. Everything comes from seeded functions, so reruns give the same pixels.

The metals are meant to sit next to Create's casings and blocks, so they are built the way Create's are and from Create's
colours: a dark outer rim, a bright inner rim that is lit along the top and left, and an inside of broad, low-contrast
strokes (diagonal on soft metals, horizontal on iron) rather than per-pixel noise. They are 16x16 tiles laid on the models by
position, so a small part shows a crop of the strokes and a large face also shows the rim. tools/generate_models.py uses them
alongside Create's own casings and shafts.

The fluids are greyscale, and the game tints them per fluid (see CEFluids): thin fuels ripple quickly and let the light through,
thick oils move slowly and are nearly opaque. Animations loop, and their frames tile.

The puffs are the sprites of the gas particle (exhaust, vapour, steam): white, because the game tints each puff to
the colour its engine gives it, and shaped and shaded like vanilla's campfire smoke, which is grey and so cannot be
tinted any lighter than itself.
"""
import json
import math
import os
import struct
import zlib

ROOT = "src/main/resources/assets/create_engines/textures"
SIZE = 16


def write_png(path, rows):
    """rows of (r, g, b) or (r, g, b, a) pixels."""
    raw = b"".join(b"\x00" + b"".join(bytes(tuple(p) + (255,) if len(p) == 3 else p) for p in row) for row in rows)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    full = os.path.join(ROOT, path + ".png")
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "wb") as fh:
        fh.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", len(rows[0]), len(rows), 8, 6, 0, 0, 0)) +
                 chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))
    print("wrote", path)


def write_strip(path, frames, frametime, interpolate=False):
    """Stacks same-sized frames into one strip, with its animation file beside it."""
    write_png(path, [row for frame in frames for row in frame])
    meta = {"animation": {"frametime": frametime}}
    if interpolate:
        meta["animation"]["interpolate"] = True
    with open(os.path.join(ROOT, path + ".png.mcmeta"), "w", newline="\n") as fh:
        json.dump(meta, fh, indent=2)
        fh.write("\n")


def ramp(*codes):
    return [tuple(int(code[i:i + 2], 16) for i in (0, 2, 4)) for code in codes]


# Dark to light, in Create's own colours
BRASS = ramp("592424", "724731", "9e6947", "af794f", "bf8e55", "c79758", "cea05a", "d7aa5e", "e4b763", "eec168", "f7cb6c", "fbdc7d", "ffeb8c")
COPPER = ramp("3d161e", "5b2924", "793b2b", "904931", "9a5038", "a75a40", "b26247", "c26b4c", "c87456", "d67b5b", "e3826c", "f0a184", "f7bf9f")
STEEL = ramp("353b39", "404543", "494c4a", "505351", "585b58", "60635f", "686c68", "707570", "797e7b", "828784", "8f9691", "9aa49d", "a8b3ab", "b4c1b8")
IRON = ramp("2b2b2e", "353438", "3f3e42", "444444", "494848", "4f4f4f", "595858", "676161", "757171", "8d8b8b", "989797")
OXIDE = ramp("1b1a1d", "1f1e20", "252426", "29282a", "2f2e31", "353437", "3b3a3d", "43414f", "4f4e5a", "565660")
RED = ramp("3d161e", "592424", "7a2a2d", "93333a", "a93f4b", "b84e55", "c8625f", "dc8676", "efb79c")
CERAMIC = ramp("8f8672", "a89d86", "bfb39a", "d4c2a4", "e0d2ae", "e8dfc2", "f2ecd6", "fbf8ea")
WRAP = ramp("3d2c17", "553a1f", "5a4424", "70522e", "7a5a34", "886539", "a58150", "bb9c6c", "cfb78c")
SCREEN = ramp("0d0f10", "14181a", "1a1f22", "222227", "2a3136", "3c4c52")

KINDLED = ramp("4a160b", "7a2a0c", "b0440f", "d9651a", "f08c2a", "f9b245", "ffd877")
SEETHING = ramp("1d2d5a", "2f55a0", "4a84c9", "68b4e0", "9fdcf0", "d0eae9", "ffffff")


def hashed(x, y, seed):
    """A repeatable value in 0..1 for a cell."""
    n = (x * 374761393 + y * 668265263 + round(seed * 1000) * 2147483647) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def pick(colours, index):
    return colours[max(0, min(len(colours) - 1, round(index)))]


def rimmed(colours, inside, outer=(2, 1, 0), inner=(-1, -2, -3, -4)):
    """The frame every Create metal has: a dark outer rim (lit, shaded, corner), and a bright inner rim (top, left, right,
    bottom). inside(x, y) gives the ramp index of each pixel within, as a float."""
    size = len(colours)
    rows = []
    for y in range(SIZE):
        row = []
        for x in range(SIZE):
            if x in (0, 15) and y in (0, 15):
                index = outer[2]
            elif y == 0 or x == 0:
                index = outer[0]
            elif y == 15 or x == 15:
                index = outer[1]
            elif y == 1:
                index = size + inner[0]
            elif x == 1:
                index = size + inner[1]
            elif x == 14:
                index = size + inner[2]
            elif y == 14:
                index = size + inner[3]
            else:
                index = inside(x, y)
            row.append(pick(colours, index))
        rows.append(row)
    return rows


def diagonal(mid, swing, seed, period=9.0):
    """Soft metal: wide bands crossing on the diagonal, a little broken at their edges, in the shadow of the rim."""

    def inside(x, y):
        wave = math.sin((x + y) * 2 * math.pi / period + seed)
        edge = 0.8 * (hashed(x, y, seed) - 0.5)
        shade = -1.5 if x == 2 or y == 2 else -0.5 if x == 13 or y == 13 else 0.0
        return mid + wave * swing + edge + shade

    return inside


def strokes(mid, swing, seed):
    """Iron and steel: rows of horizontal strokes, brightest in the middle of the tile and falling away towards the rim."""

    def inside(x, y):
        centre = 1 - (abs(x - 7.5) / 6.5) ** 2
        row = hashed(0, y, seed) - 0.5
        run = hashed(x // 3 + (y * 5) % 3, y, seed + 1) - 0.5
        return mid + swing * (centre - 0.5) + row * swing * 0.9 + run * swing * 0.6

    return inside


def brass():
    return rimmed(BRASS, diagonal(6.2, 1.5, 0.4))


def copper():
    return rimmed(COPPER, diagonal(6.5, 1.6, 2.1))


def copper_panel():
    """Copper in four cut panels, for pipes and the plate on a head: a dark seam across each way with a lit lip beside it."""
    base = diagonal(6.6, 1.2, 4.3, period=7.0)

    def inside(x, y):
        if x == 7 or y == 7:
            return 2.5
        if x == 8 or y == 8:
            return 9.5
        return base(x, y)

    return rimmed(COPPER, inside)


def steel():
    return rimmed(STEEL, strokes(8.0, 3.2, 7), inner=(-1, -3, -5, -6))


def cast_iron():
    return rimmed(IRON, strokes(5.5, 3.5, 11), outer=(2, 1, 0), inner=(-1, -2, -3, -4))


def black_oxide():
    """Blackened steel: a dark panel with one soft reflection across it, a thin lit rim."""
    return rimmed(OXIDE, diagonal(3.6, 1.0, 5.2, period=14.0), outer=(2, 1, 0), inner=(-1, -2, -3, -4))


def rivet_plate():
    """Dark plate with a ring of rivets, for the flywheel's faces and the recorder's lid."""
    spots = {(3, 3), (12, 3), (3, 12), (12, 12), (7, 2), (8, 2), (7, 13), (8, 13), (2, 7), (2, 8), (13, 7), (13, 8)}
    base = strokes(4.5, 3.0, 13)

    def inside(x, y):
        if (x, y) in spots:
            return 10
        if (x - 1, y) in spots or (x, y - 1) in spots:
            return 0.5
        return base(x, y)

    return rimmed(IRON, inside)


def enamel_red():
    """Gloss paint: flat colour, one highlight along the top where the coat catches the light."""

    def inside(x, y):
        return 5.2 if y in (3, 4) and 2 < x < 13 else 3.8 + 0.5 * (hashed(x // 2, y // 2, 3) - 0.5) - (0.8 if y > 11 else 0)

    return rimmed(RED, inside, outer=(1, 0, 0), inner=(-2, -3, -4, -5))


def ceramic():
    """Glazed insulator: round shading across the tile, bright to the left of the middle."""

    def inside(x, y):
        return 5.6 - abs(x - 5.5) * 0.55 + (hashed(x, y // 3, 5) - 0.5) * 0.5

    return rimmed(CERAMIC, inside, outer=(1, 0, 0), inner=(-1, -2, -4, -5))


def screen():
    """Dark glass: nearly flat, with a single soft reflection in one corner."""

    def inside(x, y):
        return 4.0 if x + y < 9 and (x + y) % 3 == 0 else 3.0 if x + y < 11 else 2.0

    return rimmed(SCREEN, inside, outer=(1, 0, 0), inner=(-3, -3, -4, -4))


def heat_wrap():
    """Fibreglass exhaust wrap: broad overlapping turns running across the tile, each dark along one edge."""
    rows = []
    for y in range(SIZE):
        row = []
        for x in range(SIZE):
            turn = (y + x // 5) % 4
            row.append(pick(WRAP, 5.5 + (1.2 if turn == 0 else -2.0 if turn == 3 else 0.0) + (hashed(x // 2, y, 9) - 0.5)))
        rows.append(row)
    return rows


def grille():
    """Louvres for an air intake, with gaps that show through: steel slats in a frame."""
    rows = rimmed(STEEL, lambda x, y: 9.5 if y % 3 == 2 else 7.0 if y % 3 == 0 else 5.0, inner=(-1, -3, -5, -6))
    for y in range(2, 14):
        if y % 3 == 1:
            for x in range(2, 14):
                rows[y][x] = (0, 0, 0, 0)
    return rows


def periodic_noise(x, y, period_x, period_y, seed):
    """Value noise that repeats every period_x by period_y cells."""
    ix, iy = math.floor(x), math.floor(y)
    fx, fy = x - ix, y - iy
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)

    def at(a, b):
        return hashed(a % period_x, b % period_y, seed)

    top = at(ix, iy) * (1 - fx) + at(ix + 1, iy) * fx
    bottom = at(ix, iy + 1) * (1 - fx) + at(ix + 1, iy + 1) * fx
    return top * (1 - fy) + bottom * fy


def grey_levels(low, high, steps, alpha):
    return [(v, v, v, alpha) for v in (round(low + (high - low) * n / (steps - 1)) for n in range(steps))]


THIN_LEVELS = grey_levels(172, 245, 10, 190)
THICK_LEVELS = grey_levels(150, 225, 10, 245)


def thin_still(i, j, t):
    a = periodic_noise(i / 4 + t * 4, j / 4, 4, 4, 1)
    b = periodic_noise(i / 4, j / 4 - t * 4, 4, 4, 2)
    return 0.15 + a * 0.4 + b * 0.4


def thick_still(i, j, t):
    a = periodic_noise(i / 8 + math.sin(t * math.tau) * 0.5, j / 8 + math.cos(t * math.tau) * 0.5, 2, 2, 3)
    b = periodic_noise(i / 4, j / 4 + t * 4, 4, 4, 4)
    return 0.2 + a * 0.6 + (b - 0.5) * 0.15


def thin_flow(i, j, t):
    streak = periodic_noise(i / 3, j / 8 - t * 4, 11, 4, 5)
    fine = periodic_noise(i / 2, j / 4 - t * 8, 16, 8, 6)
    return 0.15 + streak * 0.65 + (fine - 0.5) * 0.2


def thick_flow(i, j, t):
    wobble = (periodic_noise(i / 8, j / 8 - t * 4, 4, 4, 7) - 0.5) * 3
    rope = periodic_noise((i + wobble) / 5, j / 16 - t * 2, 7, 2, 8)
    return 0.15 + rope * 0.7


def fluid(name, painter, size, frames, frametime, levels):
    strip = [[[pick(levels, painter(i, j, f / frames) * (len(levels) - 1)) for i in range(size)] for j in range(size)]
             for f in range(frames)]
    write_strip("block/fluid/" + name, strip, frametime)


def heat(name, colours, seed):
    """Hot metal crawling: bright cracks that wander over a dark glow, drawn over a heat exchanger when it runs hot."""
    frames = 16
    strip = []
    for f in range(frames):
        t = f / frames
        frame = []
        for j in range(SIZE):
            row = []
            for i in range(SIZE):
                a = periodic_noise(i / 4 + t * 4, j / 4, 4, 4, seed)
                b = periodic_noise(i / 4, j / 4 - t * 4, 4, 4, seed + 1)
                crack = 1 - abs(a - b) * 4
                row.append(pick(colours, (0.3 + 0.7 * max(0.0, crack) * (0.55 + a * 0.6)) * (len(colours) - 1)))
            frame.append(row)
        strip.append(frame)
    write_strip("block/heat_" + name, strip, 3, interpolate=True)


def tread_plate():
    """The advancement tab's background, a seamless tile: dark tread plate with its lugs leaning alternate ways."""
    rows = []
    for j in range(SIZE):
        row = []
        for i in range(SIZE):
            index = 1.0 + (hashed(i // 2, j // 2, 21) > 0.7) * 1.0
            u, v = i % 8 - 3.5, j % 8 - 3.5
            lean = 1 if (i // 8 + j // 8) % 2 == 0 else -1
            along, across = (u + lean * v) / math.sqrt(2), (u - lean * v) / math.sqrt(2)
            if abs(along) < 3.2 and abs(across) < 0.9:
                index = 3 if across < 0 else 2.5
            row.append(pick(IRON, index))
        rows.append(row)
    write_png("gui/advancements/tread_plate", rows)


PUFFS = 4


def puff(seed):
    """A round, ragged cloud: a few overlapping lobes about the middle of the tile, lit from the top left in three
    flat tones with a little mottling, the way vanilla's campfire smoke is drawn. Solid or clear, nothing between."""
    lobes = [(7.5, 7.5, 4.4)]
    for k in range(5):
        turn = 2 * math.pi * (k / 5.0 + hashed(k, 1, seed))
        reach = 2.2 + 2.0 * hashed(k, 2, seed)
        lobes.append((7.5 + reach * math.cos(turn), 7.5 + reach * math.sin(turn), 2.2 + 1.6 * hashed(k, 3, seed)))
    rows = []
    for j in range(SIZE):
        row = []
        for i in range(SIZE):
            # How far inside the nearest lobe's edge this pixel is, in pixels
            depth = max(r - math.hypot(i - x, j - y) for x, y, r in lobes)
            if depth < 0 or depth < 0.8 and hashed(i, j, seed + 5) < 0.3:
                row.append((0, 0, 0, 0))
                continue
            light = 0.5 - (i + j - 15) / 22.0 + 0.16 * (hashed(i // 2, j // 2, seed + 9) - 0.5)
            shade = 255 if light > 0.62 else 236 if light > 0.38 else 214
            row.append((shade, shade, shade, 255))
        rows.append(row)
    return rows


def main():
    for name, painter in (("brass", brass), ("copper", copper), ("copper_panel", copper_panel), ("steel", steel),
                          ("cast_iron", cast_iron), ("black_oxide", black_oxide), ("rivet_plate", rivet_plate),
                          ("enamel_red", enamel_red), ("ceramic", ceramic), ("screen", screen), ("heat_wrap", heat_wrap),
                          ("grille", grille)):
        write_png("block/" + name, painter())
    fluid("thin_still", thin_still, 16, 32, 2, THIN_LEVELS)
    fluid("thin_flow", thin_flow, 32, 32, 2, THIN_LEVELS)
    fluid("thick_still", thick_still, 16, 32, 5, THICK_LEVELS)
    fluid("thick_flow", thick_flow, 32, 32, 4, THICK_LEVELS)
    heat("kindled", KINDLED, 31)
    heat("seething", SEETHING, 41)
    tread_plate()
    for index in range(PUFFS):
        write_png("particle/puff_%d" % index, puff(61 + 7 * index))
    # A spark is one solid square, sized and coloured by the particle that draws it
    write_png("particle/spark", [[(255, 255, 255, 255)] * 4 for _ in range(4)])


main()
