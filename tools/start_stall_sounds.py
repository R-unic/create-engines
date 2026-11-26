"""An engine catching and an engine dying, for generate_sounds.py.

Both are heard from outside, down the exhaust, and are built from the same parts: beats (one cylinder's charge going down
the pipe) and, as an engine dies, chuffs (a cylinder pushing its air out unlit). Nothing is a pitched sweep, which reads
as a drum machine: the weight is noise and the one pressure wave. The beats are unevenly spaced, as an engine that is
not yet, or no longer, running is.
"""
import numpy as np

from sound_dsp import SR, band, finish, highpass, lowpass


def darkening(source, start, floor, fall):
    """`source` through a two-pole low-pass whose cutoff falls from `start` hertz to `floor` with time constant `fall`."""
    t = np.arange(len(source)) / SR
    share = 1 - np.exp(-2 * np.pi * (floor + (start - floor) * np.exp(-t / fall)) / SR)
    out = np.zeros(len(source))
    one = two = 0.0
    for i in range(len(source)):
        one += share[i] * (source[i] - one)
        two += share[i] * (one - two)
        out[i] = two
    return out


def unit(x):
    return x / np.max(np.abs(x))


def beat(rng, n, pitch, force):
    """One firing down the exhaust. `force`, 0 to 1, is how well the charge burned: a hard one has an edge and opens
    bright, a weak one is a soft, dull puff."""
    t = np.arange(n) / SR
    onset = np.minimum(t / (0.003 - 0.0022 * force), 1)
    # The pressure wave: a shove out and a longer, shallower pull back
    length = 0.0065 / pitch
    wave_front = (1 - t / length) * np.exp(-t / length) * onset
    gas = darkening(rng.standard_normal(n), (700 + 2800 * force) * pitch, 220 * pitch, 0.016)
    gas = unit(highpass(gas, 110, 2)) * onset * (0.8 * np.exp(-t / 0.024) + 0.2 * np.exp(-t / 0.085))
    thud = unit(band(rng.standard_normal(n), 55 * pitch, 170 * pitch, 2)) * np.minimum(t / 0.005, 1) * np.exp(-t / 0.05)
    # The air in the pipe, set going by the pulse: hollow, from noise, so it has no note of its own
    pipe = unit(band(rng.standard_normal(n), 190 * pitch, 290 * pitch, 1)) * np.minimum(t / 0.012, 1) * np.exp(-t / 0.075)
    return unit((0.4 + 0.45 * force) * wave_front + 0.85 * gas + 0.9 * thud + 0.35 * pipe)


def chuff(rng, n, pitch):
    """A cylinder that did not light pushing its air out: a breath with no edge, swelling and gone."""
    t = np.arange(n) / SR
    swell = np.minimum(t / 0.035, 1) ** 1.5 * np.exp(-np.maximum(t - 0.035, 0) / 0.045)
    air = unit(band(rng.standard_normal(n), 220 * pitch, 1100 * pitch, 2))
    low = unit(band(rng.standard_normal(n), 60 * pitch, 160 * pitch, 2))
    return unit((0.7 * air + 0.8 * low) * swell)


def place(mix, at, level, make):
    """Adds make(samples left) to `mix` from `at` seconds on."""
    k = int(at * SR)
    mix[k:] += level * make(len(mix) - k)


def fade_out(mix, seconds):
    n = len(mix)
    return mix * np.clip((n - np.arange(n)) / (seconds * SR), 0, 1)


