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
	/**
	 * Sources that are not cylinders but breathe through the engine's networks like one ({@link Breather}), and were attached
	 * to a port: they get the same pressures a cylinder on that network does, and are not given the engine-wide average.
	 */
	final List<Breather> attached = new ArrayList<>();
	/** Kept and emptied each tick rather than made new: this runs for every engine every tick. */
	private final Parallel intake = new Parallel();
	private final Parallel exhaust = new Parallel();
	/** Ambient air density, 1 at sea level: what every cylinder's intake pressure starts from. */
	double density = 1;
	/** The density the cylinders were last given with no compressor to set their pressure, so an unchanged one is left alone. */
	private double givenDensity = 1;

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
				if (density != givenDensity)
					cylinder.intakePressure = density;
			}
			for (int attachedI = 0, attachedN = attached.size(); attachedI < attachedN; attachedI++) {
				Breather source = attached.get(attachedI);
				// Always written: a source's own starting values are its business, and there are few of these
				source.setIntakeTemperature(cool);
				source.setIntakePressure(density);
			}
			givenDensity = density;
			return;
		}
		for (int cylinderI = 0, cylinderN = cylinders.size(); cylinderI < cylinderN; cylinderI++) {
			CylinderModel cylinder = cylinders.get(cylinderI);
			cylinder.intakePressure = density;
			givenDensity = density;
			cylinder.intakeTemperature = 1;
			cylinder.exhaustPressure = 1;
		}
		for (int attachedI = 0, attachedN = attached.size(); attachedI < attachedN; attachedI++) {
			Breather source = attached.get(attachedI);
			source.setIntakePressure(density);
			source.setIntakeTemperature(1);
			source.setExhaustPressure(1);
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
				Breather cylinder = turbo.compressor().get(cylinderI);
				if (turbo.parallelCompressor)
					intake.add(cylinder, pressure, temperature);
				else {
					cylinder.setIntakePressure(cylinder.intakePressure() * pressure);
					cylinder.setIntakeTemperature(cylinder.intakeTemperature() * temperature);
				}
			}
			double back = (turbo.backPressure() - 1) * turbine;
			for (int cylinderI = 0, cylinderN = turbo.turbine().size(); cylinderI < cylinderN; cylinderI++) {
				Breather cylinder = turbo.turbine().get(cylinderI);
				if (turbo.parallelTurbine)
					exhaust.add(cylinder, back, 0);
				else
					cylinder.setExhaustPressure(cylinder.exhaustPressure() + back);
			}
		}
		for (int blowerI = 0, blowerN = superchargers.size(); blowerI < blowerN; blowerI++) {
			Supercharger blower = superchargers.get(blowerI);
			double pressure = 1 + blower.boost() * strength;
			double temperature = Tuning.blend(blower.chargeTemperature(), strength);
			for (int cylinderI = 0, cylinderN = blower.compressor().size(); cylinderI < cylinderN; cylinderI++) {
				Breather cylinder = blower.compressor().get(cylinderI);
				if (blower.parallel)
					intake.add(cylinder, pressure, temperature);
				else {
					cylinder.setIntakePressure(cylinder.intakePressure() * pressure);
					cylinder.setIntakeTemperature(cylinder.intakeTemperature() * temperature);
				}
			}
		}
		intake.forEach((cylinder, pressure, temperature) -> {
			cylinder.setIntakePressure(cylinder.intakePressure() * pressure);
			cylinder.setIntakeTemperature(cylinder.intakeTemperature() * temperature);
		});
		exhaust.forEach((cylinder, back, $) -> cylinder.setExhaustPressure(cylinder.exhaustPressure() + back));
		for (int coolerI = 0, coolerN = intercoolers.size(); coolerI < coolerN; coolerI++) {
			Intercooler cooler = intercoolers.get(coolerI);
			cooler.cool();
		}
		if (cool != 1)
			for (int cylinderI = 0, cylinderN = cylinders.size(); cylinderI < cylinderN; cylinderI++) {
				CylinderModel cylinder = cylinders.get(cylinderI);
				cylinder.intakeTemperature *= cool;
			}
		if (cool != 1)
			for (int attachedI = 0, attachedN = attached.size(); attachedI < attachedN; attachedI++) {
				Breather source = attached.get(attachedI);
				source.setIntakeTemperature(source.intakeTemperature() * cool);
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

	/**
	 * Running averages of what several parallel stages make for each cylinder they serve. The slots are kept from tick to
	 * tick and only their sums are zeroed: an engine with stages side by side builds this every tick, and a map and an
	 * array per cylinder made new each time was over a kilobyte of garbage per engine per tick.
	 */
	private static final class Parallel {
		/** Made when the first stage in parallel is added: most engines have none. */
		private Map<Breather, Slot> slots;
		/** The slots added to since the last {@link #clear}, in the order they were first added to. */
		private final List<Slot> active = new ArrayList<>();

		void clear() {
			for (int i = 0, n = active.size(); i < n; i++)
				active.get(i).count = 0;
			active.clear();
		}

		void add(Breather cylinder, double first, double second) {
			if (slots == null)
				slots = new IdentityHashMap<>();
			Slot slot = slots.get(cylinder);
			if (slot == null) {
				slot = new Slot(cylinder);
				slots.put(cylinder, slot);
			}
			if (slot.count == 0) {
				slot.first = 0;
				slot.second = 0;
				active.add(slot);
			}
			slot.first += first;
			slot.second += second;
			slot.count++;
		}

		void forEach(Average average) {
			for (int i = 0, n = active.size(); i < n; i++) {
				Slot slot = active.get(i);
				average.accept(slot.cylinder, slot.first / slot.count, slot.second / slot.count);
			}
		}

		private static final class Slot {
			final Breather cylinder;
			double first;
			double second;
			int count;

			Slot(Breather cylinder) {
				this.cylinder = cylinder;
			}
		}

		interface Average {
			void accept(Breather cylinder, double first, double second);
		}
	}
}
