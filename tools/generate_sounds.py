"""Synthesizes the mod's sounds and encodes them to Ogg Vorbis under assets/create_engines/sounds.

Needs numpy and scipy, plus a way to write Ogg Vorbis: a libsndfile built with Vorbis support (Audacity ships one:
point CE_SNDFILE at its sndfile.dll if it isn't found in the usual install location), or failing that ffmpeg with
libvorbis on the path. Run from anywhere; output paths are relative to this file. Name sounds to make only those, which
leaves the others' recordings alone (a different encoder gives a different file even from the same synthesis):

    python tools/generate_sounds.py
    python tools/generate_sounds.py turbo_whistle turbo_blowoff supercharger_whine
    python tools/generate_sounds.py --wav build/sound-preview engine_catch engine_stall

Every sound that plays once goes through the level pass (sound_levels.py): its own loudness, a peak ceiling, no DC and a
fade at each end.

With --wav DIR the named sounds are written as WAV files into DIR instead, to listen to before encoding them.

Sounds:
  cylinder_fire1..3  the bang of a charge igniting, three cylinders' worth: a hard crack, a mid slap, a gritty thump, an
                     echo off the pipe's end and the pipe ringing after
  starter_whir       a seamless loop of a starter motor turning over; the game bends its pitch with the motor's speed
  turbo_whistle      a seamless loop of a spooling turbo: a breathy whistle over a little rushing air (boost_sounds.py)
  turbo_blowoff, turbo_blowoff2..3  the blow-off valve dumping boost: a hiss of air that starts to flutter as the
                     compressor surges; three takes, fluttering at their own rates (boost_sounds.py)
  supercharger_whine a seamless loop of a roots blower: the singing tone of its lobes, the growl of its gears and the air
                     it gulps (boost_sounds.py)
  engine_catch, engine_catch2..3  an engine catching on the starter: a cough or two, a stumble, then firings crowding
                     together as it flares and falls back (start_stall_sounds.py)
  engine_stall, engine_stall2..3  an engine dying: uneven last firings and unlit chuffs, the crank rocking to a stop
                     against compression and a sigh from the exhaust (start_stall_sounds.py)
  engine_settle, engine_settle2..3  a stopping crank alone, rocking against compression, and the sigh after it
  engine_catch_hard, engine_catch_hard2..3  a cold or long-cranked engine: coughs that come to nothing, then a ragged catch
  engine_stall_lugged, engine_stall_lugged2..3  dragged down by its load: hard, slowing firings and a heavy stop
  engine_stall_starved, engine_stall_starved2..3  out of fuel: weak firings among misses, and a soft stop
  exhaust_pop1..5    unburned fuel lighting in a hot exhaust (the backfire): a crack, a blow-down down the note's pipe, a
                     soft rush of flame and the sparks fizzing after it, some with a smaller pop or two (exhaust_pop_sounds)
  exhaust_spit1..3   a charge boost lit early, spat out of the pipe: a quick hiss of burning gas, a small puff and a fizz
                     (exhaust_pop_sounds)
  metal_tick1..3     hot metal ticking as it cools: a small bright tink
  preignition_knock1..3  a charge lighting itself early under boost: a quick run of hard metallic knocks (at a cylinder, in wear mode)
  engine_clatter     a seamless loop of valve gear: soft tappet knocks over the hum of the gears (no longer played; the sound ID is kept)
  bearing_squeal     a seamless loop of bearings running dry: a wavering shriek over a stick-slip rasp
  intake_roar        a seamless loop of air gulped through an intake: a hollow rush swelling in eight pulses a second
  rod_thrown1..2, gasket_blow1..2, apex_fail1..2, head_warp1..2, turbo_fail1..2, piston_seize1..2, seize_warning1..2
                     the wear mode's failures and the dry piston's warning (failure_sounds.py)
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

import boost_sounds
import exhaust_pop_sounds
import failure_sounds
import sound_levels
import start_stall_sounds
from sound_dsp import SR, band, finish, highpass, lowpass

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
            lib.sf_command.restype = ctypes.c_int
            lib.sf_command.argtypes = [ctypes.c_void_p, ctypes.c_int, ctypes.c_void_p, ctypes.c_int]
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


# Sounds that are mostly noise bursts, which the encoder's default quality smears into a grainy, crushed-sounding warble.
# Only these are encoded at a higher quality; the others keep their recordings.
HIGH_QUALITY = {"exhaust_pop%d" % i: 0.8 for i in range(1, 6)}
HIGH_QUALITY.update({"exhaust_spit%d" % i: 0.8 for i in range(1, 4)})
HIGH_QUALITY.update({name: 0.8 for name in start_stall_sounds.SOUNDS})
HIGH_QUALITY.update({name: 0.8 for name in failure_sounds.SOUNDS})
HIGH_QUALITY.update({name: 0.8 for name in boost_sounds.SOUNDS if name.startswith("turbo_blowoff")})


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
    quality = HIGH_QUALITY.get(name)
    if quality is not None:
        # SFC_SET_VBR_ENCODING_QUALITY: libsndfile's default is low, and short bursts of noise show it as a warble
        q = ctypes.c_double(quality)
        lib.sf_command(handle, 0x1300, ctypes.byref(q), 8)
    data = np.ascontiguousarray(samples, dtype=np.float32)
    lib.sf_write_float(handle, data.ctypes.data_as(ctypes.POINTER(ctypes.c_float)), len(data))
    lib.sf_close(handle)
    print("wrote", os.path.normpath(path), "%.2fs" % (len(data) / SR))


def bang(seed, pitch, bright=1.0):
    """A charge going off, built to be heard on its own at a few firings a second (a slow engine's 256 to 512 rpm):
    a hard, near-instant crack of the flame front, a mid-range slap of the pressure wave, the low thump of the gas
    punching the pipe, an echo as the wave comes back off the pipe's end, and the pipe ringing after. `bright` moves the
    crack and slap up (a small, tight cylinder) or down (a big, heavy one), so the three variants are three cylinders."""
    rng = np.random.default_rng(seed)
    n = int(0.55 * SR)
    t = np.arange(n) / SR
    rise = np.minimum(t / 0.0004, 1)

    # The crack: broadband and over in a few milliseconds, with a second, lower hit as the wave leaves the bore
    crack = band(rng.standard_normal(n), 1400 * bright * pitch, 9000, 2) * rise * np.exp(-t / 0.005)
    late = np.maximum(t - 0.006, 0)
    crack += 0.5 * band(rng.standard_normal(n), 900 * bright, 4200, 2) * (t >= 0.006) * np.exp(-late / 0.01)
    # The slap: the mid-range of the pressure wave, which is what makes a bang read as a bang and not a puff
    slap = band(rng.standard_normal(n), 300 * pitch, 2200 * bright, 2) * rise * np.exp(-t / 0.022)

    # The thump drops in pitch as the pressure falls, and is driven into a soft clip so it has a bite and a grunt
    sweep = (44 + 150 * np.exp(-t / 0.022)) * pitch
    thump = np.sin(2 * np.pi * np.cumsum(sweep) / SR) * np.minimum(t / 0.001, 1) * np.exp(-t / 0.085)
    thump = np.tanh(1.8 * thump)
    body = lowpass(rng.standard_normal(n), 500 * pitch) * rise * np.exp(-t / 0.06)

    ring_noise = lowpass(rng.standard_normal(n), 400)
    ring = (np.sin(2 * np.pi * 88 * pitch * t) + 0.6 * np.sin(2 * np.pi * 176 * pitch * t + 1.1)
            + 0.35 * np.sin(2 * np.pi * 265 * pitch * t + 2.3))
    ring = ring * (0.75 + 0.25 * ring_noise / np.max(np.abs(ring_noise))) * np.minimum(t / 0.008, 1) * np.exp(-t / 0.12)

    mix = 0.9 * crack + 0.75 * slap + 1.0 * thump + 0.45 * body + 0.3 * ring
    # The wave coming back off the end of the pipe, a short way behind and a little duller
    delay = int(0.0115 * SR)
    echo = np.zeros(n)
    echo[delay:] = lowpass(mix, 2500, 1)[:n - delay]
    mix = np.tanh(1.5 * (mix + 0.3 * echo))
    mix *= np.clip((n - np.arange(n)) / (0.05 * SR), 0, 1)
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


def preignition_knock(seed, f0):
    """A charge lighting itself early: pinking, a quick run of three or four hard metallic cracks, each a struck
    cylinder wall ringing (lower and longer than the cooling tick) over a flat crack of noise, getting softer."""
    rng = np.random.default_rng(seed)
    n = int(0.3 * SR)
    t = np.arange(n) / SR
    mix = np.zeros(n)
    hits = 3 + int(rng.integers(0, 2))
    at = 0.0
    for i in range(hits):
        k = int(at * SR)
        m = n - k
        u = np.arange(m) / SR
        level = 1.0 - 0.18 * i
        ring = sum(lv * np.sin(2 * np.pi * f0 * ratio * u + rng.uniform(0, 2 * np.pi)) * np.exp(-u / dec)
                   for ratio, lv, dec in ((1.0, 1.0, 0.022), (1.9, 0.7, 0.014), (3.1, 0.4, 0.008)))
        crack = band(rng.standard_normal(m), 1500, 6500, 2) * np.exp(-u / 0.004)
        hit = (ring + 1.2 * crack / np.max(np.abs(crack))) * np.minimum(u / 0.0003, 1)
        mix[k:] += level * hit
        at += 0.028 + rng.uniform(0, 0.012)
    mix = np.tanh(1.3 * mix / np.max(np.abs(mix)))
    mix *= np.clip((n - np.arange(n)) / (0.05 * SR), 0, 1)
    return finish(mix, 0.8)


def clatter_loop():
    """One second of valve gear that repeats seamlessly: sixteen soft tappet knocks, no two quite alike but the pattern
    of four coming round again as the cam does, over a low hum of gears and a slow churn. Deliberately dull: the knocks
    are low and rounded with nothing above a few kilohertz and no ringing, so a running engine reads as machinery and not
    as a buzz. Every part is a whole number of cycles long."""
    rng = np.random.default_rng(13)
    n = SR
    t = np.arange(n) / SR
    ticks = 16
    phase = (t * ticks) % 1 / ticks
    index = np.floor(t * ticks).astype(int) % ticks
    levels = np.tile([1.0, 0.6, 0.85, 0.5], ticks // 4) * rng.uniform(0.8, 1.0, ticks)
    strike = np.minimum(phase / 0.0018, 1) * np.exp(-phase / 0.011) * levels[index]
    knock = periodic_noise(rng, n, lambda f: np.where((f > 220) & (f < 2600), 1 / np.maximum(f, 1) ** 0.6, 0))
    tick = knock / np.max(np.abs(knock)) * strike
    # A dull body to each knock, a low thud rather than a ring
    thud = np.sin(2 * np.pi * 240 * phase) * np.exp(-phase / 0.01) * levels[index]

    hum = np.sin(2 * np.pi * 80 * t) + 0.4 * np.sin(2 * np.pi * 160 * t + 0.8)
    hum *= 0.85 + 0.15 * np.cos(2 * np.pi * 8 * t)
    churn = periodic_noise(rng, n, lambda f: np.where((f > 50) & (f < 300), 1.0, 0.0))
    churn = churn / np.max(np.abs(churn)) * (0.75 + 0.25 * np.cos(2 * np.pi * 4 * t))

    mix = 0.75 * tick + 0.3 * thud + 0.15 * hum / np.max(np.abs(hum)) + 0.2 * churn
    return finish(np.tanh(1.1 * mix), 0.7)


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


def intake_roar_loop():
    """One second of air being drawn into an intake that repeats seamlessly: a hollow rush with a low body, swelling
    eight times a second as the cylinders each take a gulp, no two gulps quite alike. The game bends the pitch with the
    engine, which speeds the pulses up with it. Every part is a whole number of cycles long."""
    rng = np.random.default_rng(41)
    n = SR
    t = np.arange(n) / SR
    pulses = 8
    index = np.floor(t * pulses).astype(int) % pulses
    levels = rng.uniform(0.8, 1.0, pulses)
    swell = (0.5 - 0.5 * np.cos(2 * np.pi * pulses * t)) ** 1.4
    gulp = (0.3 + 0.7 * swell) * levels[index]
    rush = periodic_noise(rng, n, lambda f: np.where((f > 160) & (f < 1700), 1 / np.sqrt(np.maximum(f, 1)), 0))
    rush = rush / np.max(np.abs(rush)) * gulp
    body = periodic_noise(rng, n, lambda f: np.where((f > 55) & (f < 230), 1.0, 0.0))
    body = body / np.max(np.abs(body)) * (0.6 + 0.4 * swell)
    hiss = periodic_noise(rng, n, lambda f: np.where((f > 2200) & (f < 5500), 1 / np.maximum(f, 1), 0))
    hiss = hiss / np.max(np.abs(hiss)) * swell ** 2
    mix = 0.7 * rush + 0.4 * body + 0.12 * hiss
    return finish(np.tanh(1.2 * mix), 0.8)


def soot_catch(seed, pitch):
    """A pinch of soot dropping into a steel canister: a dull knock and a little dry grit pattering after it."""
    rng = np.random.default_rng(seed)
    n = int(0.28 * SR)
    t = np.arange(n) / SR
    knock = np.sin(2 * np.pi * np.cumsum((190 + 120 * np.exp(-t / 0.015)) * pitch) / SR) * np.minimum(t / 0.0006, 1) * np.exp(-t / 0.03)
    grit = band(rng.standard_normal(n), 1800 * pitch, 6500, 2)
    # Three or four grains landing at uneven times
    patter = np.zeros(n)
    for at, level in ((0.012, 1.0), (0.05, 0.55), (0.085, 0.35), (0.13, 0.2)):
        i = int(at * SR)
        patter[i:] += level * np.exp(-(t[: n - i]) / 0.006)
    mix = knock + 0.35 * grit / np.max(np.abs(grit)) * patter
    mix *= np.clip((n - np.arange(n)) / (0.05 * SR), 0, 1)
    return finish(mix, 0.7)


def flare_whoosh(seed):
    """A gas flame catching and settling: a soft rising rush of air, with a low roar under it, fading out."""
    rng = np.random.default_rng(seed)
    n = int(0.9 * SR)
    t = np.arange(n) / SR
    swell = np.minimum(t / 0.18, 1) ** 2 * np.exp(-np.maximum(t - 0.25, 0) / 0.35)
    rush = band(rng.standard_normal(n), 500, 3200, 2) * swell
    roar = lowpass(rng.standard_normal(n), 260, 2) * swell
    mix = 0.6 * rush / np.max(np.abs(rush)) + 0.9 * roar / np.max(np.abs(roar))
    mix *= np.clip((n - np.arange(n)) / (0.2 * SR), 0, 1)
    return finish(np.tanh(1.1 * mix), 0.6)


SOUNDS = {
    "soot_catch1": lambda: soot_catch(79, 1.0),
    "soot_catch2": lambda: soot_catch(83, 0.9),
    "soot_catch3": lambda: soot_catch(89, 1.12),
    "flare_whoosh1": lambda: flare_whoosh(97),
    "flare_whoosh2": lambda: flare_whoosh(101),
    "cylinder_fire1": lambda: bang(11, 1.0, 1.0),
    "cylinder_fire2": lambda: bang(23, 0.9, 0.78),
    "cylinder_fire3": lambda: bang(37, 1.1, 1.3),
    "starter_whir": starter_loop,
    **boost_sounds.SOUNDS,
    **start_stall_sounds.SOUNDS,
    **exhaust_pop_sounds.SOUNDS,
    "metal_tick1": lambda: metal_tick(67, 3000),
    "metal_tick2": lambda: metal_tick(71, 3600),
    "metal_tick3": lambda: metal_tick(73, 2500),
    "preignition_knock1": lambda: preignition_knock(103, 1150),
    "preignition_knock2": lambda: preignition_knock(107, 1380),
    "preignition_knock3": lambda: preignition_knock(109, 980),
    "engine_clatter": clatter_loop,
    "bearing_squeal": squeal_loop,
    "intake_roar": intake_roar_loop,
    **failure_sounds.SOUNDS,
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
        samples = sound_levels.level(name, SOUNDS[name]())
        if preview:
            write_wav(preview, name, samples)
        else:
            write_ogg(lib, name, samples)


if __name__ == "__main__":
    main()
