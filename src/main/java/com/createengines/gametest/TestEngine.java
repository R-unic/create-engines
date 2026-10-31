package com.createengines.gametest;

import java.util.function.BiConsumer;

import com.createengines.content.airflow.PortOpeningBlock;
import com.createengines.content.camshaft.CamshaftBlockEntity;
import com.createengines.content.cylinder.CylinderBlock;
import com.createengines.content.head.CylinderHeadBlock;
import com.createengines.content.head.CylinderHeadBlockEntity;
import com.createengines.content.rotor.RotorHousingBlock;
import com.createengines.content.rotor.RotorHousingBlockEntity;
import com.createengines.content.starter.StarterBlock;
import com.createengines.engine.CrankGeometry;
import com.createengines.registry.CEBlocks;
import com.createengines.registry.CEFluids;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

/**
 * The single-cylinder engine used by the GameTests and the visual smoke test: a crankshaft along X, started by a
 * starter driven by a creative motor. Its camshaft sits beside the cylinder head and is geared to half crank speed
 * the Create way: a small cogwheel on the crank driving a large one, then a pair of encased chain drives up to the
 * camshaft.
 */
public final class TestEngine {

	public static final BlockPos PRIMARY = new BlockPos(1, 2, 2);
	public static final BlockPos THROW = new BlockPos(2, 2, 2);
	public static final BlockPos CYLINDER = new BlockPos(2, 3, 2);
	public static final BlockPos HEAD = new BlockPos(2, 4, 2);
	public static final BlockPos LOBE = new BlockPos(2, 4, 3);
	public static final BlockPos AIR_INTAKE = new BlockPos(2, 4, 1);
	public static final BlockPos EXHAUST = new BlockPos(1, 4, 2);
	public static final BlockPos STARTER = new BlockPos(1, 1, 2);
	public static final BlockPos MOTOR = new BlockPos(1, 0, 2);
	public static final BlockPos STARTER_POWER = new BlockPos(0, 1, 2);
	/** On the crank's own axis, so the engine charges the battery while it turns. */
	public static final BlockPos ALTERNATOR = new BlockPos(0, 2, 2);
	/** On top of the first journal, touching the crank, so its plugs fire from it. */
	public static final BlockPos BATTERY = new BlockPos(1, 3, 2);
	public static final int MOTOR_RPM = 64;

	private static BlockState axisX(BlockState state) {
		return state.setValue(BlockStateProperties.AXIS, Axis.X);
	}

	/** Builds the engine with its corner at {@code origin}. Positions above are relative to that corner. */
	public static void build(Level level, BlockPos origin, boolean fuelled) {
		build(level, origin, fuelled, true);
	}

