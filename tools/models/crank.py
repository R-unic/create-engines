"""Crankshaft, throw, camshaft and cam lobe."""

import math

from models.core import AXIS, AXIS_TOP, BRASS, CASING, DARK, IRON, IRON_BLOCK, box, shaft, write


# --- crank, throw, cam ---------------------------------------------------------------------------------------------

def crankshaft():
    """A main bearing on the crank's line: the journal with a flange at each end, and the split bearing round it."""
    textures = {"axis": AXIS, "end": AXIS_TOP, "journal": IRON, "bearing": CASING, "strap": BRASS, "particle": CASING}
    elements = [
        shaft((6, 0, 6), (10, 16, 10)),
        box((4.5, 2, 4.5), (11.5, 4, 11.5), "journal"),
        box((4.5, 12, 4.5), (11.5, 14, 11.5), "journal"),
        box((5.5, 4, 5.5), (10.5, 12, 10.5), "journal"),
        box((4, 5, 4), (12, 11, 12), "bearing"),
        # The cap's ears, on the two sides the throws leave free
        box((3, 6.5, 6), (13, 9.5, 10), "strap"),
    ]
    write("crankshaft/block", textures, elements)


def crank_throw():
    """Two webs, each an arm out to the pin with a broad counterweight behind it, and the pin between them."""
    textures = {"axis": AXIS, "end": AXIS_TOP, "web": IRON, "pin": IRON_BLOCK, "weight": DARK, "particle": IRON}
    elements = [shaft((6, 0, 6), (10, 3, 10)), shaft((6, 13, 6), (10, 16, 10))]
    for y0 in (3, 11):
        elements.append(box((4.5, y0, 6.5), (11.5, y0 + 2, 15), "web"))
        elements.append(box((2.5, y0, 1), (13.5, y0 + 2, 6.5), "weight"))
    elements.append(box((6, 5, 10), (10, 11, 14), "pin"))
    write("crank_throw/block", textures, elements)


def camshaft():
    textures = {"axis": AXIS, "end": AXIS_TOP, "bearing": CASING, "groove": DARK, "particle": AXIS}
    elements = [
        shaft((6.5, 0, 6.5), (9.5, 16, 9.5)),
        box((5.5, 1, 5.5), (10.5, 3, 10.5), "bearing"),
        box((5.5, 13, 5.5), (10.5, 15, 10.5), "bearing"),
        box((5, 6.5, 5), (11, 9.5, 11), "bearing"),
        box((5.25, 7.5, 5.25), (10.75, 8.5, 10.75), "groove"),
    ]
    write("camshaft/block", textures, elements)


def cam_profile(base_r=3.0, nose_r=1.0, nose_at=4.5, step=0.25):
    """The outline of a tangent cam, nose towards +Z, as non-overlapping rows (z0, z1, half width) a quarter pixel
    deep: the hull of a base circle and a small nose circle, which is the union of circles whose radius runs from
    one to the other along the line between their centres."""
    def half_width(z):
        best = 0.0
        for i in range(2001):
            t = i / 2000
            d = (base_r + (nose_r - base_r) * t) ** 2 - (z - nose_at * t) ** 2
            if d > 0:
                best = max(best, math.sqrt(d))
        return best

    rows = []
    z = -base_r
    while z < nose_at + nose_r - 1e-6:
        w = round(half_width(z + step / 2) / step) * step
        if w > 0:
            if rows and rows[-1][2] == w:
                rows[-1][1] = z + step
            else:
                rows.append([z, z + step, w])
        z += step
    return rows


def cam_disc(y0, y1, rows):
    """One cam, lying across the shaft between heights y0 and y1. Faces that a neighbouring row covers are left out."""
    elements = []
    for i, (z0, z1, w) in enumerate(rows):
        faces = {face: {"texture": "#lobe"} for face in ("east", "west", "up", "down")}
        if i == 0 or rows[i - 1][2] < w:
            faces["north"] = {"texture": "#lobe"}
        if i == len(rows) - 1 or rows[i + 1][2] < w:
            faces["south"] = {"texture": "#lobe"}
        elements.append({"from": [8 - w, y0, 8 + z0], "to": [8 + w, y1, 8 + z1], "faces": faces})
    return elements


def cam_lobe():
    """The set of lobes on a camshaft: an intake, an exhaust and an injector cam, each a smooth egg with its nose
    towards +Z at angle zero, on the shaft between bearing collars and spaced by thin rings."""
    textures = {"axis": AXIS, "end": AXIS_TOP, "lobe": IRON, "spacer": DARK, "bearing": CASING, "particle": IRON}
    elements = [
        shaft((6.5, 0, 6.5), (9.5, 16, 9.5)),
        box((5.5, 2, 5.5), (10.5, 3, 10.5), "bearing"),
        box((5.5, 13, 5.5), (10.5, 14, 10.5), "bearing"),
    ]
    elements += [box((5.75, y, 5.75), (10.25, y + 1, 10.25), "spacer") for y in (3, 6, 9, 12)]
    rows = cam_profile()
    for y in (4, 7, 10):
        elements += cam_disc(y, y + 2, rows)
    write("cam_lobe/block", textures, elements)

    # The same set in pieces, for the renderer: each lobe is turned on its own to the angle its valve needs, so the
    # shaft with its collars and spacers and the three lobes are drawn apart (the whole set above is the item)
    body = elements[:3 + 4]
    write("cam_lobe/shaft", textures, body)
    for name, y in (("intake", 4), ("exhaust", 7), ("injector", 10)):
        write("cam_lobe/" + name, textures, cam_disc(y, y + 2, rows))
