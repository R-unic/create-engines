package com.createengines.gametest;

import com.createengines.CreateEngines;
import com.createengines.engine.sim.Tuning;
import com.createengines.registry.CEFluids;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Liquid fuel lying in the world burns when lit, and only fuel does. */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class FuelFireGameTests {

	private static final int POOL = 5;

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < POOL + 2; x++)
			for (int z = 0; z < 3; z++)
				helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
	}

	@GameTest(template = "empty", timeoutTicks = 200)
	public static void aPoolOfGasolineBurnsAwayFromOneFlame(GameTestHelper helper) {
		floor(helper);
		for (int x = 0; x < POOL; x++)
			helper.setBlock(new BlockPos(x, 2, 1), CEFluids.GASOLINE.get()
				.getSource()
				.defaultFluidState()
				.createLegacyBlock());
		helper.setBlock(new BlockPos(POOL, 2, 1), Blocks.FIRE);

		helper.succeedWhen(() -> {
			for (int x = 0; x < POOL; x++)
				helper.assertFalse(helper.getBlockState(new BlockPos(x, 2, 1))
					.getFluidState()
					.is(CEFluids.GASOLINE.get()
						.getSource()), "gasoline at x=" + x + " should have burnt");
		});
	}

	/** A lone source on a one-block tower runs down its sides onto a flame; it must burn out, not pour for ever. */
	@GameTest(template = "empty_large", timeoutTicks = 600)
	public static void aSourceOnATowerBurnsOutAboveAFlame(GameTestHelper helper) {
		for (int x = 0; x < 5; x++)
			for (int z = 0; z < 5; z++)
				helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
		for (int y = 2; y < 8; y++)
			helper.setBlock(new BlockPos(2, y, 2), Blocks.STONE);
		BlockPos top = new BlockPos(2, 8, 2);
		helper.setBlock(top, CEFluids.GASOLINE.get()
			.getSource()
			.defaultFluidState()
			.createLegacyBlock());
		helper.setBlock(new BlockPos(3, 2, 2), Blocks.FIRE);

		helper.succeedWhen(() -> {
			helper.assertFalse(helper.getBlockState(top)
				.getFluidState()
				.is(CEFluids.GASOLINE.get()
					.getSource()), "the source on the tower should have burnt");
			for (int y = 2; y <= 8; y++)
				helper.assertFalse(helper.getBlockState(new BlockPos(3, y, 2))
					.getFluidState()
					.is(CEFluids.GASOLINE.get()), "gasoline still pouring at y=" + y);
		});
	}

	@GameTest(template = "empty", timeoutTicks = 200)
	public static void lavaLightsDieselToo(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(new BlockPos(1, 2, 1), CEFluids.DIESEL.get()
			.getSource()
			.defaultFluidState()
			.createLegacyBlock());
		helper.setBlock(new BlockPos(2, 2, 1), Blocks.LAVA);
		helper.succeedWhen(() -> helper.assertFalse(helper.getBlockState(new BlockPos(1, 2, 1))
			.getFluidState()
			.is(CEFluids.DIESEL.get()
				.getSource()), "diesel beside lava should have burnt"));
	}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void engineOilBesideFireDoesNotBurn(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(new BlockPos(1, 2, 1), CEFluids.ENGINE_OIL.get()
			.getSource()
			.defaultFluidState()
			.createLegacyBlock());
		helper.setBlock(new BlockPos(2, 2, 1), Blocks.FIRE);
		helper.runAfterDelay(60, () -> {
			helper.assertTrue(helper.getBlockState(new BlockPos(1, 2, 1))
				.getFluidState()
				.is(CEFluids.ENGINE_OIL.get()
					.getSource()), "engine oil is not a fuel and must stay");
			helper.succeed();
		});
	}

	private static final int ROW_GASOLINE = 1;
	private static final int ROW_DIESEL = 6;

	private static void laidPool(GameTestHelper helper, int z, net.minecraft.world.level.material.Fluid fuel) {
		for (int x = 0; x < POOL + 2; x++)
			helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
		for (int x = 0; x < POOL; x++)
			helper.setBlock(new BlockPos(x, 2, z), fuel.defaultFluidState()
				.createLegacyBlock());
		helper.setBlock(new BlockPos(POOL, 2, z), Blocks.FIRE);
	}

	private static int fuelLeft(GameTestHelper helper, int z, net.minecraft.world.level.material.Fluid fuel) {
		int left = 0;
		for (int x = 0; x < POOL; x++)
			if (helper.getBlockState(new BlockPos(x, 2, z))
				.getFluidState()
				.is(fuel))
				left++;
		return left;
	}

	/**
	 * Spilled gasoline lights at once and burns off in a quick wave; spilled diesel is slow to catch and slow to spread. The
	 * same five-block pool, lit at one end at the same moment: gasoline is gone before diesel can have burnt even its fifth block
	 * (it needs at least five delays of six ticks), and diesel still burns out in the end.
	 */
	@GameTest(template = "empty_large", timeoutTicks = 500)
	public static void aGasolinePoolBurnsOffFasterThanADieselPool(GameTestHelper helper) {
		var gasoline = CEFluids.GASOLINE.get()
			.getSource();
		var diesel = CEFluids.DIESEL.get()
			.getSource();
		laidPool(helper, ROW_GASOLINE, gasoline);
		laidPool(helper, ROW_DIESEL, diesel);
		helper.runAtTickTime(25, () -> {
			helper.assertTrue(fuelLeft(helper, ROW_GASOLINE, gasoline) == 0, "gasoline should be gone by now: " + fuelLeft(helper, ROW_GASOLINE, gasoline));
			helper.assertTrue(fuelLeft(helper, ROW_DIESEL, diesel) > 0, "diesel cannot be gone yet: five blocks of six ticks each");
		});
		helper.succeedWhen(() -> helper.assertTrue(fuelLeft(helper, ROW_DIESEL, diesel) == 0, "diesel burns out in the end"));
	}

	/** With {@code compat.fuelFireByVolatility} off every fuel burns alike, as in 2.0.1. Its own batch: it flips a global. */
	@GameTest(template = "empty_large", timeoutTicks = 100, batch = "fuel-fire-alike")
	public static void withVolatilityOffGasolineAndDieselBurnAlike(GameTestHelper helper) {
		Tuning.fuelFireByVolatility = false;
		var gasoline = CEFluids.GASOLINE.get()
			.getSource();
		var diesel = CEFluids.DIESEL.get()
			.getSource();
		laidPool(helper, ROW_GASOLINE, gasoline);
		laidPool(helper, ROW_DIESEL, diesel);
		helper.runAtTickTime(40, () -> {
			try {
				helper.assertTrue(fuelLeft(helper, ROW_GASOLINE, gasoline) == 0 && fuelLeft(helper, ROW_DIESEL, diesel) == 0,
					"both pools burn off within the same time: gasoline " + fuelLeft(helper, ROW_GASOLINE, gasoline) + ", diesel "
						+ fuelLeft(helper, ROW_DIESEL, diesel));
			} finally {
				Tuning.fuelFireByVolatility = true;
			}
			helper.succeed();
		});
	}

	/** An engine's own fuel tank is not spilled fuel: with fire all around, a cylinder head holding gasoline keeps it. */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void fuelInAnEnginesTankDoesNotBurn(GameTestHelper helper) {
		floor(helper);
		BlockPos head = new BlockPos(2, 2, 1);
		helper.setBlock(head, com.createengines.registry.CEBlocks.CYLINDER_HEAD.getDefaultState());
		var tank = ((com.createengines.content.head.CylinderHeadBlockEntity) helper.getBlockEntity(head)).tank();
		tank.fill(new net.neoforged.neoforge.fluids.FluidStack(CEFluids.GASOLINE.get()
			.getSource(), 250), net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
		helper.setBlock(new BlockPos(1, 2, 1), Blocks.FIRE);
		helper.setBlock(new BlockPos(3, 2, 1), Blocks.LAVA);
		helper.runAfterDelay(60, () -> {
			helper.assertTrue(tank.getFluidAmount() == 250, "the tank keeps its gasoline: " + tank.getFluidAmount());
			helper.assertTrue(helper.getBlockState(head)
				.getBlock() instanceof com.createengines.content.head.CylinderHeadBlock, "and the head stays a head");
			helper.succeed();
		});
	}

	/** With {@code compat.fuelFire} off, fuel beside lava lies there as it did before. Its own batch: it flips a global. */
	@GameTest(template = "empty", timeoutTicks = 100, batch = "fuel-fire-off")
	public static void fuelBesideLavaStaysWhenFuelFireIsOff(GameTestHelper helper) {
		floor(helper);
		Tuning.fuelFire = false;
		helper.setBlock(new BlockPos(1, 2, 1), CEFluids.GASOLINE.get()
			.getSource()
			.defaultFluidState()
			.createLegacyBlock());
		helper.setBlock(new BlockPos(2, 2, 1), Blocks.LAVA);
		helper.runAfterDelay(60, () -> {
			try {
				helper.assertTrue(helper.getBlockState(new BlockPos(1, 2, 1))
					.getFluidState()
					.is(CEFluids.GASOLINE.get()
						.getSource()), "fuel must stay when fuelFire is off");
			} finally {
				Tuning.fuelFire = true;
			}
			helper.succeed();
		});
	}
}
