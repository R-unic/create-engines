package com.createengines.engine.sim;

import org.jetbrains.annotations.Nullable;

/**
 * One piston-cylinder: a pocket of gas whose volume is set by crank angle, moved by cam-driven valves and heated by
 * cam-triggered fuel injection.
 * <p>
 * The whole model is five rules (plus a spark plug, if the cylinder has one):
 * <ol>
 *     <li>Volume follows slider-crank geometry; the torque on the crank is the p·dV work.</li>
 *     <li>Gas changes volume adiabatically.</li>
 *     <li>An open valve relaxes cylinder pressure toward its port's pressure at a rate set by lift × flow × time.</li>
 *     <li>Injected fuel ignites after a delay, but only if the charge is hot enough, and burns only as far as fresh
 *     air allows. With a spark plug it is lit by the spark instead, if the mixture is neither too lean nor too rich.
 *     Fuel it has no air to burn only soaks up heat.</li>
 *     <li>(In {@link EngineSimulation}) the rotor's kinetic energy changes by work minus friction and load.</li>
 * </ol>
 * The piston follows the crank; the valves and injector follow the cam. Whether those two agree is entirely down to
 * how the engine was built, and every behaviour (misfires, weak cylinders, stalls, rough running) comes out of that.
 */
public final class CylinderModel implements TorqueSource {

	static final double GAMMA = 1.3;
	static final double FUEL_EPSILON = 1e-6;
	/** Most an injector can deliver as a multiple of {@link CylinderSpec#maxFuelPerShot()}: Fuel Metering's top setting. */
	public static final double INJECTOR_CAPACITY = 2;
	/** Fresh charge below which a cylinder counts as having no air at all, rather than too little for the shot. */
	static final double MIN_FRESH_AIR = 0.05;
	/** Pilot fuel injected to light a charge, as a share of the main shot (by volume). */
	public static final double PILOT_SHARE = 0.08;
	/** A charge lit by a pilot flame spreads a little more slowly than one lit by a spark across the chamber. */
	public static final double PILOT_BURN_SLOWDOWN = 1.15;
	/** Richer than this (equivalence ratio) and a spark can't get a flame going. */
	static final double RICH_LIMIT = 1.8;

	public final CylinderSpec spec;
	private final CylinderSpec.Geometry geometry;
	private final CamProfile.Curve intakeCurve;
	private final CamProfile.Curve exhaustCurve;
	/**
	 * Degrees of a rotary chamber's cycle for each degree of eccentric shaft: the rotor turns at a third of the shaft,
	 * and a chamber goes through two volume cycles (four strokes) per turn of the rotor.
	 */
	public static final double ROTARY_SCALE = 2 / 3d;
	/** Eccentric shaft degrees between one chamber of a rotor reaching a given stroke and the next one doing so. */
	public static final double ROTARY_CHAMBER_SPACING = 360;
	/** Eccentric shaft degrees in a chamber's whole four-stroke cycle: one turn of the rotor. */
	public static final double ROTARY_CYCLE = 1080;

	private final CamProfile profile;
	/** Geometric crank angle (degrees) at which this piston is at top dead centre. */
	private final double tdcCrankAngle;
	private final @Nullable Camshaft camshaft;
	/** Angle set on this cylinder's lobe block, cam degrees. */
	private final double dialAngle;
	/**
	 * Angle of this cylinder's lobe on its camshaft, cam degrees: the dial, plus what sharing the lobe with an earlier
	 * cylinder adds for the way the crank turns (see {@link #shareLobe}).
	 */
	private double lobeAngle;
	private double forwardShift;
	private double reverseShift;
	private int turning = 1;
	/**
	 * How the crank sets this cylinder's place in its cycle and steps it. A rotary chamber has no cam: the housing's ports
	 * are cut at fixed places, so its valves and its injection follow its own cycle angle (see {@link CycleDrive}).
	 */
	private final CycleDrive drive;

