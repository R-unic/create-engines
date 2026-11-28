"""Precision parts, governor, tools and headphones."""

from models.core import (
    BLACK, BLUE, BRASS, BRASS_CASING, COIL_CAN, DARK, GLASS, IRON, IRON_BLOCK, QUARTZ, RED, box, write, write_item)
from models.cylinder import glass_panels
from models.head import assist_elements, coil_elements


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
    assisted = dict(textures, assist=COIL_CAN)
    write("precision_cylinder_head/block_injector_assist", assisted, base + injector[:1] + assist)
    write("precision_cylinder_head/block_injector_assist_plug", assisted, base + injector[:1] + assist + plug)
    # The coil stands on the plug's ceramic and swallows its terminal and the one that tops the collar, so those are left out under it
    coil = coil_elements(3.5, 5.5, 11.5, 13.5, 20.5)
    coiled = dict(textures, assist=COIL_CAN)
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


def compression_pistons():
    """The high and low compression pistons: a forged piston's dark skirt, band and pin under two crowns that cannot be taken
    for each other. The high one stands a red dome on its top; the low one is dished, a steel rim round a blue floor. Neither
    reaches more than a pixel past the forged piston's crown, which is under the head."""
    textures = {"piston": DARK, "ring": BRASS, "pin": IRON_BLOCK, "high": RED, "low": BLUE, "rim": IRON_BLOCK,
                "particle": DARK}
    pin = box((6.5, -1, 2.6), (9.5, 2, 13.4), "pin")
    write("cylinder/high_compression_piston", textures,
          [box((3, -2, 3), (13, 5, 13), "piston"), box((2.9, 3.2, 2.9), (13.1, 3.8, 13.1), "ring"), pin,
           box((4.5, 5, 4.5), (11.5, 7, 11.5), "high")])
    write("cylinder/low_compression_piston", textures,
          [box((3, -2, 3), (13, 4, 13), "piston", up="low"), box((2.9, 2.6, 2.9), (13.1, 3.2, 13.1), "ring"), pin,
           box((3, 4, 3), (13, 6, 5), "rim"), box((3, 4, 11), (13, 6, 13), "rim"),
           box((3, 4, 5), (5, 6, 11), "rim"), box((11, 4, 5), (13, 6, 11), "rim")])


def piston_rings():
    """The rings fitted to a piston, drawn at its pose: thin cast-iron bands standing proud of its skirt, below the bands the
    pistons have of their own, one model for each number fitted."""
    heights = (1.8, 0.6, -0.6)
    for count in (1, 2, 3):
        write("cylinder/rings_%d" % count, {"ring": IRON, "particle": IRON},
              [box((2.75, y, 2.75), (13.25, y + 0.5, 13.25), "ring") for y in heights[:count]])


def precision_injector_item():
    write("cylinder_head/precision_injector_item",
          {"body": BRASS, "cap": DARK, "nozzle": IRON_BLOCK, "rail": QUARTZ, "particle": BRASS},
          [box((6, 0, 6), (10, 9, 10), "body"), box((5, 9, 5), (11, 12, 11), "cap"),
           box((7, -3, 7), (9, 0, 9), "nozzle"), box((4, 10, 7), (6, 11.5, 9), "rail")])


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
