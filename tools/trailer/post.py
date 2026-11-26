"""Cuts the trailer from the recordings: the shots in the order and at the lengths of edit.txt, graded and letterboxed, a
caption under each with its chapter's name over it, a flash and a hit where a chapter starts, the title over the title shot,
an end card, and the game's sound mixed with the music, which drops when an engine is the point of a shot.

    python tools/trailer/post.py --raw RAW.mp4 --shots trailer_shots.json --offset SECONDS --out trailer.mp4
        [--cut full|teaser|vertical] [--audio GAME.wav --audio-offset SECONDS] [--music FILE] [--no-music]
        [--thumbnail thumbnail.png]   (with --cut full: the 16:9 still; with --cut vertical: the 9:16 one for Shorts)

RAW.mp4 is the screen recording and --offset is how many seconds into it the first shot began (record.py measures that, and
the same for the recording of the game's sound, GAME.wav). The words are in tools/trailer/captions.txt, the edit in
tools/trailer/edit.txt, and the words are drawn in Minecraft's own font (mcfont.py). The music is, in order: --music, the
first file in tools/trailer/music/, or a backing track that tools/trailer/music.py makes; the hits come from sfx.py.

--cut full is the whole trailer. teaser is a title card, four three-second shots (the `short_shots` of captions.txt) with a
few big words each, and an end card. vertical is the 9:16 cut for Shorts, with its own edit (tools/trailer/vertical.txt): no
title card, words from the first frame, a cut every one or two seconds, a small AI tag at the top throughout, and an end piece
over footage with the title, the links, the AI notice and where to read how AI is used (docs/AI.md); it loops. Every piece
lasts a whole number of half bars of the music (80 beats a minute), so the cuts fall on the beat. A .srt of the captions is
written beside the video; the full cut also writes the video description, and the vertical cut the Shorts description.

How it is made: every piece of the cut is rendered on its own (its stretch of the recording, sped up if edit.txt says so,
graded, letterboxed, with its flash, caption and fades), the pieces are joined, and the sound is mixed in numpy and brought to
YouTube's loudness (-14 LUFS, true peak -2 dB, which leaves the AAC encoder room) by ffmpeg's loudnorm in two passes.

Needs ffmpeg (winget install Gyan.FFmpeg), Pillow and numpy.
"""
import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import wave
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageEnhance, ImageFilter

import mcfont
import sfx

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import create_lettering  # noqa: E402  (the "CREATE" lettering above the wordmark)

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
LOGO = ROOT / "docs" / "images" / "icon.png"
WORDMARK = ROOT / "docs" / "images" / "wordmark.png"  # the gold block-letter title, 2000x650 on transparent
BAR = 3.0  # seconds of one bar at the music's tempo (music.py: 80 beats a minute)
FPS = 60
RATE = sfx.RATE
BACKGROUND = (14, 14, 17, 255)
COPPER = (184, 115, 51, 255)
GOLD = (255, 170, 0)  # Minecraft's gold
WHITE = (255, 255, 255)
GREY = (170, 170, 170)
INK = (20, 12, 8, 255)  # the dark outline of the big words
TITLE_SHOT = "title_sequence"  # the shot the title lies over
END_CARD = 2 * BAR  # seconds the end card holds in the full cut
SHORT_SHOT = BAR  # seconds of each teaser shot
# A light touch, close to the game's own look: a slight S-curve, a hint of warm highlights and cool shadows, a little more
# colour and edge, and a faint vignette (a stronger grade was tried and judged too much)
GRADE = ("curves=master='0/0 0.25/0.235 0.75/0.765 1/1',colorbalance=rs=-0.01:bs=0.015:rh=0.02:bh=-0.02,"
         "eq=saturation=1.05,unsharp=5:5:0.2,vignette=angle=PI/8")
LETTERBOX = 104  # pixels of black at the top and the bottom of the wide cut: 2.2:1, which also hides the hotbar's row

# What differs between the wide and the tall cut: the picture, the cards' layout and the words' sizes
LAYOUTS = {
    "wide": dict(size=(1920, 1080), icon=120, icon_y=95, create=520, create_y=225, mark=1040, mark_y=372, rule_y=752, sub=5, sub_y=787,
                 links=4, links_y=845, ver=3, ver_y=900, notice=3, notice_y=955, lone=65,
                 caption=4, kicker=3, caption_x=110, caption_gap=28, letterbox=LETTERBOX, big=9, big_gap=56),
    # The tall cut keeps every word above SAFE_BOTTOM, and its end piece's smallest words are four screen pixels to a font pixel:
    # smaller ones were too hard to read on a phone
    "tall": dict(size=(1080, 1920), icon=130, icon_y=140, create=540, create_y=280, mark=780, mark_y=432, rule_y=735, sub=6, sub_y=768,
                 links=5, links_y=845, ver=4, ver_y=918, lone=0, notice=4, notice_y=1040, docs=4,
                 caption=3, kicker=2, caption_x=70, caption_gap=330, letterbox=0, big=8, big_gap=560),
}
# Shorts and TikTok draw the description, the sound's name and their buttons over the bottom of the video: no word of the tall
# cut may reach below this (of 1920), and rendering stops if one would
SAFE_BOTTOM = 1380
# The big words: screen pixels to a font pixel for the small white line and for the big gold one (tall cut; shrunk to fit)
BIG_SETUP, BIG_KEY = 10, 17
GOLD_TOP, GOLD_BOTTOM = (255, 232, 96), (255, 146, 20)  # the gold line's shading, top to bottom
# The vertical cut: where a `fit` piece's 16:9 band lies (1080x608, a little above the middle), the margins its words keep
# clear of the app's buttons down the right edge, and where the AI tag sits (top left, where a Shorts player draws nothing)
FIT_Y = 420
TALL_MARGIN = 130
TAG_AT = (56, 200)


def find_ffmpeg():
    found = shutil.which("ffmpeg")
    if found:
        return found
    packages = Path(os.environ.get("LOCALAPPDATA", "")) / "Microsoft" / "WinGet" / "Packages"
    for candidate in sorted(packages.glob("Gyan.FFmpeg*/**/bin/ffmpeg.exe")):
        return str(candidate)
    sys.exit("ffmpeg not found: winget install Gyan.FFmpeg")


def read_captions():
    words = {"captions": {}, "short_captions": {}, "chapters": {}}
    for line in (HERE / "captions.txt").read_text(encoding="utf-8").splitlines():
        if not line.strip() or line.startswith("#"):
            continue
        key, _, text = line.partition(":")
        key, text = key.strip(), text.strip()
        if key.startswith("caption "):
            words["captions"][key[len("caption "):]] = text
        elif key.startswith("short_caption "):
            words["short_captions"][key[len("short_caption "):]] = text
        elif key.startswith("chapter "):
            words["chapters"][key[len("chapter "):]] = text
        else:
            words[key] = text
    return words


