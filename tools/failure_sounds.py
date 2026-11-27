"""The wear mode's failures and the dry piston's warning, for generate_sounds.py.

What made the first takes sound like cartoons, and so what these keep away from: a tone that sweeps (a slide whistle, a
theremin), a few clean partials left to ring (a bell, a spring), a thump that is one falling sine (a toy drum) and
anything that repeats evenly or slows by a fixed ratio (a bouncing ball). Here instead:
  struck metal   many close, unrelated modes that die in hundredths of a second, the high ones first, set off by a
                 contact a thousandth or two long: a block bolted down does not ring
  weight         low noise, not a note
  scraping       jittered stick-slip grains and rough noise through a few broad resonances, its loudness wandering
  gas            noise shaped to the leak, fluttering at random, spitting
  timing         every gap and level drawn at random
and everything is heard among a few scattered reflections, as off the ground and the engine around it.
"""
import numpy as np
from scipy import signal

from sound_dsp import SR, band, finish, highpass, lowpass


def seconds(n):
    return np.arange(n) / SR


def unit(x):
    return x / max(np.max(np.abs(x)), 1e-12)


def wander(rng, n, hz):
    """A slow random line about zero, a standard deviation of one, moving about `hz` times a second."""
    x = lowpass(rng.standard_normal(n + SR // 4), hz, 2)[SR // 4:]
    return x / max(np.std(x), 1e-12)


def flutter(rng, n, hz, depth):
    """A loudness that never holds still: the turbulence of a leak, the unevenness of a scrape."""
    return np.exp(depth * np.clip(wander(rng, n, hz), -2.5, 2.5))


def ramp(t, at, over):
    """0 before `at`, 1 from `over` later on, eased."""
    x = np.clip((t - at) / over, 0, 1)
    return x * x * (3 - 2 * x)


def add(mix, at, x, level=1.0):
    i = int(at * SR)
    n = min(len(x), len(mix) - i)
    if n > 0:
        mix[i:i + n] += level * x[:n]


def struck(rng, low, high, count, decay, contact, length):
    """A lump of metal hit once. `count` modes between `low` and `high` hertz, none in step with another; a mode at `low`
    dies in about `decay` seconds and higher ones sooner. The hit lasts `contact` seconds: the longer, the duller."""
    n = int(length * SR)
    t = seconds(n)
    ring = np.zeros(n)
    for f in np.exp(rng.uniform(np.log(low), np.log(high), count)):
        d = decay * (low / f) ** 0.55 * rng.uniform(0.5, 1.5)
        ring += rng.uniform(0.3, 1) * (low / f) ** 0.25 * np.sin(2 * np.pi * f * t + rng.uniform(0, 2 * np.pi)) * np.exp(-t / d)
    hit = rng.standard_normal(int(4 * contact * SR) + 8)
    hit *= np.exp(-seconds(len(hit)) / contact)
    ring = signal.fftconvolve(ring, hit)[:n]
    edge = np.zeros(n)
    add(edge, 0, lowpass(hit, min(0.9 / contact, 12000), 2))
    return unit(0.75 * unit(ring) + 0.5 * unit(edge))


def weight(rng, low, high, decay, length):
    """The heft of a blow: low noise, felt more than pitched."""
    n = int(length * SR)
    t = seconds(n)
    return unit(band(rng.standard_normal(n), low, high, 2) * ramp(t, 0, 0.004) * np.exp(-t / decay))


def burst(rng, low, high, decay, length, rise=0.0006):
    """A shock of broadband noise: a crack when short and bright, a bark of gas when longer and lower."""
    n = int(length * SR)
    t = seconds(n)
    return unit(band(rng.standard_normal(n), low, high, 2) * ramp(t, 0, rise) * np.exp(-t / decay))


def resonate(x, modes):
    """`x` through the resonances of the part it comes from: (hertz, Q, level) each."""
    out = np.zeros(len(x))
    for f, q, level in modes:
        b, a = signal.iirpeak(f, q, fs=SR)
        out += level * unit(signal.lfilter(b, a, x))
    return out


def scrape(rng, rate, modes, rough=0.6):
    """Metal dragged on metal: it sticks and lets go `rate` times a second (a figure for each sample), never evenly, each
    letting go a grain of its own size, over the hiss of the two surfaces."""
    n = len(rate)
    jitter = np.clip(1 + 0.35 * wander(rng, n, 60), 0.25, 2.2)
    phase = np.cumsum(rate * jitter) / SR
    slips = np.flatnonzero(np.diff(np.floor(phase), prepend=0) > 0)
    grains = np.zeros(n)
    grains[slips] = rng.lognormal(0, 0.55, len(slips)) * rng.choice((-1.0, 1.0), len(slips))
    grains = signal.fftconvolve(grains, np.exp(-seconds(120) / 0.00045))[:n]
    surface = rng.standard_normal(n) * flutter(rng, n, 45, 0.55)
    drive = unit(grains) * 3 + rough * unit(highpass(surface, 500))
    return unit(0.8 * unit(resonate(drive, modes)) + 0.25 * unit(band(drive, 700, 7500, 2)))


def gas(rng, n, low, high, flutter_hz=35, depth=0.4):
    """Gas forced through a gap."""
    return unit(band(rng.standard_normal(n), low, high, 2) * flutter(rng, n, flutter_hz, depth))


def spits(rng, n, a_second, low, high):
    """Droplets and grit blown out with a leak: tiny cracks at random."""
    x = np.zeros(n)
    at = np.flatnonzero(rng.random(n) < a_second / SR)
    x[at] = rng.lognormal(0, 0.8, len(at)) * rng.choice((-1.0, 1.0), len(at))
    return unit(band(x, low, high, 2))


def debris(rng, mix, start, end, count, level):
    """Bits of metal thrown clear and landing: small dull clinks, thickest at first, no two alike."""
    for _ in range(count):
        at = start + (end - start) * rng.beta(1.2, 2.6)
        f = rng.uniform(900, 2600)
        clink = struck(rng, f, f * 3.4, 9, rng.uniform(0.006, 0.022), 0.0002, 0.08)
        add(mix, at, clink, level * rng.uniform(0.25, 1) * (1 - 0.6 * (at - start) / (end - start)))


def place(rng, x, wet=0.28):
    """A few reflections off what stands around, each duller than the sound itself."""
    reflections = np.zeros(int(0.09 * SR))
    for at in rng.uniform(0.004, 0.085, 28):
        reflections[int(at * SR)] += rng.choice((-1.0, 1.0)) * rng.uniform(0.3, 1) * np.exp(-at / 0.032)
    reflections /= np.sqrt(np.sum(reflections ** 2))
    return x + wet * signal.fftconvolve(lowpass(x, 3800, 1), reflections)[:len(x)]


def done(rng, mix, drive, tail, peak):
    """Placed, driven into a soft clip as a loud thing overloads what hears it, and faded over its last `tail` seconds."""
    mix = highpass(mix, 32)
    mix = np.tanh(drive * unit(place(rng, mix)))
    n = len(mix)
    mix *= np.clip((n - np.arange(n)) / (tail * SR), 0, 1)
    return finish(mix, peak)


def rod_thrown(seed, size):
    """A connecting rod going through the head: one enormous flat bang as the casting breaks, the loose end hammering the
    block a few more times as the crank carries it round and stops, broken metal coming down, and what was under
    pressure blowing out of the hole."""
    rng = np.random.default_rng(seed)
    n = int(1.9 * SR)
    t = seconds(n)
    mix = np.zeros(n)
    add(mix, 0, burst(rng, 90, 9000, 0.022, 0.3, 0.0002), 1.2)
    add(mix, 0, struck(rng, 190 * size, 6500, 70, 0.13, 0.0007, 0.9), 1.0)
    add(mix, 0, weight(rng, 35, 150 * size, 0.13, 0.8), 1.3)
    add(mix, 0.011, struck(rng, 520 * size, 7500, 40, 0.06, 0.0003, 0.4), 0.55)
    # The wreck going round: each knock later than the last by no rule, and weaker
    at = 0.0
    for knock, gap in enumerate((0.085, 0.105, 0.16, 0.24)):
        at += gap * rng.uniform(0.85, 1.15)
        level = 0.6 * 0.74 ** knock * rng.uniform(0.8, 1.15)
        add(mix, at, struck(rng, 240 * size * rng.uniform(0.85, 1.2), 4200, 36, 0.055, 0.0012, 0.35), level)
        add(mix, at, weight(rng, 45, 190, 0.05, 0.3), 0.9 * level)
    drag = scrape(rng, 150 * np.exp(-t / 0.5) + 25, ((430 * size, 5, 1.0), (1150 * size, 6, 0.7), (2700, 7, 0.4)), 0.8)
    mix += 0.3 * drag * ramp(t, 0.03, 0.05) * np.exp(-np.maximum(t - 0.25, 0) / 0.2)
    debris(rng, mix, 0.04, 1.35, 22, 0.3)
    blow = gas(rng, n, 700, 6500, 28, 0.5)
    mix += 0.16 * blow * ramp(t, 0.02, 0.06) * np.exp(-np.maximum(t - 0.2, 0) / 0.5)
    return done(rng, mix, 2.6, 0.3, 0.95)


def gasket_blow(seed, size):
    """A head gasket blowing: a flat bark as the joint gives, then the leak: a hard hiss that comes in uneven puffs while
    the pressure behind it lasts, spitting water, and thins away."""
    rng = np.random.default_rng(seed)
    n = int(1.5 * SR)
    t = seconds(n)
    mix = np.zeros(n)
    add(mix, 0, burst(rng, 140, 4200 * size, 0.02, 0.25), 1.0)
    add(mix, 0, weight(rng, 50, 230 * size, 0.045, 0.3), 0.8)
    add(mix, 0.002, struck(rng, 600 * size, 3600, 18, 0.018, 0.0008, 0.15), 0.3)
    # Each puff is a cylinder's pressure going out of the gap: soft-edged, and ever less of it
    puffs = np.zeros(n)
    at, level = 0.065, 1.0
    while at < 1.2:
        length = rng.uniform(0.045, 0.075)
        u = seconds(int(length * 2.5 * SR))
        add(puffs, at, ramp(u, 0, 0.007) * np.exp(-u / (length * 0.38)), level * rng.uniform(0.65, 1.1))
        at += length * rng.uniform(0.95, 1.35)
        level *= 0.89
    hold = ramp(t, 0.004, 0.02) * np.exp(-np.maximum(t - 0.1, 0) / 0.42)
    jet = gas(rng, n, 1500 * size, 8500, 55, 0.35)
    throat = unit(resonate(gas(rng, n, 300, 3000, 30, 0.4), ((720 * size, 3, 1.0), (1850 * size, 4, 0.6))))
    mix += jet * (0.42 * puffs + 0.17 * hold) + throat * (0.4 * puffs + 0.08 * hold)
    mix += 0.16 * spits(rng, n, 140, 900, 5500) * hold
    return done(rng, mix, 1.5, 0.3, 0.85)


def seize_warning(seed, size):
    """A dry piston scuffing its bore: a harsh dry scrape on each stroke, catching worse in the middle of it, with a tick
    where the piston rocks over at the end. Rough all through: no note to whistle."""
    rng = np.random.default_rng(seed)
    n = int(1.0 * SR)
    t = seconds(n)
    mix = np.zeros(n)
    modes = ((780 * size, 5, 0.8), (1630 * size, 7, 1.0), (2470 * size, 7, 0.7), (3900 * size, 4, 0.4))
    rub = scrape(rng, 330 * size * np.clip(1 + 0.12 * wander(rng, n, 7), 0.7, 1.3), modes, 0.9)
    strokes = np.zeros(n)
    at = 0.02
    for level in (0.55, 0.85, 1.0, 0.7, 0.4):
        length = rng.uniform(0.13, 0.18)
        u = seconds(int(length * SR))
        add(strokes, at, np.sin(np.pi * u / length) ** 1.6, level * rng.uniform(0.85, 1.15))
        tick = struck(rng, 1100 * size, 4800, 14, 0.012, 0.0004, 0.07)
        add(mix, at + length, tick, 0.16 * level)
        at += length * rng.uniform(1.04, 1.2)
    mix += rub * strokes
    return done(rng, mix, 1.2, 0.15, 0.7)


def piston_seize(seed, size):
    """A piston seizing: a very short, thickening grind as the metal picks up, then the whole engine stopped dead against
    it: a heavy dull clunk, the slack in the train knocking back, the load groaning off and the hot metal ticking."""
    rng = np.random.default_rng(seed)
    n = int(1.4 * SR)
    t = seconds(n)
    lock = 0.14  # short, so the clunk lands with the smoke and the cylinder stopping
    mix = np.zeros(n)
    modes = ((620 * size, 6, 0.9), (1480 * size, 10, 1.0), (2650 * size, 11, 0.7), (4100, 6, 0.3))
    # It sticks and lets go ever more slowly as it grabs
    grind = scrape(rng, 480 - 330 * ramp(t, 0.02, lock), modes, 1.0)
    mix += 0.75 * grind * (0.25 + 0.75 * ramp(t, 0, lock)) * ramp(t, 0, 0.012) * (1 - ramp(t, lock - 0.004, 0.016))
    add(mix, lock, struck(rng, 130 * size, 3400, 55, 0.09, 0.0018, 0.6), 1.0)
    add(mix, lock, weight(rng, 34, 140 * size, 0.09, 0.6), 1.35)
    add(mix, lock, burst(rng, 250, 3800, 0.014, 0.15), 0.6)
    back = lock + rng.uniform(0.055, 0.075)
    add(mix, back, struck(rng, 260 * size, 3000, 30, 0.045, 0.0012, 0.3), 0.42)
    add(mix, back, weight(rng, 45, 170, 0.04, 0.25), 0.4)
    groan = scrape(rng, 70 * np.exp(-np.maximum(t - lock, 0) / 0.35) + 14,
                   ((190 * size, 4, 1.0), (460 * size, 3.5, 0.7), (1240, 2.5, 0.25)), 0.9)
    mix += 0.26 * groan * ramp(t, back, 0.08) * np.exp(-np.maximum(t - back - 0.1, 0) / 0.22)
    for at in np.sort(rng.uniform(0.55, 1.15, 4)):
        add(mix, at, struck(rng, 2100, 6800, 8, 0.008, 0.0002, 0.05), rng.uniform(0.025, 0.065))
    return done(rng, mix, 1.9, 0.25, 0.85)


def head_warp(seed, size):
    """A cylinder head moving in the heat: a hard dead crack as the casting lets go, a creak as it settles, a second
    crack, and steam finding its way out of the joint, spitting."""
    rng = np.random.default_rng(seed)
    n = int(1.7 * SR)
    t = seconds(n)
    mix = np.zeros(n)
    for at, level in ((0.0, 1.0), (0.23 * rng.uniform(0.9, 1.15), 0.6)):
        add(mix, at, struck(rng, 460 * size, 6800, 34, 0.038, 0.00025, 0.3), level)
        add(mix, at, burst(rng, 1200, 8500, 0.005, 0.06, 0.0002), 0.75 * level)
        add(mix, at, weight(rng, 70, 320 * size, 0.035, 0.25), 0.5 * level)
    creak = scrape(rng, 55 + 30 * wander(rng, n, 5).clip(-1.2, 1.2),
                   ((340 * size, 14, 1.0), (910 * size, 12, 0.7), (2050 * size, 10, 0.3)), 0.2)
    mix += 0.22 * creak * ramp(t, 0.04, 0.04) * (1 - ramp(t, 0.5, 0.25)) * flutter(rng, n, 9, 0.5) / 2.5
    steam = ramp(t, 0.1, 0.28) * np.exp(-np.maximum(t - 0.65, 0) / 0.42)
    mix += (0.2 * gas(rng, n, 2200 * size, 9500, 45, 0.3) + 0.07 * gas(rng, n, 600, 2400, 25, 0.5)
            + 0.15 * spits(rng, n, 190, 1300, 6500)) * steam
    return done(rng, mix, 1.5, 0.3, 0.8)


def turbo_fail(seed, size):
    """A turbocharger failing: a crunch as the wheel touches its housing and sheds blades, the wreck of it dragging round,
    rough, slower and slower, and the boost in the pipes sighing away."""
    rng = np.random.default_rng(seed)
    n = int(1.7 * SR)
    t = seconds(n)
    mix = np.zeros(n)
    add(mix, 0, burst(rng, 350, 8000, 0.016, 0.2, 0.0003), 0.9)
    add(mix, 0, struck(rng, 700 * size, 7600, 44, 0.05, 0.0005, 0.35), 0.85)
    add(mix, 0, weight(rng, 70, 300, 0.04, 0.3), 0.6)
    for at in np.sort(rng.uniform(0.008, 0.11, 9)):
        add(mix, at, struck(rng, rng.uniform(1300, 2900) * size, 8200, 10, 0.012, 0.0002, 0.08), rng.uniform(0.2, 0.6))
    # The shaft's turning is heard only as the roughness of the drag, never as a tone
    turning = 20 + 120 * np.exp(-t / 0.45)
    rubbing = np.abs(np.sin(np.pi * np.cumsum(turning * np.clip(1 + 0.1 * wander(rng, n, 25), 0.7, 1.3)) / SR)) ** 3
    drag = scrape(rng, 1100 * np.exp(-t / 0.6) + 70, ((950 * size, 3, 0.8), (2100 * size, 4, 1.0), (3600 * size, 4, 0.7)), 1.0)
    mix += 0.7 * drag * (0.2 + rubbing) * ramp(t, 0.02, 0.03) * np.exp(-np.maximum(t - 0.3, 0) / 0.4)
    # The air loses its top as the wheel slows: from bright to dull, without a pitch to follow down
    bright, dull = gas(rng, n, 1800, 8000, 30, 0.3), gas(rng, n, 250, 1700, 18, 0.35)
    share = np.exp(-t / 0.28)
    mix += 0.24 * (bright * share + dull * (1 - share)) * ramp(t, 0.01, 0.04) * np.exp(-np.maximum(t - 0.2, 0) / 0.45)
    debris(rng, mix, 0.12, 1.0, 10, 0.2)
    return done(rng, mix, 1.8, 0.3, 0.82)


def apex_fail(seed, size):
    """A rotor's apex seal letting go: a sharp crack, the broken strip caught and flung about the chamber in a short ragged
    rattle until it jams, and compression chuffing past the tip that no longer seals as the rotor runs down."""
    rng = np.random.default_rng(seed)
    n = int(1.6 * SR)
    t = seconds(n)
    mix = np.zeros(n)
    add(mix, 0, burst(rng, 1400, 9500, 0.006, 0.07, 0.0002), 0.9)
    add(mix, 0, struck(rng, 820 * size, 7200, 30, 0.035, 0.0003, 0.25), 0.85)
    add(mix, 0, weight(rng, 55, 210 * size, 0.06, 0.35), 0.75)
    # In knots: the strip chatters where it catches, then flies free for a moment
    at, level = 0.04, 0.75
    while at < 0.72:
        hit_at = at
        for _ in range(rng.integers(1, 5)):
            hit = struck(rng, rng.uniform(650, 1500) * size, 5600, 16, 0.014, 0.0005, 0.1)
            add(mix, hit_at, hit, level * rng.uniform(0.25, 1))
            hit_at += rng.uniform(0.006, 0.019)
        add(mix, at, weight(rng, 80, 300, 0.018, 0.1), 0.4 * level)
        at = hit_at + rng.uniform(0.015, 0.075) * (1 + at)
        level *= 0.91
    jam = hit_at + 0.05
    add(mix, jam, struck(rng, 300 * size, 3400, 28, 0.035, 0.0013, 0.25), 0.55)
    add(mix, jam, weight(rng, 45, 160, 0.05, 0.25), 0.55)
    chuffs = np.zeros(n)
    at, gap, level = 0.08, 0.075, 1.0
    while at < 1.35:
        u = seconds(int(0.17 * SR))
        add(chuffs, at, ramp(u, 0, 0.012) * np.exp(-u / 0.035), level * rng.uniform(0.6, 1.15))
        at += gap * rng.uniform(0.8, 1.2)
        gap += 0.008
        level *= 0.88
    breath = unit(resonate(gas(rng, n, 350, 4200, 30, 0.4), ((560 * size, 2.5, 1.0), (1500 * size, 3, 0.6))))
    mix += 0.5 * breath * chuffs + 0.09 * gas(rng, n, 1600, 6500, 40, 0.4) * chuffs
    return done(rng, mix, 1.8, 0.25, 0.85)


# (take, seed, size): a size under one is a bigger, lower part
TAKES = {
    rod_thrown: ((1, 131, 1.0), (2, 137, 0.86)),
    gasket_blow: ((1, 139, 1.0), (2, 149, 0.86)),
    seize_warning: ((1, 163, 1.0), (2, 167, 0.91)),
    piston_seize: ((1, 151, 1.0), (2, 157, 0.87)),
    apex_fail: ((1, 163, 1.0), (2, 167, 0.87)),
    head_warp: ((1, 173, 1.0), (2, 179, 0.87)),
    turbo_fail: ((1, 181, 1.0), (2, 191, 0.87)),
}

SOUNDS = {"%s%d" % (make.__name__, take): (lambda make=make, seed=seed, size=size: make(seed, size))
          for make, takes in TAKES.items() for take, seed, size in takes}
