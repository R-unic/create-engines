"""Boxes, files and the texture names every model module uses."""

import json
import os


OUT = "src/main/resources/assets/create_engines/models/block"
FACES = ("north", "south", "east", "west", "up", "down")


# Only the refinery models use this: older models were made and approved with the default patches, so they stay as
# they are. Set by the functions that want it.
WRAP_UV = False
# With WRAP_UV off, a face is still moved back inside its texture when it runs this many pixels outside it or more
WRAP_MIN_OVERRUN = 0.5


def _face_patch(a, b, face):
    """The [u0, v0, u1, v1] patch of its texture that a face of the box from a to b shows by default."""
    x1, y1, z1 = a
    x2, y2, z2 = b
    return {
        "down": (x1, 16 - z2, x2, 16 - z1),
        "up": (x1, z1, x2, z2),
        "north": (16 - x2, 16 - y2, 16 - x1, 16 - y1),
        "south": (x1, 16 - y2, x2, 16 - y1),
        "west": (z1, 16 - y2, z2, 16 - y1),
        "east": (16 - z2, 16 - y2, 16 - z1, 16 - y1),
    }[face]


def _wrapped_default_uv(a, b, face):
    """The patch a face would show by default, moved back inside the texture when the element pokes out of the block
    (a boss or port outside 0..16): outside the sprite the default patch samples the neighbouring sprites in the atlas
    and shows as stray colours. Returns None when the default is already inside and needs no uv."""
    u0, v0, u1, v1 = _face_patch(a, b, face)
    if min(u0, v0) >= 0 and max(u1, v1) <= 16:
        return None
    # Models made before WRAP_UV (the pipe arms and the like) keep their approved default patches where the overrun is a
    # fraction of a pixel; a pixel or more (the head plugs and injectors, rockers, pushrods, valves, pistons, rods) shows as
    # stray colour from the neighbouring sprite, which one atlas layout hides and another (a bigger modpack) does not
    overrun = max(-min(u0, v0, 0), max(u1, v1, 16) - 16)
    if not WRAP_UV and overrun < WRAP_MIN_OVERRUN:
        return None
    du = -(u0 // 16) * 16
    dv = -(v0 // 16) * 16
    patch = [u0 + du, v0 + dv, u1 + du, v1 + dv]
    # A piece wider than the texture would still overrun: show the whole texture instead
    if max(patch) > 16 or min(patch) < 0:
        return [0, 0, 16, 16]
    return [round(v, 4) for v in patch]


def box(a, b, tex, uv=None, **overrides):
    """One element from corner a to corner b. tex is the texture for every face; overrides retexture single faces; uv
    maps a face name to the [u0, v0, u1, v1] patch of its texture to show, instead of the one under it."""
    faces = {}
    for face in FACES:
        faces[face] = {"texture": "#" + overrides.get(face, tex)}
        if uv and face in uv:
            faces[face]["uv"] = uv[face]
        else:
            patch = _wrapped_default_uv(a, b, face)
            if patch:
                faces[face]["uv"] = patch
    return {"from": [round(v, 4) for v in a], "to": [round(v, 4) for v in b], "faces": faces}


def shaft(a, b):
    """A Create-style shaft: axle texture along its length, end texture on the caps."""
    return box(a, b, "axis", up="end", down="end")


def write(path, textures, elements, **extra):
    data = {"parent": "block/block"}
    data.update(extra)
    data["textures"] = textures
    data["elements"] = elements
    full = os.path.join(OUT, path + ".json")
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w", newline="\n") as fh:
        json.dump(data, fh, indent=2)
        fh.write("\n")
    print("wrote", full)


def write_item(path, parent):
    full = os.path.join(OUT, path + ".json")
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w", newline="\n") as fh:
        json.dump({"parent": "create_engines:block/" + parent}, fh, indent=2)
        fh.write("\n")


CASING = "create:block/andesite_casing"
COPPER_CASING = "create:block/copper_casing"
IRON = "create_engines:block/cast_iron"
DARK = "create_engines:block/black_oxide"
BRASS = "create_engines:block/brass"
ZINC = "create:block/zinc_block"
COPPER = "create_engines:block/copper"
CUT_COPPER = "create_engines:block/copper_panel"
INTAKE_FLANGE = "create_engines:block/intake_flange"
INTAKE_PIPE = "create_engines:block/intake_pipe"
INTAKE_BELL = "create_engines:block/intake_bell"
IRON_BLOCK = "create_engines:block/steel"
AXIS = "create:block/axis"
AXIS_TOP = "create:block/axis_top"
BLACK = "create_engines:block/screen"
QUARTZ = "create_engines:block/ceramic"
SMOOTH = "create_engines:block/heat_wrap"
BRASS_CASING = "create:block/brass_casing"
RED = "create_engines:block/enamel_red"
BLUE = "create_engines:block/enamel_blue"
RIVETED = "create_engines:block/rivet_plate"
MAGMA = "create_engines:block/heat_kindled"
LAVA = "create_engines:block/heat_seething"
GLASS = "minecraft:block/glass"
COIL_CAN = "create_engines:block/coil_can"
DOME = "create_engines:block/piston_dome"
DISH = "create_engines:block/piston_dish"


def write_child(path, parent, textures):
    """A model that is Create's, with its textures swapped."""
    full = os.path.join(OUT, path + ".json")
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w", newline="\n") as fh:
        json.dump({"parent": parent, "textures": textures}, fh, indent=2)
        fh.write("\n")
    print("wrote", full)
