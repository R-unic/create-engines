"""Engine Casing."""

import json
import os

from models.core import DARK, FACES, IRON_BLOCK, OUT, write, write_child, write_item


CASING_SPRITE = "create_engines:block/engine_casing"
CASING_TEXTURES = {"casing": CASING_SPRITE, "cap": IRON_BLOCK, "dark": DARK, "particle": CASING_SPRITE}


def casing_faces(faces, texture="casing", cull=True, tint=False):
    """Full-tile faces of a cube, for the sides named; CULLED against the neighbour so a clad run draws only its skin.
    A tinted face takes the paint of the casing block it belongs to (tint index 0)."""
    extra = {"tintindex": 0} if tint else {}
    return {f: dict({"texture": "#" + texture, "uv": [0, 0, 16, 16]}, **({"cullface": f} if cull else {}), **extra)
            for f in faces}


def engine_casing():
    """The engine casing, a cube of one face-filling tile so that its connected-texture sheet can join a run of it into
    one plate, and its cased variant. The cased variant is the same cube with its two ends opened into a recessed square
    for the part inside (a shaft, a crank, a cam), drawn standing on the Y axis and turned onto the part's axis by the
    blockstate. The sides use the casing sprite exactly, so they join the casing beside them too; the ends do not."""
    write("engine_casing/block", CASING_TEXTURES,
          [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": casing_faces(FACES, tint=True)}])
    write_item("engine_casing/item", "engine_casing/block")
    # The riveted and louvred plates: the same cube on their own sheet. Clad parts use the encased models further down.
    for kind in ("riveted", "louvred"):
        sprite = "create_engines:block/engine_%s_casing" % kind
        write("engine_%s_casing/block" % kind, {"casing": sprite, "particle": sprite},
              [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": casing_faces(FACES, tint=True)}])
        write_item("engine_%s_casing/item" % kind, "engine_%s_casing/block" % kind)
    # The window plate has a pane of glass in it, which is made of see-through pixels, so it draws in the translucent layer
    window = "create_engines:block/engine_window_casing"
    write("engine_window_casing/block", {"casing": window, "particle": window},
          [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": casing_faces(FACES, tint=True)}],
          render_type="minecraft:translucent")
    write_item("engine_window_casing/item", "engine_window_casing/block")
    elements = [{"from": [0, 0, 0], "to": [16, 16, 16],
                 "faces": casing_faces(("north", "south", "east", "west"), tint=True)}]
    for y0, y1, end in ((14, 16, "up"), (0, 2, "down")):
        # A square plate round an 8 by 8 opening: four strips, each showing its end and the wall of the opening
        for a, b, wall in (((0, y0, 0), (16, y1, 4), "south"), ((0, y0, 12), (16, y1, 16), "north"),
                           ((0, y0, 4), (4, y1, 12), "east"), ((12, y0, 4), (16, y1, 12), "west")):
            elements.append({"from": list(a), "to": list(b),
                             "faces": {end: {"texture": "#cap"}, wall: {"texture": "#dark"}}})
    # A dark floor behind each opening, so the shaft shows against it rather than through the casing
    elements.append({"from": [4, 14, 4], "to": [12, 14, 12], "faces": {"up": {"texture": "#dark"}}})
    elements.append({"from": [4, 2, 4], "to": [12, 2, 12], "faces": {"down": {"texture": "#dark"}}})
    write("engine_casing/encased", CASING_TEXTURES, elements)
    # A part clad in the riveted or louvred plate: the same cap and opening, with that plate on the four sides
    for kind in ("riveted", "louvred"):
        sprite = "create_engines:block/engine_%s_casing" % kind
        write("engine_%s_casing/encased" % kind, dict(CASING_TEXTURES, casing=sprite, particle=sprite), elements)
    # The same for a part behind a window: the four sides are the window plate, so the part inside shows through the glass
    window_elements = [dict(e) for e in elements]
    window_elements[0] = {"from": [0, 0, 0], "to": [16, 16, 16],
                          "faces": casing_faces(("north", "south", "east", "west"), texture="window", tint=True)}
    write("engine_casing/window", dict(CASING_TEXTURES, window="create_engines:block/engine_window_casing"),
          window_elements, render_type="minecraft:translucent")

    # Create's own encased shaft and cogwheel models, dressed in the engine casing. The block models are copies of
    # Create's with the casing faces tinted (tools/derive_cased_models.py); only the items are children of Create's.
    opening = {"casing": CASING_SPRITE, "opening": DARK, "particle": CASING_SPRITE}
    write_child("engine_cased_shaft/item", "create:block/encased_shaft/item", opening)

    # The shaft and cogwheels clad in the other three plates: copies of the plain cased models (derived from Create's, see
    # tools/derive_cased_models.py) with the plate swapped in for the casing faces. The window ones show the part through
    # their glass, so they draw in the translucent layer. The cog sides keep the plain steel ones.
    for kind in ("riveted", "louvred", "window"):
        sprite = "create_engines:block/engine_%s_casing" % kind
        for part, suffixes in (("shaft", ("",)), ("cogwheel", ("", "_top", "_bottom", "_top_bottom")),
                               ("large_cogwheel", ("", "_top", "_bottom", "_top_bottom"))):
            for suffix in suffixes:
                with open(os.path.join(OUT, "engine_cased_%s/block%s.json" % (part, suffix))) as fh:
                    model = json.load(fh)
                model["textures"]["casing"] = sprite
                model["textures"]["particle"] = sprite
                if kind == "window":
                    model["render_type"] = "minecraft:translucent"
                target = os.path.join(OUT, "engine_%s_cased_%s/block%s.json" % (kind, part, suffix))
                os.makedirs(os.path.dirname(target), exist_ok=True)
                with open(target, "w", newline="\n") as fh:
                    json.dump(model, fh, indent=2)
                    fh.write("\n")
                print("wrote", target)