	// Inputs, refreshed by the owner every tick
	public double rack = 1;
	/** Share of {@link CylinderSpec#maxFuelPerShot()} the injector is set to deliver, set by hand. */
	public double metering = 1;
	public IgnitionSource ignition = IgnitionSource.NONE;
	public double intakeFactor = 1;
	/** Share of the shot a throttle body's linkage lets through; 1 where there is no throttle. */
	public double fuelTrim = 1;
	/**
	 * Equivalence ratio a MAF sensor holds the shot to, or 0 where there is none. The injector delivers the fuel the
	 * air the cylinder drew in can burn at this ratio, trimming a shot that is too big and topping up one that is too
	 * small, up to the most the injector can deliver ({@link #INJECTOR_CAPACITY}). Fuel Metering no longer applies.
	 */
	public double airMatch = 0;
	/**
	 * Rack position a mechanical governor holds the engine at, 0 (shut) to 1 (wide open); 1 where there is none. A
	 * compression engine is governed on fuel alone. A spark engine can't be, because a lean charge won't light, so the
	 * governor throttles its air and the throttle linkage trims its fuel to follow. Not part of the engine's rating: a
	 * governor holds speed with the torque in hand, it does not take any away.
	 */
	public double governor = 1;
	public double exhaustFactor = 1;
	/** Pressure of the air the intake valve draws on, ambient (1) unless a compressor is pushing it. */
	public double intakePressure = 1;
	/** Temperature of that air: compressing it heats it, which costs some of the density the pressure bought. */
	public double intakeTemperature = 1;
	/** How cold the block around the cylinder is, 0 (warm) to 1 (stone cold): cold walls quench some of the flame. */
	public double coldness = 0;
	/** Pressure the exhaust valve blows down into, ambient (1) unless a turbine is in the way. */
	public double exhaustPressure = 1;
	public FuelSupply fuelSupply = FuelSupply.NONE;
	/**
	 * A second, easily ignited fuel (diesel, say) a cylinder with no spark plug can inject a little of to light a main
	 * charge that compression alone couldn't: the pilot ignites at its own, lower autoignition temperature and its flame
	 * lights the rest. Used only when the main fuel would not light by itself.
	 */
	public FuelSupply pilotSupply = FuelSupply.NONE;

	/** The gas in the cylinder, which the parts below all work on; see {@link Charge}. */
	final Charge charge = new Charge();
	/** Gas waiting in the intake and exhaust ports, and what flows between them and the cylinder; see {@link Ports}. */
	private final Ports ports = new Ports();
	final CycleStats current = new CycleStats();
	private final CycleStats last = new CycleStats();
	/** Soot on the plug and in the exhaust; see {@link Soiling}. */
	private final Soiling soiling = new Soiling();
	/** Injection, ignition and burning of the fuel; see {@link Combustion}. */
	private final Combustion combustion = new Combustion(this, charge, current, soiling);
	private double travelSinceCycle;

	public CylinderModel(CylinderSpec spec, CamProfile profile, double tdcCrankAngle, @Nullable Camshaft camshaft,
						 double lobeAngle) {
		this(spec, profile, tdcCrankAngle, camshaft, lobeAngle, CycleDrive.PISTON);
	}

	private CylinderModel(CylinderSpec spec, CamProfile profile, double tdcCrankAngle, @Nullable Camshaft camshaft,
						  double lobeAngle, CycleDrive drive) {
		this.spec = spec;
		this.geometry = spec.geometry();
		this.profile = profile;
		this.intakeCurve = profile.intakeCurve();
		this.exhaustCurve = profile.exhaustCurve();
		this.tdcCrankAngle = tdcCrankAngle;
		this.camshaft = camshaft;
		this.dialAngle = lobeAngle;
		this.lobeAngle = lobeAngle;
		this.drive = drive;
		resetGas();
	}

	/**
	 * One chamber of a Wankel rotor. {@code eccentricTdc} is the shaft angle at which the rotor's eccentric points at the
	 * housing's reference mark, and {@code chamber} (0 to 2) which of the rotor's three faces this is.
	 */
	public static CylinderModel rotary(CylinderSpec spec, CamProfile profile, double eccentricTdc, int chamber) {
		return new CylinderModel(spec, profile, eccentricTdc, null, 0,
			new CycleDrive.RotaryDrive(chamber * ROTARY_CHAMBER_SPACING * ROTARY_SCALE));
	}

	/** Crank angle of this piston past its top dead centre, for a rotor angle travelling in {@code direction}. */
	public double pistonAngle(double rotorAngle, int direction) {
		return pistonAngle(rotorAngle, rotorAngle, direction);
	}

	/**
	 * As {@link #pistonAngle(double, int)}, given the angle the shaft has turned through in the {@link #ROTARY_CYCLE} a
	 * rotary chamber repeats over. A rotary chamber's cycle angle is where it is in its four strokes: the shaft turns
	 * three times to its once.
	 */
	public double pistonAngle(double rotorAngle, double rotaryAngle, int direction) {
		return drive.cycleAngle(rotorAngle, rotaryAngle, direction, tdcCrankAngle);
	}

