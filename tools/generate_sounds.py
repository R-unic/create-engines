"""Synthesizes the mod's sounds and encodes them to Ogg Vorbis under assets/create_engines/sounds.

Needs numpy and scipy, plus a way to write Ogg Vorbis: a libsndfile built with Vorbis support (Audacity ships one:
point CE_SNDFILE at its sndfile.dll if it isn't found in the usual install location), or failing that ffmpeg with
libvorbis on the path. Run from anywhere; output paths are relative to this file. Name sounds to make only those, which
leaves the others' recordings alone (a different encoder gives a different file even from the same synthesis):

    python tools/generate_sounds.py
    python tools/generate_sounds.py turbo_whistle turbo_blowoff supercharger_whine
    python tools/generate_sounds.py --wav build/sound-preview engine_catch engine_stall

With --wav DIR the named sounds are written as WAV files into DIR instead, to listen to before encoding them.

Sounds:
  cylinder_fire1..3  the bang of a charge igniting: a pressure thump, a sharp crack and a ring down the exhaust
  starter_whir       a seamless loop of a starter motor turning over; the game bends its pitch with the motor's speed
  turbo_whistle      a seamless loop of a spooling turbine: a thin whistle over air rush; pitch follows the wheel speed
  turbo_blowoff      the blow-off valve dumping boost: a thump, a hiss and a flutter of the compressor surging
  supercharger_whine a seamless loop of a roots blower: gear whine over the beat of its lobes; pitch follows its shaft
  engine_catch       an engine catching on the starter: a few firings, each sooner and harder than the last
  engine_stall       an engine dying: its last firings slowing and weakening, a clunk and a sigh from the exhaust
  exhaust_pop1..3    unburned fuel lighting in a hot exhaust: a crack, a short thump and the ring of the pipe
  metal_tick1..3     hot metal ticking as it cools: a small bright tink
  engine_clatter     a seamless loop of valve gear: tappets ticking over the hum of the gears; pitch follows the crank
  bearing_squeal     a seamless loop of bearings running dry: a wavering shriek over a stick-slip rasp
"""
import ctypes
import glob
import os
import shutil
import subprocess
import sys
import tempfile
import wave

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
    return None


def write_ogg_ffmpeg(path, samples):
    """Encodes through ffmpeg's libvorbis when there is no libsndfile to do it."""
    ffmpeg = shutil.which("ffmpeg")
    if not ffmpeg:
        sys.exit("No libsndfile and no ffmpeg found: set CE_SNDFILE to a sndfile.dll that can write Ogg Vorbis.")
    pcm = (np.clip(samples, -1, 1) * 32767).astype("<i2")
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, "in.wav")
        with wave.open(wav, "wb") as fh:
            fh.setnchannels(1)
            fh.setsampwidth(2)
            fh.setframerate(SR)
            fh.writeframes(pcm.tobytes())
        subprocess.run([ffmpeg, "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "5", path],
                       check=True)


def write_ogg(lib, name, samples):
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, name + ".ogg")
    if lib is None:
        write_ogg_ffmpeg(path, samples)
        print("wrote", os.path.normpath(path), "%.2fs (ffmpeg)" % (len(samples) / SR))
        return
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