	/** As {@link #build(Level, BlockPos, boolean)}, optionally leaving the engine dry. */
	public static void build(Level level, BlockPos origin, boolean fuelled, boolean oiled) {
		BiConsumer<BlockPos, BlockState> set = (pos, state) -> level.setBlock(origin.offset(pos), state, Block.UPDATE_ALL);
		set.accept(PRIMARY, axisX(CEBlocks.CRANKSHAFT.getDefaultState()));
		set.accept(THROW, axisX(CEBlocks.CRANK_THROW.getDefaultState()));
		set.accept(new BlockPos(3, 2, 2), axisX(CEBlocks.CRANKSHAFT.getDefaultState()));
		set.accept(new BlockPos(4, 2, 2), axisX(AllBlocks.SHAFT.getDefaultState()));
		set.accept(new BlockPos(5, 2, 2), axisX(AllBlocks.COGWHEEL.getDefaultState()));

		set.accept(CYLINDER, CEBlocks.CYLINDER.getDefaultState()
			.setValue(CylinderBlock.FACING, Direction.UP)
			.setValue(CylinderBlock.HAS_PISTON, true)
			.setValue(CylinderBlock.HAS_ROD, true));
		set.accept(HEAD, CEBlocks.CYLINDER_HEAD.getDefaultState()
			.setValue(CylinderHeadBlock.FACING, Direction.UP)
			.setValue(CylinderHeadBlock.HAS_INJECTOR, true));
		set.accept(AIR_INTAKE, CEBlocks.AIR_INTAKE.getDefaultState()
			.setValue(PortOpeningBlock.FACING, Direction.NORTH));
		set.accept(EXHAUST, CEBlocks.EXHAUST_OUTLET.getDefaultState()
			.setValue(PortOpeningBlock.FACING, Direction.WEST));

		// Camshaft beside the head, geared 1:2 from the crank
		set.accept(LOBE, axisX(CEBlocks.CAM_LOBE.getDefaultState()));
		for (int x = 3; x <= 5; x++)
			set.accept(new BlockPos(x, 4, 3), axisX(CEBlocks.CAMSHAFT.getDefaultState()));
		set.accept(new BlockPos(5, 3, 3), axisX(AllBlocks.LARGE_COGWHEEL.getDefaultState()));
		set.accept(new BlockPos(6, 3, 3), axisX(AllBlocks.ENCASED_CHAIN_DRIVE.getDefaultState()));
		set.accept(new BlockPos(6, 4, 3), axisX(AllBlocks.ENCASED_CHAIN_DRIVE.getDefaultState()));

		// Starter under the first journal, driven by a creative motor, engaged by a redstone block
		set.accept(STARTER, CEBlocks.STARTER.getDefaultState()
			.setValue(StarterBlock.FACING, Direction.UP));
		set.accept(MOTOR, AllBlocks.CREATIVE_MOTOR.getDefaultState()
			.setValue(CreativeMotorBlock.FACING, Direction.UP));
		set.accept(STARTER_POWER, Blocks.REDSTONE_BLOCK.defaultBlockState());

		CreativeMotorBlockEntity motor = (CreativeMotorBlockEntity) level.getBlockEntity(origin.offset(MOTOR));
		motor.generatedSpeed.setValue(MOTOR_RPM);

		// Time the cam lobe for this cylinder: the cam must fire it when its piston is at top dead centre
		double tdc = CrankGeometry.topDeadCentreAngle(Axis.X, 0, Direction.UP);
		double lobe = ((tdc / 2) % 180 + 180) % 180;
		CamshaftBlockEntity cam = (CamshaftBlockEntity) level.getBlockEntity(origin.offset(LOBE));
		cam.lobeAngle.setValue((int) Math.round(lobe / CamshaftBlockEntity.LOBE_ANGLE_STEP));

		if (oiled) {
			CylinderHeadBlockEntity head = (CylinderHeadBlockEntity) level.getBlockEntity(origin.offset(HEAD));
			head.oilTank()
				.fill(new FluidStack(CEFluids.ENGINE_OIL.get()
					.getSource(), 250), FluidAction.EXECUTE);
		}
		if (fuelled) {
			CylinderHeadBlockEntity head = (CylinderHeadBlockEntity) level.getBlockEntity(origin.offset(HEAD));
			head.tank()
				.fill(new FluidStack(CEFluids.DIESEL.get()
					.getSource(), 250), FluidAction.EXECUTE);
		}
	}

	/**
	 * The same engine as a spark engine: a spark plug in the head, gasoline in the tank (which compression alone can't
	 * light) and, if asked, a flat battery on the crankshaft and an alternator on its end.
	 */
	public static void buildSpark(Level level, BlockPos origin, boolean battery) {
		build(level, origin, false);
		level.setBlock(origin.offset(HEAD), CEBlocks.CYLINDER_HEAD.getDefaultState()
			.setValue(CylinderHeadBlock.FACING, Direction.UP)
			.setValue(CylinderHeadBlock.HAS_INJECTOR, true)
			.setValue(CylinderHeadBlock.HAS_SPARK_PLUG, true), Block.UPDATE_ALL);
		CylinderHeadBlockEntity head = (CylinderHeadBlockEntity) level.getBlockEntity(origin.offset(HEAD));
		head.tank()
			.fill(new FluidStack(CEFluids.GASOLINE.get()
				.getSource(), 250), FluidAction.EXECUTE);
		if (battery)
			level.setBlock(origin.offset(BATTERY), CEBlocks.BATTERY.getDefaultState(), Block.UPDATE_ALL);
			level.setBlock(origin.offset(ALTERNATOR), axisX(CEBlocks.ALTERNATOR.getDefaultState()), Block.UPDATE_ALL);
	}

