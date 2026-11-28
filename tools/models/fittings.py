"""Dynamometer, Black Box, intercooler, filter, muffler, soot trap, soot and jerry can."""

from models.core import (
    BLACK, BRASS, CASING, COPPER, COPPER_CASING, DARK, IRON, IRON_BLOCK, RED, RIVETED, box, write,
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


def _can(textures, window=None):
    """An exhaust canister along the Y axis: a pipe flange and a short neck at each end of a fat can with a rolled seam
    round each of its ends, so it reads as a can in a pipe run. window is a band of another texture round its middle."""
    elements = [box((3.5, 0, 3.5), (12.5, 1.5, 12.5), "flange"), box((5, 1.5, 5), (11, 2.5, 11), "flange"),
                box((2, 2.5, 2), (14, 4, 14), "seam"), box((2.25, 4, 2.25), (13.75, 12, 13.75), "shell"),
                box((2, 12, 2), (14, 13.5, 14), "seam"), box((5, 13.5, 5), (11, 14.5, 11), "flange"),
                box((3.5, 14.5, 3.5), (12.5, 16, 12.5), "flange")]
    if window:
        elements.append(box((2.1, 5.5, 2.1), (13.9, 10.5, 13.9), window))
    return elements


def intercooler():
    """A charge-air cooler along the Y axis (the block state turns it): a steel finned core between two copper tanks."""
    textures = {"tank": COPPER, "core": "create_engines:block/finned_core", "particle": COPPER}
    elements = [box((2, 0, 2), (14, 3, 14), "tank"), box((3, 3, 3), (13, 13, 13), "core"),
                box((2, 13, 2), (14, 16, 14), "tank")]
    write("intercooler/block", textures, elements)
    write_item("intercooler/item", "intercooler/block")


def air_filter():
    """An air filter along the Y axis (the block state turns it): a pleated paper element between two copper casing
    end plates."""
    textures = {"plate": COPPER_CASING, "element": "create_engines:block/pleats", "particle": COPPER_CASING}
    elements = [box((2, 0, 2), (14, 2, 14), "plate"), box((3, 2, 3), (13, 14, 13), "element"),
                box((2, 14, 2), (14, 16, 14), "plate")]
    write("air_filter/block", textures, elements)
    write_item("air_filter/item", "air_filter/block")


def muffler():
    """A muffler along the Y axis (the block state turns it): a plain cast can in the pipe run."""
    textures = {"flange": IRON, "seam": DARK, "shell": IRON, "particle": IRON}
    write("muffler/block", textures, _can(textures))
    write_item("muffler/item", "muffler/block")


def soot_trap():
    """A soot trap along the Y axis (the block state turns it): a dark can with brass seams and a mesh window round its
    middle, where the soot is caught."""
    textures = {"flange": IRON, "seam": BRASS, "shell": DARK, "mesh": "create_engines:block/grille",
                "particle": DARK}
    write("soot_trap/block", textures, _can(textures, "mesh"))
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
