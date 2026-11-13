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


def box(a, b, tex, **overrides):
    """One element from corner a to corner b. tex is the texture for every face; overrides retexture single faces."""
    faces = {}
    for face in FACES:
        faces[face] = {"texture": "#" + overrides.get(face, tex)}
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
RIVETED = "create_engines:block/rivet_plate"
MAGMA = "create_engines:block/heat_kindled"
LAVA = "create_engines:block/heat_seething"


# --- crank, throw, cam ---------------------------------------------------------------------------------------------

def crankshaft():
    textures = {"axis": AXIS, "end": AXIS_TOP, "collar": CASING, "journal": IRON, "bolt": BRASS, "particle": CASING}
    elements = [
        shaft((6, 0, 6), (10, 16, 10)),
        box((5, 3, 5), (11, 13, 11), "journal"),
        box((4, 5.5, 4), (12, 10.5, 12), "collar"),
        box((3.75, 7.5, 3.75), (12.25, 8.5, 12.25), "journal"),
    ]
    # Tie bolts at the corners of the bearing cap, with a nut at each end
    for x in (3.6, 11.4):
        for z in (3.6, 11.4):
            elements.append(box((x, 5, z), (x + 1, 11, z + 1), "bolt"))
    write("crankshaft/block", textures, elements)


def crank_throw():
    textures = {"axis": AXIS, "end": AXIS_TOP, "web": IRON, "pin": IRON_BLOCK, "weight": DARK, "collar": CASING,
                "particle": IRON}
    elements = [shaft((6, 0, 6), (10, 3, 10)), shaft((6, 13, 6), (10, 16, 10)),
                box((5, 2, 5), (11, 3, 11), "collar"), box((5, 13, 5), (11, 14, 11), "collar")]
    for y0 in (3, 11):
        y1 = y0 + 2
        # A web: a hub round the shaft, running out to a boss round the pin
        elements.append(box((4.5, y0, 4), (11.5, y1, 15), "web"))
        elements.append(box((4, y0, 5), (12, y1, 11), "web"))
        # The counterweight on the far side from the pin, which balances the pin and the big end of the rod
        elements.append(box((4, y0, 1), (12, y1, 5.5), "weight"))
    elements.append(box((6.5, 5, 10.5), (9.5, 11, 13.5), "pin"))
    elements.append(box((6.2, 7.5, 10.2), (9.8, 8.5, 13.8), "pin"))
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


# --- cylinder, piston, rod -----------------------------------------------------------------------------------------

def cylinder():
    """Four corner posts and a rim at each end, so the piston shows through the open sides."""
    textures = {"frame": CASING, "rim": IRON, "particle": CASING}
    elements = []
    for x in (0, 13):
        for z in (0, 13):
            elements.append(box((x, 0, z), (x + 3, 16, z + 3), "frame"))
    for y0, y1 in ((0, 2), (14, 16)):
        elements += [box((3, y0, 0), (13, y1, 3), "rim"), box((3, y0, 13), (13, y1, 16), "rim"),
                     box((0, y0, 3), (3, y1, 13), "rim"), box((13, y0, 3), (16, y1, 13), "rim")]
    write("cylinder/block", textures, elements)


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


def head():
    base = head_base()
    write("cylinder_head/block", HEAD_TEXTURES, base)
    write("cylinder_head/block_injector", HEAD_TEXTURES, base + injector_elements())
    write("cylinder_head/block_plug", HEAD_TEXTURES, base + plug_elements())
    write("cylinder_head/block_injector_plug", HEAD_TEXTURES, base + injector_elements() + plug_elements())

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
    textures = {"body": COPPER_CASING, "rim": IRON, "bolt": BRASS, "grille": "create_engines:block/grille",
                "particle": COPPER_CASING}
    elements = [
        box((3, 0, 3), (13, 1.5, 13), "rim"),
        box((5, 1.5, 5), (11, 6, 11), "body"),
        box((4, 6, 4), (12, 8, 12), "body"),
        box((3, 8, 3), (13, 10, 13), "body"),
        box((2, 10, 2), (14, 12, 14), "body"),
        box((1.5, 12, 1.5), (14.5, 13, 3), "rim"),
        box((1.5, 12, 13), (14.5, 13, 14.5), "rim"),
        box((1.5, 12, 3), (3, 13, 13), "rim"),
        box((13, 12, 3), (14.5, 13, 13), "rim"),
        box((3, 12.2, 3), (13, 12.35, 13), "grille"),
    ]
    for x in (3.5, 11.5):
        for z in (3.5, 11.5):
            elements.append(box((x, 1.5, z), (x + 1, 2.2, z + 1), "bolt"))
    write("air_intake/block", textures, elements, render_type="minecraft:cutout")


