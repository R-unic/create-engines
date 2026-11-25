package com.createengines.engine.sim;

/**
 * How a battery wears in the opt-in wear mode (see {@link Wear}): deep discharge and drawing on it in the cold slowly reduce what it
 * holds. Plain numbers with no Minecraft types, so it is unit tested; {@code BatteryBlockEntity} applies it.
 * <p>
 * Wear is a number from 0 (new) to 1 (spent), the same scale as every other worn part, so the readouts can fold it in with them. A worn
 * battery holds less and <em>never stops working</em>: at wear 1 it still holds {@code 1 - MAX_FADE} of what it did new. The charge it
 * had is kept up to what it now holds, no more. With the mode off none of this is called and a battery holds exactly what its tier says.
 * <p>
 * Each tier wears for its own reasons, because each is for something different:
 * <ul>
 * <li>the <b>plain battery</b> is the all-rounder: a middling life, hurt by deep discharge and by the cold;</li>
 * <li>the <b>dry cell</b> is made for the cold, so the cold does not hurt it, but it is cheap: it has the shortest life and a deep discharge
 * hurts it most;</li>
 * <li>the <b>large battery</b> is built for the long haul and lasts longest, but it is the one the cold hurts most, as it holds its charge
 * worst in the cold already: a large bank left outdoors in the snow wears fastest of all.</li>
 * </ul>
 * The numbers are a first pass for the author to tune, none yet shown as a dyno-style table.
 */
public final class BatteryWear {

	/** The share of its capacity a completely worn battery has lost: it keeps the rest, so a worn battery is weak and not dead. */
	public static final double MAX_FADE = 0.5;
	/** Below this share of capacity a draw counts as a deep discharge. */
	public static final double DEEP_BELOW = 0.2;

	/**
	 * What wears a tier.
	 *
	 * @param cycles      how many full discharges (its whole capacity drawn out) a battery of this tier lasts, drawn warm and from a good charge
	 * @param deepStress  how many times harder a draw from an empty battery wears it than one from a good charge: the stress rises
	 *                    evenly from 1 at {@link #DEEP_BELOW} to this at empty
	 * @param coldStress  how much harder, at most, a draw in the deepest cold wears it: 0 is untouched by the cold, 2 is three times the wear
	 */
	public record Profile(double cycles, double deepStress, double coldStress) {
	}

	private static final Profile PLAIN = new Profile(1500, 3, 2);
	private static final Profile DRY_CELL = new Profile(800, 4, 0);
	private static final Profile LARGE = new Profile(3000, 2, 3);

	private BatteryWear() {
	}

	/** What wears a tier of battery. */
	public static Profile profile(BatteryTier tier) {
		return switch (tier) {
			case PLAIN -> PLAIN;
			case DRY_CELL -> DRY_CELL;
			case LARGE -> LARGE;
		};
	}

	/** How much a battery of this tier holds when it is this worn: its capacity less the share that wear has taken. */
	public static float capacity(BatteryTier tier, double wear) {
		return (float) (tier.capacity() * capacityShare(wear));
	}

	/** The share of its capacity a battery this worn still holds, 1 when new and {@code 1 - MAX_FADE} when spent. */
	public static double capacityShare(double wear) {
		return 1 - MAX_FADE * Wear.clamp(wear);
	}

	/** How much harder a draw wears a battery that holds {@code share} (0 to 1) of what it can: 1 above {@link #DEEP_BELOW}, rising to {@code deepStress} when empty. */
	public static double depthFactor(BatteryTier tier, double share) {
		double s = Double.isNaN(share) ? 1 : Math.clamp(share, 0, 1);
		double deep = Math.clamp((DEEP_BELOW - s) / DEEP_BELOW, 0, 1);
		return 1 + (profile(tier).deepStress() - 1) * deep;
	}

	/** How much harder a draw wears a battery {@code cold} (0 warm, 1 as cold as it gets): 1 warm, rising to {@code 1 + coldStress}. */
	public static double coldFactor(BatteryTier tier, double cold) {
		double c = Double.isNaN(cold) ? 0 : Math.clamp(cold, 0, 1);
		return 1 + profile(tier).coldStress() * c;
	}

	/**
	 * The wear that drawing {@code drawn} sparks takes from a battery of this tier, which held {@code share} of what it can when the draw
	 * began and is {@code cold} (0 to 1). A full discharge, warm and from a good charge, takes {@code 1 / cycles}. Never negative, never
	 * more than 1, and nothing for a draw that is not a finite amount.
	 */
	public static double drawWear(BatteryTier tier, double drawn, double share, double cold) {
		if (!Double.isFinite(drawn))
			return 0;
		double perSpark = 1 / (tier.capacity() * profile(tier).cycles());
		return Math.min(1, Math.max(0, drawn) * perSpark * depthFactor(tier, share) * coldFactor(tier, cold));
	}

	/** The wear as goggles word it, in percent: a decimal below 10, where a whole number would sit still for minutes of running. */
	public static String percentText(double wear) {
		double worn = Wear.clamp(wear);
		// Worded from the step that clients are told at, so the two never round apart
		int step = shownStep(worn);
		if (worn == 0 || step >= 100)
			return Long.toString(Math.round(worn * 100));
		return step / 10 + "." + step % 10;
	}

	/** The wear in tenths of a percent, the finest {@link #percentText} shows: clients are told, and the save marked, when it moves. */
	public static int shownStep(double wear) {
		return (int) Math.round(Wear.clamp(wear) * 1000);
	}

	/** Whether the wear is past what a Service Kit sets right, so that only a new battery cures it. */
	public static boolean heavy(double wear) {
		return Wear.clamp(wear) > Wear.REPAIRABLE;
	}

	/** Whether the wear is light enough for a Service Kit to set right (the same rule as every other part). */
	public static boolean repairable(double wear) {
		return Wear.repairable(wear);
	}

	/** The wear a battery is left with after a Service Kit: see {@link Wear#repaired}. */
	public static double repaired(double wear) {
		return Wear.repaired(wear);
	}
}
