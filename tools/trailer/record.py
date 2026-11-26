"""Records the trailer and cuts it.

    python tools/trailer/record.py [--no-shaders] [--no-aeronautics] [--no-copy] [--no-music] [--music FILE] [--post-only] [--only full|teaser|vertical]

1. Copies the project to a private folder beside it (create-engines-trailer), because running the game from the shared
   checkout crashes whenever another build recompiles under it, and runs `gradlew runShowcaseShaders -PshowcaseSet=trailer`
   there: the game with Iris, Sodium and the shader pack in tools/trailer/shaderpacks. --no-shaders runs `runShowcase`, the
   game without them. Create Aeronautics and Sable are loaded too (the wheeled car, shots `rover_*`), through
   `-PwithAeronautics` (shaders) or `runShowcaseAeronautics` (--no-shaders); --no-aeronautics leaves them out, and the shots with them.
2. Starts ffmpeg on the whole screen, and a recording of what the speakers play (the game's engine sound), when the game
   reports `Showcase trailer ready` (eight seconds before the first shot), and stops both a moment after the game says
   `after the trailer`. The game goes fullscreen at 1920x1080; leave the machine alone for the five or so minutes the run
   takes, keep other windows off the game, keep the speakers on and nothing else playing sound: the sound is what the
   speakers play, so a notification would be in the trailer. (Needs `pip install soundcard`; without it the trailer has
   only the music.)
3. Hands both recordings to post.py with the offsets between their starts and the first shot, for the full trailer, the
   teaser and the vertical cut for Shorts (its edit in vertical.txt), and the thumbnails.

The cuts land in run/trailer of this project (trailer.mp4, trailer_teaser.mp4, trailer_vertical.mp4, each with a .srt, and
thumbnail.png and thumbnail_vertical.png, and the descriptions), with the raw recordings next to them. --post-only cuts the last recordings again (after a change to
captions.txt or the music) without playing the game.
"""
import argparse
import collections
import json
import os
import re
import shutil
import subprocess
import sys
import threading
import time
import wave
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PRIVATE = ROOT.parent / (ROOT.name + "-trailer")
OUT = ROOT / "run" / "trailer"
SAMPLE_RATE = 48000


def find_ffmpeg():
    found = shutil.which("ffmpeg")
    if found:
        return found
    packages = Path(os.environ.get("LOCALAPPDATA", "")) / "Microsoft" / "WinGet" / "Packages"
    for candidate in sorted(packages.glob("Gyan.FFmpeg*/**/bin/ffmpeg.exe")):
        return str(candidate)
    sys.exit("ffmpeg not found: winget install Gyan.FFmpeg")


def encoder_args(ffmpeg):
    """The graphics card's encoder if it has one (it leaves the processor to the game), otherwise x264."""
    probe = subprocess.run([ffmpeg, "-hide_banner", "-loglevel", "error", "-f", "lavfi", "-i", "color=c=black:s=1920x1080",
                            "-frames:v", "2", "-c:v", "h264_nvenc", "-f", "null", "-"], capture_output=True)
    if probe.returncode == 0:
        return ["-c:v", "h264_nvenc", "-preset", "p1", "-rc", "constqp", "-qp", "16", "-pix_fmt", "yuv420p"]
    return ["-c:v", "libx264", "-preset", "ultrafast", "-crf", "14", "-pix_fmt", "yuv420p"]


