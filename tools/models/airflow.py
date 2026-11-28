"""Intake, outlet, rain cap, Maker's Plate, framed blueprint and pipes."""

from models.core import (
    BLACK, BRASS, BRASS_CASING, COPPER, COPPER_CASING, CUT_COPPER, DARK, IRON, IRON_BLOCK, LAVA, MAGMA, SMOOTH,
    box, write)


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
