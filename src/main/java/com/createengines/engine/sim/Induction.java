package com.createengines.engine.sim;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The forced induction of one engine: its turbochargers, superchargers and intercoolers, and how together they set the
 * air each cylinder breathes. Moved out of {@link EngineSimulation}, which owns the crank and the cylinders and hands
 * this the speed and the cylinders once a tick.
 */
final class Induction {

	final List<Turbocharger> turbos = new ArrayList<>();
	final List<Supercharger> superchargers = new ArrayList<>();
	final List<Intercooler> intercoolers = new ArrayList<>();
	/** Kept and emptied each tick rather than made new: this runs for every engine every tick. */
	private final Parallel intake = new Parallel();
	private final Parallel exhaust = new Parallel();

	/**
	 * Lets each turbo spool and each blower turn on this tick, then sets every cylinder's air. They spin down even while
	 * the crank is stopped.
	 *
	 * @param crankRpm the crank's speed, which a blower geared to it follows
	 * @param cool     the engine's {@link Thermal#chargeFactor()}: a cool engine draws a denser charge
	 */
	void tick(double crankRpm, List<CylinderModel> cylinders, double cool) {
		for (int turboI = 0, turboN = turbos.size(); turboI < turboN; turboI++) {
			Turbocharger turbo = turbos.get(turboI);
			turbo.tick(EngineSimulation.TICK);
		}
		for (int blowerI = 0, blowerN = superchargers.size(); blowerI < blowerN; blowerI++) {
			Supercharger blower = superchargers.get(blowerI);
			if (blower.followsCrank())
				blower.rpm = blower.driveRatio * crankRpm;
			blower.tick(EngineSimulation.TICK);
		}
		apply(cylinders, cool);
	}

	/**
	 * Sets the air every cylinder breathes. The compressors on a cylinder's intake run in turn, so their pressure and
	 * temperature ratios multiply; the intercoolers then take heat out of the result; and the turbines on its exhaust
	 * run each hold it up, so their back-pressures add.
	 */
	private void apply(List<CylinderModel> cylinders, double cool) {
		if (turbos.isEmpty() && superchargers.isEmpty() && intercoolers.isEmpty()) {
			// No compressors, but a cool engine still draws a denser charge
			for (int cylinderI = 0, cylinderN = cylinders.size(); cylinderI < cylinderN; cylinderI++) {
				CylinderModel cylinder = cylinders.get(cylinderI);
				cylinder.intakeTemperature = cool;
			}
			return;
		}
		for (int cylinderI = 0, cylinderN = cylinders.size(); cylinderI < cylinderN; cylinderI++) {
			CylinderModel cylinder = cylinders.get(cylinderI);
			cylinder.intakePressure = 1;
			cylinder.intakeTemperature = 1;
			cylinder.exhaustPressure = 1;
		}
		// Stages in series multiply (or add) straight onto the cylinder; stages in parallel each handle a share of the
		// air, so the cylinder gets their average, whatever the number of them
		intake.clear();
		exhaust.clear();
		// The server can scale boost, or switch forced induction off, in which case the turbines add no back-pressure either
		double strength = Tuning.forcedInduction ? Tuning.boostStrength : 0;
		double turbine = Tuning.forcedInduction ? 1 : 0;
		for (int turboI = 0, turboN = turbos.size(); turboI < turboN; turboI++) {
			Turbocharger turbo = turbos.get(turboI);
			double pressure = 1 + turbo.boost() * strength;
			double temperature = Tuning.blend(turbo.chargeTemperature(), strength);
			for (int cylinderI = 0, cylinderN = turbo.compressor().size(); cylinderI < cylinderN; cylinderI++) {
				CylinderModel cylinder = turbo.compressor().get(cylinderI);
				if (turbo.parallelCompressor)
					intake.add(cylinder, pressure, temperature);
				else {
					cylinder.intakePressure *= pressure;
					cylinder.intakeTemperature *= temperature;
				}
			}
			double back = (turbo.backPressure() - 1) * turbine;
			for (int cylinderI = 0, cylinderN = turbo.turbine().size(); cylinderI < cylinderN; cylinderI++) {
				CylinderModel cylinder = turbo.turbine().get(cylinderI);
				if (turbo.parallelTurbine)
					exhaust.add(cylinder, back, 0);
				else
					cylinder.exhaustPressure += back;
			}
		}
		for (int blowerI = 0, blowerN = superchargers.size(); blowerI < blowerN; blowerI++) {
			Supercharger blower = superchargers.get(blowerI);
			double pressure = 1 + blower.boost() * strength;
			double temperature = Tuning.blend(blower.chargeTemperature(), strength);
			for (int cylinderI = 0, cylinderN = blower.compressor().size(); cylinderI < cylinderN; cylinderI++) {
				CylinderModel cylinder = blower.compressor().get(cylinderI);
				if (blower.parallel)
					intake.add(cylinder, pressure, temperature);
				else {
					cylinder.intakePressure *= pressure;
					cylinder.intakeTemperature *= temperature;
				}
			}
		}
		intake.forEach((cylinder, pressure, temperature) -> {
			cylinder.intakePressure *= pressure;
			cylinder.intakeTemperature *= temperature;
		});
		exhaust.forEach((cylinder, back, $) -> cylinder.exhaustPressure += back);
		for (int coolerI = 0, coolerN = intercoolers.size(); coolerI < coolerN; coolerI++) {
			Intercooler cooler = intercoolers.get(coolerI);
			cooler.cool();
		}
		if (cool != 1)
			for (int cylinderI = 0, cylinderN = cylinders.size(); cylinderI < cylinderN; cylinderI++) {
				CylinderModel cylinder = cylinders.get(cylinderI);
				cylinder.intakeTemperature *= cool;
			}
	}

