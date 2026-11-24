"""Writes the block models under src/main/resources/assets/create_engines/models/block/.

Run from the repository root: python tools/generate_models.py
Everything here is built from boxes in 1/16 block units, textured with Create's casings and shafts and with the metals
drawn by tools/generate_textures.py, which are made to sit next to them. Parts that
the engine animates (piston, rod, valve, crank, cam) keep the pivot and extent the renderers expect: they are drawn
about the same axis and stay inside the same envelope, with detail added inside it.
"""
import json
import math
import os

OUT = "src/main/resources/assets/create_engines/models/block"
FACES = ("north", "south", "east", "west", "up", "down")


# Only the refinery models use this: older models were made and approved with the default patches, so they stay as
# they are. Set by the functions that want it.
WRAP_UV = False
# With WRAP_UV off, a face is still moved back inside its texture when it runs this many pixels outside it or more
WRAP_MIN_OVERRUN = 0.5


def _face_patch(a, b, face):
    """The [u0, v0, u1, v1] patch of its texture that a face of the box from a to b shows by default."""
    x1, y1, z1 = a
    x2, y2, z2 = b
    return {
        "down": (x1, 16 - z2, x2, 16 - z1),
        "up": (x1, z1, x2, z2),
        "north": (16 - x2, 16 - y2, 16 - x1, 16 - y1),
        "south": (x1, 16 - y2, x2, 16 - y1),
        "west": (z1, 16 - y2, z2, 16 - y1),
        "east": (16 - z2, 16 - y2, 16 - z1, 16 - y1),
    }[face]


