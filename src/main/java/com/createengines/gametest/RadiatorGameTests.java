package com.createengines.gametest;

import com.createengines.CreateEngines;
import com.createengines.content.radiator.RadiatorBlockEntity;
import com.createengines.engine.RadiatorBank;
import com.createengines.engine.sim.Radiator;
import com.createengines.engine.topology.EngineLayout;
import com.createengines.engine.topology.EngineScanner;
import com.createengines.registry.CEBlocks;
import com.createengines.registry.CEFluids;
import com.simibubi.create.api.boiler.BoilerHeater;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The radiator in the world: joining an engine, its coolant, boiling, and the basin on top of it. */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class RadiatorGameTests {

	private static final String BATCH = "radiator";
	private static final BlockPos AT = new BlockPos(3, 1, 2);

	private static RadiatorBlockEntity place(GameTestHelper helper, BlockPos pos) {
		helper.setBlock(pos, CEBlocks.RADIATOR.getDefaultState());
		return helper.getBlockEntity(pos);
	}

	private static void fill(RadiatorBlockEntity radiator, int amount) {
		radiator.tank()
			.fill(new FluidStack(CEFluids.COOLANT.get()
				.getSource(), amount), FluidAction.EXECUTE);
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void aRadiatorTakesCoolantAndWaterAndNothingElse(GameTestHelper helper) {
		RadiatorBlockEntity radiator = place(helper, AT);
		var fluids = radiator.fluidCapability();
		helper.assertTrue(fluids.fill(new FluidStack(Fluids.LAVA, 500), FluidAction.SIMULATE) == 0, "no lava");
		helper.assertTrue(fluids.fill(new FluidStack(CEFluids.ENGINE_OIL.get()
			.getSource(), 500), FluidAction.SIMULATE) == 0, "no oil");
		helper.assertTrue(fluids.fill(new FluidStack(Fluids.WATER, 5000), FluidAction.EXECUTE) == RadiatorBlockEntity.CAPACITY,
			"it holds " + RadiatorBlockEntity.CAPACITY);
		helper.assertTrue(radiator.quality() == CEFluids.WATER_COOLING && radiator.comparatorSignal() == 15,
			"full of water: " + radiator.quality() + ", " + radiator.comparatorSignal());
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 100, batch = BATCH)
	public static void aRadiatorTouchingTheCrankOrAHeadJoinsTheEngine(GameTestHelper helper) {
		TestEngine.build(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		helper.runAtTickTime(20, () -> {
			EngineLayout bare = EngineScanner.scan(helper.getLevel(), helper.absolutePos(TestEngine.PRIMARY));
			helper.assertTrue(bare != null && bare.radiators()
				.isEmpty(), "none placed, none found");
			BlockPos crank = freeSide(helper, TestEngine.PRIMARY);
			place(helper, crank);
			BlockPos head = freeSide(helper, TestEngine.HEAD);
			place(helper, head);
			EngineLayout cooled = EngineScanner.scan(helper.getLevel(), helper.absolutePos(TestEngine.PRIMARY));
			helper.assertTrue(cooled != null && cooled.radiators()
				.size() == 2, "both found: " + (cooled == null ? null : cooled.radiators()));
			helper.assertTrue(cooled.members()
				.contains(helper.absolutePos(crank)), "and they are part of the engine");
			helper.succeed();
		});
	}

	private static BlockPos freeSide(GameTestHelper helper, BlockPos of) {
		for (Direction side : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP})
			if (helper.getBlockState(of.relative(side))
				.isAir())
				return of.relative(side);
		helper.fail("no free side beside " + of);
		return of;
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void aBoilingRadiatorLosesCoolantAndAQuietOneDoesNot(GameTestHelper helper) {
		RadiatorBlockEntity radiator = place(helper, AT);
		fill(radiator, 500);
		Radiator.Supply bank = new RadiatorBank(helper.getLevel(), java.util.List.of(helper.absolutePos(AT)));
		for (int i = 0; i < 100; i++)
			bank.ran(0, 50, 0);
		helper.assertTrue(radiator.tank()
			.getFluidAmount() == 500, "nothing spent while it only sheds");
		for (int i = 0; i < 100; i++)
			bank.ran(0, 50, 0.25);
		helper.assertTrue(radiator.tank()
			.getFluidAmount() == 475, "whole millibuckets boiled away: " + radiator.tank()
			.getFluidAmount());
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 400, batch = BATCH)
	public static void aHardWorkingRadiatorHeatsABasinButNeverSuperheatsAndGoesColdOffTheEngine(GameTestHelper helper) {
		RadiatorBlockEntity radiator = place(helper, AT);
		boolean[] feeding = {true};
		EveryTick.run(helper, () -> {
			if (feeding[0])
				radiator.ran(500, 0);
			helper.assertTrue(helper.getBlockState(AT)
				.getValue(BlazeBurnerBlock.HEAT_LEVEL) != HeatLevel.SEETHING, "a radiator never superheats");
		});
		helper.runAtTickTime(60, () -> {
			helper.assertTrue(helper.getBlockState(AT)
				.getValue(BlazeBurnerBlock.HEAT_LEVEL) == HeatLevel.KINDLED, "heated: " + helper.getBlockState(AT));
			float boiler = BoilerHeater.findHeat(helper.getLevel(), helper.absolutePos(AT), helper.getBlockState(AT));
			helper.assertTrue(boiler == 1, "a boiler reads it as one level of heat: " + boiler);
			feeding[0] = false;
		});
		helper.runAtTickTime(120, () -> {
			helper.assertTrue(!radiator.onEngine() && helper.getBlockState(AT)
				.getValue(BlazeBurnerBlock.HEAT_LEVEL) == HeatLevel.NONE, "cold off the engine: " + helper.getBlockState(AT));
			helper.assertTrue(BoilerHeater.findHeat(helper.getLevel(), helper.absolutePos(AT), helper.getBlockState(AT))
				== BoilerHeater.NO_HEAT, "and a cold one gives a boiler nothing");
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 100, batch = BATCH)
	public static void aRadiatorWithNoFanSitsInStillAir(GameTestHelper helper) {
		RadiatorBlockEntity radiator = place(helper, AT);
		helper.runAtTickTime(50, () -> {
			helper.assertTrue(radiator.airflow() == 1 && radiator.air() == Radiator.Air.STILL, "still air: " + radiator.airflow());
			helper.succeed();
		});
	}
}
