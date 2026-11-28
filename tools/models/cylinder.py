"""Cylinder, piston and connecting rod."""

from models.core import BRASS, CASING, GLASS, IRON, IRON_BLOCK, box, write


# --- cylinder, piston, rod -----------------------------------------------------------------------------------------

def glass_panels():
    """A pane in each side, set between the corner posts and the rims, so the bore is enclosed but still visible."""
    return [box((3, 2, 1), (13, 14, 2), "glass"), box((3, 2, 14), (13, 14, 15), "glass"),
            box((1, 2, 3), (2, 14, 13), "glass"), box((14, 2, 3), (15, 14, 13), "glass")]


def cylinder():
    """Four corner posts and a rim at each end, so the piston shows through the open sides."""
    textures = {"frame": CASING, "rim": IRON, "glass": GLASS, "particle": CASING}
    elements = []
    for x in (0, 13):
        for z in (0, 13):
            elements.append(box((x, 0, z), (x + 3, 16, z + 3), "frame"))
    for y0, y1 in ((0, 2), (14, 16)):
        elements += [box((3, y0, 0), (13, y1, 3), "rim"), box((3, y0, 13), (13, y1, 16), "rim"),
                     box((0, y0, 3), (3, y1, 13), "rim"), box((13, y0, 3), (16, y1, 13), "rim")]
    elements += glass_panels()
    write("cylinder/block", textures, elements, render_type="minecraft:cutout")


def piston():
    textures = {"piston": IRON_BLOCK, "ring": IRON, "particle": IRON_BLOCK}
    write("cylinder/piston", textures,
          [box((3, -2, 3), (13, 6, 13), "piston"), box((2.9, 4, 2.9), (13.1, 4.6, 13.1), "ring")])


def rod_elements(big_y, small_y, beam_from, beam_to):
    return [
        box((7, beam_from, 7), (9, beam_to, 9), "rod"),
        box((6.5, big_y, 6), (9.5, big_y + 3, 10), "rod"),
        box((6.75, small_y, 6.5), (9.25, small_y + 2.5, 9.5), "rod"),
    ]


def rod():
    textures = {"rod": IRON, "particle": IRON}
    write("cylinder/rod", textures, rod_elements(-1.5, 12.75, 1, 13))
    write("cylinder/rod_item", textures, rod_elements(0, 13, 1, 15))
    # The light and heavy rods, slimmer in brass and stouter in steel: first as fitted in a cylinder, then as items
    write("cylinder/light_rod", {"rod": BRASS, "particle": BRASS},
          [box((7.25, 1, 7.25), (8.75, 13, 8.75), "rod"),
           box((6.75, -1.25, 6.5), (9.25, 1.25, 9.5), "rod"),
           box((7, 12.75, 6.75), (9, 14.75, 9.25), "rod")])
    write("cylinder/heavy_rod", {"rod": IRON_BLOCK, "particle": IRON_BLOCK},
          [box((6.5, 1, 6.75), (9.5, 13, 9.25), "rod"),
           box((6, -1.75, 5.5), (10, 1.75, 10.5), "rod"),
           box((6.25, 12.5, 6), (9.75, 15.5, 10), "rod")])
    write("cylinder/light_rod_item", {"rod": BRASS, "particle": BRASS},
          [box((7.25, 1, 7.25), (8.75, 15, 8.75), "rod"),
           box((6.75, 0, 6.5), (9.25, 2.5, 9.5), "rod"),
           box((7, 13.25, 6.75), (9, 15.25, 9.25), "rod")])
    write("cylinder/heavy_rod_item", {"rod": IRON_BLOCK, "particle": IRON_BLOCK},
          [box((6.5, 1, 6.75), (9.5, 15, 9.25), "rod"),
           box((6, 0, 5.5), (10, 3.5, 10.5), "rod"),
           box((6.25, 12.5, 6), (9.75, 15.5, 10), "rod")])
