package com.createengines.engine.sim;

/**
 * The figures stamped on a Maker's Plate from a measured curve: where torque and power peak, and at what speed. Kept
 * free of Minecraft so the tests can reach it. Torque is stress capacity per RPM and power is that times the RPM (SU).
 *
 * @param peakTorque    torque at its highest, SU per RPM
 * @param torqueRpm     the speed it comes at
 * @param peakPower     power at its highest, SU
 * @param powerRpm      the speed it comes at
 */
public record PlateFigures(float peakTorque, int torqueRpm, float peakPower, int powerRpm) {

	/** What a plate holds before it has been stamped. */
	public static final PlateFigures NONE = new PlateFigures(0, 0, 0, 0);

	/**
	 * The figures for a curve, or {@code null} if it is not one worth stamping: empty, ragged, or with no power in it.
	 */
	public static PlateFigures of(float[] rpm, float[] torque) {
		if (rpm == null || torque == null || rpm.length == 0 || rpm.length != torque.length)
			return null;
		int bestTorque = 0;
		int bestPower = 0;
		for (int i = 0; i < rpm.length; i++) {
			if (Float.isNaN(rpm[i]) || Float.isNaN(torque[i]))
				return null;
			if (torque[i] > torque[bestTorque])
				bestTorque = i;
			if (torque[i] * rpm[i] > torque[bestPower] * rpm[bestPower])
				bestPower = i;
		}
		float power = torque[bestPower] * rpm[bestPower];
		if (!(power > 0) || !(torque[bestTorque] > 0))
			return null;
		return new PlateFigures(tenths(torque[bestTorque]), Math.round(rpm[bestTorque]), tenths(power), Math.round(rpm[bestPower]));
	}

	/** Whole tenths, which is all a plate keeps, so a plate that is read back equals the figures it was given. */
	private static float tenths(float value) {
		return Math.round(value * 10) / 10f;
	}

	public boolean stamped() {
		return peakPower > 0;
	}

	/** The figures as the plate stores them: whole tenths, so that a plate reads back what it was given. */
	public int[] pack() {
		return new int[] {Math.round(peakTorque * 10), torqueRpm, Math.round(peakPower * 10), powerRpm};
	}

	public static PlateFigures unpack(int[] packed) {
		if (packed == null || packed.length != 4)
			return NONE;
		return new PlateFigures(Math.max(0, packed[0]) / 10f, Math.max(0, packed[1]), Math.max(0, packed[2]) / 10f,
			Math.max(0, packed[3]));
	}

	/** A name cut to what a plate has room for: control characters dropped, runs of blanks closed up, then cut short. */
	public static String cleanName(String raw, int limit) {
		if (raw == null)
			return "";
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < raw.length() && out.length() < limit; i++) {
			char c = raw.charAt(i);
			if (c < 32 || c == 127)
				c = ' ';
			if (c == ' ' && (out.length() == 0 || out.charAt(out.length() - 1) == ' '))
				continue;
			out.append(c);
		}
		return out.toString()
			.stripTrailing();
	}
}
