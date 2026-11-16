package com.createengines.engine.sim;

/**
 * What a Soot Trap does, in plain numbers. Rich cycles leave fuel unburned and a share of it leaves as soot
 * ({@link CylinderModel#takeSoot()}); a trap in the exhaust run catches that, and every {@link #MB_PER_SOOT} mB of it
 * makes one lump of soot, which waits in the trap until it is taken out. A full trap lets the rest through. Ethanol
 * burns clean and makes none.
 * <p>
 * Pure arithmetic with no Minecraft types, so it is unit tested without a world. Every input is treated as hostile:
 * NaN, infinity and negatives count as nothing, so a damaged save or an odd fuel cannot corrupt a trap.
 */
public final class SootCollector {

	/** Unburned fuel that makes one lump of soot, mB. */
	public static final double MB_PER_SOOT = 50;
	/** Most lumps one trap holds. */
	public static final int CAPACITY = 64;
	/** A trap the exhaust can go round catches half of what passes it. */
	public static final float BYPASSED = 0.5f;

	private double progress;
	private int stored;

	/** Soot, in mB of unburned fuel, one cycle makes: what was injected, the share that went unburned, the fuel's own factor. */
	public static double sootFrom(double injectedMb, double unburnedShare, double sootFactor) {
		double soot = injectedMb * unburnedShare * sootFactor;
		return Double.isFinite(soot) && soot > 0 ? soot : 0;
	}

	/** Share of a run's soot its traps catch: all of it behind a trap every path goes through, half behind one it can bypass. */
	public static float catchShare(boolean series, boolean bypassed) {
		return series ? 1 : bypassed ? BYPASSED : 0;
	}

	/**
	 * What one trap is given of an engine's soot this round.
	 *
	 * @param total      all the engine's soot, mB
	 * @param runHeads   cylinder heads on the run the trap is in
	 * @param totalHeads cylinder heads on all of the engine's exhaust runs
	 * @param catchShare see {@link #catchShare}
	 * @param traps      traps in that run, which share what the run catches
	 */
	public static double share(double total, int runHeads, int totalHeads, float catchShare, int traps) {
		if (!(total > 0) || !Double.isFinite(total) || runHeads <= 0 || totalHeads <= 0 || traps <= 0)
			return 0;
		double share = total * Math.min(1.0, (double) runHeads / totalHeads) * Math.clamp(catchShare, 0f, 1f) / traps;
		return Double.isFinite(share) ? share : 0;
	}

	/** Lumps waiting in the trap. */
	public int stored() {
		return stored;
	}

	/** Unburned fuel gathered towards the next lump, mB. */
	public double progress() {
		return progress;
	}

	public boolean full() {
		return stored >= CAPACITY;
	}

	/** Catches {@code mb} of soot; returns how many whole lumps that added. Once full, anything more is lost. */
	public int add(double mb) {
		if (!(mb > 0) || !Double.isFinite(mb) || full())
			return 0;
		progress += mb;
		int made = (int) Math.min(CAPACITY - stored, Math.floor(progress / MB_PER_SOOT));
		stored += made;
		progress -= made * MB_PER_SOOT;
		if (full() || progress < 0)
			progress = 0;
		return made;
	}

	/** Takes up to {@code max} lumps out; returns how many came out. */
	public int take(int max) {
		int taken = Math.clamp(max, 0, stored);
		stored -= taken;
		return taken;
	}

	/** Restores what a save held, clamped so a damaged save cannot overfill or poison the trap. */
	public void restore(int storedLumps, double gathered) {
		stored = Math.clamp(storedLumps, 0, CAPACITY);
		progress = Double.isFinite(gathered) ? Math.clamp(gathered, 0, MB_PER_SOOT) : 0;
		if (full())
			progress = 0;
	}
}
