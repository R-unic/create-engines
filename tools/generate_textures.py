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
BLUE = ramp("162447", "1f3766", "27498a", "315ba8", "3f73c4", "5589d6", "71a4e2", "9cc3ee", "cfe3f8")
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


def coil_can():
    """A wound coil's can. The models show a three-pixel patch of it: rows 0 to 2 are the side, lit lid, blue winding and
    shaded foot, and rows 3 to 5 the plain end."""
    winding = (5, 4, 3)

    def pixel(x, y):
        if y == 1:
            return pick(BLUE, winding[x % 3])
        if y < 3:
            return pick(OXIDE, (7 if y == 0 else 2) - 0.8 * (x % 3 == 2))
        return pick(OXIDE, 6 - (x % 3) - (y % 3))

    return [[pixel(x, y) for x in range(SIZE)] for y in range(SIZE)]


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


def enamel_blue():
    """Gloss paint for the electric motor's can, laid like the red: flat colour with one highlight along the top."""

    def inside(x, y):
        return 5.2 if y in (3, 4) and 2 < x < 13 else 3.8 + 0.5 * (hashed(x // 2, y // 2, 5) - 0.5) - (0.8 if y > 11 else 0)

    return rimmed(BLUE, inside, outer=(1, 0, 0), inner=(-2, -3, -4, -5))


def flywheel_steel():
    """Machined steel for the flywheel's rims, flange and hub. It tiles both ways (no frame), and is laid along the length of a
    bar: a bright lit edge, a fine turned groove, a broad flat, and a shaded lower edge, with a little repeating grain."""
    bands = {0: 10.5, 1: 9.5, 2: 8.0, 3: 7.2, 4: 7.6, 5: 4.5, 6: 9.8, 7: 8.2, 8: 8.0, 9: 7.8, 10: 8.2, 11: 7.4, 12: 5.5,
             13: 6.5, 14: 4.0, 15: 2.5}

    def pixel(x, y):
        grain = hashed(x % 8, y, 21) - 0.5
        return bands[y] + 0.9 * grain + (0.5 if (x % 8) in (0, 1) else 0)

    return [[pick(STEEL, pixel(x, y)) for x in range(SIZE)] for y in range(SIZE)]


def flywheel_oxide():
    """Blackened, machined iron for the masses' rings and spokes: dark with a soft lit edge and a fine bead line, tiling both ways."""
    bands = {0: 7.5, 1: 6.0, 2: 4.2, 3: 3.6, 4: 3.4, 5: 3.2, 6: 4.8, 7: 3.0, 8: 2.8, 9: 3.0, 10: 3.2, 11: 3.4, 12: 3.0,
             13: 2.4, 14: 1.6, 15: 1.0}

    def pixel(x, y):
        speck = hashed(x % 8, y, 33) - 0.5
        return bands[y] + 1.1 * speck + (1.0 if (x + y) % 8 == 0 and 5 < y < 13 else 0)

    return [[pick(OXIDE, pixel(x, y)) for x in range(SIZE)] for y in range(SIZE)]


def flywheel_coil():
    """A coil spring in brass, to lay along the pack's length: a ring every four pixels, dark between and lit on the crown,
    shaded to a round bar top to bottom. It tiles both ways."""
    ring = {0: 1.0, 1: 9.0, 2: 11.0, 3: 6.0}

    def pixel(x, y):
        round_bar = 3.2 * ((abs(y - 7.5) / 7.5) ** 2)
        return ring[x % 4] - round_bar + 0.5 * (hashed(x % 4, y, 17) - 0.5)

    return [[pick(BRASS, pixel(x, y)) for x in range(SIZE)] for y in range(SIZE)]


def flywheel_enamel():
    """Red enamel for the performance stripe: gloss colour, a bright line along the top and a thin dark one below, tiling both ways."""
    rows = {0: 5.6, 1: 4.6, 2: 3.6, 3: 3.3, 4: 3.1, 5: 3.0, 6: 2.8, 7: 2.7, 8: 2.7, 9: 2.7, 10: 2.7, 11: 2.6, 12: 2.4,
            13: 2.0, 14: 1.4, 15: 0.8}

    def pixel(x, y):
        return rows[y] + 0.4 * (hashed(x % 8, y, 9) - 0.5) + (0.5 if y == 3 and x % 8 < 5 else 0)

    return [[pick(RED, pixel(x, y)) for x in range(SIZE)] for y in range(SIZE)]


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


GLOW_GLASS = ramp("9a9a9a", "b0b0b0", "c4c4c4", "d6d6d6", "e6e6e6", "f4f4f4", "ffffff")


def column_glow():
    """Sight-window glass that glows: pale and nearly white, because the game tints it to the colour of the fluid inside."""

    def inside(x, y):
        return 4.5 + 1.2 * math.sin((x - y) * 0.9) + (hashed(x, y, 3) - 0.5) * 0.8 - (x + y) * 0.04

    return rimmed(GLOW_GLASS, inside, outer=(1, 0, 0), inner=(-2, -2, -3, -3))


def heat_wrap():
    """Fibreglass exhaust wrap: broad turns running across the tile, each lit along the top and dark where the next one
    laps over it."""
    turn = (7.0, 6.0, 5.0, 5.0, 2.5)
    return [[pick(WRAP, turn[(y + x // 8) % 5] + (0.4 if (x // 4 + y // 5) % 3 == 0 else 0.0)) for x in range(SIZE)]
            for y in range(SIZE)]


def grille():
    """Louvres for an air intake, with gaps that show through: steel slats in a frame."""
    rows = rimmed(STEEL, lambda x, y: 9.5 if y % 3 == 2 else 7.0 if y % 3 == 0 else 5.0, inner=(-1, -3, -5, -6))
    for y in range(2, 14):
        if y % 3 == 1:
            for x in range(2, 14):
                rows[y][x] = (0, 0, 0, 0)
    return rows


def pleats():
    """A paper filter element: upright folds of slightly different widths and shades, as pressed paper sits, in shadow
    under the end plates (it is seen on columns 3 to 12 and rows 2 to 13)."""
    folds = (5.5, 6.5, 3.0, 5.0, 6.0, 4.5, 1.5, 6.0, 7.0, 5.0, 2.5, 5.5, 6.5, 3.5, 5.0, 4.0)

    def pixel(x, y):
        shade = -2.0 if y <= 2 else -1.0 if y == 3 else -1.0 if y >= 13 else 0.0
        return folds[x] + shade + (0.5 if (y + 3 * x) % 11 < 3 else 0.0)

    return [[pick(CERAMIC, pixel(x, y)) for x in range(SIZE)] for y in range(SIZE)]


def finned_core():
    """A radiator core: level fins held between two side rails, each fin catching the light towards the left and the
    gaps darkest under the top tank (it is seen on columns 3 to 12 and rows 3 to 12)."""

    def pixel(x, y):
        if x in (3, 12):
            return 8.0 if x == 3 else 4.0
        if y % 3 == 0:
            return 10.5 - max(0, x - 6) * 0.45 + (0.6 if (x + y) % 7 < 2 else 0.0)
        if y % 3 == 1:
            return 6.0 - max(0, x - 8) * 0.4
        return 0.0 if y < 6 else 1.5

    return [[pick(STEEL, pixel(x, y)) for x in range(SIZE)] for y in range(SIZE)]


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


GLOWS = 2


def glow(seed):
    """A soft round blob of light, white so the game can tint it: opaque in the middle and fading in a few flat steps
    to the edge, the way a pixel-art lamp glow is drawn, with a little noise on each step so no ring is perfect.
    Drawn additively it is a flame; drawn normally it is haze."""
    rows = []
    for j in range(SIZE):
        row = []
        for i in range(SIZE):
            reach = math.hypot(i - 7.5, j - 7.5) / 7.6
            level = max(0.0, 1 - reach) ** 1.5 * 5 + 0.5 * (hashed(i, j, seed) - 0.5)
            steps = int(level)
            if steps <= 0:
                row.append((0, 0, 0, 0))
            else:
                row.append((255, 255, 255, min(255, 56 + 50 * steps)))
        rows.append(row)
    return rows


FLAMES = 4


def flame(age):
    """One frame of a tongue of flame, 8 wide and 16 tall, leading end at the top: a round bright head and a ragged tail,
    which later frames break into rags. The heart is white, so it takes the game's tint as it is; the body and rim are
    warmer and darker, so one tint gives a flame a bright heart and a red edge."""
    rows = []
    for j in range(16):
        # Half width of the tongue at this row: round at the head, tapering down the tail
        half = 3.8 * math.sqrt(max(0.0, 1 - ((5 - j) / 5.0) ** 2)) if j < 5 else 3.8 * (1 - (j - 5) / 12.0)
        sway = 0.9 * math.sin(j * 0.8 + age * 2.1) * (j / 15.0)
        row = []
        for i in range(8):
            depth = half - abs(i - 3.5 - sway)
            torn = hashed(i, (j + 3 * age) // 2, 83 + age) < 0.14 * age + 0.25 * max(0, j - 8) / 7.0
            if depth <= 0 or torn:
                row.append((0, 0, 0, 0))
                continue
            heart = depth > 1.7 and j < 11
            row.append((255, 255, 255, 255) if heart else (255, 190, 130, 255) if depth > 0.8 else (225, 105, 60, 255))
        rows.append(row)
    return rows


def spark_streak():
    """A spark's streak, 4 wide and 8 tall: solid at the leading end, fading down its tail."""
    return [[(255, 255, 255, (255, 255, 255, 235, 200, 160, 115, 70)[j])] * 4 for j in range(8)]


def casing_plate(x, y):
    """The flat plate of engine casing: rows of broken strokes in steel, the same in every tile so that connected
    tiles read as one sheet."""
    return 7.2 + (hashed(0, y, 5) - 0.5) * 1.1 + (hashed(x // 3 + (y * 5) % 3, y, 6) - 0.5) * 0.9


def casing_tile(up, down, left, right, top_right, top_left, bottom_right, bottom_left, kind="plain"):
    """One tile of the engine casing's connected sheet. A side that joins another casing carries the plate on across;
    a side that does not gets Create's frame (dark outer rim, bright inner rim) and, where the plate bends back round a
    missing diagonal, a small notch in the frame. Rivets sit only in the corners where two frames meet."""
    size = len(STEEL)
    top_open, bottom_open, left_open, right_open = not up, not down, not left, not right
    rows = []
    for y in range(SIZE):
        row = []
        for x in range(SIZE):
            index = None
            if top_open and y == 0:
                index = 0 if (x == 0 and left_open) or (x == 15 and right_open) else 2
            elif bottom_open and y == 15:
                index = 0 if (x == 0 and left_open) or (x == 15 and right_open) else 1
            elif left_open and x == 0:
                index = 2
            elif right_open and x == 15:
                index = 1
            elif top_open and y == 1:
                index = size - 1
            elif left_open and x == 1:
                index = size - 2
            elif right_open and x == 14:
                index = size - 3
            elif bottom_open and y == 14:
                index = size - 4
            if index is None:
                # Notches where two joined sides meet at a corner that is not joined
                if up and left and not top_left:
                    index = {(0, 0): 0, (1, 0): 2, (0, 1): 2, (1, 1): size - 1}.get((x, y))
                if index is None and up and right and not top_right:
                    index = {(15, 0): 0, (14, 0): 2, (15, 1): 1, (14, 1): size - 3}.get((x, y))
                if index is None and down and left and not bottom_left:
                    index = {(0, 15): 0, (1, 15): 1, (0, 14): 2, (1, 14): size - 4}.get((x, y))
                if index is None and down and right and not bottom_right:
                    index = {(15, 15): 0, (14, 15): 1, (15, 14): 1, (14, 14): size - 4}.get((x, y))
            if index is None:
                index = casing_plate(x, y)
                for corner_x, corner_y, one, other in ((3, 3, top_open, left_open), (12, 3, top_open, right_open),
                                                        (3, 12, bottom_open, left_open), (12, 12, bottom_open, right_open)):
                    if one and other:
                        if (x, y) == (corner_x, corner_y):
                            index = 11
                        elif (x, y) == (corner_x + 1, corner_y + 1):
                            index = 3
                if kind == "riveted":
                    # A rivet every four pixels each way, so the pattern runs on from tile to tile
                    if x % 4 == 3 and y % 4 == 3:
                        index = 12
                    elif x % 4 == 0 and y % 4 == 0 and x > 0 and y > 0:
                        index = 2
                elif kind == "louvred":
                    # Slats every four rows: a dark slot, then the lit lip of the slat below it
                    if y % 4 == 1:
                        index = 1
                    elif y % 4 == 2:
                        index = size - 2
            if kind == "window" and type(index) is float and 4 <= x <= 11 and 4 <= y <= 11:
                # A pane of glass in the middle of the plate: pale, mostly clear, with a glint along one diagonal
                row.append((235, 245, 250, 150) if (x + y) % 6 == 0 else (176, 214, 226, 96))
            else:
                row.append(pick(STEEL, index))
        rows.append(row)
    return rows


def connected_index(up, down, left, right, top_right, top_left, bottom_right, bottom_left):
    """Create's AllCTTypes.OMNIDIRECTIONAL tile choice (sheet of 8 by 8 tiles), copied so the sheet matches it."""
    a = b = 0
    borders = (not up) + (not down) + (not left) + (not right)
    if up:
        a += 1
    if down:
        a += 2
    if left:
        b += 1
    if right:
        b += 2
    if borders == 0:
        if top_right:
            a += 1
        if top_left:
            a += 2
        if bottom_right:
            b += 2
        if bottom_left:
            b += 1
    if borders == 1:
        if not right and (top_left or bottom_left):
            b = 4
            a = -1 + bottom_left + top_left * 2
        if not left and (top_right or bottom_right):
            b = 5
            a = -1 + bottom_right + top_right * 2
        if not down and (top_left or top_right):
            b = 6
            a = -1 + top_left + top_right * 2
        if not up and (bottom_left or bottom_right):
            b = 7
            a = -1 + bottom_left + bottom_right * 2
    if borders == 2:
        if ((up and left and top_left) or (down and left and bottom_left) or (up and right and top_right)
                or (down and right and bottom_right)):
            a += 3
    return a + 8 * b


def engine_casing(name="engine_casing", kind="plain"):
    """The engine casing and its connected-texture sheet: the plain tile, and one tile for every way a face can join
    its neighbours, placed where Create's connected-texture code looks for them. The riveted and louvred plates are the
    same sheet with another pattern on the plate; the window plate has a pane of glass (some transparent pixels) in
    the middle of it."""
    tiles = {}
    for context in range(256):
        c = tuple(bool(context >> bit & 1) for bit in range(8))
        index = connected_index(*c)
        tile = casing_tile(*c, kind=kind)
        # Contexts that share a tile must draw it the same, or the sheet cannot show both
        assert tiles.setdefault(index, tile) == tile, (index, c)
    write_png("block/" + name, casing_tile(False, False, False, False, False, False, False, False, kind=kind))
    blank = casing_tile(True, True, True, True, True, True, True, True, kind=kind)
    sheet = [[None] * 128 for _ in range(128)]
    for index in range(64):
        tile = tiles.get(index, blank)
        for y in range(SIZE):
            for x in range(SIZE):
                sheet[index // 8 * SIZE + y][index % 8 * SIZE + x] = tile[y][x]
    write_png("block/" + name + "_connected", sheet)


def main():
    for index in range(GLOWS):
        write_png("particle/glow_%d" % index, glow(17 + 11 * index))
    for name, painter in (("brass", brass), ("copper", copper), ("copper_panel", copper_panel), ("steel", steel),
                          ("cast_iron", cast_iron), ("black_oxide", black_oxide), ("rivet_plate", rivet_plate),
                          ("enamel_red", enamel_red), ("enamel_blue", enamel_blue),("flywheel_steel", flywheel_steel), ("flywheel_oxide", flywheel_oxide), ("flywheel_coil", flywheel_coil),
                          ("flywheel_enamel", flywheel_enamel), ("ceramic", ceramic), ("screen", screen), ("column_glow", column_glow), ("heat_wrap", heat_wrap),
                          ("grille", grille), ("pleats", pleats), ("finned_core", finned_core), ("coil_can", coil_can)):
        write_png("block/" + name, painter())
    fluid("thin_still", thin_still, 16, 32, 2, THIN_LEVELS)
    fluid("thin_flow", thin_flow, 32, 32, 2, THIN_LEVELS)
    fluid("thick_still", thick_still, 16, 32, 5, THICK_LEVELS)
    fluid("thick_flow", thick_flow, 32, 32, 4, THICK_LEVELS)
    heat("kindled", KINDLED, 31)
    heat("seething", SEETHING, 41)
    tread_plate()
    engine_casing()
    engine_casing("engine_riveted_casing", "riveted")
    engine_casing("engine_louvred_casing", "louvred")
    engine_casing("engine_window_casing", "window")
    for index in range(PUFFS):
        write_png("particle/puff_%d" % index, puff(61 + 7 * index))
    for index in range(FLAMES):
        write_png("particle/flame_%d" % index, flame(index))
    # A spark is a streak, sized, coloured and stretched by the particle that draws it
    write_png("particle/spark", spark_streak())


main()
