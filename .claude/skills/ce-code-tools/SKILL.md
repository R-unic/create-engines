---
name: ce-code-tools
description: Cheap ways to read, look up and edit code in Create Engines. Use before reading a large file here, before digging into a Create, NeoForge or Minecraft jar (javap, unzip, find in .gradle), and before writing a patch, edit or generator script.
---

# Reading, looking up and editing

## Large files: outline first

`python tools/ce_outline.py EngineController` prints every method with its line range. `-m NAME` picks methods, `-g REGEX`
shows which methods hold a match. It also takes Markdown (`docs/ROADMAP.md`, `docs/ROADMAP-3.0.md`, `docs/DESIGN.md`) and
prints headings with ranges. Then use Read with `offset` and `limit`. Read a whole file only when most of it matters.

The costly ones: `EngineController.java`, `EngineScanner.java`, `EngineSimulation.java`, `CylinderModel.java`,
`EngineGameTests.java`, `ShowcaseRun.java`, `tools/generate_models.py` and the docs above.

## Library code: `tools/ce_lib.py`

Never search `~/.gradle` with `find` (it takes minutes) and never unzip jars by hand.

    python tools/ce_lib.py find SpriteShift              class names
    python tools/ce_lib.py api KineticBlockEntity -g speed
    python tools/ce_lib.py code KineticBlockEntity -m tick       bytecode, for Create (no source)
    python tools/ce_lib.py src BlockEntity -m setChanged         Minecraft and NeoForge source
    python tools/ce_lib.py res "create/models/block/shaft" --cat
    python tools/ce_lib.py api SomeClass --jar sable             an optional mod's jar

## Edits

- One or two changes: use Edit.
- Many changes, or several files: write a patch file to the scratchpad with Write, then run
  `python tools/ce_patch.py <file>` (format in `--help`). It keeps CRLF, writes nothing unless every block matches, shows
  the nearest line when one does not, and can be rerun. Do not write a new `patch.py`, `edit.py` or heredoc Python script.
- Never put a heredoc containing apostrophes in the Bash tool: it fails to parse.
- Source uses tabs. Check the real diff with `rtk proxy git diff` (the shell hook filters plain `git diff`).
  `git status` can list generated JSON that differs only in line endings.

## Generators (they overwrite hand edits: change the generator, then rerun it)

| Script | Notes |
|---|---|
| `tools/generate_models.py` | block models; run from the repo root; safe to rerun |
| `tools/generate_textures.py` | run with `/c/Python315/python.exe` (another zlib rewrites every PNG's bytes) |
| `tools/generate_sounds.py` | run with `/c/Python312/python.exe` (numpy, scipy, Pillow); `--wav DIR name...` previews |
| `tools/ponder_schematics.py` | rewrites every `.nbt` when run: to add one, exec the helpers plus only the new tail from a scratchpad script |
| `tools/preview_models.py` | `--scale` is the cell size in pixels |

Check what a generator will touch before running it: restoring with `git checkout` is blocked by auto mode.

## Shell

Robocopy exit codes 0-7 mean success. A `cd` in Bash carries to later calls: use absolute paths.
