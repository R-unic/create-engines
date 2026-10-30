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

write_schematic("throttle", (6, 6, 6), [
    (1, 1, 3, ce("crankshaft"), axis("x")),
    (2, 1, 3, ce("crank_throw"), axis("x")),
    (3, 1, 3, ce("crankshaft"), axis("x")),
    (2, 2, 3, ce("cylinder"), cylinder("up")),
    (2, 3, 3, ce("cylinder_head"), head("up")),
    (2, 3, 2, ce("throttle"), pipe("north", "south")),
    (2, 3, 1, ce("air_intake"), facing("north")),
    (2, 3, 4, ce("exhaust_outlet"), facing("south")),
    (2, 4, 3, ce("cam_lobe"), axis("x")),
    (1, 4, 3, ce("camshaft"), axis("x")),
    (3, 4, 3, ce("camshaft"), axis("x")),
    # A lever beside the throttle body stands in for a pedal
    (3, 2, 2, "minecraft:stone", {}),
    (3, 3, 2, "minecraft:lever", {"face": "floor", "facing": "north", "powered": "false"}),
])

write_schematic("mixture", (6, 6, 6), [
    (1, 1, 3, ce("crankshaft"), axis("x")),
    (2, 1, 3, ce("crank_throw"), axis("x")),
    (3, 1, 3, ce("crankshaft"), axis("x")),
    (2, 2, 3, ce("cylinder"), cylinder("up")),
    (2, 3, 3, ce("cylinder_head"), head("up")),
    (2, 3, 2, ce("air_intake"), facing("north")),
    (2, 3, 4, ce("exhaust_outlet"), facing("south")),
    (2, 4, 3, ce("cam_lobe"), axis("x")),
    (1, 4, 3, ce("camshaft"), axis("x")),
    (3, 4, 3, ce("camshaft"), axis("x")),
])

write_schematic("turbocharger", (7, 6, 7), [
    (1, 1, 3, ce("crankshaft"), axis("x")),
    (2, 1, 3, ce("crank_throw"), axis("x")),
    (3, 1, 3, ce("crankshaft"), axis("x")),
    (2, 2, 3, ce("cylinder"), cylinder("up")),
    (2, 3, 3, ce("cylinder_head"), head("up")),
    # The turbo is bolted to the head's exhaust; its compressor faces west
    (1, 3, 3, ce("turbocharger"), facing("west")),
    (0, 3, 3, ce("air_intake"), facing("west")),
    (1, 3, 4, ce("exhaust_outlet"), facing("south")),
    # The compressor's output runs round to the head's intake
    (1, 3, 2, ce("intake_manifold"), pipe("south", "east")),
    (2, 3, 2, ce("intake_manifold"), pipe("west", "south")),
    (2, 4, 3, ce("cam_lobe"), axis("x")),
    (1, 4, 3, ce("camshaft"), axis("x")),
    (3, 4, 3, ce("camshaft"), axis("x")),
    # Redstone for the wastegate, kept out of sight until the scene shows it
    (1, 2, 3, "minecraft:redstone_block", {}),
])

write_schematic("supercharger", (7, 6, 7), [
    (1, 1, 3, ce("crankshaft"), axis("x")),
    (2, 1, 3, ce("crank_throw"), axis("x")),
    (3, 1, 3, ce("crankshaft"), axis("x")),
    (2, 2, 3, ce("cylinder"), cylinder("up")),
    (2, 3, 3, ce("cylinder_head"), head("up")),
    (1, 3, 3, ce("exhaust_outlet"), facing("west")),
    # The blower is bolted to the head's north face, with its shaft along X, and an intercooler and the air intake
    # in line beyond it
    (2, 3, 2, ce("supercharger"), axis("x")),
    (2, 3, 1, ce("intercooler"), axis("z")),
    (2, 3, 0, ce("air_intake"), facing("north")),
    (3, 3, 2, "create:shaft", {"axis": "x", "waterlogged": "false"}),
    (4, 3, 2, "create:creative_motor", facing("west")),
    (2, 4, 3, ce("cam_lobe"), axis("x")),
    (1, 4, 3, ce("camshaft"), axis("x")),
    (3, 4, 3, ce("camshaft"), axis("x")),
])

write_schematic("transmission", (8, 4, 5), [
    (0, 1, 2, "create:creative_motor", facing("east")),
    (1, 1, 2, "create:shaft", {"axis": "x", "waterlogged": "false"}),
    (2, 1, 2, ce("transmission"), axis("x")),
    (3, 1, 2, "create:shaft", {"axis": "x", "waterlogged": "false"}),
    (4, 1, 2, "create:shaft", {"axis": "x", "waterlogged": "false"}),
    (5, 1, 2, "create:flywheel", {"axis": "x", "waterlogged": "false"}),
    # A lever beside the transmission selects a gear by redstone strength
    (2, 1, 3, "minecraft:lever", {"face": "floor", "facing": "north", "powered": "false"}),
])

