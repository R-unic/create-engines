"""The forced-induction sounds, for generate_sounds.py: the turbo's whistle, its blow-off valve and the supercharger's
whine.

The two loops are also made live inside an engine's note (BoostWhine.java), to the same recipe: the same tones (whole
hertz, so a one-second loop has no seam), the same bands of noise and the same weights. Change them in both places.
NoteDeliveryTest and BoostSoundsTest hold the recordings and the live voices to each other.
"""
import numpy as np

from sound_dsp import SR, band, finish, highpass, lowpass

TAU = 2 * np.pi


def noise_band(rng, n, centre, width, passes=2):
    """A band of noise that repeats every n samples, at an RMS of 1, with the spread of `passes` band-pass filters in a
    row (each `width` Hz wide at half power), which is what the live voice runs."""
    freqs = np.maximum(np.fft.rfftfreq(n, 1 / SR), 1e-3)
    shape = (1 + (centre / width * (freqs / centre - centre / freqs)) ** 2) ** (-passes / 2)
    spectrum = shape * np.exp(1j * rng.uniform(0, TAU, len(freqs)))
    spectrum[0] = 0
    out = np.fft.irfft(spectrum, n)
    return out / np.sqrt(np.mean(out ** 2))


def tone(t, hz, turns=0.0):
    return np.sin(TAU * (hz * t + turns))


def loop(mix, drive):
    """Eased into a soft clip, then to the level the live voice plays at: a peak of 0.8."""
    return finish(np.tanh(drive * mix), 0.8)


def turbo_loop():
    """A turbo on song. What is heard of a real one is a breathy whistle: turbulence singing at one pitch, with a steadier
    core in the middle of it, never quite still. Under it a hollow note an octave down; over it two bright overtones and
    a wide rush of air, which are its high end. At the game's pitch 1 the whistle sits at 2 kHz; the wheel running up
    bends it from under half that to above."""
    rng = np.random.default_rng(3)
    n = SR
    t = np.arange(n) / SR
    # The core drifts a little, slowly and unevenly, in turns
    drift = 0.11 * tone(t, 3, 0.07) + 0.05 * tone(t, 7, 0.41) + 0.025 * tone(t, 13, 0.83)
    core = (tone(t, 2000, drift) + 0.35 * tone(t, 4000, 2 * drift + 0.2) + 0.11 * tone(t, 6000, 0.3)
            + 0.11 * tone(t, 1000, 0.5 * drift + 0.6))
    breath = noise_band(rng, n, 2000, 80)
    under = noise_band(rng, n, 1000, 110)
    # One pass only: its slopes are gentle, so it reaches right to the top
    air = noise_band(rng, n, 4873, 7000, 1) * (0.8 + 0.2 * tone(t, 38, 0.25))
    whoosh = noise_band(rng, n, 520, 420)
    mix = 0.5 * core + 0.21 * breath + 0.075 * under + 0.168 * air + 0.085 * whoosh
    return loop(mix, 0.78)


# The blower's lobe tone and its soft overtones, and the growl: copies of them 48 Hz either side
LOBES = ((520, 1.0, 0.0), (1040, 0.28, 0.31), (1560, 0.08, 0.67))
GROWL = ((472, 0.14, 0.55), (568, 0.11, 0.23), (992, 0.05, 0.79), (1088, 0.04, 0.45))
# The drive gears' mesh, which is no multiple of the lobe tone, kept far under it
MESH = ((1361, 0.05, 0.9),)


def supercharger_loop():
    """A roots blower, made to be lived with: it plays as long as its shaft turns. A rounded tone at the rate its lobes
    pass, with little above it, tied exactly to the shaft; under it a low thrum, and round it a soft whir of air and a
    trace of the gears. At the game's pitch 1 the whine sits at 520 Hz."""
    rng = np.random.default_rng(5)
    n = SR
    t = np.arange(n) / SR
    wander = 0.035 * tone(t, 2, 0.19) + 0.018 * tone(t, 5, 0.62)
    whine = sum(level * tone(t, hz, hz / 520 * wander + turns) for hz, level, turns in LOBES + GROWL + MESH)
    thrum = tone(t, 87, 0.11) + 0.6 * tone(t, 174, 0.62) + 0.3 * tone(t, 261, 0.35)
    sing = noise_band(rng, n, 520, 90)
    overtone = noise_band(rng, n, 1040, 170)
    whir = noise_band(rng, n, 1500, 1500)
    mix = 0.25 * whine + 0.17 * thrum + 0.07 * sing + 0.04 * overtone + 0.1 * whir
    return loop(mix, 1.35)