	/** What {@link #airForSources} last worked out, for the sources that are not cylinders. */
	double sourcePressure = 1;
	double sourceTemperature = 1;

	/**
	 * Works out the air for sources that are not cylinders, which have no intake of their own: the average of what the
	 * cylinders were just given, or, on an engine with no cylinders, what its blowers make in series. A turbo has nothing to
	 * spool it without cylinders' exhaust, and parallel and intercooler settings are about cylinders' ports.
	 */
	void airForSources(List<CylinderModel> cylinders, double cool) {
		int count = cylinders.size();
		if (count > 0) {
			double pressure = 0;
			double temperature = 0;
			for (int cylinderI = 0; cylinderI < count; cylinderI++) {
				CylinderModel cylinder = cylinders.get(cylinderI);
				pressure += cylinder.intakePressure;
				temperature += cylinder.intakeTemperature;
			}
			sourcePressure = pressure / count;
			sourceTemperature = temperature / count;
			return;
		}
		double strength = Tuning.forcedInduction ? Tuning.boostStrength : 0;
		double pressure = 1;
		double temperature = 1;
		for (int blowerI = 0, blowerN = superchargers.size(); blowerI < blowerN; blowerI++) {
			Supercharger blower = superchargers.get(blowerI);
			pressure *= 1 + blower.boost() * strength;
			temperature *= Tuning.blend(blower.chargeTemperature(), strength);
		}
		sourcePressure = pressure;
		sourceTemperature = temperature * cool;
	}

	/** Running averages of what several parallel stages make for each cylinder they serve. */
	private static final class Parallel {
		/** Made when the first stage in parallel is added: most engines have none, and this is built every tick. */
		private Map<CylinderModel, double[]> sums;

		void clear() {
			sums = null;
		}

		void add(CylinderModel cylinder, double first, double second) {
			if (sums == null)
				sums = new IdentityHashMap<>();
			double[] sum = sums.computeIfAbsent(cylinder, $ -> new double[3]);
			sum[0] += first;
			sum[1] += second;
			sum[2]++;
		}

		void forEach(Average average) {
			if (sums != null)
				sums.forEach((cylinder, sum) -> average.accept(cylinder, sum[0] / sum[2], sum[1] / sum[2]));
		}

		interface Average {
			void accept(CylinderModel cylinder, double first, double second);
		}
	}
}
