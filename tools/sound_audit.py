"""Audits a sound bench capture (docs/SMOKE.md, "The sound bench"): the game's own output, lined up with its event log.

    python tools/sound_audit.py RUN_DIR [--refs DIR] [--compare OLD_RUN_DIR]
    python tools/sound_audit.py --self-test

RUN_DIR holds capture.wav (the speakers, 48 kHz stereo), meta.json (when it began) and events.jsonl (from the game), as
tools/sound_bench.py leaves them. It writes report.md, report.json, one WAV a scene under wav/, and, for each scene with a
recording DIR/<scene>.wav of a real engine, an A/B WAV under ab/ (the real one, a second of silence, then the game's, at the
same loudness). --compare puts an earlier run's figures beside this one's.

What it measures: stream underruns (from the log of the sound engine's reads) and silent gaps while an engine runs; clicks
that no firing explains; volume or pitch moving in 50 ms stairs; how long after each firing drawn it is heard; each scene's
loudness against a furnace, a minecart and a Mechanical Press heard from the same spot; the room of sixteen against one of
its engines; how alike one firing sounds to the next; how long newly made audio waits in a stream's queue, which is how
late a change of throttle is heard (worked out from the log, not heard); what making the
sound costs the sound thread; how a moving structure changes what is heard; and whether one engine ever plays two notes at once.

Lag is measured against a clap the game plays through its own sound, so 0 ms means "as late as any sound the game plays at
once". It is not the delay to the ear. The audit finds faults; the author's listening is still what signs off.
"""
import argparse
import json
import math
import sys
import wave
from collections import defaultdict
from pathlib import Path

import numpy as np
from scipy import ndimage, signal

RATE = 48000
NOTE_RATE = 32000
TICK_HZ = 20
# An engine is meant to be heard through these phases, all from the listening distance
# The scenes that move one idling engine's surroundings and nothing else, each phase held against the scene's first
PLACE_SCENES = {"moving": ("held", "leave", "pass", "pass_back")}
RUNNING_PHASES = ("alone", "crowd_early", "crowd_waiting", "crowd_settled", "pace", "stay", "idle", "sweep", "settle", "load_approach", "load_on", "load_off", "restarted", "one", "sixteen",
                  *(phase for phases in PLACE_SCENES.values() for phase in phases))
# The roadmap's budget for the sound thread, as a share of one core
CPU_LIMIT_SHARE = 0.02
# Two notes of one engine for longer than this are stacked copies, not one handing over to the next
STACKED_SECONDS = 1.0
ROOM_LIMIT_DB = 6.0
ROOM_ENGINES = 16
# The soak's phases with all sixteen running, the listener standing in the middle for all but the pacing
CROWD_PHASES = ("crowd_early", "crowd_waiting", "crowd_settled", "pace", "stay")
# A crowd left alone may drift this much louder from one stretch to a later one before it is called growing
GROWTH_LIMIT_DB = 1.5
# Two stretches this alike, sample for sample, are the same audio played again, not two engines sounding alike
REPEAT_LIMIT = 0.97
REPEAT_SECONDS = 1.0
# The empty pad is silent: anything louder than this on it was heard from somewhere it should not be
SILENCE_LIMIT_LUFS = -70.0
# A sound the game plays at once must measure this close to 0 ms late, or the recording and the log are not lined up
CALIBRATION_LIMIT_MS = 60.0


# ---------------------------------------------------------------------------------------------------------------------
# Measures. Each takes mono (or, for loudness, frames x channels) float arrays at RATE, so the self-test can feed it.

def db(value):
    return 20 * math.log10(max(float(value), 1e-12))


def loudness_blocks(audio):
    """K-weighted mean square of each 400 ms block, 100 ms apart (ITU-R BS.1770, which is defined at 48 kHz)."""
    if audio.ndim == 1:
        audio = audio[:, None]
    if len(audio) < int(0.4 * RATE):
        return np.zeros(0)
    shelved = signal.lfilter([1.53512485958697, -2.69169618940638, 1.19839281085285],
                             [1.0, -1.69065929318241, 0.73248077421585], audio, axis=0)
    weighted = signal.lfilter([1.0, -2.0, 1.0], [1.0, -1.99004745483398, 0.99007225036621], shelved, axis=0)
    size, hop = int(0.4 * RATE), int(0.1 * RATE)
    total = np.concatenate([np.zeros((1, weighted.shape[1])), np.cumsum(weighted ** 2, axis=0)])
    starts = np.arange(0, len(weighted) - size + 1, hop)
    return ((total[starts + size] - total[starts]) / size).sum(axis=1)


def lufs_of(power):
    return -0.691 + 10 * math.log10(max(float(power), 1e-12))


def integrated_lufs(audio):
    """Gated integrated loudness, or None when everything is below the absolute gate."""
    blocks = loudness_blocks(audio)
    kept = blocks[blocks > 10 ** ((-70 + 0.691) / 10)]
    if not len(kept):
        return None
    kept = kept[kept > kept.mean() * 0.1]
    return lufs_of(kept.mean())


def momentary_lufs(audio):
    """The loudest 400 ms, for sounds too short or too sparse for the gated figure."""
    blocks = loudness_blocks(audio)
    return lufs_of(blocks.max()) if len(blocks) else None


def frame_rms(mono, seconds):
    size = int(seconds * RATE)
    count = len(mono) // size
    return np.sqrt(np.mean(mono[:count * size].reshape(count, size) ** 2, axis=1)) if count else np.zeros(0)


def runs(mask):
    """(start, end) of each stretch of True."""
    edges = np.flatnonzero(np.diff(np.concatenate([[0], mask.astype(np.int8), [0]])))
    return list(zip(edges[::2], edges[1::2]))


def silent_gaps(mono, shortest):
    """(seconds into the stretch, length) of each silence at least ``shortest`` long, 40 dB under its usual level, or None
    where the sound is not continuous enough to say: a slow single is next to silent between its firings, and only the
    stream log can tell a dropout there from the engine's own quiet."""
    levels = frame_rms(mono, 0.005)
    if not len(levels):
        return None
    if float(np.percentile(levels, 10)) < 10 ** (-65 / 20):
        # Too quiet in places to call, unless nearly all of it is silent
        return [(0.0, len(mono) / RATE)] if float(np.percentile(levels, 99)) < 10 ** (-65 / 20) else None
    floor = max(float(np.median(levels)) * 0.01, 10 ** (-75 / 20))
    return [(a * 0.005, (b - a) * 0.005) for a, b in runs(levels < floor) if (b - a) * 0.005 >= shortest]


