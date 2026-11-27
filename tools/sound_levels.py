"""The level pass over every recording that plays once (step 3 of the sound plan), for generate_sounds.py: no DC offset,
a short fade at each end, a loudness of its kind's own and a ceiling on its peaks. Loops are left as they were made.

The loudness is of the loudest 400 ms (sound_audit.momentary_lufs), of the recording itself, before the volume the game
plays it at. For scale, the engine note rendered at idle reads about -16.5.
"""
import re

import numpy as np
from scipy import signal

import sound_audit
from sound_dsp import SR

LOOPS = {"starter_whir", "turbo_whistle", "supercharger_whine", "engine_clatter", "bearing_squeal", "intake_roar"}

TARGETS = {
    # Heard in place of, or over, the note
    "exhaust_pop": -19.0, "exhaust_spit": -23.0, "cylinder_fire": -13.0,
    "engine_catch": -16.0, "engine_catch_hard": -18.0, "engine_stall": -17.5, "engine_stall_lugged": -17.0,
    "engine_stall_starved": -19.0, "engine_settle": -19.0,
    # Failures and warnings, above the note. The warning is at the louder of what its takes were
    "rod_thrown": -8.5, "piston_seize": -10.0, "turbo_fail": -10.0, "apex_fail": -11.0, "gasket_blow": -11.0,
    "head_warp": -11.0, "seize_warning": -9.3,
    # The rest, each at about what it was
    "turbo_blowoff": -15.5, "preignition_knock": -18.0, "metal_tick": -21.0, "soot_catch": -21.0,
    "flare_whoosh": -15.5,
}

# A little under full scale: the encoder overshoots
CEILING = 0.9
KNEE = 0.6
# Longer than the 2 ms SoundAssetsTest asks for
FADE = 0.003


def kind_of(name):
    return re.sub(r"\d+$", "", name)


def loudness(samples):
    x = signal.resample_poly(samples, sound_audit.RATE // 100, SR // 100)
    # A sound shorter than the 400 ms block is measured with silence after it
    x = np.concatenate([x, np.zeros(max(0, int(0.45 * sound_audit.RATE) - len(x)))])
    return sound_audit.momentary_lufs(x)


def limit(x):
    """Unchanged up to the knee, then eased toward the ceiling."""
    over = np.maximum(np.abs(x) - KNEE, 0)
    eased = KNEE + (CEILING - KNEE) * np.tanh(over / (CEILING - KNEE))
    return np.where(over > 0, np.sign(x) * eased, x)


def edges(x):
    """Silence a fade long before the sound, so the fade does not take the edge off its first crack; faded at both ends."""
    n = int(FADE * SR)
    x = np.concatenate([np.zeros(n), x])
    ramp = np.sin(np.linspace(0, np.pi / 2, n)) ** 2
    x[:n] *= ramp
    x[-n:] *= ramp[::-1]
    return x


def level(name, samples):
    """`samples` as they are to be written; a loop comes back untouched."""
    if name in LOOPS:
        return samples
    target = TARGETS[kind_of(name)]
    x = edges(samples - np.mean(samples))
    gain = 1.0
    # Limiting takes loudness off, so the gain is found by trying it
    for _ in range(12):
        gain *= 10 ** ((target - loudness(limit(x * gain))) / 20)
    out = limit(x * gain)
    got = loudness(out)
    if abs(got - target) > 0.5:
        raise ValueError(f"{name} cannot be brought to {target} LUFS under its peak ceiling: it reaches {got:.1f}")
    return out
