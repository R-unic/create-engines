package com.createengines.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.createengines.engine.sim.StatusLine;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

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
 * @param muffling         how much of the engine's exhaust goes through a muffler, 0 to 1 (see Muffling): only how
 *                         loud it sounds, never how it runs
 * @param sourceLines      goggle lines from sources that packs or addons added to the engine (see StatusLine)
 * @param oilPerMinute     oil the moving parts are drawing from the pans and heads, mB per minute; 0 when stopped
 * @param failures         how many parts of each kind have failed, packed (see {@code WearFailures}); 0 when none, and always 0 with the wear mode off
 * @param wear             the wear mode's total: the plain mean of how worn every part of the build is, 0 new to 1 spent, or -1 with the mode off
 *                         (see {@code WearTotal})
 * @param sound            what the engine is built of as far as it changes how it sounds (see EngineSound); only the sound
 * @param intakeDirt       share of its air density the engine loses to polluted air at its intakes, 0 unless that is turned on
 */
public record EngineSummary(EngineState state, float rpm, float availableTorque, float loadTorque,
							float fuelPerMinute, float ripple, int firing, int cylinders, float inertia, float lubrication,
							boolean starterEngaged, float[] firingAngles, int lines, float thinning, float roughness,
							float condition, float temperature, float coolantRate, boolean pilot, boolean filtered,
							float flex, int longestLine, Diagnosis.Health health, Diagnosis.Cause cause, float muffling, float boost,
								List<StatusLine> sourceLines, float oilPerMinute, float wear, long failures, EngineSound sound,
								float intakeDirt) {

	/** The summary as it was before engines breathed polluted air: clean. */
	public EngineSummary(EngineState state, float rpm, float availableTorque, float loadTorque, float fuelPerMinute,
						 float ripple, int firing, int cylinders, float inertia, float lubrication,
						 boolean starterEngaged, float[] firingAngles, int lines, float thinning, float roughness,
						 float condition, float temperature, float coolantRate, boolean pilot, boolean filtered,
						 float flex, int longestLine, Diagnosis.Health health, Diagnosis.Cause cause, float muffling, float boost,
						 List<StatusLine> sourceLines, float oilPerMinute, float wear, long failures, EngineSound sound) {
		this(state, rpm, availableTorque, loadTorque, fuelPerMinute, ripple, firing, cylinders, inertia, lubrication,
			starterEngaged, firingAngles, lines, thinning, roughness, condition, temperature, coolantRate, pilot, filtered,
			flex, longestLine, health, cause, muffling, boost, sourceLines, oilPerMinute, wear, failures, sound, 0);
	}

		/** The summary as it was before the build's sound was synced: a plain note. */
		public EngineSummary(EngineState state, float rpm, float availableTorque, float loadTorque, float fuelPerMinute,
							 float ripple, int firing, int cylinders, float inertia, float lubrication,
							 boolean starterEngaged, float[] firingAngles, int lines, float thinning, float roughness,
							 float condition, float temperature, float coolantRate, boolean pilot, boolean filtered,
							 float flex, int longestLine, Diagnosis.Health health, Diagnosis.Cause cause, float muffling, float boost,
							 List<StatusLine> sourceLines, float oilPerMinute, float wear, long failures) {
			this(state, rpm, availableTorque, loadTorque, fuelPerMinute, ripple, firing, cylinders, inertia, lubrication,
				starterEngaged, firingAngles, lines, thinning, roughness, condition, temperature, coolantRate, pilot, filtered,
				flex, longestLine, health, cause, muffling, boost, sourceLines, oilPerMinute, wear, failures, EngineSound.NONE);
		}

		/** The summary as it was before failures were counted: none have failed. */
		public EngineSummary(EngineState state, float rpm, float availableTorque, float loadTorque, float fuelPerMinute,
							 float ripple, int firing, int cylinders, float inertia, float lubrication,
							 boolean starterEngaged, float[] firingAngles, int lines, float thinning, float roughness,
							 float condition, float temperature, float coolantRate, boolean pilot, boolean filtered,
							 float flex, int longestLine, Diagnosis.Health health, Diagnosis.Cause cause, float muffling, float boost,
							 List<StatusLine> sourceLines, float oilPerMinute, float wear) {
			this(state, rpm, availableTorque, loadTorque, fuelPerMinute, ripple, firing, cylinders, inertia, lubrication,
				starterEngaged, firingAngles, lines, thinning, roughness, condition, temperature, coolantRate, pilot, filtered,
				flex, longestLine, health, cause, muffling, boost, sourceLines, oilPerMinute, wear, 0);
		}

		/** The summary as it was before the wear total: there is none. */
		public EngineSummary(EngineState state, float rpm, float availableTorque, float loadTorque, float fuelPerMinute,
							 float ripple, int firing, int cylinders, float inertia, float lubrication,
							 boolean starterEngaged, float[] firingAngles, int lines, float thinning, float roughness,
							 float condition, float temperature, float coolantRate, boolean pilot, boolean filtered,
							 float flex, int longestLine, Diagnosis.Health health, Diagnosis.Cause cause, float muffling, float boost,
							 List<StatusLine> sourceLines, float oilPerMinute) {
			this(state, rpm, availableTorque, loadTorque, fuelPerMinute, ripple, firing, cylinders, inertia, lubrication,
				starterEngaged, firingAngles, lines, thinning, roughness, condition, temperature, coolantRate, pilot, filtered,
				flex, longestLine, health, cause, muffling, boost, sourceLines, oilPerMinute, -1f);
		}

		/** The summary as it was before oil use was shown: no oil is being used. */
		public EngineSummary(EngineState state, float rpm, float availableTorque, float loadTorque, float fuelPerMinute,
							 float ripple, int firing, int cylinders, float inertia, float lubrication,
							 boolean starterEngaged, float[] firingAngles, int lines, float thinning, float roughness,
							 float condition, float temperature, float coolantRate, boolean pilot, boolean filtered,
							 float flex, int longestLine, Diagnosis.Health health, Diagnosis.Cause cause, float muffling, float boost,
							 List<StatusLine> sourceLines) {
			this(state, rpm, availableTorque, loadTorque, fuelPerMinute, ripple, firing, cylinders, inertia, lubrication,
				starterEngaged, firingAngles, lines, thinning, roughness, condition, temperature, coolantRate, pilot, filtered,
				flex, longestLine, health, cause, muffling, boost, sourceLines, 0);
		}

	public static final EngineSummary EMPTY =
		new EngineSummary(EngineState.STALLED, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, new float[0], 1, 0, 0, 1, 0, 0,
			false, false, 0, 0, Diagnosis.Health.HEALTHY, Diagnosis.Cause.NONE, 0, 0, List.of());

	public EngineSummary(EngineState state, float rpm, float availableTorque, float loadTorque, float fuelPerMinute,
						 float ripple, int firing, int cylinders, float inertia, float lubrication,
						 boolean starterEngaged, float[] firingAngles) {
		this(state, rpm, availableTorque, loadTorque, fuelPerMinute, ripple, firing, cylinders, inertia, lubrication,
			starterEngaged, firingAngles, 1, 0, 0, 1, 0, 0, false, false, 0, 0, Diagnosis.Health.HEALTHY, Diagnosis.Cause.NONE, 0, 0, List.of(), 0);
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
		tag.putFloat("OilUse", oilPerMinute);
		if (intakeDirt > 0)
			tag.putFloat("IntakeDirt", intakeDirt);
		if (wear >= 0)
			tag.putFloat("Wear", wear);
		if (failures != 0)
			tag.putLong("Failures", failures);
		tag.putBoolean("Pilot", pilot);
		tag.putBoolean("Filtered", filtered);
		tag.putFloat("Flex", flex);
		tag.putInt("LongestLine", longestLine);
		tag.putInt("Health", health.ordinal());
		tag.putInt("Cause", cause.ordinal());
		tag.putFloat("Muffling", muffling);
		tag.putFloat("Boost", boost);
		tag.putInt("Lines", lines);
		tag.putBoolean("Starter", starterEngaged);
		tag.putIntArray("Firings", FiringAngles.toTenths(firingAngles));
		if (!sound.plain()) {
			tag.putIntArray("SoundOutlets", sound.outlets());
			tag.putIntArray("SoundPipes", sound.pipeHundredths());
			tag.putBoolean("SoundCompression", sound.compression());
			tag.putFloat("SoundPrecision", sound.precision());
			tag.putFloat("SoundEnclosure", sound.enclosure());
			tag.putIntArray("SoundMuffles", sound.muffleHundredths());
			if (sound.rotary())
				tag.putBoolean("SoundRotary", true);
		}
		if (!sourceLines.isEmpty()) {
			ListTag lines = new ListTag();
			for (StatusLine line : sourceLines) {
				CompoundTag entry = new CompoundTag();
				entry.putString("Key", line.key());
				entry.putInt("Tone", line.tone().ordinal());
				ListTag args = new ListTag();
				for (String arg : line.args())
					args.add(StringTag.valueOf(arg));
				entry.put("Args", args);
				lines.add(entry);
			}
			tag.put("Sources", lines);
		}
		return tag;
	}

	public static EngineSummary read(CompoundTag tag) {
		float[] firingAngles = FiringAngles.fromTenths(tag.getIntArray("Firings"));
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
			causes[Math.clamp(tag.getInt("Cause"), 0, causes.length - 1)], tag.getFloat("Muffling"), tag.getFloat("Boost"),
			readSourceLines(tag), tag.getFloat("OilUse"), tag.contains("Wear") ? tag.getFloat("Wear") : -1f,
			Math.max(0, tag.getLong("Failures")), readSound(tag), tag.getFloat("IntakeDirt"));
	}

	/** The build's sound, from a tag that may not have it (an older world, a plain engine): then a plain note. */
	private static EngineSound readSound(CompoundTag tag) {
		if (!tag.contains("SoundOutlets"))
			return EngineSound.NONE;
		return EngineSound.held(tag.getIntArray("SoundOutlets"), tag.getIntArray("SoundPipes"), tag.getBoolean("SoundCompression"),
			tag.getFloat("SoundPrecision"), tag.getFloat("SoundEnclosure"),
			tag.getIntArray("SoundMuffles")).asRotary(tag.getBoolean("SoundRotary"));
	}

	private static List<StatusLine> readSourceLines(CompoundTag tag) {
		ListTag lines = tag.getList("Sources", 10);
		if (lines.isEmpty())
			return List.of();
		StatusLine.Tone[] tones = StatusLine.Tone.values();
		List<StatusLine> read = new ArrayList<>();
		for (int i = 0; i < Math.min(lines.size(), StatusLine.LIMIT); i++) {
			CompoundTag entry = lines.getCompound(i);
			List<String> args = new ArrayList<>();
			ListTag given = entry.getList("Args", 8);
			for (int a = 0; a < Math.min(given.size(), StatusLine.MAX_ARGS); a++)
				args.add(given.getString(a));
			read.add(new StatusLine(entry.getString("Key"), args,
				tones[Math.clamp(entry.getInt("Tone"), 0, tones.length - 1)]));
		}
		return List.copyOf(read);
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
			|| relativeChange(oilPerMinute, other.oilPerMinute) || Math.abs(wear - other.wear) >= 0.01f || failures != other.failures || cause != other.cause || Math.abs(muffling - other.muffling) >= 0.02
			|| !Arrays.equals(firingAngles, other.firingAngles) || !sourceLines.equals(other.sourceLines)
			|| !sound.sameAs(other.sound) || Math.abs(intakeDirt - other.intakeDirt) >= 0.005f;
	}

	private static boolean relativeChange(float a, float b) {
		return Math.abs(a - b) > Math.max(0.5f, 0.05f * Math.abs(b));
	}
}
