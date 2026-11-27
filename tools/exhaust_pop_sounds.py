"""What is heard as flame and sparks leave an exhaust, for generate_sounds.py: the backfire, and the smaller spit of a
charge that boost lit early.

An engine with a note plays these itself, out of its own pipe (NoteBangs.java). These recordings stand in for one without:
the same recipe from the same grains and pipe (engine_pipe.py), so the two sound alike. A backfire is a big blow-down into
the pipe, the burning gas behind it and the thud of it going off. It opens over a few thousandths of a second and has
little above the pipe's own range, so it lands as a bang and not as a click.
"""
import numpy as np

import engine_pipe as ep
from sound_dsp import finish

# NoteBangs.java's figures
SCALE = 0.07
POP_BODY = 100
POP_CRACK = 28
POP_FLAME = 4.5
POP_THUD = 0.6
POP_MOUTH = 0.28
SPIT_BODY = 75
SPIT_JET = 2.2
POP_FIZZ = 0.006
SPIT_FIZZ = 0.012


def white(rng, n):
    return rng.uniform(-1, 1, n)


def follow(drive, hz):
    """A level that follows `drive` over a few thousandths of a second, so nothing starts on an edge."""
    return ep.one_pole(drive, hz)


def opening(x):
    """The blow-down opens over a few thousandths of a second: on its own edge it is a click."""
    t = np.clip(np.arange(len(x)) / ep.SR / 0.006, 0, 1)
    return x * t * t * (3 - 2 * t)


def fizz(rng, n, level):
    """The sparks burning out, far down."""
    t = np.arange(n) / ep.SR
    noise = white(rng, n)
    return (noise - ep.one_pole(noise, 2200)) * level * (1 - np.exp(-2 * np.pi * 8 * t)) * np.exp(-t / 0.085)


def flame(rng, n, level):
    """The burning gas: dull noise that opens fast, but not at once, and is gone in about a tenth of a second."""
    t = np.arange(n) / ep.SR
    noise = ep.one_pole(ep.one_pole(white(rng, n), 1100), 1100)
    return (noise - ep.one_pole(noise, 130)) * follow(level * np.exp(-t / 0.032), 65)


def thud(n, note):
    """The thud of the gas going off at the mouth, from above the pipe's note to under it."""
    t = np.arange(n) / ep.SR
    phase = 2 * np.pi * np.cumsum(note * (0.6 + 0.7 * np.exp(-t / 0.014))) / ep.SR
    return np.sin(phase) * follow(POP_THUD * np.exp(-t / 0.045), 55)


def exhaust_pop(seed, note, pops):
    """A backfire in a pipe of `note` hertz. `pops` lists its bangs as (seconds, loudness, pitch against the take's
    own); a bang of another pitch goes out of a pipe of that pitch."""
    rng = np.random.default_rng(seed)
    n = int((max(at for at, _, _ in pops) + 0.4) * ep.SR)
    mix = np.zeros(n)
    for at, level, own in pops:
        k = int(at * ep.SR)
        into, raps = np.zeros(n - k), np.zeros(n - k)
        ep.strike(into, 0, ep.body(rng, 2, 2), level * POP_BODY)
        ep.strike(raps, 0, ep.crack(rng, 2), level * POP_CRACK)
        gas = flame(rng, n - k, level * POP_FLAME)
        into, raps = opening(into) + gas, opening(raps)
        mix[k:] += (ep.pipe(into, note * own) + raps) * SCALE + gas * POP_MOUTH + level * thud(n - k, note * own)
    loudest = int(max(pops, key=lambda pop: pop[1])[0] * ep.SR)
    mix[loudest:] += fizz(rng, n - loudest, POP_FIZZ)
    mix = ep.to_sound_rate(np.tanh(mix))
    mix *= np.clip((len(mix) - np.arange(len(mix))) / (0.05 * 44100), 0, 1)
    return finish(mix, 0.9)


def jet(rng, n, tone):
    """A soft "phah" of burning gas that darkens as it goes."""
    t = np.arange(n) / ep.SR
    bright = np.exp(-t / 0.03)
    share = ep.pole(650 * tone) + (ep.pole(2600 * tone) - ep.pole(650 * tone)) * bright
    noise = white(rng, n)
    low = np.zeros(n)
    first = second = 0.0
    for i in range(n):
        first += share[i] * (noise[i] - first)
        second += share[i] * (first - second)
        low[i] = second
    return (low - ep.one_pole(low, 220 * tone)) * follow(SPIT_JET * np.exp(-t / 0.045), 32)


def exhaust_spit(seed, tone):
    """A charge that boost lit early, spat out of the pipe with a flash: a breath of burning gas and a small puff down the
    pipe under it. One smooth breath of noise: ringing taps sound like something struck, and a bright one clicks."""
    rng = np.random.default_rng(seed)
    n = int(0.32 * ep.SR)
    into = np.zeros(n)
    ep.strike(into, 0, ep.body(rng, 1, 0), SPIT_BODY)
    mix = ep.pipe(opening(into), ep.run_note(0, 0.5) * tone) * SCALE + jet(rng, n, tone) + fizz(rng, n, SPIT_FIZZ)
    mix = ep.to_sound_rate(np.tanh(mix))
    mix *= np.clip((len(mix) - np.arange(len(mix))) / (0.05 * 44100), 0, 1)
    return finish(mix, 0.9)


SOUNDS = {
    "exhaust_spit1": lambda: exhaust_spit(211, 1.0),
    "exhaust_spit2": lambda: exhaust_spit(223, 0.87),
    "exhaust_spit3": lambda: exhaust_spit(227, 1.13),
    # A single bang; one with a second pop on its heels; a quick "pa-BANG"; a deep single; one that goes off in three
    "exhaust_pop1": lambda: exhaust_pop(53, 86, [(0.0, 1.0, 1.0)]),
    "exhaust_pop2": lambda: exhaust_pop(59, 78, [(0.0, 1.0, 1.0), (0.105, 0.45, 1.2)]),
    "exhaust_pop3": lambda: exhaust_pop(61, 100, [(0.0, 0.4, 1.25), (0.055, 1.0, 1.0)]),
    "exhaust_pop4": lambda: exhaust_pop(197, 62, [(0.0, 1.0, 1.0)]),
    "exhaust_pop5": lambda: exhaust_pop(199, 118, [(0.0, 1.0, 1.0), (0.075, 0.5, 1.15), (0.17, 0.3, 1.3)]),
}