def exhaust_outlet():
    textures = {"flange": IRON, "pipe": DARK, "tip": IRON_BLOCK, "inside": BLACK, "bolt": BRASS,
                "particle": DARK}
    elements = [
        box((3, 0, 3), (13, 2, 13), "flange"),
        box((5.5, 2, 5.5), (10.5, 13, 10.5), "pipe"),
        box((6.5, 6.5, 6.5), (9.5, 7.2, 9.5), "tip"),
        box((4.5, 13, 4.5), (11.5, 15, 11.5), "pipe"),
        box((3.5, 15, 3.5), (12.5, 16, 5), "tip"),
        box((3.5, 15, 11), (12.5, 16, 12.5), "tip"),
        box((3.5, 15, 5), (5, 16, 11), "tip"),
        box((11, 15, 5), (12.5, 16, 11), "tip"),
        box((5, 14.9, 5), (11, 15.1, 11), "inside"),
    ]
    for x in (3.4, 11.6):
        for z in (3.4, 11.6):
            elements.append(box((x, 2, z), (x + 1, 2.6, z + 1), "bolt"))
    write("exhaust_outlet/block", textures, elements)


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


def battery():
    textures = {"body": ZINC, "band": BRASS, "cap": CASING, "pole": IRON, "lid": DARK, "vent": IRON_BLOCK,
                "particle": ZINC}
    elements = [
        box((2, 0, 2), (14, 12, 14), "body"),
        box((1.5, 5, 1.5), (14.5, 8, 14.5), "band"),
        box((1.5, 12, 1.5), (14.5, 13.5, 14.5), "lid"),
        box((3, 13.5, 3), (13, 14, 13), "cap"),
        # Positive terminal (brass) and negative (iron), each a tapered post
        box((4, 14, 6), (6.5, 15, 8.5), "band"),
        box((4.75, 15, 6.75), (5.75, 16, 7.75), "band"),
        box((9.5, 14, 6), (12, 15, 8.5), "pole"),
        box((10.25, 15, 6.75), (11.25, 16, 7.75), "pole"),
    ]
    # Filler caps over the six cells
    for x in (3.5, 6.75, 10):
        for z in (3.25, 10.75):
            elements.append(box((x, 14, z), (x + 2.5, 14.6, z + 2), "vent"))
    # Hold-down straps
    elements += [box((1.7, 0, 5), (2, 5, 6.5), "lid"), box((1.7, 0, 9.5), (2, 5, 11), "lid"),
                 box((14, 0, 5), (14.3, 5, 6.5), "lid"), box((14, 0, 9.5), (14.3, 5, 11), "lid")]
    write("battery/block", textures, elements)


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

    wheel_textures = {"blade": BRASS, "hot": IRON_BLOCK, "shaft": IRON, "particle": BRASS}
    wheel = [
        box((7.25, 6, 7.25), (8.75, 10, 8.75), "shaft"),
        # Compressor wheel: a hub with a cross of vanes
        box((6.5, 10, 6.5), (9.5, 15.2, 9.5), "blade"),
        box((5.4, 10.4, 7.5), (10.6, 14.8, 8.5), "blade"),
        box((7.5, 10.4, 5.4), (8.5, 14.8, 10.6), "blade"),
        # Turbine wheel below it, darker for the heat
        box((6.7, 0.8, 6.7), (9.3, 6, 9.3), "hot"),
        box((5.8, 1.2, 7.6), (10.2, 5.6, 8.4), "hot"),
        box((7.6, 1.2, 5.8), (8.4, 5.6, 10.2), "hot"),
    ]
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

    wheel_textures = {"blade": BRASS, "hot": IRON_BLOCK, "shaft": IRON, "particle": BRASS}
    wheel = [
        box((7.25, 6, 7.25), (8.75, 10, 8.75), "shaft"),
        box((6.5, 10, 6.5), (9.5, 15.2, 9.5), "blade"),
        box((5.4, 10.4, 7.5), (10.6, 14.8, 8.5), "blade"),
        box((7.5, 10.4, 5.4), (8.5, 14.8, 10.6), "blade"),
        box((6.7, 0.8, 6.7), (9.3, 6, 9.3), "hot"),
        box((5.8, 1.2, 7.6), (10.2, 5.6, 8.4), "hot"),
        box((7.6, 1.2, 5.8), (8.4, 5.6, 10.2), "hot"),
    ]
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


