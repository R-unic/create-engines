"""Starter, batteries and alternator."""

from models.core import (
    BLACK, BRASS, CASING, COPPER, COPPER_CASING, DARK, IRON, IRON_BLOCK, MAGMA, ZINC, _face_patch, box, write,
    write_item)


# --- starter, battery, alternator ----------------------------------------------------------------------------------

def starter():
    textures = {"body": CASING, "pinion": BRASS, "coil": COPPER, "plate": IRON, "bolt": BRASS, "particle": CASING}
    elements = [
        # Mounting plate round the input socket, so the shaft shows in the middle
        box((4, 0, 4), (12, 2, 6), "body"), box((4, 0, 10), (12, 2, 12), "body"),
        box((4, 0, 6), (6, 2, 10), "body"), box((10, 0, 6), (12, 2, 10), "body"),
        # Motor can, an octagon, with the wound field banding it
        box((3, 2, 4), (13, 10, 12), "body"),
        box((4, 2, 3), (12, 10, 13), "body"),
        box((2.75, 3.5, 4.75), (13.25, 4.5, 11.25), "coil"),
        box((4.75, 3.5, 2.75), (11.25, 4.5, 13.25), "coil"),
        box((2.75, 7.5, 4.75), (13.25, 8.5, 11.25), "coil"),
        box((4.75, 7.5, 2.75), (11.25, 8.5, 13.25), "coil"),
        # Solenoid riding on the side, with its terminals
        box((12, 3, 5.5), (15.5, 9, 10.5), "coil"),
        box((14.5, 9, 6), (15.5, 10.5, 7), "plate"),
        box((14.5, 9, 9), (15.5, 10.5, 10), "plate"),
        # Nose housing and mounting ears
        box((5, 10, 5), (11, 12, 11), "body"),
        box((1, 10, 6.5), (15, 12, 9.5), "plate"),
        box((1.75, 12, 7.25), (2.75, 12.5, 8.25), "bolt"),
        box((13.25, 12, 7.25), (14.25, 12.5, 8.25), "bolt"),
        # The pinion that meshes with the crank
        box((6.5, 12, 6.5), (9.5, 13, 9.5), "pinion"),
        box((6, 13, 6), (10, 16, 10), "pinion"),
        box((5, 13.5, 7), (6, 15.5, 9), "pinion"),
        box((10, 13.5, 7), (11, 15.5, 9), "pinion"),
        box((7, 13.5, 5), (9, 15.5, 6), "pinion"),
        box((7, 13.5, 10), (9, 15.5, 11), "pinion"),
    ]
    write("starter/block", textures, elements)


BATTERY_SIDES = ("north", "east", "south", "west")


def battery_textures():
    # "dark" is the unlit lamp: a model that names a texture it does not define shows the purple and black missing texture
    return {"body": ZINC, "band": BRASS, "cap": CASING, "pole": IRON, "lid": DARK, "vent": IRON_BLOCK, "dark": BLACK,
            "particle": ZINC}


# For each face, the joined edge of the block (or the top) that moves a side of the face's [u0, v0, u1, v1] patch one pixel in:
# {side: (index in the patch, step)}. Which side of the patch a world edge falls on follows from _face_patch.
BATTERY_JOIN_TRIMS = {
    "north": {"east": (0, 1), "west": (2, -1), "top": (1, 1)},
    "south": {"west": (0, 1), "east": (2, -1), "top": (1, 1)},
    "west": {"north": (0, 1), "south": (2, -1), "top": (1, 1)},
    "east": {"south": (0, 1), "north": (2, -1), "top": (1, 1)},
    "up": {"west": (0, 1), "east": (2, -1), "north": (1, 1), "south": (3, -1)},
}


