package com.createengines.gametest;

import static com.createengines.gametest.EngineRigs.*;

import com.createengines.CreateEngines;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.content.cylinder.CylinderBlockEntity;
import com.createengines.content.starter.StarterBlock;
import com.createengines.content.starter.StarterBlockEntity;
import com.createengines.engine.EngineState;
import com.createengines.registry.CEBlocks;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The heavy starter, engine casing, dye, rain cap and Maker's Plate. Test engines as in {@link EngineGameTests}. */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class StarterCasingGameTests {

	private static BlockState heavyStarter() {
		return CEBlocks.HEAVY_STARTER.getDefaultState()
			.setValue(StarterBlock.FACING, Direction.UP);
	}

	@GameTest(template = "empty", timeoutTicks = 1200)
	public static void heavyStarterPutsLessStressOnItsInput(GameTestHelper helper) {
		buildSingleCylinder(helper, false);
		float[] plain = new float[1];
		helper.runAtTickTime(300, () -> {
			StarterBlockEntity starter = helper.getBlockEntity(STARTER);
			plain[0] = starter.calculateStressApplied();
			helper.assertTrue(plain[0] > 0, "Cranking should stress the starter's input");
			helper.setBlock(STARTER, heavyStarter());
		});
		helper.runAtTickTime(800, () -> {
			StarterBlockEntity starter = helper.getBlockEntity(STARTER);
			float heavy = starter.calculateStressApplied();
			helper.assertTrue(heavy > 0 && heavy < plain[0] * 0.6f,
				"Heavy starter should gear the effort down: plain " + plain[0] + ", heavy " + heavy);
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 1200)
	public static void startersTurningTheSameWayShareTheEffort(GameTestHelper helper) {
		buildSingleCylinder(helper, false);
		float[] alone = new float[1];
		helper.runAtTickTime(300, () -> {
			alone[0] = ((StarterBlockEntity) helper.getBlockEntity(STARTER)).calculateStressApplied();
			helper.assertTrue(alone[0] > 0, "A lone starter should feel the whole effort");
			// A second starter on the next crank block along, with its own motor and redstone
			helper.setBlock(new BlockPos(3, 1, 2), CEBlocks.STARTER.getDefaultState()
				.setValue(StarterBlock.FACING, Direction.UP));
			helper.setBlock(new BlockPos(3, 0, 2), AllBlocks.CREATIVE_MOTOR.getDefaultState()
				.setValue(CreativeMotorBlock.FACING, Direction.UP));
			((CreativeMotorBlockEntity) helper.getBlockEntity(new BlockPos(3, 0, 2))).generatedSpeed.setValue(TestEngine.MOTOR_RPM);
			helper.setBlock(new BlockPos(4, 1, 2), Blocks.REDSTONE_BLOCK.defaultBlockState());
		});
		helper.runAtTickTime(800, () -> {
			StarterBlockEntity first = helper.getBlockEntity(STARTER);
			StarterBlockEntity second = helper.getBlockEntity(new BlockPos(3, 1, 2));
			float shared = first.calculateStressApplied();
			helper.assertTrue(shared > 0 && shared < alone[0] * 0.7f,
				"Each starter should feel about half the effort: alone " + alone[0] + ", shared " + shared);
			helper.assertTrue(Math.abs(second.calculateStressApplied() - shared) < shared * 0.35f,
				"Equal starters should carry equal shares");
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 1200)
	public static void heavyStarterChokesAColdEngineThenLetsGo(GameTestHelper helper) {
		buildSingleCylinder(helper, false);
		helper.setBlock(STARTER, heavyStarter());
		helper.runAtTickTime(200, () -> {
			CrankshaftBlockEntity crank = helper.getBlockEntity(PRIMARY);
			helper.assertTrue(summary(helper).temperature() < 0.3f, "A new engine should be cold: " + summary(helper));
			helper.assertTrue(crank.controller()
				.crankAssist() > 0.99, "The heavy starter should be choking the cold engine");
			helper.setBlock(STARTER_POWER, Blocks.AIR);
		});
		helper.runAtTickTime(600, () -> {
			CrankshaftBlockEntity crank = helper.getBlockEntity(PRIMARY);
			helper.assertTrue(crank.controller()
				.crankAssist() == 0, "The choke should let go once the starter has");
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 900)
	public static void plainStarterDoesNotChokeAColdEngine(GameTestHelper helper) {
		buildSingleCylinder(helper, false);
		helper.runAtTickTime(200, () -> {
			CrankshaftBlockEntity crank = helper.getBlockEntity(PRIMARY);
			helper.assertTrue(crank.controller()
				.crankAssist() == 0, "A plain starter should leave the cold alone");
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 1200)
	public static void heavyStarterStartsAnEngine(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		helper.setBlock(STARTER, heavyStarter());
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(600, () -> {
			helper.assertTrue(summary(helper).state() == EngineState.RUNNING,
				"A fuelled engine should catch and run on a heavy starter: " + summary(helper));
			helper.succeed();
		});
	}

	/**
	 * Engine Casing is Create's encasing for shafts and cogs, and a state of the cranks, cams, cylinders and plain pipes:
	 * the variants are registered, the cased blocks keep valid block entities, and a clad cylinder is still a cylinder.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void casingCoversShaftsCogsCylindersAndPipes(GameTestHelper helper) {
		net.minecraft.world.level.block.Block casing = CEBlocks.ENGINE_CASING.get();
		for (net.minecraft.world.level.block.Block block : new net.minecraft.world.level.block.Block[] {
			AllBlocks.SHAFT.get(), AllBlocks.COGWHEEL.get(), AllBlocks.LARGE_COGWHEEL.get()})
			helper.assertTrue(com.simibubi.create.content.decoration.encasing.EncasingRegistry.getVariants(block)
				.stream()
				.anyMatch(variant -> variant instanceof com.simibubi.create.content.decoration.encasing.EncasedBlock cased
					&& cased.getCasing() == casing), "Engine Casing should encase " + block);
		BlockState[] cased = {CEBlocks.ENGINE_CASED_SHAFT.getDefaultState(), CEBlocks.ENGINE_CASED_COGWHEEL.getDefaultState(),
			CEBlocks.ENGINE_CASED_LARGE_COGWHEEL.getDefaultState()};
		for (int i = 0; i < cased.length; i++) {
			BlockPos pos = new BlockPos(1 + i, 2, 1);
			helper.setBlock(pos, cased[i]);
			helper.assertTrue(helper.getBlockEntity(pos) instanceof KineticBlockEntity,
				"A cased shaft or cog should be a kinetic block: " + cased[i]);
		}
		BlockPos cylinder = new BlockPos(1, 2, 3);
		helper.setBlock(cylinder, CEBlocks.CYLINDER.getDefaultState()
			.setValue(com.createengines.content.Encasing.ENCASED, true));
		helper.assertTrue(helper.getBlockEntity(cylinder) instanceof CylinderBlockEntity,
			"A clad cylinder keeps its block entity");
		BlockPos pipe = new BlockPos(2, 2, 3);
		helper.setBlock(pipe, CEBlocks.INTAKE_MANIFOLD.getDefaultState()
			.setValue(com.createengines.content.Encasing.ENCASED, true));
		helper.assertTrue(helper.getBlockState(pipe)
			.getValue(com.createengines.content.Encasing.ENCASED), "A pipe can be clad");
		helper.succeed();
	}

	/**
	 * The three casings take a dye and a bucket of water washes it off. The paint is only looks: the block stays the
	 * same block, a dye is used up outside creative, and a dye that is already on does nothing.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void casingTakesDyeAndWashesClean(GameTestHelper helper) {
		java.util.List<com.createengines.content.EngineCasingBlock> casings = java.util.List.of(CEBlocks.ENGINE_CASING.get(),
			CEBlocks.ENGINE_RIVETED_CASING.get(), CEBlocks.ENGINE_LOUVRED_CASING.get());
		net.minecraft.world.entity.player.Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
		for (int i = 0; i < casings.size(); i++) {
			BlockPos pos = new BlockPos(1 + i, 2, 1);
			helper.setBlock(pos, casings.get(i)
				.defaultBlockState());
			helper.assertTrue(helper.getBlockState(pos)
				.getValue(com.createengines.content.EngineCasingBlock.PAINT) == com.createengines.content.CasingPaint.NONE,
				"Casing starts as bare steel");

			net.minecraft.world.item.ItemStack dye = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.RED_DYE, 2);
			player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, dye);
			dye(helper, player, pos, dye);
			helper.assertTrue(helper.getBlockState(pos)
				.getValue(com.createengines.content.EngineCasingBlock.PAINT) == com.createengines.content.CasingPaint.RED,
				"Red dye should turn " + casings.get(i) + " red");
			helper.assertTrue(dye.getCount() == 1, "One dye is used up");
			helper.assertTrue(helper.getBlockState(pos)
				.is(casings.get(i)), "It is still the same casing");

			dye(helper, player, pos, dye);
			helper.assertTrue(dye.getCount() == 1, "A dye already on is not wasted");

			net.minecraft.world.item.ItemStack water = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WATER_BUCKET);
			dye(helper, player, pos, water);
			helper.assertTrue(helper.getBlockState(pos)
				.getValue(com.createengines.content.EngineCasingBlock.PAINT) == com.createengines.content.CasingPaint.NONE,
				"Water washes the paint off");
			helper.assertTrue(water.getCount() == 1 && water.is(net.minecraft.world.item.Items.WATER_BUCKET),
				"and the bucket is kept");
		}
		helper.succeed();
	}

	/**
	 * Casing clad onto a part takes dye too, and washes clean; a bare part ignores a dye and keeps it. The cased shaft and
	 * cogs, which are always clad, take it as well.
	 */
	@GameTest(template = "empty_large", timeoutTicks = 100)
	public static void cladPartsTakeDyeAndBarePartsDoNot(GameTestHelper helper) {
		net.minecraft.world.entity.player.Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
		BlockState[] clad = {
			CEBlocks.CYLINDER.getDefaultState()
				.setValue(com.createengines.content.Encasing.ENCASED, true),
			CEBlocks.CRANKSHAFT.getDefaultState()
				.setValue(com.createengines.content.Encasing.ENCASED, true),
			CEBlocks.CAMSHAFT.getDefaultState()
				.setValue(com.createengines.content.Encasing.ENCASED, true),
			CEBlocks.INTAKE_MANIFOLD.getDefaultState()
				.setValue(com.createengines.content.Encasing.ENCASED, true),
			CEBlocks.ENGINE_CASED_SHAFT.getDefaultState(), CEBlocks.ENGINE_CASED_COGWHEEL.getDefaultState(),
			CEBlocks.ENGINE_CASED_LARGE_COGWHEEL.getDefaultState()};
		for (int i = 0; i < clad.length; i++) {
			BlockPos pos = new BlockPos(2 + i * 2, 2, 2);
			helper.setBlock(pos, clad[i]);
			net.minecraft.world.item.ItemStack dye = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BLUE_DYE, 2);
			dye(helper, player, pos, dye);
			helper.assertTrue(dyeOf(helper, pos) == com.createengines.content.CasingPaint.BLUE,
				"Blue dye should tint the casing on " + clad[i]);
			helper.assertTrue(dye.getCount() == 1, "One dye is used up on " + clad[i]);
			dye(helper, player, pos, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WATER_BUCKET));
			helper.assertTrue(dyeOf(helper, pos) == com.createengines.content.CasingPaint.NONE,
				"Water should wash " + clad[i]);
		}
		// A bare cylinder has no casing to colour, and the dye is not wasted
		BlockPos bare = new BlockPos(2, 2, 6);
		helper.setBlock(bare, CEBlocks.CYLINDER.getDefaultState());
		net.minecraft.world.item.ItemStack dye = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BLUE_DYE, 2);
		dye(helper, player, bare, dye);
		helper.assertTrue(dyeOf(helper, bare) == com.createengines.content.CasingPaint.NONE
			&& dye.getCount() == 2, "A bare cylinder takes no dye");
		helper.succeed();
	}

	/**
	 * A rain cap goes on the mouth of an exhaust outlet and nowhere else, leaves the mouth open to the engine (it is only
	 * for looks), and goes with the outlet it sits on.
	 */
	@GameTest(template = "empty_large", timeoutTicks = 100)
	public static void rainCapSitsOnAnExhaustMouthAndNeverSealsIt(GameTestHelper helper) {
		BlockPos outlet = new BlockPos(2, 2, 2);
		BlockPos cap = outlet.above();
		helper.setBlock(outlet, CEBlocks.EXHAUST_OUTLET.getDefaultState()
			.setValue(com.createengines.content.airflow.PortOpeningBlock.FACING, net.minecraft.core.Direction.UP));
		var state = CEBlocks.RAIN_CAP.getDefaultState()
			.setValue(com.createengines.content.raincap.RainCapBlock.FACING, net.minecraft.core.Direction.UP);
		helper.assertTrue(com.createengines.content.raincap.RainCapBlock.sitsOnOutlet(helper.getLevel(),
			helper.absolutePos(cap), net.minecraft.core.Direction.UP), "A cap fits the mouth of an upward outlet");
		helper.assertFalse(com.createengines.content.raincap.RainCapBlock.sitsOnOutlet(helper.getLevel(),
			helper.absolutePos(cap), net.minecraft.core.Direction.EAST), "but not facing some other way");
		helper.setBlock(cap, state);
		helper.assertTrue(helper.getBlockEntity(cap) instanceof com.createengines.content.raincap.RainCapBlockEntity,
			"A cap has its block entity");
		helper.assertTrue(com.createengines.content.airflow.PortOpeningBlock.isOpen(helper.getLevel(),
			helper.absolutePos(outlet), helper.getBlockState(outlet)), "The cap does not cover the mouth");
		// An intake mouth takes no cap
		BlockPos intake = new BlockPos(6, 2, 2);
		helper.setBlock(intake, CEBlocks.AIR_INTAKE.getDefaultState()
			.setValue(com.createengines.content.airflow.PortOpeningBlock.FACING, net.minecraft.core.Direction.UP));
		helper.assertFalse(com.createengines.content.raincap.RainCapBlock.sitsOnOutlet(helper.getLevel(),
			helper.absolutePos(intake.above()), net.minecraft.core.Direction.UP), "A cap is for exhaust, not intake");
		// Take the outlet away and the cap goes too, dropping its item
		helper.setBlock(outlet, net.minecraft.world.level.block.Blocks.AIR);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(helper.getBlockState(cap)
				.isAir(), "The cap goes with its outlet");
			helper.succeed();
		});
	}

	/**
	 * A maker's plate takes its name and builder when placed and the figures of an engine's finished curve when used,
	 * keeps them across a save, turns away a plate on no engine, and goes when what it is on goes.
	 */
	@GameTest(template = "empty", timeoutTicks = 900)
	public static void aMakersPlateStampsTheEnginesRun(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		BlockPos plate = new BlockPos(2, 2, 1);
		net.minecraft.world.entity.player.Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
		var stack = CEBlocks.MAKER_PLATE.asStack();
		stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
			net.minecraft.network.chat.Component.literal("Old Faithful"));
		helper.setBlock(plate, CEBlocks.MAKER_PLATE.getDefaultState()
			.setValue(com.createengines.content.plate.MakerPlateBlock.FACING, net.minecraft.core.Direction.NORTH));
		BlockPos abs = helper.absolutePos(plate);
		CEBlocks.MAKER_PLATE.get()
			.setPlacedBy(helper.getLevel(), abs, helper.getBlockState(plate), player, stack);
		com.createengines.content.plate.MakerPlateBlockEntity be = helper.getBlockEntity(plate);
		helper.assertTrue(be.name().equals("Old Faithful") && !be.builder().isEmpty(), "Name and builder: " + be.name() + "/" + be.builder());
		helper.assertFalse(be.figures().stamped(), "A new plate is blank");
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(800, () -> {
			var hit = new net.minecraft.world.phys.BlockHitResult(abs.getCenter(), net.minecraft.core.Direction.NORTH, abs, false);
			helper.getBlockState(plate)
				.useWithoutItem(helper.getLevel(), player, hit);
			helper.assertTrue(be.figures().stamped() && be.figures().peakPower() > 0 && be.figures().powerRpm() > 0,
				"The plate takes the run: " + be.figures());
			// It reads back after a save
			var tag = be.saveWithoutMetadata(helper.getLevel().registryAccess());
			var copy = new com.createengines.content.plate.MakerPlateBlockEntity(be.getType(), abs, helper.getBlockState(plate));
			copy.loadWithComponents(tag, helper.getLevel().registryAccess());
			helper.assertTrue(copy.name().equals("Old Faithful") && copy.figures().equals(be.figures()), "It survives a save");
			// Take the engine away and the plate goes
			helper.setBlock(new BlockPos(2, 2, 2), Blocks.AIR);
			helper.runAfterDelay(2, () -> {
				helper.assertTrue(helper.getBlockState(plate).isAir(), "The plate goes with what it is on");
				helper.succeed();
			});
		});
	}

	/** A plate on a block that is no engine has nothing to stamp. */
	@GameTest(template = "empty", timeoutTicks = 60)
	public static void aMakersPlateOffAnEngineStampsNothing(GameTestHelper helper) {
		BlockPos stone = new BlockPos(2, 2, 2);
		BlockPos plate = new BlockPos(2, 2, 1);
		helper.setBlock(stone, Blocks.STONE);
		helper.setBlock(plate, CEBlocks.MAKER_PLATE.getDefaultState()
			.setValue(com.createengines.content.plate.MakerPlateBlock.FACING, net.minecraft.core.Direction.NORTH));
		net.minecraft.world.entity.player.Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
		BlockPos abs = helper.absolutePos(plate);
		helper.getBlockState(plate)
			.useWithoutItem(helper.getLevel(), player,
				new net.minecraft.world.phys.BlockHitResult(abs.getCenter(), net.minecraft.core.Direction.NORTH, abs, false));
		com.createengines.content.plate.MakerPlateBlockEntity be = helper.getBlockEntity(plate);
		helper.assertFalse(be.figures().stamped(), "Nothing to stamp from a stone");
		helper.succeed();
	}

	/**
	 * Window Casing clads a crank or cam part like plain casing does, but marks it windowed, so the part inside shows
	 * through. It takes no hold on a cylinder or a pipe, gives its own item back, and takes a dye like the rest.
	 */
	@GameTest(template = "empty_large", timeoutTicks = 100)
	public static void windowCasingCladsCranksAndCamsAndShowsThem(GameTestHelper helper) {
		net.minecraft.world.entity.player.Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
		var encased = com.createengines.content.Encasing.ENCASED;
		var windowed = com.createengines.content.Encasing.WINDOWED;
		BlockState[] parts = {CEBlocks.CRANKSHAFT.getDefaultState(), CEBlocks.CRANK_THROW.getDefaultState(),
			CEBlocks.CAMSHAFT.getDefaultState(), CEBlocks.CAM_LOBE.getDefaultState()};
		for (int i = 0; i < parts.length; i++) {
			BlockPos pos = new BlockPos(2 + i * 2, 2, 2);
			helper.setBlock(pos, parts[i]);
			net.minecraft.world.item.ItemStack casing = CEBlocks.ENGINE_WINDOW_CASING.asStack(2);
			dye(helper, player, pos, casing);
			BlockState state = helper.getBlockState(pos);
			helper.assertTrue(state.getValue(encased) && state.getValue(windowed), "Window casing should clad " + parts[i]);
			helper.assertTrue(casing.getCount() == 1, "One window casing is used up on " + parts[i]);
			helper.assertTrue(state.getShape(helper.getLevel(), helper.absolutePos(pos))
				.equals(net.minecraft.world.phys.shapes.Shapes.block()), "A clad part is a full block");
			helper.assertTrue(com.createengines.content.Encasing.casingStack(state)
				.is(CEBlocks.ENGINE_WINDOW_CASING.asItem()), "It gives the window casing back");
			dye(helper, player, pos, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GREEN_DYE));
			helper.assertTrue(dyeOf(helper, pos) == com.createengines.content.CasingPaint.GREEN,
				"A windowed part takes a dye too");
		}
		// Plain casing on a crank is not windowed, and returns plain casing
		BlockPos plain = new BlockPos(2, 2, 6);
		helper.setBlock(plain, CEBlocks.CRANKSHAFT.getDefaultState());
		dye(helper, player, plain, CEBlocks.ENGINE_CASING.asStack(1));
		helper.assertTrue(helper.getBlockState(plain)
			.getValue(encased), "Plain casing clads a crank");
		helper.assertFalse(helper.getBlockState(plain)
			.getValue(windowed), "and leaves it without a window, state is " + helper.getBlockState(plain));
		helper.assertTrue(com.createengines.content.Encasing.casingStack(helper.getBlockState(plain))
			.is(CEBlocks.ENGINE_CASING.asItem()), "and gives plain casing back");
		// A cylinder and a pipe have no window to give: the window casing does nothing to them
		BlockPos cylinder = new BlockPos(6, 2, 6);
		helper.setBlock(cylinder, CEBlocks.CYLINDER.getDefaultState());
		dye(helper, player, cylinder, CEBlocks.ENGINE_WINDOW_CASING.asStack(1));
		helper.assertFalse(helper.getBlockState(cylinder)
			.getValue(encased), "A cylinder takes no window casing");
		// A rotor housing is a crank part that draws its own way: no window for it either
		BlockPos rotor = new BlockPos(8, 2, 6);
		helper.setBlock(rotor, CEBlocks.ROTOR_HOUSING.getDefaultState());
		dye(helper, player, rotor, CEBlocks.ENGINE_WINDOW_CASING.asStack(1));
		helper.assertFalse(helper.getBlockState(rotor)
			.getValue(encased), "A rotor housing takes no window casing");
		helper.succeed();
	}

	/**
	 * Every casing clads every part it can: the riveted and louvred plates clad cranks, cams, cylinders and pipes and are
	 * remembered (and given back) as that plate, the window casing clads cranks and cams, and each of them makes Create's
	 * encased shaft and cogwheels of its own, with valid block entities.
	 */
	@GameTest(template = "empty_large", timeoutTicks = 100)
	public static void everyCasingCladsEveryPartItCan(GameTestHelper helper) {
		net.minecraft.world.entity.player.Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
		var style = com.createengines.content.Encasing.STYLE;
		var encased = com.createengines.content.Encasing.ENCASED;
		var plates = java.util.Map.of(com.createengines.content.CasingStyle.RIVETED, CEBlocks.ENGINE_RIVETED_CASING,
			com.createengines.content.CasingStyle.LOUVRED, CEBlocks.ENGINE_LOUVRED_CASING);
		int row = 0;
		for (var plate : plates.entrySet()) {
			BlockState[] parts = {CEBlocks.CRANKSHAFT.getDefaultState(), CEBlocks.CRANK_THROW.getDefaultState(),
				CEBlocks.CAMSHAFT.getDefaultState(), CEBlocks.CAM_LOBE.getDefaultState(), CEBlocks.CYLINDER.getDefaultState(),
				CEBlocks.INTAKE_MANIFOLD.getDefaultState()};
			for (int i = 0; i < parts.length; i++) {
				BlockPos pos = new BlockPos(2 + i * 2, 2, 2 + row * 4);
				helper.setBlock(pos, parts[i]);
				net.minecraft.world.item.ItemStack casing = plate.getValue()
					.asStack(2);
				dye(helper, player, pos, casing);
				BlockState state = helper.getBlockState(pos);
				helper.assertTrue(state.getValue(encased) && state.getValue(style) == plate.getKey(),
					plate.getKey() + " casing should clad " + parts[i] + ", got " + state);
				helper.assertTrue(casing.getCount() == 1, "One casing is used up on " + parts[i]);
				helper.assertTrue(com.createengines.content.Encasing.casingStack(state)
					.is(plate.getValue()
						.asItem()), "It gives its own casing back, not the plain one");
				helper.assertTrue(com.createengines.content.Encasing.styleOf(state) == plate.getKey(), "styleOf reads it back");
				dye(helper, player, pos, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GREEN_DYE));
				helper.assertTrue(dyeOf(helper, pos) == com.createengines.content.CasingPaint.GREEN
					&& helper.getBlockState(pos)
						.getValue(style) == plate.getKey(), "Painting a clad part keeps its plate");
			}
			row++;
		}
		// A part clad before plates existed (no style recorded) is plain
		helper.assertTrue(com.createengines.content.Encasing.styleOf(CEBlocks.CRANKSHAFT.getDefaultState()
			.setValue(encased, true)) == com.createengines.content.CasingStyle.PLAIN, "A part with no plate is plain");
		// Each casing makes Create's shaft and cogwheels of its own kind, and each is a working kinetic block
		var variants = java.util.Map.of(CEBlocks.ENGINE_RIVETED_CASING.get(),
			java.util.List.of(CEBlocks.ENGINE_RIVETED_CASED_SHAFT, CEBlocks.ENGINE_RIVETED_CASED_COGWHEEL,
				CEBlocks.ENGINE_RIVETED_CASED_LARGE_COGWHEEL),
			CEBlocks.ENGINE_LOUVRED_CASING.get(),
			java.util.List.of(CEBlocks.ENGINE_LOUVRED_CASED_SHAFT, CEBlocks.ENGINE_LOUVRED_CASED_COGWHEEL,
				CEBlocks.ENGINE_LOUVRED_CASED_LARGE_COGWHEEL),
			CEBlocks.ENGINE_WINDOW_CASING.get(),
			java.util.List.of(CEBlocks.ENGINE_WINDOW_CASED_SHAFT, CEBlocks.ENGINE_WINDOW_CASED_COGWHEEL,
				CEBlocks.ENGINE_WINDOW_CASED_LARGE_COGWHEEL));
		net.minecraft.world.level.block.Block[] bases = {AllBlocks.SHAFT.get(), AllBlocks.COGWHEEL.get(),
			AllBlocks.LARGE_COGWHEEL.get()};
		int x = 1;
		for (var entry : variants.entrySet())
			for (int i = 0; i < bases.length; i++) {
				var block = entry.getValue()
					.get(i)
					.get();
				helper.assertTrue(com.simibubi.create.content.decoration.encasing.EncasingRegistry.getVariants(bases[i])
					.stream()
					.anyMatch(variant -> variant == block
						&& variant instanceof com.simibubi.create.content.decoration.encasing.EncasedBlock cased
						&& cased.getCasing() == entry.getKey()), entry.getKey() + " should encase " + bases[i]);
				BlockPos pos = new BlockPos(x++, 2, 14);
				helper.setBlock(pos, block.defaultBlockState());
				helper.assertTrue(helper.getBlockEntity(pos) instanceof KineticBlockEntity,
					"A cased shaft or cog is a kinetic block: " + block);
			}
		helper.succeed();
	}

	private static void dye(GameTestHelper helper, net.minecraft.world.entity.player.Player player, BlockPos pos,
							net.minecraft.world.item.ItemStack stack) {
		net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
			net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(pos)), net.minecraft.core.Direction.UP,
			helper.absolutePos(pos), false);
		helper.getBlockState(pos)
			.useItemOn(stack, helper.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
	}

	/** The dye on whatever is at a position: a casing holds it in its state, a clad part in its block entity. */
	private static com.createengines.content.CasingPaint dyeOf(GameTestHelper helper, BlockPos pos) {
		BlockState state = helper.getBlockState(pos);
		return state.hasProperty(com.createengines.content.EngineCasingBlock.PAINT)
			? state.getValue(com.createengines.content.EngineCasingBlock.PAINT)
			: com.createengines.content.PaintedBlockEntity.at(helper.getLevel(), helper.absolutePos(pos));
	}
}