	/** The rotor housing of {@link #buildRotary}, where the throw is on the piston engines. */
	public static final BlockPos HOUSING = THROW;
	public static final BlockPos ROTARY_INTAKE = new BlockPos(2, 2, 1);
	public static final BlockPos ROTARY_EXHAUST = new BlockPos(2, 3, 2);

	/**
	 * A single-rotor engine: the same crankshaft, starter and battery as the spark engine, with a rotor housing where the
	 * throw was. It has no camshaft. Its intake is on the housing's north face and its exhaust on its top.
	 */
	public static void buildRotary(Level level, BlockPos origin, boolean sparkPlug) {
		BiConsumer<BlockPos, BlockState> set = (pos, state) -> level.setBlock(origin.offset(pos), state, Block.UPDATE_ALL);
		set.accept(PRIMARY, axisX(CEBlocks.CRANKSHAFT.getDefaultState()));
		set.accept(HOUSING, axisX(CEBlocks.ROTOR_HOUSING.getDefaultState())
			.setValue(RotorHousingBlock.HAS_ROTOR, true)
			.setValue(RotorHousingBlock.HAS_INJECTOR, true)
			.setValue(RotorHousingBlock.HAS_SPARK_PLUG, sparkPlug));
		set.accept(new BlockPos(3, 2, 2), axisX(CEBlocks.CRANKSHAFT.getDefaultState()));
		set.accept(new BlockPos(4, 2, 2), axisX(AllBlocks.SHAFT.getDefaultState()));
		set.accept(new BlockPos(5, 2, 2), axisX(AllBlocks.COGWHEEL.getDefaultState()));
		set.accept(ROTARY_INTAKE, CEBlocks.AIR_INTAKE.getDefaultState()
			.setValue(PortOpeningBlock.FACING, Direction.NORTH));
		set.accept(ROTARY_EXHAUST, CEBlocks.EXHAUST_OUTLET.getDefaultState()
			.setValue(PortOpeningBlock.FACING, Direction.UP));

		set.accept(STARTER, CEBlocks.STARTER.getDefaultState()
			.setValue(StarterBlock.FACING, Direction.UP));
		set.accept(MOTOR, AllBlocks.CREATIVE_MOTOR.getDefaultState()
			.setValue(CreativeMotorBlock.FACING, Direction.UP));
		set.accept(STARTER_POWER, Blocks.REDSTONE_BLOCK.defaultBlockState());
		set.accept(BATTERY, CEBlocks.BATTERY.getDefaultState());
		set.accept(ALTERNATOR, axisX(CEBlocks.ALTERNATOR.getDefaultState()));
		CreativeMotorBlockEntity motor = (CreativeMotorBlockEntity) level.getBlockEntity(origin.offset(MOTOR));
		motor.generatedSpeed.setValue(MOTOR_RPM);

		RotorHousingBlockEntity housing = (RotorHousingBlockEntity) level.getBlockEntity(origin.offset(HOUSING));
		housing.oilTank()
			.fill(new FluidStack(CEFluids.ENGINE_OIL.get()
				.getSource(), 250), FluidAction.EXECUTE);
		housing.tank()
			.fill(new FluidStack(CEFluids.GASOLINE.get()
				.getSource(), 250), FluidAction.EXECUTE);
	}