def battery_shell(n=False, e=False, s=False, w=False, u=False):
    """The battery with the sides in n (north, z-), e (x+), s (z+), w (x-) and the top u joined to another battery, as a
    tank or a vault runs on into the next one. The body, the band and the lid reach the edge of the block on a joined side and
    leave out the faces that look into the neighbour; a battery with another on top has a body that runs up to the next
    one and no terminals, caps or vents (they stay on the top of the stack), but keeps the lid ring so each still reads as
    a cell and carries its own lamp strip. Everything on a side that is not joined is as a lone battery has it."""
    x0, x1 = (0 if w else 2), (16 if e else 14)
    z0, z1 = (0 if n else 2), (16 if s else 14)
    tx0, tx1 = (0 if w else 1.5), (16 if e else 14.5)
    tz0, tz1 = (0 if n else 1.5), (16 if s else 14.5)

    strips = []

    def flat(face, lo, hi, tex):
        """One face with no thickness: the piece of a joined face that stays drawn."""
        patch = [round(v, 4) for v in _face_patch(lo, hi, face)]
        return {"from": [round(v, 4) for v in lo], "to": [round(v, 4) for v in hi],
                "faces": {face: {"texture": "#" + tex, "uv": patch}}}

    def margin_strips(face, a, b, tex):
        """The part of a joined face that a neighbour may not cover. Every neighbour has a body 2 units in from the edges its own
        joins leave free (1.5 for the band and the lid ring) and one lid and band below 13.5, so the middle of the face is left out
        and only the margin beside a side this one is joined on stays; the body above 13.5 (there only with a battery on top)
        stays whole, since a neighbour that is not stacked has nothing there."""
        base = 1.5 if tex in ("band", "lid") else 2
        out = []
        if face == "up":
            y = b[1]
            if a[0] < base:
                out.append(flat(face, (a[0], y, a[2]), (min(b[0], base), y, b[2]), tex))
            if b[0] > 16 - base:
                out.append(flat(face, (max(a[0], 16 - base), y, a[2]), (b[0], y, b[2]), tex))
            xl, xh = max(a[0], base), min(b[0], 16 - base)
            if a[2] < base:
                out.append(flat(face, (xl, y, a[2]), (xh, y, min(b[2], base)), tex))
            if b[2] > 16 - base:
                out.append(flat(face, (xl, y, max(a[2], 16 - base)), (xh, y, b[2]), tex))
            return out
        along_x = face in ("north", "south")
        lo, hi = (a[0], b[0]) if along_x else (a[2], b[2])
        if a[1] >= 13.5:
            spans = [(lo, hi)]
        else:
            spans = []
            if lo < base:
                spans.append((lo, min(hi, base)))
            if hi > 16 - base:
                spans.append((max(lo, 16 - base), hi))
        for p0, p1 in spans:
            if face == "east":
                out.append(flat(face, (b[0], a[1], p0), (b[0], b[1], p1), tex))
            elif face == "west":
                out.append(flat(face, (a[0], a[1], p0), (a[0], b[1], p1), tex))
            elif face == "south":
                out.append(flat(face, (p0, a[1], b[2]), (p1, b[1], b[2]), tex))
            else:
                out.append(flat(face, (p0, a[1], a[2]), (p1, b[1], a[2]), tex))
        return out

    def part(a, b, tex, omit=()):
        element = box(a, b, tex)
        for face in omit:
            element["faces"].pop(face, None)
        # A face on the edge of the block on a joined side looks into the neighbour, but only where the neighbour is there too: it
        # may be narrower (not joined to what this one is joined to) or lower (no battery on its top), and a face left out there
        # would be a slot into the inside of the body, where an L bends or a stack steps. The part that every neighbour covers is
        # left out; the rest of the face stays as a thin strip (it is inside the bank wherever the neighbour matches).
        for face, on in (("west", w and a[0] == 0), ("east", e and b[0] == 16), ("north", n and a[2] == 0),
                         ("south", s and b[2] == 16), ("up", u and b[1] == 16)):
            if on:
                element["faces"].pop(face, None)
                strips.extend(margin_strips(face, a, b, tex))
        # A face that runs on into a neighbour would show the texture's own border (the brass plate's bevel, a dark edge line) again
        # where the two meet, as a seam: leave out the border pixel on each joined edge, so the texture runs on across the join
        edge = {"west": w and a[0] == 0, "east": e and b[0] == 16, "north": n and a[2] == 0, "south": s and b[2] == 16,
                "top": u and b[1] == 16}
        for face, moves in BATTERY_JOIN_TRIMS.items():
            if face not in element["faces"]:
                continue
            patch = list(element["faces"][face].get("uv") or _face_patch(a, b, face))
            trimmed = False
            for side, (index, step) in moves.items():
                if edge[side]:
                    patch[index] += step
                    trimmed = True
            if trimmed:
                element["faces"][face]["uv"] = [round(v, 4) for v in patch]
        return element

    elements = [
        # The body, in three lengths round the band and the lid ring, whose faces would share its planes on a joined side
        part((x0, 0, z0), (x1, 5, z1), "body", omit=("up",)),
        part((x0, 8, z0), (x1, 12, z1), "body", omit=("up", "down")),
        part((tx0, 5, tz0), (tx1, 8, tz1), "band"),
        part((tx0, 12, tz0), (tx1, 13.5, tz1), "lid"),
    ]
    if u:
        elements.append(part((x0, 13.5, z0), (x1, 16, z1), "body", omit=("down",)))
    else:
        elements.append(part((3, 13.5, 3), (13, 14, 13), "cap", omit=("down",)))
        # Positive terminal (brass) and negative (iron), each a tapered post
        elements += [
            part((4, 14, 6), (6.5, 15, 8.5), "band", omit=("down",)),
            part((4.75, 15, 6.75), (5.75, 16, 7.75), "band", omit=("down",)),
            part((9.5, 14, 6), (12, 15, 8.5), "pole", omit=("down",)),
            part((10.25, 15, 6.75), (11.25, 16, 7.75), "pole", omit=("down",)),
        ]
        # Filler caps over the six cells
        for x in (3.5, 6.75, 10):
            for z in (3.25, 10.75):
                elements.append(part((x, 14, z), (x + 2.5, 14.6, z + 2), "vent", omit=("down",)))
    # Hold-down straps on the two long faces, where nothing is joined
    if not w:
        elements += [part((1.7, 0, 5), (2, 5, 6.5), "lid"), part((1.7, 0, 9.5), (2, 5, 11), "lid")]
    if not e:
        elements += [part((14, 0, 5), (14.3, 5, 6.5), "lid"), part((14, 0, 9.5), (14.3, 5, 11), "lid")]
    # A piece whose every face looks into a neighbour (the middle of the body, with all four sides joined) is nothing to draw, and a
    # model with an element that has no faces does not load at all: the game shows the missing model for the whole block
    return [e for e in elements + strips if e["faces"]]


