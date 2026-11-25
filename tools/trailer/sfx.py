"""Synthesised trailer hits, like music.py: original, so there is nothing to license.

    impact(strength)   a low boom with a short crack on top, for the title and a chapter's first frame
    riser(seconds)     noise and a tone that climb into the next hit
    sting()            the end card's hit: a boom and a ringing low chord that dies away

Each returns a float array of (samples, 2) at RATE, peaking near 1 before post.py sets its level.
"""
import numpy as np

RATE = 48000


def _room(x, seconds=1.4, wet=0.3, seed=3):
    rng = np.random.default_rng(seed)
    n = int(seconds * RATE)
    ir = rng.standard_normal(n) * np.exp(-np.arange(n) / (0.3 * RATE))
    size = 1
    while size < len(x) + n:
        size *= 2
    tail = np.fft.irfft(np.fft.rfft(x, size) * np.fft.rfft(ir, size), size)[: len(x) + n]
    tail /= max(1e-9, np.abs(tail).max())
    out = np.zeros(len(x) + n)
    out[: len(x)] = x * (1 - wet)
    return out + tail * wet * np.abs(x).max()


def _stereo(mono, width=0.0, seed=5):
    """Widens a mono hit a little by delaying one side a few hundred microseconds."""
    shift = int(width * RATE)
    right = np.concatenate([np.zeros(shift), mono])[: len(mono)] if shift else mono
    out = np.stack([mono, right], axis=1)
    return out / max(1e-9, np.abs(out).max())


def impact(strength=1.0, seed=11):
    rng = np.random.default_rng(seed)
    seconds = 0.9 + 0.8 * strength
    t = np.arange(int(seconds * RATE)) / RATE
    sweep = 38 + 110 * np.exp(-t * 16)  # a kick that falls from about 150 Hz to a 38 Hz rumble
    boom = np.sin(2 * np.pi * np.cumsum(sweep) / RATE) * np.exp(-t * (4.2 - 1.8 * strength))
    crack = rng.standard_normal(len(t)) * np.exp(-t * 55)
    crack = np.diff(crack, prepend=0.0)  # a bright edge, no low end
    metal = sum(np.sin(2 * np.pi * f * t) / (i + 1) for i, f in enumerate((211, 347, 523))) * np.exp(-t * 9)
    mono = boom + 0.35 * crack + 0.08 * strength * metal
    return _stereo(_room(mono, wet=0.22 + 0.1 * strength, seed=seed), width=0.0004)


def riser(seconds=3.0, seed=13):
    rng = np.random.default_rng(seed)
    n = int(seconds * RATE)
    t = np.arange(n) / RATE
    ramp = (t / seconds) ** 2
    noise = rng.standard_normal(n)
    # A one-pole low-pass that opens as it climbs: the noise brightens towards the hit
    out = np.zeros(n)
    y = 0.0
    alpha = 0.02 + 0.5 * ramp
    for i in range(n):
        y += alpha[i] * (noise[i] - y)
        out[i] = y
    freq = 90 * 2 ** (3 * t / seconds)  # three octaves up
    tone = np.sin(2 * np.pi * np.cumsum(freq) / RATE)
    mono = (out / max(1e-9, np.abs(out).max()) * 0.8 + tone * 0.35) * ramp
    mono[-int(0.02 * RATE):] *= np.linspace(1, 0, int(0.02 * RATE))  # cut clean at the hit
    return _stereo(mono, width=0.0006)


def sting(seed=17):
    boom = impact(1.0, seed=seed)
    seconds = 4.5
    t = np.arange(int(seconds * RATE)) / RATE
    chord = sum(np.sin(2 * np.pi * f * t) * (0.6 ** i) for i, f in enumerate((55.0, 82.4, 110.0, 164.8, 220.0)))
    chord *= np.minimum(1, t / 0.01) * np.exp(-t * 0.9)
    ring = _stereo(_room(chord, seconds=2.0, wet=0.35, seed=seed + 1), width=0.0008)
    out = np.zeros((max(len(boom), len(ring)), 2))
    out[: len(boom)] += boom
    out[: len(ring)] += 0.55 * ring
    return out / np.abs(out).max()
