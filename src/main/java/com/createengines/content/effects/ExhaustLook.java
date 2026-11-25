package com.createengines.content.effects;

import com.createengines.engine.sim.CycleResult;
import com.createengines.engine.sim.FuelSpec;
import com.createengines.engine.sim.Knock;
import com.createengines.engine.sim.Mixture;
import com.createengines.engine.sim.Thermal;

/**
 * What one cylinder's exhaust looks like as it leaves the pipe, worked out from how the cylinder burned and what the
 * engine is doing, so the smoke tells the truth: black when rich, a thin haze when clean, white vapour while the block
 * is cold, a blue cast when fuel has got into the oil, a pale mist of fuel when nothing lit, and a crack of flame when
 * that fuel meets a hot pipe. Kept free of the game so it can be tested.
 *
 * @param puffs    puffs of gas to throw out; 0 means nothing comes out worth seeing
 * @param red      colour of the gas, each part 0 to 1
 * @param alpha    how solid a new puff is
 * @param scale    how big a puff gets (see {@link GasParticleOptions})
 * @param speed    how fast it leaves the pipe, blocks a tick
 * @param lift     how hard it rises once out (see {@link GasParticleOptions})
 * @param lifetime ticks a puff lasts
 * @param flame    chance, 0 to 1, that this puff comes with a lick of flame
 * @param pop      chance, 0 to 1, that it comes with the crack of an afterfire and a burst of flame and sparks
 */
