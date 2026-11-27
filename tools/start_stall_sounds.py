"""An engine catching and an engine dying, for generate_sounds.py.

An engine with a note plays these itself, from its own speed and firing order (EngineNoteGesture.java, NoteBangs.java).
These recordings stand in for one without, and are made as the note is: each beat a grain struck into an exhaust pipe
(engine_pipe.py) with its crack beside it and the thump under it. As an engine dies there are chuffs too, a cylinder
pushing its air out unlit. The beats are unevenly spaced, as an engine that is not yet, or no longer, running is.
"""
import numpy as np

import engine_pipe as ep
from sound_dsp import finish

# EngineNoteSynth.java's weights, at the scale it plays at
SCALE = 0.07
BODY_WEIGHT = 110
CRACK_WEIGHT = 100
THUMP_WEIGHT = 0.35
# NoteBangs.java's
ROCK_BODY = 170
SIGH = 3
ROCKS = ((0.0, 0.5), (0.15, 0.26), (0.25, 0.11))


def thump(n, pitch):
    """The weight under a beat: a sine dropping from about 150 Hz to 46 Hz, gone in about a tenth of a second."""
    t = np.arange(n) / ep.SR
    note = (46 + 110 * np.exp(-t / 0.02)) * pitch
    swell = (1 - np.exp(-t / 0.004)) * np.exp(-t / 0.036)
    return np.sin(2 * np.pi * np.cumsum(note) / ep.SR) * swell


class Engine:
    """The one pipe an engine's beats all go down, with what goes past it."""

    def __init__(self, rng, seconds, pitch):
        n = int(seconds * ep.SR)
        self.rng = rng
        self.pitch = pitch
        self.into = np.zeros(n)
        self.past = np.zeros(n)

    def beat(self, at, strength, force):
        """One firing, `strength` loud. `force`, 0 to 1, is how well the charge burned: a hard one cracks."""
        load = 2 if force > 0.75 else 1 if force > 0.4 else 0
        ep.strike(self.into, at, ep.body(self.rng, 1, load), strength * BODY_WEIGHT)
        ep.strike(self.past, at, ep.crack(self.rng, 1), strength * force * CRACK_WEIGHT)
        n = len(self.past) - int(at * ep.SR)
        ep.strike(self.past, at, thump(n, self.pitch), strength * THUMP_WEIGHT * 14)

    def chuff(self, at, strength):
        """A cylinder that did not light: its air shoved out, with no crack and no thump, and a breath after it."""
        ep.strike(self.into, at, ep.body(self.rng, 1, 0), strength * 0.4 * BODY_WEIGHT)
        n = int(0.16 * ep.SR)
        t = np.arange(n) / ep.SR
        swell = np.minimum(t / 0.035, 1) ** 1.5 * np.exp(-np.maximum(t - 0.035, 0) / 0.045)
        air = ep.one_pole(self.rng.uniform(-1, 1, n), 1100)
        ep.strike(self.into, at, (air - ep.one_pole(air, 220)) * swell, strength * 5)

    def rest(self, stop, slam, sigh):
        """The stopped crank rocking back off compression, and what is left in the pipe sighing out."""
        for after, force in ROCKS:
            ep.strike(self.into, stop + after, ep.body(self.rng, 2, 0), slam * force * ROCK_BODY)
        n = len(self.into) - int(stop * ep.SR)
        t = np.arange(n) / ep.SR
        breath = ep.one_pole(self.rng.uniform(-1, 1, n), 900)
        ep.strike(self.into, stop, breath * np.minimum(t / 0.1, 1) * np.exp(-np.maximum(t - 0.25, 0) / 0.22),
                  sigh * SIGH)

    def heard(self):
        mix = ep.to_sound_rate(np.tanh((ep.pipe(self.into, ep.run_note(0.5, 0.5) * self.pitch) + self.past) * SCALE))
        mix *= np.clip((len(mix) - np.arange(len(mix))) / (0.2 * 44100), 0, 1)
        return finish(mix, 0.9)


def engine_catch(seed, pitch, coughs, run, length=1.35):
    """An engine catching on the starter: a cough or two with a stumble after, then it takes hold, the firings crowding
    together as it flares up and falling away as the engine's own note takes over. `coughs` lists the tentative firings
    as (seconds, force); `run` is (seconds it takes hold, first gap, how much shorter each gap is than the last), and
    `length` how long it all lasts, the flare ending 0.3 s before the end."""
    rng = np.random.default_rng(seed)
    engine = Engine(rng, length, pitch)
    end = length - 0.3
    for at, force in coughs:
        engine.beat(at, 0.45 + 0.45 * force, force)
    at, gap, shrink = run
    start = at
    while at < end:
        # How far the flare has got: up quickly, then sinking back under the note that is fading in
        done = (at - start) / (end - start)
        level = (0.65 + 0.35 * min(done / 0.35, 1)) * (1 - 0.7 * max(done - 0.4, 0) / 0.6)
        force = min(0.55 + 0.7 * done, 0.95) * rng.uniform(0.85, 1.05)
        engine.beat(at, level * rng.uniform(0.85, 1.0), force)
        at += gap * rng.uniform(0.9, 1.1)
        gap = max(gap * shrink, 0.052)
    return engine.heard()


