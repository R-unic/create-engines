"""Turbochargers and superchargers."""

from models.core import BRASS, BRASS_CASING, CASING, COPPER_CASING, DARK, IRON, IRON_BLOCK, box, write


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


def turbo_port():
    """The flange a turbocharger shows on a side that something is joined to. The housings stand a pixel in from the
    block's sides, so without it a pipe or an outlet on a side stops that pixel short. Modelled on the top face; the
    renderer turns it to each joined side."""
    write("turbocharger/port", {"flange": IRON, "particle": IRON}, [box((3, 15, 3), (13, 16, 13), "flange")])


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
