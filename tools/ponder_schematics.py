"""Writes the Ponder scene schematics to src/main/resources/assets/create_engines/ponder/.

Run from the repository root: python tools/ponder_schematics.py
Each scene is a list of (x, y, z, block, properties) on top of Create's usual snow / white concrete base plate.
"""
import gzip
import os
import struct

OUT = "src/main/resources/assets/create_engines/ponder"
DATA_VERSION = 3955  # Minecraft 1.21.1


def _string(text):
    raw = text.encode("utf-8")
    return struct.pack(">H", len(raw)) + raw


def _named(tag, name, payload):
    return bytes([tag]) + _string(name) + payload


def _int(value):
    return struct.pack(">i", value)


def _list(tag, items):
    return bytes([tag]) + struct.pack(">i", len(items)) + b"".join(items)


def _compound(entries):
    return b"".join(entries) + b"\x00"


def write_schematic(name, size, blocks):
    sx, sy, sz = size
    palette = []
    index = {}

    def state_id(block, properties):
        key = (block, tuple(sorted(properties.items())))
        if key not in index:
            index[key] = len(palette)
            palette.append(key)
        return index[key]

    placed = {}
    for x in range(sx):
        for z in range(sz):
            plate = "minecraft:snow_block" if (x + z) % 2 == 0 else "minecraft:white_concrete"
            placed[(x, 0, z)] = state_id(plate, {})
    for x, y, z, block, properties in blocks:
        placed[(x, y, z)] = state_id(block, properties)

    palette_tags = []
    for block, properties in palette:
        entries = [_named(8, "Name", _string(block))]
        if properties:
            entries.append(_named(10, "Properties",
                                  _compound([_named(8, k, _string(v)) for k, v in properties])))
        palette_tags.append(_compound(entries))
    block_tags = [_compound([_named(9, "pos", _list(3, [_int(c) for c in pos])), _named(3, "state", _int(state))])
                  for pos, state in sorted(placed.items())]
    root = _compound([
        _named(3, "DataVersion", _int(DATA_VERSION)),
        _named(9, "size", _list(3, [_int(sx), _int(sy), _int(sz)])),
        _named(9, "palette", _list(10, palette_tags)),
        _named(9, "blocks", _list(10, block_tags)),
        _named(9, "entities", _list(0, [])),
    ])
    path = os.path.join(OUT, name + ".nbt")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with gzip.open(path, "wb") as fh:
        fh.write(_named(10, "", root))
    print("wrote", path)


def ce(name):
    return "create_engines:" + name


def axis(a):
    return {"axis": a}


def cylinder(facing):
    return {"facing": facing, "piston": "true", "connecting_rod": "true"}


def head(facing):
    return {"facing": facing, "injector": "true"}


def facing(f):
    return {"facing": f}


def pipe(*open_ends):
    """A pipe open on the given sides."""
    return {side: "true" for side in open_ends}


write_schematic("crankshaft", (7, 5, 7), [
    (1, 1, 3, ce("crankshaft"), axis("x")),
    (2, 1, 3, ce("crank_throw"), axis("x")),
    (3, 1, 3, ce("crankshaft"), axis("x")),
    (4, 1, 3, ce("crank_throw"), axis("x")),
    (5, 1, 3, ce("crankshaft"), axis("x")),
    (6, 1, 3, "create:shaft", {"axis": "x", "waterlogged": "false"}),
    (2, 2, 3, ce("cylinder"), cylinder("up")),
    (4, 2, 3, ce("cylinder"), cylinder("up")),
    (2, 3, 3, ce("cylinder_head"), head("up")),
    (4, 3, 3, ce("cylinder_head"), head("up")),
    # A second bank on the north face of the second throw, for the V layout
    (4, 1, 2, ce("cylinder"), cylinder("north")),
    (4, 1, 1, ce("cylinder_head"), head("north")),
])

write_schematic("cylinder", (5, 6, 5), [
    (1, 1, 2, ce("crankshaft"), axis("x")),
    (2, 1, 2, ce("crank_throw"), axis("x")),
    (3, 1, 2, ce("crankshaft"), axis("x")),
    (2, 2, 2, ce("cylinder"), cylinder("up")),
    (2, 3, 2, ce("cylinder_head"), head("up")),
    (2, 3, 1, ce("air_intake"), facing("north")),
    (2, 3, 3, ce("exhaust_outlet"), facing("south")),
    (2, 4, 2, ce("cam_lobe"), axis("x")),
    (1, 4, 2, ce("camshaft"), axis("x")),
    (3, 4, 2, ce("camshaft"), axis("x")),
])