def engine_stall(seed, pitch, fires, chuffs, stop, slam=1.0, sigh_level=0.11):
    """An engine dying: its last firings coming further apart and weaker, a cylinder or two that do not light, then the
    crank fetching up against compression and rocking back, and what is left in the exhaust sighing out. `fires` lists
    the last firings as (seconds, force), `chuffs` the unlit ones as (seconds, loudness), `stop` when the crank stops.
    `slam` is how hard it fetches up, and `sigh_level` how much is left in the exhaust to sigh out."""
    rng = np.random.default_rng(seed)
    engine = Engine(rng, stop + 0.85, pitch)
    for at, force in fires:
        engine.beat(at, 0.3 + 0.7 * force, force)
    for at, level in chuffs:
        engine.chuff(at, level)
    engine.rest(stop, slam, sigh_level)
    return engine.heard()


# Three takes of each, so an engine does not start and die the same way every time. StartStallTake picks which set
# plays from how the engine got there
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
    # Cold, or long on the starter: coughs that come to nothing, then a slow, ragged taking hold
    "engine_catch_hard": lambda: engine_catch(
        241, 0.94, [(0.0, 0.3), (0.34, 0.5), (0.47, 0.2), (0.86, 0.65), (1.0, 0.3)], (1.28, 0.19, 0.87), 2.25),
    "engine_catch_hard2": lambda: engine_catch(
        251, 0.9, [(0.0, 0.55), (0.16, 0.2), (0.61, 0.35), (1.05, 0.75)], (1.42, 0.17, 0.86), 2.35),
    "engine_catch_hard3": lambda: engine_catch(
        257, 0.97, [(0.0, 0.25), (0.29, 0.25), (0.55, 0.6), (0.68, 0.45), (1.12, 0.2), (1.3, 0.8)],
        (1.58, 0.16, 0.85), 2.5),
    # Dragged down by the load: every firing hard and further from the last, then stopped dead
    "engine_stall_lugged": lambda: engine_stall(
        263, 0.84, [(0.0, 0.8), (0.14, 0.9), (0.32, 0.95), (0.56, 1.0), (0.9, 0.85)], [], 1.12, 1.9, 0.05),
    "engine_stall_lugged2": lambda: engine_stall(
        269, 0.8, [(0.0, 0.85), (0.17, 0.95), (0.4, 1.0), (0.74, 0.9)], [], 1.0, 2.1, 0.05),
    "engine_stall_lugged3": lambda: engine_stall(
        271, 0.88, [(0.0, 0.75), (0.12, 0.85), (0.27, 0.9), (0.46, 1.0), (0.72, 0.95), (1.08, 0.7)], [], 1.3,
        1.8, 0.05),
    # Out of fuel: weak firings among more and more misses, one last hopeful one, and a soft stop
    "engine_stall_starved": lambda: engine_stall(
        277, 1.08, [(0.0, 0.6), (0.13, 0.25), (0.41, 0.5), (0.52, 0.15), (1.04, 0.45)],
        [(0.27, 0.3), (0.67, 0.28), (0.84, 0.24), (1.22, 0.22), (1.43, 0.15)], 1.62, 0.55, 0.07),
    "engine_stall_starved2": lambda: engine_stall(
        281, 1.12, [(0.0, 0.5), (0.25, 0.55), (0.36, 0.2), (0.9, 0.6)],
        [(0.12, 0.28), (0.52, 0.3), (0.71, 0.22), (1.1, 0.24), (1.32, 0.16)], 1.5, 0.5, 0.07),
    "engine_stall_starved3": lambda: engine_stall(
        283, 1.04, [(0.0, 0.65), (0.11, 0.35), (0.24, 0.2), (0.66, 0.5), (1.31, 0.3)],
        [(0.43, 0.3), (0.88, 0.26), (1.09, 0.24), (1.54, 0.14)], 1.76, 0.6, 0.07),
    # Only the crank coming to rest, for a note lost part way through a stall it was playing
    "engine_settle": lambda: engine_stall(293, 1.0, [], [], 0.1),
    "engine_settle2": lambda: engine_stall(307, 0.94, [], [], 0.12),
    "engine_settle3": lambda: engine_stall(311, 1.06, [], [], 0.1),
}