def continuity(mono):
    """(fall in dB, share of quiet time) as docs/SOUND-FIT.md defines them: the spread of a 20 ms loudness envelope above
    30 Hz (95th less 5th percentile), and the share of time more than 25 dB under the loud parts."""
    if len(mono) < RATE // 10:
        return None
    heard = signal.sosfilt(signal.butter(2, 30, btype="highpass", fs=RATE, output="sos"), mono)
    size, hop = RATE // 50, RATE // 200
    power = np.concatenate([[0], np.cumsum(heard ** 2)])
    starts = np.arange(0, len(heard) - size, hop)
    level = 10 * np.log10((power[starts + size] - power[starts]) / size + 1e-14)
    loud, low = np.percentile(level, [95, 5])
    return float(loud - low), float(np.mean(level < loud - 25))


def rail_samples(audio):
    """Samples at the 16-bit limit, in either ear."""
    return int(np.sum(np.abs(audio) >= 32767 / 32768))


def padded_holes(mono):
    """(seconds, length) of each run of exact zeros over 10 ms. The speakers always carry the recorder's faint hiss, so these
    are holes the recorder filled, not silence the game played."""
    return [(a / RATE, (b - a) / RATE) for a, b in runs(mono == 0) if b - a > RATE // 100]


def clicks(mono, firings=(), after=0.04, before=0.02):
    """Seconds of each burst above 6 kHz, a few milliseconds long, that stands far out of its surroundings and is not near
    a firing (``firings`` already moved by the measured lag)."""
    if len(mono) < RATE // 10:
        return []
    high = signal.sosfiltfilt(signal.butter(4, 6000, "highpass", fs=RATE, output="sos"), mono)
    size = RATE // 1000
    count = len(high) // size
    peaks = np.abs(high[:count * size]).reshape(count, size).max(axis=1)
    around = ndimage.median_filter(peaks, 201, mode="reflect")
    found = []
    for a, b in runs((peaks > 10 * (around + 1e-4)) & (peaks > 10 ** (-45 / 20))):
        at = a / 1000
        if b - a <= 4 and not any(f - before <= at <= f + after for f in firings):
            found.append(at)
    return found


def audio_stairs(mono):
    """Seconds at which three or more level jumps in a row come 50 ms apart with the level flat between: a volume moving
    once a game tick. Only a steady tone shows this; the log's levels are the surer measure for the game."""
    levels = 20 * np.log10(frame_rms(mono, 0.01) + 1e-9)
    jumps = []
    i = 2
    while i < len(levels) - 2:
        jump = levels[i + 1] - levels[i - 1]
        flat = abs(levels[i - 1] - levels[i - 2]) < 0.3 and abs(levels[i + 2] - levels[i + 1]) < 0.3
        if abs(jump) > 0.8 and flat and levels[i] > -60:
            jumps.append((i, math.copysign(1, jump)))
            i += 2
        i += 1
    found, chain = [], []
    for jump in jumps:
        if chain and 4 <= jump[0] - chain[-1][0] <= 6 and jump[1] == chain[-1][1]:
            chain.append(jump)
        else:
            if len(chain) >= 3:
                found.append(chain[0][0] * 0.01)
            chain = [jump]
    if len(chain) >= 3:
        found.append(chain[0][0] * 0.01)
    return found


def level_steps(events):
    """From the logged volume and pitch of one tickable sound, one entry a tick: the jumps big enough to hear as a stair,
    more than 1.5 dB or half a semitone at once while audible. Returns (count, worst dB, worst semitones, ticks seen)."""
    count, worst_db, worst_semitones = 0, 0.0, 0.0
    for before, now in zip(events, events[1:]):
        if now["t"] - before["t"] > 150 or min(before["v"], now["v"]) < 0.02:
            continue
        loud = abs(db(now["v"]) - db(before["v"]))
        pitch = abs(12 * math.log2(now["p"] / before["p"])) if before["p"] > 0 and now["p"] > 0 else 0.0
        if loud > 1.5 or pitch > 0.5:
            count += 1
        worst_db, worst_semitones = max(worst_db, loud), max(worst_semitones, pitch)
    return count, worst_db, worst_semitones, len(events)


def underruns(reads):
    """From one stream's reads [(ms, samples)]: the times its queue ran dry, as (ms, seconds dry), and the least audio it
    had queued at a read after the first few, in seconds (what a smaller chunk would have to spare)."""
    dry, ahead_at = queue(reads)
    return dry, min(ahead_at[6:]) if len(ahead_at) > 6 else None


def queue(reads):
    """From one stream's reads: the times its queue ran dry, and at each read the audio waiting to be played, in seconds:
    how long what is made at that read waits before it is heard."""
    dry, waiting = [], []
    start, queued = None, 0.0
    for t, samples in reads:
        if start is None:
            start = t
        ahead = queued - (t - start) / 1000
        if ahead < -0.02:
            dry.append((t, -ahead))
            start, queued, ahead = t, 0.0, 0.0
        waiting.append(ahead)
        queued += samples / NOTE_RATE
    return dry, waiting


def onset_rise(mono):
    """How steeply the level rises, once a millisecond."""
    energy = ndimage.uniform_filter1d(mono ** 2, RATE // 250)
    envelope = np.sqrt(np.maximum(energy[::RATE // 1000], 0))
    rise = np.zeros_like(envelope)
    rise[4:] = np.maximum(envelope[4:] - envelope[:-4], 0)
    return rise


def lags(mono, firings):
    """For each firing drawn (seconds into the stretch), how long after it the sharpest onset is heard. Each is looked for
    within 0.45 of the gap to its neighbours (at most 0.35 s), so a lag is only known to within that: firings closer than
    90 ms are not measured. Returns (measured [(firing, lag, window)], how many were too close, how many had no onset)."""
    rise = onset_rise(mono)
    ordered = sorted(firings)
    found, dense, unheard = [], 0, 0
    for i, at in enumerate(ordered):
        gaps = [abs(ordered[j] - at) for j in (i - 1, i + 1) if 0 <= j < len(ordered)]
        window = min(0.35, 0.45 * min(gaps)) if gaps else 0.35
        if window < 0.04:
            dense += 1
            continue
        low, high = int((at - window) * 1000), int((at + window) * 1000)
        if low < 0 or high > len(rise):
            continue
        stretch = rise[low:high]
        best = int(np.argmax(stretch))
        if stretch[best] < 1e-5 or stretch[best] < 3 * np.median(stretch) + 1e-6:
            unheard += 1
            continue
        # The rise is taken over 4 ms and the energy smoothed over 4 ms: its peak is about 2 ms into the onset
        found.append((at, (low + best - 2) / 1000 - at, window))
    return found, dense, unheard


def alikeness(mono, onsets):
    """How closely each firing heard matches the one before: the best correlation of their first 80 ms, within 2 ms of
    shift, for firings at least 90 ms apart."""
    size, shift = int(0.08 * RATE), int(0.002 * RATE)
    ordered = sorted(onsets)
    scores = []
    for first, second in zip(ordered, ordered[1:]):
        a, b = int(first * RATE), int(second * RATE)
        if second - first < 0.09 or a < 0 or b - shift < 0 or b + size + shift > len(mono) or a + size > len(mono):
            continue
        one = mono[a:a + size]
        wide = mono[b - shift:b + size + shift]
        products = signal.correlate(wide, one, mode="valid")
        energy = np.concatenate([[0], np.cumsum(wide ** 2)])
        norms = np.sqrt(np.maximum(energy[size:] - energy[:-size], 0) * np.sum(one ** 2))
        scores.append(float(np.max(products / np.maximum(norms, 1e-12))))
    return scores


def brightness(mono):
    """The middle of the sound's spectrum in Hz (the power-weighted mean frequency, above 20 Hz): what the air
    takes the top off. None for silence."""
    if len(mono) < RATE // 2:
        return None
    frequencies, power = signal.welch(mono, RATE, nperseg=4096)
    kept = frequencies >= 20
    total = float(power[kept].sum())
    return float((frequencies[kept] * power[kept]).sum() / total) if total > 1e-14 else None


def cost(renders, seconds):
    """What a stretch's sound cost, from the log's [(samples, nanos)] of audio made: its share of one core over
    ``seconds``, and the slowest single piece. Clock time, so a thread the machine set
    aside half way through counts in full."""
    if not renders or seconds <= 0:
        return None
    samples, spent = sum(n for n, _ in renders), sum(ns for _, ns in renders)
    return {"sound_core_share": spent / 1e9 / seconds, "ns_per_sample": spent / max(samples, 1),
            "worst_read_ms": max(ns for _, ns in renders) / 1e6}


def stacked_seconds(levels):
    """From the logged (ms, id) of one engine's note: for how long, in all, two or more of them were playing at once."""
    ticks = defaultdict(set)
    for t, which in levels:
        ticks[t // 100].add(which)
    return sum(1 for ids in ticks.values() if len(ids) > 1) * 0.1


# ---------------------------------------------------------------------------------------------------------------------
# A run

def exact_repeat(mono, excerpts=8):
    """The closest any of ``excerpts`` stretches of ``mono`` comes to being played again later, 0 to 1: the best normalised
    correlation of each, at any offset past its own end. A loop scores near 1. None if there is too little to judge."""
    length = int(REPEAT_SECONDS * RATE)
    if len(mono) < 4 * length:
        return None
    best = None
    for start in np.linspace(0, len(mono) - 3 * length, excerpts).astype(int):
        piece = mono[start:start + length] - mono[start:start + length].mean()
        later = mono[start + length:].astype(float)
        size = np.sqrt(np.sum(piece ** 2))
        if size < 1e-4:
            continue
        top = signal.fftconvolve(later, piece[::-1], mode="valid")
        running = np.concatenate([[0.0], np.cumsum(later ** 2)])
        under = np.sqrt(np.maximum(running[length:] - running[:-length], 0)) * size
        loud = under > 0.1 * size * size
        if loud.any():
            score = float(np.max(top[loud] / under[loud]))
            best = score if best is None else max(best, score)
    return best


def soak_findings(levels, repeat, references):
    """What the soak says about the rules for many engines: ``levels`` is each phase's integrated LUFS, ``repeat`` the
    closest the standing stretch came to repeating, ``references`` the game's other sounds. Returns the figures and the
    faults."""
    figures, faults = {}, []
    alone = levels.get("alone")
    crowd = [levels[name] for name in CROWD_PHASES if name != "pace" and levels.get(name) is not None]
    if alone is not None and crowd:
        figures["over_one_db"] = rounded(max(crowd) - alone)
        if figures["over_one_db"] > ROOM_LIMIT_DB:
            faults.append(f"soak: the crowd round the listener is {figures['over_one_db']} dB above one engine from the same "
                          f"spot (limit {ROOM_LIMIT_DB})")
    early = levels.get("crowd_early")
    for name, key in (("crowd_settled", "settled_db"), ("stay", "stayed_db")):
        if early is not None and levels.get(name) is not None:
            figures[key] = rounded(levels[name] - early)
    held = levels.get("crowd_settled")
    if held is not None and levels.get("stay") is not None and levels["stay"] - held > GROWTH_LIMIT_DB:
        faults.append(f"soak: the crowd grew {rounded(levels['stay'] - held)} dB louder while it was left alone")
    if early is not None and held is not None and held - early > GROWTH_LIMIT_DB:
        faults.append(f"soak: the crowd got {rounded(held - early)} dB louder as it settled")
    figures["exact_repeat"] = None if repeat is None else rounded(repeat, 3)
    if repeat is not None and repeat > REPEAT_LIMIT:
        faults.append(f"soak: a stretch of the crowd was played again almost sample for sample ({repeat:.3f})")
    others = [v for name, v in references.items() if name != "silence" and v is not None]
    if others and crowd:
        figures["over_loudest_other_db"] = rounded(max(crowd) - max(others))
    return figures, faults


def read_wav(path):
    with wave.open(str(path), "rb") as handle:
        if handle.getsampwidth() != 2:
            sys.exit(f"{path}: only 16 bit WAVs are read")
        data = np.frombuffer(handle.readframes(handle.getnframes()), dtype="<i2")
        return data.reshape(-1, handle.getnchannels()).astype(np.float64) / 32768, handle.getframerate()


def write_wav(path, audio):
    path.parent.mkdir(parents=True, exist_ok=True)
    if audio.ndim == 1:
        audio = audio[:, None]
    with wave.open(str(path), "wb") as handle:
        handle.setnchannels(audio.shape[1])
        handle.setsampwidth(2)
        handle.setframerate(RATE)
        handle.writeframes((np.clip(audio, -1, 1) * 32767).astype("<i2").tobytes())


def find_clap(mono, expected):
    """Seconds into the recording at which a clap starts, looked for within 2.5 s of where it should be, or None."""
    low, high = max(0, int((expected - 2.5) * RATE)), min(len(mono), int((expected + 2.5) * RATE))
    if high <= low:
        return None
    level = np.abs(mono[low:high])
    top = int(np.argmax(level))
    if level[top] < 0.02:
        return None
    back = max(0, top - int(0.04 * RATE))
    return (low + back + int(np.argmax(level[back:top + 1] >= level[top] / 2))) / RATE


class Run:
    def __init__(self, folder):
        self.folder = Path(folder)
        audio, rate = read_wav(self.folder / "capture.wav")
        if rate != RATE:
            sys.exit(f"capture.wav is {rate} Hz, not {RATE}")
        self.audio = audio
        self.mono = audio.mean(axis=1)
        meta = json.loads((self.folder / "meta.json").read_text(encoding="utf-8"))
        self.started = meta["sound_started"]
        with open(self.folder / "events.jsonl", encoding="utf-8") as handle:
            self.events = sorted((json.loads(line) for line in handle if line.strip()), key=lambda e: e["t"])
        self.by_kind = defaultdict(list)
        for event in self.events:
            self.by_kind[event["e"]].append(event)
        self.phases = []  # (scene, phase, ms from, ms to)
        marks = self.by_kind["phase"]
        for mark, following in zip(marks, marks[1:] + [{"t": self.events[-1]["t"]}]):
            self.phases.append((mark["scene"], mark["phase"], mark["t"], following["t"]))
        self.problems = []
        self.claps = []
        for event in self.by_kind["clap"]:
            expected = event["t"] / 1000 - self.started
            found = find_clap(self.mono, expected)
            if found is None:
                self.problems.append("a sync clap was not found in the recording")
            else:
                self.claps.append((event["t"], found - expected))
        if not self.claps:
            self.problems.append("no sync clap: the recording is silent, or is not of this run, so lags cannot be trusted")
        if len(self.claps) == 2 and abs(self.claps[1][1] - self.claps[0][1]) > 0.15:
            self.problems.append(f"the two claps disagree by {1000 * (self.claps[1][1] - self.claps[0][1]):.0f} ms: "
                                 "the recorder lost or gained samples, so lags cannot be trusted")
        if meta.get("discontinuities"):
            self.problems.append(f"the recorder reported losing audio {meta['discontinuities']} times")
        # Holes between the scenes spoil nothing; the recorder fills them to length, so the timing holds
        scenes = [(start, end) for scene, _, start, end in self.phases if scene != "setup"]
        holes = [h for start, end in scenes for h in padded_holes(self.clip(start, end))]
        if holes:
            self.problems.append(f"the recorder filled {len(holes)} holes with zeros in the scenes "
                                 f"({sum(h[1] for h in holes):.2f} s): gaps, clicks and lags may be the recorder's")
        if self.by_kind["fail"]:
            self.problems.append("a bench step threw in the game: see its log")
        for event in self.by_kind["timeout"]:
            self.problems.append(f"{event['scene']} {event['phase']} never reached its goal and gave up")

    def at(self, ms):
        """Seconds into the recording of the moment ``ms`` of the log, between the two claps' corrections."""
        offset = 0.0
        if len(self.claps) == 1:
            offset = self.claps[0][1]
        elif self.claps:
            offset = float(np.interp(ms, [c[0] for c in self.claps], [c[1] for c in self.claps]))
        return ms / 1000 - self.started + offset

    def clip(self, start_ms, end_ms, mono=True):
        source = self.mono if mono else self.audio
        return source[max(0, int(self.at(start_ms) * RATE)):max(0, int(self.at(end_ms) * RATE))]

    def between(self, kind, start_ms, end_ms):
        return [e for e in self.by_kind[kind] if start_ms <= e["t"] < end_ms]


def rounded(value, places=1):
    return None if value is None else round(float(value), places)


def firing_times(run, start, end):
    return [run.at(e["t"]) - run.at(start) for e in run.between("firing", start, end)]


def scene_lag(run, phases):
    """The one lag a scene's firings are heard at: the median over every one measured while its engine ran. One phase has too
    few, and too noisy, to say where a firing's crack will be."""
    found = []
    for phase, start, end in phases:
        firings = firing_times(run, start, end)
        if phase in RUNNING_PHASES and firings:
            found += [lag for _, lag, _ in lags(run.clip(start, end), firings)[0]]
    return float(np.median(found)) if len(found) >= 5 else 0.0


def calibration(run):
    """How late the Mechanical Press's activations are heard, in ms: sounds played at once, at moments the log has, so this
    is the error in lining the recording up with the log. None without the reference scene."""
    for scene, phase, start, end in run.phases:
        if scene == "reference" and phase == "press":
            played = [run.at(e["t"]) - run.at(start) for e in run.between("play", start, end) if "press" in e["sound"]]
            # One activation may be several sounds started together
            played = [t for i, t in enumerate(sorted(played)) if i == 0 or t - sorted(played)[i - 1] > 0.1]
            measured = lags(run.clip(start, end), played)[0] if played else []
            return rounded(1000 * float(np.median([lag for _, lag, _ in measured]))) if len(measured) >= 3 else None
    return None


def analyse_phase(run, phase, start, end, heard_after):
    mono = run.clip(start, end)
    stereo = run.clip(start, end, mono=False)
    result = {"seconds": rounded((end - start) / 1000), "lufs": rounded(integrated_lufs(stereo)),
              "momentary": rounded(momentary_lufs(stereo)),
              "peak_db": rounded(db(np.abs(mono).max())) if len(mono) else None,
              "brightness_hz": rounded(brightness(mono), 0), "rail_samples": rail_samples(stereo)}
    states = run.between("state", start, end)
    if states:
        result["rpm"] = [rounded(min(s["rpm"] for s in states)), rounded(max(s["rpm"] for s in states))]
        shares = [s["load"] / s["available"] for s in states if s["available"] > 0]
        result["load_share"] = rounded(max(shares), 2) if shares else None
    firings = firing_times(run, start, end)
    result["firings"] = len(firings)
    typical = float(np.median(np.diff(sorted(firings)))) if len(firings) > 2 else None
    measured, dense, unheard = lags(mono, firings) if firings else ([], 0, 0)
    if len(measured) >= 5:
        values = np.array([lag for _, lag, _ in measured])
        middle = float(np.median(values))
        result["lag"] = {"median_ms": rounded(1000 * middle), "p90_off_ms": rounded(1000 * np.percentile(np.abs(values - middle), 90)),
                         "measured": len(measured), "too_close": dense, "no_onset": unheard,
                         "known_within_ms": rounded(1000 * float(np.median([w for _, _, w in measured])), 0)}
    else:
        if firings:
            result["lag"] = {"median_ms": None, "measured": len(measured), "too_close": dense, "no_onset": unheard}
    result["clicks"] = [rounded(t, 3) for t in clicks(mono, [f + heard_after for f in firings], after=0.07, before=0.05)]
    result["stairs_heard"] = [rounded(t, 2) for t in audio_stairs(mono)]
    if phase in RUNNING_PHASES:
        held = continuity(mono)
        if held:
            result["fall_db"], result["quiet_share"] = rounded(held[0]), rounded(held[1], 3)
        shortest = max(0.06, 1.5 * typical) if typical else 0.06
        gaps = silent_gaps(mono, shortest)
        if gaps is None:
            result["gaps_not_judged"] = True
        else:
            result["gaps"] = [[rounded(a, 2), rounded(b, 3)] for a, b in gaps]
        scores = alikeness(mono, [f + lag for f, lag, _ in measured])
        if len(scores) >= 5:
            result["alike"] = {"median": rounded(np.median(scores), 3), "over_098": rounded(np.mean(np.array(scores) > 0.98), 2),
                               "pairs": len(scores)}
    return result


def analyse(run):
    scenes = {}
    for scene, phase, start, end in run.phases:
        if scene == "setup":
            continue
        entry = scenes.setdefault(scene, {"start": start, "end": end, "phases": {}, "marks": []})
        entry["end"] = end
        if phase not in ("build", "time", "assemble", "clear"):
            entry["marks"].append((phase, start, end))
    for entry in scenes.values():
        marks = entry.pop("marks")
        heard_after = scene_lag(run, marks)
        entry["clicks_excused_at_lag_ms"] = rounded(1000 * heard_after)
        for phase, start, end in marks:
            entry["phases"][phase] = analyse_phase(run, phase, start, end, heard_after)
        start, end = entry.pop("start"), entry.pop("end")
        entry["span"] = [start, end]
        streams = defaultdict(list)
        for event in run.between("read", start, end):
            streams[event["id"]].append((event["t"], event["n"]))
        dry, least, waits = [], [], []
        for reads in streams.values():
            # The first reads fill the queue, so they say nothing about how a running stream stands
            waits += queue(reads)[1][6:]
            gone, spare = underruns(reads)
            dry += [[rounded(run.at(t) - run.at(start), 2), rounded(seconds, 3)] for t, seconds in gone]
            if spare is not None:
                least.append(spare)
        entry["streams"] = {"count": len(streams), "underruns": dry, "least_queued_s": rounded(min(least), 3) if least else None}
        entry["wait_ms"] = {"median": rounded(1000 * np.median(waits), 0), "p95": rounded(1000 * np.percentile(waits, 95), 0)} \
            if waits else None
        voices = defaultdict(list)
        for event in run.between("level", start, end):
            voices[(event["voice"], event["id"])].append(event)
        steps = {}
        for (voice, _), events in voices.items():
            count, loud, pitch, ticks = level_steps(events)
            total = steps.setdefault(voice, {"steps": 0, "worst_db": 0.0, "worst_semitones": 0.0, "ticks": 0})
            total["steps"] += count
            total["ticks"] += ticks
            total["worst_db"] = rounded(max(total["worst_db"], loud))
            total["worst_semitones"] = rounded(max(total["worst_semitones"], pitch), 2)
        entry["level_steps"] = steps
        spent = cost([(e["n"], e["ns"]) for e in run.between("render", start, end)], (end - start) / 1000)
        entry["cost"] = None if spent is None else {k: None if v is None else round(float(v), 5) for k, v in spent.items()}
        entry["notes_stacked_s"] = rounded(stacked_seconds(
            [(e["t"], e["id"]) for e in run.between("level", start, end) if e["voice"] == "note"]))
    report = {"problems": run.problems, "clap_offsets_ms": [rounded(1000 * o) for _, o in run.claps], "scenes": scenes}
    room = scenes.get("room", {}).get("phases", {})
    if room.get("one", {}).get("lufs") is not None and room.get("sixteen", {}).get("lufs") is not None:
        over = rounded(room["sixteen"]["lufs"] - room["one"]["lufs"])
        report["room"] = {"over_one_db": over, "limit_db": ROOM_LIMIT_DB, "within": over <= ROOM_LIMIT_DB}
    reference = scenes.get("reference", {}).get("phases", {})
    report["references"] = {name: reference[name]["momentary"] for name in ("silence", "furnace", "minecart", "press")
                            if name in reference}
    silence = report["references"].get("silence")
    if silence is not None and silence > SILENCE_LIMIT_LUFS:
        quiet = next((start, end) for scene, phase, start, end in run.phases if (scene, phase) == ("reference", "silence"))
        started = sorted({e["sound"] for e in run.between("play", *quiet)})
        who = "the game started " + ", ".join(started) if started else \
            "the game started no sound in it, so it was another program, or one of the game's already going"
        run.problems.append(f"the empty pad's silence measured {silence} LUFS: {who}")
    for scene, phase, start, end in run.phases:
        if (scene, phase) == ("room", "sixteen"):
            heard = len({e["engine"] for e in run.between("firing", start, end)})
            if "room" in report:
                report["room"]["engines_firing"] = heard
            if heard != ROOM_ENGINES:
                run.problems.append(f"the room of sixteen had {heard} engines firing: its level says nothing about sixteen")
    report["faults"] = []
    gone = scenes.get("room", {}).get("phases", {}).get("gone")
    if gone:
        # The first two seconds are the fade and the clearing
        for scene, phase, start, end in run.phases:
            if (scene, phase) == ("room", "gone"):
                left = momentary_lufs(run.clip(start + 2000, end, mono=False))
                if left is not None and left > SILENCE_LIMIT_LUFS:
                    report["faults"].append(f"the room is still sounding, at {left:.1f} LUFS, after all its engines are gone")
    # The room of sixteen is meant to have several notes at once; every other scene has one engine
    for name, entry in scenes.items():
        if name not in ("room", "soak", "reference") and entry["notes_stacked_s"] > STACKED_SECONDS:
            report["faults"].append(f"{name}: one engine played two or more notes at once for {entry['notes_stacked_s']} s")
    soak = scenes.get("soak", {}).get("phases", {})
    if soak:
        stay = next(((start, end) for scene, phase, start, end in run.phases if (scene, phase) == ("soak", "stay")), None)
        others = {name: reference[name].get("lufs") for name in report["references"]}
        report["soak"], found = soak_findings({name: p.get("lufs") for name, p in soak.items()},
                                              exact_repeat(run.clip(*stay)) if stay else None, others)
        report["faults"] += found
        if stay:
            heard = len({e["engine"] for e in run.between("firing", *stay)})
            report["soak"]["engines_firing"] = heard
            if heard != ROOM_ENGINES:
                run.problems.append(f"the soak had {heard} engines firing: its levels say nothing about sixteen")
    report["calibration_ms"] = calibration(run)
    if report["calibration_ms"] is not None and abs(report["calibration_ms"]) > CALIBRATION_LIMIT_MS:
        run.problems.append(f"the Mechanical Press, played at once, measured {report['calibration_ms']} ms late: the recording "
                            "and the log are not lined up, so lags cannot be trusted")
    return report


def totals(entry):
    phases = entry["phases"].values()
    return {"underruns": len(entry["streams"]["underruns"]), "gaps": sum(len(p.get("gaps", [])) for p in phases),
            "clicks": sum(len(p["clicks"]) for p in phases),
            "steps": sum(v["steps"] for v in entry["level_steps"].values()),
            "idle_lufs": entry["phases"].get("idle", entry["phases"].get("one", {})).get("lufs"),
            "lag_ms": entry["phases"].get("idle", entry["phases"].get("one", {})).get("lag", {}).get("median_ms"),
            "alike": entry["phases"].get("idle", entry["phases"].get("one", {})).get("alike", {}).get("median")}


def shown(value):
    return "–" if value is None else str(value)


def wait_lines(report):
    waits = {name: entry["wait_ms"] for name, entry in report["scenes"].items() if entry.get("wait_ms")}
    if not waits:
        return []
    lines = ["", "## How late a change is heard", "",
             "How long audio waits in a stream's queue between being made and being played: a change of throttle or load cannot "
             "be heard sooner than this after the synthesizer is told. Worked out from the log of the stream's reads, not "
             "heard. Not in it: the synthesizer's own follow, the sound card's delay, and what the look-ahead wins back.", "",
             "| Scene | Median ms | 95th percentile ms |", "|---|---|---|"]
    return lines + [f"| {name} | {w['median']} | {w['p95']} |" for name, w in waits.items()]


def cost_lines(report):
    spent = {name: entry["cost"] for name, entry in report["scenes"].items() if entry.get("cost")}
    if not spent:
        return []
    lines = ["", "## What the sound costs", "",
             f"The sound thread's budget is {100 * CPU_LIMIT_SHARE:.0f}% of one core. Averaged over each whole scene, by the "
             "clock, so time the thread was set aside counts too. The note streams and the room voice only: the game's own "
             "mixing is not in it.", "",
             "| Scene | Sound thread | ns a sample | Slowest read ms |", "|---|---|---|---|"]
    for name, c in spent.items():
        over = " **over**" if c["sound_core_share"] > CPU_LIMIT_SHARE else ""
        lines.append(f"| {name} | {100 * c['sound_core_share']:.2f}%{over} | {c['ns_per_sample']:.0f} | "
                     f"{c['worst_read_ms']:.2f} |")
    return lines


def place_lines(report):
    lines = []
    for name, order in PLACE_SCENES.items():
        phases = report["scenes"].get(name, {}).get("phases", {})
        first = phases.get(order[0], {})
        if not first:
            continue
        lines += ["", f"## {name.capitalize()}: against `{order[0]}`", "",
                  "| Phase | LUFS | Change dB | Brightness Hz |", "|---|---|---|---|"]
        for phase in order:
            p = phases.get(phase)
            if p:
                change = None if p["lufs"] is None or first["lufs"] is None else rounded(p["lufs"] - first["lufs"])
                lines.append(f"| {phase} | {shown(p['lufs'])} | {shown(change)} | {shown(p.get('brightness_hz'))} |")
    return lines


def continuity_lines(report):
    rows = []
    for name, entry in report["scenes"].items():
        for phase, p in entry["phases"].items():
            if name != "reference" and (p.get("rail_samples") or "fall_db" in p):
                rows.append(f"| {name} | {phase} | {shown(p.get('fall_db'))} | {shown(p.get('quiet_share'))} | "
                            f"{p.get('rail_samples', 0)} |")
    if not rows:
        return []
    return ["", "## Continuity and clipping", "",
            "Targets (docs/SOUND-FIT.md): fall at most 20 dB from 5 firings a second and 25 dB under that, no quiet time, and no "
            "sample at the 16-bit limit. Taken from the speakers, so everything the game plays is in it.", "",
            "| Scene | Phase | Fall dB | Quiet share | Samples at the limit |", "|---|---|---|---|---|"] + rows


def markdown(report, old=None):
    lines = ["# Sound bench report", ""]
    if report["problems"]:
        lines += ["**Do not trust this run until these are explained:**", ""] + [f"- {p}" for p in report["problems"]] + [""]
    if report.get("faults"):
        lines += ["## Faults found", ""] + [f"- **{fault}**" for fault in report["faults"]] + [""]
    calibrated = report.get("calibration_ms")
    lines += ["Timing check: the Mechanical Press, played at once, is heard "
              + (f"{calibrated} ms after the log has it (it should be near 0, and every lag below is off by about as much)."
                 if calibrated is not None else "— not checked (it needs the reference scene)."), ""]
    claps = report.get("clap_offsets_ms", [])
    if len(claps) == 2:
        lines += [f"The two sync claps are {abs(claps[1] - claps[0]):.0f} ms apart in where they put the recording. The audit "
                  "takes that to build up evenly; if it did not, a lag in mid-run is uncertain by up to that much.", ""]
    lines += ["## Scenes", "",
              "Underruns, gaps, clicks and steps are counts over the whole scene; loudness, lag and likeness are of the idle phase"
              " (the room: its one engine)." + (" In brackets: the run compared with." if old else ""), "",
              "| Scene | Underruns | Gaps | Clicks | Level steps | Idle LUFS | Lag ms | Likeness |",
              "|---|---|---|---|---|---|---|---|"]
    for name, entry in report["scenes"].items():
        if name == "reference":
            continue
        now = totals(entry)
        before = totals(old["scenes"][name]) if old and name in old["scenes"] else None
        cells = [shown(now[k]) + (f" ({shown(before[k])})" if before else "") for k in now]
        lines.append(f"| {name} | " + " | ".join(cells) + " |")
    lines += ["", "## Against the game's other sounds", "",
              "The loudest 400 ms of each, from the same distance (LUFS). `silence` is the empty pad: anything much above -70 there"
              " was another program, and the run's levels cannot be trusted.", ""]
    lines += [f"- {name}: {shown(value)}" for name, value in report["references"].items()] or ["- not recorded in this run"]
    if "room" in report:
        room = report["room"]
        lines += ["", "## The room of sixteen", "",
                  f"{room.get('engines_firing', '?')} engines firing, {room['over_one_db']} dB above one of them "
                  f"(limit {room['limit_db']} dB): "
                  + ("within." if room["within"] else "**over the limit.**")]
    if "soak" in report:
        soak = report["soak"]
        lines += ["", "## The soak among sixteen", "",
                  f"{soak.get('engines_firing', '?')} engines firing, the listener standing among them. Against one of them "
                  f"alone from the same spot: {shown(soak.get('over_one_db'))} dB (limit {ROOM_LIMIT_DB}). Against the crowd as "
                  f"it came up: settled {shown(soak.get('settled_db'))} dB, after pacing through it and standing again "
                  f"{shown(soak.get('stayed_db'))} dB. Closest any second came to being played again: "
                  f"{shown(soak.get('exact_repeat'))} (limit {REPEAT_LIMIT}). Against the loudest of the game's other sounds, "
                  f"each from 6 blocks: {shown(soak.get('over_loudest_other_db'))} dB."]
    lines += continuity_lines(report) + wait_lines(report) + cost_lines(report) + place_lines(report)
    lines += ["", "## Each phase", ""]
    for name, entry in report["scenes"].items():
        if name == "reference":
            continue
        streams = entry["streams"]
        lines += [f"### {name}", "", f"{streams['count']} streams; underruns at [seconds, length]: {streams['underruns'] or 'none'};"
                  f" least queued: {shown(streams['least_queued_s'])} s."]
        for voice, steps in entry["level_steps"].items():
            lines.append(f"`{voice}`: {steps['steps']} stairs in {steps['ticks']} ticks, worst {steps['worst_db']} dB,"
                         f" {steps['worst_semitones']} semitones.")
        lines += ["", "| Phase | LUFS | rpm | Load | Lag ms (± p90, window) | Clicks at s | Gaps [s, length] | Likeness |",
                  "|---|---|---|---|---|---|---|---|"]
        for phase, p in entry["phases"].items():
            lag = p.get("lag")
            if not lag:
                told = "–"
            elif lag["median_ms"] is None:
                told = f"not measured ({lag['too_close']} too close, {lag['no_onset']} unheard)"
            else:
                told = f"{lag['median_ms']} (±{lag['p90_off_ms']}, {lag['known_within_ms']}; n={lag['measured']})"
            at = p["clicks"][:6]
            more = f" +{len(p['clicks']) - 6}" if len(p["clicks"]) > 6 else ""
            rpm = f"{p['rpm'][0]}–{p['rpm'][1]}" if "rpm" in p else "–"
            lines.append(f"| {phase} | {shown(p['lufs'])} | {rpm} | {shown(p.get('load_share'))} | {told} | "
                         f"{(str(at) + more) if at else '–'} | "
                         f"{'too quiet to judge' if p.get('gaps_not_judged') else p.get('gaps') or '–'} | "
                         f"{shown(p.get('alike', {}).get('median'))} |")
        lines.append("")
    return "\n".join(lines)


def export(run, report, refs):
    for name, entry in report["scenes"].items():
        clip = run.clip(*entry["span"], mono=False)
        write_wav(run.folder / "wav" / f"{name}.wav", clip)
        real = Path(refs) / f"{name}.wav" if refs else None
        if not real or not real.is_file():
            continue
        other, rate = read_wav(real)
        if rate != RATE:
            other = signal.resample_poly(other, RATE, rate, axis=0)
        other = np.repeat(other[:, :1], 2, axis=1) if other.shape[1] == 1 else other[:, :2]
        ours, theirs = integrated_lufs(clip), integrated_lufs(other)
        if ours is not None and theirs is not None:
            other = other * 10 ** ((ours - theirs) / 20)
        write_wav(run.folder / "ab" / f"{name}.wav", np.concatenate([other, np.zeros((RATE, 2)), clip]))


def audit(folder, refs=None, compare=None):
    run = Run(folder)
    report = analyse(run)
    export(run, report, refs)
    old = json.loads((Path(compare) / "report.json").read_text(encoding="utf-8")) if compare else None
    (run.folder / "report.json").write_text(json.dumps(report, indent=1), encoding="utf-8")
    (run.folder / "report.md").write_text(markdown(report, old), encoding="utf-8")
    print(f"report: {run.folder / 'report.md'}")
    for fault in report["faults"]:
        print("FAULT:", fault)
    for problem in report["problems"]:
        print("PROBLEM:", problem)
    return not report["problems"]


# ---------------------------------------------------------------------------------------------------------------------
# The self-test: made-up faults the measures must find, and clean sound they must pass.

def thumps(seconds, times, seed=1):
    """A made-up slow engine: a decaying 90 Hz thump with a little noise at each of ``times``, over a faint hum."""
    random = np.random.default_rng(seed)
    clock = np.arange(int(seconds * RATE)) / RATE
    out = 0.01 * np.sin(2 * np.pi * 55 * clock)
    for at in times:
        start = int(at * RATE)
        length = min(int(0.15 * RATE), len(out) - start)
        t = clock[:length]
        attack = np.minimum(1, t / 0.002)
        noise = signal.sosfilt(signal.butter(2, 2000, fs=RATE, output="sos"), random.standard_normal(length))
        out[start:start + length] += attack * np.exp(-t / 0.03) * (0.4 * np.sin(2 * np.pi * 90 * t) + 0.1 * noise)
    return out


def self_test():
    times = [0.5 + 0.4 * i for i in range(20)]
    clean = thumps(9, times)

    tone = 0.5 * np.sin(2 * np.pi * 997 * np.arange(RATE * 3) / RATE)
    assert abs(integrated_lufs(np.stack([tone * 2, 0 * tone], axis=1)) - -3.01) < 0.1, "loudness of a full scale 997 Hz sine"
    assert integrated_lufs(np.zeros(RATE)) is None

    steady_noise = np.random.default_rng(9).standard_normal(4 * RATE) * 0.1
    fall, quiet = continuity(steady_noise)
    assert fall < 3 and quiet == 0, (fall, quiet)
    fall, quiet = continuity(thumps(9, times))
    assert fall > 25 and quiet > 0.3, (fall, quiet)
    assert rail_samples(np.array([[1.0, 0.0], [-1.0, 0.5]])) == 2

    assert silent_gaps(clean, 0.06) == [], "no gap in clean sound"
    sparse = thumps(9, times) - 0.01 * np.sin(2 * np.pi * 55 * np.arange(9 * RATE) / RATE)
    assert silent_gaps(sparse, 0.06) is None, "quiet between a slow engine's firings is not judged"
    assert silent_gaps(np.zeros(RATE), 0.06) == [(0.0, 1.0)], "all silence is a gap"
    hissed = clean + 1e-4 * np.random.default_rng(5).standard_normal(len(clean))
    assert padded_holes(hissed) == [], "hiss is not a hole"
    hissed[RATE:RATE + 960] = 0
    assert len(padded_holes(hissed)) == 1, "20 ms of exact zeros is one"
    broken = clean.copy()
    broken[int(3.2 * RATE):int(3.35 * RATE)] = 0
    gaps = silent_gaps(broken, 0.06)
    assert len(gaps) == 1 and abs(gaps[0][0] - 3.2) < 0.02 and abs(gaps[0][1] - 0.15) < 0.02, gaps

    assert clicks(clean, times) == [], "no click in clean sound"
    spiked = clean.copy()
    spiked[int(2.75 * RATE)] += 0.5
    found = clicks(spiked, times)
    assert len(found) == 1 and abs(found[0] - 2.75) < 0.005, found
    assert clicks(spiked, times + [2.74]) == [], "a burst a firing explains is not a click"

    steady = 0.3 * np.sin(2 * np.pi * 400 * np.arange(RATE * 3) / RATE)
    assert audio_stairs(steady) == [], "a steady tone has no stairs"
    smooth = steady * np.linspace(0.2, 1, len(steady))
    assert audio_stairs(smooth) == [], "a smooth fade has no stairs"
    gain = np.ones(len(steady))
    for k in range(8):
        gain[int((1.0025 + 0.05 * k) * RATE):] *= 10 ** (2 / 20)
    stairs = audio_stairs(steady * gain * 0.1)
    assert len(stairs) >= 1 and abs(stairs[0] - 1.0) < 0.1, stairs

    ticks = [{"t": 50 * i, "v": 0.5, "p": 1.0} for i in range(10)]
    assert level_steps(ticks)[0] == 0
    ticks[5] = {"t": 250, "v": 0.3, "p": 1.0}
    assert level_steps(ticks)[0] == 2, "down and back up"
    ticks[5] = {"t": 250, "v": 0.5, "p": 1.1}
    assert level_steps(ticks)[0] == 2, "a pitch stair"

    # Four chunks at once, then one each time one has played, a little late: never dry. Then a stall of a second
    chunk = NOTE_RATE // 10
    reads = [(0, chunk)] * 4 + [(100 * i + 130, chunk) for i in range(30)]
    dry, least = underruns(reads)
    assert dry == [] and 0.2 < least <= 0.4, (dry, least)
    stalled = reads + [(reads[-1][0] + 1000, chunk)]
    dry, _ = underruns(stalled)
    assert len(dry) == 1 and 0.4 < dry[0][1] < 0.8, dry

    late = thumps(9, [t + 0.063 for t in times])
    measured, dense, unheard = lags(late, times)
    assert len(measured) >= 18 and dense == 0, (len(measured), dense, unheard)
    assert abs(float(np.median([lag for _, lag, _ in measured])) - 0.063) < 0.006, np.median([m[1] for m in measured])
    assert lags(late, [1 + 0.03 * i for i in range(50)])[1] == 50, "firings 30 ms apart are not measured"
    assert lags(np.zeros(RATE * 9), times)[0] == [], "silence has no onsets"

    same = np.zeros(RATE * 3)
    one = thumps(0.2, [0.001])
    for at in (0.5, 1.0, 1.5, 2.0):
        same[int(at * RATE):int(at * RATE) + len(one)] += one
    assert min(alikeness(same, [0.5, 1.0, 1.5, 2.0])) > 0.999, "one take repeated is alike"
    varied = np.zeros(RATE * 3)
    random = np.random.default_rng(3)
    for at in (0.5, 1.0, 1.5, 2.0):
        take = random.standard_normal(RATE // 10) * 0.1
        varied[int(at * RATE):int(at * RATE) + len(take)] += take
    assert max(alikeness(varied, [0.5, 1.0, 1.5, 2.0])) < 0.5, "different takes are not"

    # Reads a chunk apart in step with playing keep the same audio waiting; four read at once leave three ahead of the next
    steady_reads = [(0, chunk)] * 4 + [(100 * i, chunk) for i in range(1, 30)]
    waiting = queue(steady_reads)[1]
    assert abs(float(np.median(waiting[6:])) - 0.3) < 0.01, waiting

    noise = np.random.default_rng(7).standard_normal(6 * RATE)
    dull = signal.sosfilt(signal.butter(4, 800, fs=RATE, output="sos"), noise)
    assert brightness(dull) < 0.2 * brightness(noise), "less top is less bright"
    assert brightness(np.zeros(RATE)) is None

    spent = cost([(1600, 2_000_000)] * 20, 1.0)
    assert abs(spent["sound_core_share"] - 0.04) < 1e-9 and spent["ns_per_sample"] == 1250, spent
    assert spent["worst_read_ms"] == 2, spent
    assert cost([], 1.0) is None

    random = np.random.default_rng(11)
    fresh = random.standard_normal(12 * RATE) * 0.1
    assert exact_repeat(fresh) < 0.2, "noise never repeats"
    loop = np.tile(random.standard_normal(int(2.7 * RATE)) * 0.1, 5)
    assert exact_repeat(loop) > 0.99, "a loop repeats"
    assert exact_repeat(fresh[:RATE]) is None, "too short to judge"
    fine = {"alone": -40.0, "crowd_early": -35.0, "crowd_waiting": -36.0, "crowd_settled": -37.0, "pace": -33.0,
            "stay": -37.0}
    figures, faults = soak_findings(fine, 0.5, {"silence": -100.0, "minecart": -30.0})
    assert faults == [] and figures["over_one_db"] == 5.0 and figures["settled_db"] == -2.0, (figures, faults)
    assert figures["over_loudest_other_db"] == -5.0, figures
    assert len(soak_findings({**fine, "crowd_early": -34.2}, 0.5, {})[1]) == 0, "settling down is no fault"
    assert len(soak_findings({**fine, "alone": -43.0}, 0.5, {})[1]) == 1, "over the ceiling"
    assert len(soak_findings({**fine, "stay": -35.0}, 0.5, {})[1]) == 1, "grew while left alone"
    assert len(soak_findings(fine, 0.995, {})[1]) == 1, "a loop is a fault"

    assert stacked_seconds([(50 * i, 1) for i in range(100)]) == 0, "one note"
    assert stacked_seconds([(50 * i, which) for i in range(100) for which in (1, 2)]) > 4.9, "two at once"

    # A whole made-up run through the same path as a real one, with the recording starting 0.3 s off what the log says
    import tempfile
    with tempfile.TemporaryDirectory() as folder:
        folder = Path(folder)
        sound = thumps(12, [t + 2 for t in times])
        for at in (1.0, 11.0):
            sound[int(at * RATE):int(at * RATE) + 480] += 0.9 * np.exp(-np.arange(480) / 100) * np.sin(np.arange(480))
        write_wav(folder / "capture.wav", np.stack([sound, sound], axis=1))
        epoch = 1000.0
        stamp = lambda s: int(round((epoch + s - 0.3) * 1000))
        events = [{"t": stamp(0.5), "e": "phase", "scene": "setup", "phase": "ready"},
                  {"t": stamp(1.0), "e": "clap"}, {"t": stamp(2.2), "e": "phase", "scene": "single", "phase": "idle"},
                  {"t": stamp(10.4), "e": "phase", "scene": "setup", "phase": "clap_end"}, {"t": stamp(11.0), "e": "clap"},
                  {"t": stamp(11.5), "e": "phase", "scene": "setup", "phase": "done"}]
        events += [{"t": stamp(t + 2), "e": "firing", "engine": "0 0 0"} for t in times]
        (folder / "events.jsonl").write_text("".join(json.dumps(e) + "\n" for e in events), encoding="utf-8")
        (folder / "meta.json").write_text(json.dumps({"sound_started": epoch}), encoding="utf-8")
        assert audit(folder), "a clean made-up run has no problems"
        report = json.loads((folder / "report.json").read_text(encoding="utf-8"))
        idle = report["scenes"]["single"]["phases"]["idle"]
        assert abs(idle["lag"]["median_ms"]) < 8, idle["lag"]
        assert idle["clicks"] == [] and idle["gaps"] == [], idle
        assert (folder / "wav" / "single.wav").is_file()
    print("self-test passed")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("run", nargs="?")
    parser.add_argument("--refs", help="folder of recordings of real engines, <scene>.wav, for A/B files")
    parser.add_argument("--compare", help="an earlier run's folder, to show its figures beside these")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        self_test()
    elif args.run:
        sys.exit(0 if audit(args.run, args.refs, args.compare) else 1)
    else:
        parser.error("a run folder, or --self-test")


if __name__ == "__main__":
    main()