	/** Whether this chamber's valves and injection follow its own cycle rather than a camshaft's. */
	public boolean ported() {
		return drive.ported();
	}

	/** How many degrees of this cylinder's own cycle go by for each degree the shaft turns. */
	public double angleScale() {
		return drive.scale();
	}

	/** Whether something fixes when its valves open and its injector fires: a cam, or a rotary housing's ports. */
	private boolean timed() {
		return drive.timed(camshaft);
	}

	/** Shaft degrees one four-stroke cycle of this cylinder takes: two turns for a piston, three for a rotary chamber. */
	public double cycleDegrees() {
		return drive.cycleDegrees();
	}

	/** Whether valve timing is locked to the shaft: a cam geared 1:2, or fixed ports. */
	public boolean isTimed() {
		return drive.phaseLocked(camshaft);
	}

	/** Where the cam says this cylinder is in its cycle (0 = intake TDC, 360 = firing TDC). */
	public double camCycleAngle() {
		return camshaft == null ? 0 : camCycleAngle(camshaft.angle(), lobeAngle);
	}

	public static double camCycleAngle(double camAngle, double lobeAngle) {
		return SimMath.wrap(2 * (camAngle - lobeAngle) + 360);
	}

	/**
	 * How far the cam's firing TDC is from the piston's top dead centre, in crank degrees within ±180. A cam can be
	 * right on either crank revolution (that choice is the firing order), so this is measured modulo 360.
	 * Only meaningful when the cam is phase-locked.
	 */
	public double timingError(int direction) {
		double error = (2 * lobeAngle - direction * tdcCrankAngle) % 360;
		if (error > 180)
			error -= 360;
		if (error <= -180)
			error += 360;
		return error;
	}

	/** Rotor angle at which the cam fires this cylinder, assuming the cam is phase-locked. */
	public double firingRotorAngle() {
		return SimMath.wrap(2 * lobeAngle);
	}

	public @Nullable Camshaft camshaft() {
		return camshaft;
	}

	public double lobeAngle() {
		return lobeAngle;
	}

	/** What sharing a lobe has added to the angle set on it, cam degrees: 0 for a cylinder that has its lobe to itself. */
	public double lobeShift() {
		return lobeAngle - dialAngle;
	}

	/**
	 * Puts this cylinder on a lobe that an earlier cylinder, whose piston reaches top dead centre at {@code leaderTdc},
	 * is timed by. The lobe's angle times that first cylinder; this one gets the same timing error, firing the same
	 * distance from its own top dead centre, on whichever crank turn puts it within one turn after the first. Reversing
	 * the crank mirrors the offset, like everything else about timing.
	 */
	public void shareLobe(double leaderTdc) {
		forwardShift = SimMath.wrap(tdcCrankAngle - leaderTdc, 360) / 2;
		reverseShift = SimMath.wrap(leaderTdc - tdcCrankAngle, 360) / 2;
		lobeAngle = dialAngle + (turning > 0 ? forwardShift : reverseShift);
	}

	/** Tells a cylinder on a shared lobe which way the crank turns. */
	@Override
	public void setDirection(int direction) {
		if (direction == turning)
			return;
		turning = direction;
		lobeAngle = dialAngle + (turning > 0 ? forwardShift : reverseShift);
	}

	public double tdcCrankAngle() {
		return tdcCrankAngle;
	}

	/** A fresh cylinder built the same way on {@code camshaft}, to test apart from the engine. */
	public CylinderModel copyOnto(@Nullable Camshaft camshaft) {
		CylinderModel copy =
			new CylinderModel(spec, profile, tdcCrankAngle, camshaft, dialAngle, drive);
		copy.lobeAngle = lobeAngle;
		copy.forwardShift = forwardShift;
		copy.reverseShift = reverseShift;
		copy.turning = turning;
		copy.rack = rack;
		copy.metering = metering;
		copy.ignition = ignition;
		copy.intakeFactor = intakeFactor;
		copy.fuelTrim = fuelTrim;
		copy.pilotSupply = pilotSupply;
		copy.airMatch = airMatch;
		copy.exhaustFactor = exhaustFactor;
		copy.intakePressure = intakePressure;
		copy.intakeTemperature = intakeTemperature;
		copy.coldness = coldness;
		copy.exhaustPressure = exhaustPressure;
		return copy;
	}

