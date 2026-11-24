"""Cuts the trailer: a title card with the wordmark, the recording with a caption over each shot, an end card, the engine sound
from the game and the music.

    python tools/trailer/post.py --raw RAW.mp4 --shots trailer_shots.json --offset SECONDS --out trailer.mp4
        [--cut full|teaser|vertical] [--audio GAME.wav --audio-offset SECONDS] [--music FILE] [--no-music]
        [--thumbnail thumbnail.png]   (with --cut full: the 16:9 still; with --cut vertical: the 9:16 one for Shorts)

RAW.mp4 is the screen recording and --offset is how many seconds into it the first shot began (record.py measures that, and
the same for the recording of the game's sound, GAME.wav). The words are in tools/trailer/captions.txt and are drawn in
Minecraft's own font (mcfont.py), as pictures that ffmpeg lays over the footage. The music is, in order: --music, the first
file in tools/trailer/music/, or a backing track that tools/trailer/music.py makes.

--cut full is the whole trailer. teaser is four three-second shots (the `short_shots` of captions.txt) between short cards,
vertical is the same teaser cropped to 9:16 for phones. Every cut is a whole number of bars (three seconds, the music's tempo)
and every shot starts on a bar, so the cuts fall on the beat. A .srt of the captions is written beside the video.

Needs ffmpeg (winget install Gyan.FFmpeg) and Pillow.
"""
import argparse
import json
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

from PIL import Image, ImageDraw, ImageEnhance, ImageFilter

import mcfont

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import create_lettering  # noqa: E402  (Create's logo and the "CREATE" block letters, side by side)

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
LOGO = ROOT / "docs" / "images" / "icon.png"
WORDMARK = ROOT / "docs" / "images" / "wordmark.png"  # the gold block-letter title, 2000x650 on transparent
BAR = 3.0  # seconds of one bar at the music's tempo (music.py: 80 beats a minute)
FADE = 1.0  # seconds a card and the footage cross-fade for
BACKGROUND = (14, 14, 17, 255)
COPPER = (184, 115, 51, 255)
GOLD = (255, 170, 0)  # Minecraft's gold, for the line under the title
WHITE = (255, 255, 255)
GREY = (170, 170, 170)
SHORT_SHOT_SECONDS = BAR
TITLE_SHOT = "title_sequence"  # the opening shot of the full cut: the title lies over it instead of a dark card before it

# What differs between the wide and the tall cut: the picture, the cards' layout and the caption size
LAYOUTS = {
    "wide": dict(size=(1920, 1080), icon=120, icon_y=95, create=520, create_y=225, mark=1040, mark_y=372, rule_y=752, sub=5, sub_y=787, links=4, links_y=845, ver=3, ver_y=905, notice=3, notice_y=950, lone=65,
                 caption=4, caption_pad=22, caption_bottom=110),
    "tall": dict(size=(1080, 1920), icon=200, icon_y=330, create=640, create_y=545, mark=980, mark_y=725, rule_y=1085, sub=3, sub_y=1125, links=3, links_y=1165, ver=2, ver_y=1215, lone=0, notice=3, notice_y=1600,
                 caption=3, caption_pad=16, caption_bottom=330),
}


def find_ffmpeg():
    found = shutil.which("ffmpeg")
    if found:
        return found
    packages = Path(os.environ.get("LOCALAPPDATA", "")) / "Microsoft" / "WinGet" / "Packages"
    for candidate in sorted(packages.glob("Gyan.FFmpeg*/**/bin/ffmpeg.exe")):
        return str(candidate)
    sys.exit("ffmpeg not found: winget install Gyan.FFmpeg")


def read_captions():
    words = {"captions": {}, "short_captions": {}}
    for line in (HERE / "captions.txt").read_text(encoding="utf-8").splitlines():
        if not line.strip() or line.startswith("#"):
            continue
        key, _, text = line.partition(":")
        key, text = key.strip(), text.strip()
        if key.startswith("caption "):
            words["captions"][key[len("caption "):]] = text
        elif key.startswith("short_caption "):
            words["short_captions"][key[len("short_caption "):]] = text
        else:
            words[key] = text
    return words


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
    out = work / "music.wav"
    subprocess.run([sys.executable, str(HERE / "music.py"), str(out), str(length + 2)], check=True)
    return out


