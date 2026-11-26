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
	/** Seconds of running dry at the reference speed, kept up, after which a standard piston seizes (first-pass; the author's to tune). */
	public static final double SEIZE_AFTER = 120;
	/** A slow engine scores its bore more slowly and a fast one faster, but never by less or more than these shares of the reference rate. */
	public static final double SEIZE_SLOWEST = 0.5;
	public static final double SEIZE_FASTEST = 2;
	/** Shares of {@link #SEIZE_AFTER} at which a piston running dry cries out: from half way, closer together the nearer it is. */
	private static final double[] WARNINGS = {0.5, 0.68, 0.81, 0.9, 0.96};
	/** Oil brings the dryness back down this many times faster than running dry builds it. */
	public static final double SEIZE_RECOVERY = 2;
	/** A seized piston is left at least this worn: it needs replacing soon, and a Service Kit only pulls it out. */
	public static final double SEIZED_WEAR = 0.75;
	/** How many times a plain cylinder's drag on the crank a seized one has: it locks, and a small engine with one may stall. */
	public static final double SEIZED_DRAG = 4;

	/** Wear up to this much is light damage, which a repair item sets right. More is cured only by replacing the part. */
	public static final double REPAIRABLE = 0.35;
	/** What a repair item takes a part down to: wear comes back to this, never to new. */
	public static final double REPAIRED_TO = 0.1;
	/** Failure chance per second of a set of apex seals or a turbocharger completely spent, at its own reference. */
	public static final double FAILURE_RATE = 0.02;
	/**
	 * Failure chance per second of a rod or a gasket completely spent, warm, at the reference speed (retuned for 2.4: at the old
	 * {@link #FAILURE_RATE}, growing with the square of speed, a part at 0.8 wear was certain to go within a minute at 256 RPM, which was
	 * no warning at all). First-pass.
	 */
	public static final double PART_FAILURE_RATE = 0.002;
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

	/** Block temperature (see {@code Thermal}) from which heat wears parts far faster and makes a worn one likelier to fail. First-pass. */
	public static final double HOT = Thermal.HOT;
	/** Block temperature from which heat alone blows gaskets, seizes pistons and warps heads. First-pass. */
	public static final double CRITICAL = Thermal.CRITICAL;
	/** How far past {@link #CRITICAL} the effects of heat stop growing. */
	public static final double CRITICAL_SPAN = 0.2;
	/** How much more wear each whole unit of temperature past {@link #HOT} adds, on top of the gentle rise from normal. */
	public static final double HOT_RISE = 10;
	/** Chance per second, at {@link #CRITICAL}, that a sound gasket blows from heat. */
	public static final double HEAT_BLOW_RATE = 1.0 / 400;
	/** How fast a block at {@link #CRITICAL} scores a bore, as a share of how fast running dry does. */
	public static final double HEAT_SEIZE_SHARE = 0.5;
	/** Seconds past {@link #CRITICAL} that warp a head. */
	public static final double WARP_AFTER = 180;
	/** The share of every burn a warped head loses past its joint. */
	public static final double WARP_SEAL_LOSS = 0.15;
	/** The load a rod is read as carrying when nothing says: half of what the engine can give. */
	public static final double REFERENCE_LOAD = 0.5;
	/** How hard a rod works spinning free, and pulling all the engine has, against 1 at half load. */
	public static final double LOAD_FREE = 0.6;
	public static final double LOAD_FULL = 1.4;

	/**
	 * The connecting rod a cylinder holds. {@link #STANDARD} is the rod there has always been. A light rod is less mass going up and down,
	 * so it drags a little less as the speed rises, but it wears fast under load; a heavy one takes load in its stride and costs a little
	 * at speed. The difference in drag is there with the wear mode on or off; only the difference in wear belongs to the mode. First-pass.
	 *
	 * @param viscous how its share of the cylinder's friction that grows with speed compares with a standard rod's
	 * @param free    how hard it works with the engine spinning free, against a standard rod at half load
	 * @param full    how hard it works with the engine pulling all it can
	 */
	public enum Rod {
		STANDARD(1, LOAD_FREE, LOAD_FULL),
		LIGHT(0.96, 0.5, 2.0),
		HEAVY(1.04, 0.7, 1.0);

		public final double viscous;
		public final double free;
		public final double full;

		Rod(double viscous, double free, double full) {
			this.viscous = viscous;
			this.free = free;
			this.full = full;
		}

		/** The kind for a saved number, {@link #STANDARD} for anything unknown. */
		public static Rod byId(int id) {
			Rod[] all = values();
			return id < 1 || id >= all.length ? STANDARD : all[id];
		}

		/** How much harder this rod works for the load on the engine, 0 to 1; nonsense is half. */
		public double loadFactor(double load) {
			return free + (full - free) * Math.clamp(finite(load, REFERENCE_LOAD), 0, 1);
		}
	}

	/** What a cylinder reads from its worn parts. */
	public interface State {
		/** The share of the burn's heat the cylinder keeps, 1 for a sound one. */
		double seal();

		/** How many times harder than a sound one the cylinder drags on the crank, 1 for a sound one. */
		double drag();

		/** How the friction that grows with speed compares with a standard rod's (see {@link Rod}). */
		default double viscous() {
			return 1;
		}

		/** The share of sparks a worn plug misses, 0 for a sound one (see {@link PlugWear}). */
		default double misfire() {
			return 0;
		}

		/** What the leanest mixture the plug can light is multiplied by, 1 for a sound one (see {@link PlugWear}). */
		default double lean() {
			return 1;
		}
	}

	/** {@code state} with a rod of this kind in the cylinder; a standard rod changes nothing, and gives back the very same state. */
	public static State withRod(State state, Rod rod) {
		return rod == Rod.STANDARD ? state : state(state.seal(), state.drag(), rod.viscous, state.misfire(), state.lean());
	}

	/** {@code state} with a spark plug this worn; one that has not worn changes nothing, and gives back the very same state. */
	public static State withPlug(State state, double plugWear) {
		return clamp(plugWear) == 0 ? state
			: state(state.seal(), state.drag(), state.viscous(), PlugWear.misfireChance(plugWear), PlugWear.leanScale(plugWear));
	}

	/** {@code state} under a warped head, which loses {@link #WARP_SEAL_LOSS} of every burn past its joint. */
	public static State warped(State state) {
		return state(state.seal() * (1 - WARP_SEAL_LOSS), state.drag(), state.viscous(), state.misfire(), state.lean());
	}

	private static State state(double seal, double drag, double viscous, double misfire, double lean) {
		return new State() {
			@Override
			public double seal() {
				return seal;
			}

			@Override
			public double drag() {
				return drag;
			}

			@Override
			public double viscous() {
				return viscous;
			}

			@Override
			public double misfire() {
				return misfire;
			}

			@Override
			public double lean() {
				return lean;
			}
		};
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

	/**
	 * How much worse hot running is: nothing at or below normal temperature, half as much again for each 0.2 over it, and from
	 * {@link #HOT} on far more steeply, {@link #HOT_RISE} more for each whole unit past it.
	 */
	public static double heatFactor(double temperature) {
		double t = finite(temperature, Thermal.NORMAL);
		return 1 + 2.5 * Math.max(0, t - Thermal.NORMAL) + HOT_RISE * Math.max(0, t - HOT);
	}

	/** How far past {@link #CRITICAL} a block is, 0 at it or below to 1 at {@link #CRITICAL_SPAN} over and beyond. */
	public static double pastCritical(double temperature) {
		return Math.clamp((finite(temperature, Thermal.NORMAL) - CRITICAL) / CRITICAL_SPAN, 0, 1);
	}

	/** Whether the block is hot enough to break things by heat alone. */
	public static boolean critical(double temperature) {
		return finite(temperature, Thermal.NORMAL) >= CRITICAL;
	}

	/**
	 * The chance, in {@code dt} seconds, that a gasket blows from heat alone, however sound it is: none below {@link #CRITICAL}, and
	 * twice as likely at {@link #CRITICAL_SPAN} past it as at it.
	 */
	public static double heatBlowChance(double temperature, double dt) {
		if (!Double.isFinite(dt) || !critical(temperature))
			return 0;
		return Math.min(1, HEAT_BLOW_RATE * (1 + pastCritical(temperature)) * Math.max(0, dt));
	}

	/**
	 * How long a head has soaked past {@link #CRITICAL}, after {@code dt} more seconds at this temperature: it builds above it, twice as
	 * fast at {@link #CRITICAL_SPAN} past it, and falls away second for second below it. Never negative, never above the warping point,
	 * and nonsense in leaves it unchanged. Deterministic, so the goggles' overheating reading is the warning.
	 */
	public static double heatSoak(double before, double temperature, double dt) {
		double was = Double.isFinite(before) ? Math.clamp(before, 0, WARP_AFTER) : 0;
		if (!Double.isFinite(dt))
			return was;
		double span = Math.max(0, dt);
		if (critical(temperature))
			return Math.min(WARP_AFTER, was + span * (1 + pastCritical(temperature)));
		return Math.max(0, was - span);
	}

	/** Whether a head that has soaked this long has warped. */
	public static boolean warps(double soak) {
		return soak >= WARP_AFTER;
	}

	/** How much harder a standard rod works for the load on the engine, 0 (spinning free) to 1 (all it can give); nonsense is half. */
	public static double loadFactor(double load) {
		return Rod.STANDARD.loadFactor(load);
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

	/** Wear a connecting rod takes in {@code dt} seconds on a warm engine at half load: see {@link #rodWear(double, double, double, double, double)}. */
	public static double rodWear(double rpm, double film, double dt) {
		return rodWear(rpm, Thermal.NORMAL, film, REFERENCE_LOAD, dt);
	}

	/** Wear a connecting rod takes in {@code dt} seconds: speed, heat, no oil and how hard the engine is working, but not boost. */
	public static double rodWear(double rpm, double temperature, double film, double load, double dt) {
		return rodWear(Rod.STANDARD, rpm, temperature, film, load, dt);
	}

	/** As above, for a rod of this kind, which takes the load its own way (see {@link Rod}). */
	public static double rodWear(Rod rod, double rpm, double temperature, double film, double load, double dt) {
		return step(dt, ROD_LIFE, speedFactor(rpm) * heatFactor(temperature) * dryFactor(film) * rod.loadFactor(load));
	}

	/** Wear a head gasket takes in {@code dt} seconds: heat and boost on top of speed. */
	public static double gasketWear(Gasket gasket, double rpm, double temperature, double intakePressure, double dt) {
		return step(dt, GASKET_LIFE * gasket.life, speedFactor(rpm) * heatFactor(temperature) * boostFactor(intakePressure, gasket));
	}

	/**
	 * How far a cylinder has run dry, after {@code dt} more seconds at this oil film and speed, counted in seconds at the reference speed on a
	 * standard piston. Running below {@link #SEIZE_FILM} adds to it, faster the faster the crank turns (between {@link #SEIZE_SLOWEST} and
	 * {@link #SEIZE_FASTEST} times), and {@link #PRECISION_LIFE} times as slowly on a forged precision piston; oil takes it off
	 * {@link #SEIZE_RECOVERY} times as fast as time passes; a stopped engine neither gains nor loses (nothing is turning, so nothing is
	 * scoring). Never negative, never above the seizing point, and nonsense in leaves it unchanged.
	 */
	public static double dryTime(double before, double film, double rpm, double dt, boolean precision) {
		return dryTime(before, film, Thermal.NORMAL, rpm, dt, precision);
	}

	/**
	 * As {@link #dryTime(double, double, double, double, boolean)}, with heat as a second way to score the bore: from {@link #CRITICAL}
	 * the count builds at {@link #HEAT_SEIZE_SHARE} of the dry rate, on top of it when the piston is dry too, and oil does not bring it
	 * down until the block is back below.
	 */
	public static double dryTime(double before, double film, double temperature, double rpm, double dt, boolean precision) {
		double was = Double.isFinite(before) ? Math.min(SEIZE_AFTER, Math.max(0, before)) : 0;
		if (!Double.isFinite(dt) || !(rpm > 0))
			return was;
		// A span of time below nothing is none
		double span = Math.max(0, dt);
		double scoring = (finite(film, 1) < SEIZE_FILM ? 1 : 0) + (critical(temperature) ? HEAT_SEIZE_SHARE : 0);
		if (scoring > 0) {
			double rate = scoring * Math.clamp(rpm / REFERENCE_RPM, SEIZE_SLOWEST, SEIZE_FASTEST) / (precision ? PRECISION_LIFE : 1);
			return Math.min(SEIZE_AFTER, was + span * rate);
		}
		return Math.max(0, was - SEIZE_RECOVERY * span);
	}

	/** Whether a cylinder that has run dry this far has seized. */
	public static boolean seizes(double dryTime) {
		return dryTime >= SEIZE_AFTER;
	}

	/** Whether the step from {@code before} to {@code after} passed one of the points at which a dry piston cries out. */
	public static boolean warns(double before, double after) {
		for (double share : WARNINGS) {
			double at = share * SEIZE_AFTER;
			if (before < at && after >= at)
				return true;
		}
		return false;
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

	/** The chance a rod or gasket this worn fails on a warm engine: see {@link #failureChance(double, double, double, double)}. */
	public static double failureChance(double wear, double rpm, double dt) {
		return failureChance(wear, rpm, Thermal.NORMAL, dt);
	}

	/**
	 * The chance, in {@code dt} seconds, that a rod or gasket this worn fails: zero below {@link #FAILURE_FROM}, then rising with the cube
	 * of how far past it the wear is, with speed (in proportion, from the reference up to {@link #SPEED_CAP} times it) and with heat (see
	 * {@link #heatRisk}). Never above 1.
	 */
	public static double failureChance(double wear, double rpm, double temperature, double dt) {
		if (!Double.isFinite(dt))
			return 0;
		// Below the threshold there is nothing past it, and so no chance; a span of time below nothing is none
		double past = Math.max(0, (clamp(wear) - FAILURE_FROM) / (1 - FAILURE_FROM));
		double speed = Math.clamp(finite(rpm, 0) / REFERENCE_RPM, 1, SPEED_CAP);
		double chance = PART_FAILURE_RATE * past * past * past * speed * heatRisk(temperature) * Math.max(0, dt);
		return Math.min(1, chance);
	}

	/** How many times likelier heat makes a worn part fail: 1 up to {@link #HOT}, 3 at {@link #CRITICAL}, 6 at {@link #CRITICAL_SPAN} past it. */
	public static double heatRisk(double temperature) {
		double warm = Math.clamp((finite(temperature, Thermal.NORMAL) - HOT) / (CRITICAL - HOT), 0, 1);
		return 1 + 2 * warm + 3 * pastCritical(temperature);
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