	/**
	 * Closes a tick's accounting of the heat this cylinder sent out of its exhaust valve, in moles of gas times how far
	 * hotter than ambient it was. That is what a turbine has to work with: gas that was never heated carries nothing.
	 */
	@Override
	public void endTick() {
		ports.endTick();
	}

	/** Heat this cylinder put into its exhaust during the last whole tick. */
	@Override
	public double exhaustHeatLastTick() {
		return ports.exhaustHeatLastTick();
	}

	// --- TorqueSource: what the crank asks of a cylinder ---

	/** Fuel the last {@link #advance} took in. */
	private double stepFuel;

	@Override
	public double inertia() {
		return spec.inertia();
	}

	@Override
	public double frictionStatic() {
		return spec.frictionStatic();
	}

	@Override
	public double frictionViscous() {
		return spec.frictionViscous();
	}

	@Override
	public double frictionAt(double omega, double film) {
		return spec.frictionAt(omega, film);
	}

	@Override
	public double advance(double angle, double rotaryAngle, int direction, double delta, double dt) {
		double before = current.fuelInjected;
		double work = drive.advance(this, angle, rotaryAngle, direction, delta, dt);
		stepFuel = Math.max(0, current.fuelInjected - before);
		return work;
	}

	@Override
	public double fuelUsedLastStep() {
		return stepFuel;
	}

	@Override
	public void setColdness(double coldness) {
		this.coldness = coldness;
	}

	@Override
	public double unburnedFraction() {
		return last.fuelInjected <= 0 ? -1 : last.unburnedFraction();
	}

	@Override
	public boolean fired() {
		return last.result.fired();
	}

	/**
	 * Work per crank cycle at full rack. Work from a cylinder that fired scales with the fuel it burned, up to the air it
	 * had; everything else (pumping and friction losses) is what it is.
	 */
	@Override
	public double availableWork() {
		CycleStats last = this.last;
		double scale = 1;
		FuelSpec fuel = Combustion.usable(fuelSupply.fuel());
		if (last.result.fired() && last.fuelBurned > 0 && fuel != null) {
			double airLimit = last.freshAirAtInjection / fuel.airPerMb();
			// A MAF sensor sets the shot from the air, up to what the injector can deliver; without one it is
			// whatever Fuel Metering says
			double fullShot = airMatch > 0
				? Math.min(airLimit * airMatch, injectorLimit(fuel))
				: usualShot(fuel) * metering * fuelTrim;
			scale = Math.max(1, Math.min(fullShot, airLimit) / last.fuelBurned);
		}
		// A rotary chamber takes a turn and a half of the shaft to do what a piston does in two
		return last.work * scale * SimMath.CYCLE / cycleDegrees();
	}

	public void resetGas() {
		resetGas(0);
	}

	/**
	 * Fills the cylinder with stale gas at ambient pressure for wherever its piston currently is. It holds no fresh
	 * air to burn: a cylinder has to breathe in through its intake before it can fire, so one with no intake never
	 * does.
	 */
	public void resetGas(double pistonAngle) {
		charge.moles = spec.volumeAt(pistonAngle);
		charge.temperature = 1;
		charge.fresh = 0;
		ports.reset();
		combustion.reset();
	}

	/** Takes over another model's gas and cycle state, e.g. when the engine is rebuilt around this cylinder. */
	public void copyStateFrom(CylinderModel other) {
		charge.copyFrom(other.charge);
		ports.copyFrom(other.ports);
		combustion.copyFrom(other.combustion);
		soiling.copyFrom(other.soiling);
		travelSinceCycle = other.travelSinceCycle;
		current.copyFrom(other.current);
		last.copyFrom(other.last);
	}

	public CycleStats lastCycle() {
		return last;
	}

	/** Soot the exhaust carried out since the last call, in mB of unburned fuel; empties the count. */
	public double takeSoot() {
		return soiling.takeSoot();
	}

	/** Soot on the spark plug from 0 to 1. A fouled plug can't light a rich mixture it otherwise could. */
	public double fouling() {
		return soiling.fouling();
	}

	public CycleStats currentCycle() {
		return current;
	}

