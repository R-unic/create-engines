"""Runs the sound bench, records what the game plays and audits it (docs/SMOKE.md, "The sound bench").

    /c/Python312/python.exe tools/sound_bench.py [--scenes single,walls,room] [--refs DIR] [--compare RUN] [--no-copy]
    /c/Python312/python.exe tools/sound_bench.py --moving     # the moving structure, with Create Aeronautics loaded

1. Copies the project to a private folder beside it (create-engines-soundbench), because running the game from the shared
   checkout crashes whenever another build recompiles under it, and runs `gradlew runVisualSmokeTest -PsmokeSound` there.
2. Records what the default speakers play, with the trailer's recorder (`pip install soundcard`), from when the game says
   `SOUNDBENCH ready` until `SOUNDBENCH done`. Keep the speakers on and every other program silent: whatever they play is
   in the recording. A full run takes about 30 minutes.
3. Leaves capture.wav, meta.json, events.jsonl and the game's log in run/sound-bench/<date-time> of this project, and runs
   tools/sound_audit.py on them, which adds report.md, report.json, a WAV a scene and any A/B files.

A finished run is audited again, without the game, with `python tools/sound_audit.py run/sound-bench/<date-time>`.
"""
import argparse
import collections
import ctypes
import json
import shutil
import subprocess
import sys
import time
import warnings
from pathlib import Path

sys.path.insert(0, str(Path(__file__).with_name("trailer")))
from record import SAMPLE_RATE, SoundRecorder  # noqa: E402
import sound_audit  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
PRIVATE = ROOT.parent / (ROOT.name + "-soundbench")


class BenchRecorder(SoundRecorder):
    """The trailer's recorder, made to look again before it fills a hole.

    The library pads with zeros when no audio has come for four device periods (about 40 ms), but it judges that after
    sleeping, without looking again: a thread woken late pads over audio that is waiting, and the recording grows. Here it
    is only handed over to the library once audio is waiting, or after half a second of none. The recorder's hiss keeps audio
    coming. It reaches into the library, so the audit still looks for padded holes.
    """

    def __init__(self):
        # Half a second of buffer: with the library's 10 ms, a busy machine loses audio
        super().__init__(blocksize=SAMPLE_RATE // 2)

    def _opened(self, recorder):
        library = getattr(recorder, "_record_chunk", None)
        available = getattr(recorder, "_capture_available_frames", None)
        if library is None or available is None:
            return

        def patient():
            asked = time.perf_counter()
            while available() == 0 and time.perf_counter() - asked < 0.5:
                time.sleep(0.002)
            return library()
        recorder._record_chunk = patient


def hurry():
    """Runs this process ahead of the game on Windows. It does next to nothing, but must do it on time or audio is lost."""
    if sys.platform == "win32":
        kernel = ctypes.windll.kernel32
        kernel.GetCurrentProcess.restype = ctypes.c_void_p
        kernel.SetPriorityClass.argtypes = [ctypes.c_void_p, ctypes.c_uint32]
        kernel.SetPriorityClass(kernel.GetCurrentProcess(), 0x00000080)  # HIGH_PRIORITY_CLASS


def sync():
    PRIVATE.mkdir(exist_ok=True)
    # robocopy returns 0-7 for success; /XD also protects the private copy's own run, build and .gradle from /MIR
    result = subprocess.run(["robocopy", str(ROOT), str(PRIVATE), "/MIR", "/XD", "run", "build", ".gradle", ".git", "/NFL",
                             "/NDL", "/NJH", "/NJS", "/NP"], capture_output=True)
    if result.returncode >= 8:
        sys.exit(f"robocopy failed ({result.returncode})")


def play(out, scenes, moving=False):
    """Plays the bench in the private copy and records it into ``out``."""
    hurry()
    game_folder = PRIVATE / "run" / "sound-bench-aeronautics" if moving else PRIVATE / "run"
    events = game_folder / "sound-bench" / "events.jsonl"
    events.unlink(missing_ok=True)
    task = ["runSoundBenchAeronautics"] if moving else ["runVisualSmokeTest", "-PsmokeSound"]
    command = [str(PRIVATE / "gradlew.bat"), *task, "--offline", "--console=plain"]
    if scenes:
        command.append(f"-PsoundScenes={scenes}")
    game = subprocess.Popen(command, cwd=PRIVATE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True,
                            encoding="utf-8", errors="replace")
    recorder, started, done = BenchRecorder(), None, False
    lost = []  # the library's "data discontinuity" warnings: audio the recording is missing
    warnings.simplefilter("always")
    previous = warnings.showwarning

    def count(message, *rest, **more):
        if "discontinuity" in str(message):
            lost.append(1)
        else:
            previous(message, *rest, **more)
    warnings.showwarning = count
    tail = collections.deque(maxlen=40)
    with open(out / "game.log", "w", encoding="utf-8") as log:
        for line in game.stdout:
            log.write(line)
            tail.append(line.rstrip())
            if "SOUNDBENCH ready" in line and started is None:
                started = time.time()
                recorder.start()
                if recorder.error:
                    game.kill()
                    sys.exit(recorder.error)
            if "SOUNDBENCH done" in line and not done:
                done = True
                time.sleep(1.0)
                started = recorder.finish(out / "capture.wav")
            if "SOUNDBENCH" in line:
                print(line.rstrip()[line.index("SOUNDBENCH"):], flush=True)
    game.wait()
    if started is None or not done:
        recorder.finish(out / "capture.wav")
        print("\n".join(tail))
        sys.exit(f"the bench did not finish (gradle exit {game.returncode}): the whole output is in {out / 'game.log'}")
    if recorder.error or not (out / "capture.wav").is_file():
        sys.exit(recorder.error or "nothing was recorded")
    (out / "meta.json").write_text(json.dumps({"sound_started": started, "discontinuities": len(lost)}), encoding="utf-8")
    shutil.copy(events, out / "events.jsonl")


def main():
    parser = argparse.ArgumentParser()
    which = parser.add_mutually_exclusive_group()
    which.add_argument("--scenes", help="only these, comma-separated (default: every scene but the moving one)")
    which.add_argument("--moving", action="store_true", help="only the moving structure, with Create Aeronautics loaded")
    parser.add_argument("--refs", help="folder of recordings of real engines, <scene>.wav, for A/B files")
    parser.add_argument("--compare", help="an earlier run's folder, to show its figures beside these")
    parser.add_argument("--no-copy", action="store_true", help="run in the private copy as it is")
    args = parser.parse_args()
    out = ROOT / "run" / "sound-bench" / time.strftime("%Y%m%d-%H%M%S")
    out.mkdir(parents=True)
    if not args.no_copy:
        sync()
    play(out, args.scenes, args.moving)
    sys.exit(0 if sound_audit.audit(out, args.refs, args.compare) else 1)


if __name__ == "__main__":
    main()
