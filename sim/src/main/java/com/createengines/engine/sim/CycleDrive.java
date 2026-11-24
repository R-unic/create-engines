package com.createengines.engine.sim;

import org.jetbrains.annotations.Nullable;

/**
 * How the crank sets a {@link CylinderModel}'s place in its own four-stroke cycle, and how that cylinder is stepped:
 * the one thing that differs between a piston under a camshaft ({@link #PISTON}) and a Wankel chamber whose ports are cut
 * in the housing ({@link RotaryDrive}). It replaces the flag the simulation used to test on every step.
 * <p>
 * A drive holds no per-cylinder state beyond fixed numbers, so one may be shared.
 */
interface CycleDrive {

	/** A piston: its cycle is the crank's angle past its own top dead centre, and a camshaft (if any) times its valves. */
	CycleDrive PISTON = new PistonDrive();

	/** The cylinder's angle past top dead centre, from the crank's two angles; {@code tdc} is where it reaches it. */
	double cycleAngle(double rotorAngle, double rotaryAngle, int direction, double tdc);

	/** Degrees of the cylinder's own cycle for each degree the shaft turns. */
	double scale();

	/** Shaft degrees one four-stroke cycle takes. */
	double cycleDegrees();

	/** Whether the valves and injection follow the cylinder's own cycle, fixed by ports and not by a camshaft. */
	boolean ported();

	/** Whether something fixes when the valves open and the injector fires. */
	boolean timed(@Nullable Camshaft camshaft);

	/** Whether valve timing is locked to the shaft. */
	boolean phaseLocked(@Nullable Camshaft camshaft);

	/** Steps {@code cylinder} by {@code delta} crank degrees in {@code dt} seconds and returns the work it did. */
	double advance(CylinderModel cylinder, double angle, double rotaryAngle, int direction, double delta, double dt);

	final class PistonDrive implements CycleDrive {

		@Override
		public double cycleAngle(double rotorAngle, double rotaryAngle, int direction, double tdc) {
			return SimMath.wrap(rotorAngle - direction * tdc);
		}

		@Override
		public double scale() {
			return 1;
		}

		@Override
		public double cycleDegrees() {
			return SimMath.CYCLE;
		}

		@Override
		public boolean ported() {
			return false;
		}

		@Override
		public boolean timed(@Nullable Camshaft camshaft) {
			return camshaft != null;
		}

		@Override
		public boolean phaseLocked(@Nullable Camshaft camshaft) {
			return camshaft != null && camshaft.isPhaseLocked();
		}

		@Override
		public double advance(CylinderModel cylinder, double angle, double rotaryAngle, int direction, double delta,
							  double dt) {
			Camshaft camshaft = cylinder.camshaft();
			double camDelta = camshaft == null ? 0 : 2 * camshaft.ratio * delta;
			return cylinder.step(cylinder.pistonAngle(angle, direction), delta, cylinder.camCycleAngle(), camDelta, dt);
		}
	}

	/**
	 * One chamber of a Wankel rotor: it is in step with the shaft by the housing's gears, so its ports follow its own
	 * cycle, which is the shaft's angle in {@link CylinderModel#ROTARY_CYCLE} degrees scaled by {@link #scale()} and
	 * set ahead by {@code cycleOffset}, the chamber's place on the rotor.
	 */
	final class RotaryDrive implements CycleDrive {
		private final double cycleOffset;

		RotaryDrive(double cycleOffset) {
			this.cycleOffset = cycleOffset;
		}

		@Override
		public double cycleAngle(double rotorAngle, double rotaryAngle, int direction, double tdc) {
			double shaft = SimMath.wrap(rotaryAngle - direction * tdc, CylinderModel.ROTARY_CYCLE);
			return SimMath.wrap(shaft * CylinderModel.ROTARY_SCALE + cycleOffset);
		}

		@Override
		public double scale() {
			return CylinderModel.ROTARY_SCALE;
		}

		@Override
		public double cycleDegrees() {
			return CylinderModel.ROTARY_CYCLE;
		}

		@Override
		public boolean ported() {
			return true;
		}

		@Override
		public boolean timed(@Nullable Camshaft camshaft) {
			return true;
		}

		@Override
		public boolean phaseLocked(@Nullable Camshaft camshaft) {
			return true;
		}

		@Override
		public double advance(CylinderModel cylinder, double angle, double rotaryAngle, int direction, double delta,
							  double dt) {
			double cycle = cylinder.pistonAngle(angle, rotaryAngle, direction);
			double cycleDelta = delta * scale();
			return cylinder.step(cycle, cycleDelta, cycle, cycleDelta, dt);
		}
	}
}