	/**
	 * Advances this cylinder by {@code delta} crank degrees taking {@code dt} seconds.
	 *
	 * @param pistonFrom  piston crank angle past TDC at the start of the step
	 * @param camFrom     cam-cycle angle at the start of the step
	 * @param camDelta    how far the cam-cycle angle moves during the step
	 * @return p·dV work done on the crank during the step
	 */
	double step(double pistonFrom, double delta, double camFrom, double camDelta, double dt) {
		final Charge charge = this.charge;
		double v0 = geometry.volume(pistonFrom);
		double v1 = geometry.volume(pistonFrom + delta);

		// Rule 2: adiabatic volume change (T times V^(γ-1) stays put). Rule 1: work relative to the crankcase at ambient pressure.
		double p0 = charge.pressure(v0);
		charge.temperature *= geometry.heat(pistonFrom) / geometry.heat(pistonFrom + delta);
		double p1 = charge.pressure(v1);
		double work = (0.5 * (p0 + p1) - 1) * (v1 - v0);

		// Rule 3: valves, opened by the cam
		if (timed()) {
			double mid = SimMath.wrap(camFrom + camDelta / 2);
			ports.exchangeIntake(charge, v1, spec.intakeFlow() * intakeCurve.at(mid) * intakeFactor * governedAir(), dt,
				intakePressure, intakeTemperature, combustion.evaporativeCooling());
			ports.exchangeExhaust(charge, v1, spec.exhaustFlow() * exhaustCurve.at(mid) * exhaustFactor, dt, exhaustPressure,
				current, last);
		}

		// Rule 4: injection (triggered by the cam), ignition, combustion
		if (timed() && SimMath.crosses(camFrom, camDelta, profile.injection()))
			combustion.inject();
		if (timed() && SimMath.crosses(camFrom, camDelta, profile.spark()))
			combustion.spark();
		if (combustion.hasFuel())
			combustion.combust(dt);
		if (combustion.hasFuel() && !combustion.burning())
			current.compressionTemp = Math.max(current.compressionTemp, charge.temperature);
		current.peakPressure = Math.max(current.peakPressure, charge.pressure(v1));
		current.work += work;

		// A cycle ends when the cam starts the next intake stroke, or after two revolutions if the cam isn't turning
		travelSinceCycle += delta;
		boolean camWrapped = timed() && SimMath.crosses(camFrom, camDelta, 0);
		if (camWrapped || travelSinceCycle >= SimMath.CYCLE)
			finishCycle();
		return work;
	}

	/** How open the governor leaves the intake: a spark engine is throttled, a compression engine breathes freely. */
	private double governedAir() {
		return ignition.installed() ? Governor.opening(governor) : 1;
	}

	/** Share of the shot the governor lets through. */
	double governedFuel() {
		return ignition.installed() ? Throttle.fuelTrim(Governor.opening(governor)) : governor;
	}

	/** The shot Fuel Metering's 100% means for {@code fuel}, mB: a thin fuel needs a bigger one to match the same air. */
	public double usualShot(FuelSpec fuel) {
		return spec.maxFuelPerShot() * fuel.injectorFlow();
	}

	/** The most the injector can deliver of {@code fuel} in one shot, mB. */
	public double injectorLimit(FuelSpec fuel) {
		return usualShot(fuel) * INJECTOR_CAPACITY * spec.injectorFlow();
	}

	private void finishCycle() {
		if (current.fuelInjected <= 0)
			// A MAF sensor that cut the shot to nothing did so because there was nothing to burn it with
			current.result = current.mafCut > 0 && current.freshAirAtInjection < MIN_FRESH_AIR ? CycleResult.NO_AIR
				: CycleResult.NO_FUEL;
		else if (current.fuelBurned > 0)
			current.result = CycleResult.FIRED;
		else if (current.freshAirAtInjection < MIN_FRESH_AIR)
			current.result = CycleResult.NO_AIR;
		else if (current.deadSparks > 0 && current.sparks == 0)
			current.result = CycleResult.NO_SPARK;
		else if (current.flameOut < 0)
			current.result = CycleResult.TOO_LEAN;
		else if (current.flameOut == 2)
			current.result = CycleResult.FOULED;
		else if (current.flameOut > 0)
			current.result = CycleResult.TOO_RICH;
		else
			current.result = CycleResult.NO_IGNITION;

		soiling.endCycle(current, ignition.installed(), combustion.burningFuel());

		last.copyFrom(current);
		current.reset();
		combustion.endCycle();
		travelSinceCycle = 0;
	}
}
