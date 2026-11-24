"""Renders the mod's logo: the forged piston on its connecting rod and crank throw, tilted, on a blueprint disc.

Run from the repository root: python tools/generate_logo.py [output_dir]
The parts are rendered from the mod's own models (cylinder/forged_piston, cylinder/rod and crank_throw/block) with the mod's own
textures, in the inventory view, using the depth-buffered renderer of tools/preview_models.py at 3x supersampling. The crank
throw is turned so its shaft runs along the piston's pin and its pin sits in the rod's big end, as it does in an engine.
Create's shaft and casing textures are read out of Create's jar in the Gradle cache. Around the parts: contact shadows where one
part sits close in front of another, a warm light from the top left and a cool fill, a rim light, a dark outline, a spotlight on
the blueprint and a cast shadow. The parts are centred by their mass, not their bounding box. Pure Python, because this machine
has no Pillow. Writes icon.png (512x512) and logo.png (256x256); without an argument they go to build/logo/.
See docs/LISTING.md for where the two PNGs go.
"""
import glob
import math
import os
import struct
import sys
import zipfile
import zlib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from preview_models import load_model, quad, texture  # noqa: E402

SIZE = 512
SS = 3                 # supersampling, per side
W = SIZE * SS
TILT = 24              # degrees clockwise, so the piston points up and to the right
YAW, PITCH = 225, 30   # the inventory view
REACH = 205            # how far from the centre the parts' farthest corner may be, in output pixels
CRANK = True
CRANK_DEGREES = 140   # the crank angle from top dead centre: 0 is straight up, so the rod and piston stand in one line
CORNER_RADIUS = 7      # output pixels: how much the silhouette's corners are rounded
EMBERS = True          # square flame particles above the crown
SMOKE = True           # pale square puffs above the embers
OUTLINE = 1.7          # output pixels of dark outline around the parts
GLOSS = 1.0            # strength of the sheen on upward faces
RIM = "white"          # the disc's rim: "white", or "brass" with rivets, as on Create's own blocks
COG = False            # a cogwheel pressed into the blueprint behind the parts
SPOT = 0.17            # strength of the spotlight in the middle of the disc
GRID = 1.0             # strength of the blueprint grid
CREATE_TEXTURES = ("axis", "axis_top", "andesite_casing")

# Minecraft's face shading, opened up a little (the game's own tops are 1.0, sides 0.8 and 0.6) so the black-oxide parts
# still show their bevels against the blue, with a warm key light from the top and a cool fill from the other sides
LIGHT = {"up": 1.3, "east": 1.12, "west": 0.9, "south": 0.88, "north": 0.8, "down": 0.5}
TINT = {"up": (1.03, 1.01, 0.97), "east": (1.02, 1.0, 0.98), "west": (0.94, 0.98, 1.06), "south": (0.94, 0.98, 1.06),
        "north": (0.92, 0.96, 1.07), "down": (0.9, 0.95, 1.08)}
TONE = 0.8             # exponent applied to the texture's brightness: below 1 lifts the shadows

NAVY = (14, 28, 60)
WHITE = (244, 246, 250)
BLUE = (43, 98, 174)

