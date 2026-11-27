"""Makes the engine note's grains (step 2 of the sound plan): single firings, synthesized here, that the note strikes into
its modelled exhaust pipes. Nothing is recorded.

    python tools/engine_grains.py                 writes src/main/resources/assets/create_engines/engine_grains.bin
    python tools/engine_grains.py --wav FOLDER    also writes one WAV a set, to listen to

Three kinds, a set of eight takes each so no firing need repeat the one before:

* body: the blow-down as the exhaust valve opens (a pressure that shoves out fast and sucks back, a chirp falling as the
  cylinder empties) and the rush of burning gas behind it. By ignition (spark, compression), cylinder size and load.
* crack: the flame front, a short bright snap. By cylinder size; the note fades it out as firings run together.
* knock: compression ignition's double rap. One set.

The file: b"CEG1", then little-endian int32 rate, sizes, loads, takes, then each grain as int32 length and that many int16
samples, in the order body[ignition][size][load][take], crack[size][take], knock[take]. EngineGrains reads it.
"""
import argparse
import struct
import wave
from pathlib import Path

import numpy as np
from scipy import signal

# The note's own rate (EngineNoteSynth.SAMPLE_RATE): the grains are made at it, so none is resampled
SR = 32000
SIZES, LOADS, TAKES = 3, 3, 8
OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/create_engines/engine_grains.bin"


def band(x, low, high, order=2):
    return signal.sosfilt(signal.butter(order, [low, high], btype="bandpass", fs=SR, output="sos"), x)


def unit(x):
    return x / np.max(np.abs(x))


def rise(t, seconds):
    """0 to 1 with no corner at either end, so no grain starts with a click."""
    x = np.clip(t / seconds, 0, 1)
    return x * x * (3 - 2 * x)


def fade_out(x, seconds=0.004):
    n = int(seconds * SR)
    x[-n:] *= np.cos(np.linspace(0, np.pi / 2, n)) ** 2
    x[-1] = 0
    return x


def jitter(rng, value, share=0.05):
    return value * (1 + share * rng.uniform(-1, 1))


def body(rng, compression, size, load):
    """size 0 (small) to 2 (big); load 0 (light) to 2 (full)."""
    big, hard = size / 2, load / 2
    length = int((0.034 + 0.030 * big) * SR)
    t = np.arange(length) / SR
    attack = jitter(rng, 0.0016 - 0.0008 * hard + 0.0004 * big) * (0.7 if compression else 1)
    shove_fall = jitter(rng, 0.0022 + 0.0030 * big)
    suck_fall = shove_fall * jitter(rng, 2.6)
    # Out and back: the two have the same area, so the pulse pushes no air in the end
    shove = rise(t, attack) * (np.exp(-t / shove_fall) - shove_fall / suck_fall * np.exp(-t / suck_fall))
    start = jitter(rng, 520 - 220 * big)
    end = jitter(rng, 150 - 60 * big)
    pitch = end + (start - end) * np.exp(-t / jitter(rng, 0.006))
    chirp = np.sin(2 * np.pi * np.cumsum(pitch) / SR) * rise(t, attack * 1.5) * np.exp(-t / jitter(rng, 0.006 + 0.005 * big))
    top = 1300 + 1500 * hard + (600 if compression else 0)
    gas = unit(band(rng.standard_normal(length), 180, top))
    gas *= rise(t, attack) * np.exp(-t / jitter(rng, 0.004 + 0.007 * big))
    rush = (0.09 + 0.15 * hard) * (1.25 if compression else 1)
    grain = unit(shove) + jitter(rng, 0.32, 0.1) * chirp + jitter(rng, rush, 0.1) * gas
    grain = fade_out(grain)
    # Every take of a set hits as hard as the others; a loaded cylinder blows down harder than an idling one
    return grain / np.sqrt(np.sum(grain ** 2)) * (1 + 0.12 * load)


def crack(rng, size):
    big = size / 2
    fall = jitter(rng, 0.0035 + 0.0050 * big)
    length = int((fall * 5 + 0.004) * SR)
    t = np.arange(length) / SR
    centre = jitter(rng, 3300 - 1500 * big, 0.18)
    snap = band(rng.standard_normal(length), centre * 0.62, min(centre * 1.7, SR * 0.45))
    snap *= rise(t, 0.0012) * np.exp(-t / fall)
    snap = fade_out(snap)
    return snap / np.sqrt(np.sum(snap ** 2))


def knock(rng):
    length = int(0.014 * SR)
    t = np.arange(length) / SR
    grain = np.zeros(length)
    note = jitter(rng, 2700, 0.07)
    for delay, strength in ((0.0, 1.0), (jitter(rng, 0.0013), 0.55)):
        s = np.clip(t - delay, 0, None)
        ring = 0.6 * np.sin(2 * np.pi * note * s) + unit(band(rng.standard_normal(length), 1900, 3900))
        grain += strength * ring * rise(s, 0.0008) * np.exp(-s / 0.0022) * (t >= delay)
    grain = fade_out(grain, 0.002)
    return grain / np.sqrt(np.sum(grain ** 2))


def every_grain():
    """(name, samples) in the file's order, each take from its own seed so changing one set moves no other."""
    for ignition, compression in enumerate((False, True)):
        for size in range(SIZES):
            for load in range(LOADS):
                for take in range(TAKES):
                    rng = np.random.default_rng([1, ignition, size, load, take])
                    yield f"body_{'compression' if compression else 'spark'}_s{size}_l{load}", body(rng, compression, size, load)
    for size in range(SIZES):
        for take in range(TAKES):
            yield f"crack_s{size}", crack(np.random.default_rng([2, size, take]), size)
    for take in range(TAKES):
        yield "knock", knock(np.random.default_rng([3, take]))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--wav", help="also write one WAV a set, its takes a quarter second apart, into this folder")
    args = parser.parse_args()
    sets = {}
    data = bytearray(b"CEG1" + struct.pack("<4i", SR, SIZES, LOADS, TAKES))
    for name, grain in every_grain():
        # Each has unit energy (times its load), so its peak is well under full scale and nothing is clipped
        sets.setdefault(name, []).append(grain)
        pcm = np.round(np.clip(grain, -1, 1) * 32767).astype("<i2")
        data += struct.pack("<i", len(pcm)) + pcm.tobytes()
    OUT.write_bytes(bytes(data))
    print(f"{OUT.name}: {sum(len(g) for g in sets.values())} grains, {len(data) / 1024:.0f} kB, "
          f"peak {max(np.abs(g).max() for takes in sets.values() for g in takes):.3f}")
    if args.wav:
        folder = Path(args.wav)
        folder.mkdir(parents=True, exist_ok=True)
        for name, takes in sets.items():
            gap = np.zeros(SR // 4)
            sound = np.concatenate([np.concatenate([g / np.abs(g).max() * 0.7, gap]) for g in takes])
            with wave.open(str(folder / f"{name}.wav"), "wb") as handle:
                handle.setnchannels(1)
                handle.setsampwidth(2)
                handle.setframerate(SR)
                handle.writeframes(np.round(sound * 32767).astype("<i2").tobytes())


if __name__ == "__main__":
    main()
