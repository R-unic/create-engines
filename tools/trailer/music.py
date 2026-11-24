"""Synthesises an original backing track for the trailer, so there is music with nothing to license.

    python tools/trailer/music.py OUT.wav SECONDS [SEED]

A slow build over a four-chord loop: a warm pad, a plucked bass, then a pulse and a high arpeggio come in as the trailer
goes on. It is meant as a placeholder that sounds like a trailer; a track the author prefers goes in tools/trailer/music/
(post.py uses the first file there) or is passed to post.py with --music.
"""
import sys
import wave

import numpy as np

SR = 44100
BPM = 80  # a bar is three seconds: post.py cuts the trailer on whole bars (its BAR)
BEAT = 60.0 / BPM
# Am, F, C, G, as MIDI notes: a root for the bass and a triad for the pad
CHORDS = [(33, (57, 60, 64)), (29, (53, 57, 60)), (36, (55, 60, 64)), (31, (55, 59, 62))]


def hz(midi):
    return 440.0 * 2 ** ((midi - 69) / 12.0)


def tone(freq, seconds, harmonics, rng):
    """A soft saw-ish voice: a few harmonics, a little detune for width."""
    t = np.arange(int(seconds * SR)) / SR
    out = np.zeros_like(t)
    for detune in (-0.004, 0.0, 0.004):
        f = freq * (1 + detune)
        phase = rng.uniform(0, 2 * np.pi)
        for h in range(1, harmonics + 1):
            out += np.sin(2 * np.pi * f * h * t + phase * h) / h
    return out / 3.0


def reverb(x, rng, seconds=2.2, wet=0.28):
    """Convolves with decaying noise, which is a cheap and decent room."""
    n = int(seconds * SR)
    ir = rng.standard_normal(n) * np.exp(-np.arange(n) / (0.45 * SR))
    size = 1
    while size < len(x) + n:
        size *= 2
    wetted = np.fft.irfft(np.fft.rfft(x, size) * np.fft.rfft(ir, size), size)[: len(x)]
    wetted /= max(1e-9, np.abs(wetted).max())
    return x * (1 - wet) + wetted * wet * np.abs(x).max()


def render(seconds, seed=7):
    rng = np.random.default_rng(seed)
    total = int(seconds * SR)
    left = np.zeros(total)
    right = np.zeros(total)

    def mix(start, signal, gain, pan=0.0):
        """Adds a signal at a sample position; pan runs from -1 (left) to 1 (right)."""
        end = min(total, start + len(signal))
        if end <= start:
            return
        s = signal[: end - start] * gain
        left[start:end] += s * (1 - max(0.0, pan))
        right[start:end] += s * (1 + min(0.0, pan))

    bar = 4 * BEAT
    bars = int(seconds / bar) + 2
    for b in range(bars):
        root, triad = CHORDS[b % len(CHORDS)]
        start = int(b * bar * SR)
        progress = min(1.0, b * bar / seconds)

        # The pad swells into each bar and holds
        length = bar + 1.2
        n = int(length * SR)
        env = np.minimum(1, np.arange(n) / (0.9 * SR)) * np.minimum(1, (n - np.arange(n)) / (1.0 * SR))
        for note in triad:
            mix(start, tone(hz(note), length, 6, rng) * env, 0.11, pan=rng.uniform(-0.5, 0.5))

        # The bass plucks on every beat, a little softer on the off ones
        for beat in range(4):
            t = np.arange(int(BEAT * 1.1 * SR)) / SR
            pluck = np.sin(2 * np.pi * hz(root) * t) * np.exp(-t * 3.2)
            pluck += 0.35 * np.sin(2 * np.pi * hz(root + 12) * t) * np.exp(-t * 5)
            mix(start + int(beat * BEAT * SR), pluck, 0.30 if beat % 2 == 0 else 0.22)

        # From a fifth of the way in, a kick on every beat: the engine turning over
        if progress > 0.2:
            for beat in range(4):
                at = start + int(beat * BEAT * SR)
                t = np.arange(int(0.28 * SR)) / SR
                sweep = 140 * np.exp(-t * 22) + 45
                kick = np.sin(2 * np.pi * np.cumsum(sweep) / SR) * np.exp(-t * 9)
                mix(at, kick, 0.55 * min(1.0, (progress - 0.2) / 0.15))

        # From the middle, an eighth-note arpeggio and an off-beat hat
        if progress > 0.38:
            gain = 0.16 * min(1.0, (progress - 0.38) / 0.12)
            for step in range(8):
                note = triad[step % 3] + (12 if step % 2 else 24)
                at = start + int(step * BEAT / 2 * SR)
                t = np.arange(int(0.5 * SR)) / SR
                tri = (2 / np.pi) * np.arcsin(np.sin(2 * np.pi * hz(note) * t)) * np.exp(-t * 7)
                mix(at, tri, gain, pan=0.4 if step % 2 else -0.4)
        if progress > 0.5:
            for beat in range(4):
                at = start + int((beat + 0.5) * BEAT * SR)
                t = np.arange(int(0.07 * SR)) / SR
                hat = rng.standard_normal(len(t)) * np.exp(-t * 60)
                hat = np.diff(hat, prepend=0.0)
                mix(at, hat, 0.10)

    stereo = np.stack([reverb(left, rng), reverb(right, rng)], axis=1)
    peak = np.abs(stereo).max()
    stereo = stereo / peak * 0.7
    fade_in = np.minimum(1, np.arange(total) / (1.5 * SR))
    fade_out = np.minimum(1, (total - np.arange(total)) / (3.0 * SR))
    return stereo * (fade_in * fade_out)[:, None]


def write(path, audio):
    data = (np.clip(audio, -1, 1) * 32767).astype("<i2")
    with wave.open(path, "wb") as f:
        f.setnchannels(2)
        f.setsampwidth(2)
        f.setframerate(SR)
        f.writeframes(data.tobytes())


if __name__ == "__main__":
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    write(sys.argv[1], render(float(sys.argv[2]), int(sys.argv[3]) if len(sys.argv) > 3 else 7))
