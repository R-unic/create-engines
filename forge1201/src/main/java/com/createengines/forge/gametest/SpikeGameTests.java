package com.createengines.forge.gametest;

import com.createengines.forge.CreateEnginesForge;
import com.createengines.forge.SpikeCrankBlockEntity;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreateEnginesForge.ID)
@PrefixGameTestTemplate(false)
public class SpikeGameTests {

	/** The spike's whole question: does a simulated engine, fed from a Create fluid tank, turn a shaft on a Create network? */
	@GameTest(template = "empty", timeoutTicks = 400)
	public static void engineTurnsAShaft(GameTestHelper helper) {
		BlockPos crank = new BlockPos(1, 1, 1);
		BlockPos shaft = crank.east();
		BlockPos tank = crank.above();
		helper.setBlock(tank, AllBlocks.FLUID_TANK.getDefaultState());
		((com.simibubi.create.content.fluids.tank.FluidTankBlockEntity) helper.getBlockEntity(tank)).getTankInventory()
			.fill(new net.minecraftforge.fluids.FluidStack(com.createengines.forge.SpikeFluids.DIESEL.get(), 4000),
				net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
		helper.setBlock(crank, CreateEnginesForge.CRANK.get().defaultBlockState()
			.setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.X));
		helper.setBlock(shaft, AllBlocks.SHAFT.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.X));
		for (int t : new int[] {20, 40, 80, 120, 160})
			helper.runAfterDelay(t, () -> {
				SpikeCrankBlockEntity e = (SpikeCrankBlockEntity) helper.getBlockEntity(crank);
				System.out.println("SPIKE t=" + t + " sim " + e.simRpm() + " published " + e.getGeneratedSpeed() + " crank " + e.getSpeed());
			});
		helper.runAfterDelay(200, () -> {
			SpikeCrankBlockEntity engine = (SpikeCrankBlockEntity) helper.getBlockEntity(crank);
			KineticBlockEntity turning = (KineticBlockEntity) helper.getBlockEntity(shaft);
			System.out.println("SPIKE sim rpm " + engine.simRpm() + ", shaft speed " + turning.getSpeed()
				+ ", crank speed " + engine.getSpeed());
			if (engine.simRpm() <= 0)
				helper.fail("the simulation is not running");
			else if (Math.abs(Math.abs(engine.getGeneratedSpeed()) - engine.simRpm()) > 0.2 * engine.simRpm() + 3)
				helper.fail("the network turns at " + engine.getGeneratedSpeed() + " but the simulation runs at " + engine.simRpm());
			else if (turning.getSpeed() == 0)
				helper.fail("the shaft is not turning (sim " + engine.simRpm() + " rpm)");
			else
				helper.succeed();
		});
	}

	/** The packs' fuel files reach the game: the mod's own file, read at the world's data reload. */
	@GameTest(template = "empty")
	public static void fuelFilesAreLoaded(GameTestHelper helper) {
		var diesel = com.createengines.forge.FuelData.of(new net.minecraft.resources.ResourceLocation("create_engines", "diesel"));
		if (diesel == null)
			helper.fail("create_engines:diesel is not a fuel (" + com.createengines.forge.FuelData.count() + " loaded)");
		else if (!diesel.equals(com.createengines.engine.sim.FuelSpec.DIESEL))
			helper.fail("diesel reads as " + diesel);
		else
			helper.succeed();
	}

	/** The other side of it: with nothing to burn the engine is only coasting from its first turn-over and dies. */
	@GameTest(template = "empty", timeoutTicks = 400)
	public static void anEngineWithNoFuelStops(GameTestHelper helper) {
		BlockPos crank = new BlockPos(1, 1, 1);
		helper.setBlock(crank, CreateEnginesForge.CRANK.get().defaultBlockState()
			.setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.X));
		helper.runAfterDelay(300, () -> {
			SpikeCrankBlockEntity engine = (SpikeCrankBlockEntity) helper.getBlockEntity(crank);
			if (engine.simRpm() >= 40)
				helper.fail("an engine with no fuel is still at " + engine.simRpm() + " rpm");
			else
				helper.succeed();
		});
	}
}
