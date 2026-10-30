package com.createengines.engine;

import java.util.Arrays;

import net.minecraft.nbt.CompoundTag;

/**
 * What the engine looks like from outside: the figures goggles show on any of its crankshaft blocks. Computed on
 * the server and synced on the primary crankshaft block.
 *
 * @param availableTorque  SU per RPM the engine could deliver at full fuel
 * @param loadTorque       SU per RPM the network is asking for
 * @param fuelPerMinute    mB per minute being injected
 * @param lubrication      how well oiled the moving parts are, 0 (dry) to 1, counting the oil fuel has thinned
 * @param thinning         share of the oil's strength unburned fuel has taken, 0 to 0.5
 * @param ripple           speed variation over a cycle, fraction of mean speed
 * @param firingAngles     crank angles (0-720) at which each cam-timed cylinder fires, sorted
 * @param lines            how many crankshafts are geared together into this engine
 */
public record EngineSummary(EngineState state, float rpm, float availableTorque, float loadTorque,
							float fuelPerMinute, float ripple, int firing, int cylinders, float inertia, float lubrication,
							boolean starterEngaged, float[] firingAngles, int lines, float thinning) {

	public static final EngineSummary EMPTY =
		new EngineSummary(EngineState.STALLED, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, new float[0], 1, 0);

	public EngineSummary(EngineState state, float rpm, float availableTorque, float loadTorque, float fuelPerMinute,
						 float ripple, int firing, int cylinders, float inertia, float lubrication,
						 boolean starterEngaged, float[] firingAngles) {
		this(state, rpm, availableTorque, loadTorque, fuelPerMinute, ripple, firing, cylinders, inertia, lubrication,
			starterEngaged, firingAngles, 1, 0);
	}

	public CompoundTag write() {
		CompoundTag tag = new CompoundTag();
		tag.putInt("State", state.ordinal());
		tag.putFloat("Rpm", rpm);
		tag.putFloat("Torque", availableTorque);
		tag.putFloat("Load", loadTorque);
		tag.putFloat("Fuel", fuelPerMinute);
		tag.putFloat("Ripple", ripple);
		tag.putInt("Firing", firing);
		tag.putInt("Cylinders", cylinders);
		tag.putFloat("Inertia", inertia);
		tag.putFloat("Lubrication", lubrication);
		tag.putFloat("Thinning", thinning);
		tag.putInt("Lines", lines);
		tag.putBoolean("Starter", starterEngaged);
		int[] angles = new int[firingAngles.length];
		for (int i = 0; i < angles.length; i++)
			angles[i] = Math.round(firingAngles[i] * 10);
		tag.putIntArray("Firings", angles);
		return tag;
	}

	public static EngineSummary read(CompoundTag tag) {
		int[] angles = tag.getIntArray("Firings");
		float[] firingAngles = new float[angles.length];
		for (int i = 0; i < angles.length; i++)
			firingAngles[i] = angles[i] / 10f;
		EngineState[] states = EngineState.values();
		return new EngineSummary(states[Math.clamp(tag.getInt("State"), 0, states.length - 1)], tag.getFloat("Rpm"),
			tag.getFloat("Torque"), tag.getFloat("Load"), tag.getFloat("Fuel"), tag.getFloat("Ripple"),
			tag.getInt("Firing"), tag.getInt("Cylinders"), tag.getFloat("Inertia"), tag.getFloat("Lubrication"),
			tag.getBoolean("Starter"),
			firingAngles, Math.max(1, tag.getInt("Lines")), tag.getFloat("Thinning"));
	}

	/** Gaps between consecutive firings around the 720° cycle. */
	public float[] firingIntervals() {
		if (firingAngles.length < 2)
			return new float[0];
		float[] intervals = new float[firingAngles.length];
		for (int i = 0; i < firingAngles.length; i++) {
			float next = firingAngles[(i + 1) % firingAngles.length];
			float gap = next - firingAngles[i];
			intervals[i] = gap <= 0 ? gap + 720 : gap;
		}
		return intervals;
	}

	/** Whether the sync-worthy figures differ noticeably. */
	public boolean differsFrom(EngineSummary other) {
		return state != other.state || firing != other.firing || cylinders != other.cylinders || lines != other.lines
			|| starterEngaged != other.starterEngaged || Math.abs(rpm - other.rpm) >= 1
			|| relativeChange(availableTorque, other.availableTorque) || relativeChange(loadTorque, other.loadTorque)
			|| relativeChange(fuelPerMinute, other.fuelPerMinute) || Math.abs(ripple - other.ripple) > 0.01
			|| Math.abs(lubrication - other.lubrication) >= 0.02
			|| !Arrays.equals(firingAngles, other.firingAngles);
	}

	private static boolean relativeChange(float a, float b) {
		return Math.abs(a - b) > Math.max(0.5f, 0.05f * Math.abs(b));
	}
}