def battery_joins_name(n, e, s, w, u):
    return "".join("1" if on else "0" for on in (n, e, s, w, u))


def battery_tier_textures(kind):
    """The materials of each tier of battery. The plain battery is zinc, brass and andesite, as it always was; the next tier will
    need its own materials, to read apart on a bank. The dry cell is copper-cased with an iron band; the large battery is a steel
    tank with a brass band and a black lid."""
    if kind == "dry_cell":
        return {"body": COPPER_CASING, "band": IRON, "cap": CASING, "pole": IRON, "lid": DARK, "vent": IRON_BLOCK, "dark": BLACK,
                "particle": COPPER_CASING}
    if kind == "large_battery":
        return {"body": IRON_BLOCK, "band": BRASS, "cap": DARK, "pole": IRON, "lid": DARK, "vent": CASING, "dark": BLACK,
                "particle": IRON_BLOCK}
    return battery_textures()


def battery():
    for kind in ("battery", "dry_cell", "large_battery"):
        battery_tier(kind, battery_tier_textures(kind))


def battery_tier(kind, textures):
    lit_textures = dict(textures, lit=MAGMA)
    # One model for each of the 32 ways the neighbours can join it; the blockstate picks by the join properties
    for bits in range(32):
        joins = tuple(bool(bits >> i & 1) for i in range(5))
        write(kind + "/block_j" + battery_joins_name(*joins), textures, battery_shell(*joins))
    # The inventory model, and the one the particle and the lone battery use: a lone battery with its lamps dark
    lone = battery_shell()
    for side in BATTERY_SIDES:
        lone += battery_gauge_side(side, "dark", 0)
    write(kind + "/block", textures, lone)
    write_item(kind + "/item", kind + "/block")
    # Charge gauge: four slots along each face of the lid, dark when empty. gauge_N lights the first N of them (the
    # blockstate's charge property, see BatteryChemistry.gaugeStage), so the charge reads from any side without goggles.
    # A side that is joined to another battery has no lid face to put them on.
    for side in BATTERY_SIDES:
        write(kind + "/block_dark_" + side, textures, battery_gauge_side(side, "dark", 0))
        for stage in range(1, 5):
            write(kind + "/block_gauge_%d_%s" % (stage, side), lit_textures, battery_gauge_side(side, "lit", stage),
                  ambientocclusion=False)


