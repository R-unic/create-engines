"""Writes the block models of the engine-cased shaft and cogwheels: Create's encased models with this mod's textures, and
with every face that shows the casing marked as tinted, so that a dye on the casing reaches them (tint index 0).

Run from the repository root with Create's jar:
    python tools/derive_cased_models.py path/to/create-1.21.1-6.0.10-281-slim.jar
A child model cannot add a tint index to its parent's faces, which is why these are copies and not children. The result is
committed, so this only needs running again if Create's models change in a newer Create.
"""
import json
import os
import sys
import zipfile

OUT = "src/main/resources/assets/create_engines/models/block"
CASING = "create_engines:block/engine_casing"
DARK = "create_engines:block/black_oxide"
TINTED = ("#casing", "#side")


def derive(archive, create_name, out_name, textures):
    model = json.loads(archive.read("assets/create/models/block/%s.json" % create_name))
    result = {"textures": textures, "elements": model["elements"]}
    if "parent" in model:
        result["parent"] = model["parent"]
    for element in model["elements"]:
        for face in element["faces"].values():
            if face["texture"] in TINTED:
                face["tintindex"] = 0
    path = os.path.join(OUT, out_name + ".json")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n") as fh:
        json.dump(result, fh, indent=2)
        fh.write("\n")
    print("wrote", path)


def main(jar):
    archive = zipfile.ZipFile(jar)
    derive(archive, "encased_shaft/block", "engine_cased_shaft/block",
           {"casing": CASING, "opening": DARK, "particle": CASING, "1": "#opening"})
    for name, side in (("engine_cased_cogwheel", "create_engines:block/engine_cased_cogwheel_side"),
                       ("engine_cased_large_cogwheel", "create_engines:block/engine_cased_cogwheel_side_connected")):
        create = "encased_cogwheel" if name == "engine_cased_cogwheel" else "encased_large_cogwheel"
        textures = {"casing": CASING, "particle": CASING, "side": side, "4": DARK, "1": "block/stripped_spruce_log_top"}
        for suffix in ("", "_top", "_bottom", "_top_bottom"):
            derive(archive, "%s/block%s" % (create, suffix), name + "/block" + suffix, dict(textures))


main(sys.argv[1])
