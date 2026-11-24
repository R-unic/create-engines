package com.createengines.engine.sim;

/**
 * How one dynamometer run compares with the one before it: the figures a tuner reads off two curves. Pure Java, so it can
 * be tested without the game.
 *
 * @param torqueDelta    change in peak torque, SU per RPM
 * @param powerDelta     change in peak power, SU
 * @param peakShift      change in the speed of peak power, RPM
 * @param bandDelta      change in the width of the powerband, RPM
 * @param torquePercent  change in peak torque, percent of the old peak
 * @param powerPercent   change in peak power, percent of the old peak
 */
public record RunComparison(float torqueDelta, float powerDelta, float peakShift, float bandDelta, float torquePercent,
							float powerPercent) {

	/** Share of the peak power the powerband is the speeds above. */
	public static final float POWERBAND = 0.9f;
	/** Percent below which two peaks count as the same. */
	public static final float SAME = 0.5f;

	/** Compares {@code now} against {@code before}, each a curve of torque (SU per RPM) at the speeds in {@code rpm}. */
	public static RunComparison between(float[] rpmBefore, float[] torqueBefore, float[] rpmNow, float[] torqueNow) {
		Peaks before = Peaks.of(rpmBefore, torqueBefore);
		Peaks now = Peaks.of(rpmNow, torqueNow);
		return new RunComparison(now.torque - before.torque, now.power - before.power, now.powerRpm - before.powerRpm,
			now.band - before.band, percent(now.torque, before.torque), percent(now.power, before.power));
	}

	private static float percent(float now, float before) {
		return before <= 0 ? 0 : (now - before) / before * 100;
	}

	/** Whether the two runs are so alike that the dyno has nothing to say about the change. */
	public boolean same() {
		return Math.abs(torquePercent) < SAME && Math.abs(powerPercent) < SAME && Math.abs(peakShift) < 1
			&& Math.abs(bandDelta) < 1;
	}

	private record Peaks(float torque, float power, float powerRpm, float band) {

		static Peaks of(float[] rpm, float[] torque) {
			int n = Math.min(rpm.length, torque.length);
			if (n == 0)
				return new Peaks(0, 0, 0, 0);
			float peakTorque = 0;
			int peakPower = 0;
			for (int i = 0; i < n; i++) {
				peakTorque = Math.max(peakTorque, torque[i]);
				if (torque[i] * rpm[i] > torque[peakPower] * rpm[peakPower])
					peakPower = i;
			}
			float power = torque[peakPower] * rpm[peakPower];
			int low = peakPower;
			int high = peakPower;
			while (low > 0 && torque[low - 1] * rpm[low - 1] >= POWERBAND * power)
				low--;
			while (high < n - 1 && torque[high + 1] * rpm[high + 1] >= POWERBAND * power)
				high++;
			return new Peaks(peakTorque, power, rpm[peakPower], rpm[high] - rpm[low]);
		}
	}
}
