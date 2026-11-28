"""Dynamometer, Black Box, intercooler, filter, muffler, soot trap, soot and jerry can."""

from models.core import (
    BLACK, BRASS, CASING, COPPER_CASING, DARK, IRON, IRON_BLOCK, QUARTZ, RED, RIVETED, SMOOTH, box, write,
    write_item)


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


def air_filter():
    """An air filter along the Y axis (the block state turns it): a pale pleated element between two copper end plates held
    apart by brass posts, with a charcoal band round its middle and an inlet stub on each end."""
    textures = {"plate": COPPER_CASING, "element": QUARTZ, "post": BRASS, "charcoal": DARK, "stub": BRASS,
                "particle": COPPER_CASING}
    elements = [box((2, 0, 2), (14, 3, 14), "plate"), box((2, 13, 2), (14, 16, 14), "plate"),
                box((4, 3, 4), (12, 13, 12), "element"), box((2.75, 7, 2.75), (13.25, 9, 13.25), "charcoal")]
    for x in (2, 12.5):
        for z in (2, 12.5):
            elements.append(box((x, 3, z), (x + 1.5, 13, z + 1.5), "post"))
    # The pleats: ridges standing out of each face of the element
    for at in (5, 7.5, 10):
        elements += [box((at, 3, 3), (at + 1, 13, 4), "element"), box((at, 3, 12), (at + 1, 13, 13), "element"),
                     box((3, 3, at), (4, 13, at + 1), "element"), box((12, 3, at), (13, 13, at + 1), "element")]
    elements += [box((6, -0.1, 6), (10, 1, 10), "stub"), box((6, 15, 6), (10, 16.1, 10), "stub")]
    write("air_filter/block", textures, elements)
    write_item("air_filter/item", "air_filter/block")


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