class SoundRecorder:
    """Records what the default speakers play (WASAPI loopback) into a WAV, on two threads.

    Loopback delivers nothing while nothing is playing, which would stall the recording and shift everything after the
    silence, so a second thread plays an inaudible hiss through the same speakers to keep the stream alive.
    """

    def __init__(self):
        self.stop = threading.Event()
        self.started = None
        self.chunks = []
        self.error = None
        self.threads = [threading.Thread(target=self._record, daemon=True), threading.Thread(target=self._keep_alive, daemon=True)]

    def start(self):
        try:
            import soundcard  # noqa: F401
            import numpy  # noqa: F401
        except ImportError:
            self.error = "soundcard is not installed (pip install soundcard): the trailer will have only the music"
            return
        for thread in self.threads:
            thread.start()

    def _record(self):
        try:
            import soundcard as sc
            speaker = sc.default_speaker()
            microphone = sc.get_microphone(id=str(speaker.name), include_loopback=True)
            with microphone.recorder(samplerate=SAMPLE_RATE, channels=2) as recorder:
                self.started = time.time()
                while not self.stop.is_set():
                    self.chunks.append(recorder.record(numframes=SAMPLE_RATE // 10))
        except Exception as error:  # a device that will not open must not stop the picture being recorded
            self.error = f"could not record the speakers: {error}"

    def _keep_alive(self):
        try:
            import numpy as np
            import soundcard as sc
            hiss = (np.random.default_rng(1).standard_normal((SAMPLE_RATE // 10, 2)) * 1e-4).astype("float32")
            with sc.default_speaker().player(samplerate=SAMPLE_RATE, channels=2) as player:
                while not self.stop.is_set():
                    player.play(hiss)
        except Exception:
            pass  # the recording itself reports a problem, if there is one

    def finish(self, path):
        """Stops, writes the WAV and returns the epoch second its first sample was taken, or None if nothing was recorded."""
        self.stop.set()
        for thread in self.threads:
            if thread.is_alive():
                thread.join(timeout=5)
        if self.error:
            print(self.error)
        if not self.chunks or self.started is None:
            return None
        import numpy as np
        audio = np.concatenate(self.chunks)
        peak = float(np.abs(audio).max())
        print(f"game sound: {len(audio) / SAMPLE_RATE:.1f} s recorded, peak {peak:.3f}" + ("  (SILENT: are the speakers on?)" if peak < 1e-3 else ""))
        data = (np.clip(audio, -1, 1) * 32767).astype("<i2")
        with wave.open(str(path), "wb") as handle:
            handle.setnchannels(2)
            handle.setsampwidth(2)
            handle.setframerate(SAMPLE_RATE)
            handle.writeframes(data.tobytes())
        return self.started

# The engines turn slowly in the showcase (40 to 70 rpm) and a light crank's speed ripples by several percent each cycle, which
# the network shows as parts that surge and jump. The trailer's own copy of the game runs with five times the usual inertia on the
# crank and its flywheels, which holds the speed nearly steady (a ripple goes as one over the inertia). It is a setting of the
# trailer's private game folder only: nothing in the mod changes, and the engines still start and run the same way.
TRAILER_CONFIG = {
    "journalInertia": 0.75,
    "throwInertia": 1.0,
    "flywheelInertia": 12.5,
    "castFlywheelInertia": 8.0,
    "dualMassPrimaryInertia": 2.25,
}


def steady_crank_config(run_dir):
    """Writes TRAILER_CONFIG into the private game folder's config for the mod (creating the file if the game has not yet)."""
    path = PRIVATE / run_dir / "config" / "create_engines-v2-server.toml"
    path.parent.mkdir(parents=True, exist_ok=True)
    text = path.read_text(encoding="utf-8") if path.is_file() else "[calibration]" + chr(10)
    for key, value in TRAILER_CONFIG.items():
        line = chr(9) + f"{key} = {value}"
        if re.search(rf"^\s*{key}\s*=", text, re.M):
            text = re.sub(rf"^\s*{key}\s*=.*$", line, text, flags=re.M)
        else:
            text = text.replace("[calibration]", "[calibration]" + chr(10) + line, 1)
    path.write_text(text, encoding="utf-8")


def find_flash(ffmpeg, raw):
    """Seconds into the recording at which the sync flash first fills the screen, or None. The picture is read at 60 frames a
    second, shrunk to 16x9 grey, and the flash is the first frame with nothing darker than 235 in it."""
    size = 16 * 9
    process = subprocess.Popen([ffmpeg, "-hide_banner", "-loglevel", "error", "-i", str(raw), "-t", "25", "-vf",
                                "fps=60,scale=16:9:flags=area,format=gray", "-f", "rawvideo", "-"],
                               stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
    index = 0
    found = None
    while True:
        frame = process.stdout.read(size)
        if len(frame) < size:
            break
        if min(frame) >= 235:
            found = index / 60.0
            break
        index += 1
    process.kill()
    process.wait()
    return found


def find_clap(sound_path, expected):
    """Seconds into the sound recording at which the sync clap begins, looked for within two and a half seconds of where it
    should be, or None. The loudest thing in that stretch."""
    import numpy as np
    with wave.open(str(sound_path), "rb") as handle:
        rate = handle.getframerate()
        data = np.frombuffer(handle.readframes(handle.getnframes()), dtype="<i2").reshape(-1, 2).astype(float) / 32768
    lo = max(0, int((expected - 2.5) * rate))
    hi = min(len(data), int((expected + 2.5) * rate))
    if hi <= lo:
        return None
    level = np.abs(data[lo:hi]).max(axis=1)
    peak = float(level.max())
    if peak < 0.05:
        return None
    # The clap is the loudest thing in the stretch (the engine's own pulses are quieter). Its start is the first sample within
    # 40 ms before that peak that is at least half as loud, so the measure is of the onset and not of the top of the wave
    top = int(np.argmax(level))
    back = max(0, top - int(0.04 * rate))
    return (lo + back + int(np.argmax(level[back:top + 1] >= peak / 2))) / rate


def measured_offsets(ffmpeg, raw, sound_path, sync_file, first_shot, video_started, sound_started):
    """How many seconds into the picture and into the sound the first shot began, from the sync mark (a flash and a clap the game
    made at one moment) rather than from when each recorder said it started. Returns (picture, sound) with None for one that
    could not be measured, so the caller falls back to the recorder's own time for that."""
    if not sync_file.is_file():
        print("no sync mark from the game: using the recorders' own start times")
        return None, None
    flash_epoch = json.loads(sync_file.read_text(encoding="utf-8"))["flashEpochMs"] / 1000.0
    to_shot = first_shot - flash_epoch
    flash = find_flash(ffmpeg, raw)
    picture = flash + to_shot if flash is not None else None
    sound = None
    if sound_started is not None and sound_path.is_file():
        clap = find_clap(sound_path, flash_epoch - sound_started)
        sound = clap + to_shot if clap is not None else None
    print("sync mark: picture " + (f"{picture:.3f} s" if picture is not None else "not found")
          + " (recorder said " + (f"{first_shot - video_started:.3f}" if video_started else "?") + "), sound "
          + (f"{sound:.3f} s" if sound is not None else "not found")
          + " (recorder said " + (f"{first_shot - sound_started:.3f}" if sound_started else "?") + ")")
    return picture, sound



def sync():
    PRIVATE.mkdir(exist_ok=True)
    # robocopy returns 0-7 for success; /XD also protects the private copy's own run, build and .gradle from /MIR
    result = subprocess.run(["robocopy", str(ROOT), str(PRIVATE), "/MIR", "/XD", "run", "build", ".gradle", ".git", "/NFL",
                             "/NDL", "/NJH", "/NJS", "/NP"], capture_output=True)
    if result.returncode >= 8:
        sys.exit(f"robocopy failed ({result.returncode})")


def record(ffmpeg, raw, sound_path, task, run_dir, extra=()):
    """Plays the game and records it; returns how many seconds into the picture and into the sound the first shot began."""
    if task != "runShowcaseShaders":
        # The game without shaders keeps its own options; give it the trailer's (sound on, music off, a short render distance)
        options = PRIVATE / run_dir / "options.txt"
        options.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy(ROOT / "tools" / "trailer" / "options.txt", options)
    (PRIVATE / run_dir / "trailer_sync.json").unlink(missing_ok=True)
    steady_crank_config(run_dir)
    encoder = encoder_args(ffmpeg)  # probed now: doing it after the game's ready line would make the picture start late
    game = subprocess.Popen([str(PRIVATE / "gradlew.bat"), task, "-PshowcaseSet=trailer", *extra, "--offline", "--console=plain"],
                            cwd=PRIVATE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding="utf-8",
                            errors="replace")
    video = None
    sound = SoundRecorder()
    video_started = [None]
    ended = False
    tail = collections.deque(maxlen=40)
    for line in game.stdout:
        tail.append(line.rstrip())
        if "Showcase trailer ready" in line and video is None:
            raw.parent.mkdir(parents=True, exist_ok=True)
            video = subprocess.Popen(
                [ffmpeg, "-y", "-hide_banner", "-f", "gdigrab", "-framerate", "60", "-draw_mouse", "0", "-i", "desktop",
                 *encoder, str(raw)],
                stdin=subprocess.PIPE, stderr=subprocess.PIPE, text=True, encoding="utf-8", errors="replace")
            sound.start()

            def watch(stream=video.stderr):
                for message in stream:
                    if "Press [q]" in message and video_started[0] is None:
                        video_started[0] = time.time()
            threading.Thread(target=watch, daemon=True).start()
            print("recording started")
        if "after the trailer" in line and video is not None and not ended:
            ended = True
            time.sleep(1.5)
            video.stdin.write("q")
            video.stdin.flush()
        if "Showcase trailer" in line or "Showcase screenshot" in line:
            print(line.rstrip())
    game.wait()
    if video is None:
        print(chr(10).join(tail))
        sys.exit("the game never reached the trailer; the end of Gradle's output is above, the game's own log is in "
                 + str(PRIVATE / run_dir / "logs"))
    if not ended:
        video.stdin.write("q")
        video.stdin.flush()
    video.wait(timeout=60)
    sound_started = sound.finish(sound_path)
    shots = json.loads((PRIVATE / run_dir / "trailer_shots.json").read_text(encoding="utf-8"))
    shutil.copy(PRIVATE / run_dir / "trailer_shots.json", raw.parent / "trailer_shots.json")
    first_shot = shots["startEpochMs"] / 1000.0
    picture = first_shot - (video_started[0] or time.time())
    measured_picture, measured_sound = measured_offsets(ffmpeg, raw, sound_path, PRIVATE / run_dir / "trailer_sync.json",
                                                        first_shot, video_started[0], sound_started)
    if measured_picture is not None:
        picture = measured_picture
    if picture < 0.5:
        sys.exit(f"the picture started {-picture:.1f} s after the first shot began (or too close to it): the cut would be out of step. "
                 "Run it again, and leave the machine alone while it starts.")
    if measured_sound is not None:
        return picture, measured_sound
    return picture, (first_shot - sound_started) if sound_started else None


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--no-shaders", action="store_true", help="run the game without Iris and the shader pack")
    parser.add_argument("--no-aeronautics", action="store_true", help="run the game without Create Aeronautics and Sable (no wheeled-car shots)")
    parser.add_argument("--no-copy", action="store_true", help="run in the private copy as it is")
    parser.add_argument("--no-music", action="store_true")
    parser.add_argument("--music")
    parser.add_argument("--post-only", action="store_true")
    parser.add_argument("--pickup", metavar="SHOTS",
                        help="record only these shots (comma-separated) again, into run/trailer/pickups, and cut them in over "
                             "the main recording")
    parser.add_argument("--only", choices=("full", "teaser", "vertical"), help="cut only this one")
    args = parser.parse_args()

    ffmpeg = find_ffmpeg()
    raw = OUT / "raw.mp4"
    sound = OUT / "game.wav"
    meta = OUT / "meta.json"
    if not args.post_only:
        if not args.no_copy:
            sync()
        if args.no_shaders:
            task, run_dir = ("runShowcase", "run/showcase") if args.no_aeronautics else ("runShowcaseAeronautics", "run/showcase-aeronautics")
        else:
            task, run_dir = "runShowcaseShaders", "run/showcase-shaders"
        extra = ["-PwithAeronautics"] if task == "runShowcaseShaders" and not args.no_aeronautics else []
        if args.pickup:
            take = OUT / "pickups" / args.pickup.replace(",", "+")
            take.mkdir(parents=True, exist_ok=True)
            extra.append(f"-PtrailerShots={args.pickup}")
        else:
            take = OUT
            shutil.rmtree(OUT / "pickups", ignore_errors=True)  # a whole new recording makes every pickup stale
        (take / "game.wav").unlink(missing_ok=True)
        (take / "meta.json").unlink(missing_ok=True)
        offset, audio_offset = record(ffmpeg, take / "raw.mp4", take / "game.wav", task, run_dir, extra)
        (take / "meta.json").write_text(json.dumps({"offset": offset, "audio_offset": audio_offset}), encoding="utf-8")
        print(f"first shot begins {offset:.2f} s into the picture"
              + (f" and {audio_offset:.2f} s into the sound" if audio_offset is not None else ""))
    saved = json.loads(meta.read_text(encoding="utf-8"))

    for cut, name in (("full", "trailer.mp4"), ("teaser", "trailer_teaser.mp4"), ("vertical", "trailer_vertical.mp4")):
        if args.only and args.only != cut:
            continue
        command = [sys.executable, str(Path(__file__).with_name("post.py")), "--raw", str(raw), "--shots",
                   str(OUT / "trailer_shots.json"), "--offset", f"{saved['offset']:.3f}", "--out", str(OUT / name), "--cut", cut]
        if sound.is_file() and saved.get("audio_offset") is not None:
            command += ["--audio", str(sound), "--audio-offset", f"{saved['audio_offset']:.3f}"]
        if cut == "full":
            command += ["--thumbnail", str(OUT / "thumbnail.png")]
        if cut == "vertical":
            command += ["--thumbnail", str(OUT / "thumbnail_vertical.png")]
        if args.no_music:
            command.append("--no-music")
        if args.music:
            command += ["--music", args.music]
        subprocess.run(command, check=True)


if __name__ == "__main__":
    main()