# Each take: its noise, how fast the flutter starts and where it slows to (swells a second), and when it sets in
TAKES = (
    dict(seed=19, quick=21.0, slow=12.0, onset=0.07),
    dict(seed=23, quick=25.0, slow=14.0, onset=0.06),
    dict(seed=41, quick=18.0, slow=10.5, onset=0.09),
)
# The blow-off is cut off below this, so that its body never turns into a thud
AIR_FLOOR_HZ = 220
# How much of the air still gets out between one swell of the flutter and the next
BETWEEN = 0.24
# The whine of the wheel running down, in hertz: where it starts, where it is falling to and how fast (seconds)
WHINE_FROM = 3300
WHINE_TO = 1500
WHINE_FALL = 0.45
# How loud the whine is; it is meant to be a little one, under the air
WHINE = 0.062


def turbo_blowoff(seed, quick, slow, onset):
    """The blow-off valve letting a charge go, which is all moving air, a good pipeful of it. It opens in a rush, and as
    the compressor, suddenly unloaded, surges, the one stream of air starts to flutter, "pshh-tu-tu-tu", slowing as the
    wheel runs down. The flutter is the same rush swelling and easing, soft at both ends and never shut off, so that it
    does not sound like things striking each other. Under the air the wheel whines, falling as it runs down."""
    rng = np.random.default_rng(seed)
    n = int(1.0 * SR)
    t = np.arange(n) / SR
    rise = np.sin(0.5 * np.pi * np.clip(t / 0.012, 0, 1)) ** 2

    # One turn of the flutter for each swell, counted from when it sets in
    rate = slow + (quick - slow) * np.exp(-np.maximum(t - onset, 0) / 0.3)
    cycle = np.cumsum(np.where(t >= onset, rate, 0)) / SR % 1
    # A raised cosine, leant forward so that each swell comes a little quicker than it goes
    swell = (0.5 - 0.5 * np.cos(2 * np.pi * cycle ** 0.8)) ** 1.6
    # How far the stream has started to flutter, from not at all to wholly
    broken = np.clip((t - onset) / 0.1, 0, 1)
    stream = 1 + broken * (BETWEEN + 1.5 * swell - 1)

    def unit(x):
        return x / np.max(np.abs(x))

    pressure = 0.6 * np.exp(-t / 0.3) + 0.4 * np.exp(-t / 0.09)
    lasting = 0.45 * pressure + 0.55 * np.exp(-t / 0.36)
    # The top of the hiss; the air that carries the flutter; its body, air too, low in the middle of the range; and the
    # hollow voice of the pipe it leaves by
    hiss = unit(band(rng.standard_normal(n), 2600, 9000, 2)) * pressure
    air = unit(band(rng.standard_normal(n), 1100, 4200, 2)) * lasting
    body = unit(band(rng.standard_normal(n), 380, 1500, 2)) * lasting
    pipe = unit(band(rng.standard_normal(n), 560, 820, 1)) * lasting

    # The wheel's whine: it sags a touch and swells with each surge of the flutter, and is never quite steady
    waver = lowpass(rng.standard_normal(n), 30, 2)
    wheel = (WHINE_TO + (WHINE_FROM - WHINE_TO) * np.exp(-t / WHINE_FALL)) * (1 + 0.006 * waver / np.std(waver))
    wheel *= 1 + 0.03 * broken * (swell - 0.4)
    turns = np.cumsum(wheel) / SR
    whine = np.sin(TAU * turns) + 0.18 * np.sin(TAU * 2 * turns + 0.9)
    whine *= np.clip((t - 0.015) / 0.05, 0, 1) * np.exp(-t / 0.38) * (1 + broken * (0.9 * swell - 0.45))

    mix = (0.34 * hiss + 0.62 * air + 0.82 * body + 0.34 * pipe) * stream * rise + WHINE * whine
    mix = lowpass(highpass(np.tanh(1.1 * mix), AIR_FLOOR_HZ, 4), 10000, 2)
    mix *= np.clip((n - np.arange(n)) / (0.12 * SR), 0, 1)
    return finish(mix)


SOUNDS = {
    "turbo_whistle": turbo_loop,
    "supercharger_whine": supercharger_loop,
    **{"turbo_blowoff" + (str(i + 1) if i else ""): (lambda take=take: turbo_blowoff(**take))
       for i, take in enumerate(TAKES)},
}
