package com.createengines.gametest;

import java.util.function.Consumer;

import org.slf4j.Logger;

import com.createengines.CreateEngines;
import com.createengines.compat.pollution.PollutionCompat;
import com.createengines.compat.pollution.PollutionProbe;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.content.head.CylinderHeadBlockEntity;
import com.createengines.engine.EngineState;
import com.createengines.registry.CEFluids;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Engines and Pollution of the Realms, on running engines: gas turns up at a running engine's outlet, not at a stopped one, and
 * sulfur follows the fuel. Every test passes without doing anything when the mod is not in the run (see
 * {@code runGameTestServerPollution}). The two {@code measure...} tests are how the release rates were chosen and only log.
 * <p>
 * The mod lets gas drift, so a single look at the air round an outlet can miss what was there a moment before. These tests
 * look every {@link #LOOK_EVERY} ticks and judge the most gas that was ever seen (or, for the ones that expect none, that
 * none ever was).
 */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class PollutionGameTests {

	private static final Logger LOGGER = LogUtils.getLogger();

	/** How far round the outlet the gas is counted: it spreads, so a single cell would miss it. */
	private static final int RADIUS = 8;

	/** Ticks between looks, and when the looking starts and ends (the starter lets go at 300). */
	private static final int LOOK_EVERY = 100;
	private static final int LOOK_FROM = 400;
	private static final int LOOK_TO = 1600;

	private static BlockPos outlet(GameTestHelper helper) {
		return helper.absolutePos(TestEngine.EXHAUST);
	}

	private static long carbon(GameTestHelper helper) {
		return PollutionProbe.carbonAround(helper.getLevel(), outlet(helper), RADIUS);
	}

	private static long sulfur(GameTestHelper helper) {
		return PollutionProbe.sulfurAround(helper.getLevel(), outlet(helper), RADIUS);
	}

	private static EngineState state(GameTestHelper helper) {
		CrankshaftBlockEntity crank = helper.getBlockEntity(TestEngine.PRIMARY);
		return crank.summary() == null ? null : crank.summary()
			.state();
	}

	/** The most gas seen so far at any look, and whether the engine was running at the last one. */
	private static final class Watch {
		long carbon;
		long sulfur;
		EngineState lastState;
	}

	/** Cranks the engine for 300 ticks, lets go, then looks every {@link #LOOK_EVERY} ticks and hands the result to {@code verdict}. */
	private static void runAndWatch(GameTestHelper helper, Consumer<Watch> verdict) {
		Watch watch = new Watch();
		helper.runAtTickTime(300, () -> helper.setBlock(TestEngine.STARTER_POWER, Blocks.AIR));
		for (int tick = LOOK_FROM; tick <= LOOK_TO; tick += LOOK_EVERY) {
			int at = tick;
			helper.runAtTickTime(at, () -> {
				watch.carbon = Math.max(watch.carbon, carbon(helper));
				watch.sulfur = Math.max(watch.sulfur, sulfur(helper));
				watch.lastState = state(helper);
				if (at == LOOK_TO)
					verdict.accept(watch);
			});
		}
	}

	@GameTest(template = "empty", timeoutTicks = 1800)
	public static void aRunningEngineReleasesCarbonAndSulfurAtItsOutlet(GameTestHelper helper) {
		if (!PollutionCompat.active()) {
			helper.succeed();
			return;
		}
		TestEngine.build(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		runAndWatch(helper, watch -> {
			helper.assertTrue(watch.lastState == EngineState.RUNNING, "the engine should be running, it is " + watch.lastState);
			helper.assertTrue(watch.carbon > 0, "a running diesel engine should have put carbon in the air, most seen " + watch.carbon);
			helper.assertTrue(watch.sulfur > 0, "and sulfur, which diesel carries, most seen " + watch.sulfur);
			helper.assertTrue(watch.carbon > watch.sulfur,
				"burning makes more carbon than sulfur (" + watch.carbon + " against " + watch.sulfur + ")");
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 1800)
	public static void anEngineThatIsOnlyBeingCrankedReleasesNothing(GameTestHelper helper) {
		if (!PollutionCompat.active()) {
			helper.succeed();
			return;
		}
		// No fuel: the starter turns it over, nothing burns and it never runs
		TestEngine.build(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), false);
		runAndWatch(helper, watch -> {
			helper.assertTrue(watch.lastState != EngineState.RUNNING, "a dry engine should not run, it is " + watch.lastState);
			helper.assertTrue(watch.carbon == 0, "a cranked engine burns nothing but put out carbon: " + watch.carbon);
			helper.assertTrue(watch.sulfur == 0, "and sulfur: " + watch.sulfur);
			helper.succeed();
		});
	}

	/**
	 * Flips a server-wide setting, so it has a batch to itself: tests in one batch run side by side, and an engine in another
	 * test would otherwise find pollution off and pass without checking anything. The setting is put back whatever happens.
	 */
	@GameTest(template = "empty", timeoutTicks = 1800, batch = "pollutionSwitchedOff")
	public static void anEngineWithItsPollutionSwitchedOffReleasesNothing(GameTestHelper helper) {
		if (!PollutionCompat.installed()) {
			helper.succeed();
			return;
		}
		boolean was = com.createengines.config.CEConfig.POLLUTION.get();
		com.createengines.config.CEConfig.POLLUTION.set(false);
		try {
			TestEngine.build(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
			runAndWatch(helper, watch -> {
				com.createengines.config.CEConfig.POLLUTION.set(was);
				helper.assertTrue(watch.lastState == EngineState.RUNNING, "the engine should be running, it is " + watch.lastState);
				helper.assertTrue(watch.carbon == 0 && watch.sulfur == 0,
					"with pollution off nothing should be released: " + watch.carbon + " carbon, " + watch.sulfur + " sulfur");
				helper.succeed();
			});
		} catch (RuntimeException e) {
			com.createengines.config.CEConfig.POLLUTION.set(was);
			throw e;
		}
	}

	/** Ethanol burns clean and carries no sulfur, so its engine puts out carbon only: sulfur follows the fuel's own value. */
	@GameTest(template = "empty", timeoutTicks = 1800)
	public static void anEthanolEngineReleasesCarbonButNoSulfur(GameTestHelper helper) {
		if (!PollutionCompat.active()) {
			helper.succeed();
			return;
		}
		TestEngine.buildSpark(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		CylinderHeadBlockEntity head = helper.getBlockEntity(TestEngine.HEAD);
		head.tank()
			.drain(250, FluidAction.EXECUTE);
		head.tank()
			.fill(new FluidStack(CEFluids.ETHANOL.get()
				.getSource(), 250), FluidAction.EXECUTE);
		runAndWatch(helper, watch -> {
			helper.assertTrue(watch.lastState == EngineState.RUNNING, "the engine should be running, it is " + watch.lastState);
			helper.assertTrue(watch.carbon > 0, "ethanol still burns to carbon, most seen " + watch.carbon);
			helper.assertTrue(watch.sulfur == 0, "but has no sulfur, most seen " + watch.sulfur);
			helper.succeed();
		});
	}

	/**
	 * An outlet stops releasing into air that already holds the area cap's worth of gas, and starts again where the cap is off or
	 * not yet reached. The gas is put in the cube in front of the mouth directly, and what the outlet is then offered to release
	 * is counted, so nothing here waits on a running engine. It flips a server-wide setting, so it has a batch to itself.
	 */
	@GameTest(template = "empty", timeoutTicks = 100, batch = "pollutionAreaCap")
	public static void anOutletReleasesNothingIntoAirThatAlreadyHoldsTheAreaCap(GameTestHelper helper) {
		if (!PollutionCompat.active()) {
			helper.succeed();
			return;
		}
		int was = com.createengines.config.CEConfig.POLLUTION_AREA_CAP.get();
		try {
			TestEngine.build(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), false);
			BlockPos mouth = outlet(helper);
			var facing = helper.getLevel()
				.getBlockState(mouth)
				.getValue(com.createengines.content.airflow.PortOpeningBlock.FACING);
			BlockPos front = mouth.relative(facing);
			int cap = 12;
			com.createengines.config.CEConfig.POLLUTION_AREA_CAP.set(cap);
			// Fill the cube in front of the mouth with carbon until it holds the cap, a cell at a time
			long held = 0;
			for (BlockPos pos : BlockPos.betweenClosed(front.offset(-3, -3, -3), front.offset(3, 3, 3))) {
				if (held >= cap)
					break;
				if (!pos.equals(mouth) && helper.getLevel()
					.getBlockState(pos)
					.isAir())
					held += PollutionProbe.pumpCarbon(helper.getLevel(), pos.immutable(), PollutionProbe.carbonCapacity());
			}
			helper.assertTrue(held >= cap, "the test could not fill the air to the cap, only " + held);
			PollutionProbe.startCounting();
			PollutionCompat.release(helper.getLevel(), mouth, 5, 5);
			helper.assertTrue(PollutionProbe.carbonReleased(mouth) == 0 && PollutionProbe.sulfurReleased(mouth) == 0,
				"air already at the cap should have turned the outlet away, it was offered " + PollutionProbe.carbonReleased(mouth)
					+ " carbon and " + PollutionProbe.sulfurReleased(mouth) + " sulfur");
			com.createengines.config.CEConfig.POLLUTION_AREA_CAP.set(0);
			PollutionCompat.release(helper.getLevel(), mouth, 5, 5);
			helper.assertTrue(PollutionProbe.carbonReleased(mouth) > 0, "with the cap off the outlet should release again");
			PollutionProbe.stopCounting();
			PollutionProbe.startCounting();
			com.createengines.config.CEConfig.POLLUTION_AREA_CAP.set(100000);
			PollutionCompat.release(helper.getLevel(), mouth, 5, 5);
			helper.assertTrue(PollutionProbe.carbonReleased(mouth) > 0, "air far under a high cap should not stop the outlet");
			helper.succeed();
		} finally {
			PollutionProbe.stopCounting();
			com.createengines.config.CEConfig.POLLUTION_AREA_CAP.set(was);
		}
	}

	/**
	 * Logs how much gas a running engine has put into the air, sampled as it runs, against how much a cell of the mod holds.
	 * It asserts nothing: it is how the release rates were chosen, and is kept so they can be chosen again.
	 */
	@GameTest(template = "empty", timeoutTicks = 3000)
	public static void measureTheReleaseRates(GameTestHelper helper) {
		if (!PollutionCompat.active()) {
			helper.succeed();
			return;
		}
		TestEngine.build(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		LOGGER.info("POLLUTIONMEASURE cell capacity (carbon) {}, rate {}", PollutionProbe.carbonCapacity(),
			com.createengines.config.CEConfig.POLLUTION_RATE.get());
		helper.runAtTickTime(300, () -> helper.setBlock(TestEngine.STARTER_POWER, Blocks.AIR));
		for (int tick = 400; tick <= 2400; tick += 200) {
			int at = tick;
			helper.runAtTickTime(at, () -> {
				CrankshaftBlockEntity crank = helper.getBlockEntity(TestEngine.PRIMARY);
				var summary = crank.summary();
				LOGGER.info("POLLUTIONMEASURE tick {} state {} rpm {} carbon {} sulfur {} outletCell carbon {} sulfur {}", at,
					summary == null ? null : summary.state(), summary == null ? 0 : Math.round(summary.rpm()), carbon(helper),
					sulfur(helper), PollutionProbe.carbonAt(helper.getLevel(), outlet(helper).west()),
					PollutionProbe.sulfurAt(helper.getLevel(), outlet(helper).west()));
				if (at == 2400)
					helper.succeed();
			});
		}
	}

	/** Logs how long a little carbon and a little sulfur last in the open, so the two can be compared fairly. Asserts nothing. */
	@GameTest(template = "empty", timeoutTicks = 1500)
	public static void measureHowLongEachGasLasts(GameTestHelper helper) {
		if (!PollutionCompat.active()) {
			helper.succeed();
			return;
		}
		BlockPos carbon = helper.absolutePos(new BlockPos(2, 6, 2));
		BlockPos sulfur = helper.absolutePos(new BlockPos(6, 6, 2));
		int c = PollutionProbe.pumpCarbon(helper.getLevel(), carbon, 3);
		int s = PollutionProbe.pumpSulfur(helper.getLevel(), sulfur, 3);
		LOGGER.info("POLLUTIONLAST pumped carbon {} sulfur {}", c, s);
		for (int tick = 100; tick <= 1200; tick += 100) {
			int at = tick;
			helper.runAtTickTime(at, () -> {
				LOGGER.info("POLLUTIONLAST tick {} carbon in box {} sulfur in box {}", at,
					PollutionProbe.carbonAround(helper.getLevel(), carbon, 4), PollutionProbe.sulfurAround(helper.getLevel(), sulfur, 4));
				if (at == 1200)
					helper.succeed();
			});
		}
	}

	/**
	 * Where the two engines of a pair stand: two blocks in, so the outlet one block out from the head still has air in front of
	 * it and not the test's barrier ring, and twelve apart, far enough to be separate engines and separate patches of air.
	 */
	private static final BlockPos FIRST = new BlockPos(2, 0, 0);
	private static final BlockPos SECOND = new BlockPos(2, 0, 12);
	private static final int PAIR_RADIUS = 5;
	/** Richer than the default 10, and still a mixture an engine can run on. */
	private static final int RICH_METERING = 16;

	/** A rich engine at {@code origin}. */
	private static void buildRich(GameTestHelper helper, BlockPos origin) {
		TestEngine.build(helper.getLevel(), helper.absolutePos(origin), true);
		CylinderHeadBlockEntity head = (CylinderHeadBlockEntity) helper.getLevel()
			.getBlockEntity(helper.absolutePos(origin.offset(TestEngine.HEAD)));
		head.metering.setValue(RICH_METERING);
	}

	/** Moves an engine's outlet one block out and puts a Soot Trap where it was, against the head. */
	private static void addTrap(GameTestHelper helper, BlockPos origin) {
		helper.setBlock(origin.offset(TestEngine.EXHAUST.west()), com.createengines.registry.CEBlocks.EXHAUST_OUTLET.getDefaultState()
			.setValue(com.createengines.content.airflow.PortOpeningBlock.FACING, net.minecraft.core.Direction.WEST));
		helper.setBlock(origin.offset(TestEngine.EXHAUST), com.createengines.registry.CEBlocks.SOOT_TRAP.getDefaultState()
			.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS, net.minecraft.core.Direction.Axis.X));
	}

	/** Lets go of both starters at tick 300. */
	private static void releaseBothStarters(GameTestHelper helper) {
		helper.runAtTickTime(300, () -> {
			helper.setBlock(FIRST.offset(TestEngine.STARTER_POWER), Blocks.AIR);
			helper.setBlock(SECOND.offset(TestEngine.STARTER_POWER), Blocks.AIR);
		});
	}

	private static EngineState stateAt(GameTestHelper helper, BlockPos origin) {
		CrankshaftBlockEntity crank = helper.getBlockEntity(origin.offset(TestEngine.PRIMARY));
		return crank.summary() == null ? null : crank.summary()
			.state();
	}

	/**
	 * Two identical rich engines, one with a Soot Trap in its exhaust. What the trap catches is not released as the extra carbon of
	 * a rich run, so the trapped engine puts out the carbon of burning fuel alone and the open one the carbon of its soot as well.
	 */
	@GameTest(template = "empty", timeoutTicks = 1800)
	public static void aSootTrapCutsTheCarbonOfARichEngine(GameTestHelper helper) {
		if (!PollutionCompat.active()) {
			helper.succeed();
			return;
		}
		buildRich(helper, FIRST);
		buildRich(helper, SECOND);
		addTrap(helper, FIRST);
		releaseBothStarters(helper);
		BlockPos trappedOutlet = helper.absolutePos(FIRST.offset(TestEngine.EXHAUST.west()));
		BlockPos openOutlet = helper.absolutePos(SECOND.offset(TestEngine.EXHAUST));
		PollutionProbe.startCounting();
		helper.runAtTickTime(LOOK_TO, () -> {
			long trappedCarbon = PollutionProbe.carbonReleased(trappedOutlet);
			long trappedSulfur = PollutionProbe.sulfurReleased(trappedOutlet);
			long openCarbon = PollutionProbe.carbonReleased(openOutlet);
			long openSulfur = PollutionProbe.sulfurReleased(openOutlet);
			EngineState trappedState = stateAt(helper, FIRST);
			EngineState openState = stateAt(helper, SECOND);
			PollutionProbe.stopCounting();
			String seen = " (trapped " + trappedCarbon + " carbon, " + trappedSulfur + " sulfur; open " + openCarbon + " carbon, "
				+ openSulfur + " sulfur)";
			helper.assertTrue(trappedState == EngineState.RUNNING && openState == EngineState.RUNNING,
				"both engines should be running, they are " + trappedState + " and " + openState);
			helper.assertTrue(openSulfur > 0 && trappedSulfur > 0, "both engines burn diesel, which carries sulfur" + seen);
			helper.assertTrue(Math.abs(trappedSulfur - openSulfur) <= 2,
				"a trap does not touch sulfur, so both engines put out the same" + seen);
			// The base carbon of burning fuel is in step with the sulfur by the two per-mB rates, so what the trapped engine
			// puts out beyond that is soot carbon that got past the trap
			double base = trappedSulfur * (com.createengines.engine.sim.Emissions.CARBON_PER_MB
				/ com.createengines.engine.sim.Emissions.SULFUR_PER_MB);
			helper.assertTrue(trappedCarbon <= base + 2, "a trap that catches the soot leaves only the carbon of burning" + seen);
			helper.assertTrue(openCarbon > 2 * trappedCarbon, "the open rich engine adds the carbon of its soot" + seen);
			helper.succeed();
		});
	}

	/**
	 * An engine whose outlet is shut by solid blocks, and one with water over its outlet, both stall (the engine's own check for a
	 * blocked or flooded exhaust) and put nothing in the air. Gas never goes into a shut or flooded mouth.
	 */
	@GameTest(template = "empty", timeoutTicks = 1800)
	public static void anEngineWithItsOutletShutOrFloodedStallsAndReleasesNothing(GameTestHelper helper) {
		if (!PollutionCompat.active()) {
			helper.succeed();
			return;
		}
		TestEngine.build(helper.getLevel(), helper.absolutePos(FIRST), true);
		TestEngine.build(helper.getLevel(), helper.absolutePos(SECOND), true);
		for (int reach = 1; reach <= 4; reach++)
			helper.setBlock(FIRST.offset(TestEngine.EXHAUST.west(reach)), Blocks.STONE);
		helper.setBlock(SECOND.offset(TestEngine.EXHAUST.west()), Blocks.WATER);
		releaseBothStarters(helper);
		long[] gas = new long[2];
		for (int tick = LOOK_FROM; tick <= LOOK_TO; tick += LOOK_EVERY) {
			int at = tick;
			helper.runAtTickTime(at, () -> {
				gas[0] += PollutionProbe.carbonAround(helper.getLevel(), helper.absolutePos(FIRST.offset(TestEngine.EXHAUST)), PAIR_RADIUS)
					+ PollutionProbe.sulfurAround(helper.getLevel(), helper.absolutePos(FIRST.offset(TestEngine.EXHAUST)), PAIR_RADIUS);
				gas[1] += PollutionProbe.carbonAround(helper.getLevel(), helper.absolutePos(SECOND.offset(TestEngine.EXHAUST)), PAIR_RADIUS)
					+ PollutionProbe.sulfurAround(helper.getLevel(), helper.absolutePos(SECOND.offset(TestEngine.EXHAUST)), PAIR_RADIUS);
				if (at == LOOK_TO) {
					helper.assertTrue(stateAt(helper, FIRST) != EngineState.RUNNING, "solid blocks over the outlet should stall the engine");
					helper.assertTrue(stateAt(helper, SECOND) != EngineState.RUNNING, "water over the outlet should stall the engine");
					helper.assertTrue(gas[0] == 0, "no gas should come out of an outlet shut by blocks, saw " + gas[0]);
					helper.assertTrue(gas[1] == 0, "none should go through water either, saw " + gas[1]);
					helper.succeed();
				}
			});
		}
	}
}