write_schematic("sump", (6, 6, 6), [
    (1, 2, 3, ce("crankshaft"), axis("x")),
    (2, 2, 3, ce("crank_throw"), axis("x")),
    (3, 2, 3, ce("crankshaft"), axis("x")),
    (2, 3, 3, ce("cylinder"), cylinder("up")),
    (2, 4, 3, ce("cylinder_head"), head("up")),
    (3, 1, 3, ce("sump"), {}),
    (4, 1, 3, "create:fluid_pipe", {"east": "true", "west": "true", "north": "false", "south": "false",
                                    "up": "false", "down": "false", "waterlogged": "false"}),
    (5, 1, 3, "create:fluid_tank", {}),
])

# Four cylinders in a row, with a cam lobe over each head, for setting the firing order
firing_order = [
    (1, 1, 3, ce("crankshaft"), axis("x")),
    (6, 1, 3, ce("crankshaft"), axis("x")),
    (1, 4, 3, ce("camshaft"), axis("x")),
    (6, 4, 3, ce("camshaft"), axis("x")),
]
for x in range(2, 6):
    firing_order += [
        (x, 1, 3, ce("crank_throw"), axis("x")),
        (x, 2, 3, ce("cylinder"), cylinder("up")),
        (x, 3, 3, ce("cylinder_head"), head("up")),
        (x, 3, 2, ce("air_intake"), facing("north")),
        (x, 3, 4, ce("exhaust_outlet"), facing("south")),
        (x, 4, 3, ce("cam_lobe"), axis("x")),
    ]
# A Create flywheel on the crank's axis, which the firing order page brings in to show inertia (other pages leave it hidden)
firing_order.append((7, 1, 3, "create:flywheel", {"axis": "x", "waterlogged": "false"}))
write_schematic("firing_order", (8, 5, 8), firing_order)

# A small V: one bank bored up and one bored north from the same two throws. Each bank's cam sits against its
# cylinders, so the heads keep their faces for the intake and exhaust
compact_v = [
    (1, 2, 3, ce("crankshaft"), axis("x")),
    (4, 2, 3, ce("crankshaft"), axis("x")),
    (1, 3, 4, ce("camshaft"), axis("x")),
    (4, 3, 4, ce("camshaft"), axis("x")),
    (1, 1, 2, ce("camshaft"), axis("x")),
    (4, 1, 2, ce("camshaft"), axis("x")),
]
for x in range(2, 4):
    compact_v += [
        (x, 2, 3, ce("crank_throw"), axis("x")),
        # Upright bank
        (x, 3, 3, ce("cylinder"), cylinder("up")),
        (x, 4, 3, ce("cylinder_head"), head("up")),
        (x, 4, 2, ce("air_intake"), facing("north")),
        (x, 4, 4, ce("exhaust_outlet"), facing("south")),
        (x, 3, 4, ce("cam_lobe"), axis("x")),
        # Bank bored north
        (x, 2, 2, ce("cylinder"), cylinder("north")),
        (x, 2, 1, ce("cylinder_head"), head("north")),
        (x, 3, 1, ce("air_intake"), facing("up")),
        (x, 1, 1, ce("exhaust_outlet"), facing("down")),
        (x, 1, 2, ce("cam_lobe"), axis("x")),
    ]
write_schematic("compact_v", (6, 6, 6), compact_v)


# A MAF sensor in the intake run, with a lamp beside it to show its redstone output
write_schematic("maf_sensor", (6, 6, 6), [
    (1, 1, 3, ce("crankshaft"), axis("x")),
    (2, 1, 3, ce("crank_throw"), axis("x")),
    (3, 1, 3, ce("crankshaft"), axis("x")),
    (2, 2, 3, ce("cylinder"), cylinder("up")),
    (2, 3, 3, ce("cylinder_head"), head("up")),
    (2, 3, 2, ce("maf_sensor"), pipe("north", "south")),
    (2, 3, 1, ce("air_intake"), facing("north")),
    (2, 3, 4, ce("exhaust_outlet"), facing("south")),
    (2, 4, 3, ce("cam_lobe"), axis("x")),
    (1, 4, 3, ce("camshaft"), axis("x")),
    (3, 4, 3, ce("camshaft"), axis("x")),
    (3, 3, 2, "minecraft:redstone_lamp", {"lit": "false"}),
])

# A lambda sensor in the exhaust run, with a lamp beside it
write_schematic("lambda_sensor", (6, 6, 6), [
    (1, 1, 3, ce("crankshaft"), axis("x")),
    (2, 1, 3, ce("crank_throw"), axis("x")),
    (3, 1, 3, ce("crankshaft"), axis("x")),
    (2, 2, 3, ce("cylinder"), cylinder("up")),
    (2, 3, 3, ce("cylinder_head"), head("up")),
    (2, 3, 2, ce("air_intake"), facing("north")),
    (2, 3, 4, ce("lambda_sensor"), pipe("north", "south")),
    (2, 3, 5, ce("exhaust_outlet"), facing("south")),
    (2, 4, 3, ce("cam_lobe"), axis("x")),
    (1, 4, 3, ce("camshaft"), axis("x")),
    (3, 4, 3, ce("camshaft"), axis("x")),
    (3, 3, 4, "minecraft:redstone_lamp", {"lit": "false"}),
])

