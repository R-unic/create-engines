"""Cylinder head, injector, plug and valve."""

from models.core import BLUE, BRASS, COPPER_CASING, CUT_COPPER, DARK, IRON, IRON_BLOCK, QUARTZ, box, write


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


def assist_elements(y, x0, x1, z0, z1):
    """An electric injector assist: a dark driver box that sits on the injector's body, a blue winding band round it and a
    lead out of its side. Sunk 0.1 into what is under it so no face lies in the same plane as one below."""
    return [
        box((x0, y, z0), (x1, y + 2.1, z1), "assist"),
        box((x0 - 0.2, y + 0.8, z0 - 0.2), (x1 + 0.2, y + 1.5, z1 + 0.2), "coil"),
        box((x1 - 0.1, y + 1.0, (z0 + z1) / 2 - 0.4), (x1 + 1.2, y + 1.6, (z0 + z1) / 2 + 0.4), "terminal"),
    ]


def coil_elements(x0, x1, z0, z1, y):
    """A high-energy ignition coil that sits on a spark plug's ceramic (its top at y): a dark can, a blue winding band round it
    and a lead out of the top. The can is sunk 0.1 into the ceramic and stands 0.1 proud of it on every side, so no face lies in
    the same plane as one under it."""
    cx, cz = (x0 + x1) / 2, (z0 + z1) / 2
    return [
        box((x0 - 0.1, y - 0.1, z0 - 0.1), (x1 + 0.1, y + 2.9, z1 + 0.1), "assist"),
        box((x0 - 0.3, y + 0.9, z0 - 0.3), (x1 + 0.3, y + 1.7, z1 + 0.3), "coil"),
        box((cx - 0.5, y + 2.8, cz - 0.5), (cx + 0.5, y + 3.9, cz + 0.5), "terminal"),
    ]


def head():
    base = head_base()
    write("cylinder_head/block", HEAD_TEXTURES, base)
    write("cylinder_head/block_injector", HEAD_TEXTURES, base + injector_elements())
    write("cylinder_head/block_plug", HEAD_TEXTURES, base + plug_elements())
    write("cylinder_head/block_injector_plug", HEAD_TEXTURES, base + injector_elements() + plug_elements())
    assist = assist_elements(17.4, 10.6, 14.4, 1.6, 5.4)
    textures = dict(HEAD_TEXTURES, assist=DARK, coil=BLUE)
    write("cylinder_head/block_injector_assist", textures, base + injector_elements() + assist)
    write("cylinder_head/block_injector_assist_plug", textures,
          base + injector_elements() + assist + plug_elements())
    # A coil stands on the plug's ceramic and swallows its terminal, so that box is left out under it
    plug = plug_elements()
    coil = coil_elements(2.5, 5.5, 10.5, 13.5, 19)
    coiled = dict(HEAD_TEXTURES, assist=DARK, coil=BLUE)
    write("cylinder_head/block_plug_coil", coiled, base + [plug[0], plug[2]] + coil)
    write("cylinder_head/block_injector_plug_coil", coiled, base + injector_elements() + [plug[0], plug[2]] + coil)
    write("cylinder_head/block_injector_assist_plug_coil", coiled,
          base + injector_elements() + assist + [plug[0], plug[2]] + coil)
    write("cylinder_head/coil_item",
          {"assist": DARK, "coil": BLUE, "terminal": IRON, "ceramic": QUARTZ, "particle": DARK},
          [box((5.5, 0, 5.5), (10.5, 3, 10.5), "ceramic"), box((5.4, 2.9, 5.4), (10.6, 8.9, 10.6), "assist"),
           box((5.2, 4.7, 5.2), (10.8, 6.3, 10.8), "coil"), box((7, 8.8, 7), (9, 10.8, 9), "terminal")])
    write("cylinder_head/assist_item",
          {"assist": DARK, "coil": BLUE, "terminal": IRON, "nozzle": IRON_BLOCK, "particle": DARK},
          [box((5.5, 0, 5.5), (10.5, 5, 10.5), "assist"), box((5.3, 2, 5.3), (10.7, 3.4, 10.7), "coil"),
           box((10.4, 2.2, 7.6), (12.4, 3.2, 8.4), "terminal"), box((7, -3, 7), (9, 0, 9), "nozzle")])

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