def _wrapped_default_uv(a, b, face):
    """The patch a face would show by default, moved back inside the texture when the element pokes out of the block
    (a boss or port outside 0..16): outside the sprite the default patch samples the neighbouring sprites in the atlas
    and shows as stray colours. Returns None when the default is already inside and needs no uv."""
    u0, v0, u1, v1 = _face_patch(a, b, face)
    if min(u0, v0) >= 0 and max(u1, v1) <= 16:
        return None
    # Models made before WRAP_UV (the pipe arms and the like) keep their approved default patches where the overrun is a
    # fraction of a pixel; a pixel or more (the head plugs and injectors, rockers, pushrods, valves, pistons, rods) shows as
    # stray colour from the neighbouring sprite, which one atlas layout hides and another (a bigger modpack) does not
    overrun = max(-min(u0, v0, 0), max(u1, v1, 16) - 16)
    if not WRAP_UV and overrun < WRAP_MIN_OVERRUN:
        return None
    du = -(u0 // 16) * 16
    dv = -(v0 // 16) * 16
    patch = [u0 + du, v0 + dv, u1 + du, v1 + dv]
    # A piece wider than the texture would still overrun: show the whole texture instead
    if max(patch) > 16 or min(patch) < 0:
        return [0, 0, 16, 16]
    return [round(v, 4) for v in patch]


def box(a, b, tex, uv=None, **overrides):
    """One element from corner a to corner b. tex is the texture for every face; overrides retexture single faces; uv
    maps a face name to the [u0, v0, u1, v1] patch of its texture to show, instead of the one under it."""
    faces = {}
    for face in FACES:
        faces[face] = {"texture": "#" + overrides.get(face, tex)}
        if uv and face in uv:
            faces[face]["uv"] = uv[face]
        else:
            patch = _wrapped_default_uv(a, b, face)
            if patch:
                faces[face]["uv"] = patch
    return {"from": [round(v, 4) for v in a], "to": [round(v, 4) for v in b], "faces": faces}


def shaft(a, b):
    """A Create-style shaft: axle texture along its length, end texture on the caps."""
    return box(a, b, "axis", up="end", down="end")


def write(path, textures, elements, **extra):
    data = {"parent": "block/block"}
    data.update(extra)
    data["textures"] = textures
    data["elements"] = elements
    full = os.path.join(OUT, path + ".json")
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w", newline="\n") as fh:
        json.dump(data, fh, indent=2)
        fh.write("\n")
    print("wrote", full)


def write_item(path, parent):
    full = os.path.join(OUT, path + ".json")
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w", newline="\n") as fh:
        json.dump({"parent": "create_engines:block/" + parent}, fh, indent=2)
        fh.write("\n")


CASING = "create:block/andesite_casing"
COPPER_CASING = "create:block/copper_casing"
IRON = "create_engines:block/cast_iron"
DARK = "create_engines:block/black_oxide"
BRASS = "create_engines:block/brass"
ZINC = "create:block/zinc_block"
COPPER = "create_engines:block/copper"
CUT_COPPER = "create_engines:block/copper_panel"
IRON_BLOCK = "create_engines:block/steel"
AXIS = "create:block/axis"
AXIS_TOP = "create:block/axis_top"
BLACK = "create_engines:block/screen"
QUARTZ = "create_engines:block/ceramic"
SMOOTH = "create_engines:block/heat_wrap"
BRASS_CASING = "create:block/brass_casing"
RED = "create_engines:block/enamel_red"
BLUE = "create_engines:block/enamel_blue"
RIVETED = "create_engines:block/rivet_plate"
MAGMA = "create_engines:block/heat_kindled"
LAVA = "create_engines:block/heat_seething"
GLASS = "minecraft:block/glass"


# --- crank, throw, cam ---------------------------------------------------------------------------------------------

def crankshaft():
    textures = {"axis": AXIS, "end": AXIS_TOP, "collar": CASING, "journal": IRON, "bolt": BRASS, "groove": DARK,
                "particle": CASING}
    elements = [
        shaft((6, 0, 6), (10, 16, 10)),
        # The journal, with a thrust collar at each end that keeps the crank from walking along its line
        box((5, 2.5, 5), (11, 13.5, 11), "journal"),
        box((4.5, 2.5, 4.5), (11.5, 3.5, 11.5), "collar"),
        box((4.5, 12.5, 4.5), (11.5, 13.5, 11.5), "collar"),
        # The split main bearing: a lower shell and a cap, with the joint between them showing as a dark line
        box((4, 5, 4), (12, 7.75, 12), "collar"),
        box((4, 8.25, 4), (12, 11, 12), "collar"),
        box((3.75, 7.75, 3.75), (12.25, 8.25, 12.25), "groove"),
        # The oil feed into the shell, on the side the rest of the engine leaves free
        box((3.5, 6, 7), (4, 7, 9), "groove"),
        box((12, 6, 7), (12.5, 7, 9), "groove"),
    ]
    # Cap bolts at the corners, with a brass nut standing proud of the cap
    for x in (3.6, 11.4):
        for z in (3.6, 11.4):
            elements.append(box((x, 5.5, z), (x + 1, 10.5, z + 1), "bolt"))
            elements.append(box((x - 0.25, 10.5, z - 0.25), (x + 1.25, 11.5, z + 1.25), "bolt"))
    write("crankshaft/block", textures, elements)


def crank_throw():
    textures = {"axis": AXIS, "end": AXIS_TOP, "web": IRON, "pin": IRON_BLOCK, "weight": DARK, "collar": CASING,
                "bolt": BRASS, "particle": IRON}
    elements = [shaft((6, 0, 6), (10, 3, 10)), shaft((6, 13, 6), (10, 16, 10)),
                box((5, 2, 5), (11, 3, 11), "collar"), box((5, 13, 5), (11, 14, 11), "collar")]
    for y0 in (3, 11):
        y1 = y0 + 2
        outer = 2.9 if y0 == 3 else 13
        # A web: a broad hub round the shaft that narrows into an arm out to the pin
        elements.append(box((3.5, y0, 3.5), (12.5, y1, 10), "web"))
        elements.append(box((5, y0, 9), (11, y1, 15), "web"))
        # The counterweight on the far side from the pin, which balances the pin and the big end of the rod: a heavy
        # fan that is widest at its rim
        elements.append(box((3.5, y0, 1.5), (12.5, y1, 4), "weight"))
        elements.append(box((3, y0, 1.5), (13, y1, 3), "weight"))
        elements.append(box((4.5, y0, 1), (11.5, y1, 1.5), "weight"))
        # Two balance holes drilled in the outer face of the weight
        elements.append(box((4.5, outer, 2.2), (6.5, outer + 0.1, 3.8), "web"))
        elements.append(box((9.5, outer, 2.2), (11.5, outer + 0.1, 3.8), "web"))
    # The crank pin, with a fillet at each web, and the oil way across it
    elements.append(box((6.25, 5, 10.25), (9.75, 11, 13.75), "pin"))
    elements.append(box((5.75, 5, 9.75), (10.25, 5.6, 13.9), "pin"))
    elements.append(box((5.75, 10.4, 9.75), (10.25, 11, 13.9), "pin"))
    elements.append(box((6, 7.6, 10), (10, 8.4, 14), "bolt"))
    write("crank_throw/block", textures, elements)


def camshaft():
    textures = {"axis": AXIS, "end": AXIS_TOP, "bearing": CASING, "groove": DARK, "particle": AXIS}
    elements = [
        shaft((6.5, 0, 6.5), (9.5, 16, 9.5)),
        box((5.5, 1, 5.5), (10.5, 3, 10.5), "bearing"),
        box((5.5, 13, 5.5), (10.5, 15, 10.5), "bearing"),
        box((5, 6.5, 5), (11, 9.5, 11), "bearing"),
        box((5.25, 7.5, 5.25), (10.75, 8.5, 10.75), "groove"),
    ]
    write("camshaft/block", textures, elements)


def cam_profile(base_r=3.0, nose_r=1.0, nose_at=4.5, step=0.25):
    """The outline of a tangent cam, nose towards +Z, as non-overlapping rows (z0, z1, half width) a quarter pixel
    deep: the hull of a base circle and a small nose circle, which is the union of circles whose radius runs from
    one to the other along the line between their centres."""
    def half_width(z):
        best = 0.0
        for i in range(2001):
            t = i / 2000
            d = (base_r + (nose_r - base_r) * t) ** 2 - (z - nose_at * t) ** 2
            if d > 0:
                best = max(best, math.sqrt(d))
        return best

    rows = []
    z = -base_r
    while z < nose_at + nose_r - 1e-6:
        w = round(half_width(z + step / 2) / step) * step
        if w > 0:
            if rows and rows[-1][2] == w:
                rows[-1][1] = z + step
            else:
                rows.append([z, z + step, w])
        z += step
    return rows


def cam_disc(y0, y1, rows):
    """One cam, lying across the shaft between heights y0 and y1. Faces that a neighbouring row covers are left out."""
    elements = []
    for i, (z0, z1, w) in enumerate(rows):
        faces = {face: {"texture": "#lobe"} for face in ("east", "west", "up", "down")}
        if i == 0 or rows[i - 1][2] < w:
            faces["north"] = {"texture": "#lobe"}
        if i == len(rows) - 1 or rows[i + 1][2] < w:
            faces["south"] = {"texture": "#lobe"}
        elements.append({"from": [8 - w, y0, 8 + z0], "to": [8 + w, y1, 8 + z1], "faces": faces})
    return elements


def cam_lobe():
    """The set of lobes on a camshaft: an intake, an exhaust and an injector cam, each a smooth egg with its nose
    towards +Z at angle zero, on the shaft between bearing collars and spaced by thin rings."""
    textures = {"axis": AXIS, "end": AXIS_TOP, "lobe": IRON, "spacer": DARK, "bearing": CASING, "particle": IRON}
    elements = [
        shaft((6.5, 0, 6.5), (9.5, 16, 9.5)),
        box((5.5, 2, 5.5), (10.5, 3, 10.5), "bearing"),
        box((5.5, 13, 5.5), (10.5, 14, 10.5), "bearing"),
    ]
    elements += [box((5.75, y, 5.75), (10.25, y + 1, 10.25), "spacer") for y in (3, 6, 9, 12)]
    rows = cam_profile()
    for y in (4, 7, 10):
        elements += cam_disc(y, y + 2, rows)
    write("cam_lobe/block", textures, elements)

    # The same set in pieces, for the renderer: each lobe is turned on its own to the angle its valve needs, so the
    # shaft with its collars and spacers and the three lobes are drawn apart (the whole set above is the item)
    body = elements[:3 + 4]
    write("cam_lobe/shaft", textures, body)
    for name, y in (("intake", 4), ("exhaust", 7), ("injector", 10)):
        write("cam_lobe/" + name, textures, cam_disc(y, y + 2, rows))


# --- cylinder, piston, rod -----------------------------------------------------------------------------------------

def glass_panels():
    """A pane in each side, set between the corner posts and the rims, so the bore is enclosed but still visible."""
    return [box((3, 2, 1), (13, 14, 2), "glass"), box((3, 2, 14), (13, 14, 15), "glass"),
            box((1, 2, 3), (2, 14, 13), "glass"), box((14, 2, 3), (15, 14, 13), "glass")]


def cylinder():
    """Four corner posts and a rim at each end, so the piston shows through the open sides."""
    textures = {"frame": CASING, "rim": IRON, "glass": GLASS, "particle": CASING}
    elements = []
    for x in (0, 13):
        for z in (0, 13):
            elements.append(box((x, 0, z), (x + 3, 16, z + 3), "frame"))
    for y0, y1 in ((0, 2), (14, 16)):
        elements += [box((3, y0, 0), (13, y1, 3), "rim"), box((3, y0, 13), (13, y1, 16), "rim"),
                     box((0, y0, 3), (3, y1, 13), "rim"), box((13, y0, 3), (16, y1, 13), "rim")]
    elements += glass_panels()
    write("cylinder/block", textures, elements, render_type="minecraft:cutout")


def piston():
    textures = {"piston": IRON_BLOCK, "ring": IRON, "particle": IRON_BLOCK}
    write("cylinder/piston", textures,
          [box((3, -2, 3), (13, 6, 13), "piston"), box((2.9, 4, 2.9), (13.1, 4.6, 13.1), "ring")])


def rod_elements(big_y, small_y, beam_from, beam_to):
    return [
        box((7, beam_from, 7), (9, beam_to, 9), "rod"),
        box((6.5, big_y, 6), (9.5, big_y + 3, 10), "rod"),
        box((6.75, small_y, 6.5), (9.25, small_y + 2.5, 9.5), "rod"),
    ]


def rod():
    textures = {"rod": IRON, "particle": IRON}
    write("cylinder/rod", textures, rod_elements(-1.5, 12.75, 1, 13))
    write("cylinder/rod_item", textures, rod_elements(0, 13, 1, 15))


# --- head, injector, plug, valve -----------------------------------------------------------------------------------

def head_base():
    return [
        box((0, 0, 0), (16, 14, 16), "side", up="top"),
        box((1, 14, 1), (15, 16, 15), "top"),
    ]


HEAD_TEXTURES = {"side": COPPER_CASING, "top": CUT_COPPER, "injector": BRASS, "ceramic": QUARTZ, "terminal": IRON,
                 "particle": COPPER_CASING}


def injector_elements():
    return [box((11, 16, 2), (14, 17.5, 5), "injector")]


def plug_elements():
    return [
        box((2.5, 16, 10.5), (5.5, 19, 13.5), "ceramic"),
        box((3.5, 19, 11.5), (4.5, 20.5, 12.5), "terminal"),
        box((2, 16, 10), (6, 17, 14), "terminal"),
    ]


def assist_elements(y, x0, x1, z0, z1):
    """An electric injector assist: a dark driver box that sits on the injector's body, a blue winding band round it and a
    lead out of its side. Sunk 0.1 into what is under it so no face lies in the same plane as one below."""
    return [
        box((x0, y, z0), (x1, y + 2.1, z1), "assist"),
        box((x0 - 0.2, y + 0.8, z0 - 0.2), (x1 + 0.2, y + 1.5, z1 + 0.2), "coil"),
        box((x1 - 0.1, y + 1.0, (z0 + z1) / 2 - 0.4), (x1 + 1.2, y + 1.6, (z0 + z1) / 2 + 0.4), "terminal"),
    ]


def coil_elements(x0, x1, z0, z1, y):
    """A high-energy ignition coil that sits on a spark plug's ceramic (its top at y): a dark can, a blue winding band round it
    and a lead out of the top. The can is sunk 0.1 into the ceramic and stands 0.1 proud of it on every side, so no face lies in
    the same plane as one under it."""
    cx, cz = (x0 + x1) / 2, (z0 + z1) / 2
    return [
        box((x0 - 0.1, y - 0.1, z0 - 0.1), (x1 + 0.1, y + 2.9, z1 + 0.1), "assist"),
        box((x0 - 0.3, y + 0.9, z0 - 0.3), (x1 + 0.3, y + 1.7, z1 + 0.3), "coil"),
        box((cx - 0.5, y + 2.8, cz - 0.5), (cx + 0.5, y + 3.9, cz + 0.5), "terminal"),
    ]


def head():
    base = head_base()
    write("cylinder_head/block", HEAD_TEXTURES, base)
    write("cylinder_head/block_injector", HEAD_TEXTURES, base + injector_elements())
    write("cylinder_head/block_plug", HEAD_TEXTURES, base + plug_elements())
    write("cylinder_head/block_injector_plug", HEAD_TEXTURES, base + injector_elements() + plug_elements())
    assist = assist_elements(17.4, 10.6, 14.4, 1.6, 5.4)
    textures = dict(HEAD_TEXTURES, assist=DARK, coil=BLUE)
    write("cylinder_head/block_injector_assist", textures, base + injector_elements() + assist)
    write("cylinder_head/block_injector_assist_plug", textures,
          base + injector_elements() + assist + plug_elements())
    # A coil stands on the plug's ceramic and swallows its terminal, so that box is left out under it
    plug = plug_elements()
    coil = coil_elements(2.5, 5.5, 10.5, 13.5, 19)
    coiled = dict(HEAD_TEXTURES, assist=DARK, coil=BLUE)
    write("cylinder_head/block_plug_coil", coiled, base + [plug[0], plug[2]] + coil)
    write("cylinder_head/block_injector_plug_coil", coiled, base + injector_elements() + [plug[0], plug[2]] + coil)
    write("cylinder_head/block_injector_assist_plug_coil", coiled,
          base + injector_elements() + assist + [plug[0], plug[2]] + coil)
    write("cylinder_head/coil_item",
          {"assist": DARK, "coil": BLUE, "terminal": IRON, "ceramic": QUARTZ, "particle": DARK},
          [box((5.5, 0, 5.5), (10.5, 3, 10.5), "ceramic"), box((5.4, 2.9, 5.4), (10.6, 8.9, 10.6), "assist"),
           box((5.2, 4.7, 5.2), (10.8, 6.3, 10.8), "coil"), box((7, 8.8, 7), (9, 10.8, 9), "terminal")])
    write("cylinder_head/assist_item",
          {"assist": DARK, "coil": BLUE, "terminal": IRON, "nozzle": IRON_BLOCK, "particle": DARK},
          [box((5.5, 0, 5.5), (10.5, 5, 10.5), "assist"), box((5.3, 2, 5.3), (10.7, 3.4, 10.7), "coil"),
           box((10.4, 2.2, 7.6), (12.4, 3.2, 8.4), "terminal"), box((7, -3, 7), (9, 0, 9), "nozzle")])

    write("cylinder_head/injector_item",
          {"body": BRASS, "cap": CUT_COPPER, "nozzle": IRON_BLOCK, "particle": BRASS},
          [box((6, 0, 6), (10, 9, 10), "body"), box((5, 9, 5), (11, 12, 11), "cap"),
           box((7, -3, 7), (9, 0, 9), "nozzle")])
    write("cylinder_head/plug_item",
          {"ceramic": QUARTZ, "terminal": IRON, "particle": QUARTZ},
          [box((6, 0, 6), (10, 7, 10), "ceramic"), box((7, 7, 7), (9, 10, 9), "terminal"),
           box((5, 0, 5), (11, 2, 11), "terminal"), box((7.5, -3, 7.5), (8.5, 0, 8.5), "terminal")])


def valve():
    write("cylinder_head/valve", {"stem": IRON_BLOCK, "cap": BRASS, "particle": IRON_BLOCK},
          [box((7.25, -2, 7.25), (8.75, 1.5, 8.75), "stem"), box((6.5, 1.5, 6.5), (9.5, 2.25, 9.5), "cap")])


# --- airflow -------------------------------------------------------------------------------------------------------

def air_intake():
    """A bolted cast-iron flange at the back, a short copper neck with a cast band, and a stepped bell opening into a
    hollow mouth: a riveted iron ring round a dark well, with a louvred grille and a cross brace set down in it. The back
    flange is the 9 by 9 joint the pipes use; the mouth ring keeps its old 13 by 13 outline."""
    textures = {"body": COPPER_CASING, "bead": COPPER, "rim": IRON, "bolt": BRASS,
                "grille": "create_engines:block/grille", "well": BLACK, "particle": COPPER_CASING}
    elements = [
        # flange and collar
        box((3, 0, 3), (13, 1.5, 13), "rim"),
        box((4.5, 1.5, 4.5), (11.5, 3, 11.5), "rim"),
        # neck, with a cast band round it
        box((5.25, 3, 5.25), (10.75, 6, 10.75), "body"),
        box((4.5, 4.25, 4.5), (11.5, 5.25, 11.5), "rim"),
        # the bell, stepping out to the mouth
        box((4.5, 6, 4.5), (11.5, 8, 11.5), "body"),
        box((4.25, 6.75, 4.25), (11.75, 7.75, 11.75), "bead"),
        box((3.5, 8, 3.5), (12.5, 10, 12.5), "body"),
        box((3.25, 8.75, 3.25), (12.75, 9.75, 12.75), "bead"),
        box((2.5, 10, 2.5), (13.5, 12, 13.5), "body"),
        box((2.25, 10.75, 2.25), (13.75, 11.75, 13.75), "bead"),
        # the dark well the grille is set above
        box((3, 12, 3), (13, 12.1, 13), "well"),
        # the mouth ring
        box((1.5, 12, 1.5), (14.5, 13.5, 3), "rim"),
        box((1.5, 12, 13), (14.5, 13.5, 14.5), "rim"),
        box((1.5, 12, 3), (3, 13.5, 13), "rim"),
        box((13, 12, 3), (14.5, 13.5, 13), "rim"),
        # grille, and a brace across it with a boss at the middle
        box((3, 12.7, 3), (13, 12.85, 13), "grille"),
        box((7.25, 12.3, 2.9), (8.75, 13.1, 13.1), "rim"),
        box((7, 13.1, 7), (9, 13.6, 9), "bolt"),
    ]
    for x in (3.5, 11.5):
        for z in (3.5, 11.5):
            elements.append(box((x, 1.5, z), (x + 1, 2.2, z + 1), "bolt"))
    # rivets on the corners of the ring
    for x in (2, 13):
        for z in (2, 13):
            elements.append(box((x, 13.5, z), (x + 1, 14, z + 1), "bolt"))
    write("air_intake/block", textures, elements, render_type="minecraft:cutout")


def exhaust_outlet():
    """A bolted flange and collar, a heat-wrapped pipe with a cast clamp, a swaged shoulder, and a flared tailpipe
    finished with a rolled steel lip. The lip is 9 by 9 pixels at y 15 to 16, where the rain cap sits, and must stay so."""
    textures = {"flange": IRON, "pipe": DARK, "tip": IRON_BLOCK, "wrap": SMOOTH, "inside": BLACK, "bolt": BRASS,
                "particle": DARK}
    elements = [
        # flange and collar
        box((3, 0, 3), (13, 1.5, 13), "flange"),
        box((4.5, 1.5, 4.5), (11.5, 3, 11.5), "flange"),
        # the pipe, wrapped low down where the gas is hottest, with a clamp at the top of the wrap
        box((5.5, 3, 5.5), (10.5, 11, 10.5), "pipe"),
        box((5.25, 3.5, 5.25), (10.75, 6.5, 10.75), "wrap"),
        box((5, 6.5, 5), (11, 7.5, 11), "flange"),
        # swaged out to the tailpipe
        box((5, 11, 5), (11, 12, 11), "pipe"),
        box((4.5, 12, 4.5), (11.5, 15, 11.5), "pipe"),
        box((4, 12.5, 4), (12, 13.25, 12), "flange"),
        # the rolled lip, with the dark bore showing inside it
        box((3.5, 15, 3.5), (12.5, 16, 5), "tip"),
        box((3.5, 15, 11), (12.5, 16, 12.5), "tip"),
        box((3.5, 15, 5), (5, 16, 11), "tip"),
        box((11, 15, 5), (12.5, 16, 11), "tip"),
        box((5, 14.9, 5), (11, 15.1, 11), "inside"),
    ]
    for x in (3.4, 11.6):
        for z in (3.4, 11.6):
            elements.append(box((x, 1.5, z), (x + 1, 2.1, z + 1), "bolt"))
    # a bolt head on each face of the clamp
    elements += [box((7.25, 6.75, 4.7), (8.75, 7.25, 5), "bolt"), box((7.25, 6.75, 11), (8.75, 7.25, 11.3), "bolt"),
                 box((4.7, 6.75, 7.25), (5, 7.25, 8.75), "bolt"), box((11, 6.75, 7.25), (11.3, 7.25, 8.75), "bolt")]
    write("exhaust_outlet/block", textures, elements)


def rain_cap():
    """A rain cap, modelled facing up and hinged along its +Z edge (the renderer draws the flap, lifted about that hinge,
    as a partial of its own; the block model is the bracket and the stationary flap, and the item model shows both). It
    sits on the rim of an exhaust outlet, whose rim is 9 by 9 pixels at the middle of the block."""
    textures = {"plate": IRON_BLOCK, "lug": IRON, "pin": BRASS, "tip": DARK, "particle": IRON_BLOCK}
    bracket = [
        box((3, 0, 11.5), (4.5, 1.8, 13.5), "lug"),
        box((11.5, 0, 11.5), (13, 1.8, 13.5), "lug"),
        box((3, 0.6, 12), (13, 1.4, 13), "pin"),
    ]
    flap = [
        box((3.5, 0, 3.5), (12.5, 0.8, 12.5), "plate"),
        box((3.5, 0.8, 3.5), (12.5, 1.5, 4.5), "tip"),
    ]
    write("rain_cap/block", textures, bracket)
    write("rain_cap/flap", textures, flap)
    write("rain_cap/item", textures, bracket + flap)


def maker_plate():
    """A maker's plate, modelled facing up: a brass plate with a raised rim and a bolt in each corner. The renderer
    draws the name and the figures on its face (the face is 1.5 pixels up, see MakerPlateRenderer)."""
    textures = {"plate": BRASS, "rim": BRASS_CASING, "bolt": IRON, "particle": BRASS}
    elements = [
        box((2, 0, 3.5), (14, 1.2, 12.5), "plate"),
        box((2, 1.2, 3.5), (14, 1.5, 4.2), "rim"),
        box((2, 1.2, 11.8), (14, 1.5, 12.5), "rim"),
        box((2, 1.2, 4.2), (2.7, 1.5, 11.8), "rim"),
        box((13.3, 1.2, 4.2), (14, 1.5, 11.8), "rim"),
    ]
    for x in (3.1, 12.1):
        for z in (4.6, 10.6):
            elements.append(box((x, 1.2, z), (x + 0.8, 1.7, z + 0.8), "bolt"))
    write("maker_plate/block", textures, elements)
    write("maker_plate/item", textures, elements)


def framed_blueprint():
    """A frame for a blueprint, modelled facing up: a cast iron frame round a sheet of blueprint paper. The renderer draws
    the plan and the figures on the paper (the paper is 1.2 pixels up, see FramedBlueprintRenderer). Sixteen models, one
    per set of joined sides (bit 0 north, 1 east, 2 south, 3 west, in the model's own frame): a joined side has no rim or
    bolts, and the plate and paper run out to the block's edge so neighbouring frames read as one sheet. Model c0 is the
    lone frame, and is what the item shows."""
    textures = {"frame": IRON, "rim": BRASS_CASING, "paper": "create_engines:block/blueprint_paper", "bolt": BRASS,
                "particle": IRON}
    for mask in range(16):
        n, e, s, w = (bool(mask & 1), bool(mask & 2), bool(mask & 4), bool(mask & 8))
        x0, x1 = (0 if w else 2), (16 if e else 14)
        z0, z1 = (0 if n else 2), (16 if s else 14)
        px0, px1 = (0 if w else 2.7), (16 if e else 13.3)
        pz0, pz1 = (0 if n else 2.7), (16 if s else 13.3)
        elements = [
            box((x0, 0, z0), (x1, 1.0, z1), "frame"),
            box((px0, 1.0, pz0), (px1, 1.2, pz1), "paper"),
        ]
        if not n:
            elements.append(box((x0, 1.0, 2), (x1, 1.5, 2.7), "rim"))
        if not s:
            elements.append(box((x0, 1.0, 13.3), (x1, 1.5, 14), "rim"))
        rz0, rz1 = (2.7 if not n else 0), (13.3 if not s else 16)
        if not w:
            elements.append(box((2, 1.0, rz0), (2.7, 1.5, rz1), "rim"))
        if not e:
            elements.append(box((13.3, 1.0, rz0), (14, 1.5, rz1), "rim"))
        for x, side in ((2.1, w), (12.9, e)):
            for z, end in ((2.1, n), (12.9, s)):
                if not side and not end:
                    elements.append(box((x, 1.5, z), (x + 1, 1.8, z + 1), "bolt"))
        write("framed_blueprint/c%d" % mask, textures, elements)
        if mask == 0:
            write("framed_blueprint/block", textures, elements)
    write("framed_blueprint/item", textures, [
        box((2, 0, 2), (14, 1.0, 14), "frame"),
        box((2, 1.0, 2), (14, 1.5, 2.7), "rim"),
        box((2, 1.0, 13.3), (14, 1.5, 14), "rim"),
        box((2, 1.0, 2.7), (2.7, 1.5, 13.3), "rim"),
        box((13.3, 1.0, 2.7), (14, 1.5, 13.3), "rim"),
        box((2.7, 1.0, 2.7), (13.3, 1.2, 13.3), "paper"),
    ] + [box((x, 1.5, z), (x + 1, 1.8, z + 1), "bolt") for x in (2.1, 12.9) for z in (2.1, 12.9)])


def to_frame(point, direction):
    """Maps a point from the 'arm runs out of the north face' frame to the frame where it runs out of direction."""
    x, y, z = point
    return {"north": (x, y, z), "south": (16 - x, y, 16 - z), "east": (16 - z, y, x), "west": (z, y, 16 - x),
            "up": (x, 16 - z, y), "down": (x, z, 16 - y)}[direction]


def oriented(element, direction):
    a = to_frame(element["from"], direction)
    b = to_frame(element["to"], direction)
    moved = dict(element)
    moved["from"] = [round(min(p, q), 4) for p, q in zip(a, b)]
    moved["to"] = [round(max(p, q), 4) for p, q in zip(a, b)]
    return moved


def arm(style):
    """The elements of one pipe arm in the north frame: neck, bead rings, flange plate and flange bolts."""
    elements = [
        box((5, 5, 2), (11, 11, 5), "pipe"),
        box((4.5, 4.5, 2.2), (11.5, 11.5, 2.9), "bead"),
        box((3.5, 3.5, 0), (12.5, 12.5, 1.5), "flange"),
        box((4, 4, 1.5), (12, 12, 2.2), "gasket"),
    ]
    if style == "exhaust":
        elements += [box((4.75, 4.75, 3.2), (11.25, 11.25, 3.9), "wrap"), box((4.75, 4.75, 4.2), (11.25, 11.25, 4.9), "wrap")]
    else:
        elements.append(box((4.5, 4.5, 3.9), (11.5, 11.5, 4.5), "bead"))
    for x in (4.2, 10.8):
        for y in (4.2, 10.8):
            elements.append(box((x, y, -0.3), (x + 1, y + 1, 0), "bolt"))
    return elements


def core(style):
    """The junction at the middle of a pipe: a bulged node that every arm runs out of."""
    elements = [box((5, 5, 5), (11, 11, 11), "pipe"),
                box((4.5, 5.5, 5.5), (11.5, 10.5, 10.5), "bead"),
                box((5.5, 4.5, 5.5), (10.5, 11.5, 10.5), "bead"),
                box((5.5, 5.5, 4.5), (10.5, 10.5, 11.5), "bead")]
    if style == "throttle":
        elements = [box((5, 5, 5), (11, 11, 11), "pipe"),
                    box((4, 4, 4), (12, 12, 12), "body"),
                    box((3.5, 5.5, 5.5), (12.5, 10.5, 10.5), "bead"),
                    box((5.5, 5.5, 3.5), (10.5, 10.5, 12.5), "bead"),
                    # Spindle on top with the pedal lever, return spring post and the throttle position sensor
                    box((7, 12, 7), (9, 14, 9), "bolt"),
                    box((6, 14, 7.5), (12, 15, 8.5), "body"),
                    box((11, 12, 7.25), (11.8, 14.5, 8.75), "bolt"),
                    box((9.2, 12, 9.2), (11.5, 13.6, 11.5), "dark"),
                    box((4.5, 12, 6.5), (6.5, 13, 8.5), "bolt")]
    if style == "maf":
        elements = [box((5, 5, 5), (11, 11, 11), "pipe"),
                    box((4, 4, 4), (12, 12, 12), "body"),
                    box((3.5, 5.5, 5.5), (12.5, 10.5, 10.5), "bead"),
                    box((5.5, 5.5, 3.5), (10.5, 10.5, 12.5), "bead"),
                    # Electronics housing on top, with the connector plug and the strap that holds it down
                    box((5, 12, 5), (11, 14, 11), "dark"),
                    box((5.5, 14, 5.5), (10.5, 15, 10.5), "body"),
                    box((6.5, 14.5, 10.5), (9.5, 16, 12.5), "plug"),
                    box((4.5, 12.5, 7), (5, 13.5, 9), "bolt"),
                    box((11, 12.5, 7), (11.5, 13.5, 9), "bolt")]
    if style == "heat":
        elements = [box((5, 5, 5), (11, 11, 11), "pipe"),
                    box((4, 4, 4), (12, 12, 12), "body"),
                    box((3.5, 5.5, 5.5), (12.5, 10.5, 10.5), "bead"),
                    box((5.5, 5.5, 3.5), (10.5, 10.5, 12.5), "bead"),
                    # Cooling fins stacked up the body, and the hot plate a basin sits on
                    box((2.5, 4.5, 2.5), (13.5, 5.5, 13.5), "fin"),
                    box((2.5, 7, 2.5), (13.5, 8, 13.5), "fin"),
                    box((2.5, 9.5, 2.5), (13.5, 10.5, 13.5), "fin"),
                    box((2, 12, 2), (14, 15, 14), "plate"),
                    box((3.5, 15, 3.5), (12.5, 15.5, 12.5), "body")]
    return elements


def heat_glow():
    """Hot metal over the fins and round the hot plate, drawn over the exchanger when it is running hot."""
    return [box((2.4, 4.6, 2.4), (13.6, 5.4, 13.6), "glow"),
            box((2.4, 7.1, 2.4), (13.6, 7.9, 13.6), "glow"),
            box((2.4, 9.6, 2.4), (13.6, 10.4, 13.6), "glow"),
            box((1.9, 12.5, 1.9), (14.1, 14.5, 14.1), "glow"),
            box((3.6, 15.5, 3.6), (12.4, 15.6, 12.4), "glow")]


PIPE_STYLES = {
    "intake_manifold": ("intake", {"pipe": CUT_COPPER, "bead": COPPER, "flange": COPPER, "gasket": DARK,
                                   "bolt": BRASS, "particle": CUT_COPPER}),
    "exhaust_manifold": ("exhaust", {"pipe": DARK, "bead": IRON, "flange": IRON, "gasket": DARK, "wrap": SMOOTH,
                                     "bolt": BRASS, "particle": DARK}),
    "throttle": ("throttle", {"pipe": CUT_COPPER, "bead": COPPER, "flange": COPPER, "gasket": DARK, "body": IRON_BLOCK,
                              "dark": DARK, "bolt": BRASS, "particle": IRON_BLOCK}),
    "maf_sensor": ("maf", {"pipe": CUT_COPPER, "bead": COPPER, "flange": COPPER, "gasket": DARK, "body": BRASS,
                           "dark": DARK, "plug": IRON_BLOCK, "bolt": BRASS, "particle": BRASS}),
    "heat_exchanger": ("heat", {"pipe": DARK, "bead": IRON, "flange": IRON, "gasket": DARK, "wrap": SMOOTH,
                                 "body": IRON_BLOCK, "fin": COPPER, "plate": DARK, "bolt": BRASS,
                                 "particle": DARK}),
}


def pipes():
    for name, (style, textures) in PIPE_STYLES.items():
        arm_style = "exhaust" if style in ("exhaust", "heat") else "intake"
        core_elements = core(style)
        write(name + "/core", textures, core_elements)
        if style == "heat":
            for level, glow in (("kindled", MAGMA), ("seething", LAVA)):
                elements = [dict(e, neoforge_data={"block_light": 15, "sky_light": 15}) for e in heat_glow()]
                write(name + "/glow_" + level, {"glow": glow, "particle": glow}, elements)
        if name.endswith("_manifold"):
            # Fittings for the shapes a run takes, drawn along z (the north-south axis) and turned for the others
            write(name + "/core_elbow", textures, [box((5, 5, 5), (11, 11, 11), "pipe"),
                                                   box((4.25, 4.25, 4.25), (11.75, 11.75, 11.75), "bead")])
            for axis, direction in (("x", "east"), ("y", "up"), ("z", "north")):
                straight = [box((5, 5, 4.5), (11, 11, 11.5), "pipe"), box((4.5, 4.5, 7), (11.5, 11.5, 9), "bead")]
                tee = [box((5, 5, 4.5), (11, 11, 11.5), "pipe"), box((4.25, 4.25, 4.25), (11.75, 11.75, 11.75), "bead"),
                       box((4, 4, 6.5), (12, 12, 9.5), "flange")]
                write(name + "/core_straight_" + axis, textures, [oriented(e, direction) for e in straight])
                write(name + "/core_tee_" + axis, textures, [oriented(e, direction) for e in tee])
        arms = {}
        for direction in ("north", "south", "east", "west", "up", "down"):
            arms[direction] = [oriented(e, direction) for e in arm(arm_style)]
            write(name + "/arm_" + direction, textures, arms[direction])
        # The item shows a straight length of pipe
        write(name + "/item", textures, core_elements + arms["east"] + arms["west"])


# --- starter, battery, alternator ----------------------------------------------------------------------------------

def starter():
    textures = {"body": CASING, "pinion": BRASS, "coil": COPPER, "plate": IRON, "bolt": BRASS, "particle": CASING}
    elements = [
        # Mounting plate round the input socket, so the shaft shows in the middle
        box((4, 0, 4), (12, 2, 6), "body"), box((4, 0, 10), (12, 2, 12), "body"),
        box((4, 0, 6), (6, 2, 10), "body"), box((10, 0, 6), (12, 2, 10), "body"),
        # Motor can, an octagon, with the wound field banding it
        box((3, 2, 4), (13, 10, 12), "body"),
        box((4, 2, 3), (12, 10, 13), "body"),
        box((2.75, 3.5, 4.75), (13.25, 4.5, 11.25), "coil"),
        box((4.75, 3.5, 2.75), (11.25, 4.5, 13.25), "coil"),
        box((2.75, 7.5, 4.75), (13.25, 8.5, 11.25), "coil"),
        box((4.75, 7.5, 2.75), (11.25, 8.5, 13.25), "coil"),
        # Solenoid riding on the side, with its terminals
        box((12, 3, 5.5), (15.5, 9, 10.5), "coil"),
        box((14.5, 9, 6), (15.5, 10.5, 7), "plate"),
        box((14.5, 9, 9), (15.5, 10.5, 10), "plate"),
        # Nose housing and mounting ears
        box((5, 10, 5), (11, 12, 11), "body"),
        box((1, 10, 6.5), (15, 12, 9.5), "plate"),
        box((1.75, 12, 7.25), (2.75, 12.5, 8.25), "bolt"),
        box((13.25, 12, 7.25), (14.25, 12.5, 8.25), "bolt"),
        # The pinion that meshes with the crank
        box((6.5, 12, 6.5), (9.5, 13, 9.5), "pinion"),
        box((6, 13, 6), (10, 16, 10), "pinion"),
        box((5, 13.5, 7), (6, 15.5, 9), "pinion"),
        box((10, 13.5, 7), (11, 15.5, 9), "pinion"),
        box((7, 13.5, 5), (9, 15.5, 6), "pinion"),
        box((7, 13.5, 10), (9, 15.5, 11), "pinion"),
    ]
    write("starter/block", textures, elements)


BATTERY_SIDES = ("north", "east", "south", "west")


def battery_textures():
    # "dark" is the unlit lamp: a model that names a texture it does not define shows the purple and black missing texture
    return {"body": ZINC, "band": BRASS, "cap": CASING, "pole": IRON, "lid": DARK, "vent": IRON_BLOCK, "dark": BLACK,
            "particle": ZINC}


# For each face, the joined edge of the block (or the top) that moves a side of the face's [u0, v0, u1, v1] patch one pixel in:
# {side: (index in the patch, step)}. Which side of the patch a world edge falls on follows from _face_patch.
BATTERY_JOIN_TRIMS = {
    "north": {"east": (0, 1), "west": (2, -1), "top": (1, 1)},
    "south": {"west": (0, 1), "east": (2, -1), "top": (1, 1)},
    "west": {"north": (0, 1), "south": (2, -1), "top": (1, 1)},
    "east": {"south": (0, 1), "north": (2, -1), "top": (1, 1)},
    "up": {"west": (0, 1), "east": (2, -1), "north": (1, 1), "south": (3, -1)},
}


def battery_shell(n=False, e=False, s=False, w=False, u=False):
    """The battery with the sides in n (north, z-), e (x+), s (z+), w (x-) and the top u joined to another battery, as a
    tank or a vault runs on into the next one. The body, the band and the lid reach the edge of the block on a joined side and
    leave out the faces that look into the neighbour; a battery with another on top has a body that runs up to the next
    one and no terminals, caps or vents (they stay on the top of the stack), but keeps the lid ring so each still reads as
    a cell and carries its own lamp strip. Everything on a side that is not joined is as a lone battery has it."""
    x0, x1 = (0 if w else 2), (16 if e else 14)
    z0, z1 = (0 if n else 2), (16 if s else 14)
    tx0, tx1 = (0 if w else 1.5), (16 if e else 14.5)
    tz0, tz1 = (0 if n else 1.5), (16 if s else 14.5)

    strips = []

    def flat(face, lo, hi, tex):
        """One face with no thickness: the piece of a joined face that stays drawn."""
        patch = [round(v, 4) for v in _face_patch(lo, hi, face)]
        return {"from": [round(v, 4) for v in lo], "to": [round(v, 4) for v in hi],
                "faces": {face: {"texture": "#" + tex, "uv": patch}}}

    def margin_strips(face, a, b, tex):
        """The part of a joined face that a neighbour may not cover. Every neighbour has a body 2 units in from the edges its own
        joins leave free (1.5 for the band and the lid ring) and one lid and band below 13.5, so the middle of the face is left out
        and only the margin beside a side this one is joined on stays; the body above 13.5 (there only with a battery on top)
        stays whole, since a neighbour that is not stacked has nothing there."""
        base = 1.5 if tex in ("band", "lid") else 2
        out = []
        if face == "up":
            y = b[1]
            if a[0] < base:
                out.append(flat(face, (a[0], y, a[2]), (min(b[0], base), y, b[2]), tex))
            if b[0] > 16 - base:
                out.append(flat(face, (max(a[0], 16 - base), y, a[2]), (b[0], y, b[2]), tex))
            xl, xh = max(a[0], base), min(b[0], 16 - base)
            if a[2] < base:
                out.append(flat(face, (xl, y, a[2]), (xh, y, min(b[2], base)), tex))
            if b[2] > 16 - base:
                out.append(flat(face, (xl, y, max(a[2], 16 - base)), (xh, y, b[2]), tex))
            return out
        along_x = face in ("north", "south")
        lo, hi = (a[0], b[0]) if along_x else (a[2], b[2])
        if a[1] >= 13.5:
            spans = [(lo, hi)]
        else:
            spans = []
            if lo < base:
                spans.append((lo, min(hi, base)))
            if hi > 16 - base:
                spans.append((max(lo, 16 - base), hi))
        for p0, p1 in spans:
            if face == "east":
                out.append(flat(face, (b[0], a[1], p0), (b[0], b[1], p1), tex))
            elif face == "west":
                out.append(flat(face, (a[0], a[1], p0), (a[0], b[1], p1), tex))
            elif face == "south":
                out.append(flat(face, (p0, a[1], b[2]), (p1, b[1], b[2]), tex))
            else:
                out.append(flat(face, (p0, a[1], a[2]), (p1, b[1], a[2]), tex))
        return out

    def part(a, b, tex, omit=()):
        element = box(a, b, tex)
        for face in omit:
            element["faces"].pop(face, None)
        # A face on the edge of the block on a joined side looks into the neighbour, but only where the neighbour is there too: it
        # may be narrower (not joined to what this one is joined to) or lower (no battery on its top), and a face left out there
        # would be a slot into the inside of the body, where an L bends or a stack steps. The part that every neighbour covers is
        # left out; the rest of the face stays as a thin strip (it is inside the bank wherever the neighbour matches).
        for face, on in (("west", w and a[0] == 0), ("east", e and b[0] == 16), ("north", n and a[2] == 0),
                         ("south", s and b[2] == 16), ("up", u and b[1] == 16)):
            if on:
                element["faces"].pop(face, None)
                strips.extend(margin_strips(face, a, b, tex))
        # A face that runs on into a neighbour would show the texture's own border (the brass plate's bevel, a dark edge line) again
        # where the two meet, as a seam: leave out the border pixel on each joined edge, so the texture runs on across the join
        edge = {"west": w and a[0] == 0, "east": e and b[0] == 16, "north": n and a[2] == 0, "south": s and b[2] == 16,
                "top": u and b[1] == 16}
        for face, moves in BATTERY_JOIN_TRIMS.items():
            if face not in element["faces"]:
                continue
            patch = list(element["faces"][face].get("uv") or _face_patch(a, b, face))
            trimmed = False
            for side, (index, step) in moves.items():
                if edge[side]:
                    patch[index] += step
                    trimmed = True
            if trimmed:
                element["faces"][face]["uv"] = [round(v, 4) for v in patch]
        return element

    elements = [
        # The body, in three lengths round the band and the lid ring, whose faces would share its planes on a joined side
        part((x0, 0, z0), (x1, 5, z1), "body", omit=("up",)),
        part((x0, 8, z0), (x1, 12, z1), "body", omit=("up", "down")),
        part((tx0, 5, tz0), (tx1, 8, tz1), "band"),
        part((tx0, 12, tz0), (tx1, 13.5, tz1), "lid"),
    ]
    if u:
        elements.append(part((x0, 13.5, z0), (x1, 16, z1), "body", omit=("down",)))
    else:
        elements.append(part((3, 13.5, 3), (13, 14, 13), "cap", omit=("down",)))
        # Positive terminal (brass) and negative (iron), each a tapered post
        elements += [
            part((4, 14, 6), (6.5, 15, 8.5), "band", omit=("down",)),
            part((4.75, 15, 6.75), (5.75, 16, 7.75), "band", omit=("down",)),
            part((9.5, 14, 6), (12, 15, 8.5), "pole", omit=("down",)),
            part((10.25, 15, 6.75), (11.25, 16, 7.75), "pole", omit=("down",)),
        ]
        # Filler caps over the six cells
        for x in (3.5, 6.75, 10):
            for z in (3.25, 10.75):
                elements.append(part((x, 14, z), (x + 2.5, 14.6, z + 2), "vent", omit=("down",)))
    # Hold-down straps on the two long faces, where nothing is joined
    if not w:
        elements += [part((1.7, 0, 5), (2, 5, 6.5), "lid"), part((1.7, 0, 9.5), (2, 5, 11), "lid")]
    if not e:
        elements += [part((14, 0, 5), (14.3, 5, 6.5), "lid"), part((14, 0, 9.5), (14.3, 5, 11), "lid")]
    # A piece whose every face looks into a neighbour (the middle of the body, with all four sides joined) is nothing to draw, and a
    # model with an element that has no faces does not load at all: the game shows the missing model for the whole block
    return [e for e in elements + strips if e["faces"]]


def battery_joins_name(n, e, s, w, u):
    return "".join("1" if on else "0" for on in (n, e, s, w, u))


def battery_tier_textures(kind):
    """The materials of each tier of battery. The plain battery is zinc, brass and andesite, as it always was; the next tier will
    need its own materials, to read apart on a bank. The dry cell is copper-cased with an iron band; the large battery is a steel
    tank with a brass band and a black lid."""
    if kind == "dry_cell":
        return {"body": COPPER_CASING, "band": IRON, "cap": CASING, "pole": IRON, "lid": DARK, "vent": IRON_BLOCK, "dark": BLACK,
                "particle": COPPER_CASING}
    if kind == "large_battery":
        return {"body": IRON_BLOCK, "band": BRASS, "cap": DARK, "pole": IRON, "lid": DARK, "vent": CASING, "dark": BLACK,
                "particle": IRON_BLOCK}
    return battery_textures()


def battery():
    for kind in ("battery", "dry_cell", "large_battery"):
        battery_tier(kind, battery_tier_textures(kind))


def battery_tier(kind, textures):
    lit_textures = dict(textures, lit=MAGMA)
    # One model for each of the 32 ways the neighbours can join it; the blockstate picks by the join properties
    for bits in range(32):
        joins = tuple(bool(bits >> i & 1) for i in range(5))
        write(kind + "/block_j" + battery_joins_name(*joins), textures, battery_shell(*joins))
    # The inventory model, and the one the particle and the lone battery use: a lone battery with its lamps dark
    lone = battery_shell()
    for side in BATTERY_SIDES:
        lone += battery_gauge_side(side, "dark", 0)
    write(kind + "/block", textures, lone)
    write_item(kind + "/item", kind + "/block")
    # Charge gauge: four slots along each face of the lid, dark when empty. gauge_N lights the first N of them (the
    # blockstate's charge property, see BatteryChemistry.gaugeStage), so the charge reads from any side without goggles.
    # A side that is joined to another battery has no lid face to put them on.
    for side in BATTERY_SIDES:
        write(kind + "/block_dark_" + side, textures, battery_gauge_side(side, "dark", 0))
        for stage in range(1, 5):
            write(kind + "/block_gauge_%d_%s" % (stage, side), lit_textures, battery_gauge_side(side, "lit", stage),
                  ambientocclusion=False)


def battery_gauge_side(side, texture, lit_cells):
    """The gauge cells on one face of the lid: all four dark (lit_cells 0), or the first lit_cells lit. A lit cell is a hair
    wider and taller than the dark one under it, so no face of one lies in the plane of a face of the other, and a cell has
    no face on the lid (which it would share with the lid's own)."""
    cells = []
    out = 0.1 if not lit_cells else 0.2
    y0, y1 = (12.5, 13.0) if not lit_cells else (12.4, 13.1)
    grow = 0 if not lit_cells else 0.1
    for i in range(4):
        if lit_cells and i >= lit_cells:
            continue
        a = 3.5 + 2.5 * i - grow
        b = 3.5 + 2.5 * i + 2 + grow
        lo, hi, inner = {
            "north": ((a, y0, 1.5 - out), (b, y1, 1.5), "south"),
            "south": ((a, y0, 14.5), (b, y1, 14.5 + out), "north"),
            "west": ((1.5 - out, y0, a), (1.5, y1, b), "east"),
            "east": ((14.5, y0, a), (14.5 + out, y1, b), "west"),
        }[side]
        cell = box(lo, hi, texture)
        del cell["faces"][inner]
        cells.append(cell)
    return cells


def alternator():
    textures = {"body": COPPER_CASING, "band": IRON, "cap": CASING, "vent": DARK, "bolt": BRASS, "lug": IRON_BLOCK,
                "particle": COPPER_CASING}
    elements = [
        box((2, 3, 2), (14, 13, 14), "body"),
        box((1.5, 6.5, 1.5), (14.5, 9.5, 14.5), "band"),
        box((3, 1, 3), (13, 3, 13), "cap"),
        box((3, 13, 3), (13, 15, 13), "cap"),
    ]
    # Cooling slots in the end housings
    for y in (4, 10.5):
        elements += [box((4, y, 1.75), (12, y + 1, 2), "vent"), box((4, y, 14), (12, y + 1, 14.25), "vent"),
                     box((1.75, y, 4), (2, y + 1, 12), "vent"), box((14, y, 4), (14.25, y + 1, 12), "vent")]
    # Through-bolts at the corners, mounting lugs and the rectifier pack with its output stud
    for x in (2.2, 12.8):
        for z in (2.2, 12.8):
            elements.append(box((x, 1, z), (x + 1, 15, z + 1), "bolt"))
    elements += [box((0.5, 4.5, 7), (2, 6, 9), "lug"), box((14, 4.5, 7), (15.5, 6, 9), "lug"),
                 box((14.25, 9.75, 5.5), (15.75, 12.25, 10.5), "band"),
                 box((15.75, 10.25, 7.25), (16, 11.25, 8.25), "bolt")]
    write("alternator/block", textures, elements)


TURBO_WHEEL_TEXTURES = {"blade": BRASS, "hot": IRON_BLOCK, "shaft": IRON, "hub": IRON, "particle": BRASS}


def spun_box(a, b, tex, angle):
    """A box turned about the vertical axis through the middle of the block."""
    element = box(a, b, tex)
    if angle:
        element["rotation"] = {"origin": [8, (a[1] + b[1]) / 2, 8], "axis": "y", "angle": angle}
    return element


def blade_fan(slices, tex, thickness=0.55):
    """Eight vanes from four crossed plates (two square on, two at 45 degrees). Each slice is (y0, y1, radius): the
    stack steps in towards the nose, so the vanes sweep back from the hub like a real impeller. Plates at different
    angles are offset a hair in height so crossing faces never fight."""
    half = thickness / 2
    parts = []
    for i, angle in enumerate((0, 45, 90, -45)):
        nudge = 0.02 * i
        for y0, y1, r in slices:
            lo, hi = (y0, y1 - nudge) if y0 < y1 else (y1 + nudge, y0)
            a = (8 - r, lo, 8 - half) if angle in (0, 45, -45) else (8 - half, lo, 8 - r)
            b = (8 + r, hi, 8 + half) if angle in (0, 45, -45) else (8 + half, hi, 8 + r)
            parts.append(spun_box(a, b, tex, 0 if angle in (0, 90) else angle))
    return parts


def turbo_wheel():
    """Compressor wheel up (+Y), turbine wheel down, joined by the shaft. Each is a stepped, tapering hub on a back
    plate with a fan of vanes whose outline steps in towards the nose; the turbine is the finer, heavier of the two."""
    parts = [box((7.25, 6, 7.25), (8.75, 10, 8.75), "shaft")]
    # Compressor: back plate, tapering hub, nose nut, and vanes that shorten as they rise
    parts += [
        box((4.6, 10, 4.6), (11.4, 10.5, 11.4), "blade"),
        box((6.5, 10.5, 6.5), (9.5, 12.2, 9.5), "hub"),
        box((7, 12.2, 7), (9, 13.8, 9), "hub"),
        box((7.4, 13.8, 7.4), (8.6, 14.8, 8.6), "hub"),
        box((7.75, 14.8, 7.75), (8.25, 15.5, 8.25), "blade"),
    ]
    parts += blade_fan([(10.5, 11.9, 5.2), (11.9, 13.1, 4.4), (13.1, 14.1, 3.4), (14.1, 14.9, 2.4)], "blade")
    # Turbine: heavier back plate, a shorter, fatter hub, and a denser, shallower fan
    parts += [
        box((4.2, 5.5, 4.2), (11.8, 6, 11.8), "hot"),
        box((6.4, 3.8, 6.4), (9.6, 5.5, 9.6), "hub"),
        box((6.9, 2.4, 6.9), (9.1, 3.8, 9.1), "hub"),
        box((7.4, 1.4, 7.4), (8.6, 2.4, 8.6), "hub"),
        box((7.75, 0.8, 7.75), (8.25, 1.4, 8.25), "hot"),
    ]
    parts += blade_fan([(5.5, 4.4, 4.6), (4.4, 3.4, 3.8), (3.4, 2.4, 3.0), (2.4, 1.4, 2.1)], "hot", 0.45)
    return parts


def turbocharger():
    """Compressor housing up (+Y), turbine housing down, bearing housing between. The wheels are a separate model so
    they can spin; both housings are open at the middle of their outer faces so the wheels show."""
    textures = {"compressor": COPPER_CASING, "turbine": DARK, "centre": IRON, "bolt": BRASS, "pipe": BRASS,
                "particle": COPPER_CASING}

    def ring(y0, y1, hole, tex):
        lo, hi = 8 - hole, 8 + hole
        return [box((1, y0, 1), (15, y1, lo), tex), box((1, y0, hi), (15, y1, 15), tex),
                box((1, y0, lo), (lo, y1, hi), tex), box((hi, y0, lo), (15, y1, hi), tex)]

    elements = ring(10, 16, 2.5, "compressor") + ring(0, 6, 2, "turbine")
    elements += [
        # Bearing housing between the two, with its oil feed and the clamp bands that hold the housings on
        box((4.5, 6, 4.5), (11.5, 10, 11.5), "centre"),
        box((3.5, 6.5, 3.5), (12.5, 7.5, 12.5), "bolt"),
        box((3.5, 8.5, 3.5), (12.5, 9.5, 12.5), "bolt"),
        box((11.5, 7, 7.25), (13.5, 8.75, 8.75), "pipe"),
        box((12.75, 8.75, 7.25), (13.5, 10.5, 8.75), "pipe"),
    ]
    for x in (1.6, 13.4):
        for z in (1.6, 13.4):
            elements.append(box((x, 15.6, z), (x + 1, 16.2, z + 1), "bolt"))
            elements.append(box((x, -0.2, z), (x + 1, 0.4, z + 1), "bolt"))
    write("turbocharger/block", textures, elements)

    wheel_textures = TURBO_WHEEL_TEXTURES
    wheel = turbo_wheel()
    write("turbocharger/wheel", wheel_textures, wheel)
    write("turbocharger/item", {**textures, **wheel_textures}, elements + wheel)


def variable_turbocharger():
    """A turbocharger with a ring of vanes round its turbine: the same housings and wheels, in gold-coloured
    compressor casing, with a vane ring and an actuator on the turbine housing."""
    textures = {"compressor": BRASS_CASING, "turbine": DARK, "centre": IRON, "bolt": BRASS, "pipe": BRASS,
                "vane": IRON_BLOCK, "actuator": COPPER_CASING, "particle": BRASS_CASING}

    def ring(y0, y1, hole, tex):
        lo, hi = 8 - hole, 8 + hole
        return [box((1, y0, 1), (15, y1, lo), tex), box((1, y0, hi), (15, y1, 15), tex),
                box((1, y0, lo), (lo, y1, hi), tex), box((hi, y0, lo), (15, y1, hi), tex)]

    elements = ring(10, 16, 2.5, "compressor") + ring(0, 6, 2, "turbine")
    elements += [
        box((4.5, 6, 4.5), (11.5, 10, 11.5), "centre"),
        box((3.5, 6.5, 3.5), (12.5, 7.5, 12.5), "bolt"),
        box((3.5, 8.5, 3.5), (12.5, 9.5, 12.5), "bolt"),
        box((11.5, 7, 7.25), (13.5, 8.75, 8.75), "pipe"),
        box((12.75, 8.75, 7.25), (13.5, 10.5, 8.75), "pipe"),
        # The vane ring: a thin collar of blades just inside the turbine housing, and the actuator that turns it
        box((2.5, 2, 2.5), (13.5, 3, 13.5), "vane"),
        box((3.5, 3, 3.5), (12.5, 3.75, 12.5), "vane"),
        box((13.5, 1.5, 6), (16, 4.5, 10), "actuator"),
        box((15.5, 2.25, 7.25), (16.5, 3.75, 8.75), "bolt"),
    ]
    for x in (1.6, 13.4):
        for z in (1.6, 13.4):
            elements.append(box((x, 15.6, z), (x + 1, 16.2, z + 1), "bolt"))
            elements.append(box((x, -0.2, z), (x + 1, 0.4, z + 1), "bolt"))
    write("variable_turbocharger/block", textures, elements)

    wheel_textures = TURBO_WHEEL_TEXTURES
    wheel = turbo_wheel()
    write("variable_turbocharger/item", {**textures, **wheel_textures}, elements + wheel)


def supercharger():
    """A belt-driven blower along the Y axis: a finned housing with the shaft through both ends, open ring plates so
    the rotor shows, and an inlet flange on each side. The rotor (a two-lobe roots pair on the shaft) is a separate
    model so it can spin."""
    textures = {"housing": CASING, "plate": IRON, "fin": DARK, "bolt": BRASS, "flange": COPPER_CASING,
                "particle": CASING}

    def ring(y0, y1, hole, tex):
        lo, hi = 8 - hole, 8 + hole
        return [box((1, y0, 1), (15, y1, lo), tex), box((1, y0, hi), (15, y1, 15), tex),
                box((1, y0, lo), (lo, y1, hi), tex), box((hi, y0, lo), (15, y1, hi), tex)]

    elements = ring(0, 2, 3, "plate") + ring(14, 16, 3, "plate")
    elements += [
        # The housing: four walls round a hollow where the rotor turns
        box((1.5, 2, 1.5), (14.5, 14, 4), "housing"),
        box((1.5, 2, 12), (14.5, 14, 14.5), "housing"),
        box((1.5, 2, 4), (4, 14, 12), "housing"),
        box((12, 2, 4), (14.5, 14, 12), "housing"),
        # Cooling fins round the middle
        box((1, 5, 1), (15, 6, 15), "fin"),
        box((1, 10, 1), (15, 11, 15), "fin"),
    ]
    # Inlet flanges on each side, where the intake pipes bolt on
    elements += [box((6, 4, 0), (10, 12, 1.5), "flange"), box((6, 4, 14.5), (10, 12, 16), "flange"),
                 box((0, 4, 6), (1.5, 12, 10), "flange"), box((14.5, 4, 6), (16, 12, 10), "flange")]
    for x in (1.6, 13.4):
        for z in (1.6, 13.4):
            elements.append(box((x, 15.8, z), (x + 1, 16.2, z + 1), "bolt"))
            elements.append(box((x, -0.2, z), (x + 1, 0.2, z + 1), "bolt"))
    write("supercharger/block", textures, elements)

    rotor_textures = {"shaft": IRON, "lobe": BRASS, "particle": BRASS}
    rotor = [
        box((6, 0, 6), (10, 16, 10), "shaft"),
        # Two lobes at right angles, like the pair of a roots blower seen end on
        box((4.5, 3, 6.75), (11.5, 13, 9.25), "lobe"),
        box((6.75, 3, 4.5), (9.25, 13, 11.5), "lobe"),
        box((5.25, 4, 5.25), (10.75, 12, 10.75), "lobe"),
    ]
    write("supercharger/rotor", rotor_textures, rotor)
    write("supercharger/item", {**textures, **rotor_textures}, elements + rotor)


def variable_supercharger():
    """A supercharger in gold-coloured casing with a variable-ratio drive: the same ring plates, flanges and rotor
    (shared, so it spins), with a drive housing and its actuator on the corner and a gold band round the middle."""
    textures = {"housing": BRASS_CASING, "plate": IRON, "fin": DARK, "bolt": BRASS, "flange": COPPER_CASING,
                "drive": IRON_BLOCK, "actuator": COPPER_CASING, "particle": BRASS_CASING}

    def ring(y0, y1, hole, tex):
        lo, hi = 8 - hole, 8 + hole
        return [box((1, y0, 1), (15, y1, lo), tex), box((1, y0, hi), (15, y1, 15), tex),
                box((1, y0, lo), (lo, y1, hi), tex), box((hi, y0, lo), (15, y1, hi), tex)]

    elements = ring(0, 2, 3, "plate") + ring(14, 16, 3, "plate")
    elements += [
        box((1.5, 2, 1.5), (14.5, 14, 4), "housing"),
        box((1.5, 2, 12), (14.5, 14, 14.5), "housing"),
        box((1.5, 2, 4), (4, 14, 12), "housing"),
        box((12, 2, 4), (14.5, 14, 12), "housing"),
        # One cooling fin, and a band of drive plates in place of the other: the variable-ratio drive
        box((1, 5, 1), (15, 6, 15), "fin"),
        box((1, 10, 1), (15, 10.75, 15), "drive"),
        box((1.25, 11, 1.25), (14.75, 11.75, 14.75), "drive"),
        # Its actuator, on the corner away from the flanges
        box((1, 6.5, 14.5), (4.5, 9.5, 16), "actuator"),
        box((2, 7.25, 15.75), (3.5, 8.75, 16.4), "bolt"),
    ]
    elements += [box((6, 4, 0), (10, 12, 1.5), "flange"), box((6, 4, 14.5), (10, 12, 16), "flange"),
                 box((0, 4, 6), (1.5, 12, 10), "flange"), box((14.5, 4, 6), (16, 12, 10), "flange")]
    for x in (1.6, 13.4):
        for z in (1.6, 13.4):
            elements.append(box((x, 15.8, z), (x + 1, 16.2, z + 1), "bolt"))
            elements.append(box((x, -0.2, z), (x + 1, 0.2, z + 1), "bolt"))
    write("variable_supercharger/block", textures, elements)

    rotor_textures = {"shaft": IRON, "lobe": BRASS, "particle": BRASS}
    rotor = [
        box((6, 0, 6), (10, 16, 10), "shaft"),
        box((4.5, 3, 6.75), (11.5, 13, 9.25), "lobe"),
        box((6.75, 3, 4.5), (9.25, 13, 11.5), "lobe"),
        box((5.25, 4, 5.25), (10.75, 12, 10.75), "lobe"),
    ]
    write("variable_supercharger/item", {**textures, **rotor_textures}, elements + rotor)


def cast_flywheel():
    """A spoked iron flywheel on the shaft, along the Y axis (the block state turns it). The block model is only the
    bearing collars either side; the wheel is a separate model so it can spin with the shaft. A steel rim with stepped
    corners, four thin cast-iron spokes with open gaps between them, and a dark hub bolted in brass. Everything stays
    inside 4 to 12 along the axis, which is what the block's collision shape covers. The inventory item is turned so the
    face of the wheel, not its shaft cap, is what shows."""
    textures = {"collar": CASING, "bolt": BRASS, "particle": IRON}
    collars = [box((5, 0, 5), (11, 2, 11), "collar"), box((5, 14, 5), (11, 16, 11), "collar")]
    write("cast_flywheel/block", textures, collars)

    disc_textures = {"axis": AXIS, "end": AXIS_TOP, "spoke": IRON, "rim": IRON_BLOCK, "hub": DARK, "bolt": BRASS,
                     "particle": IRON}
    # The shaft runs the block's full length so it turns with the axle, but its end caps lie inside the collars and in their
    # planes (y 0 and 16), where the two fight for the pixels: the shaft has no cap faces
    axle = shaft((6, 0, 6), (10, 16, 10))
    del axle["faces"]["up"], axle["faces"]["down"]
    disc = [
        axle,
        # The rim: four thin bars, with a corner piece a little lower and set in at each corner so it reads as eight-sided
        box((3.5, 6, 0.5), (12.5, 10, 2.5), "rim"),
        box((3.5, 6, 13.5), (12.5, 10, 15.5), "rim"),
        box((0.5, 6, 3.5), (2.5, 10, 12.5), "rim"),
        box((13.5, 6, 3.5), (15.5, 10, 12.5), "rim"),
        box((1.5, 6.25, 1.5), (3.75, 9.75, 3.75), "spoke"),
        box((12.25, 6.25, 1.5), (14.5, 9.75, 3.75), "spoke"),
        box((1.5, 6.25, 12.25), (3.75, 9.75, 14.5), "spoke"),
        box((12.25, 6.25, 12.25), (14.5, 9.75, 14.5), "spoke"),
        # Four slim spokes into the rim, the one pair in two halves so no two top faces share a plane
        box((2, 7, 7.25), (14, 9, 8.75), "spoke"),
        box((7.25, 7, 2), (8.75, 9, 7.25), "spoke"),
        box((7.25, 7, 8.75), (8.75, 9, 14), "spoke"),
        # The hub, a boss that stands proud of the thin wheel either side
        box((5.5, 4.5, 5.5), (10.5, 11.5, 10.5), "hub"),
    ]
    # Bolts: round the hub, and one on each corner piece
    for x, z in ((5.9, 5.9), (9.1, 5.9), (5.9, 9.1), (9.1, 9.1)):
        disc.append(box((x, 11.5, z), (x + 1, 11.9, z + 1), "bolt"))
        disc.append(box((x, 4.1, z), (x + 1, 4.5, z + 1), "bolt"))
    for x, z in ((2.1, 2.1), (12.9, 2.1), (2.1, 12.9), (12.9, 12.9)):
        disc.append(box((x, 9.75, z), (x + 1, 10.15, z + 1), "bolt"))
        disc.append(box((x, 5.85, z), (x + 1, 6.25, z + 1), "bolt"))
    write("cast_flywheel/disc", disc_textures, disc)
    # Turned a quarter about Z in the inventory so the wheel faces the viewer; the other displays keep the block's
    write("cast_flywheel/item", {**textures, **disc_textures}, collars + disc,
          display={"gui": {"rotation": [30, 225, 90], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]}})


def _tbox(a, b, tex, **overrides):
    """A box whose faces all show the texture at one pixel density: a face is cut from the texture's corner at its own size
    (so a thin bar is not stretched, and a face longer than the texture is stretched across it once, not wrapped)."""
    x1, y1, z1 = a
    x2, y2, z2 = b
    size = {"up": (x2 - x1, z2 - z1), "down": (x2 - x1, z2 - z1), "north": (x2 - x1, y2 - y1),
            "south": (x2 - x1, y2 - y1), "west": (z2 - z1, y2 - y1), "east": (z2 - z1, y2 - y1)}
    uv = {face: [0, 0, round(min(w, 16), 3), round(min(h, 16), 3)] for face, (w, h) in size.items()}
    return box(a, b, tex, uv=uv, **overrides)


def _octagon(radius, y0, y1, tex):
    """A stepped octagon about the shaft (centre 8, 8) from y0 to y1, as boxes that touch but never overlap: a wide strip,
    a tall strip either side of it, and two steps in each corner. Reaches radius either way."""
    k = radius * 0.414
    m = (k + radius) / 2 - 1.2
    n = radius - (radius - k) * 0.22
    c = 8
    out = [_tbox((c - radius, y0, c - k), (c + radius, y1, c + k), tex),
           _tbox((c - k, y0, c + k), (c + k, y1, c + radius), tex),
           _tbox((c - k, y0, c - radius), (c + k, y1, c - k), tex)]
    for sx in (1, -1):
        for sz in (1, -1):
            def at(x0, x1, z0, z1):
                xs = sorted((c + sx * x0, c + sx * x1))
                zs = sorted((c + sz * z0, c + sz * z1))
                return _tbox((xs[0], y0, zs[0]), (xs[1], y1, zs[1]), tex)
            out.append(at(k, m, k, n))
            out.append(at(m, n, k, m))
    return out


def dual_mass_flywheel():
    """A dual-mass flywheel on the shaft along the Y axis, as wide as Create's own flywheel (about 42 across, past its block, like
    Create's) and built as a real flywheel: rims, spokes and a hub, twice over, with the spring pack that makes it dual-mass
    in plain view. Low down is the big primary mass, a black-oxide wheel with a starter ring gear round its edge. On its rim
    stand eight packs of four brass coils between dark stops, as an arc-spring damper has them, and inside them sits the
    secondary mass: a smaller steel wheel with a red enamel stripe on its rim and black spokes, driven by eight steel lugs
    that stand between the spring packs. A steel hub, bolted in brass, runs through both. Both wheels are open. Along the axis
    it runs 3 to 13, a pixel past the block's collision shape either way, as Create's does. Diagonal pieces are the straight
    ones turned 45 degrees about the shaft (the lugs, 22.5); they are a little shorter, so no two faces ever share a plane
    where the bars cross."""
    textures = {"collar": CASING, "bolt": BRASS, "particle": IRON}
    collars = [box((5, 0, 5), (11, 2, 11), "collar"), box((5, 14, 5), (11, 16, 11), "collar")]
    write("dual_mass_flywheel/block", textures, collars)

    steel = "create_engines:block/flywheel_steel"
    oxide = "create_engines:block/flywheel_oxide"
    disc_textures = {"axis": AXIS, "end": AXIS_TOP, "steel": steel, "primary": oxide, "spoke": oxide, "stop": oxide,
                     "tooth": steel, "lug": steel, "coil": "create_engines:block/flywheel_coil",
                     "stripe": "create_engines:block/flywheel_enamel", "bolt": BRASS, "particle": oxide}
    axle = shaft((6, 0, 6), (10, 16, 10))
    del axle["faces"]["up"], axle["faces"]["down"]
    disc = [axle]
    c = 8
    ANGLES = (0, 45, -45, 90)

    def quarter(part):
        """A piece turned a quarter about the shaft, as the same box with its x and z ranges swapped about the centre. The game
        allows an element no more than 45 degrees of rotation, so a rotation of 90 stops the whole model loading."""
        (x1, y1, z1), (x2, y2, z2) = part["from"], part["to"]
        names = {face["texture"][1:] for face in part["faces"].values()}
        assert len(names) == 1, "a quarter turn is for a piece of one texture"
        xs = sorted((z1, z2))
        zs = sorted((2 * c - x2, 2 * c - x1))
        return _tbox((xs[0], y1, zs[0]), (xs[1], y2, zs[1]), names.pop())

    def turn(part, angle, y):
        if abs(angle) == 90:
            return quarter(part)
        if angle:
            part["rotation"] = {"origin": [c, y, c], "axis": "y", "angle": angle}
        return part

    def place(make, y):
        """make(sign, trim) is a piece (or a list of them) on the +z (sign 1) or -z (-1) side of the shaft. Add it, then turned
        to the two diagonals (trimmed a little in height) and to the x sides, both ways round."""
        for sign in (1, -1):
            for angle in ANGLES:
                made = make(sign, 0.15 if angle in (45, -45) else 0)
                for part in (made if isinstance(made, list) else [made]):
                    disc.append(turn(part, angle, y))

    def ring(apothem, thick, y0, y1, tex):
        half = apothem * 0.414 + 0.3

        def make(sign, trim):
            edge = c + sign * apothem
            z0, z1 = sorted((edge, edge - sign * thick))
            return _tbox((c - half, y0 + trim, z0), (c + half, y1 - trim, z1), tex)
        place(make, (y0 + y1) / 2)

    def spokes(inner, outer, y0, y1, width, tex):
        def make(sign, trim):
            z0, z1 = sorted((c + sign * inner, c + sign * outer))
            return _tbox((c - width, y0 + trim, z0), (c + width, y1 - trim, z1), tex)
        place(make, (y0 + y1) / 2)

    # Primary mass (low, the big one): a black-oxide wheel, rim 18 to 21 out, spokes, and a ring gear of steel teeth round it
    ring(21, 3, 3, 6, "primary")
    spokes(2.5, 18, 3.8, 5.2, 1.0, "spoke")

    def teeth(sign, trim):
        z0, z1 = sorted((c + sign * 21, c + sign * 22))
        return [_tbox((c + x - 0.75, 3.8 + trim, z0), (c + x + 0.75, 5.2 - trim, z1), "tooth") for x in (-7, -3.5, 0, 3.5, 7)]
    place(teeth, 4.5)

    # The spring pack: eight packs of four brass coils between a pair of dark stops, standing on the primary's rim
    def springs(sign, trim):
        z0, z1 = sorted((c + sign * 18.5, c + sign * 20.5))
        parts = [_tbox((c - 5.4, 6, z0), (c - 4.4, 10, z1), "stop"), _tbox((c + 4.4, 6, z0), (c + 5.4, 10, z1), "stop")]
        for i in range(4):
            x0 = c - 3.9 + i * 2.1
            parts.append(_tbox((x0, 6.5 + trim, z0 + 0.1), (x0 + 1.5, 9.5 - trim, z1 - 0.1), "coil"))
        return parts
    place(springs, 8)

    secondary_from = len(disc)
    # The lugs stop short of the spring packs' ring (18.5 out): they drive the packs from just inside it, so the secondary can swing
    # a good way either side of the primary (SpringPackLag) without a lug ever passing through a spring
    LUG_REACH = 18.3

    # Secondary mass (high, inside the springs): a steel wheel with its red stripe and black spokes, and eight lugs that reach out
    # between the spring packs to drive them (the +z, -z, +x and -x lugs, each turned 22.5 either way)
    ring(17, 3, 10, 12.4, "steel")
    ring(17, 3, 12.4, 13, "stripe")
    spokes(2.5, 14, 10.4, 11.8, 1.1, "spoke")
    for sign in (1, -1):
        for angle in (22.5, -22.5):
            lug = _tbox((c - 1.0, 6.2, c + sign * 17), (c + 1.0, 12.2, c + sign * LUG_REACH), "lug") if sign > 0 else \
                _tbox((c - 1.0, 6.2, c - LUG_REACH), (c + 1.0, 12.2, c - 17), "lug")
            disc.append(turn(lug, angle, 9))
            side = _tbox((c + sign * 17, 6.2, c - 1.0), (c + sign * LUG_REACH, 12.2, c + 1.0), "lug") if sign > 0 else \
                _tbox((c - LUG_REACH, 6.2, c - 1.0), (c - 17, 12.2, c + 1.0), "lug")
            disc.append(turn(side, angle, 9))

    secondary_to = len(disc)

    # The hub, steel, through both wheels, bolted in brass top and bottom; and bolts round the underside of the primary's rim
    disc.append(_tbox((5.5, 3, 5.5), (10.5, 12.6, 10.5), "steel"))
    for x, z in ((5.5, 5.5), (10, 5.5), (5.5, 10), (10, 10)):
        disc.append(_tbox((x, 12.6, z), (x + 0.5, 13, z + 0.5), "bolt"))
        disc.append(_tbox((x, 2.6, z), (x + 0.5, 3, z + 0.5), "bolt"))
    for x, z in ((c - 0.5, c - 19.5 - 0.5), (c - 0.5, c + 19.5 - 0.5), (c - 19.5 - 0.5, c - 0.5), (c + 19.5 - 0.5, c - 0.5),
                 (c - 13.8 - 0.5, c - 13.8 - 0.5), (c + 13.8 - 0.5, c - 13.8 - 0.5), (c - 13.8 - 0.5, c + 13.8 - 0.5),
                 (c + 13.8 - 0.5, c + 13.8 - 0.5)):
        disc.append(_tbox((x, 2.6, z), (x + 1, 3, z + 1), "bolt"))
    write("dual_mass_flywheel/disc", disc_textures, disc)
    # The same wheel in its two masses, which the renderer draws apart so the secondary can lag the primary through the
    # resonance (SpringPackLag): the secondary is its wheel, stripe, spokes and lugs; the primary is everything else, the
    # shaft, hub, bolts, rim, ring gear and spring packs. Together they are exactly disc, and a test holds them to it
    write("dual_mass_flywheel/primary", disc_textures, disc[:secondary_from] + disc[secondary_to:])
    write("dual_mass_flywheel/secondary", disc_textures, disc[secondary_from:secondary_to])
    # Turned a quarter about Z in the inventory so the wheel faces the viewer, and scaled to fit the slot
    write("dual_mass_flywheel/item", {**textures, **disc_textures}, collars + disc,
          display={"gui": {"rotation": [30, 225, 90], "translation": [0, 0, 0], "scale": [0.28, 0.28, 0.28]}})


def dyno():
    """A chassis dynamometer: a low bench with a pair of rollers between two frames and a read-out screen behind."""
    textures = {"base": CASING, "frame": IRON, "drum": DARK, "axle": IRON_BLOCK, "screen": BLACK, "bar": BRASS,
                "trace": RED, "bolt": BRASS, "particle": CASING}
    elements = [
        box((0, 0, 0), (16, 3, 16), "base"),
        box((0, 3, 1), (2.5, 9, 15), "frame"),
        box((13.5, 3, 1), (16, 9, 15), "frame"),
        # Two rollers, each on an axle through both frames
        box((2.5, 3.5, 2.5), (13.5, 7.5, 6.5), "drum"),
        box((2.5, 3.5, 9.5), (13.5, 7.5, 13.5), "drum"),
        box((1, 5, 4), (15, 6, 5), "axle"),
        box((1, 5, 11), (15, 6, 12), "axle"),
        # The read-out: a screen on the back rail with a few bars of the curve
        box((4, 9, 13.5), (12, 12, 15.5), "frame"),
        box((4.5, 9.5, 13.4), (11.5, 11.5, 13.5), "screen"),
    ]
    for i, height in enumerate((0.6, 1.0, 1.4, 1.8, 1.5, 1.0)):
        x = 5 + i * 1.0
        elements.append(box((x, 9.6, 13.3), (x + 0.6, 9.6 + height, 13.4), "bar"))
    elements.append(box((5, 11, 13.3), (11, 11.2, 13.4), "trace"))
    for x in (0.7, 14.2):
        for z in (2.2, 13.2):
            elements.append(box((x, 9, z), (x + 1, 9.5, z + 1), "bolt"))
    write("dyno/block", textures, elements)
    write_item("dyno/item", "dyno/block")


def black_box():
    """A flight recorder: a squat crash case in red enamel under a riveted lid, with a little read-out window on the front
    and a carrying handle."""
    textures = {"case": RED, "lid": RIVETED, "trim": IRON, "window": BLACK, "bar": BRASS, "trace": RED, "bolt": BRASS,
                "particle": RED}
    elements = [
        box((1, 0, 1), (15, 8, 15), "case"),
        box((0.5, 8, 0.5), (15.5, 10, 15.5), "lid"),
        # Read-out window on the front: a frame, the dark glass and a few bars of tape
        box((2.5, 2, 0.5), (13.5, 7, 1), "trim"),
        box((3, 2.5, 0.3), (13, 6.5, 0.5), "window"),
    ]
    for i, height in enumerate((1.0, 1.8, 1.2, 2.6, 1.6, 0.8)):
        x = 4 + i * 1.5
        elements.append(box((x, 3, 0.2), (x + 0.8, 3 + height, 0.3), "bar"))
    elements.append(box((4, 5.6, 0.2), (12, 5.8, 0.3), "trace"))
    # Handle across the lid
    elements.append(box((5, 10, 7), (6, 11.5, 9), "trim"))
    elements.append(box((10, 10, 7), (11, 11.5, 9), "trim"))
    elements.append(box((5, 11.5, 7), (11, 12.5, 9), "trim"))
    for x in (1.2, 14.2):
        for z in (1.2, 14.2):
            elements.append(box((x, 10, z), (x + 0.6, 10.4, z + 0.6), "bolt"))
    write("black_box/block", textures, elements)
    write_item("black_box/item", "black_box/block")


def intercooler():
    """A charge-air cooler along the Y axis (the block state turns it): a finned core between two end tanks, with an
    inlet stub on each end."""
    textures = {"tank": COPPER_CASING, "core": DARK, "fin": IRON, "stub": BRASS, "particle": COPPER_CASING}
    elements = [
        box((2, 0, 2), (14, 3, 14), "tank"),
        box((2, 13, 2), (14, 16, 14), "tank"),
        box((3, 3, 3), (13, 13, 13), "core"),
    ]
    # Fins across the core
    for i in range(7):
        y = 3.5 + i * 1.4
        elements.append(box((1.5, y, 2.5), (14.5, y + 0.6, 13.5), "fin"))
    elements += [box((6, 0, 6), (10, 1, 10), "stub"), box((6, 15, 6), (10, 16, 10), "stub")]
    write("intercooler/block", textures, elements)
    write_item("intercooler/item", "intercooler/block")


def muffler():
    """A muffler along the Y axis (the block state turns it): a wrapped canister between two end caps, with banding
    round it and an exhaust stub on each end."""
    textures = {"shell": SMOOTH, "cap": DARK, "band": IRON, "stub": IRON_BLOCK, "particle": DARK}
    elements = [
        box((2, 0, 2), (14, 2, 14), "cap"),
        box((2, 14, 2), (14, 16, 14), "cap"),
        box((3, 2, 3), (13, 14, 13), "shell"),
        box((2.5, 4.5, 2.5), (13.5, 5.5, 13.5), "band"),
        box((2.5, 10.5, 2.5), (13.5, 11.5, 13.5), "band"),
        box((6, 0, 6), (10, 1, 10), "stub"),
        box((6, 15, 6), (10, 16, 10), "stub"),
    ]
    write("muffler/block", textures, elements)
    write_item("muffler/item", "muffler/block")


def soot_trap():
    """A soot trap along the Y axis (the block state turns it): a dark filter canister with a grille window and two
    brass bands between steel caps, and an exhaust stub on each end."""
    textures = {"shell": DARK, "cap": IRON_BLOCK, "band": BRASS, "mesh": "create_engines:block/grille",
                "stub": IRON, "particle": DARK}
    elements = [
        box((2, 0, 2), (14, 2, 14), "cap"),
        box((2, 14, 2), (14, 16, 14), "cap"),
        box((3, 2, 3), (13, 14, 13), "shell"),
        box((2.5, 3.5, 2.5), (13.5, 4.5, 13.5), "band"),
        box((2.5, 11.5, 2.5), (13.5, 12.5, 13.5), "band"),
        box((2.6, 5.5, 2.6), (13.4, 10.5, 13.4), "mesh"),
        box((6, 0, 6), (10, 1, 10), "stub"),
        box((6, 15, 6), (10, 16, 10), "stub"),
    ]
    write("soot_trap/block", textures, elements)
    write_item("soot_trap/item", "soot_trap/block")


def soot():
    """A heap of soot lumps, for the item."""
    textures = {"soot": DARK, "dust": BLACK, "particle": DARK}
    elements = [
        box((3, 0, 4), (9, 4, 10), "soot"),
        box((8, 0, 7), (13, 3, 12), "soot"),
        box((5, 4, 5), (8, 6, 8), "soot"),
        box((6, 0, 1), (10, 2, 4), "soot"),
        box((2, 0, 10), (6, 1.5, 14), "dust"),
    ]
    write("soot/item", textures, elements)


def jerry_can():
    """A jerry can: a red slab with a handle across the top, a spout and a cap, and pressed ribs on its faces."""
    textures = {"body": RED, "rib": IRON_BLOCK, "handle": DARK, "cap": BRASS, "particle": RED}
    elements = [
        box((3, 0, 5.5), (13, 11, 10.5), "body"),
        box((4.5, 11, 6.5), (11.5, 12, 9.5), "body"),
        box((5, 12, 7), (6.5, 15, 9), "handle"),
        box((9.5, 12, 7), (11, 15, 9), "handle"),
        box((5, 15, 7), (11, 16, 9), "handle"),
        box((11.5, 9.5, 7.2), (14.5, 12.5, 8.8), "cap"),
        box((12.5, 12.5, 6.8), (15, 13.5, 9.2), "cap"),
    ]
    # Pressed ribs: a pair of bands and a cross on each broad face
    for z0, z1 in ((5.2, 5.5), (10.5, 10.8)):
        elements.append(box((4, 2, z0), (12, 3, z1), "rib"))
        elements.append(box((4, 8, z0), (12, 9, z1), "rib"))
        elements.append(box((7.5, 3, z0), (8.5, 8, z1), "rib"))
    write("jerry_can/item", textures, elements)


CASING_SPRITE = "create_engines:block/engine_casing"
CASING_TEXTURES = {"casing": CASING_SPRITE, "cap": IRON_BLOCK, "dark": DARK, "particle": CASING_SPRITE}


def casing_faces(faces, texture="casing", cull=True, tint=False):
    """Full-tile faces of a cube, for the sides named; CULLED against the neighbour so a clad run draws only its skin.
    A tinted face takes the paint of the casing block it belongs to (tint index 0)."""
    extra = {"tintindex": 0} if tint else {}
    return {f: dict({"texture": "#" + texture, "uv": [0, 0, 16, 16]}, **({"cullface": f} if cull else {}), **extra)
            for f in faces}


def engine_casing():
    """The engine casing, a cube of one face-filling tile so that its connected-texture sheet can join a run of it into
    one plate, and its cased variant. The cased variant is the same cube with its two ends opened into a recessed square
    for the part inside (a shaft, a crank, a cam), drawn standing on the Y axis and turned onto the part's axis by the
    blockstate. The sides use the casing sprite exactly, so they join the casing beside them too; the ends do not."""
    write("engine_casing/block", CASING_TEXTURES,
          [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": casing_faces(FACES, tint=True)}])
    write_item("engine_casing/item", "engine_casing/block")
    # The riveted and louvred plates: the same cube on their own sheet. Clad parts use the encased models further down.
    for kind in ("riveted", "louvred"):
        sprite = "create_engines:block/engine_%s_casing" % kind
        write("engine_%s_casing/block" % kind, {"casing": sprite, "particle": sprite},
              [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": casing_faces(FACES, tint=True)}])
        write_item("engine_%s_casing/item" % kind, "engine_%s_casing/block" % kind)
    # The window plate has a pane of glass in it, which is made of see-through pixels, so it draws in the translucent layer
    window = "create_engines:block/engine_window_casing"
    write("engine_window_casing/block", {"casing": window, "particle": window},
          [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": casing_faces(FACES, tint=True)}],
          render_type="minecraft:translucent")
    write_item("engine_window_casing/item", "engine_window_casing/block")
    elements = [{"from": [0, 0, 0], "to": [16, 16, 16],
                 "faces": casing_faces(("north", "south", "east", "west"), tint=True)}]
    for y0, y1, end in ((14, 16, "up"), (0, 2, "down")):
        # A square plate round an 8 by 8 opening: four strips, each showing its end and the wall of the opening
        for a, b, wall in (((0, y0, 0), (16, y1, 4), "south"), ((0, y0, 12), (16, y1, 16), "north"),
                           ((0, y0, 4), (4, y1, 12), "east"), ((12, y0, 4), (16, y1, 12), "west")):
            elements.append({"from": list(a), "to": list(b),
                             "faces": {end: {"texture": "#cap"}, wall: {"texture": "#dark"}}})
    # A dark floor behind each opening, so the shaft shows against it rather than through the casing
    elements.append({"from": [4, 14, 4], "to": [12, 14, 12], "faces": {"up": {"texture": "#dark"}}})
    elements.append({"from": [4, 2, 4], "to": [12, 2, 12], "faces": {"down": {"texture": "#dark"}}})
    write("engine_casing/encased", CASING_TEXTURES, elements)
    # A part clad in the riveted or louvred plate: the same cap and opening, with that plate on the four sides
    for kind in ("riveted", "louvred"):
        sprite = "create_engines:block/engine_%s_casing" % kind
        write("engine_%s_casing/encased" % kind, dict(CASING_TEXTURES, casing=sprite, particle=sprite), elements)
    # The same for a part behind a window: the four sides are the window plate, so the part inside shows through the glass
    window_elements = [dict(e) for e in elements]
    window_elements[0] = {"from": [0, 0, 0], "to": [16, 16, 16],
                          "faces": casing_faces(("north", "south", "east", "west"), texture="window", tint=True)}
    write("engine_casing/window", dict(CASING_TEXTURES, window="create_engines:block/engine_window_casing"),
          window_elements, render_type="minecraft:translucent")

    # Create's own encased shaft and cogwheel models, dressed in the engine casing. The block models are copies of
    # Create's with the casing faces tinted (tools/derive_cased_models.py); only the items are children of Create's.
    opening = {"casing": CASING_SPRITE, "opening": DARK, "particle": CASING_SPRITE}
    write_child("engine_cased_shaft/item", "create:block/encased_shaft/item", opening)

    # The shaft and cogwheels clad in the other three plates: copies of the plain cased models (derived from Create's, see
    # tools/derive_cased_models.py) with the plate swapped in for the casing faces. The window ones show the part through
    # their glass, so they draw in the translucent layer. The cog sides keep the plain steel ones.
    for kind in ("riveted", "louvred", "window"):
        sprite = "create_engines:block/engine_%s_casing" % kind
        for part, suffixes in (("shaft", ("",)), ("cogwheel", ("", "_top", "_bottom", "_top_bottom")),
                               ("large_cogwheel", ("", "_top", "_bottom", "_top_bottom"))):
            for suffix in suffixes:
                with open(os.path.join(OUT, "engine_cased_%s/block%s.json" % (part, suffix))) as fh:
                    model = json.load(fh)
                model["textures"]["casing"] = sprite
                model["textures"]["particle"] = sprite
                if kind == "window":
                    model["render_type"] = "minecraft:translucent"
                target = os.path.join(OUT, "engine_%s_cased_%s/block%s.json" % (kind, part, suffix))
                os.makedirs(os.path.dirname(target), exist_ok=True)
                with open(target, "w", newline="\n") as fh:
                    json.dump(model, fh, indent=2)
                    fh.write("\n")
                print("wrote", target)


def write_child(path, parent, textures):
    """A model that is Create's, with its textures swapped."""
    full = os.path.join(OUT, path + ".json")
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w", newline="\n") as fh:
        json.dump({"parent": parent, "textures": textures}, fh, indent=2)
        fh.write("\n")
    print("wrote", full)


def transmission():
    """A split-shaft gearbox: a casing round the axis, with the shaft halves drawn by the renderer in the openings.
    Stepped bands on the faces show the gear train and a small selector plate shows it is a gearbox."""
    textures = {"casing": CASING, "band": BRASS, "cover": IRON, "dial": DARK, "bolt": BRASS, "particle": CASING}
    elements = [
        box((2, 2, 2), (14, 14, 14), "casing"),
        box((1, 3, 1), (15, 5, 15), "cover"),
        box((1, 11, 1), (15, 13, 15), "cover"),
        box((4, 5, 1.6), (12, 11, 2), "dial"),
        box((4, 5, 14), (12, 11, 14.4), "dial"),
        box((1.6, 5, 4), (2, 11, 12), "dial"),
        box((14, 5, 4), (14.4, 11, 12), "dial"),
        box((6, 6.5, 14.4), (10, 9.5, 15), "band"),
    ]
    for x in (2, 13):
        for z in (2, 13):
            elements.append(box((x, 14, z), (x + 1, 14.6, z + 1), "bolt"))
    write("transmission/block", textures, elements)


def refinery():
    """The three parts of a refining column. Each fills its block to the edge, so a wide column is a solid tower."""
    global WRAP_UV
    WRAP_UV = True
    # Reboiler: a squat firebox with a riveted drum on it and a feed boss on the side
    write("reboiler/block", {"plate": RIVETED, "band": IRON_BLOCK, "foot": DARK, "boss": BRASS, "port": COPPER,
                             "particle": RIVETED},
          [box((0, 0, 0), (16, 4, 16), "foot"), box((0, 4, 0), (16, 13, 16), "plate"),
           box((0, 13, 0), (16, 16, 16), "band"), box((-0.2, 7, -0.2), (16.2, 8.5, 16.2), "band"),
           box((16, 4, 5), (18, 9, 11), "boss"), box((-2, 4, 5), (0, 9, 11), "boss"),
           box((6, 4, 16), (10, 8, 17.5), "port"), box((6, 4, -1.5), (10, 8, 0), "port")])
    write_item("reboiler/item", "reboiler/block")
    # Column section: banded plate with a sight window on every side, dark while idle and glowing while it works
    for name, glass in (("block", BLACK), ("active", "create_engines:block/column_glow")):
        elements = [box((0, 2, 0), (16, 14, 16), "plate"), box((0, 0, 0), (16, 2, 16), "band"),
                    box((0, 14, 0), (16, 16, 16), "band")]
        for lo, hi in (((4, 5, -0.4), (12, 11, 0)), ((4, 5, 16), (12, 11, 16.4)),
                       ((-0.4, 5, 4), (0, 11, 12)), ((16, 5, 4), (16.4, 11, 12))):
            elements.append(box(lo, hi, "glass"))
        for lo, hi in (((3, 4, -0.6), (13, 5, 0)), ((3, 11, -0.6), (13, 12, 0)),
                       ((3, 4, 16), (13, 5, 16.6)), ((3, 11, 16), (13, 12, 16.6)),
                       ((-0.6, 4, 3), (0, 5, 13)), ((-0.6, 11, 3), (0, 12, 13)),
                       ((16, 4, 3), (16.6, 5, 13)), ((16, 11, 3), (16.6, 12, 13))):
            elements.append(box(lo, hi, "band"))
        if name == "active":
            # The lit glass is pale and takes the colour of the fluid in it (tint index 0, set by the block's tint)
            for element in elements:
                for face in element["faces"].values():
                    if face["texture"] == "#glass":
                        face["tintindex"] = 0
        write("column_section/" + name, {"plate": RIVETED, "band": IRON_BLOCK, "glass": glass, "particle": RIVETED},
              elements)
    write_item("column_section/item", "column_section/block")
    # Condenser: finned cap with a draw-off spout
    elements = [box((0, 0, 0), (16, 3, 16), "band"), box((0, 3, 0), (16, 11, 16), "plate"),
                box((1, 11, 1), (15, 13, 15), "fin"), box((2, 13, 2), (14, 14.5, 14), "fin"),
                box((3, 14.5, 3), (13, 16, 13), "fin"),
                box((16, 5, 5), (18, 10, 11), "boss"), box((-2, 5, 5), (0, 10, 11), "boss"),
                box((6, 5, 16), (10, 9, 17.5), "port"), box((6, 5, -1.5), (10, 9, 0), "port")]
    write("condenser/block", {"plate": RIVETED, "band": IRON_BLOCK, "fin": COPPER, "boss": BRASS, "port": COPPER,
                              "particle": RIVETED}, elements)
    write_item("condenser/item", "condenser/block")
    column_arms()
    WRAP_UV = False


def column_arms():
    """The arms a column section grows where a layer leans, built for a lean to the south (the block state turns them).
    They are drawn into the empty block above (the wedge) or below (the brace) the section, so the model reaches outside
    its own block. Both are a triangle in the z-y plane under the line y = z: the brace, which hangs under a leaning
    section and meets the wall of the section below it, is above that line; the wedge, which stands on a section and meets
    the wall of the leaning one, below it. A stepped fill of 2-pixel slices sits just inside the slope and a 45-degree
    plate lies along it, so the slope reads as one slab."""
    textures = {"fill": RIVETED, "slab": IRON_BLOCK, "particle": RIVETED}

    def slab(y0):
        # Built lying along z and tipped up by 45 degrees, so that the box stays inside the range models may use
        piece = box((0, y0 + 8 - 1.5, 8 - 9.5), (16, y0 + 8 + 1.5, 8 + 9.5), "slab")
        piece["rotation"] = {"angle": -45, "axis": "x", "origin": [8, y0 + 8, 8]}
        return piece

    brace = [box((0, -16 + 2 * k + 2, 2 * k), (16, 0, 2 * k + 2), "fill") for k in range(8)] + [slab(-16)]
    wedge = [box((0, 16, 2 * k), (16, 16 + 2 * k, 2 * k + 2), "fill") for k in range(1, 8)] + [slab(16)]
    write("column_section/brace", textures, brace)
    write("column_section/wedge", textures, wedge)


def flare_stack():
    """A thin chimney on a flange, with a burner cup on top; the lit model adds the flame."""
    global WRAP_UV
    WRAP_UV = True
    textures = {"pipe": IRON_BLOCK, "flange": DARK, "cup": IRON, "flame": MAGMA, "particle": IRON_BLOCK}
    base = [box((3, 0, 3), (13, 2, 13), "flange"), box((6, 2, 6), (10, 13, 10), "pipe"),
            box((5, 13, 5), (11, 15, 11), "cup"), box((5.5, 7, 5.5), (10.5, 8, 10.5), "flange")]
    write("flare_stack/block", textures, base)
    write("flare_stack/lit", textures, base + [box((6.5, 15, 6.5), (9.5, 18, 9.5), "flame"),
                                               box((7.2, 18, 7.2), (8.8, 19.5, 8.8), "flame")])
    write_item("flare_stack/item", "flare_stack/lit")
    WRAP_UV = False


def sump():
    """An oil pan: a shallow sheet-metal tub under a bolted flange, with a drain plug, dipstick and filler."""
    textures = {"pan": DARK, "flange": IRON, "rib": IRON_BLOCK, "bolt": BRASS, "cap": COPPER, "particle": DARK}
    elements = [
        box((2, 0, 2), (14, 11, 14), "pan"),
        box((1, 3, 1), (15, 4, 15), "rib"),
        box((1, 7, 1), (15, 8, 15), "rib"),
        box((0, 11, 0), (16, 14, 16), "flange"),
        box((1, 14, 1), (15, 16, 15), "pan"),
        # Drain plug in the bottom edge, dipstick tube and handle, filler cap
        box((6, 0, 13.5), (10, 2.5, 14.5), "bolt"),
        box((12, 14, 12), (13.5, 16, 13.5), "cap"),
        box((11.5, 16, 11.5), (14, 17.5, 14), "cap"),
        box((3, 16, 3), (6, 17.2, 6), "bolt"),
    ]
    for x in (0.7, 14.3):
        for z in (0.7, 14.3):
            elements.append(box((x, 14, z), (x + 1, 14.6, z + 1), "bolt"))
    write("sump/block", textures, elements)
    write_item("sump/item", "sump/block")
    # The oil filter that fits in the pan: a steel canister with a ribbed band and a threaded boss
    write("sump/filter_item",
          {"can": IRON_BLOCK, "band": BRASS, "boss": IRON, "particle": IRON_BLOCK},
          [box((4, 0, 4), (12, 9, 12), "can"), box((3.5, 3, 3.5), (12.5, 5, 12.5), "band"),
           box((5, 9, 5), (11, 10, 11), "boss"), box((6.5, 10, 6.5), (9.5, 11.5, 9.5), "boss")])


# --- precision parts and the governor ------------------------------------------------------------------------------

def precision_cylinder():
    """A liner-bored cylinder in brass: the same four posts and rims, with a bright machined sleeve showing between."""
    textures = {"frame": BRASS_CASING, "rim": BRASS, "sleeve": IRON_BLOCK, "glass": GLASS, "particle": BRASS_CASING}
    elements = []
    for x in (0, 13):
        for z in (0, 13):
            elements.append(box((x, 0, z), (x + 3, 16, z + 3), "frame"))
    for y0, y1 in ((0, 2), (14, 16)):
        elements += [box((3, y0, 0), (13, y1, 3), "rim"), box((3, y0, 13), (13, y1, 16), "rim"),
                     box((0, y0, 3), (3, y1, 13), "rim"), box((13, y0, 3), (16, y1, 13), "rim")]
    # Honing rings round the sleeve, just outside the piston's travel
    for y in (5, 8, 11):
        elements.append(box((2.2, y, 2.2), (2.7, y + 0.6, 13.8), "sleeve"))
        elements.append(box((13.3, y, 2.2), (13.8, y + 0.6, 13.8), "sleeve"))
    elements += glass_panels()
    write("precision_cylinder/block", textures, elements, render_type="minecraft:cutout")
    write_item("precision_cylinder/item", "precision_cylinder/block")


PRECISION_HEAD_TEXTURES = {"side": BRASS_CASING, "top": BRASS, "cover": DARK, "injector": BRASS, "ceramic": QUARTZ,
                           "terminal": IRON, "bolt": IRON_BLOCK, "particle": BRASS_CASING}


def precision_head_base():
    elements = [
        box((0, 0, 0), (16, 14, 16), "side", up="top"),
        box((1, 14, 1), (15, 16, 15), "top"),
        # Two cam covers over the four valves, each held down by its bolts
        box((2.5, 16, 2.5), (6.5, 17.5, 13.5), "cover"),
        box((9.5, 16, 2.5), (13.5, 17.5, 13.5), "cover"),
    ]
    for x in (4.5, 11.5):
        for z in (3.5, 8, 12.5):
            elements.append(box((x - 0.5, 17.5, z - 0.5), (x + 0.5, 18.1, z + 0.5), "bolt"))
    return elements


def precision_injector_elements():
    """Sunk 0.2 into the cam cover and clear of its edges, so no face lies in the same plane as a cover or bolt face
    (the standard head's injector does, which z-fights on this head). It stands taller than the bolt it swallows."""
    return [box((10.5, 17.3, 2), (13, 18.6, 5), "injector"), box((11, 18.6, 2.5), (12.5, 19.2, 4.5), "terminal")]


def precision_plug_elements():
    """Same rule as the injector: a collar sunk into the cover with the bolt inside it, ceramic standing on that."""
    return [
        box((3, 17.3, 11), (6, 18.3, 14), "terminal"),
        box((3.5, 18, 11.5), (5.5, 20.5, 13.5), "ceramic"),
        box((4, 20.5, 12), (5, 22, 13), "terminal"),
    ]


def precision_head():
    base = precision_head_base()
    textures = PRECISION_HEAD_TEXTURES
    injector, plug = precision_injector_elements(), precision_plug_elements()
    write("precision_cylinder_head/block", textures, base)
    write("precision_cylinder_head/block_injector", textures, base + injector)
    write("precision_cylinder_head/block_plug", textures, base + plug)
    write("precision_cylinder_head/block_injector_plug", textures, base + injector + plug)
    # The assist stands above the injector's terminal, which it swallows, so that box is left out under it
    assist = assist_elements(18.4, 10.1, 13.4, 1.7, 5.3)
    assisted = dict(textures, assist=DARK, coil=BLUE)
    write("precision_cylinder_head/block_injector_assist", assisted, base + injector[:1] + assist)
    write("precision_cylinder_head/block_injector_assist_plug", assisted, base + injector[:1] + assist + plug)
    # The coil stands on the plug's ceramic and swallows its terminal and the one that tops the collar, so those are left out under it
    coil = coil_elements(3.5, 5.5, 11.5, 13.5, 20.5)
    coiled = dict(textures, assist=DARK, coil=BLUE)
    write("precision_cylinder_head/block_plug_coil", coiled, base + plug[:2] + coil)
    write("precision_cylinder_head/block_injector_plug_coil", coiled, base + injector + plug[:2] + coil)
    write("precision_cylinder_head/block_injector_assist_plug_coil", coiled,
          base + injector[:1] + assist + plug[:2] + coil)
    write_item("precision_cylinder_head/item", "precision_cylinder_head/block")


def forged_piston():
    textures = {"piston": DARK, "ring": BRASS, "pin": IRON_BLOCK, "particle": DARK}
    write("cylinder/forged_piston", textures,
          [box((3, -2, 3), (13, 6, 13), "piston"), box((2.9, 4, 2.9), (13.1, 4.6, 13.1), "ring"),
           box((2.9, 3, 2.9), (13.1, 3.4, 13.1), "ring"), box((6.5, -1, 2.6), (9.5, 2, 13.4), "pin")])


def precision_injector_item():
    write("cylinder_head/precision_injector_item",
          {"body": BRASS, "cap": DARK, "nozzle": IRON_BLOCK, "rail": QUARTZ, "particle": BRASS},
          [box((6, 0, 6), (10, 9, 10), "body"), box((5, 9, 5), (11, 12, 11), "cap"),
           box((7, -3, 7), (9, 0, 9), "nozzle"), box((4, 10, 7), (6, 11.5, 9), "rail"),
           box((6.5, -3.6, 6.5), (9.5, -3, 9.5), "nozzle")])


def governor():
    """A flyball governor in a brass casing: the weights on their arms stand out above the lid, spindle between."""
    textures = {"plate": IRON, "body": BRASS_CASING, "lid": BRASS, "spindle": IRON_BLOCK, "ball": RED, "arm": IRON,
                "vent": IRON_BLOCK, "bolt": BRASS, "particle": BRASS_CASING}
    elements = [
        box((1, 0, 1), (15, 2.5, 15), "plate"),
        box((2, 2.5, 2), (14, 9, 14), "body"),
        box((3, 9, 3), (13, 10, 13), "lid"),
        # The spindle with a collar at the top, and a weight on a pair of arms each side
        box((7.2, 10, 7.2), (8.8, 15, 8.8), "spindle"),
        box((6.4, 14.2, 6.4), (9.6, 15.6, 9.6), "lid"),
        box((3.2, 11.6, 7.4), (7.2, 12.2, 8.6), "arm"),
        box((8.8, 11.6, 7.4), (12.8, 12.2, 8.6), "arm"),
        box((1.9, 10.4, 6.9), (4.1, 12.6, 9.1), "ball"),
        box((11.9, 10.4, 6.9), (14.1, 12.6, 9.1), "ball"),
        # A vent plate on each side
        box((4, 4, 1.6), (12, 7.5, 2), "vent"),
        box((4, 4, 14), (12, 7.5, 14.4), "vent"),
        box((1.6, 4, 4), (2, 7.5, 12), "vent"),
        box((14, 4, 4), (14.4, 7.5, 12), "vent"),
    ]
    for x in (1.6, 13.4):
        for z in (1.6, 13.4):
            elements.append(box((x, 2.5, z), (x + 1, 3.1, z + 1), "bolt"))
    write("governor/block", textures, elements)
    write_item("governor/item", "governor/block")


def timing_tool():
    """A timing light: a pistol grip with a lamp head and a dial, held like a tool."""
    textures = {"grip": DARK, "body": BRASS, "lens": QUARTZ, "dial": IRON_BLOCK, "needle": RED, "cable": BLACK,
                "particle": BRASS}
    elements = [
        box((6, 0, 6), (9, 7, 10), "grip"),
        box((5, 7, 5), (10, 10, 11), "body"),
        box((4.5, 8, 3), (10.5, 12, 5), "body"),
        box((5, 8.5, 2.2), (10, 11.5, 3), "lens"),
        box((6, 10, 5.2), (9, 10.6, 8), "dial"),
        box((7.3, 10.6, 5.5), (7.7, 10.9, 7.3), "needle"),
        box((7, 4, 10), (8, 5, 12.5), "cable"),
    ]
    write("timing_tool/item", textures, elements)



def bank_builder():
    """A layout tray: a flat frame with three cylinder-shaped slots and a handle."""
    textures = {"frame": DARK, "slot": IRON_BLOCK, "bore": BLACK, "grip": BRASS, "mark": RED, "particle": DARK}
    elements = [
        box((1, 6, 4), (15, 8, 12), "frame"),
        box((6, 3, 7), (10, 6, 9), "grip"),
        box((6, 1, 6.5), (10, 3, 9.5), "grip"),
    ]
    # Three bores seen from above, and a mark by the one that is clicked
    for x in (2.5, 6.5, 10.5):
        elements.append(box((x, 8, 5.5), (x + 3, 8.4, 8.5), "slot"))
        elements.append(box((x + 0.6, 8.4, 6.1), (x + 2.4, 8.5, 7.9), "bore"))
    elements.append(box((2.5, 8, 9.5), (5.5, 8.4, 10.5), "mark"))
    write("bank_builder/item", textures, elements)


def headphones():
    """Noise-cancelling headphones: a steel band over the crown and a padded cup over each ear, drawn to be worn. The head
    slot draws an item at ten pixels of head to sixteen of model, so the head is the 1.6..14.4 box in x and z and tops out at
    14.4 in y, and the ears are at the middle. A brass plate and a red lamp on the left cup are what mark them as the
    noise-cancelling ones."""
    textures = {"band": IRON_BLOCK, "cup": DARK, "pad": "minecraft:block/gray_wool", "plate": BRASS, "lamp": RED,
                "particle": DARK}
    elements = [
        # The band: over the crown, with a riser down each side to the cups and a pad where it rests on the head
        box((0.2, 14.5, 7), (15.8, 15.9, 9), "band"),
        box((0.2, 10.9, 7), (1.7, 14.5, 9), "band"),
        box((14.3, 10.9, 7), (15.8, 14.5, 9), "band"),
        box((4, 14.2, 6.6), (12, 14.5, 9.4), "pad"),
    ]
    for side in (0, 1):
        def x(lo, hi):
            return (lo, hi) if side == 0 else (16 - hi, 16 - lo)

        # The cup, a padded ring against the ear and a plate on the outside; the cup is a little taller than the ear
        c0, c1 = x(-0.6, 1.6)
        elements.append(box((c0, 4.8, 4.6), (c1, 11.2, 11.4), "cup"))
        p0, p1 = x(1.6, 2.6)
        elements.append(box((p0, 5.4, 5.2), (p1, 10.6, 10.8), "pad"))
        q0, q1 = x(-0.9, -0.6)
        elements.append(box((q0, 6, 6), (q1, 10, 10), "plate"))
    # The lamp that says the noise is being cancelled, low on the outside of the left cup
    elements.append(box((-0.8, 5.2, 9.2), (-0.6, 6, 10.4), "lamp"))
    write("headphones/item", textures, elements,
          display={"gui": {"rotation": [20, 205, 0], "translation": [0, -1.5, 0], "scale": [0.8, 0.8, 0.8]},
                   "head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
                   "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]},
                   "fixed": {"rotation": [0, 180, 0], "translation": [0, -2, 0], "scale": [0.9, 0.9, 0.9]},
                   "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
                   "firstperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 4, 0], "scale": [0.6, 0.6, 0.6]}})


# --- rotor housing -------------------------------------------------------------------------------------------------

ROTOR_RADIUS = 4.6
ROTOR_ECCENTRICITY = 1.5


def rotor_rows(radius=ROTOR_RADIUS, step=0.5):
    """An equilateral triangle with its apex towards -Z, centred on (8, 8), as non-overlapping rows (z0, z1, half
    width) half a pixel deep. Its flanks bulge a little, as a Wankel rotor's do."""
    top = 8 - radius
    rows = []
    z = top
    while z < 8 + radius / 2 - 1e-6:
        depth = z + step / 2 - top
        half = depth * math.tan(math.radians(30)) + min(depth, 3.0) * 0.12 + 0.3
        half = min(half, radius * math.sin(math.radians(60)) + 0.3)
        half = round(half / 0.25) * 0.25
        rows.append((z, min(z + step, 8 + radius / 2), half))
        z += step
    return rows


def rotor_elements(y0=5.0, y1=11.0):
    """The rotor: a slab, an apex seal at each corner and a hub. The hub and seals stand a little proud of the slab on
    both faces, and nothing else shares a plane with it, so nothing flickers as it turns."""
    elements = []
    for z0, z1, half in rotor_rows():
        elements.append(box((8 - half, y0, z0), (8 + half, y1, z1), "rotor", up="face", down="face"))
    for angle in (0, 120, 240):
        a = math.radians(angle)
        x = 8 + (ROTOR_RADIUS - 0.2) * math.sin(a)
        z = 8 - (ROTOR_RADIUS - 0.2) * math.cos(a)
        elements.append(box((x - 0.6, y0 - 0.15, z - 0.6), (x + 0.6, y1 + 0.15, z + 0.6), "seal"))
    elements.append(box((6, y0 - 0.1, 6), (10, y1 + 0.1, 10), "hub", up="face", down="face"))
    return elements


ROTOR_TEXTURES = {"rotor": IRON_BLOCK, "face": CUT_COPPER, "seal": BRASS, "hub": DARK, "particle": IRON_BLOCK}


def rotor_housing():
    textures = {"frame": IRON, "rim": COPPER_CASING, "boss": CASING, "injector": BRASS, "ceramic": QUARTZ,
                "terminal": IRON_BLOCK, "particle": COPPER_CASING}
    base = []
    # An open ring at each end of the shaft, held to the shaft's bearing by four spokes, and four posts holding the rings
    # apart: the rotor shows through every side and down the shaft. The rotor's tips never reach the posts.
    for y0, y1 in ((0, 2), (14, 16)):
        base += [box((0, y0, 0), (16, y1, 2), "rim"), box((0, y0, 14), (16, y1, 16), "rim"),
                 box((0, y0, 2), (2, y1, 14), "rim"), box((14, y0, 2), (16, y1, 14), "rim"),
                 box((5, y0, 5), (6.5, y1, 11), "boss"), box((9.5, y0, 5), (11, y1, 11), "boss"),
                 box((6.5, y0, 5), (9.5, y1, 6.5), "boss"), box((6.5, y0, 9.5), (9.5, y1, 11), "boss"),
                 box((7, y0, 2), (9, y1, 5), "boss"), box((7, y0, 11), (9, y1, 14), "boss"),
                 box((2, y0, 7), (5, y1, 9), "boss"), box((11, y0, 7), (14, y1, 9), "boss")]
    for x in (0, 13.5):
        for z in (0, 13.5):
            base.append(box((x, 2, z), (x + 2.5, 14, z + 2.5), "frame"))
    injector = [box((11, 2, 16), (14, 5, 17.5), "injector")]
    plug = [box((2.5, 2.5, 16), (5.5, 5.5, 19), "ceramic"), box((3.5, 3.5, 19), (4.5, 4.5, 20.5), "terminal"),
            box((2, 2, 16), (6, 6, 17), "terminal")]
    write("rotor_housing/block", textures, base)
    write("rotor_housing/block_injector", textures, base + injector)
    write("rotor_housing/block_plug", textures, base + plug)
    write("rotor_housing/block_injector_plug", textures, base + injector + plug)
    write_item("rotor_housing/item", "rotor_housing/block")

    # What turns inside: the shaft, a web at each end of the rotor, and the eccentric journal between them (offset
    # towards +Z). The shaft stops at the webs rather than passing through the rotor, and the journal is inside the
    # rotor's own thickness, so the rotor can orbit round it with nothing poking out of its faces.
    e = ROTOR_ECCENTRICITY
    write("rotor_housing/eccentric",
          {"axis": AXIS, "end": AXIS_TOP, "journal": IRON, "web": DARK, "particle": IRON},
          [shaft((6.5, 0, 6.5), (9.5, 4, 9.5)), shaft((6.5, 12, 6.5), (9.5, 16, 9.5)),
           box((5.5, 4, 5.5), (10.5, 4.9, 10.5), "web"), box((5.5, 11.1, 5.5), (10.5, 12, 10.5), "web"),
           box((6, 5.05, 8 + e - 2), (10, 10.95, 8 + e + 2), "journal")])
    write("rotor_housing/rotor", ROTOR_TEXTURES, rotor_elements())
    write("rotor_housing/rotor_item", ROTOR_TEXTURES, rotor_elements(6.0, 10.0))


def pushrod():
    """A pushrod and the follower foot at its bottom, in the frame of the cylinder block it runs beside: +Y is the bore,
    +Z the side the cam is on, the rod runs just off that face from the height of the lobes (the middle of the block) up
    to the rocker on the head. Centred on the middle of the block across the cam, so the renderer slides it to its lobe."""
    write("cylinder/pushrod", {"rod": IRON_BLOCK, "foot": IRON, "particle": IRON_BLOCK},
          [box((7.25, 8, 16.25), (8.75, 19.25, 18), "rod"), box((6.75, 7, 16.25), (9.25, 9, 18), "foot")])


def rocker():
    """A rocker arm, in the frame of the head it sits on: +Y is the bore, +Z the side its cam is on. It pivots about a pin
    inside the head (at y 4, z 14) and the arm comes out of the head's face, so the pushrod under its end tips it."""
    write("cylinder_head/rocker", {"arm": BRASS, "cap": IRON_BLOCK, "particle": BRASS},
          [box((6.75, 3.25, 14), (9.25, 4.75, 20.5), "arm"), box((6.25, 3, 18.5), (9.75, 5, 20.5), "cap")])


def main():
    rotor_housing()
    precision_cylinder()
    precision_head()
    forged_piston()
    precision_injector_item()
    governor()
    timing_tool()
    bank_builder()
    headphones()
    crankshaft()
    crank_throw()
    camshaft()
    cam_lobe()
    cylinder()
    piston()
    rod()
    head()
    valve()
    pushrod()
    rocker()
    air_intake()
    exhaust_outlet()
    rain_cap()
    maker_plate()
    framed_blueprint()
    pipes()
    starter()
    battery()
    alternator()
    turbocharger()
    variable_turbocharger()
    supercharger()
    variable_supercharger()
    cast_flywheel()
    dual_mass_flywheel()
    dyno()
    black_box()
    intercooler()
    transmission()
    muffler()
    engine_casing()
    sump()
    refinery()
    flare_stack()
    soot_trap()
    soot()
    jerry_can()


main()