# A rev limiter against the crankshaft, with a lamp beside it
write_schematic("rev_limiter", (6, 6, 6), [
    (1, 1, 3, ce("crankshaft"), axis("x")),
    (2, 1, 3, ce("crank_throw"), axis("x")),
    (3, 1, 3, ce("crankshaft"), axis("x")),
    (2, 2, 3, ce("cylinder"), cylinder("up")),
    (2, 3, 3, ce("cylinder_head"), head("up")),
    (2, 3, 2, ce("air_intake"), facing("north")),
    (2, 3, 4, ce("exhaust_outlet"), facing("south")),
    (2, 4, 3, ce("cam_lobe"), axis("x")),
    (1, 4, 3, ce("camshaft"), axis("x")),
    (3, 4, 3, ce("camshaft"), axis("x")),
    (3, 2, 3, ce("rev_limiter"), {}),
    (4, 2, 3, "minecraft:redstone_lamp", {"lit": "false"}),
])

# A governor against the crankshaft, with a lamp beside it
write_schematic("governor", (6, 6, 6), [
    (1, 1, 3, ce("crankshaft"), axis("x")),
    (2, 1, 3, ce("crank_throw"), axis("x")),
    (3, 1, 3, ce("crankshaft"), axis("x")),
    (2, 2, 3, ce("cylinder"), cylinder("up")),
    (2, 3, 3, ce("cylinder_head"), head("up")),
    (2, 3, 2, ce("air_intake"), facing("north")),
    (2, 3, 4, ce("exhaust_outlet"), facing("south")),
    (2, 4, 3, ce("cam_lobe"), axis("x")),
    (1, 4, 3, ce("camshaft"), axis("x")),
    (3, 4, 3, ce("camshaft"), axis("x")),
    (3, 2, 3, ce("governor"), {}),
    (4, 2, 3, "minecraft:redstone_lamp", {"lit": "false"}),
])

# The same four cylinders as the firing order page, for setting them with the timing tool
write_schematic("timing_tool", (8, 5, 8), firing_order)

# Two crank lines side by side, each with a cylinder under its own overhead cam, geared together by two cogwheels
write_schematic("coupled_cranks", (6, 6, 6), [
    (1, 1, 2, ce("crankshaft"), axis("x")),
    (2, 1, 2, ce("crank_throw"), axis("x")),
    (3, 1, 2, ce("crankshaft"), axis("x")),
    (1, 1, 3, ce("crankshaft"), axis("x")),
    (2, 1, 3, ce("crankshaft"), axis("x")),
    (3, 1, 3, ce("crank_throw"), axis("x")),
    (4, 1, 3, ce("crankshaft"), axis("x")),
    (0, 1, 2, "create:cogwheel", {"axis": "x", "waterlogged": "false"}),
    (0, 1, 3, "create:cogwheel", {"axis": "x", "waterlogged": "false"}),
    (2, 2, 2, ce("cylinder"), cylinder("up")),
    (2, 3, 2, ce("cylinder_head"), head("up")),
    (2, 3, 1, ce("air_intake"), facing("north")),
    (1, 3, 2, ce("exhaust_outlet"), facing("west")),
    (2, 4, 2, ce("cam_lobe"), axis("x")),
    (1, 4, 2, ce("camshaft"), axis("x")),
    (3, 4, 2, ce("camshaft"), axis("x")),
    (3, 2, 3, ce("cylinder"), cylinder("up")),
    (3, 3, 3, ce("cylinder_head"), head("up")),
    (3, 3, 4, ce("air_intake"), facing("south")),
    (4, 3, 3, ce("exhaust_outlet"), facing("east")),
    (3, 4, 3, ce("cam_lobe"), axis("x")),
    (2, 4, 3, ce("camshaft"), axis("x")),
    (4, 4, 3, ce("camshaft"), axis("x")),
])

# One cylinder built entirely from precision parts
write_schematic("precision_parts", (5, 6, 5), [
    (1, 1, 2, ce("crankshaft"), axis("x")),
    (2, 1, 2, ce("crank_throw"), axis("x")),
    (3, 1, 2, ce("crankshaft"), axis("x")),
    (2, 2, 2, ce("precision_cylinder"),
     {"facing": "up", "piston": "true", "forged_piston": "true", "connecting_rod": "true"}),
    (2, 3, 2, ce("precision_cylinder_head"), {"facing": "up", "injector": "true", "fine_injector": "true"}),
    (2, 3, 1, ce("air_intake"), facing("north")),
    (2, 3, 3, ce("exhaust_outlet"), facing("south")),
    (2, 4, 2, ce("cam_lobe"), axis("x")),
    (1, 4, 2, ce("camshaft"), axis("x")),
    (3, 4, 2, ce("camshaft"), axis("x")),
])