write_schematic("camshaft", (7, 5, 7), [
    (1, 1, 2, ce("crankshaft"), axis("x")),
    (2, 1, 2, ce("crank_throw"), axis("x")),
    (3, 1, 2, ce("crankshaft"), axis("x")),
    (4, 1, 2, "create:shaft", {"axis": "x", "waterlogged": "false"}),
    (5, 1, 2, "create:cogwheel", {"axis": "x", "waterlogged": "false"}),
    (2, 2, 2, ce("cylinder"), cylinder("up")),
    (2, 3, 2, ce("cylinder_head"), head("up")),
    (2, 3, 1, ce("air_intake"), facing("north")),
    (1, 3, 2, ce("exhaust_outlet"), facing("west")),
    (2, 3, 3, ce("cam_lobe"), axis("x")),
    (3, 3, 3, ce("camshaft"), axis("x")),
    (4, 3, 3, ce("camshaft"), axis("x")),
    (5, 3, 3, ce("camshaft"), axis("x")),
    (5, 2, 3, "create:large_cogwheel", {"axis": "x", "waterlogged": "false"}),
    (6, 2, 3, "create:encased_chain_drive", {"axis": "x", "part": "none", "connected_along_first_coordinate": "false"}),
    (6, 3, 3, "create:encased_chain_drive", {"axis": "x", "part": "none", "connected_along_first_coordinate": "false"}),
])

write_schematic("airflow", (6, 6, 6), [
    (1, 1, 3, ce("crankshaft"), axis("x")),
    (2, 1, 3, ce("crank_throw"), axis("x")),
    (3, 1, 3, ce("crank_throw"), axis("x")),
    (4, 1, 3, ce("crankshaft"), axis("x")),
    (2, 2, 3, ce("cylinder"), cylinder("up")),
    (3, 2, 3, ce("cylinder"), cylinder("up")),
    (2, 3, 3, ce("cylinder_head"), head("up")),
    (3, 3, 3, ce("cylinder_head"), head("up")),
    (2, 3, 2, ce("intake_manifold"), pipe("west", "east", "south")),
    (3, 3, 2, ce("intake_manifold"), pipe("west", "south")),
    (1, 3, 2, ce("air_intake"), facing("west")),
    (2, 3, 4, ce("exhaust_manifold"), pipe("north", "east")),
    (3, 3, 4, ce("exhaust_manifold"), pipe("north", "west", "up")),
    (3, 4, 4, ce("exhaust_outlet"), facing("up")),
    (2, 4, 3, ce("cam_lobe"), axis("x")),
    (3, 4, 3, ce("cam_lobe"), axis("x")),
    (1, 4, 3, ce("camshaft"), axis("x")),
    (4, 4, 3, ce("camshaft"), axis("x")),
])

write_schematic("spark", (6, 6, 6), [
    (0, 1, 3, ce("alternator"), axis("x")),
    (1, 2, 3, ce("battery"), {}),
    (1, 1, 3, ce("crankshaft"), axis("x")),
    (2, 1, 3, ce("crank_throw"), axis("x")),
    (3, 1, 3, ce("crankshaft"), axis("x")),
    (2, 2, 3, ce("cylinder"), cylinder("up")),
    (2, 3, 3, ce("cylinder_head"), {**head("up"), "spark_plug": "true"}),
    (2, 3, 2, ce("air_intake"), facing("north")),
    (2, 3, 4, ce("exhaust_outlet"), facing("south")),
    (2, 4, 3, ce("cam_lobe"), axis("x")),
    (1, 4, 3, ce("camshaft"), axis("x")),
    (3, 4, 3, ce("camshaft"), axis("x")),
])

write_schematic("starter", (5, 6, 5), [
    (1, 2, 3, ce("crankshaft"), axis("x")),
    (2, 2, 3, ce("crank_throw"), axis("x")),
    (3, 2, 3, ce("crankshaft"), axis("x")),
    (2, 3, 3, ce("cylinder"), cylinder("up")),
    (2, 4, 3, ce("cylinder_head"), head("up")),
    (1, 2, 2, ce("starter"), facing("south")),
    (1, 2, 1, "create:shaft", {"axis": "z", "waterlogged": "false"}),
    (1, 2, 0, "create:creative_motor", facing("south")),
    (0, 2, 2, "minecraft:redstone_block", {}),
])

write_schematic("lubrication", (6, 6, 6), [
    (1, 1, 3, ce("crankshaft"), axis("x")),
    (2, 1, 3, ce("crank_throw"), axis("x")),
    (3, 1, 3, ce("crankshaft"), axis("x")),
    (2, 2, 3, ce("cylinder"), cylinder("up")),
    (2, 3, 3, ce("cylinder_head"), head("up")),
    # Oil comes in through a pipe from a tank, into the side of the head
    (3, 3, 3, "create:fluid_pipe", {"east": "true", "west": "true", "north": "false", "south": "false",
                                    "up": "false", "down": "false", "waterlogged": "false"}),
    (4, 3, 3, "create:fluid_tank", {}),
])