def cast_flywheel():
    """A heavy iron disc on the shaft, along the Y axis (the block state turns it). The block model is only the bearing
    collars either side; the disc is a separate model so it can spin with the shaft."""
    textures = {"collar": CASING, "bolt": BRASS, "particle": IRON}
    collars = [box((5, 0, 5), (11, 2, 11), "collar"), box((5, 14, 5), (11, 16, 11), "collar")]
    write("cast_flywheel/block", textures, collars)

    disc_textures = {"axis": AXIS, "end": AXIS_TOP, "iron": RIVETED, "rim": IRON_BLOCK, "web": DARK, "bolt": BRASS,
                     "particle": IRON}
    disc = [
        shaft((6, 0, 6), (10, 16, 10)),
        # An octagonal body: two overlapping squares and a square turned square-on
        box((1, 4, 4), (15, 12, 12), "iron"),
        box((4, 4, 1), (12, 12, 15), "iron"),
        box((2, 4, 2), (14, 12, 14), "iron"),
        # A thicker rim all the way round, and a hub
        box((0.5, 5, 5), (15.5, 11, 11), "rim"),
        box((5, 5, 0.5), (11, 11, 15.5), "rim"),
        box((1.5, 5, 1.5), (14.5, 11, 14.5), "rim"),
        box((4.5, 3, 4.5), (11.5, 13, 11.5), "web"),
    ]
    # Lightening holes: darker squares let into each face, between the hub and the rim
    for y in (11.95, 3.95):
        for x, z in ((3, 3), (10, 3), (3, 10), (10, 10)):
            disc.append(box((x, y, z), (x + 3, y + 0.1, z + 3), "web"))
    # Bolts round the hub
    for x, z in ((5, 5), (10, 5), (5, 10), (10, 10)):
        disc.append(box((x, 12.9, z), (x + 1, 13.4, z + 1), "bolt"))
    write("cast_flywheel/disc", disc_textures, disc)
    write("cast_flywheel/item", {**textures, **disc_textures}, collars + disc)


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


def engine_casing():
    """A cube of riveted plate, steel on top and bottom: plain cladding to build a cowl round an engine."""
    textures = {"plate": RIVETED, "cap": IRON_BLOCK, "particle": RIVETED}
    write("engine_casing/block", textures, [box((0, 0, 0), (16, 16, 16), "plate", up="cap", down="cap")])
    write_item("engine_casing/item", "engine_casing/block")


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
    textures = {"frame": BRASS_CASING, "rim": BRASS, "sleeve": IRON_BLOCK, "particle": BRASS_CASING}
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
    write("precision_cylinder/block", textures, elements)
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


def precision_head():
    base = precision_head_base()
    textures = PRECISION_HEAD_TEXTURES
    write("precision_cylinder_head/block", textures, base)
    write("precision_cylinder_head/block_injector", textures, base + injector_elements())
    write("precision_cylinder_head/block_plug", textures, base + plug_elements())
    write("precision_cylinder_head/block_injector_plug", textures, base + injector_elements() + plug_elements())
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


def main():
    rotor_housing()
    precision_cylinder()
    precision_head()
    forged_piston()
    precision_injector_item()
    governor()
    timing_tool()
    bank_builder()
    crankshaft()
    crank_throw()
    camshaft()
    cam_lobe()
    cylinder()
    piston()
    rod()
    head()
    valve()
    air_intake()
    exhaust_outlet()
    pipes()
    starter()
    battery()
    alternator()
    turbocharger()
    variable_turbocharger()
    supercharger()
    cast_flywheel()
    dyno()
    black_box()
    intercooler()
    muffler()
    engine_casing()
    sump()


main()