def read_edit(file="edit.txt"):
    """The lines of an edit (edit.txt, or vertical.txt): shot, seconds, its options, and for the vertical cut the words after
    a `|`. edit.txt takes at, speed, engine, quiet and cold; vertical.txt takes at, speed, x, zoom, fit, flash, engine, cold and end."""
    vertical = file == "vertical.txt"
    numbers = ("at", "speed", "x", "zoom") if vertical else ("at", "speed")
    flags = ("fit", "flash", "engine", "cold", "end") if vertical else ("engine", "quiet", "cold")
    lines = []
    for number, line in enumerate((HERE / file).read_text(encoding="utf-8").splitlines(), 1):
        line = line.split("#", 1)[0].strip()
        if not line:
            continue
        line, _, said = line.partition("|")
        name, seconds, *options = line.split()
        entry = dict(name=name, dur=float(seconds), at=None, speed=1.0, x=0.5, zoom=1.0, words=said.strip() or None,
                     **{flag: False for flag in ("engine", "quiet", "cold", "fit", "flash", "end")})
        for option in options:
            key, _, value = option.partition("=")
            if key in numbers and value:
                entry[key] = float(value)
            elif key in flags and not value:
                entry[key] = True
            else:
                sys.exit(f"{file} line {number}: unknown option {option!r}")
        if abs(entry["dur"] / (BAR / 2) - round(entry["dur"] / (BAR / 2))) > 1e-6:
            sys.exit(f"{file} line {number}: {seconds} s is not a whole number of half bars ({BAR / 2} s)")
        if not 0 <= entry["x"] <= 1:
            sys.exit(f"{file} line {number}: x={entry['x']} is not between 0 and 1")
        if not 1 <= entry["zoom"] <= 3:
            sys.exit(f"{file} line {number}: zoom={entry['zoom']} is not between 1 and 3")
        lines.append(entry)
    return lines


def versions():
    """The line on the end card, from gradle.properties: the mod, Minecraft, NeoForge and Create versions."""
    props = {}
    for line in (ROOT / "gradle.properties").read_text(encoding="utf-8").splitlines():
        if "=" in line and not line.startswith("#"):
            key, _, value = line.partition("=")
            props[key.strip()] = value.strip()
    create = props.get("create_version", "").split("-")[0]
    return f"v{props.get('mod_version', '?')}  |  Minecraft {props.get('minecraft_version', '?')}  |  NeoForge  |  Create {create}"


def pick_music(args, length, work):
    if args.no_music:
        return None
    if args.music:
        return Path(args.music)
    folder = HERE / "music"
    if folder.is_dir():
        for file in sorted(folder.iterdir()):
            if file.suffix.lower() in (".mp3", ".wav", ".ogg", ".flac", ".m4a"):
                return file
    out = work / "music_synth.wav"
    subprocess.run([sys.executable, str(HERE / "music.py"), str(out), f"{length + 2:.2f}"], check=True)
    return out


# ---------------------------------------------------------------------------------------------------------------- pictures

