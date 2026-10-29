"""Synthesizes the mod's sounds and encodes them to Ogg Vorbis under assets/create_engines/sounds.

Needs numpy and scipy, plus a libsndfile built with Vorbis support. Audacity ships one: point CE_SNDFILE at its
sndfile.dll if it isn't found in the usual install location. Run from anywhere; output paths are relative to this file.

    python tools/generate_sounds.py

Sounds:
  cylinder_fire1..3  the bang of a charge igniting: a pressure thump, a sharp crack and a ring down the exhaust
  starter_whir       a seamless loop of a starter motor turning over; the game bends its pitch with the motor's speed
"""
import ctypes
import glob
import os
import sys

import numpy as np
from scipy import signal

SR = 44100
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets",
                   "create_engines", "sounds")

SF_FORMAT_OGG = 0x200000
SF_FORMAT_VORBIS = 0x0060
SFM_WRITE = 0x20


class SfInfo(ctypes.Structure):
    _fields_ = [("frames", ctypes.c_int64), ("samplerate", ctypes.c_int), ("channels", ctypes.c_int),
                ("format", ctypes.c_int), ("sections", ctypes.c_int), ("seekable", ctypes.c_int)]


def load_sndfile():
    candidates = [os.environ.get("CE_SNDFILE", "")]
    candidates += glob.glob("C:/Program Files/Audacity*/bin/sndfile.dll")
    candidates += glob.glob("C:/Program Files (x86)/Audacity*/bin/sndfile.dll")
    for path in candidates:
        if path and os.path.exists(path):
            os.add_dll_directory(os.path.dirname(path))
            lib = ctypes.CDLL(path)
            lib.sf_open.restype = ctypes.c_void_p
            lib.sf_open.argtypes = [ctypes.c_char_p, ctypes.c_int, ctypes.POINTER(SfInfo)]
            lib.sf_write_float.restype = ctypes.c_int64
            lib.sf_write_float.argtypes = [ctypes.c_void_p, ctypes.POINTER(ctypes.c_float), ctypes.c_int64]
            lib.sf_close.argtypes = [ctypes.c_void_p]
            return lib
    sys.exit("No libsndfile found: set CE_SNDFILE to a sndfile.dll that can write Ogg Vorbis.")


def write_ogg(lib, name, samples):
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, name + ".ogg")
    info = SfInfo(0, SR, 1, SF_FORMAT_OGG | SF_FORMAT_VORBIS, 0, 0)
    handle = lib.sf_open(path.encode(), SFM_WRITE, ctypes.byref(info))
    if not handle:
        sys.exit("Could not open " + path)
    data = np.ascontiguousarray(samples, dtype=np.float32)
    lib.sf_write_float(handle, data.ctypes.data_as(ctypes.POINTER(ctypes.c_float)), len(data))
    lib.sf_close(handle)
    print("wrote", os.path.normpath(path), "%.2fs" % (len(data) / SR))


def band(noise, low, high, order=3):
    sos = signal.butter(order, [low, high], btype="bandpass", fs=SR, output="sos")
    return signal.sosfilt(sos, noise)


def lowpass(noise, cutoff, order=3):
    return signal.sosfilt(signal.butter(order, cutoff, btype="lowpass", fs=SR, output="sos"), noise)


def finish(mix, peak=0.9):
    return mix / np.max(np.abs(mix)) * peak


def bang(seed, pitch):
    """A diesel charge going off: the pressure thump arrives first, the crack rides on top, the exhaust rings after."""
    rng = np.random.default_rng(seed)
    n = int(0.5 * SR)
    t = np.arange(n) / SR
    rise = np.minimum(t / 0.0015, 1)

    sweep = (46 + 110 * np.exp(-t / 0.03)) * pitch
    thump = np.sin(2 * np.pi * np.cumsum(sweep) / SR) * rise * np.exp(-t / 0.075)

    crack = band(rng.standard_normal(n), 700 * pitch, 6500, 2) * rise * np.exp(-t / 0.014)
    body = lowpass(rng.standard_normal(n), 700 * pitch) * rise * np.exp(-t / 0.07)

    ring_noise = lowpass(rng.standard_normal(n), 400)
    ring = (np.sin(2 * np.pi * 88 * pitch * t) + 0.6 * np.sin(2 * np.pi * 176 * pitch * t + 1.1)
            + 0.35 * np.sin(2 * np.pi * 265 * pitch * t + 2.3))
    ring = ring * (0.75 + 0.25 * ring_noise / np.max(np.abs(ring_noise))) * np.minimum(t / 0.008, 1) * np.exp(-t / 0.11)

    mix = 1.0 * thump + 0.5 * crack + 0.7 * body + 0.28 * ring
    mix = np.tanh(1.7 * mix)
    mix *= np.clip((n - np.arange(n)) / (0.04 * SR), 0, 1)
    return finish(mix)


def periodic_noise(rng, n, shape):
    """Noise that repeats exactly every n samples, with a magnitude spectrum given by shape(frequency_hz)."""
    freqs = np.fft.rfftfreq(n, 1 / SR)
    spectrum = shape(freqs) * np.exp(1j * rng.uniform(0, 2 * np.pi, len(freqs)))
    spectrum[0] = 0
    return np.fft.irfft(spectrum, n)


def starter_loop():
    """One second of starter motor that repeats seamlessly: every component is a whole number of cycles long."""
    rng = np.random.default_rng(7)
    n = SR
    t = np.arange(n) / SR

    # Armature whine: a stack of harmonics, the gear mesh sitting at a higher multiple
    f0 = 172
    whine = sum(np.sin(2 * np.pi * k * f0 * t + rng.uniform(0, 2 * np.pi)) / k ** 1.15 for k in range(1, 9))
    whine += 0.35 * np.sin(2 * np.pi * 11 * f0 * t)

    # Pinion teeth catching the ring gear: a rough buzz at 18 Hz that the whine and the grit both ride on
    teeth = 0.6 + 0.4 * np.cos(2 * np.pi * 18 * t)
    pulses = (0.5 + 0.5 * np.cos(2 * np.pi * 18 * t - 0.6)) ** 2.5

    grit = periodic_noise(rng, n, lambda f: np.where((f > 1100) & (f < 5200), 1 / np.sqrt(np.maximum(f, 1)), 0))
    grit = grit / np.max(np.abs(grit))
    rumble = periodic_noise(rng, n, lambda f: np.where((f > 25) & (f < 160), 1.0, 0.0))
    rumble = rumble / np.max(np.abs(rumble)) + 0.6 * np.sin(2 * np.pi * 54 * t)

    mix = 0.5 * whine / np.max(np.abs(whine)) * teeth + 0.38 * grit * pulses + 0.3 * rumble
    mix = np.tanh(1.3 * mix)
    return finish(mix, 0.8)


def main():
    lib = load_sndfile()
    for i, (seed, pitch) in enumerate([(11, 1.0), (23, 0.93), (37, 1.07)], start=1):
        write_ogg(lib, "cylinder_fire%d" % i, bang(seed, pitch))
    write_ogg(lib, "starter_whir", starter_loop())


if __name__ == "__main__":
    main()
