package com.createengines.engine.sim;

/**
 * The opt-in wear mode's rules: how fast a piston, a connecting rod and a head gasket wear, what a worn part costs a cylinder, and how
 * likely a rod is to be thrown or a gasket to blow. Everything here is a pure function of numbers, with no Minecraft types, so it is unit
 * tested; {@code EngineController} reads the cylinders and calls it, and {@code CylinderModel} applies the two effects.
 * <p>
 * Wear is a number from 0 (new) to 1 (spent). With {@link Tuning#wearMode} off nothing wears and no wear figure is called; a cylinder's
 * {@link State} is {@link #NONE}, exactly what it was before wear existed, unless a gasket has been fitted to its head, when it is that tier's
 * {@link #fitted} seal and nothing else.
 * <p>
 * The first-pass numbers below are the author's to tune. They are chosen so that a piston engine turning at a steady, ordinary speed
 * wears out its stock gasket in a few real hours, while the same engine idling lasts many times as long, so wear is a thing to watch and
 * plan for and not a clock.
 */
public final class Wear {

	/** A speed, in RPM, at which an engine running warm on good oil wears at the basic rate. */
	public static final double REFERENCE_RPM = 128;
	/** Seconds a piston lasts at the reference speed, warm and oiled and unboosted. */
	public static final double PISTON_LIFE = 5 * 3600;
	/** Seconds a connecting rod lasts under the same conditions: it is stressed by speed and by running dry but not by heat or boost. */
	public static final double ROD_LIFE = 8 * 3600;
	/** Seconds the weakest gasket (the one a head has before any is fitted) lasts under the same conditions. */
	public static final double GASKET_LIFE = 3 * 3600;
	/** How many times longer a precision part lasts than its standard version (the author, 2026-11-24: all precision parts wear slower). First-pass number. */
	public static final double PRECISION_LIFE = 2.5;
	/** Wear slows to this share at standstill-slow speeds: a turning engine always wears a little. */
	public static final double IDLE_FLOOR = 0.02;
	/** Speeds above this many times the reference wear no faster, so a runaway engine is hard on its parts and not instantly spent. */
	public static final double SPEED_CAP = 4;

	/** A piston this worn costs the cylinder this much of its burn at most (the leak past worn rings). */
	public static final double PISTON_SEAL_LOSS = 0.2;
	/** A piston this worn drags this much harder, at most, on the crank. */
	public static final double PISTON_DRAG = 0.6;
	/** A gasket this worn costs the cylinder this much of its burn at most. */
	public static final double GASKET_SEAL_LOSS = 0.3;

	/**
	 * Oil film below which a running engine is working its pistons dry. The same figure as the goggles' "failing: dry oil" reading
	 * ({@code Diagnosis.SEIZING_BELOW}), so the readout warns before a piston seizes.
	 */
	public static final double SEIZE_FILM = 0.15;
	/** Seconds of running dry, kept up, after which a piston seizes (first-pass; the author's to tune). */
	public static final double SEIZE_AFTER = 20;
	/** Oil brings the dryness back down this many times faster than running dry builds it. */
	public static final double SEIZE_RECOVERY = 2;
	/** A seized piston is left at least this worn: it needs replacing soon, and a Service Kit only frees it. */
	public static final double SEIZED_WEAR = 0.75;
	/** How many times a plain cylinder's drag on the crank a seized one has: it locks, and a small engine with one may stall. */
	public static final double SEIZED_DRAG = 4;

	/** Wear up to this much is light damage, which a repair item sets right. More is cured only by replacing the part. */
	public static final double REPAIRABLE = 0.35;
	/** What a repair item takes a part down to: wear comes back to this, never to new. */
	public static final double REPAIRED_TO = 0.1;
	/** Failure chance per second of a part that is completely spent and running at the reference speed. */
	public static final double FAILURE_RATE = 0.02;
	/** Wear below this never fails: a part has to be well used before a failure is possible. */
	public static final double FAILURE_FROM = 0.5;

	/**
	 * The gasket a head holds. A head with none fitted has {@link #STOCK}, the weakest, so turning the mode on never leaves an existing
	 * engine without one; that head runs as it always did, and a gasket fitted to it seals the joint better, which is worth a little power
	 * whether or not the wear mode is on (the author, 2026-11-24: no gasket does nothing with the mode off).
	 *
	 * @param life  how many times {@link #GASKET_LIFE} it lasts
	 * @param hold  how much boost it takes before it works harder: the pressure above ambient is divided by this
	 * @param seal  how much of {@link #GASKET_SEAL_LOSS} it loses to wear: copper keeps its seal better as it ages
	 * @param bonus how much more of each burn's heat a new one of this tier, fitted, keeps in the cylinder than a head with none fitted (first-pass,
	 *              the author's to tune); it fades as the gasket wears, with the mode on
	 */
	public enum Gasket {
		STOCK(1, 1, 1, 0.01),
		CARDBOARD(1.5, 1.2, 0.9, 0.025),
		RUBBER(3, 1.8, 0.7, 0.04),
		COPPER(6, 3, 0.5, 0.06);

