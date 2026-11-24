"""Takes the mod page's gallery pictures again, with shaders.

    python tools/gallery.py [--no-copy] [--apply] [--sets engines,accessories]

1. Copies the project to a private folder beside it (create-engines-gallery), as record.py does, and runs
   `gradlew runShowcaseShaders -PshowcaseSet=<set>` there for each set: the game with Iris, Sodium and the shader pack in
   tools/trailer/shaderpacks (the same pack as the trailer), photographing every engine from every angle at 1920x1080.
2. Copies every still to run/gallery/all, and the ones tools/gallery_shots.txt names to run/gallery/pick, under the gallery's
   own file names, so they can be looked at next to the pictures in docs/images.
3. With --apply the picks replace the files in docs/images. Nothing else in docs/images is touched (the Ponder, goggle,
   management and other pictures come from other runs). The git diff of the images is the review.

Do not run it while the trailer is being recorded or anything else is using the screen: it opens a real window for a few minutes.
"""
import argparse
import shutil
from PIL import Image

# The sites take gallery images up to 2 MB; the limit here is 2,000,000 bytes, so it holds whichever megabyte is meant.
# GalleryImageSizeTest keeps docs/images under it.
LIMIT = 2_000_000


def fit(source, dest):
    """Copies a still to dest under LIMIT bytes: as it is when it fits, else an optimised PNG, else a 256-colour one, else that
    one a tenth smaller at a time (a shaded scene has few enough colours that the palette is rarely noticed)."""
    shutil.copy(source, dest)
    if dest.stat().st_size < LIMIT:
        return
    image = Image.open(source).convert("RGB")
    image.save(dest, optimize=True)
    scale = 1.0
    while dest.stat().st_size >= LIMIT:
        picture = image if scale == 1.0 else image.resize((round(image.width * scale), round(image.height * scale)), Image.LANCZOS)
        picture.quantize(256, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.FLOYDSTEINBERG).save(dest, optimize=True)
        scale -= 0.1
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PRIVATE = ROOT.parent / (ROOT.name + "-gallery")
OUT = ROOT / "run" / "gallery"
RUN_DIR = Path("run") / "showcase-shaders"
SHOTS = ROOT / "tools" / "gallery_shots.txt"


def sync():
    # robocopy returns 0-7 for success; /XD also protects the private copy's own run, build and .gradle from /MIR
    result = subprocess.run(["robocopy", str(ROOT), str(PRIVATE), "/MIR", "/XD", "run", "build", ".gradle", ".git", "/NFL",
                             "/NDL", "/NJH", "/NJS", "/NP"])
    if result.returncode >= 8:
        sys.exit(f"robocopy failed ({result.returncode})")


def play(shot_set):
    screenshots = PRIVATE / RUN_DIR / "screenshots"
    shutil.rmtree(screenshots, ignore_errors=True)
    result = subprocess.run([str(PRIVATE / "gradlew.bat"), "runShowcaseShaders", f"-PshowcaseSet={shot_set}", "--offline",
                             "--console=plain"], cwd=PRIVATE)
    if result.returncode != 0:
        sys.exit(f"the {shot_set} run failed ({result.returncode}); see {PRIVATE / RUN_DIR / 'logs'}")
    return sorted(screenshots.glob("showcase_*.png"))


def picks():
    """gallery file -> still name (without the _a/_b), one per line of tools/gallery_shots.txt."""
    table = {}
    for line in SHOTS.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#"):
            gallery, still = line.split()
            table[gallery] = still
    return table


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--no-copy", action="store_true", help="run in the private copy as it is")
    parser.add_argument("--apply", action="store_true", help="replace the pictures in docs/images with the picks")
    parser.add_argument("--sets", default="engines,accessories")
    parser.add_argument("--picks-only", action="store_true", help="pick again from run/gallery/all without playing the game")
    args = parser.parse_args()

    everything = OUT / "all"
    chosen = OUT / "pick"
    stills = {}
    if args.picks_only:
        shutil.rmtree(chosen, ignore_errors=True)
        chosen.mkdir(parents=True)
        stills = {still.stem: still for still in everything.glob("showcase_*.png")}
    else:
        if not args.no_copy:
            sync()
        if not list((PRIVATE / "tools" / "trailer" / "shaderpacks").glob("*.zip")):
            sys.exit("no shader pack in tools/trailer/shaderpacks (see the README there)")
        shutil.rmtree(OUT, ignore_errors=True)
        everything.mkdir(parents=True)
        chosen.mkdir()
        for shot_set in args.sets.split(","):
            for still in play(shot_set):
                shutil.copy(still, everything / still.name)
                stills[still.stem] = everything / still.name  # the copy: the next set clears the folder this one came from
    print(f"{len(stills)} stills in {everything}")

    missing = []
    for gallery, name in picks().items():
        # _b is the later of the two pictures of a view, once the camera and the smoke have settled
        source = stills.get(f"showcase_{name}_b") or stills.get(f"showcase_{name}_a")
        if source is None:
            missing.append(f"{gallery} <- {name}")
            continue
        fit(source, chosen / gallery)
        if args.apply:
            shutil.copy(chosen / gallery, ROOT / "docs" / "images" / gallery)
    if missing:
        print("no still for: " + ", ".join(missing))
    print(f"picks in {chosen}" + (", copied to docs/images" if args.apply else " (--apply puts them in docs/images)"))


if __name__ == "__main__":
    main()