def centred(image, layer, y):
    image.alpha_composite(layer, ((image.width - layer.width) // 2, y))


def wrapped(text, scale, limit):
    """Splits a line into the fewest lines that each fit in `limit` pixels."""
    lines, line = [], ""
    for word in text.split():
        trial = f"{line} {word}".strip()
        if mcfont.width(trial) * scale > limit and line:
            lines.append(line)
            line = word
        else:
            line = trial
    return lines + [line]


def card_image(title, lines, layout, path, footer=None, notice=None, links=None, overlay=False, docs=None, shade=110):
    """The mod's icon, the "CREATE" lettering and the wordmark under it (together the title), a copper rule, and
    the lines under it, in Minecraft's font on the dark card. An overlay is the same title on clear glass with a shade (`shade`
    of 255), to lie over footage. `docs` is a line under the AI notice saying where to read how AI is used."""
    lay = LAYOUTS[layout]
    image = Image.new("RGBA", lay["size"], (0, 0, 0, shade) if overlay else BACKGROUND)
    dy = lay["lone"] if not (footer or notice) else 0  # a title card has no lines at the foot, so it sits lower
    mark = Image.open(WORDMARK).convert("RGBA")
    mark = mark.resize((lay["mark"], round(mark.height * lay["mark"] / mark.width)), Image.LANCZOS)
    centred(image, mark, lay["mark_y"] + dy)
    create_lettering.place(image, lay["create"], lay["create_y"] + dy)
    icon = Image.open(LOGO).convert("RGBA").resize((lay["icon"], lay["icon"]), Image.LANCZOS)
    centred(image, icon, lay["icon_y"] + dy)
    draw = ImageDraw.Draw(image)
    half = image.width // 8
    draw.rectangle((image.width // 2 - half, lay["rule_y"] + dy, image.width // 2 + half, lay["rule_y"] + dy + 5), fill=COPPER)
    if lines:
        centred(image, mcfont.render(lines, lay["sub"], GOLD), lay["sub_y"] + dy)
    if links:
        centred(image, mcfont.render(links, lay["links"], WHITE), lay["links_y"])
    if footer:
        # Too long for one line of the tall card at a readable size: then two lines of two parts each
        parts, y = footer.split("  |  "), lay["ver_y"]
        too_wide = mcfont.width(footer) * lay["ver"] > image.width - 100
        for row in (["  |  ".join(parts[:2]), "  |  ".join(parts[2:])] if too_wide else [footer]):
            layer = mcfont.render(row, lay["ver"], WHITE if overlay else GREY)
            centred(image, layer, y)
            y += layer.height + lay["ver"] * 2
    if notice:
        # The AI notice: small, near the foot of the card, wrapped to the card's width
        scale, y = lay["notice"], lay["notice_y"]
        limit = lay["size"][0] - (180 if layout == "tall" else 200)
        for row in wrapped(notice, scale, limit):
            layer = mcfont.render(row, scale, WHITE if overlay else GREY)
            centred(image, layer, y)
            y += layer.height + scale * 2
        if docs:
            scale = lay.get("docs", lay["notice"])
            y += scale * 4
            for row in wrapped(docs, scale, limit):
                layer = mcfont.render(row, scale, GOLD)
                centred(image, layer, y)
                y += layer.height + scale * 2
        if layout == "tall" and y > SAFE_BOTTOM:
            sys.exit(f"the end card's words reach y {y}, below {SAFE_BOTTOM} where the app draws its description")
    (image if overlay else image.convert("RGB")).save(path)


def ai_tag(text, path):
    """The small line that stays at the top of the vertical cut from its first frame: the AI notice, in short, for a viewer
    who swipes away before the end piece. White in Minecraft's font with a soft shadow, so it reads on the sky."""
    layer = mcfont.render(text, 4, WHITE)
    image, pad = soft_shadow(layer, reach=8, strength=170)
    image.save(path)
    return pad


def soft_shadow(layer, reach=10, strength=150):
    """A blurred dark halo under a layer of text, so it reads on a bright sky without a box behind it."""
    pad = reach * 2
    out = Image.new("RGBA", (layer.width + 2 * pad, layer.height + 2 * pad), (0, 0, 0, 0))
    mask = Image.new("L", out.size, 0)
    mask.paste(layer.getchannel("A"), (pad, pad))
    mask = mask.filter(ImageFilter.MaxFilter(5)).filter(ImageFilter.GaussianBlur(reach))
    mask = mask.point(lambda v: min(255, v * strength // 100))
    out.paste(Image.new("RGBA", out.size, (0, 0, 0, 255)), (0, 0), mask)
    out.alpha_composite(layer, (pad, pad))
    return out, pad


def lower_third(kicker, text, layout, path):
    """The caption: the chapter's name small in gold, the line under it in white, a copper bar down their left side. Returns
    the offset of the text inside the picture (its soft shadow reaches past it)."""
    lay = LAYOUTS[layout]
    rows = [mcfont.render(row, lay["caption"], WHITE) for row in wrapped(text, lay["caption"], lay["size"][0] - 2 * lay["caption_x"] - 40)]
    head = mcfont.render(kicker.upper(), lay["kicker"], GOLD) if kicker else None
    gap = lay["caption"] * 3
    width = max([r.width for r in rows] + ([head.width] if head else []))
    height = sum(r.height for r in rows) + ((head.height + gap) if head else 0)
    bar = 6
    block = Image.new("RGBA", (bar + 22 + width, height), (0, 0, 0, 0))
    ImageDraw.Draw(block).rectangle((0, 0, bar - 1, height - 1), fill=COPPER)
    y = 0
    if head:
        block.alpha_composite(head, (bar + 22, y))
        y += head.height + gap
    for row in rows:
        block.alpha_composite(row, (bar + 22, y))
        y += row.height
    image, pad = soft_shadow(block)
    image.save(path)
    return pad, image.size


def outlined(text, scale, color, outline, reach, drop):
    """Big blocky text with a thick dark outline and a hard offset drop, so it reads at the size of a phone's shelf thumbnail."""
    glyphs = mcfont.render(text, scale, color, shadow=False)
    mask = glyphs.getchannel("A")
    grown = mask.filter(ImageFilter.MaxFilter(2 * reach + 1))
    out = Image.new("RGBA", (glyphs.width + 2 * reach + drop, glyphs.height + 2 * reach + drop), (0, 0, 0, 0))
    out.paste(Image.new("RGBA", grown.size, (0, 0, 0, 255)), (drop, drop), grown)
    out.paste(Image.new("RGBA", grown.size, outline), (0, 0), grown)
    out.alpha_composite(glyphs, (reach, reach))
    return out


def shaded(layer, colour, top, bottom):
    """Recolours the pixels of `layer` that are exactly `colour` with a vertical gradient from `top` to `bottom`."""
    pixels = np.array(layer)
    mine = np.all(pixels[:, :, :3] == colour, axis=2) & (pixels[:, :, 3] > 0)
    rows = np.nonzero(mine.any(axis=1))[0]
    if len(rows):
        t = np.clip((np.arange(pixels.shape[0]) - rows[0]) / max(1, rows[-1] - rows[0]), 0, 1)[:, None]
        ramp = (np.array(top) * (1 - t) + np.array(bottom) * t).astype(np.uint8)
        pixels[:, :, :3] = np.where(mine[:, :, None], ramp[:, None, :], pixels[:, :, :3])
    return Image.fromarray(pixels, "RGBA")


def big_words(text, layout, path):
    """The few big words over a piece, on two lines: the setup in white, then the point of it much bigger in shaded gold, both
    with a thick dark outline and a soft halo. A `/` in the words says where the gold line starts; without one it is the last
    word. Returns the picture's size."""
    lay = LAYOUTS[layout]
    room = lay["size"][0] - (2 * TALL_MARGIN if layout == "tall" else 140)
    setup, slash, key = text.partition("/")
    if not slash:
        setup, _, key = text.rpartition(" ")
    rows = []
    for words, base, colour in ((setup, BIG_SETUP, WHITE), (key, BIG_KEY, GOLD)):
        words = words.strip().upper()
        if not words:
            continue
        scale = base if layout == "tall" else base * lay["big"] // BIG_SETUP
        while scale > 4 and mcfont.width(words) * scale + 3 * scale > room:
            scale -= 1
        reach = max(4, scale * 3 // 5)
        row = outlined(words, scale, colour, INK, reach, reach)
        rows.append(shaded(row, GOLD, GOLD_TOP, GOLD_BOTTOM) if colour == GOLD else row)
    gap = 8
    block = Image.new("RGBA", (max(r.width for r in rows), sum(r.height for r in rows) + gap * (len(rows) - 1)), (0, 0, 0, 0))
    y = 0
    for row in rows:
        block.alpha_composite(row, ((block.width - row.width) // 2, y))
        y += row.height + gap
    image, _ = soft_shadow(block, reach=14, strength=120)
    image.save(path)
    return image.size


# ---------------------------------------------------------------------------------------------------------------- the plan

def plan_full(shots, words, edit):
    """The pieces of the full cut, in order: each a stretch of the recording (or a card) with its length, speed, mix, flash,
    fades and caption."""
    by_name = {s["name"]: s for s in shots["shots"]}
    pieces, kicker = [], None
    for entry in edit:
        shot = by_name.get(entry["name"])
        if shot is None:
            print(f"post: {entry['name']} is not in this recording, left out")
            continue
        length = shot["end"] - shot["start"]
        raw = entry["dur"] * entry["speed"]
        if raw > length + 1e-6:
            sys.exit(f"edit.txt: {entry['name']} needs {raw:.2f} s of a {length:.2f} s shot (shorten it or speed it up)")
        at = (length - raw) / 2 if entry["at"] is None else min(entry["at"], length - raw)
        chapter = words["chapters"].get(entry["name"])
        if chapter:
            kicker = None if entry["name"] == TITLE_SHOT else chapter
        title = entry["name"] == TITLE_SHOT
        pieces.append(dict(kind="shot", take=shot.get("take"), name=entry["name"], src=shot["start"] + at, dur=entry["dur"], speed=entry["speed"],
                           mix="cold" if entry["cold"] else "engine" if entry["engine"] else "music",
                           text=None if title else words["captions"].get(entry["name"]), quiet=entry["quiet"], kicker=kicker,
                           flash=title or (bool(chapter) and bool(pieces) and not entry["cold"]), title=title,
                           hit=1.0 if title else 0.5 if chapter and pieces and not entry["cold"] else 0.0,
                           fade_in=0.6 if not pieces else 0.0, fade_out=0.0))
    pieces[-1]["fade_out"] = 0.75  # the last shot goes to black, the end card comes up out of it
    pieces[-1]["riser"] = True  # and a riser climbs into it from the piece before
    pieces.append(dict(kind="card", name="close", dur=END_CARD, mix="card", fade_in=0.5, fade_out=0.0, sting=True))
    return pieces


def plan_short(shots, words):
    by_name = {s["name"]: s for s in shots["shots"]}
    pieces = [dict(kind="card", name="open", dur=BAR, mix="card", fade_in=0.3, fade_out=0.0)]
    for name in words["short_shots"].split():
        shot = by_name[name]
        # The middle three seconds of the shot: the camera is under way and has not finished
        pieces.append(dict(kind="shot", take=shot.get("take"), name=name, src=shot["start"] + (shot["end"] - shot["start"] - SHORT_SHOT) / 2, dur=SHORT_SHOT,
                           speed=1.0, mix="music", big=words["short_captions"].get(name), flash=True, hit=0.6,
                           fade_in=0.0, fade_out=0.0, title=False))
    pieces.append(dict(kind="card", name="close", dur=BAR, mix="card", fade_in=0.0, fade_out=0.0, sting=True, flash=True))
    return pieces


def plan_vertical(shots, words, edit):
    """The pieces of the vertical cut, from vertical.txt: a hit under the first frame and a light one at every cut after, the
    AI tag over every piece but the end one, and the end piece (title, links, AI notice) over its footage, with a sting. No
    fade at either end, so the loop a Shorts player makes is seamless."""
    by_name = {s["name"]: s for s in shots["shots"]}
    pieces = []
    for entry in edit:
        shot = by_name.get(entry["name"])
        if shot is None:
            print(f"post: {entry['name']} is not in this recording, left out")
            continue
        length = shot["end"] - shot["start"]
        raw = entry["dur"] * entry["speed"]
        if raw > length + 1e-6:
            sys.exit(f"vertical.txt: {entry['name']} needs {raw:.2f} s of a {length:.2f} s shot (shorten it or speed it up)")
        at = (length - raw) / 2 if entry["at"] is None else min(entry["at"], length - raw)
        end = entry["end"]
        pieces.append(dict(kind="shot", take=shot.get("take"), name=entry["name"], src=shot["start"] + at, dur=entry["dur"], speed=entry["speed"],
                           mix="cold" if entry["cold"] else "engine" if entry["engine"] else "music",
                           big=None if end else entry["words"], focus=entry["x"], fit=entry["fit"], zoom=entry["zoom"],
                           flash=entry["flash"],
                           hit=0.9 if not pieces else 0.0 if end else 0.6 if entry["flash"] else 0.3, hook=not pieces,
                           tag=not end, end=end, sting=end, title=False, fade_in=0.0, fade_out=0.0))
    if not pieces or not pieces[-1]["end"]:
        sys.exit("vertical.txt: the last piece must be the `end` piece")
    return pieces


def place(pieces):
    at = 0.0
    for piece in pieces:
        piece["start"] = at
        at += piece["dur"]
    return at


def captions_of(pieces):
    """Gives every piece the stretch of caption it shows, in its own time, and returns the captions' times for the .srt. A
    caption comes in a little after its piece starts and leaves a little before the last `quiet` piece after it ends."""
    srt, i = [], 0
    while i < len(pieces):
        piece = pieces[i]
        if piece["kind"] != "shot" or not piece.get("text") or piece.get("quiet"):
            i += 1
            continue
        j = i
        while j + 1 < len(pieces) and pieces[j + 1]["kind"] == "shot" and pieces[j + 1].get("quiet"):
            j += 1
        start, end = piece["start"] + 0.35, pieces[j]["start"] + pieces[j]["dur"] - 0.3
        if pieces[j].get("fade_out"):
            end -= pieces[j]["fade_out"]
        srt.append((start, end, piece["text"]))
        for k in range(i, j + 1):
            pieces[k]["caption"] = dict(text=piece["text"], kicker=piece.get("kicker"),
                                        start=max(0.0, start - pieces[k]["start"]), end=min(pieces[k]["dur"], end - pieces[k]["start"]),
                                        enter=k == i, leave=k == j)
        i = j + 1
    for piece in pieces:
        if piece.get("big"):
            srt.append((piece["start"] + 0.1, piece["start"] + piece["dur"] - 0.2, piece["big"]))
    return sorted(srt)


# ---------------------------------------------------------------------------------------------------------------- rendering

def take_of(args, item):
    """The recording a shot (or a piece of one) comes from: its pickup if it was shot again, else the main one."""
    return item.get("take") or dict(raw=str(Path(args.raw).resolve()), offset=args.offset, audio=args.audio,
                                    audio_offset=args.audio_offset)


def add_pickups(shots, folder):
    """Lays the pickups (record.py --pickup: later recordings of a few shots) over the main recording's shots, oldest first, so
    each shot comes from its latest take."""
    by_name = {s["name"]: s for s in shots["shots"]}
    for meta in sorted(folder.glob("*/meta.json"), key=lambda p: p.stat().st_mtime):
        saved = json.loads(meta.read_text(encoding="utf-8"))
        sound = meta.with_name("game.wav")
        heard = sound.is_file() and saved.get("audio_offset") is not None
        take = dict(raw=str(meta.with_name("raw.mp4").resolve()), offset=saved["offset"],
                    audio=str(sound.resolve()) if heard else None, audio_offset=saved.get("audio_offset") or 0.0)
        for shot in json.loads(meta.with_name("trailer_shots.json").read_text(encoding="utf-8-sig"))["shots"]:
            by_name[shot["name"]] = dict(shot, take=take)
            print(f"post: {shot['name']} comes from the pickup {meta.parent.name}")
    shots["shots"] = list(by_name.values())


def render_piece(ffmpeg, args, layout, piece, index, work, card_paths):
    """One piece of the cut to its own file, at full quality, with no sound."""
    lay = LAYOUTS[layout]
    width, height = lay["size"]
    out = work / f"piece_{index:03d}.mp4"
    frames = round(piece["dur"] * FPS)
    graph, inputs = [], []

    if piece["kind"] == "card":
        inputs += ["-loop", "1", "-framerate", str(FPS), "-t", f"{piece['dur'] + 0.1:.3f}", "-i", str(card_paths[piece["name"]])]
        graph.append(f"[0:v]fps={FPS},format=yuv420p[v0]")
    else:
        raw_len = piece["dur"] * piece["speed"]
        take = take_of(args, piece)
        inputs += ["-ss", f"{take['offset'] + piece['src']:.3f}", "-t", f"{raw_len + 0.2:.3f}", "-i", take["raw"]]
        timed = f"[0:v]setpts=(PTS-STARTPTS)/{piece['speed']},fps={FPS}"
        if layout == "tall" and piece.get("fit"):
            # The whole picture in a band, on a blurred, darkened copy of itself filling the frame (blurred small, so it is cheap)
            band = round(width * 9 / 16 / 2) * 2
            graph.append(f"{timed},{GRADE},split[full][back]")
            graph.append(f"[back]scale=270:480:force_original_aspect_ratio=increase,crop=270:480,gblur=sigma=9,"
                         f"eq=brightness=-0.18:saturation=0.85,scale={width}:{height}:flags=bicubic[blur]")
            # zoom= takes a smaller window of the picture into the band, a little above the middle (clear of the hotbar's row)
            zoom = piece.get("zoom", 1.0)
            window = "" if zoom == 1.0 else f"crop=iw/{zoom:.3f}:ih/{zoom:.3f}:(iw-ow)/2:(ih-oh)*0.4,"
            graph.append(f"[full]{window}scale={width}:{band}:flags=lanczos[band]")
            graph.append(f"[blur][band]overlay=0:{FIT_Y},format=yuv420p[v0]")
        else:
            focus = piece.get("focus", 0.5)
            crop = f"crop=608:1080:(iw-608)*{focus:.3f}:0,scale={width}:{height}:flags=lanczos" if layout == "tall" \
                else f"scale={width}:{height}:flags=lanczos"
            graph.append(f"{timed},{crop},{GRADE},format=yuv420p[v0]")
    last = "v0"
    n = 1

    def overlay_input(path, seconds):
        nonlocal n
        inputs.extend(["-loop", "1", "-framerate", str(FPS), "-t", f"{seconds:.3f}", "-i", str(path)])
        n += 1
        return n - 1

    if piece.get("flash"):
        inputs.extend(["-f", "lavfi", "-t", "0.6", "-i", f"color=c=white:s={width}x{height}:r={FPS}"])
        source = n
        n += 1
        strength = 0.9 if piece.get("title") else 0.6
        graph.append(f"[{source}:v]format=rgba,colorchannelmixer=aa={strength},fade=t=out:st=0.04:d=0.45:alpha=1[flash]")
        graph.append(f"[{last}][flash]overlay=0:0:eof_action=pass[v1]")
        last = "v1"
    if lay["letterbox"] and piece["kind"] == "shot":
        bar = lay["letterbox"]
        graph.append(f"[{last}]drawbox=x=0:y=0:w=iw:h={bar}:color=black:t=fill,"
                     f"drawbox=x=0:y=ih-{bar}:w=iw:h={bar}:color=black:t=fill[v2]")
        last = "v2"
    if piece.get("title"):
        source = overlay_input(card_paths["title"], piece["dur"])
        graph.append(f"[{source}:v]format=rgba,fade=t=in:st=0:d=0.12:alpha=1,fade=t=out:st={piece['dur'] - 1.3:.2f}:d=1.0:alpha=1[title]")
        graph.append(f"[{last}][title]overlay=0:0[v3]")
        last = "v3"
    caption = piece.get("caption")
    if caption:
        png = work / f"caption_{index:03d}.png"
        pad, (w, h) = lower_third(caption["kicker"], caption["text"], layout, png)
        source = overlay_input(png, piece["dur"])
        s, e = caption["start"], caption["end"]
        fades = ["format=rgba"]
        if caption["enter"]:
            fades.append(f"fade=t=in:st={s:.3f}:d=0.4:alpha=1")
        if caption["leave"]:
            fades.append(f"fade=t=out:st={e - 0.3:.3f}:d=0.3:alpha=1")
        graph.append(f"[{source}:v]{','.join(fades)}[cap]")
        x0 = lay["caption_x"] - pad
        y0 = height - lay["letterbox"] - lay["caption_gap"] - h + pad
        x = f"{x0}-48*pow(max(0,1-(t-{s:.3f})/0.45),3)" if caption["enter"] else str(x0)
        graph.append(f"[{last}][cap]overlay=x='{x}':y={y0}:enable='between(t,{s:.3f},{e:.3f})'[v4]")
        last = "v4"
    if piece.get("big"):
        png = work / f"big_{index:03d}.png"
        w, h = big_words(piece["big"], layout, png)
        source = overlay_input(png, piece["dur"])
        # The hook's words are there on the first frame (the one a feed shows before it plays); the rest pop in on the cut
        s, e = (0.0 if piece.get("hook") else 0.12), piece["dur"] - 0.15
        fade_in = "" if piece.get("hook") else f"fade=t=in:st={s}:d=0.08:alpha=1,"
        # They pop: in from small, a little past full size, and back
        pop = "" if piece.get("hook") else (f"scale=w='trunc(iw*if(lt(t,{s + 0.13:.2f}),0.62+0.46*max(0,t-{s})/0.13,"
                                            f"if(lt(t,{s + 0.25:.2f}),1.08-0.08*(t-{s + 0.13:.2f})/0.12,1))/2)*2':h=-2:eval=frame,")
        graph.append(f"[{source}:v]format=rgba,{pop}{fade_in}fade=t=out:st={e - 0.15:.3f}:d=0.15:alpha=1[big]")
        # Over a `fit` piece the words sit under the picture's band; otherwise low in the frame, above the app's description
        if layout == "tall":
            y0 = FIT_Y + round(width * 9 / 16) + 24 if piece.get("fit") else SAFE_BOTTOM - h
            if y0 + h > SAFE_BOTTOM + 20:  # the picture's soft halo may reach a little past the words
                sys.exit(f"the words {piece['big']!r} reach below {SAFE_BOTTOM}, where the app draws its description")
        else:
            y0 = height - lay["letterbox"] - lay["big_gap"] - h
        # Placed by their centre, so the pop grows out of one spot
        graph.append(f"[{last}][big]overlay=x=(W-w)/2:y='{y0}+({h}-h)/2':eval=frame:enable='between(t,{s},{e:.3f})'[v5]")
        last = "v5"
    if piece.get("tag"):
        source = overlay_input(card_paths["tag"], piece["dur"])
        pad = card_paths["tag_pad"]
        graph.append(f"[{last}][{source}:v]overlay=x={TAG_AT[0] - pad}:y={TAG_AT[1] - pad}[v6]")
        last = "v6"
    if piece.get("end"):
        source = overlay_input(card_paths["end"], piece["dur"])
        graph.append(f"[{source}:v]format=rgba,fade=t=in:st=0:d=0.25:alpha=1[endcard]")
        graph.append(f"[{last}][endcard]overlay=0:0[v7]")
        last = "v7"
    fades = []
    if piece.get("fade_in"):
        fades.append(f"fade=t=in:st=0:d={piece['fade_in']}")
    if piece.get("fade_out"):
        fades.append(f"fade=t=out:st={piece['dur'] - piece['fade_out']:.3f}:d={piece['fade_out']}")
    graph.append(f"[{last}]{','.join(fades + ['format=yuv420p', 'setsar=1'])}[v]")

    script = work / f"graph_{index:03d}.txt"
    script.write_text(";\n".join(graph), encoding="utf-8")
    subprocess.run([ffmpeg, "-y", "-hide_banner", "-loglevel", "error", *inputs, "-/filter_complex", str(script), "-map", "[v]",
                    "-frames:v", str(frames), "-r", str(FPS), "-c:v", "libx264", "-preset", "veryfast", "-crf", "12",
                    "-pix_fmt", "yuv420p", "-an", str(out)], check=True)
    return out


# ---------------------------------------------------------------------------------------------------------------- sound

def read_wav(path):
    with wave.open(str(path), "rb") as f:
        assert f.getsampwidth() == 2 and f.getframerate() == RATE, f"{path}: expected 16-bit {RATE} Hz"
        data = np.frombuffer(f.readframes(f.getnframes()), dtype="<i2").astype(np.float64) / 32768
        return data.reshape(-1, f.getnchannels())[:, :2] if f.getnchannels() >= 2 else np.repeat(data[:, None], 2, axis=1)


def write_wav(path, audio):
    with wave.open(str(path), "wb") as f:
        f.setnchannels(2)
        f.setsampwidth(2)
        f.setframerate(RATE)
        f.writeframes((np.clip(audio, -1, 1) * 32767).astype("<i2").tobytes())


def decode(ffmpeg, source, out, start=0.0, seconds=None, speed=1.0):
    command = [ffmpeg, "-y", "-hide_banner", "-loglevel", "error"]
    if start > 0:
        command += ["-ss", f"{start:.3f}"]
    if seconds:
        command += ["-t", f"{seconds:.3f}"]
    command += ["-i", str(source), "-vn"]
    if speed != 1.0:
        command += ["-af", f"atempo={speed}"]
    subprocess.run(command + ["-ar", str(RATE), "-ac", "2", "-c:a", "pcm_s16le", str(out)], check=True)
    return read_wav(out)


def active_rms(audio, floor_db=-65):
    """The level of a track where it is sounding: the power of its tenth-of-a-second blocks above the floor."""
    mono = audio.mean(axis=1)
    block = RATE // 10
    blocks = mono[: len(mono) // block * block].reshape(-1, block)
    power = (blocks ** 2).mean(axis=1)
    loud = power[power > 10 ** (floor_db / 10)]
    return float(np.sqrt(loud.mean())) if len(loud) else 0.0


def envelope(pieces, values, total, ramp=0.25):
    """A gain that steps per piece, smoothed over `ramp` seconds so a change never clicks."""
    env = np.zeros(total)
    for piece in pieces:
        a = int(piece["start"] * RATE)
        env[a:a + int(piece["dur"] * RATE)] = values[piece["mix"]]
    width = max(1, int(ramp * RATE))
    padded = np.concatenate([np.full(width, env[0]), env, np.full(width, env[-1])])
    cumulative = np.cumsum(padded)
    smooth = (cumulative[width:] - cumulative[:-width]) / width
    return smooth[width // 2: width // 2 + total]


def add(track, at, sound, gain):
    a = int(at * RATE)
    if a >= len(track):
        return
    end = min(len(track), a + len(sound))
    track[a:end] += sound[: end - a] * gain


def mix_sound(ffmpeg, args, pieces, length, work):
    """The game's sound under each piece, the music from the first piece that is not cold, and the hits; returns the path of
    the mixed sound at -14 LUFS, or None when there is nothing to hear."""
    total = int(length * RATE)
    game = np.zeros((total, 2))
    have_game = bool(args.audio) and Path(args.audio).is_file()
    if have_game:
        for i, piece in enumerate(pieces):
            if piece["kind"] != "shot":
                continue
            take = take_of(args, piece)
            if not take["audio"]:
                continue
            raw_len = piece["dur"] * piece["speed"]
            part = decode(ffmpeg, Path(take["audio"]).resolve(), work / f"game_{i:03d}.wav", max(0.0, take["audio_offset"] + piece["src"]),
                          raw_len + 0.1, piece["speed"])
            n = min(len(part), int(piece["dur"] * RATE))
            fade = min(n, int(0.03 * RATE))  # a few milliseconds of fade at a cut, so it does not click
            part = part[:n].copy()
            part[:fade] *= np.linspace(0, 1, fade)[:, None]
            part[n - fade:] *= np.linspace(1, 0, fade)[:, None]
            add(game, piece["start"], part, 1.0)
        level = active_rms(game)
        if level > 0:
            game *= min(10 ** (30 / 20), 10 ** (-21 / 20) / level)  # the engines' level where they sound, to -21 dBFS
        game *= envelope(pieces, dict(cold=1.25, engine=1.25, music=0.8, card=0.0), total)[:, None]

    music = np.zeros((total, 2))
    first = next((p["start"] for p in pieces if p["mix"] != "cold"), 0.0)
    source = pick_music(args, length - first, work)
    if source:
        track = decode(ffmpeg, Path(source).resolve(), work / "music.wav")
        level = active_rms(track)
        if level > 0:
            track = track * (10 ** (-22 / 20) / level)
        add(music, first, track, 1.0)
        tail = min(total, int(2.5 * RATE))
        music[total - tail:] *= np.linspace(1, 0, tail)[:, None]
        music *= envelope(pieces, dict(cold=0.0, engine=0.4, music=1.0, card=1.0), total)[:, None]

    hits = np.zeros((total, 2))
    for piece in pieces:
        if piece.get("hit"):
            add(hits, piece["start"], sfx.impact(piece["hit"]), 0.55 * piece["hit"] + 0.1)
        if piece.get("riser"):
            rise = sfx.riser(BAR)
            add(hits, piece["start"] - BAR, rise, 0.22)
        if piece.get("sting"):
            add(hits, piece["start"], sfx.sting(), 0.55)

    if not have_game and not source:
        return None
    mixed = game + music + hits
    level = active_rms(mixed)
    if level > 0:
        mixed *= 10 ** (-17 / 20) / level
    mixed = 0.85 * np.tanh(mixed / 0.85)  # a soft ceiling for the hits' peaks
    write_wav(work / "mix_raw.wav", mixed)

    # Two-pass loudnorm: measure, then bring it to -14 LUFS with one gain, so the mix above is kept as it is
    probe = subprocess.run([ffmpeg, "-hide_banner", "-i", str(work / "mix_raw.wav"), "-af", "loudnorm=I=-14:TP=-2.0:LRA=11:print_format=json",
                            "-f", "null", "-"], capture_output=True, text=True, check=True).stderr
    m = json.loads(re.search(r"\{[^{}]*\"input_i\"[^{}]*\}", probe, re.S).group(0))
    final = work / "mix.wav"
    subprocess.run([ffmpeg, "-y", "-hide_banner", "-loglevel", "error", "-i", str(work / "mix_raw.wav"), "-af",
                    f"loudnorm=I=-14:TP=-2.0:LRA=11:measured_I={m['input_i']}:measured_TP={m['input_tp']}:measured_LRA={m['input_lra']}:"
                    f"measured_thresh={m['input_thresh']}:offset={m['target_offset']}:linear=true,aresample={RATE}",
                    "-c:a", "pcm_s16le", str(final)], check=True)
    return final, have_game


# ---------------------------------------------------------------------------------------------------------------- main

def srt_time(seconds):
    ms = int(round(seconds * 1000))
    return f"{ms // 3600000:02d}:{ms // 60000 % 60:02d}:{ms // 1000 % 60:02d},{ms % 1000:03d}"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--raw", required=True)
    parser.add_argument("--shots", required=True)
    parser.add_argument("--offset", type=float, required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--cut", choices=("full", "teaser", "vertical"), default="full")
    parser.add_argument("--audio", help="the recording of the game's sound")
    parser.add_argument("--audio-offset", type=float, default=0.0)
    parser.add_argument("--music")
    parser.add_argument("--no-music", action="store_true")
    parser.add_argument("--thumbnail")
    args = parser.parse_args()

    ffmpeg = find_ffmpeg()
    words = read_captions()
    shots = json.loads(Path(args.shots).read_text(encoding="utf-8-sig"))
    add_pickups(shots, Path(args.shots).resolve().parent / "pickups")
    layout = "tall" if args.cut == "vertical" else "wide"
    if args.cut == "full":
        pieces = plan_full(shots, words, read_edit())
    elif args.cut == "vertical":
        pieces = plan_vertical(shots, words, read_edit("vertical.txt"))
    else:
        pieces = plan_short(shots, words)
    length = place(pieces)
    srt = captions_of(pieces)

    work = Path(tempfile.mkdtemp(prefix="trailer_post_"))
    try:
        cards = {"title": work / "title.png", "open": work / "open.png", "close": work / "close.png"}
        card_image(words["title"], words["captions"].get(TITLE_SHOT, words.get("subtitle", "")), layout, cards["title"], overlay=True)
        card_image(words["title"], words.get("subtitle", ""), layout, cards["open"])
        card_image(words.get("outro", words["title"]), words.get("outro_sub", ""), layout, cards["close"], footer=versions(),
                   links=words.get("outro_links"), notice=words.get("outro_notice"))
        if args.cut == "vertical":
            cards["end"] = work / "end.png"
            card_image(words.get("outro", words["title"]), words.get("outro_sub", ""), layout, cards["end"], footer=versions(),
                       links=words.get("outro_links"), notice=words["outro_notice"], docs=words["ai_docs"], overlay=True, shade=185)
            cards["tag"] = work / "tag.png"
            cards["tag_pad"] = ai_tag(words["vertical_ai_tag"], cards["tag"])

        with ThreadPoolExecutor(max_workers=3) as pool:
            files = list(pool.map(lambda job: render_piece(ffmpeg, args, layout, job[1], job[0], work, cards), enumerate(pieces)))
        (work / "pieces.txt").write_text("".join(f"file '{f.as_posix()}'\n" for f in files), encoding="utf-8")

        sound = mix_sound(ffmpeg, args, pieces, length, work)
        command = [ffmpeg, "-y", "-hide_banner", "-loglevel", "error", "-f", "concat", "-safe", "0", "-i", str(work / "pieces.txt")]
        if sound:
            command += ["-i", str(sound[0]), "-map", "0:v", "-map", "1:a", "-c:a", "aac", "-b:a", "256k"]
        command += ["-t", f"{length:.3f}", "-c:v", "libx264", "-preset", "slow", "-crf", "17", "-pix_fmt", "yuv420p",
                    "-movflags", "+faststart", str(Path(args.out).resolve())]
        Path(args.out).resolve().parent.mkdir(parents=True, exist_ok=True)
        subprocess.run(command, check=True)

        lines = []
        for n, (a, b, text) in enumerate(srt, 1):
            lines += [str(n), f"{srt_time(a)} --> {srt_time(b)}", text, ""]
        Path(args.out).with_suffix(".srt").write_text("\n".join(lines), encoding="utf-8")
        if args.cut == "full":
            write_description(Path(args.out).with_name("description.txt"), pieces, words)
        if args.cut == "vertical":
            write_vertical_description(Path(args.out).with_name("description_vertical.txt"), words)
        if args.thumbnail and args.cut == "full":
            thumbnail(ffmpeg, args, words, shots, work)
        if args.thumbnail and args.cut == "vertical":
            vertical_thumbnail(ffmpeg, args, words, shots, work)
        print(f"trailer: {args.out} ({length:.1f} s, {args.cut}{', with game sound' if sound and sound[1] else ''})")
    finally:
        shutil.rmtree(work, ignore_errors=True)


def stamp(seconds):
    seconds = int(seconds)
    return f"{seconds // 60}:{seconds % 60:02d}"


def write_description(path, pieces, words):
    """The video's description, written with every full cut so it always matches the cut: the pitch, the links, chapters
    (`chapter <shot>:` lines of captions.txt, at the start of that shot in the cut; YouTube wants the first at 0:00, three or
    more and each at least ten seconds long), the version line and the AI notice."""
    starts = {p["name"]: p["start"] for p in pieces if p["kind"] == "shot"}
    chapters = sorted((starts[name], text) for name, text in words["chapters"].items() if name in starts)
    if chapters and chapters[0][0] > 0:
        chapters.insert(0, (0.0, "Intro"))
    kept = []
    for when, text in chapters:  # a chapter under ten seconds long would be ignored by YouTube: fold it into the one before
        if not kept or when - kept[-1][0] >= 10:
            kept.append((when, text))
    lines = [words.get("title", ""), words.get("description", words.get("subtitle", "")), ""]
    links = [(key[len("link "):], text) for key, text in words.items() if key.startswith("link ")]
    if links:
        lines += ["Links"] + [f"{name}: {url}" for name, url in links] + [""]
    if len(kept) >= 3:
        lines += ["Chapters"] + [f"{stamp(when)} {text}" for when, text in kept] + [""]
    lines += [versions(), "", words.get("outro_notice", ""), f"How AI is used, and what is guaranteed: {words['ai_docs_url']}"]
    path.write_text("\n".join(lines).rstrip() + "\n", encoding="utf-8")
    print(f"description: {path}")


def write_vertical_description(path, words):
    """The Shorts description, written with every vertical cut: the hook line, the AI notice and the link to docs/AI.md
    first (a Shorts description shows only its first lines), then the links and the version line."""
    lines = [words.get("vertical_description", words.get("subtitle", "")), "",
             words.get("outro_notice", ""), f"How AI is used, and what is guaranteed: {words['ai_docs_url']}", ""]
    links = [(key[len("link "):], text) for key, text in words.items() if key.startswith("link ")]
    if links:
        lines += [f"{name}: {url}" for name, url in links] + [""]
    lines += [versions()]
    path.write_text("\n".join(lines).rstrip() + "\n", encoding="utf-8")
    print(f"description: {path}")


def thumbnail(ffmpeg, args, words, shots, work):
    """A 1280x720 still for the mod pages: a frame from the middle of the `thumbnail` shot, the wordmark."""
    shot = next(s for s in shots["shots"] if s["name"] == words["thumbnail"])
    take = take_of(args, shot)
    at = take["offset"] + (shot["start"] + shot["end"]) / 2
    frame = work / "frame.png"
    subprocess.run([ffmpeg, "-y", "-hide_banner", "-loglevel", "error", "-ss", f"{at:.3f}", "-i", take["raw"],
                    "-frames:v", "1", "-vf", "scale=1280:720:flags=lanczos", str(frame)], check=True)
    image = Image.open(frame).convert("RGBA")
    shade = Image.new("RGBA", image.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(shade)
    for y in range(image.height // 2, image.height):
        draw.line((0, y, image.width, y), fill=(0, 0, 0, int(190 * (y - image.height // 2) / (image.height / 2))))
    image.alpha_composite(shade)
    mark = Image.open(WORDMARK).convert("RGBA")
    mark = mark.resize((720, round(mark.height * 720 / mark.width)), Image.LANCZOS)
    mark_y = image.height - mark.height - 44
    image.alpha_composite(mark, (48, mark_y))
    create_lettering.place(image, 380, mark_y - create_lettering.height(380) - 10, 48 + (720 - 380) // 2)
    icon = Image.open(LOGO).convert("RGBA").resize((150, 150), Image.LANCZOS)
    image.alpha_composite(icon, (36, 36))
    Path(args.thumbnail).resolve().parent.mkdir(parents=True, exist_ok=True)
    image.convert("RGB").save(args.thumbnail)


def vertical_thumbnail(ffmpeg, args, words, shots, work):
    """A 1080x1920 still for YouTube Shorts and the like: one close frame at full height, hot colour, the title in huge outlined
    letters in the upper middle. The bottom fifth and the right edge are left clear, where the app draws its own buttons and
    title, and the logo sits top left, where nothing is drawn."""
    shot = next(s for s in shots["shots"] if s["name"] == words["vertical_thumbnail"])
    take = take_of(args, shot)
    at = take["offset"] + shot["start"] + float(words.get("vertical_thumbnail_at", (shot["end"] - shot["start"]) / 2))
    frame = work / "frame_tall.png"
    subprocess.run([ffmpeg, "-y", "-hide_banner", "-loglevel", "error", "-ss", f"{at:.3f}", "-i", take["raw"],
                    "-frames:v", "1", "-vf", "crop=608:1080:(iw-608)/2:0,scale=1080:1920:flags=lanczos", str(frame)], check=True)
    image = Image.open(frame).convert("RGB")
    image = ImageEnhance.Color(image).enhance(1.35)
    image = ImageEnhance.Contrast(image).enhance(1.12)
    image = image.filter(ImageFilter.UnsharpMask(radius=3, percent=70, threshold=2)).convert("RGBA")
    width, height = image.size

    shade = Image.new("RGBA", image.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(shade)
    for y in range(height):  # dark behind the words in the lower middle, a little at the top for the logo
        low = min(1.0, max(0.0, (y - 880) / 360)) ** 1.2 * 225 if y < 1500 else 225 - min(1.0, (y - 1500) / 420) * 70
        top = max(0.0, 1 - y / 300) ** 1.5 * 110
        draw.line((0, y, width, y), fill=(6, 4, 10, int(max(low, top))))
    image.alpha_composite(shade)

    margin = 70
    title_lines = words["vertical_title"].upper().split("|")
    scale = 1  # one size for both lines, the longest fills the width
    while all(mcfont.width(line) * (scale + 1) <= width - 2 * margin - 40 for line in title_lines):
        scale += 1
    reach = max(4, scale // 5)
    y = 1050
    for line, color in zip(title_lines, (WHITE, GOLD)):
        art = outlined(line, scale, color, INK, reach=reach, drop=reach)
        image.alpha_composite(art, ((width - art.width) // 2, y))
        y += art.height - reach
    sub = outlined(words["vertical_sub"].upper(), 7, (255, 255, 255), INK, reach=5, drop=5)
    image.alpha_composite(Image.new("RGBA", (sub.width, 12), COPPER), ((width - sub.width) // 2, y + 12))
    image.alpha_composite(sub, ((width - sub.width) // 2, y + 40))

    logo = Image.open(LOGO).convert("RGBA").resize((190, 190), Image.LANCZOS)
    ring = Image.new("RGBA", (214, 214), (0, 0, 0, 0))
    ImageDraw.Draw(ring).ellipse((0, 0, 213, 213), fill=(255, 255, 255, 255))
    image.alpha_composite(ring, (margin - 12, 52 - 12))
    image.alpha_composite(logo, (margin, 52))

    Path(args.thumbnail).resolve().parent.mkdir(parents=True, exist_ok=True)
    image.convert("RGB").save(args.thumbnail)


if __name__ == "__main__":
    main()
