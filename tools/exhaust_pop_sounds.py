"""What is heard as flame and sparks leave an exhaust, for generate_sounds.py: the backfire, and the smaller spit of a
charge that boost lit early.

An engine with a note plays these itself, out of its own pipe (NoteBangs.java). These recordings stand in for one without:
the same recipe from the same grains and pipe (engine_pipe.py), so the two sound alike. A backfire is a big blow-down into
the pipe with its flame front's crack, the flame rolling out of the mouth and the sparks fizzing after it.
"""
import numpy as np
from scipy import signal

import engine_pipe as ep
from sound_dsp import finish

# NoteBangs.java's figures
SCALE = 0.07
POP_BODY = 270
POP_CRACK = 220
POP_FLAME = 20
SPIT_BODY = 60
SPIT_JET = 0.9
POP_FIZZ = 0.05
SPIT_FIZZ = 0.12


def white(rng, n):
    return rng.uniform(-1, 1, n)


def fizz(rng, n, level):
    """The sparks burning out."""
    t = np.arange(n) / ep.SR
    noise = white(rng, n)
    return (noise - ep.one_pole(noise, 3200)) * level * (1 - np.exp(-2 * np.pi * 8 * t)) * np.exp(-t / 0.085)


def flame(rng, n, level):
    """The flame rolling out of the mouth: low noise that swells just after the crack and is soon gone."""
    age = np.arange(n) / ep.SR / 0.016
    low = ep.one_pole(white(rng, n), 950)
    return (low - ep.one_pole(low, 110)) * age * np.exp(1 - age) * level


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
        ep.strike(raps, 0, ep.crack(rng, 1), level * POP_CRACK)
        into += flame(rng, n - k, level * POP_FLAME)
        mix[k:] += (ep.pipe(into, note * own) + raps) * SCALE
    loudest = int(max(pops, key=lambda pop: pop[1])[0] * ep.SR)
    mix[loudest:] += fizz(rng, n - loudest, POP_FIZZ)
    mix = ep.to_sound_rate(np.tanh(mix))
    mix *= np.clip((len(mix) - np.arange(len(mix))) / (0.05 * 44100), 0, 1)
    return finish(mix, 0.9)


def jet(rng, n, tone):
    """A quick "tssah" of burning gas that starts bright and darkens."""
    t = np.arange(n) / ep.SR
    bright = np.exp(-t / 0.035)
    share = ep.pole(1300 * tone) + (ep.pole(7500 * tone) - ep.pole(1300 * tone)) * bright
    noise = white(rng, n)
    low = np.zeros(n)
    held = 0.0
    for i in range(n):
        held += share[i] * (noise[i] - held)
        low[i] = held
    return (low - ep.one_pole(low, 500 * tone)) * SPIT_JET * np.exp(-t / 0.045) * np.minimum(1, (1 - bright) * 8)


def exhaust_spit(seed, tone):
    """A charge that boost lit early, spat out of the pipe with a flash: burning gas, a small puff down the pipe under it,
    and a fizz as the sparks burn out. One smooth breath of noise: ringing taps sound like something struck."""
    rng = np.random.default_rng(seed)
    n = int(0.32 * ep.SR)
    into = np.zeros(n)
    ep.strike(into, 0, ep.body(rng, 0, 1), SPIT_BODY)
    mix = ep.pipe(into, ep.run_note(0, 0.5) * tone) * SCALE + jet(rng, n, tone) + fizz(rng, n, SPIT_FIZZ)
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