public record ExhaustLook(int puffs, float red, float green, float blue, float alpha, float scale, float speed,
						  float lift, int lifetime, float flame, float pop) {

	public static final ExhaustLook NOTHING = new ExhaustLook(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

	/** Mixture, past rich, at which smoke is as black as it gets. */
	private static final float SOOTIEST = (float) Mixture.RICH_BAND + 0.6f;
	/** Share of the oil's strength fuel has to have taken before the exhaust shows it, and where it shows it most. */
	private static final float HAZE_FROM = 0.08f;
	private static final float HAZE_FULL = 0.4f;
	/** How hard an engine has to be working for a rich one to throw flame. */
	private static final float FLAME_WORK = 0.55f;
	/** Chance that fuel nothing lit goes off in the pipe of an engine that is otherwise firing and warm. */
	private static final float AFTERFIRE = 0.3f;
	/** Chance that a too-lean cylinder spits flame back through its intake on one intake stroke. */
	private static final float SPIT = 0.35f;

	/**
	 * @param result      how the cylinder's last cycle went
	 * @param rich        it fired with fuel left over
	 * @param lean        it fired with air to spare
	 * @param mixture     its fuel-air equivalence ratio, or NaN if nothing was injected
	 * @param load        share of the torque the engine can make that is being asked of it, 0 to a little over 1
	 * @param rev         the engine's speed against 256 RPM, 0 to 1
	 * @param temperature how hot the block is (see {@link Thermal})
	 * @param thinning    share of the oil's strength unburned fuel has taken, 0 to 0.5
	 * @param othersFiring some cylinder of the engine is firing, so its exhaust is hot
	 * @param catching    the engine caught a moment ago and is clearing its throat
	 */
	public record Engine(CycleResult result, boolean rich, boolean lean, float mixture, float load, float rev,
						 float temperature, float thinning, boolean othersFiring, boolean catching) {
	}

	public static ExhaustLook of(Engine e) {
		float rev = clamp(e.rev(), 0, 1);
		if (e.result() == CycleResult.FIRED)
			return burned(e, rev);
		if (unlit(e.result()))
			return unburned(e, rev);
		return NOTHING;
	}

	/** Fuel went in and nothing lit it: it leaves the cylinder as it came. */
	public static boolean unlit(CycleResult result) {
		return result == CycleResult.NO_IGNITION || result == CycleResult.NO_SPARK || result == CycleResult.TOO_LEAN
			|| result == CycleResult.TOO_RICH || result == CycleResult.FOULED;
	}

	private static ExhaustLook burned(Engine e, float rev) {
		float work = clamp(0.25f + 0.75f * e.load(), 0, 1.1f);
		float warm = clamp(e.temperature() / (float) Thermal.NORMAL, 0, 1.2f);
		float grey;
		float alpha;
		float scale;
		int puffs;
		float flame = 0;
		float pop = 0;
		if (e.rich()) {
			float soot = Float.isNaN(e.mixture()) ? 0.6f
				: clamp((e.mixture() - (float) Mixture.RICH_BAND) / (SOOTIEST - (float) Mixture.RICH_BAND), 0.25f, 1);
			grey = 0.2f - 0.15f * soot;
			alpha = 0.6f + 0.3f * soot;
			scale = 1.1f + 0.5f * soot;
			puffs = soot > 0.6f ? 4 : 3;
			if (work >= FLAME_WORK && e.temperature() >= Thermal.COLD_BELOW) {
				flame = clamp(0.1f + 0.2f * soot + 0.55f * soot * clamp((work - FLAME_WORK) / (1 - FLAME_WORK), 0, 1), 0, 0.95f);
				pop = 0.05f * soot;
			}
		} else {
			grey = e.lean() ? 0.62f : 0.44f;
			alpha = (e.lean() ? 0.24f : 0.3f) + 0.3f * work;
			scale = 0.75f + 0.45f * work;
			puffs = work > 0.6f ? 3 : 2;
		}
		float red = grey;
		float green = grey;
		float blue = grey;

		// Fuel in the oil burns with the charge: a blue cast, however clean the mixture
		float haze = clamp((e.thinning() - HAZE_FROM) / (HAZE_FULL - HAZE_FROM), 0, 1);
		if (haze > 0) {
			float mix = 0.65f * haze;
			red += (0.5f - red) * mix;
			green += (0.57f - green) * mix;
			blue += (0.74f - blue) * mix;
			alpha += 0.15f * haze;
		}

		// A cold block: the water in the exhaust condenses as it leaves, white and slow to clear
		float chill = clamp(((float) Thermal.COLD_BELOW - e.temperature()) / (float) Thermal.COLD_BELOW, 0, 1);
		if (chill > 0) {
			float mix = 0.7f * chill;
			red += (0.94f - red) * mix;
			green += (0.94f - green) * mix;
			blue += (0.94f - blue) * mix;
			alpha += 0.2f * chill;
			scale += 0.3f * chill;
		}

		// Catching: everything that pooled in the cylinders and the pipe while it cranked comes out at once
		if (e.catching()) {
			red *= 0.55f;
			green *= 0.55f;
			blue *= 0.55f;
			alpha = Math.max(alpha, 0.8f);
			scale += 0.5f;
			puffs += 3;
		}

		return new ExhaustLook(puffs, red, green, blue, clamp(alpha, 0, 0.95f), scale, 0.05f + 0.07f * rev + 0.04f * work,
			0.2f + 0.45f * warm, Math.round(30 + 14 * scale + 10 * chill), flame, pop);
	}

	private static ExhaustLook unburned(Engine e, float rev) {
		boolean hotPipe = e.othersFiring() && e.temperature() >= Thermal.COLD_BELOW;
		float pop = hotPipe ? AFTERFIRE : 0;
		// A plug caked with soot misses with a black puff: the soot it carries is what comes out with the fuel
		if (e.result() == CycleResult.FOULED)
			return new ExhaustLook(2, 0.07f, 0.07f, 0.07f, 0.85f, 1.2f, 0.05f + 0.05f * rev, 0.2f, 40, 0, pop);
		return new ExhaustLook(2, 0.86f, 0.86f, 0.76f, 0.4f, 1, 0.04f + 0.05f * rev, 0.08f, 36, 0, pop);
	}

	/**
	 * Chance that a cylinder's intake stroke spits a lick of flame back out of its intake mouth. Only a charge too lean
	 * to light does it: it burns slowly and is still alight when the intake valve opens again. Only in a warm engine
	 * with others firing, since it takes a flame to carry back.
	 */
	public static float intakeSpit(CycleResult result, boolean othersFiring, float temperature) {
		if (result != CycleResult.TOO_LEAN || !othersFiring || temperature < Thermal.COLD_BELOW)
			return 0;
		return SPIT;
	}

	/** Whether a cylinder whose exhaust cannot get out pushes its burned gas back out of an intake that is open. */
	public static boolean chuffsBack(boolean exhaustShut, boolean intakeShut) {
		return exhaustShut && !intakeShut;
	}

	/**
	 * Chance, 0 to 0.85, that a firing of a cylinder whose charge boost lit early comes with a rattle and an orange flash
	 * in the exhaust. {@code preignition} is the share of the burn lost to it (see CylinderStatus), 0 when nothing is.
	 */
	public static float preignitionRattle(float preignition) {
		if (preignition < 0.01f)
			return 0;
		return 0.25f + 0.6f * clamp(preignition / (float) FuelSpec.PREIGNITION_MAX_LOSS, 0, 1);
	}

	/**
	 * Chance, 0 to 0.5, that a firing of a cylinder knocking loudly enough to hear (wear mode, see {@link Knock}) pings from
	 * its block. Past four cylinders each is heard less often, so a big engine pings no more than a four does.
	 */
	public static float knockPing(float preignition, int cylinders) {
		if (!Knock.audible(preignition))
			return 0;
		float chance = 0.2f + 0.3f * (float) Knock.intensity(preignition);
		return cylinders > 4 ? chance * 4 / cylinders : chance;
	}

	private static float clamp(float value, float low, float high) {
		return Math.max(low, Math.min(high, value));
	}
}
