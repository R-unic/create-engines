"""Transmission, refining column, flare stack, radiator and sump."""

from models import core
from models.core import (
    BLACK, BRASS, CASING, COPPER, COPPER_CASING, DARK, IRON, IRON_BLOCK, MAGMA, RIVETED, box, write, write_item)


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
    core.WRAP_UV = True
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
    core.WRAP_UV = False


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
    core.WRAP_UV = True
    textures = {"pipe": IRON_BLOCK, "flange": DARK, "cup": IRON, "flame": MAGMA, "particle": IRON_BLOCK}
    base = [box((3, 0, 3), (13, 2, 13), "flange"), box((6, 2, 6), (10, 13, 10), "pipe"),
            box((5, 13, 5), (11, 15, 11), "cup"), box((5.5, 7, 5.5), (10.5, 8, 10.5), "flange")]
    write("flare_stack/block", textures, base)
    write("flare_stack/lit", textures, base + [box((6.5, 15, 6.5), (9.5, 18, 9.5), "flame"),
                                               box((7.2, 18, 7.2), (8.8, 19.5, 8.8), "flame")])
    write_item("flare_stack/item", "flare_stack/lit")
    core.WRAP_UV = False


def radiator():
    """A radiator: header tanks top and bottom, a copper core of tubes set back from all four faces, thin dark fins
    across it, and brass posts at the corners with a filler cap on top."""
    textures = {"tank": COPPER_CASING, "core": COPPER, "fin": DARK, "post": BRASS, "cap": BRASS, "particle": COPPER_CASING}
    elements = [
        box((0, 0, 0), (16, 3, 16), "tank"),
        box((0, 13, 0), (16, 16, 16), "tank"),
        box((2, 3, 2), (14, 13, 14), "core"),
    ]
    # Fins stand proud of the core and stay inside the posts, and share no face plane with either
    for y in (4, 6, 8, 10):
        elements.append(box((1, y + 0.2, 1), (15, y + 1, 15), "fin"))
    for x in (0.1, 13.9):
        for z in (0.1, 13.9):
            elements.append(box((x, 3, z), (x + 2, 13, z + 2), "post"))
    elements += [box((6.5, 16, 6.5), (9.5, 17, 9.5), "cap"), box((7.25, 17, 7.25), (8.75, 17.8, 8.75), "fin")]
    write("radiator/block", textures, elements)
    write_item("radiator/item", "radiator/block")


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
