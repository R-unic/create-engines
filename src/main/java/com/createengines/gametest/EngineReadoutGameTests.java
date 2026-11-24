package com.createengines.gametest;

import java.util.List;

import com.createengines.CreateEngines;
import com.createengines.content.display.EngineReadout;
import com.createengines.engine.EngineState;
import com.createengines.engine.EngineSummary;
import com.createengines.engine.Diagnosis;
import com.createengines.engine.sim.Thermal;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The figures every Display Link source reads from an engine: horsepower, load, warmth, oil and the status lines. They are the
 * words and numbers a player puts on a nixie tube, so each is checked at its edges.
 */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class EngineReadoutGameTests {

	private static EngineSummary summary(EngineState state, float rpm, float available, float load, float condition, float temperature,
		Diagnosis.Cause cause) {
		return new EngineSummary(state, rpm, available, load, 0, 0, 0, 4, 1, 1, false, new float[0], 1, 0, 0, condition, temperature, 0,
			false, false, 0, 0, Diagnosis.Health.HEALTHY, cause, 0, 0, List.of());
	}

	private static String key(MutableComponent line) {
		return line.getContents() instanceof TranslatableContents translatable ? translatable.getKey() : "(not a translation)";
	}

	@GameTest(template = "empty")
	public static void oilUseSurvivesTheSyncAndAnOldSaveReadsZero(GameTestHelper helper) {
		EngineSummary used = new EngineSummary(EngineState.RUNNING, 100, 10, 5, 0, 0, 1, 1, 1, 1, false, new float[0], 1, 0, 0, 1, 0, 0,
			false, false, 0, 0, Diagnosis.Health.HEALTHY, Diagnosis.Cause.NONE, 0, 0, List.of(), 42.5f);
		helper.assertTrue(EngineSummary.read(used.write()).oilPerMinute() == 42.5f, "oil use is carried in the synced summary");
		helper.assertTrue(EngineSummary.read(summary(EngineState.RUNNING, 100, 10, 5, 1, 0, Diagnosis.Cause.NONE).write())
			.oilPerMinute() == 0f, "a summary with no oil use key reads zero");
		EngineSummary idle = new EngineSummary(EngineState.RUNNING, 100, 10, 5, 0, 0, 1, 1, 1, 1, false, new float[0], 1, 0, 0, 1, 0, 0,
			false, false, 0, 0, Diagnosis.Health.HEALTHY, Diagnosis.Cause.NONE, 0, 0, List.of(), 0f);
		helper.assertTrue(used.differsFrom(idle) && !idle.differsFrom(idle), "only a change in oil use is worth syncing");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void horsepowerIsCalibratedSoTheBasicFourReadsAHundred(GameTestHelper helper) {
		helper.assertTrue(EngineReadout.horsepower(1140f) == 1f, "1140 SU is one horsepower");
		helper.assertTrue(EngineReadout.horsepower(-50f) == 0f, "a negative power reads zero");
		helper.assertTrue(EngineReadout.horsepower(0f, 256f) == 0f && EngineReadout.horsepower(445f, -10f) == 0f,
			"no capacity or a backwards shaft reads zero");
		helper.assertTrue(Math.abs(EngineReadout.horsepower(445f, 256f) - 99.9f) < 0.1f, "a 445 SU/RPM engine at 256 RPM is about 100 hp");
		helper.assertTrue(EngineReadout.hp(99.94f)
			.equals("99.9"), "below 100 hp one decimal is shown");
		helper.assertTrue(EngineReadout.hp(250.4f)
			.equals("250"), "from 100 hp up whole numbers are shown");
		helper.assertTrue(EngineReadout.percent(0.456f) == 46 && EngineReadout.percent(0f) == 0 && EngineReadout.percent(1.5f) == 150,
			"a share rounds to a whole percent");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void anEnginesOutputIsWhatIsAskedForUpToWhatItCanGive(GameTestHelper helper) {
		EngineSummary light = summary(EngineState.RUNNING, 256, 445, 200, 1, 0, Diagnosis.Cause.NONE);
		EngineSummary heavy = summary(EngineState.OVERLOADED, 256, 445, 900, 1, 0, Diagnosis.Cause.NONE);
		helper.assertTrue(Math.abs(EngineReadout.output(light) - 44.9f) < 0.1f, "a light load delivers what it asks: " + EngineReadout.output(light));
		helper.assertTrue(Math.abs(EngineReadout.output(heavy) - EngineReadout.capacity(heavy)) < 0.001f,
			"an overloaded engine delivers all it has");
		helper.assertTrue(Math.abs(EngineReadout.capacity(light) - 99.9f) < 0.1f, "capacity is what it could give at this speed");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void loadWarmthAndOilAreClampedToTheirScales(GameTestHelper helper) {
		helper.assertTrue(EngineReadout.load(summary(EngineState.RUNNING, 100, 445, 445, 1, 0, Diagnosis.Cause.NONE)) == 1f, "flat out reads 1");
		helper.assertTrue(EngineReadout.load(summary(EngineState.OVERLOADED, 100, 445, 5000, 1, 0, Diagnosis.Cause.NONE)) == 2f,
			"far past its capacity reads 2, not more");
		helper.assertTrue(EngineReadout.load(summary(EngineState.OVERLOADED, 0, 0, 100, 1, 0, Diagnosis.Cause.NONE)) == 2f,
			"asked for something by an engine that has nothing reads 2");
		helper.assertTrue(EngineReadout.load(summary(EngineState.STALLED, 0, 0, 0, 1, 0, Diagnosis.Cause.NONE)) == 0f, "nothing asked of nothing reads 0");
		helper.assertTrue(EngineReadout.warmth(summary(EngineState.RUNNING, 100, 445, 100, 1, (float) Thermal.NORMAL, Diagnosis.Cause.NONE)) == 1f,
			"a warm engine reads 1");
		helper.assertTrue(EngineReadout.warmth(summary(EngineState.RUNNING, 100, 445, 100, 1, 0f, Diagnosis.Cause.NONE)) == 0f, "a cold block reads 0");
		helper.assertTrue(EngineReadout.warmth(summary(EngineState.RUNNING, 100, 445, 100, 1, 99f, Diagnosis.Cause.NONE)) == 1.5f,
			"a block running very hot reads 1.5 at most");
		helper.assertTrue(EngineReadout.oil(summary(EngineState.RUNNING, 100, 445, 100, 2f, 0, Diagnosis.Cause.NONE)) == 1f
			&& EngineReadout.oil(summary(EngineState.RUNNING, 100, 445, 100, -1f, 0, Diagnosis.Cause.NONE)) == 0f, "oil reads between 0 and 1");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void theStatusSourceGivesSixLinesForABuiltEngineAndOneWhenThereIsNone(GameTestHelper helper) {
		List<MutableComponent> running = EngineReadout.status(summary(EngineState.RUNNING, 256, 445, 200, 0.8f, 0.5f, Diagnosis.Cause.NONE));
		helper.assertTrue(running.size() == 6, "state, speed, power, load, temperature and oil: " + running.size());
		helper.assertTrue(key(running.get(0))
			.endsWith("display.engine.state.running"), "the first line names the state: " + key(running.get(0)));
		for (EngineState none : new EngineState[] {EngineState.INCOMPLETE, EngineState.BROKEN}) {
			List<MutableComponent> lines = EngineReadout.status(summary(none, 0, 0, 0, 1, 0, Diagnosis.Cause.NONE));
			helper.assertTrue(lines.size() == 1 && key(lines.get(0))
				.endsWith("display.engine.none"), none + " reads as no engine: " + lines);
			helper.assertTrue(EngineReadout.faultKey(summary(none, 0, 0, 0, 1, 0, Diagnosis.Cause.NONE))
				.equals("display.engine.none"), none + " has no fault line");
		}
		List<MutableComponent> missing = EngineReadout.status(null);
		helper.assertTrue(missing.size() == 1 && key(missing.get(0))
			.endsWith("display.engine.none"), "a crankshaft with no engine reads as no engine");
		helper.assertTrue(EngineReadout.faultKey(null)
			.equals("display.engine.none"), "and has no fault line");
		helper.assertTrue(EngineReadout.faultKey(summary(EngineState.RUNNING, 100, 445, 100, 1, 0, Diagnosis.Cause.NONE))
			.equals(Diagnosis.Cause.NONE.displayKey()), "a healthy engine's fault line is the no-fault phrase");
		helper.succeed();
	}
}