def battery_gauge_side(side, texture, lit_cells):
    """The gauge cells on one face of the lid: all four dark (lit_cells 0), or the first lit_cells lit. A lit cell is a hair
    wider and taller than the dark one under it, so no face of one lies in the plane of a face of the other, and a cell has
    no face on the lid (which it would share with the lid's own)."""
    cells = []
    out = 0.1 if not lit_cells else 0.2
    y0, y1 = (12.5, 13.0) if not lit_cells else (12.4, 13.1)
    grow = 0 if not lit_cells else 0.1
    for i in range(4):
        if lit_cells and i >= lit_cells:
            continue
        a = 3.5 + 2.5 * i - grow
        b = 3.5 + 2.5 * i + 2 + grow
        lo, hi, inner = {
            "north": ((a, y0, 1.5 - out), (b, y1, 1.5), "south"),
            "south": ((a, y0, 14.5), (b, y1, 14.5 + out), "north"),
            "west": ((1.5 - out, y0, a), (1.5, y1, b), "east"),
            "east": ((14.5, y0, a), (14.5 + out, y1, b), "west"),
        }[side]
        cell = box(lo, hi, texture)
        del cell["faces"][inner]
        cells.append(cell)
    return cells


def alternator():
    textures = {"body": COPPER_CASING, "band": IRON, "cap": CASING, "vent": DARK, "bolt": BRASS, "lug": IRON_BLOCK,
                "particle": COPPER_CASING}
    elements = [
        box((2, 3, 2), (14, 13, 14), "body"),
        box((1.5, 6.5, 1.5), (14.5, 9.5, 14.5), "band"),
        box((3, 1, 3), (13, 3, 13), "cap"),
        box((3, 13, 3), (13, 15, 13), "cap"),
    ]
    # Cooling slots in the end housings
    for y in (4, 10.5):
        elements += [box((4, y, 1.75), (12, y + 1, 2), "vent"), box((4, y, 14), (12, y + 1, 14.25), "vent"),
                     box((1.75, y, 4), (2, y + 1, 12), "vent"), box((14, y, 4), (14.25, y + 1, 12), "vent")]
    # Through-bolts at the corners, mounting lugs and the rectifier pack with its output stud
    for x in (2.2, 12.8):
        for z in (2.2, 12.8):
            elements.append(box((x, 1, z), (x + 1, 15, z + 1), "bolt"))
    elements += [box((0.5, 4.5, 7), (2, 6, 9), "lug"), box((14, 4.5, 7), (15.5, 6, 9), "lug"),
                 box((14.25, 9.75, 5.5), (15.75, 12.25, 10.5), "band"),
                 box((15.75, 10.25, 7.25), (16, 11.25, 8.25), "bolt")]
    write("alternator/block", textures, elements)
