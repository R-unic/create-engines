"""Cast and dual-mass flywheels."""

from models.core import AXIS, AXIS_TOP, BRASS, CASING, DARK, IRON, IRON_BLOCK, box, shaft, write


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
