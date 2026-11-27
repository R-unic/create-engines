package com.createengines.content;

/**
 * Keeps the note's filters out of the subnormal numbers. A filter fed nothing decays toward zero for ever, and once what
 * it holds is too small for a normal float or double the processor takes many times longer over every sum with it; a float
 * one can round back to the same subnormal and never leave. Java cannot ask the processor to treat them as zero, so each
 * filter drops what is far too small to hear itself. Pure Java.
 */
final class Denormals {

	/** Anything smaller than this is nothing: hundreds of decibels under hearing, and far above the subnormals. */
	static final float FLOOR = 1e-18f;

	static float flush(float value) {
		return Math.abs(value) < FLOOR ? 0 : value;
	}

	private Denormals() {
	}
}
