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
 * @param roughness        share of the engine's rating that its uneven running takes off, 0 to 0.4 (see Roughness)
 * @param condition        how fresh the oil is, 0 (spent) to 1 (new)
 * @param temperature      how hot the block is: 0 ambient, 0.7 a warm engine on a steady load
 * @param coolantRate      coolant being spent carrying heat away, mB per second; 0 with none
 * @param pilot            a pilot shot of fuel is lighting the charge in at least one cylinder
 * @param filtered         an oil filter is fitted in an oil pan
 * @param flex             share of the rating a long crankshaft loses to torsional flex, 0 to 0.8 (see Torsion)
 * @param longestLine      throws on the engine's longest crank line
 * @param health           how the engine is running overall (see Diagnosis)
 * @param cause            the one thing most responsible for it, or NONE
 */
public record EngineSummary(EngineState state, float rpm, float availableTorque, float loadTorque,
							float fuelPerMinute, float ripple, int firing, int cylinders, float inertia, float lubrication,
							boolean starterEngaged, float[] firingAngles, int lines, float thinning, float roughness,
							float condition, float temperature, float coolantRate, boolean pilot, boolean filtered,
							float flex, int longestLine, Diagnosis.Health health, Diagnosis.Cause cause) {

	public static final EngineSummary EMPTY =
		new EngineSummary(EngineState.STALLED, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, new float[0], 1, 0, 0, 1, 0, 0,
			false, false, 0, 0, Diagnosis.Health.HEALTHY, Diagnosis.Cause.NONE);

	public EngineSummary(EngineState state, float rpm, float availableTorque, float loadTorque, float fuelPerMinute,
						 float ripple, int firing, int cylinders, float inertia, float lubrication,
						 boolean starterEngaged, float[] firingAngles) {
		this(state, rpm, availableTorque, loadTorque, fuelPerMinute, ripple, firing, cylinders, inertia, lubrication,
			starterEngaged, firingAngles, 1, 0, 0, 1, 0, 0, false, false, 0, 0, Diagnosis.Health.HEALTHY, Diagnosis.Cause.NONE);
	}

	/** Whether the block is still giving up its heat with the crank stopped. */
	public boolean cooling() {
		boolean turning = state == EngineState.RUNNING || state == EngineState.OVERLOADED || state == EngineState.CRANKING
			|| state == EngineState.COASTING;
		return com.createengines.engine.sim.Thermal.isCooling(temperature, turning);
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
		tag.putFloat("Roughness", roughness);
		tag.putFloat("Condition", condition);
		tag.putFloat("Temperature", temperature);
		tag.putFloat("Coolant", coolantRate);
		tag.putBoolean("Pilot", pilot);
		tag.putBoolean("Filtered", filtered);
		tag.putFloat("Flex", flex);
		tag.putInt("LongestLine", longestLine);
		tag.putInt("Health", health.ordinal());
		tag.putInt("Cause", cause.ordinal());
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
		Diagnosis.Health[] healths = Diagnosis.Health.values();
		Diagnosis.Cause[] causes = Diagnosis.Cause.values();
		return new EngineSummary(states[Math.clamp(tag.getInt("State"), 0, states.length - 1)], tag.getFloat("Rpm"),
			tag.getFloat("Torque"), tag.getFloat("Load"), tag.getFloat("Fuel"), tag.getFloat("Ripple"),
			tag.getInt("Firing"), tag.getInt("Cylinders"), tag.getFloat("Inertia"), tag.getFloat("Lubrication"),
			tag.getBoolean("Starter"),
			firingAngles, Math.max(1, tag.getInt("Lines")), tag.getFloat("Thinning"), tag.getFloat("Roughness"),
			tag.contains("Condition") ? tag.getFloat("Condition") : 1, tag.getFloat("Temperature"),
			tag.getFloat("Coolant"), tag.getBoolean("Pilot"), tag.getBoolean("Filtered"), tag.getFloat("Flex"),
			tag.getInt("LongestLine"), healths[Math.clamp(tag.getInt("Health"), 0, healths.length - 1)],
			causes[Math.clamp(tag.getInt("Cause"), 0, causes.length - 1)]);
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
			|| Math.abs(lubrication - other.lubrication) >= 0.02 || Math.abs(roughness - other.roughness) >= 0.02
			|| Math.abs(condition - other.condition) >= 0.02 || Math.abs(temperature - other.temperature) >= 0.03
			|| Math.abs(coolantRate - other.coolantRate) >= 0.1 || pilot != other.pilot || filtered != other.filtered
			|| Math.abs(flex - other.flex) >= 0.01 || longestLine != other.longestLine || health != other.health
			|| cause != other.cause
			|| !Arrays.equals(firingAngles, other.firingAngles);
	}

	private static boolean relativeChange(float a, float b) {
		return Math.abs(a - b) > Math.max(0.5f, 0.05f * Math.abs(b));
	}
}