def turbo_loop():
    """One second of spooling turbine that repeats seamlessly. At the game's pitch 1 the whistle sits near 1.8 kHz; the
    wheel running up bends it from about half that to well over an octave above. Every component is a whole number of
    cycles long, so the loop has no seam."""
    rng = np.random.default_rng(3)
    n = SR
    t = np.arange(n) / SR

    # The blades passing a fixed point: a thin tone with a quiver, a bright overtone and a sub-tone, and a second tone
    # a few hertz off it that makes the whistle shimmer
    f0 = 1800
    quiver = 0.9 * np.sin(2 * np.pi * 5 * t)
    whistle = (np.sin(2 * np.pi * f0 * t + quiver) + 0.38 * np.sin(2 * np.pi * 2 * f0 * t + 2 * quiver + 0.7)
               + 0.12 * np.sin(2 * np.pi * 3 * f0 * t + 1.9) + 0.26 * np.sin(2 * np.pi * (f0 // 2) * t + 0.4)
               + 0.5 * np.sin(2 * np.pi * (f0 + 7) * t + 1.3))

    # Air going through the housing: a bright rush that swells and ebbs, and a soft low whoosh
    rush = periodic_noise(rng, n, lambda f: np.where((f > 2500) & (f < 9500), 1 / np.sqrt(np.maximum(f, 1)), 0))
    rush = rush / np.max(np.abs(rush)) * (0.8 + 0.2 * np.cos(2 * np.pi * 38 * t))
    whoosh = periodic_noise(rng, n, lambda f: np.where((f > 120) & (f < 650), 1.0, 0.0))
    whoosh = whoosh / np.max(np.abs(whoosh))

    mix = 0.55 * whistle / np.max(np.abs(whistle)) + 0.34 * rush + 0.24 * whoosh
    return finish(np.tanh(1.2 * mix), 0.8)


def turbo_blowoff():
    """The blow-off valve letting a charge go: the valve thumps open, the air hisses out and the compressor, suddenly
    unloaded, flutters as it surges before it settles."""
    rng = np.random.default_rng(19)
    n = int(1.0 * SR)
    t = np.arange(n) / SR
    rise = np.minimum(t / 0.006, 1)

    thump = np.sin(2 * np.pi * np.cumsum(55 + 90 * np.exp(-t / 0.025)) / SR) * rise * np.exp(-t / 0.05)

    hiss = band(rng.standard_normal(n), 1600, 11000, 2) * rise * (0.6 * np.exp(-t / 0.32) + 0.4 * np.exp(-t / 0.09))
    # Surge: the hiss chops at a rate that slows as the wheel runs down, chattering rather than switching cleanly
    chatter_rate = 36 * np.exp(-t / 0.35) + 9
    chop = 0.5 + 0.5 * np.sign(np.sin(2 * np.pi * np.cumsum(chatter_rate) / SR)) * 0.8
    chop = lowpass(chop, 220, 2)
    flutter = band(rng.standard_normal(n), 900, 5200, 2) * chop * rise * np.exp(-t / 0.28)

    mix = 0.8 * thump + 0.5 * hiss / np.max(np.abs(hiss)) + 0.55 * flutter / np.max(np.abs(flutter))
    mix = np.tanh(1.5 * mix)
    mix *= np.clip((n - np.arange(n)) / (0.06 * SR), 0, 1)
    return finish(mix)


def supercharger_loop():
    """One second of roots blower that repeats seamlessly: the gears whine, the lobes beat at 48 Hz and the delivered air
    thrums a third as fast. At pitch 1 the whine is near 520 Hz. It is deliberately not a clean tone: the gear mesh wanders
    and is doubled by a slightly detuned twin so it beats, odd partials and a few non-harmonic ones make it metallic, and
    gritty noise rides on it."""
    rng = np.random.default_rng(5)
    n = SR
    t = np.arange(n) / SR

    # Gear mesh: the pitch drifts a little (whole cycles per second, so it still loops) and the amplitude flutters
    f0 = 520
    wander = 2.2 * np.sin(2 * np.pi * 3 * t + 0.4) + 1.3 * np.sin(2 * np.pi * 7 * t + 2.1)
    twin = f0 + 9

    def tone(f, phase_mod, shift=0.0):
        return np.sin(2 * np.pi * f * t + phase_mod + rng.uniform(0, 2 * np.pi) + shift)

    whine = sum(tone(k * f0, k * wander) / k ** 0.7 for k in range(1, 7))
    whine += 0.7 * sum(tone(k * twin, k * wander) / k ** 0.9 for k in range(1, 4))
    # Rough, metallic partials that are not multiples of the mesh: bearings and lobe tips
    whine += 0.35 * tone(int(f0 * 2.37), wander) + 0.25 * tone(int(f0 * 3.61), 1.5 * wander)
    whine += 0.4 * np.sin(2 * np.pi * 13 * (f0 // 3) * t)
    whine = whine / np.max(np.abs(whine))
    # Eccentric gears and lobes chop the whole thing unevenly rather than a smooth swell
    flutter = 0.75 + 0.15 * np.cos(2 * np.pi * 48 * t) + 0.1 * np.cos(2 * np.pi * 23 * t + 1.2)
    # Overdrive turns the pure sine stack into something with edge
    whine = np.tanh(2.2 * whine)

    thrum = sum(np.sin(2 * np.pi * k * 96 * t + rng.uniform(0, 2 * np.pi)) / k for k in range(1, 5))
    air = periodic_noise(rng, n, lambda f: np.where((f > 500) & (f < 3800), 1 / np.sqrt(np.maximum(f, 1)), 0))
    air = air / np.max(np.abs(air))
    air *= 0.75 + 0.25 * np.cos(2 * np.pi * 48 * t - 0.8)
    # Grit: hard-edged noise that pulses with the gears, sitting right on top of the whine
    grit = periodic_noise(rng, n, lambda f: np.where((f > 900) & (f < 6500), 1 / np.sqrt(np.maximum(f, 1)), 0))
    grit = grit / np.max(np.abs(grit)) * (0.5 + 0.5 * np.cos(2 * np.pi * 48 * t - 0.3)) ** 1.5

    mix = (0.42 * whine * flutter + 0.3 * thrum / np.max(np.abs(thrum)) + 0.24 * air + 0.2 * grit)
    return finish(np.tanh(1.25 * mix), 0.8)


def firing(t, at, pitch, rng):
    """One firing heard from outside the engine, starting at time `at`: a thump that drops in pitch and a muffled crack."""
    local = np.maximum(t - at, 0)
    gate = (t >= at) * np.minimum(local / 0.002, 1)
    sweep = (48 + 95 * np.exp(-local / 0.028)) * pitch
    thump = np.sin(2 * np.pi * np.cumsum(sweep * (t >= at)) / SR) * np.exp(-local / 0.07)
    crack = band(rng.standard_normal(len(t)), 500 * pitch, 3800, 2) * np.exp(-local / 0.018)
    body = lowpass(rng.standard_normal(len(t)), 520 * pitch) * np.exp(-local / 0.06)
    return gate * (thump + 0.3 * crack + 0.5 * body)


def engine_catch():
    """An engine catching on the starter: four firings, each sooner, harder and higher than the last, over a breath of
    exhaust that swells as it takes hold."""
    rng = np.random.default_rng(41)
    n = int(0.85 * SR)
    t = np.arange(n) / SR
    mix = np.zeros(n)
    for at, level, pitch in ((0.02, 0.5, 0.82), (0.21, 0.7, 0.9), (0.36, 0.9, 0.98), (0.48, 1.0, 1.06), (0.58, 0.8, 1.12)):
        mix += level * firing(t, at, pitch, rng)
    breath = band(rng.standard_normal(n), 180, 1500, 2)
    breath = breath / np.max(np.abs(breath)) * np.clip(t / 0.5, 0, 1) ** 2 * np.exp(-np.maximum(t - 0.55, 0) / 0.09)
    mix = np.tanh(1.5 * (mix + 0.35 * breath))
    mix *= np.clip((n - np.arange(n)) / (0.12 * SR), 0, 1)
    return finish(mix)


def engine_stall():
    """An engine dying: its last firings come later and weaker, something inside knocks to a stop, and what is left in
    the exhaust sighs out."""
    rng = np.random.default_rng(43)
    n = int(1.2 * SR)
    t = np.arange(n) / SR
    mix = np.zeros(n)
    for at, level, pitch in ((0.01, 1.0, 1.0), (0.13, 0.8, 0.92), (0.29, 0.6, 0.84), (0.5, 0.42, 0.76)):
        mix += level * firing(t, at, pitch, rng)
    # The crank rocking back against compression: a dull knock with a short iron ring
    local = np.maximum(t - 0.7, 0)
    gate = (t >= 0.7) * np.minimum(local / 0.001, 1)
    knock = gate * (band(rng.standard_normal(n), 300, 2400, 2) * np.exp(-local / 0.012) * 0.25
                    + 0.45 * np.sin(2 * np.pi * 172 * local) * np.exp(-local / 0.05)
                    + 0.12 * np.sin(2 * np.pi * 437 * local) * np.exp(-local / 0.035))
    sigh = band(rng.standard_normal(n), 400, 5200, 2)
    sigh = sigh / np.max(np.abs(sigh)) * np.clip((t - 0.45) / 0.2, 0, 1) * np.exp(-np.maximum(t - 0.65, 0) / 0.2)
    mix = np.tanh(1.4 * (mix + knock + 0.16 * sigh))
    mix *= np.clip((n - np.arange(n)) / (0.15 * SR), 0, 1)
    return finish(mix)


def exhaust_pop(seed, pitch):
    """Unburned fuel lighting in a hot exhaust: a hard crack, a short thump behind it and the pipe ringing after."""
    rng = np.random.default_rng(seed)
    n = int(0.32 * SR)
    t = np.arange(n) / SR
    rise = np.minimum(t / 0.0008, 1)
    crack = band(rng.standard_normal(n), 900 * pitch, 7500, 2) * rise * np.exp(-t / 0.009)
    thump = np.sin(2 * np.pi * np.cumsum((70 + 160 * np.exp(-t / 0.012)) * pitch) / SR) * rise * np.exp(-t / 0.035)
    ring = (np.sin(2 * np.pi * 318 * pitch * t) + 0.55 * np.sin(2 * np.pi * 655 * pitch * t + 0.9)
            + 0.3 * np.sin(2 * np.pi * 1012 * pitch * t + 2.0)) * np.minimum(t / 0.004, 1) * np.exp(-t / 0.05)
    tail = band(rng.standard_normal(n), 300, 2200, 2) * np.minimum(t / 0.01, 1) * np.exp(-t / 0.06)
    mix = 0.9 * crack / np.max(np.abs(crack)) + 0.7 * thump + 0.22 * ring + 0.18 * tail / np.max(np.abs(tail))
    mix = np.tanh(1.8 * mix)
    mix *= np.clip((n - np.arange(n)) / (0.05 * SR), 0, 1)
    return finish(mix)


def metal_tick(seed, f0):
    """Hot metal ticking as it shrinks: the partials of a small struck bar, gone in a few hundredths of a second."""
    rng = np.random.default_rng(seed)
    n = int(0.2 * SR)
    t = np.arange(n) / SR
    mix = np.zeros(n)
    for ratio, level, decay in ((1.0, 1.0, 0.03), (2.76, 0.55, 0.018), (5.4, 0.3, 0.01), (8.93, 0.12, 0.006)):
        mix += level * np.sin(2 * np.pi * f0 * ratio * t + rng.uniform(0, 2 * np.pi)) * np.exp(-t / decay)
    click = band(rng.standard_normal(n), 2500, 9000, 2) * np.exp(-t / 0.0015)
    mix = mix * np.minimum(t / 0.0004, 1) + 0.6 * click / np.max(np.abs(click))
    mix *= np.clip((n - np.arange(n)) / (0.04 * SR), 0, 1)
    return finish(mix, 0.7)


def clatter_loop():
    """One second of valve gear that repeats seamlessly: twenty-four tappet ticks, no two quite alike but the pattern of
    four coming round again as the cam does, over the hum of gears and a low churn. Every part is a whole number of
    cycles long."""
    rng = np.random.default_rng(13)
    n = SR
    t = np.arange(n) / SR
    ticks = 24
    phase = (t * ticks) % 1 / ticks
    index = np.floor(t * ticks).astype(int) % ticks
    levels = np.tile([1.0, 0.55, 0.8, 0.45], ticks // 4) * rng.uniform(0.85, 1.0, ticks)
    strike = np.minimum(phase / 0.0005, 1) * np.exp(-phase / 0.0045) * levels[index]
    grit = periodic_noise(rng, n, lambda f: np.where((f > 1400) & (f < 6500), 1 / np.sqrt(np.maximum(f, 1)), 0))
    tick = grit / np.max(np.abs(grit)) * strike
    # Each tick also rings its rocker for a moment
    ring = (np.sin(2 * np.pi * 1680 * phase) + 0.5 * np.sin(2 * np.pi * 2930 * phase)) * np.exp(-phase / 0.006) * levels[index]

    hum = np.sin(2 * np.pi * 96 * t) + 0.5 * np.sin(2 * np.pi * 192 * t + 0.8) + 0.25 * np.sin(2 * np.pi * 288 * t + 1.7)
    hum *= 0.8 + 0.2 * np.cos(2 * np.pi * 12 * t)
    churn = periodic_noise(rng, n, lambda f: np.where((f > 50) & (f < 420), 1.0, 0.0))
    churn = churn / np.max(np.abs(churn)) * (0.7 + 0.3 * np.cos(2 * np.pi * 6 * t))

    mix = 0.7 * tick + 0.25 * ring + 0.2 * hum / np.max(np.abs(hum)) + 0.3 * churn
    return finish(np.tanh(1.3 * mix), 0.8)


def squeal_loop():
    """One second of a bearing running dry that repeats seamlessly: a shriek that wavers as the shaft turns, breaking
    up into a rasp where the metal sticks and slips. Every part is a whole number of cycles long."""
    rng = np.random.default_rng(29)
    n = SR
    t = np.arange(n) / SR
    f0 = 2450
    waver = 1.6 * np.sin(2 * np.pi * 7 * t) + 0.7 * np.sin(2 * np.pi * 3 * t + 1.1)
    shriek = (np.sin(2 * np.pi * f0 * t + waver) + 0.45 * np.sin(2 * np.pi * 2 * f0 * t + 2 * waver + 0.5)
              + 0.2 * np.sin(2 * np.pi * 3 * f0 * t + 3 * waver + 1.4) + 0.3 * np.sin(2 * np.pi * (f0 + 61) * t + 0.9))
    # It comes and goes with each turn of the shaft
    shriek *= 0.55 + 0.45 * np.cos(2 * np.pi * 4 * t) ** 2
    slip = (0.5 + 0.5 * np.sign(np.sin(2 * np.pi * 43 * t)))
    slip = lowpass(np.tile(slip, 3), 600, 2)[n:2 * n]
    rasp = periodic_noise(rng, n, lambda f: np.where((f > 1500) & (f < 5200), 1 / np.sqrt(np.maximum(f, 1)), 0))
    rasp = rasp / np.max(np.abs(rasp)) * slip
    mix = 0.6 * shriek / np.max(np.abs(shriek)) + 0.4 * rasp
    return finish(np.tanh(1.2 * mix), 0.8)


SOUNDS = {
    "cylinder_fire1": lambda: bang(11, 1.0),
    "cylinder_fire2": lambda: bang(23, 0.93),
    "cylinder_fire3": lambda: bang(37, 1.07),
    "starter_whir": starter_loop,
    "turbo_whistle": turbo_loop,
    "turbo_blowoff": turbo_blowoff,
    "supercharger_whine": supercharger_loop,
    "engine_catch": engine_catch,
    "engine_stall": engine_stall,
    "exhaust_pop1": lambda: exhaust_pop(53, 1.0),
    "exhaust_pop2": lambda: exhaust_pop(59, 0.88),
    "exhaust_pop3": lambda: exhaust_pop(61, 1.14),
    "metal_tick1": lambda: metal_tick(67, 2150),
    "metal_tick2": lambda: metal_tick(71, 2620),
    "metal_tick3": lambda: metal_tick(73, 1810),
    "engine_clatter": clatter_loop,
    "bearing_squeal": squeal_loop,
}


def write_wav(directory, name, samples):
    os.makedirs(directory, exist_ok=True)
    path = os.path.join(directory, name + ".wav")
    with wave.open(path, "wb") as fh:
        fh.setnchannels(1)
        fh.setsampwidth(2)
        fh.setframerate(SR)
        fh.writeframes((np.clip(samples, -1, 1) * 32767).astype("<i2").tobytes())
    print("wrote", os.path.normpath(path), "%.2fs" % (len(samples) / SR))


def main():
    args = sys.argv[1:]
    preview = None
    if args and args[0] == "--wav":
        if len(args) < 2:
            sys.exit("--wav needs a directory to write into")
        preview, args = args[1], args[2:]
    names = args or list(SOUNDS)
    for name in names:
        if name not in SOUNDS:
            sys.exit("Unknown sound %r; the sounds are: %s" % (name, ", ".join(SOUNDS)))
    lib = None if preview else load_sndfile()
    for name in names:
        if preview:
            write_wav(preview, name, SOUNDS[name]())
        else:
            write_ogg(lib, name, SOUNDS[name]())


if __name__ == "__main__":
    main()
