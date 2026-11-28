"""Intake, outlet, rain cap, Maker's Plate, framed blueprint and pipes."""

from models.core import (
    BLACK, BRASS, BRASS_CASING, COPPER, COPPER_CASING, DARK, INTAKE_BELL, INTAKE_FLANGE, INTAKE_PIPE,
    IRON, IRON_BLOCK, LAVA, MAGMA, RED, SMOOTH, box, write)


# --- airflow -------------------------------------------------------------------------------------------------------

def air_intake():
    """A back flange, a neck, and a bell in two broad lifts to a cast mouth ring, with a louvred grille over a dark well.
    The back flange covers the pipe joint and the mouth ring keeps its 13 by 13 outline."""
    textures = {"body": INTAKE_BELL, "rim": IRON, "grille": "create_engines:block/grille", "well": BLACK,
                "particle": COPPER}
    elements = [
        box((3, 0, 3), (13, 2, 13), "rim"),
        box((4, 2, 4), (12, 8, 12), "body"),
        box((3, 8, 3), (13, 10, 13), "body"),
        box((2.25, 10, 2.25), (13.75, 12, 13.75), "body"),
        # the dark well, the mouth ring round it and the grille across it
        box((3, 12, 3), (13, 12.1, 13), "well"),
        box((1.5, 12, 1.5), (14.5, 14, 3), "rim"),
        box((1.5, 12, 13), (14.5, 14, 14.5), "rim"),
        box((1.5, 12, 3), (3, 14, 13), "rim"),
        box((13, 12, 3), (14.5, 14, 13), "rim"),
        box((3, 12.7, 3), (13, 12.85, 13), "grille"),
    ]
    write("air_intake/block", textures, elements, render_type="minecraft:cutout")


def exhaust_outlet():
    """A back flange, a pipe with one broad turn of heat wrap, and a flared tailpipe with a rolled steel lip. The lip is
    9 by 9 pixels at y 15 to 16, where the rain cap sits, and must stay so."""
    textures = {"flange": IRON, "pipe": DARK, "tip": IRON_BLOCK, "wrap": SMOOTH, "inside": BLACK, "particle": DARK}
    elements = [
        box((3, 0, 3), (13, 1.5, 13), "flange"),
        box((5.5, 1.5, 5.5), (10.5, 12, 10.5), "pipe"),
        box((5, 3, 5), (11, 8, 11), "wrap"),
        box((4, 12, 4), (12, 15, 12), "pipe"),
        # the rolled lip, with the dark bore showing inside it
        box((3.5, 15, 3.5), (12.5, 16, 5), "tip"),
        box((3.5, 15, 11), (12.5, 16, 12.5), "tip"),
        box((3.5, 15, 5), (5, 16, 11), "tip"),
        box((11, 15, 5), (12.5, 16, 11), "tip"),
        box((5, 14.9, 5), (11, 15.1, 11), "inside"),
    ]
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
    """The elements of one pipe arm in the north frame: the tube and one thick flange, wrapped behind it on exhaust."""
    elements = [
        box((5, 5, 2), (11, 11, 5), "pipe"),
        box((3.5, 3.5, 0), (12.5, 12.5, 2), "flange"),
    ]
    if style == "exhaust":
        elements.append(box((4.5, 4.5, 2.25), (11.5, 11.5, 4.5), "wrap"))
    return elements


def core(style):
    """The junction at the middle of a pipe: one block that every arm runs into, with what a fitting carries on top."""
    if style == "throttle":
        return [box((4, 4, 4), (12, 12, 12), "body"),
                # the spindle, the pedal lever and the position sensor
                box((7, 12, 7), (9, 14, 9), "bolt"),
                box((6, 14, 7), (11, 15, 9), "bolt"),
                box((11, 12.5, 7), (12, 15, 9), "grip"),
                box((8.5, 12, 9.5), (11.5, 13.5, 11.5), "dark")]
    if style == "maf":
        return [box((4, 4, 4), (12, 12, 12), "body"),
                # the electronics housing and its plug
                box((5, 12, 5), (11, 14, 11), "dark"),
                box((6, 14, 8), (10, 16, 12.5), "plug")]
    if style == "heat":
        return [box((4, 4, 4), (12, 12, 12), "body"),
                # two broad fins and the hot plate a basin sits on
                box((2.5, 5, 2.5), (13.5, 6.5, 13.5), "fin"),
                box((2.5, 8.5, 2.5), (13.5, 10, 13.5), "fin"),
                box((2, 12, 2), (14, 15, 14), "plate"),
                box((3.5, 15, 3.5), (12.5, 15.5, 12.5), "body")]
    return [box((4.5, 4.5, 4.5), (11.5, 11.5, 11.5), "pipe")]


def heat_glow():
    """Hot metal over the fins and round the hot plate, drawn over the exchanger when it is running hot."""
    return [box((2.4, 5.1, 2.4), (13.6, 6.4, 13.6), "glow"),
            box((2.4, 8.6, 2.4), (13.6, 9.9, 13.6), "glow"),
            box((1.9, 12.5, 1.9), (14.1, 14.5, 14.1), "glow"),
            box((3.6, 15.5, 3.6), (12.4, 15.6, 12.4), "glow")]


