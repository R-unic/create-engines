"""Rotor and rotor housing."""

import math

from models.core import (
    AXIS, AXIS_TOP, BLUE, BRASS, COIL_CAN, COPPER_CASING, CUT_COPPER, DARK, IRON, IRON_BLOCK, QUARTZ, RED, box,
    shaft, write, write_item)
from models.head import can


# --- rotor housing -------------------------------------------------------------------------------------------------

ROTOR_RADIUS = 4.6
ROTOR_ECCENTRICITY = 1.5


def rotor_rows(radius=ROTOR_RADIUS, step=1.5):
    """An equilateral triangle with its apex towards -Z, centred on (8, 8), as non-overlapping rows (z0, z1, half
    width) a few broad steps. Its flanks bulge a little, as a Wankel rotor's do."""
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


def rotor_crowns(y0, y1):
    """The high compression rotor's mark, on both end faces: the red raised block of the high compression piston."""
    return [box((5.75, y1, 5.75), (10.25, y1 + 0.9, 10.25), "high"), box((5.75, y0 - 0.9, 5.75), (10.25, y0, 10.25), "high")]


def rotor_dishes(y0, y1):
    """The low compression rotor's mark, on both end faces: the low compression piston's steel rim round a blue floor."""
    elements = []
    for floor0, floor1, rim0, rim1 in ((y1, y1 + 0.25, y1, y1 + 0.9), (y0 - 0.25, y0, y0 - 0.9, y0)):
        elements.append(box((6.5, floor0, 6.5), (9.5, floor1, 9.5), "low"))
        elements += [box((5.5, rim0, 5.5), (10.5, rim1, 6.5), "rotor"), box((5.5, rim0, 9.5), (10.5, rim1, 10.5), "rotor"),
                     box((5.5, rim0, 6.5), (6.5, rim1, 9.5), "rotor"), box((9.5, rim0, 6.5), (10.5, rim1, 9.5), "rotor")]
    return elements


def rotor_housing():
    textures = {"casing": COPPER_CASING, "injector": BRASS, "ceramic": QUARTZ, "terminal": IRON_BLOCK,
                "particle": COPPER_CASING}
    base = []
    # A thick casing plate at each end of the shaft with a square window round it, and a post at each corner holding them
    # apart: the rotor shows through every side and down the shaft. The rotor's tips never reach the posts.
    for y0, y1 in ((0, 3), (13, 16)):
        base += [box((0, y0, 0), (16, y1, 5), "casing"), box((0, y0, 11), (16, y1, 16), "casing"),
                 box((0, y0, 5), (5, y1, 11), "casing"), box((11, y0, 5), (16, y1, 11), "casing")]
    for x in (0, 13):
        for z in (0, 13):
            base.append(box((x, 3, z), (x + 3, 13, z + 3), "casing"))
    # The injector and plug are screwed into the edge of the lower plate
    injector = [box((11, 0, 16), (14, 3, 17.5), "injector")]
    plug = [box((2.5, 0.25, 16), (5.5, 2.75, 19), "ceramic"), box((3.25, 0.75, 19), (4.75, 2.25, 20.5), "terminal"),
            box((2, 0, 16), (6, 3, 17), "terminal")]
    # The coil of a head's plug, laid along the plug (+Z): it swallows the terminal, so that box is left out under it
    coil = [can((2.3, 0.05, 18.9), (5.7, 2.95, 21.9)), box((3.25, 0.75, 21.8), (4.75, 2.25, 22.9), "terminal")]
    coiled = dict(textures, assist=COIL_CAN)
    write("rotor_housing/block", textures, base)
    write("rotor_housing/block_injector", textures, base + injector)
    write("rotor_housing/block_plug", textures, base + plug)
    write("rotor_housing/block_injector_plug", textures, base + injector + plug)
    coiled_plug = [plug[0], plug[2]] + coil
    write("rotor_housing/block_plug_coil", coiled, base + coiled_plug)
    write("rotor_housing/block_injector_plug_coil", coiled, base + injector + coiled_plug)
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
    textures = dict(ROTOR_TEXTURES, high=RED, low=BLUE)
    for suffix, y0, y1 in (("", 5.0, 11.0), ("_item", 6.0, 10.0)):
        stock = rotor_elements(y0, y1)
        write("rotor_housing/high_compression_rotor" + suffix, textures, stock + rotor_crowns(y0, y1))
        write("rotor_housing/low_compression_rotor" + suffix, textures, stock + rotor_dishes(y0, y1))