		public final double life;
		public final double hold;
		public final double seal;
		public final double bonus;

		Gasket(double life, double hold, double seal, double bonus) {
			this.life = life;
			this.hold = hold;
			this.seal = seal;
			this.bonus = bonus;
		}

		/** The tier for a saved number, {@link #STOCK} for anything unknown. */
		public static Gasket byId(int id) {
			Gasket[] all = values();
			// Stock is the first, so a number below the second is stock whether it is the first or nonsense
			return id < 1 || id >= all.length ? STOCK : all[id];
		}
	}

	/** What a cylinder reads from its worn parts. */
	public interface State {
		/** The share of the burn's heat the cylinder keeps, 1 for a sound one. */
		double seal();

		/** How many times harder than a sound one the cylinder drags on the crank, 1 for a sound one. */
		double drag();
	}

	/** No wear: the cylinder runs as it always did. */
	public static final State NONE = new State() {
		@Override
		public double seal() {
			return 1;
		}

		@Override
		public double drag() {
			return 1;
		}
	};

	private Wear() {
	}

	/** How much wear a part takes on top of a base rate for turning at this speed: a floor when slow, squared up to a cap when fast. */
	public static double speedFactor(double rpm) {
		if (!(rpm > 0))
			return 0;
		double share = Math.min(SPEED_CAP, rpm / REFERENCE_RPM);
		return Math.max(IDLE_FLOOR, share * share);
	}

	/** How much worse hot running is: nothing at or below normal temperature, and half as much again for each 0.2 over it. */
	public static double heatFactor(double temperature) {
		return 1 + 2.5 * Math.max(0, finite(temperature, Thermal.NORMAL) - Thermal.NORMAL);
	}

	/** How much worse running without oil is: 1 with the parts fully filmed, 5 with none. */
	public static double dryFactor(double film) {
		return 1 + 4 * (1 - Math.clamp(finite(film, 1), 0, 1));
	}

	/** How much worse a boosted cylinder is on its gasket: nothing at ambient pressure, rising with the pressure the gasket has to hold. */
	public static double boostFactor(double intakePressure, Gasket gasket) {
		return 1 + 3 * Math.max(0, finite(intakePressure, 1) - 1) / gasket.hold;
	}

	/** Wear a piston takes in {@code dt} seconds. */
	public static double pistonWear(double rpm, double temperature, double film, double dt) {
		return pistonWear(rpm, temperature, film, dt, false);
	}

	/** Wear a piston takes in {@code dt} seconds; a forged precision piston lasts {@link #PRECISION_LIFE} times as long as a standard one. */
	public static double pistonWear(double rpm, double temperature, double film, double dt, boolean precision) {
		return step(dt, PISTON_LIFE * (precision ? PRECISION_LIFE : 1), speedFactor(rpm) * heatFactor(temperature) * dryFactor(film));
	}

	/** Wear a connecting rod takes in {@code dt} seconds: speed and no oil, but neither heat nor boost. */
	public static double rodWear(double rpm, double film, double dt) {
		return step(dt, ROD_LIFE, speedFactor(rpm) * dryFactor(film));
	}

	/** Wear a head gasket takes in {@code dt} seconds: heat and boost on top of speed. */
	public static double gasketWear(Gasket gasket, double rpm, double temperature, double intakePressure, double dt) {
		return step(dt, GASKET_LIFE * gasket.life, speedFactor(rpm) * heatFactor(temperature) * boostFactor(intakePressure, gasket));
	}

	/**
	 * How many seconds a cylinder has been running dry, after {@code dt} more seconds at this oil film and speed. Running below
	 * {@link #SEIZE_FILM} adds the time; oil takes it off {@link #SEIZE_RECOVERY} times as fast; a stopped engine neither gains nor loses
	 * (nothing is turning, so nothing is scoring). Never negative, never above the seizing point, and nonsense in leaves it unchanged.
	 */
	public static double dryTime(double before, double film, double rpm, double dt) {
		double was = Double.isFinite(before) ? Math.min(SEIZE_AFTER, Math.max(0, before)) : 0;
		if (!Double.isFinite(dt) || !(rpm > 0))
			return was;
		// A span of time below nothing is none
		double span = Math.max(0, dt);
		if (finite(film, 1) < SEIZE_FILM)
			return Math.min(SEIZE_AFTER, was + span);
		return Math.max(0, was - SEIZE_RECOVERY * span);
	}

