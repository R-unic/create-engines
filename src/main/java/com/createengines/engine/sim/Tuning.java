package com.createengines.engine.sim;

/**
 * Server settings the simulation reads, as plain fields. The config spec can't be read before it has loaded, which a
 * unit test never does, so {@code CEConfig} copies its values here whenever it loads or reloads, and the defaults are
 * the shipped balance.
 */
public final class Tuning {

	/** Scales how far each precision part moves a cylinder from the crude one: 0 makes them cosmetic, 1 is as designed. */
	public static volatile double precisionBonus = 1;
	/** Scales the boost every turbocharger and supercharger makes. */
	public static volatile double boostStrength = 1;
	/** Scales a rotor chamber's swept volume, and so its power and the fuel it can burn. */
	public static volatile double rotaryPower = 1;
	/** Fuel drawn from the tanks for each unit the engine burns; combustion itself is unchanged. */
	public static volatile double fuelConsumption = 1;
	/** Scales the rating a long crankshaft loses to torsional flex. 0 removes the loss, 1 is as designed. */
	public static volatile double torsionalFlex = 1;
	/** Scales how much better a cool engine breathes. 0 removes the bonus, 1 is as designed. */
	public static volatile double coolingBonus = 1;
	/** Scales the heat a heat exchanger takes from the exhaust. */
	public static volatile double heatRecovery = 1;

	public static volatile boolean forcedInduction = true;
	public static volatile boolean sparkFouling = true;
	public static volatile boolean oilDilution = true;
	/** Whether engine oil goes off with use. Off, it stays new and nothing needs changing. */
	public static volatile boolean oilAgeing = true;
	/** Whether an engine has a temperature: off, it never warms up or cools and neither penalty nor bonus applies. */
	public static volatile boolean engineTemperature = true;
	/** Whether a cold battery gives less and a cold engine makes each spark cost more, see {@link BatteryChemistry}. */
	public static volatile boolean batteryCold = true;
	/** Whether a starter draws charge from the engine's batteries while it turns a spark engine over, see {@link BatteryChemistry}. */
	public static volatile boolean crankingDraw = true;
	/** Whether a pilot injection can light fuels compression alone can't. */
	public static volatile boolean pilotInjection = true;
	public static volatile boolean rotaryEngines = true;
	public static volatile boolean heatRecoveryEnabled = true;
	/** Whether the air thins with height, see {@link Altitude}. */
	public static volatile boolean altitudeThinning = true;
	/** Whether, with Sable installed, the air's thickness is Sable's own air pressure where the engine is, see {@link Altitude}. */
	public static volatile boolean sableAirPressure = true;
	/** Whether liquid fuel lying in the world burns when lit, see {@code FuelLiquidBlock}. */
	public static volatile boolean fuelFire = true;
	/** Whether a spill's fire follows the fuel: gasoline and methanol catch and burn off fast, diesel-class fuels slowly, see {@link SpillFire}. */
	public static volatile boolean fuelFireByVolatility = true;
	/** Whether setting one head's fuel metering sets every other head of its engine to the same figure, see {@code MeteringFollow}. */
	public static volatile boolean meterAllHeads = false;
	/** Height from which the air thins when no pressure is read from Sable, see {@link Altitude#START}. */
	public static volatile double altitudeStart = Altitude.START;
	/** Blocks of climb per e-fold of thinning when no pressure is read from Sable, see {@link Altitude#SCALE}. */
	public static volatile double altitudeScale = Altitude.SCALE;
	/** The thinnest air an engine is given, as a share of sea level, see {@link Altitude#FLOOR}. */
	public static volatile double altitudeFloor = Altitude.FLOOR;

	private Tuning() {
	}

	/** A multiplier of {@code base} that is applied only as far as {@code strength} says: 1 + (base - 1) * strength. */
	public static double blend(double base, double strength) {
		return 1 + (base - 1) * strength;
	}

	/** Puts every setting back to the shipped balance. */
	public static void reset() {
		precisionBonus = 1;
		boostStrength = 1;
		rotaryPower = 1;
		fuelConsumption = 1;
		heatRecovery = 1;
		coolingBonus = 1;
		torsionalFlex = 1;
		forcedInduction = true;
		sparkFouling = true;
		oilDilution = true;
		oilAgeing = true;
		engineTemperature = true;
		batteryCold = true;
		crankingDraw = true;
		pilotInjection = true;
		rotaryEngines = true;
		heatRecoveryEnabled = true;
		altitudeThinning = true;
		sableAirPressure = true;
		fuelFire = true;
		fuelFireByVolatility = true;
		meterAllHeads = false;
		altitudeStart = Altitude.START;
		altitudeScale = Altitude.SCALE;
		altitudeFloor = Altitude.FLOOR;
	}
}
