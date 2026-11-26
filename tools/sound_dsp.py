"""The sample rate and the few filters every synthesized sound uses."""
import numpy as np
from scipy import signal

SR = 44100


def band(noise, low, high, order=3):
    sos = signal.butter(order, [low, high], btype="bandpass", fs=SR, output="sos")
    return signal.sosfilt(sos, noise)


def lowpass(noise, cutoff, order=3):
    return signal.sosfilt(signal.butter(order, cutoff, btype="lowpass", fs=SR, output="sos"), noise)


def highpass(noise, cutoff, order=2):
    return signal.sosfilt(signal.butter(order, cutoff, btype="highpass", fs=SR, output="sos"), noise)


def finish(mix, peak=0.9):
    return mix / np.max(np.abs(mix)) * peak