def engine_catch(seed, pitch, coughs, run):
    """An engine catching on the starter: a cough or two with a stumble after, then it takes hold, the firings crowding
    together as it flares up and falling away as the engine's own note takes over. `coughs` lists the tentative firings
    as (seconds, force); `run` is (seconds it takes hold, first gap, how much shorter each gap is than the last)."""
    rng = np.random.default_rng(seed)
    n = int(1.35 * SR)
    t = np.arange(n) / SR
    mix = np.zeros(n)
    for at, force in coughs:
        place(mix, at, 0.45 + 0.45 * force, lambda m: beat(rng, m, pitch * (0.86 + 0.1 * force), force))
    at, gap, shrink = run
    start = at
    while at < 1.05:
        # How far the flare has got: up quickly, then sinking back under the note that is fading in
        done = (at - start) / (1.05 - start)
        level = (0.65 + 0.35 * min(done / 0.35, 1)) * (1 - 0.7 * max(done - 0.4, 0) / 0.6)
        force = min(0.55 + 0.7 * done, 0.95) * rng.uniform(0.85, 1.05)
        place(mix, at, level * rng.uniform(0.85, 1.0),
              lambda m: beat(rng, m, pitch * (0.96 + 0.18 * done), force))
        at += gap * rng.uniform(0.9, 1.1)
        gap = max(gap * shrink, 0.052)
    # The rush of gas as the revs climb
    rise = np.clip((t - start) / 0.3, 0, 1) ** 2 * np.exp(-np.maximum(t - start - 0.35, 0) / 0.2)
    mix += 0.12 * unit(band(rng.standard_normal(n), 160, 1300, 2)) * rise
    mix = lowpass(highpass(np.tanh(1.6 * mix), 45), 7500, 2)
    return finish(fade_out(mix, 0.2), 0.85)


def engine_stall(seed, pitch, fires, chuffs, stop):
    """An engine dying: its last firings coming further apart and weaker, a cylinder or two that do not light, then the
    crank fetching up against compression and rocking back, and what is left in the exhaust sighing out. `fires` lists
    the last firings as (seconds, force), `chuffs` the unlit ones as (seconds, loudness), `stop` when the crank stops."""
    rng = np.random.default_rng(seed)
    n = int((stop + 0.85) * SR)
    t = np.arange(n) / SR
    mix = np.zeros(n)
    for at, force in fires:
        place(mix, at, 0.3 + 0.7 * force, lambda m: beat(rng, m, pitch * (0.74 + 0.28 * force), force))
    for at, level in chuffs:
        place(mix, at, level, lambda m: chuff(rng, m, pitch))

    def knock(m):
        u = np.arange(m) / SR
        onset = np.minimum(u / 0.0015, 1)
        iron = sum(level * np.sin(2 * np.pi * 128 * pitch * ratio * u) * np.exp(-u / decay)
                   for ratio, level, decay in ((1.0, 1.0, 0.045), (2.32, 0.4, 0.028), (4.1, 0.15, 0.014)))
        slack = unit(band(rng.standard_normal(m), 250 * pitch, 1700 * pitch, 2)) * np.exp(-u / 0.011)
        low = unit(band(rng.standard_normal(m), 50, 150, 2)) * np.exp(-u / 0.06)
        return unit((0.7 * iron + 0.6 * slack + 0.8 * low) * onset)

    # Up against compression, back off it, and a last small settle
    for after, level in ((0.0, 0.5), (0.15, 0.26), (0.25, 0.11)):
        place(mix, stop + after, level, knock)
    sigh = darkening(rng.standard_normal(n), 3200, 500, 0.8)
    swell = np.clip((t - stop + 0.1) / 0.2, 0, 1) * np.exp(-np.maximum(t - stop - 0.1, 0) / 0.22)
    mix += 0.11 * unit(highpass(sigh, 300, 2)) * swell
    mix = lowpass(highpass(np.tanh(1.5 * mix), 45), 7000, 2)
    return finish(fade_out(mix, 0.2), 0.85)


# Three takes of each, so an engine does not start and die the same way every time
SOUNDS = {
    "engine_catch": lambda: engine_catch(211, 1.0, [(0.0, 0.45), (0.25, 0.7)], (0.42, 0.125, 0.83)),
    "engine_catch2": lambda: engine_catch(223, 0.95, [(0.0, 0.65)], (0.29, 0.14, 0.84)),
    "engine_catch3": lambda: engine_catch(227, 1.05, [(0.0, 0.35), (0.19, 0.4), (0.3, 0.8)], (0.52, 0.11, 0.83)),
    "engine_stall": lambda: engine_stall(
        229, 1.0, [(0.0, 0.9), (0.12, 0.75), (0.28, 0.5), (0.5, 0.28)], [(0.66, 0.2)], 0.86),
    "engine_stall2": lambda: engine_stall(
        233, 0.93, [(0.0, 0.85), (0.15, 0.6), (0.43, 0.75)], [(0.28, 0.25), (0.65, 0.2)], 0.9),
    "engine_stall3": lambda: engine_stall(
        239, 1.06, [(0.0, 0.8), (0.11, 0.65), (0.24, 0.4)], [(0.42, 0.27), (0.62, 0.17)], 0.8),
}
