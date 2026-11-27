"""The engine note's grains and exhaust pipe for the recordings that stand in for it (step 3 of the sound plan): the
backfire, spit, catch and stall that play for an engine without a note of its own. The pipe is ExhaustPipe.java ported,
with the same figures (SoundAssetsTest holds them to each other); the grains are engine_grains.py's own.

Everything here runs at the note's rate (SR); to_sound_rate takes a finished mix to the recordings' rate.
"""
import numpy as np
from scipy import signal

import engine_grains
from sound_dsp import SR as SOUND_RATE

SR = engine_grains.SR

# ExhaustPipe.java's figures
RING = 0.016
MOST_BACK = 0.88
OPEN_HZ = 2400
RADIATE_HZ = 120
RADIATE_LOSS = 0.5
# EngineNoteSynth.java's: where a middling pipe rings
PIPE_HZ = 86


def run_note(size, run):
    """EngineNoteSynth.pitch: the note of a run, 0 (a stub) to 1 (long), for cylinders of `size`, 0 to 1."""
    octaves = 1.0 if run < 0.5 else 0.4
    return PIPE_HZ * (1.3 - 0.6 * size) * 2 ** (2 * (0.5 - run) * octaves)


def pole(hz):
    return 1 - np.exp(-2 * np.pi * hz / SR)


def pipe(into, note):
    """What leaves the mouth of a run ringing at `note` hertz when `into` goes in at the valve."""
    keep = pole(OPEN_HZ)
    travel = SR / (2 * note)
    delay = min(max(travel - (1 - keep) / keep, 2), 1020)
    feedback = min(MOST_BACK, np.exp(-travel / (RING * SR)))
    radiate = 1 - pole(RADIATE_HZ)
    whole, part = int(delay), delay - int(delay)
    line = np.zeros(len(into))
    out = np.zeros(len(into))
    low = lows = 0.0
    for i in range(len(into)):
        near = line[i - whole] if i >= whole else 0.0
        far = line[i - whole - 1] if i >= whole + 1 else 0.0
        low += keep * (near + (far - near) * part - low)
        wave = into[i] - feedback * low
        line[i] = wave
        lows = radiate * lows + (1 - radiate) * wave
        out[i] = wave - RADIATE_LOSS * lows
    return out


def strike(mix, at, grain, gain):
    """Adds `grain`, `gain` times as strong, to `mix` from `at` seconds on."""
    k = int(at * SR)
    n = min(len(grain), len(mix) - k)
    if n > 0:
        mix[k:k + n] += gain * grain[:n]


def body(rng, size, load):
    """A blow-down take, spark ignition; size and load 0 to 2, as the note's sets."""
    return engine_grains.body(rng, False, size, load)


def crack(rng, size):
    return engine_grains.crack(rng, size)


def one_pole(x, hz):
    share = pole(hz)
    return signal.lfilter([share], [1, share - 1], x)


def to_sound_rate(x):
    return signal.resample_poly(x, SOUND_RATE // 100, SR // 100)