	/** Whether a cylinder that has run dry this long has seized. */
	public static boolean seizes(double dryTime) {
		return dryTime >= SEIZE_AFTER;
	}

	/** Whether it has run dry long enough that the engine squeals: half way to seizing. */
	public static boolean squeals(double dryTime) {
		return dryTime >= SEIZE_AFTER / 2;
	}

	private static double step(double dt, double life, double factor) {
		// A step that is not a finite span of time wears nothing, and no one step can spend more than a whole part
		if (!Double.isFinite(dt))
			return 0;
		return Math.min(1, Math.max(0, dt) / life * factor);
	}

	/** The burn a piston this worn keeps: 1 when sound, falling with the square of the wear. */
	public static double pistonSeal(double wear) {
		double w = clamp(wear);
		return 1 - PISTON_SEAL_LOSS * w * w;
	}

	/** The burn a gasket this worn keeps. */
	public static double gasketSeal(Gasket gasket, double wear) {
		double w = clamp(wear);
		return 1 - GASKET_SEAL_LOSS * gasket.seal * w * w;
	}

	/** How much harder a piston this worn drags on the crank: 1 when sound. */
	public static double pistonDrag(double wear) {
		double w = clamp(wear);
		return 1 + PISTON_DRAG * w * w;
	}

	/**
	 * The chance, in {@code dt} seconds, that a part this worn fails: zero below {@link #FAILURE_FROM}, then rising with the cube of how
	 * far past it the wear is and with speed (squared, as wear itself is). Never above 1.
	 */
	public static double failureChance(double wear, double rpm, double dt) {
		if (!Double.isFinite(dt))
			return 0;
		// Below the threshold there is nothing past it, and so no chance; a span of time below nothing is none
		double past = Math.max(0, (clamp(wear) - FAILURE_FROM) / (1 - FAILURE_FROM));
		double chance = FAILURE_RATE * past * past * past * Math.max(1, speedFactor(rpm)) * Math.max(0, dt);
		return Math.min(1, chance);
	}

	/** Whether the wear is light enough for a repair item to set right. */
	public static boolean repairable(double wear) {
		double w = clamp(wear);
		return w > REPAIRED_TO && w <= REPAIRABLE;
	}

	/** The wear a part is left with after a repair item: no more than it had, and no less than {@link #REPAIRED_TO}. */
	public static double repaired(double wear) {
		return repairable(wear) ? REPAIRED_TO : clamp(wear);
	}

	/** A part's wear kept within 0 to 1, with nonsense (NaN) read as new. */
	public static double clamp(double wear) {
		return Double.isNaN(wear) ? 0 : Math.min(1, Math.max(0, wear));
	}

	/**
	 * What a gasket fitted to a head adds: the share of the burn's heat it keeps over a head with none fitted, 1 plus the tier's bonus when new
	 * and fading to nothing as it wears (a spent gasket seals no better than none, and loses power besides, see {@link #gasketSeal}).
	 */
	public static double fittedSeal(Gasket gasket, double wear) {
		return 1 + gasket.bonus * (1 - clamp(wear));
	}

	private static final java.util.Map<Gasket, State> FITTED = new java.util.EnumMap<>(Gasket.class);

	static {
		for (Gasket tier : Gasket.values()) {
			double seal = fittedSeal(tier, 0);
			FITTED.put(tier, new State() {
				@Override
				public double seal() {
					return seal;
				}

				@Override
				public double drag() {
					return 1;
				}
			});
		}
	}

	/**
	 * What a cylinder reads with the wear mode off and a gasket fitted to its head: the tier's seal and nothing else. Nothing wears with the mode
	 * off, so it is always the new gasket's figure, and a head with none fitted is {@link #NONE}, exactly as before gaskets existed.
	 */
	public static State fitted(Gasket gasket) {
		return FITTED.get(gasket);
	}

	/** The state a cylinder with these wears reads, the gasket counted as the stock one that every head is read as holding (none fitted). */
	public static State of(double piston, Gasket gasket, double gasketWear) {
		return of(piston, gasket, gasketWear, false);
	}

	/** As above; with {@code fitted}, the gasket is one that was fitted to the head, so it also adds its tier's {@link #fittedSeal}. */
	public static State of(double piston, Gasket gasket, double gasketWear, boolean fitted) {
		double seal = pistonSeal(piston) * gasketSeal(gasket, gasketWear) * (fitted ? fittedSeal(gasket, gasketWear) : 1);
		double drag = pistonDrag(piston);
		return new State() {
			@Override
			public double seal() {
				return seal;
			}

			@Override
			public double drag() {
				return drag;
			}
		};
	}

	private static double finite(double value, double fallback) {
		return Double.isFinite(value) ? value : fallback;
	}
}