NORMAL = {"up": (0, 1, 0), "down": (0, -1, 0), "north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0)}




def mechanism():
    """The crank, rod and piston positions for the current CRANK_DEGREES."""
    global CRANK_ANGLE, PIN, ROD_LEAN, GUDGEON
    CRANK_ANGLE = math.radians(CRANK_DEGREES)
    PIN = (SHAFT[0] + CRANK_THROW * math.sin(CRANK_ANGLE), SHAFT[1] + CRANK_THROW * math.cos(CRANK_ANGLE), SHAFT[2])
    ROD_LEAN = math.asin(CRANK_THROW * math.sin(CRANK_ANGLE) / ROD_LENGTH)   # the rod swings so its small end stays on the piston's axis
    GUDGEON = (PIN[0] - ROD_LENGTH * math.sin(ROD_LEAN), PIN[1] + ROD_LENGTH * math.cos(ROD_LEAN), PIN[2])


ROD_LENGTH = 14.0      # from the rod's big end to its small end, read off models/block/cylinder/rod.json
CRANK_THROW = 4.0      # from the crank shaft to the crank pin, read off models/block/crank_throw/block.json
SHAFT = (8.0, 0.0, 8.0)
mechanism()


def identity(p):
    return p


def turn(x, y, angle):
    """(x, y) turned counter-clockwise by angle."""
    return x * math.cos(angle) - y * math.sin(angle), x * math.sin(angle) + y * math.cos(angle)


def crank_point(p):
    """The crank throw turned so its shaft (the model's y axis) runs along z, the way the piston's pin does, then swung round the
    shaft to the crank angle. In the model the shaft is at (8, 8, 8) and the crank pin is at (8, 8, 12)."""
    x, y, z = p[0] - 8, p[1] - 8, p[2] - 8
    x, y = turn(x, z, -CRANK_ANGLE)
    return SHAFT[0] + x, SHAFT[1] + y, SHAFT[2] - p[1] + 8


def crank_normal(n):
    x, y = turn(n[0], n[2], -CRANK_ANGLE)
    return (x, y, -n[1])


def rod_point(p):
    """The rod moved so its big end is on the crank pin, and swung to the lean that puts its small end on the piston's axis."""
    x, y = turn(p[0] - 8, p[1], ROD_LEAN)
    return PIN[0] + x, PIN[1] + y, PIN[2] + p[2] - 8


def rod_normal(n):
    x, y = turn(n[0], n[1], ROD_LEAN)
    return (x, y, n[2])


def shading(n):
    """Light and tint for a face normal: each axis contributes in proportion to how much the normal points along it."""
    light, tint = 0.0, [0.0, 0.0, 0.0]
    for axis, name in enumerate((("west", "east"), ("down", "up"), ("north", "south"))):
        weight = n[axis] * n[axis]
        side = name[1] if n[axis] > 0 else name[0]
        light += weight * LIGHT[side]
        for i in range(3):
            tint[i] += weight * TINT[side][i]
    return light, tint


def parts():
    """(model, offset, point transform, normal transform), in the order they are loaded."""
    crank = [("crank_throw/block", (0, 0, 0), crank_point, crank_normal)] if CRANK else []
    return crank + [("cylinder/rod", (0, 0, 0), rod_point, rod_normal),
                    ("cylinder/forged_piston", (0, GUDGEON[1] - 0.5, 0), identity, identity)]   # the pin is centred at y = 0.5


def raw(p):
    """A model-space point as (screen x, screen y down, depth), before it is fitted to the picture."""
    yaw, pitch = math.radians(YAW), math.radians(PITCH)
    x, y, z = p[0] - 8, p[1] - 8, p[2] - 8
    rx = x * math.cos(yaw) + z * math.sin(yaw)
    rz = -x * math.sin(yaw) + z * math.cos(yaw)
    ry = y * math.cos(pitch) - rz * math.sin(pitch)
    depth = y * math.sin(pitch) + rz * math.cos(pitch)
    a = math.radians(TILT)
    sx, sy = rx, -ry
    return sx * math.cos(a) - sy * math.sin(a), sx * math.sin(a) + sy * math.cos(a), depth


def create_root(out):
    """Pulls Create's shaft and casing textures out of its jar in the Gradle cache; None if the jar is not there."""
    pattern = os.path.join(os.path.expanduser("~"), ".gradle", "caches", "modules-2", "files-2.1", "com.simibubi.create",
                           "create-1.21.1", "*", "*", "create-1.21.1-*.jar")
    jars = sorted(glob.glob(pattern), key=lambda p: [int(n) for n in __import__("re").findall(r"\d+", os.path.basename(p))])
    if not jars:
        print("Create's jar was not found in the Gradle cache: its textures are drawn flat grey")
        return None
    root = os.path.join(out, "create_textures")
    folder = os.path.join(root, "assets", "create", "textures", "block")
    os.makedirs(folder, exist_ok=True)
    with zipfile.ZipFile(jars[-1]) as jar:
        for name in CREATE_TEXTURES:
            with open(os.path.join(folder, name + ".png"), "wb") as fh:
                fh.write(jar.read("assets/create/textures/block/%s.png" % name))
    return root


def render_part(root, shift=(0.0, 0.0)):
    """The parts into W x W buffers: colour (None where empty), depth and element id. Returns them and the projection."""
    corners = []
    for path, offset, point, _ in parts():
        for element in load_model(path)["elements"]:
            f = [element["from"][k] + offset[k] for k in range(3)]
            t = [element["to"][k] + offset[k] for k in range(3)]
            for face in element["faces"]:
                corners += [raw(point(c)) for c in quad(face, f, t)[0]]
    xs, ys = [c[0] for c in corners], [c[1] for c in corners]
    cx, cy = (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2
    k = REACH * SS / max(math.hypot(c[0] - cx, c[1] - cy) for c in corners)

    def project(p):
        x, y, d = raw(p)
        return (x - cx) * k + W / 2 + shift[0], (y - cy) * k + W / 2 + shift[1], d

    image = [[None] * W for _ in range(W)]
    depth = [[-1e9] * W for _ in range(W)]
    ident = [[0] * W for _ in range(W)]
    upward = [[False] * W for _ in range(W)]
    element_id = 0
    for path, offset, point, rotate in parts():
        data = load_model(path)
        textures = data.get("textures", {})
        for element in data["elements"]:
            element_id += 1
            f = [element["from"][i] + offset[i] for i in range(3)]
            t = [element["to"][i] + offset[i] for i in range(3)]
            for face, spec in element["faces"].items():
                pts = [project(point(p)) for p in quad(face, f, t)[0]]
                area = (pts[1][0] - pts[0][0]) * (pts[3][1] - pts[0][1]) - (pts[1][1] - pts[0][1]) * (pts[3][0] - pts[0][0])
                if area <= 0:
                    continue   # turned away from the viewer
                name = spec["texture"]
                while name.startswith("#"):
                    name = textures.get(name[1:], "missing")
                sprite = texture(name, root)
                sw, sh = len(sprite[0]), len(sprite)
                u0, v0, u1, v1 = spec.get("uv") or quad(face, element["from"], element["to"])[1]
                ax, ay = pts[1][0] - pts[0][0], pts[1][1] - pts[0][1]
                bx, by = pts[3][0] - pts[0][0], pts[3][1] - pts[0][1]
                det = ax * by - ay * bx
                if abs(det) < 1e-6:
                    continue
                normal = rotate(NORMAL[face])
                shade, tint = shading(normal)
                facing_up = normal[1] > 0.7
                da, db = pts[1][2] - pts[0][2], pts[3][2] - pts[0][2]
                for py in range(max(0, int(min(p[1] for p in pts))), min(W - 1, int(max(p[1] for p in pts)) + 1) + 1):
                    row, zrow, irow, urow = image[py], depth[py], ident[py], upward[py]
                    for px in range(max(0, int(min(p[0] for p in pts))), min(W - 1, int(max(p[0] for p in pts)) + 1) + 1):
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
                        irow[px] = element_id
                        urow[px] = facing_up
                        row[px] = tuple(min(255.0, 255 * (c / 255) ** TONE * shade * tint[i]) for i, c in enumerate(pixel[:3]))
    return image, depth, ident, upward, project


def mass_centre(image):
    n = sx = sy = 0
    for y, row in enumerate(image):
        for x, c in enumerate(row):
            if c is not None:
                n += 1
                sx += x
                sy += y
    return sx / n, sy / n


def contact_shadows(image, depth, ident):
    """Darkens a part where a different part stands close in front of it to the upper left, which is where the light is."""
    reach = int(2.4 * SS)
    for y in range(reach, W):
        row, zrow, irow = image[y], depth[y], ident[y]
        for x in range(reach, W):
            if row[x] is None:
                continue
            blocked = 0
            for nx, ny in ((x - reach, y - reach), (x - reach, y), (x, y - reach)):
                if ident[ny][nx] and ident[ny][nx] != irow[x] and depth[ny][nx] > zrow[x] + 0.05:
                    blocked += 1
            if blocked:
                row[x] = tuple(c * (1 - 0.17 * blocked) for c in row[x])


def gloss(image, upward):
    """A diagonal sheen across the surfaces that face up, like light on a polished crown."""
    for y in range(W):
        row, flags = image[y], upward[y]
        for x in range(W):
            if flags[x] and row[x] is not None:
                a = ((x + y) / SS) % 60
                k = 1.0 + GLOSS * (0.20 if a < 9 else 0.09 if 14 <= a < 18 else 0.0)
                if k > 1.0:
                    row[x] = tuple(min(255.0, c * k + (k - 1) * 70) for c in row[x])


def embers(image, project):
    """Square embers rising from the piston crown along the part's axis, like the mod's flame particles: flat squares with a
    hot core and a dark edge, smaller the further they rise."""
    top = project((8, 6 + GUDGEON[1] - 0.5, 8))
    ux, uy = math.sin(math.radians(TILT)), -math.cos(math.radians(TILT))
    vx, vy = -uy, ux
    seed = 7
    for step, (along, lateral, size) in enumerate(((46, -8, 17), (74, 30, 13), (62, -40, 10), (104, 10, 10), (92, -26, 7), (128, 38, 7),
                                                    (118, -14, 5), (146, 12, 5))):
        cx = top[0] / SS + ux * along * 0.66 + vx * lateral
        cy = top[1] / SS + uy * along * 0.66 + vy * lateral
        hot = (255, 236, 150) if step % 3 else (255, 206, 96)
        half = size * 1.35 * SS / 2
        for y in range(int(cy * SS - half) - 3, int(cy * SS + half) + 4):
            for x in range(int(cx * SS - half) - 3, int(cx * SS + half) + 4):
                if not (0 <= x < W and 0 <= y < W):
                    continue
                dx, dy = abs(x + 0.5 - cx * SS), abs(y + 0.5 - cy * SS)
                if dx <= half and dy <= half:
                    image[y][x] = hot if max(dx, dy) < half * 0.55 else (244, 150, 52)


def smoke(image, project):
    """Pale square puffs drifting up past the embers, like the mod's exhaust particles."""
    top = project((8, 6 + GUDGEON[1] - 0.5, 8))
    ux, uy = math.sin(math.radians(TILT)), -math.cos(math.radians(TILT))
    vx, vy = -uy, ux
    for along, lateral, size in ((70, 4, 30), (104, -20, 24), (134, 16, 19), (160, -6, 14)):
        cx = top[0] / SS + ux * along * 0.66 + vx * lateral
        cy = top[1] / SS + uy * along * 0.66 + vy * lateral
        half = size * SS / 2
        for y in range(int(cy * SS - half), int(cy * SS + half) + 1):
            for x in range(int(cx * SS - half), int(cx * SS + half) + 1):
                if 0 <= x < W and 0 <= y < W:
                    edge = max(abs(x + 0.5 - cx * SS), abs(y + 0.5 - cy * SS)) > half * 0.78
                    image[y][x] = (170, 182, 204) if edge else (226, 234, 246)


def row_masks(image):
    """Each row as an int whose bit x is set where there is something."""
    return [int("".join("1" if c is not None else "0" for c in reversed(row)), 2) for row in image]


def step(masks, square, grow):
    """One 3x3 step of growing or shrinking a shape held as row bitmasks, square (8 neighbours) or cross (4)."""
    full = (1 << W) - 1
    combine = (lambda a, b: a | b) if grow else (lambda a, b: a & b)
    horizontal = [combine(combine(m, m << 1), m >> 1) for m in masks]
    out = []
    for y in range(W):
        above = (horizontal if square else masks)[y - 1] if y > 0 else (0 if not grow else 0)
        below = (horizontal if square else masks)[y + 1] if y < W - 1 else 0
        if not grow:
            above = above if y > 0 else 0
            below = below if y < W - 1 else 0
        row = horizontal[y] if square else (combine(combine(masks[y], masks[y] << 1), masks[y] >> 1))
        out.append(combine(combine(row, above), below) & full)
    return out


def octagon(masks, radius, grow):
    """Grows or shrinks by about radius pixels with an octagon, which is close enough to a disc to round corners."""
    for i in range(radius):
        masks = step(masks, i % 2 == 0, grow)
    return masks


def round_corners(image):
    """Rounds the silhouette's convex corners by opening it (shrink, then grow) with a disc, and drops what that removes."""
    masks = row_masks(image)
    radius = CORNER_RADIUS * SS
    opened = octagon(octagon(masks, radius, False), radius, True)
    for y in range(W):
        gone = masks[y] & ~opened[y]
        row = image[y]
        x = 0
        while gone >> x:
            if (gone >> x) & 1:
                row[x] = None
            x += 1


def finish(image):
    """The rim light and the outline, drawn into the picture."""
    masks = row_masks(image)
    # rim light: pixels with nothing to their upper left, which face the light
    reach = 2 * SS
    for y in range(W):
        above = masks[y - reach] if y >= reach else 0
        rim = masks[y] & ~(above << reach)
        row = image[y]
        x = 0
        while rim >> x:
            if (rim >> x) & 1:
                row[x] = tuple(min(255.0, c * 1.45 + 26) for c in row[x])
            x += 1
    # outline: the mask grown by an octagon, minus the mask
    edge_rows = octagon(masks, int(OUTLINE * SS), True)
    for y in range(W):
        edge = edge_rows[y] & ~masks[y]
        row = image[y]
        x = 0
        while edge >> x:
            if (edge >> x) & 1:
                row[x] = NAVY
            x += 1


BRASS_DARK, BRASS_LIGHT = (112, 64, 38), (255, 228, 132)


def rim_colour(u, v, r):
    """The disc's rim between radius 241 and 253."""
    if RIM == "white":
        return WHITE
    angle = math.atan2(v - SIZE / 2, u - SIZE / 2)
    # brass, lit from the top left like the parts
    lit = 0.5 + 0.5 * math.cos(angle - math.radians(225))
    base = tuple(BRASS_DARK[i] + (BRASS_LIGHT[i] - BRASS_DARK[i]) * (0.25 + 0.75 * lit) for i in range(3))
    # twelve rivets
    step = math.pi / 6
    index = round(angle / step)
    cx, cy = SIZE / 2 + 247 * math.cos(index * step), SIZE / 2 + 247 * math.sin(index * step)
    d = math.hypot(u - cx, v - cy)
    if d < 4.4:
        if d > 3.3:
            return (70, 38, 24)
        bright = 0.5 - 0.5 * ((u - cx) + (v - cy)) / 6
        return tuple(BRASS_DARK[i] + (BRASS_LIGHT[i] - BRASS_DARK[i]) * (0.55 + 0.45 * bright) for i in range(3))
    return base


def cogwheel(u, v, r):
    """Whether a point is inside the cogwheel pressed into the blueprint."""
    angle = math.atan2(v - SIZE / 2, u - SIZE / 2)
    tooth = math.cos(14 * angle) > -0.2
    return 74 < r < (198 if tooth else 176)


def backdrop(x, y):
    """The blueprint disc at one supersampled position: RGB, or None outside the disc."""
    u, v = (x + 0.5) / SS, (y + 0.5) / SS
    r = math.hypot(u - SIZE / 2, v - SIZE / 2)
    if r > 256:
        return None
    if r > 253:
        return NAVY
    if r > 241:
        return rim_colour(u, v, r)
    if r > 239:
        return NAVY
    lift = SPOT * (1 - (r / 239) ** 1.6)    # a soft spotlight in the middle
    colour = tuple(c + (255 - c) * lift for c in BLUE)
    if COG and cogwheel(u, v, r):
        colour = tuple(c * 0.86 for c in colour)
    for coord in (u, v):   # a fine grid with a heavier line every fourth, lines centred on 32i - 16
        i = round((coord + 16) / 32)
        d = abs(coord - (i * 32 - 16))
        heavy = i % 4 == 0
        if d <= (1.5 if heavy else 0.75):
            a = (0.26 if heavy else 0.13) * GRID
            colour = tuple(c + (255 - c) * a for c in colour)
    return colour


def compose(part):
    """Backdrop, then the cast shadow, then the parts."""
    mask_dx, mask_dy = 12 * SS, 14 * SS
    out = [[None] * W for _ in range(W)]
    for y in range(W):
        row, source = out[y], part[y]
        shadow_row = part[y - mask_dy] if y >= mask_dy else None
        for x in range(W):
            if source[x] is not None:
                row[x] = source[x]
                continue
            colour = backdrop(x, y)
            if colour is None:
                continue
            if shadow_row is not None and x >= mask_dx and shadow_row[x - mask_dx] is not None \
                    and math.hypot((x + 0.5) / SS - SIZE / 2, (y + 0.5) / SS - SIZE / 2) < 239:
                colour = tuple(c * 0.72 for c in colour)
            row[x] = colour
    return out


def downsample(image, factor):
    """Box-filters the W x W picture down by factor, as RGBA rows with transparency outside the disc."""
    size = W // factor
    rows = []
    for oy in range(size):
        row = []
        for ox in range(size):
            r = g = b = n = 0
            for y in range(oy * factor, (oy + 1) * factor):
                line = image[y]
                for x in range(ox * factor, (ox + 1) * factor):
                    c = line[x]
                    if c is not None:
                        r += c[0]
                        g += c[1]
                        b += c[2]
                        n += 1
            total = factor * factor
            row.append((int(r / n), int(g / n), int(b / n), int(255 * n / total)) if n else (0, 0, 0, 0))
        rows.append(row)
    return rows


def write_png(path, rows):
    raw_bytes = b"".join(b"\x00" + b"".join(bytes(p) for p in row) for row in rows)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    with open(path, "wb") as fh:
        fh.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", len(rows[0]), len(rows), 8, 6, 0, 0, 0)) +
                 chunk(b"IDAT", zlib.compress(raw_bytes, 9)) + chunk(b"IEND", b""))
    print("wrote", path)


VARIANTS = {
    "01-classic": {},
    "02-brass-rim": {"RIM": "brass", "SPOT": 0.22, "EMBERS": True},
    "03-night-shift": {"BLUE": (14, 30, 72), "NAVY": (6, 12, 34), "SPOT": 0.3, "GRID": 1.5, "EMBERS": True, "GLOSS": 1.4},
    "04-chunky": {"CORNER_RADIUS": 15, "OUTLINE": 2.8, "TILT": 26, "GLOSS": 1.5, "CRANK_DEGREES": 80},
    "05-cog": {"COG": True, "CRANK_DEGREES": 125, "TILT": 40, "SPOT": 0.2},
    "06-exhaust": {"EMBERS": True, "SMOKE": True, "CRANK_DEGREES": 140, "TILT": 24, "RIM": "brass"},
}
OPTIONS = ("TILT", "CRANK_DEGREES", "CORNER_RADIUS", "EMBERS", "SMOKE", "OUTLINE", "GLOSS", "RIM", "COG", "SPOT", "GRID", "BLUE", "NAVY")


def render(root):
    """The whole picture for the current options, at W x W."""
    mechanism()
    # Render once to find where the parts' mass sits, then again moved so that point is the disc's centre
    first = render_part(root)[0]
    mx, my = mass_centre(first)
    part, depth, ident, upward, project = render_part(root, (W / 2 - mx, W / 2 - my))
    round_corners(part)
    contact_shadows(part, depth, ident)
    gloss(part, upward)
    if SMOKE:
        smoke(part, project)
    if EMBERS:
        embers(part, project)
    finish(part)
    return compose(part)


def sheet(pictures, path):
    """A contact sheet of the variants at half size, three across, each labelled."""
    from preview_models import label
    half, columns = SIZE // 2, 3
    rows = -(-len(pictures) // columns)
    canvas = [[(34, 38, 44)] * (columns * half) for _ in range(rows * half)]
    for index, (name, rgba) in enumerate(pictures):
        ox, oy = (index % columns) * half, (index // columns) * half
        for y in range(half):
            for x in range(half):
                px = [rgba[2 * y + dy][2 * x + dx] for dy in (0, 1) for dx in (0, 1)]
                alpha = sum(p[3] for p in px) / 1020
                colour = [sum(p[i] * p[3] for p in px) / max(1, sum(p[3] for p in px)) for i in range(3)]
                canvas[oy + y][ox + x] = tuple(int(colour[i] * alpha + canvas[oy + y][ox + x][i] * (1 - alpha)) for i in range(3))
        label(canvas, name.upper(), ox + 6, oy + 6)
    write_png(path, [[p + (255,) for p in row] for row in canvas])


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    out = args[0] if args else "build/logo"
    os.makedirs(out, exist_ok=True)
    root = create_root(out)
    if "--variants" not in sys.argv:
        image = render(root)
        write_png(os.path.join(out, "icon.png"), downsample(image, SS))
        write_png(os.path.join(out, "logo.png"), downsample(image, SS * 2))
        return
    folder = os.path.join(out, "variants")
    os.makedirs(folder, exist_ok=True)
    defaults = {name: globals()[name] for name in OPTIONS}
    pictures = []
    for name, options in VARIANTS.items():
        globals().update(defaults)
        globals().update(options)
        rgba = downsample(render(root), SS)
        write_png(os.path.join(folder, name + ".png"), rgba)
        pictures.append((name, rgba))
    globals().update(defaults)
    sheet(pictures, os.path.join(folder, "sheet.png"))


main()