# The two axes of the block each face shows across and down its texture
FACE_AXES = {"north": (0, 1), "south": (0, 1), "east": (2, 1), "west": (2, 1), "up": (0, 2), "down": (0, 2)}
# The styles whose arms are the intake's cast copper
INTAKE_STYLES = ("intake", "throttle", "maf")


def skinned(element, axis):
    """Gives an intake pipe's box the hand drawn patch for what it is, laid so that it runs with the pipe, which lies
    along axis (0 x, 1 y, 2 z; None for a junction): see intake_flange() and intake_pipe() in generate_textures.py. A
    flange keeps its own texture only where it is a thin plate; the collar of a tee takes the pipe's."""
    size = [element["to"][i] - element["from"][i] for i in range(3)]
    kind = element["faces"]["up"]["texture"][1:]
    plate = kind == "flange" and axis is not None and size[axis] <= 2
    for name, face in element["faces"].items():
        across, down = FACE_AXES[name]
        end_on = axis is not None and axis not in (across, down)
        if plate:
            patch = [0, 0, 9, 9] if end_on else [0, 9, 9, 11]
        elif kind == "flange":
            patch = [8, 8, 16, 16] if end_on else [8, 0, 12, 8]
        elif axis is None or end_on:
            patch = [0, 6, 7, 13]
        else:
            patch = [0, 0, min(7, size[axis]), 6]
        face["uv"] = patch
        # A flange edge patch runs across the pipe, the tube patch along it
        if not end_on and axis is not None and axis == (across if plate else down):
            face["rotation"] = 90
        if kind == "flange" and not plate:
            face["texture"] = "#pipe"
    return element


PIPE_STYLES = {
    "intake_manifold": ("intake", {"pipe": INTAKE_PIPE, "bead": COPPER, "flange": INTAKE_FLANGE, "gasket": DARK,
                                   "bolt": BRASS, "particle": COPPER}),
    "exhaust_manifold": ("exhaust", {"pipe": DARK, "bead": IRON, "flange": IRON, "gasket": DARK, "wrap": SMOOTH,
                                     "bolt": BRASS, "particle": DARK}),
    "throttle": ("throttle", {"pipe": INTAKE_PIPE, "bead": COPPER, "flange": INTAKE_FLANGE, "gasket": DARK,
                              "body": IRON_BLOCK,
                              "dark": DARK, "bolt": BRASS, "grip": RED,
                              "particle": IRON_BLOCK}),
    "maf_sensor": ("maf", {"pipe": INTAKE_PIPE, "bead": COPPER, "flange": INTAKE_FLANGE, "gasket": DARK, "body": BRASS,
                           "dark": DARK, "plug": RED, "bolt": BRASS, "particle": BRASS}),
    "heat_exchanger": ("heat", {"pipe": DARK, "bead": IRON, "flange": IRON, "gasket": DARK, "wrap": SMOOTH,
                                 "body": IRON_BLOCK, "fin": COPPER, "plate": DARK, "bolt": BRASS,
                                 "particle": DARK}),
}


def pipes():
    for name, (style, textures) in PIPE_STYLES.items():
        arm_style = "exhaust" if style in ("exhaust", "heat") else "intake"
        core_elements = core(style)
        if style == "intake":
            core_elements = [skinned(e, None) for e in core_elements]
        write(name + "/core", textures, core_elements)
        if style == "heat":
            for level, glow in (("kindled", MAGMA), ("seething", LAVA)):
                elements = [dict(e, neoforge_data={"block_light": 15, "sky_light": 15}) for e in heat_glow()]
                write(name + "/glow_" + level, {"glow": glow, "particle": glow}, elements)
        if name.endswith("_manifold"):
            # Fittings for the shapes a run takes, drawn along z (the north-south axis) and turned for the others
            elbow = box((4.5, 4.5, 4.5), (11.5, 11.5, 11.5), "pipe")
            write(name + "/core_elbow", textures, [skinned(elbow, None) if style == "intake" else elbow])
            for axis, direction in (("x", "east"), ("y", "up"), ("z", "north")):
                straight = [box((5, 5, 4.5), (11, 11, 11.5), "pipe")]
                tee = [box((5, 5, 4.5), (11, 11, 11.5), "pipe"), box((4, 4, 6), (12, 12, 10), "flange")]
                for shape, pieces in (("straight", straight), ("tee", tee)):
                    pieces = [oriented(e, direction) for e in pieces]
                    if style == "intake":
                        pieces = [skinned(e, "xyz".index(axis)) for e in pieces]
                    write(name + "/core_" + shape + "_" + axis, textures, pieces)
        arms = {}
        for direction in ("north", "south", "east", "west", "up", "down"):
            arms[direction] = [oriented(e, direction) for e in arm(arm_style)]
            if style in INTAKE_STYLES:
                along = {"east": 0, "west": 0, "up": 1, "down": 1, "north": 2, "south": 2}[direction]
                arms[direction] = [skinned(e, along) for e in arms[direction]]
            write(name + "/arm_" + direction, textures, arms[direction])
        # The item shows a straight length of pipe
        write(name + "/item", textures, core_elements + arms["east"] + arms["west"])