def centred(image, layer, y):
    image.alpha_composite(layer, ((image.width - layer.width) // 2, y))


def card_image(title, lines, layout, path, footer=None, notice=None, links=None, overlay=False):
    """The mod's icon, "CREATE" in Minecraft's title font and the wordmark under it (together the title), a copper rule, and the lines under it, in Minecraft's font on the dark card."""
    lay = LAYOUTS[layout]
    # An overlay is the same title on clear glass with a light shade, to lie over footage; a card is on the dark background
    image = Image.new("RGBA", lay["size"], (0, 0, 0, 120) if overlay else BACKGROUND)
    dy = lay["lone"] if not (footer or notice) else 0  # a title card has no lines at the foot, so it sits lower
    mark = Image.open(WORDMARK).convert("RGBA")
    mark = mark.resize((lay["mark"], round(mark.height * lay["mark"] / mark.width)), Image.LANCZOS)
    centred(image, mark, lay["mark_y"] + dy)
    centred(image, create_lettering.sized(lay["create"]), lay["create_y"] + dy)
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
        centred(image, mcfont.render(footer, lay["ver"], GREY), lay["ver_y"])
    if notice:
        # The AI notice: small, near the foot of the card, wrapped to the card's width
        scale, y = lay["notice"], lay["notice_y"]
        for row in wrapped(notice, scale, lay["size"][0] - 160):
            layer = mcfont.render(row, scale, GREY)
            centred(image, layer, y)
            y += layer.height + scale * 2
    (image if overlay else image.convert("RGB")).save(path)


def wrapped(text, scale, limit):
    """Splits a caption into the fewest lines that each fit in `limit` pixels."""
    lines, line = [], ""
    for word in text.split():
        trial = f"{line} {word}".strip()
        if mcfont.width(trial) * scale > limit and line:
            lines.append(line)
            line = word
        else:
            line = trial
    return lines + [line]


def caption_image(text, layout, path):
    lay = LAYOUTS[layout]
    scale, pad = lay["caption"], lay["caption_pad"]
    rows = [mcfont.render(row, scale, WHITE) for row in wrapped(text, scale, lay["size"][0] - 2 * (pad + 40))]
    width = max(r.width for r in rows) + 2 * pad
    height = sum(r.height for r in rows) + 2 * pad
    image = Image.new("RGBA", (width, height), (0, 0, 0, 120))
    y = pad
    for row in rows:
        image.alpha_composite(row, ((width - row.width) // 2, y))
        y += row.height
    image.save(path)


def plan(cut, shots, words):
    """The pieces of the cut, each a stretch of the recording: its name, where it starts in the recording (seconds after the
    first shot began), how long it lasts and the caption it carries."""
    by_name = {s["name"]: s for s in shots["shots"]}
    if cut == "full":
        # The title shot carries the title, not a caption (its caption is the line under the title)
        return [dict(name=s["name"], src=s["start"], dur=s["end"] - s["start"],
                     text=None if s["name"] == TITLE_SHOT else words["captions"].get(s["name"]))
                for s in shots["shots"]]
    pieces = []
    for name in words["short_shots"].split():
        shot = by_name[name]
        # The middle three seconds of the shot: the camera is under way and has not finished
        pieces.append(dict(name=name, src=shot["start"] + (shot["end"] - shot["start"] - SHORT_SHOT_SECONDS) / 2,
                           dur=SHORT_SHOT_SECONDS, text=words["short_captions"].get(name)))
    return pieces


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
    layout = "tall" if args.cut == "vertical" else "wide"
    width, height = LAYOUTS[layout]["size"]
    pieces = plan(args.cut, shots, words)
    body = sum(p["dur"] for p in pieces)
    titled = args.cut == "full" and bool(pieces) and pieces[0]["name"] == TITLE_SHOT
    lead = 0 if titled else 2 * BAR if args.cut == "full" else BAR  # seconds of title card before the footage's first shot
    tail = 2 * BAR if args.cut == "full" else BAR
    card_len, outro_len = (pieces[0]["dur"] if titled else lead + FADE), tail + FADE
    length = lead + body + tail

    work = Path(tempfile.mkdtemp(prefix="trailer_post_"))
    try:
        if titled:
            card_image(words["title"], words["captions"].get(TITLE_SHOT, words.get("subtitle", "")), layout, work / "open.png", overlay=True)
        else:
            card_image(words["title"], words.get("subtitle", ""), layout, work / "open.png")
        card_image(words.get("outro", words["title"]), words.get("outro_sub", ""), layout, work / "close.png", footer=versions(),
                   links=words.get("outro_links"))

        command = [ffmpeg, "-y", "-hide_banner", "-ss", f"{args.offset:.3f}", "-i", str(Path(args.raw).resolve()),
                   "-loop", "1", "-framerate", "60", "-t", f"{card_len}", "-i", "open.png",
                   "-loop", "1", "-framerate", "60", "-t", f"{outro_len}", "-i", "close.png"]
        grade = "eq=contrast=1.04:saturation=1.10,vignette=angle=PI/5"
        crop = f"crop=608:1080:(iw-608)/2:0,scale={width}:{height}:flags=lanczos" if layout == "tall" \
            else f"scale={width}:{height}:flags=lanczos"
        graph = [
            f"[0:v]fps=60,{crop},{grade},format=yuv420p,split={len(pieces)}" + "".join(f"[r{i}]" for i in range(len(pieces))),
        ]
        for i, piece in enumerate(pieces):
            graph.append(f"[r{i}]trim=start={piece['src']:.3f}:duration={piece['dur']:.3f},setpts=PTS-STARTPTS[p{i}]")
        graph.append("".join(f"[p{i}]" for i in range(len(pieces))) + f"concat=n={len(pieces)}:v=1:a=0[b0]")
        graph += [
            (f"[1:v]fps=60,format=rgba,fade=t=in:st=0.5:d=1.0:alpha=1,fade=t=out:st={card_len - 1.5:.2f}:d=1.0:alpha=1[open]" if titled else
             f"[1:v]fps=60,format=yuv420p,fade=t=in:st=0:d=0.9,fade=t=out:st={card_len - 0.9:.2f}:d=0.9,settb=1/60[open]"),
            f"[2:v]fps=60,format=yuv420p,fade=t=in:st=0:d=0.9,fade=t=out:st={outro_len - 0.9:.2f}:d=0.9,settb=1/60[close]",
        ]
        last, index, at = "b0", 0, 0.0
        srt = []
        margin_in, margin_out = (0.5, 0.4) if args.cut == "full" else (0.3, 0.3)
        for piece in pieces:
            if piece["text"]:
                start, end = at + margin_in, at + piece["dur"] - margin_out
                name = f"cap_{index:02d}.png"
                caption_image(piece["text"], layout, work / name)
                command += ["-loop", "1", "-framerate", "60", "-t", f"{end + 0.2:.2f}", "-i", name]
                source = 3 + index
                graph.append(
                    f"[{source}:v]format=rgba,fade=t=in:st={start:.2f}:d=0.3:alpha=1,fade=t=out:st={end - 0.3:.2f}:d=0.3:alpha=1[c{index}]")
                graph.append(f"[{last}][c{index}]overlay=x=(W-w)/2:y=H-h-{LAYOUTS[layout]['caption_bottom']}:"
                             f"enable='between(t,{start:.2f},{end:.2f})':eof_action=pass[b{index + 1}]")
                last = f"b{index + 1}"
                srt.append((lead + start, lead + end, piece["text"]))
                index += 1
            at += piece["dur"]
        graph.append(f"[{last}]trim=duration={body},setpts=PTS-STARTPTS,fps=60,settb=1/60[body]")
        graph += [
            (f"[body][open]overlay=0:0:eof_action=pass,format=yuv420p,settb=1/60[v1]" if titled else
             f"[open][body]xfade=transition=fade:duration={FADE}:offset={lead}[v1]"),
            f"[v1][close]xfade=transition=fade:duration={FADE}:offset={lead + body - FADE}[v]",
        ]

        inputs = 3 + index
        have_game = bool(args.audio) and Path(args.audio).is_file()
        if have_game:
            command += ["-ss", f"{max(args.audio_offset, 0):.3f}", "-i", str(Path(args.audio).resolve())]
            game = inputs
            inputs += 1
            graph.append(f"[{game}:a]aresample=48000,asplit={len(pieces)}" + "".join(f"[g{i}]" for i in range(len(pieces))))
            for i, piece in enumerate(pieces):
                graph.append(f"[g{i}]atrim=start={piece['src']:.3f}:duration={piece['dur']:.3f},asetpts=PTS-STARTPTS[ga{i}]")
            graph.append("".join(f"[ga{i}]" for i in range(len(pieces)))
                         + f"concat=n={len(pieces)}:v=0:a=1,loudnorm=I=-18:TP=-1.5:LRA=9,adelay={int(lead * 1000)}:all=1,"
                         f"afade=t=in:st={lead:.2f}:d=0.5,afade=t=out:st={lead + body - 0.5:.2f}:d=0.5[game]")
        music = pick_music(args, length, work)
        if music:
            command += ["-i", str(music.resolve())]
            level = 0.5 if have_game else 0.9
            graph.append(f"[{inputs}:a]aresample=48000,apad,atrim=0:{length:.2f},afade=t=in:st=0:d=2,"
                         f"afade=t=out:st={length - 3:.2f}:d=3,volume={level}[music]")
        if have_game and music:
            graph.append(f"[music][game]amix=inputs=2:normalize=0:duration=longest,atrim=0:{length:.2f}[a]")
        elif have_game:
            graph.append(f"[game]apad,atrim=0:{length:.2f}[a]")
        elif music:
            graph.append("[music]anull[a]")
        (work / "graph.txt").write_text(";\n".join(graph), encoding="utf-8")
        command += ["-/filter_complex", "graph.txt", "-map", "[v]"]
        if have_game or music:
            command += ["-map", "[a]", "-c:a", "aac", "-b:a", "192k"]
        command += ["-t", f"{length:.2f}", "-c:v", "libx264", "-preset", "slow", "-crf", "17", "-pix_fmt", "yuv420p",
                    "-movflags", "+faststart", str(Path(args.out).resolve())]
        Path(args.out).resolve().parent.mkdir(parents=True, exist_ok=True)
        subprocess.run(command, cwd=work, check=True)

        lines = []
        for n, (a, b, text) in enumerate(srt, 1):
            lines += [str(n), f"{srt_time(a)} --> {srt_time(b)}", text, ""]
        Path(args.out).with_suffix(".srt").write_text("\n".join(lines), encoding="utf-8")
        if args.cut == "full":
            write_description(Path(args.out).with_name("description.txt"), pieces, words, lead)
        if args.thumbnail and args.cut == "full":
            thumbnail(ffmpeg, args, words, shots, work)
        if args.thumbnail and args.cut == "vertical":
            vertical_thumbnail(ffmpeg, args, words, shots, work)
        print(f"trailer: {args.out} ({length:.1f} s, {args.cut}{', with game sound' if have_game else ''})")
    finally:
        shutil.rmtree(work, ignore_errors=True)


def stamp(seconds):
    seconds = int(seconds)
    return f"{seconds // 60}:{seconds % 60:02d}"


def write_description(path, pieces, words, lead):
    """The video's description, written with every full cut so it always matches the cut: the pitch, the links, chapters
    (`chapter <shot>:` lines of captions.txt, at the start of that shot; YouTube wants the first at 0:00, three or more and
    each at least ten seconds long), the version line and the AI notice."""
    starts, at = {}, 0.0
    for piece in pieces:
        starts[piece["name"]] = lead + at
        at += piece["dur"]
    chapters = [(starts[key[len("chapter "):]], text) for key, text in words.items()
                if key.startswith("chapter ") and key[len("chapter "):] in starts]
    chapters.sort()
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
    lines += [versions(), "", words.get("outro_notice", "")]
    path.write_text("\n".join(lines).rstrip() + "\n", encoding="utf-8")
    print(f"description: {path}")


def thumbnail(ffmpeg, args, words, shots, work):
    """A 1280x720 still for the mod pages: a frame from the middle of the `thumbnail` shot, the wordmark."""
    shot = next(s for s in shots["shots"] if s["name"] == words["thumbnail"])
    at = args.offset + (shot["start"] + shot["end"]) / 2
    frame = work / "frame.png"
    subprocess.run([ffmpeg, "-y", "-hide_banner", "-loglevel", "error", "-ss", f"{at:.3f}", "-i", str(Path(args.raw).resolve()),
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
    create = create_lettering.sized(380)
    image.alpha_composite(create, (48 + (720 - create.width) // 2, mark_y - create.height - 10))
    icon = Image.open(LOGO).convert("RGBA").resize((150, 150), Image.LANCZOS)
    image.alpha_composite(icon, (36, 36))
    Path(args.thumbnail).resolve().parent.mkdir(parents=True, exist_ok=True)
    image.convert("RGB").save(args.thumbnail)


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


def vertical_thumbnail(ffmpeg, args, words, shots, work):
    """A 1080x1920 still for YouTube Shorts and the like: one close frame at full height, hot colour, the title in huge outlined
    letters in the upper middle. The bottom fifth and the right edge are left clear, where the app draws its own buttons and
    title, and the logo sits top left, where nothing is drawn."""
    shot = next(s for s in shots["shots"] if s["name"] == words["vertical_thumbnail"])
    at = args.offset + shot["start"] + float(words.get("vertical_thumbnail_at", (shot["end"] - shot["start"]) / 2))
    frame = work / "frame_tall.png"
    subprocess.run([ffmpeg, "-y", "-hide_banner", "-loglevel", "error", "-ss", f"{at:.3f}", "-i", str(Path(args.raw).resolve()),
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
        art = outlined(line, scale, color, (20, 12, 8, 255), reach=reach, drop=reach)
        image.alpha_composite(art, ((width - art.width) // 2, y))
        y += art.height - reach
    sub = outlined(words["vertical_sub"].upper(), 7, (255, 255, 255), (20, 12, 8, 255), reach=5, drop=5)
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