	/** The second rotor of {@link #addSecondRotor}, beside the first on the same shaft. */
	public static final BlockPos HOUSING_2 = new BlockPos(3, 2, 2);

	/** Makes {@link #buildRotary} a twin-rotor engine: a second housing, half a turn round, with its own ports. */
	public static void addSecondRotor(Level level, BlockPos origin) {
		BiConsumer<BlockPos, BlockState> set = (pos, state) -> level.setBlock(origin.offset(pos), state, Block.UPDATE_ALL);
		set.accept(HOUSING_2, axisX(CEBlocks.ROTOR_HOUSING.getDefaultState())
			.setValue(RotorHousingBlock.HAS_ROTOR, true)
			.setValue(RotorHousingBlock.HAS_INJECTOR, true)
			.setValue(RotorHousingBlock.HAS_SPARK_PLUG, true));
		set.accept(HOUSING_2.north(), CEBlocks.AIR_INTAKE.getDefaultState()
			.setValue(PortOpeningBlock.FACING, Direction.NORTH));
		set.accept(HOUSING_2.above(), CEBlocks.EXHAUST_OUTLET.getDefaultState()
			.setValue(PortOpeningBlock.FACING, Direction.UP));
		RotorHousingBlockEntity housing = (RotorHousingBlockEntity) level.getBlockEntity(origin.offset(HOUSING_2));
		housing.throwAngle.setValue(180 / RotorHousingBlockEntity.THROW_ANGLE_STEP);
	}

	/** The second crank line of the coupled engine: its throw, and the cylinder and head over it. */
	public static final BlockPos THROW_2 = new BlockPos(4, 2, 4);
	public static final BlockPos CYLINDER_2 = new BlockPos(4, 3, 4);
	public static final BlockPos HEAD_2 = new BlockPos(4, 4, 4);

	/**
	 * Adds a second crank line two blocks behind the first, geared to it by a row of three small cogwheels at the west
	 * end (which turns it the same way), so the two are one engine. It carries a cylinder of its own, worked by a lobe on
	 * the first line's camshaft from the other side. Call after {@link #build}; the engine then needs timing.
	 */
	public static void addCoupledLine(Level level, BlockPos origin) {
		BiConsumer<BlockPos, BlockState> set = (pos, state) -> level.setBlock(origin.offset(pos), state, Block.UPDATE_ALL);
		for (int x = 1; x <= 3; x++)
			set.accept(new BlockPos(x, 2, 4), axisX(CEBlocks.CRANKSHAFT.getDefaultState()));
		set.accept(THROW_2, axisX(CEBlocks.CRANK_THROW.getDefaultState()));
		set.accept(CYLINDER_2, CEBlocks.CYLINDER.getDefaultState()
			.setValue(CylinderBlock.FACING, Direction.UP)
			.setValue(CylinderBlock.HAS_PISTON, true)
			.setValue(CylinderBlock.HAS_ROD, true));
		set.accept(HEAD_2, CEBlocks.CYLINDER_HEAD.getDefaultState()
			.setValue(CylinderHeadBlock.FACING, Direction.UP)
			.setValue(CylinderHeadBlock.HAS_INJECTOR, true));
		set.accept(new BlockPos(3, 4, 4), CEBlocks.AIR_INTAKE.getDefaultState()
			.setValue(PortOpeningBlock.FACING, Direction.WEST));
		set.accept(new BlockPos(5, 4, 4), CEBlocks.EXHAUST_OUTLET.getDefaultState()
			.setValue(PortOpeningBlock.FACING, Direction.EAST));
		set.accept(new BlockPos(4, 4, 3), axisX(CEBlocks.CAM_LOBE.getDefaultState()));
		for (int z = 2; z <= 4; z++)
			set.accept(new BlockPos(0, 2, z), axisX(AllBlocks.COGWHEEL.getDefaultState()));
	}

	private TestEngine() {
	}
}
