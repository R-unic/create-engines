package com.createengines.gametest;

import static com.createengines.gametest.EngineRigs.*;

import com.createengines.CreateEngines;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.content.cylinder.CylinderBlock;
import com.createengines.content.head.CylinderHeadBlock;
import com.createengines.content.head.CylinderHeadBlockEntity;
import com.createengines.content.tool.BankBuilderItem;
import com.createengines.content.tool.TimingToolItem;
import com.createengines.engine.CylinderStatus;
import com.createengines.engine.topology.EngineLayout;
import com.createengines.engine.topology.EngineScanner;
import com.createengines.registry.CEBlocks;
import com.createengines.registry.CEItems;
import com.simibubi.create.AllBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Flywheels, tuning and the Bank Builder. Test engines as in {@link EngineGameTests}. */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class FlywheelBankGameTests {

	// New in the early-game, flywheel, turbo, bank and dynamometer pass

	/** A Cast Flywheel on the crank's shaft line is counted as rotating mass by the scanner. */
	@GameTest(template = "empty")
	public static void aCastFlywheelOnTheCrankLineAddsItsInertia(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		EngineLayout bare = EngineScanner.scan(helper.getLevel(), helper.absolutePos(PRIMARY));
		helper.assertTrue(bare != null && bare.flywheelInertia() == 0, "No flywheel yet: " + bare);
		helper.setBlock(new BlockPos(0, 2, 2), CEBlocks.CAST_FLYWHEEL.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.X));
		EngineLayout layout = EngineScanner.scan(helper.getLevel(), helper.absolutePos(PRIMARY));
		helper.assertTrue(layout != null
			&& Math.abs(layout.flywheelInertia() - com.createengines.config.CEConfig.CAST_FLYWHEEL_INERTIA.get()) < 1e-9,
			"The cast flywheel should add its inertia: " + (layout == null ? null : layout.flywheelInertia()));
		helper.succeed();
	}

	/**
	 * A Dual-Mass Flywheel joins the crank with its primary mass only, and counts its secondary mass apart from the crank's:
	 * the layout keeps both, a Cast Flywheel on the same line adds no secondary, and the two add up.
	 */
	@GameTest(template = "empty")
	public static void aDualMassFlywheelCountsItsPrimaryOnTheCrankAndItsSecondaryApart(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		double primary = com.createengines.config.CEConfig.DUAL_MASS_PRIMARY_INERTIA.get();
		double secondary = com.createengines.config.CEConfig.DUAL_MASS_SECONDARY_INERTIA.get();
		double cast = com.createengines.config.CEConfig.CAST_FLYWHEEL_INERTIA.get();
		helper.setBlock(new BlockPos(0, 2, 2), CEBlocks.DUAL_MASS_FLYWHEEL.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.X));
		EngineLayout dual = EngineScanner.scan(helper.getLevel(), helper.absolutePos(PRIMARY));
		helper.assertTrue(dual != null && Math.abs(dual.flywheelInertia() - primary) < 1e-9 && dual.flywheelInertia() > 0,
			"The primary mass should join the crank's flywheel inertia: " + (dual == null ? null : dual.flywheelInertia()));
		helper.assertTrue(Math.abs(dual.isolatedInertia() - secondary) < 1e-9,
			"The secondary mass should be counted apart: " + dual.isolatedInertia());
		// A Cast Flywheel on the crank's other end adds crank inertia and no secondary
		helper.setBlock(new BlockPos(4, 2, 2), CEBlocks.CAST_FLYWHEEL.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.X));
		EngineLayout both = EngineScanner.scan(helper.getLevel(), helper.absolutePos(PRIMARY));
		helper.assertTrue(both != null && Math.abs(both.flywheelInertia() - (primary + cast)) < 1e-9
				&& Math.abs(both.isolatedInertia() - secondary) < 1e-9,
			"A cast flywheel on the same line should add only to the crank: " + both);
		helper.succeed();
	}

	/** On a running engine the secondary mass leaves the crank's inertia, and so its rev response, alone. */
	@GameTest(template = "empty", timeoutTicks = 400)
	public static void aDualMassFlywheelOnARunningEngineAddsOnlyItsPrimaryToTheCrank(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		float[] bare = new float[1];
		helper.runAtTickTime(20, () -> bare[0] = summary(helper).inertia());
		helper.runAtTickTime(21, () -> helper.setBlock(new BlockPos(0, 2, 2), CEBlocks.DUAL_MASS_FLYWHEEL.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.X)));
		helper.runAtTickTime(120, () -> {
			double added = summary(helper).inertia() - bare[0];
			helper.assertTrue(Math.abs(added - com.createengines.config.CEConfig.DUAL_MASS_PRIMARY_INERTIA.get()) < 1e-4,
				"The crank should carry the primary mass only, not the secondary: added " + added);
			EngineLayout layout = helper.<CrankshaftBlockEntity>getBlockEntity(PRIMARY).controller().layout();
			helper.assertTrue(layout.isolatedInertia() > 0, "The running engine should know its secondary mass");
			helper.succeed();
		});
	}

	/**
	 * A running engine with a Cast Flywheel says what the flywheel is worth (the ripple it has and the ripple it would have
	 * without), and a bare one says nothing. Information only: fitting it changes no other figure but the ripple itself.
	 */
	@GameTest(template = "empty", timeoutTicks = 400)
	public static void aFlywheelOnARunningEngineReportsWhatItIsWorth(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		helper.runAtTickTime(100, () -> {
			for (var line : summary(helper).sourceLines())
				helper.assertFalse(line.key().startsWith("create_engines.goggles.flywheel"), "A bare engine has no flywheel line: " + line);
			helper.setBlock(new BlockPos(0, 2, 2), CEBlocks.CAST_FLYWHEEL.getDefaultState()
				.setValue(BlockStateProperties.AXIS, Axis.X));
		});
		helper.runAtTickTime(300, () -> {
			var lines = summary(helper).sourceLines().stream()
				.filter(line -> line.key().startsWith("create_engines.goggles.flywheel"))
				.toList();
			helper.assertTrue(lines.size() == 1, "A flywheel on a running engine should give one line: " + summary(helper).sourceLines());
			var args = lines.get(0).args();
			helper.assertTrue(Integer.parseInt(args.get(0)) <= Integer.parseInt(args.get(1)),
				"With the flywheel the ripple is no more than without: " + args);
			helper.succeed();
		});
	}

	/** Breaking a Dual-Mass Flywheel takes both masses off the line at once, and putting it back returns them. */
	@GameTest(template = "empty")
	public static void breakingAndReplacingADualMassFlywheelTakesBothMassesOffAndBack(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		BlockPos at = new BlockPos(0, 2, 2);
		helper.setBlock(at, CEBlocks.DUAL_MASS_FLYWHEEL.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.X));
		helper.setBlock(at, Blocks.AIR);
		EngineLayout gone = EngineScanner.scan(helper.getLevel(), helper.absolutePos(PRIMARY));
		helper.assertTrue(gone != null && gone.flywheelInertia() == 0 && gone.isolatedInertia() == 0,
			"Both masses should be gone with the block: " + gone);
		helper.setBlock(at, CEBlocks.DUAL_MASS_FLYWHEEL.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.X));
		EngineLayout back = EngineScanner.scan(helper.getLevel(), helper.absolutePos(PRIMARY));
		helper.assertTrue(back != null && back.flywheelInertia() > 0 && back.isolatedInertia() > 0, "Both should be back: " + back);
		helper.succeed();
	}

	/**
	 * A Dual-Mass Flywheel's spring-pack dial: the middle setting leaves the engine's numbers exactly as the server settings give
	 * them, and a softer or stiffer one moves the secondary mass and where it resonates (softer: lighter and lower, stiffer: heavier
	 * and higher). Turning the dial rescans the engine on its own.
	 */
	@GameTest(template = "empty")
	public static void theSpringPackDialMovesTheSecondaryMassAndTheResonance(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		BlockPos at = new BlockPos(0, 2, 2);
		helper.setBlock(at, CEBlocks.DUAL_MASS_FLYWHEEL.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.X));
		var flywheel = helper.<com.createengines.content.flywheel.CastFlywheelBlockEntity>getBlockEntity(at);
		double secondary = com.createengines.config.CEConfig.DUAL_MASS_SECONDARY_INERTIA.get();
		helper.assertTrue(flywheel.springPack != null
				&& flywheel.springPack.getValue() == com.createengines.engine.sim.SpringPack.DEFAULT_SETTING,
			"A new flywheel should start at the middle setting");
		EngineLayout standard = EngineScanner.scan(helper.getLevel(), helper.absolutePos(PRIMARY));
		helper.assertTrue(standard != null && standard.isolatedInertia() == secondary && standard.isolatedResonanceFactor() == 1.0,
			"The middle setting should leave both figures exactly as set: " + standard);
		flywheel.springPack.setValue(com.createengines.engine.sim.SpringPack.MIN_SETTING);
		EngineLayout soft = EngineScanner.scan(helper.getLevel(), helper.absolutePos(PRIMARY));
		helper.assertTrue(soft != null && soft.isolatedInertia() < secondary && soft.isolatedResonanceFactor() < 1.0,
			"The softest setting should be lighter and resonate lower: " + soft);
		flywheel.springPack.setValue(com.createengines.engine.sim.SpringPack.MAX_SETTING);
		EngineLayout stiff = EngineScanner.scan(helper.getLevel(), helper.absolutePos(PRIMARY));
		helper.assertTrue(stiff != null && stiff.isolatedInertia() > secondary && stiff.isolatedResonanceFactor() > 1.0,
			"The stiffest setting should be heavier and resonate higher: " + stiff);
		helper.assertTrue(Math.abs(stiff.flywheelInertia() - standard.flywheelInertia()) < 1e-12,
			"The dial should not touch the primary mass on the crank");
		helper.succeed();
	}

	/**
	 * The spring-pack dial's boxes float outside the wheel, so a hit anywhere on the block is shared out between the four of them by
	 * where it lies round the shaft, whichever face the ray struck: a hit on the end of the outline from a shallow angle still opens
	 * one box, and never two or none.
	 */
	@GameTest(template = "empty")
	public static void everyHitOnADualMassFlywheelOpensExactlyOneSpringPackBox(GameTestHelper helper) {
		var shaft = Axis.X;
		var sides = new net.minecraft.core.Direction[] {net.minecraft.core.Direction.UP, net.minecraft.core.Direction.DOWN,
			net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.SOUTH};
		// A hit on the top face, on the outline's end (the shaft axis plane) high, low and to either side, and near a corner
		double[][] hits = {{0.5, 1, 0.5}, {0.3, 1, 0.8}, {0.25, 0.9, 0.5}, {0.75, 0.1, 0.5}, {0.5, 0.5, 0.05}, {0.5, 0.5, 0.95},
			{0.25, 0.6, 0.95}, {0.75, 0.55, 0.02}};
		for (double[] h : hits) {
			int count = 0;
			for (var side : sides)
				if (com.createengines.content.flywheel.CastFlywheelBlockEntity.ownsHit(side, shaft,
					new net.minecraft.world.phys.Vec3(h[0], h[1], h[2])))
					count++;
			helper.assertTrue(count == 1, "A hit at " + java.util.Arrays.toString(h) + " should open one box, opened " + count);
		}
		for (var end : new net.minecraft.core.Direction[] {net.minecraft.core.Direction.EAST, net.minecraft.core.Direction.WEST})
			helper.assertFalse(com.createengines.content.flywheel.CastFlywheelBlockEntity.ownsHit(end, shaft,
				new net.minecraft.world.phys.Vec3(0.5, 0.9, 0.5)), "The shaft's ends have no box");
		helper.succeed();
	}

	/** The Timing Tool's tune mode sets a flooded injector back to a balanced mixture. */
	@GameTest(template = "empty", timeoutTicks = 1700)
	public static void tuningAFloodedEngineBringsTheMixtureBack(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		CylinderHeadBlockEntity head = helper.getBlockEntity(HEAD);
		head.metering.setValue(CylinderHeadBlockEntity.MAX_METERING);
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(900, () -> {
			helper.assertTrue(cylinder(helper).rich(), "Twice the fuel should run rich first: " + cylinder(helper));
			var tuning = TimingToolItem.tuneFuel(helper.getLevel(), helper.absolutePos(HEAD));
			helper.assertTrue(tuning != null && tuning.tuned() == 1, "One injector should be tuned: " + tuning);
			helper.assertTrue(head.metering.getValue() < CylinderHeadBlockEntity.MAX_METERING,
				"Tuning should turn the shot down: " + head.metering.getValue());
		});
		helper.runAtTickTime(1500, () -> {
			CylinderStatus status = cylinder(helper);
			helper.assertTrue(status.mixture() > 0.5f && status.mixture() < 1.3f, "Mixture after tuning: " + status);
			helper.succeed();
		});
	}

	/** Tuning a stopped engine does nothing, and says so. */
	@GameTest(template = "empty")
	public static void tuningAStoppedEngineChangesNothing(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		helper.runAtTickTime(20, () -> {
			var tuning = TimingToolItem.tuneFuel(helper.getLevel(), helper.absolutePos(HEAD));
			helper.assertTrue(tuning != null && tuning.tuned() == 0, "Nothing to tune yet: " + tuning);
			helper.succeed();
		});
	}

	/** The Bank Builder lays a cylinder and a head on the chosen faces of every throw. */
	@GameTest(template = "empty")
	public static void theBankBuilderLaysOutAFlatAndAVeeBank(GameTestHelper helper) {
		for (int x = 1; x <= 5; x++)
			helper.setBlock(new BlockPos(x, 3, 3), (x % 2 == 0 ? CEBlocks.CRANK_THROW : CEBlocks.CRANKSHAFT).getDefaultState()
				.setValue(BlockStateProperties.AXIS, Axis.X));
		var player = helper.makeMockPlayer(GameType.CREATIVE);
		var flat = BankBuilderItem.build(helper.getLevel(), helper.absolutePos(new BlockPos(2, 3, 3)), Direction.UP,
			BankBuilderItem.Layout.FLAT, player, true);
		helper.assertTrue(flat.throwsFound() == 2 && flat.cylinders() == 4 && flat.heads() == 4, "A flat bank: " + flat);
		for (int x : new int[] {2, 4}) {
			helper.assertBlockState(new BlockPos(x, 4, 3), s -> s.getBlock() instanceof CylinderBlock
				&& s.getValue(CylinderBlock.FACING) == Direction.UP, () -> "Cylinder up on throw " + x);
			helper.assertBlockState(new BlockPos(x, 2, 3), s -> s.getBlock() instanceof CylinderBlock
				&& s.getValue(CylinderBlock.FACING) == Direction.DOWN, () -> "Cylinder down on throw " + x);
			helper.assertBlockState(new BlockPos(x, 5, 3), s -> s.getBlock() instanceof CylinderHeadBlock, () -> "Head up");
			helper.assertBlockState(new BlockPos(x, 1, 3), s -> s.getBlock() instanceof CylinderHeadBlock, () -> "Head down");
			helper.assertBlockState(new BlockPos(x, 4, 3),
				s -> s.getValue(CylinderBlock.HAS_PISTON) && s.getValue(CylinderBlock.HAS_ROD),
				() -> "A creative builder fits the piston and rod");
		}
		// Asking again is harmless: what is built is only filled in
		var again = BankBuilderItem.build(helper.getLevel(), helper.absolutePos(new BlockPos(2, 3, 3)), Direction.UP,
			BankBuilderItem.Layout.FLAT, player, true);
		helper.assertTrue(again.cylinders() == 0 && again.heads() == 0, "Nothing new to place: " + again);

		// A vee puts the second bank a quarter turn round
		var vee = BankBuilderItem.build(helper.getLevel(), helper.absolutePos(new BlockPos(2, 3, 3)), Direction.NORTH,
			BankBuilderItem.Layout.VEE, player, true);
		helper.assertTrue(vee.cylinders() >= 2, "Vee placed its new cylinders: " + vee);
		BlockPos second = new BlockPos(2, 3, 3).relative(Direction.NORTH.getClockWise(Axis.X));
		helper.assertBlockState(second, s -> s.getBlock() instanceof CylinderBlock, () -> "Vee second bank at " + second);
		helper.succeed();
	}

	/** Out of parts, a survival builder places only what it has the blocks for. */
	@GameTest(template = "empty")
	public static void theBankBuilderReportsWhatItIsMissing(GameTestHelper helper) {
		for (int x = 1; x <= 3; x++)
			helper.setBlock(new BlockPos(x, 3, 3), (x == 2 ? CEBlocks.CRANK_THROW : CEBlocks.CRANKSHAFT).getDefaultState()
				.setValue(BlockStateProperties.AXIS, Axis.X));
		var player = helper.makeMockPlayer(GameType.SURVIVAL);
		var result = BankBuilderItem.build(helper.getLevel(), helper.absolutePos(new BlockPos(2, 3, 3)), Direction.UP,
			BankBuilderItem.Layout.INLINE, player, false);
		helper.assertTrue(result.cylinders() == 0 && result.missing() == 1, "An empty inventory builds nothing: " + result);
		player.getInventory()
			.add(new ItemStack(CEBlocks.CYLINDER.get(), 1));
		player.getInventory()
			.add(new ItemStack(CEBlocks.CYLINDER_HEAD.get(), 1));
		var next = BankBuilderItem.build(helper.getLevel(), helper.absolutePos(new BlockPos(2, 3, 3)), Direction.UP,
			BankBuilderItem.Layout.INLINE, player, false);
		helper.assertTrue(next.cylinders() == 1 && next.heads() == 1 && next.missing() == 0,
			"With a cylinder and a head: " + next);
		helper.succeed();
	}

	/** A precision build places precision parts, and swaps them in for standard ones, handing the old ones back. */
	@GameTest(template = "empty")
	public static void thePrecisionBankBuilderSwapsInPrecisionParts(GameTestHelper helper) {
		for (int x = 1; x <= 3; x++)
			helper.setBlock(new BlockPos(x, 3, 3), (x == 2 ? CEBlocks.CRANK_THROW : CEBlocks.CRANKSHAFT).getDefaultState()
				.setValue(BlockStateProperties.AXIS, Axis.X));
		BlockPos throwPos = new BlockPos(2, 3, 3);
		var creative = helper.makeMockPlayer(GameType.CREATIVE);
		BankBuilderItem.build(helper.getLevel(), helper.absolutePos(throwPos), Direction.UP,
			BankBuilderItem.Layout.INLINE, creative, true);
		helper.assertBlockState(new BlockPos(2, 4, 3), s -> s.getBlock() == CEBlocks.CYLINDER.get(),
			() -> "A standard build places the standard cylinder");

		var player = helper.makeMockPlayer(GameType.SURVIVAL);
		for (var block : new Block[] {CEBlocks.PRECISION_CYLINDER.get(), CEBlocks.PRECISION_CYLINDER_HEAD.get()})
			player.getInventory()
				.add(new ItemStack(block, 1));
		player.getInventory()
			.add(CEItems.FORGED_PISTON.asStack());
		player.getInventory()
			.add(CEItems.PRECISION_INJECTOR.asStack());
		var result = BankBuilderItem.build(helper.getLevel(), helper.absolutePos(throwPos), Direction.UP,
			BankBuilderItem.Layout.INLINE, true, player, false);
		helper.assertTrue(result.upgraded() == 4, "Cylinder, head, piston and injector swapped: " + result);
		helper.assertBlockState(new BlockPos(2, 4, 3), s -> s.getBlock() == CEBlocks.PRECISION_CYLINDER.get()
			&& s.getValue(CylinderBlock.FORGED_PISTON) && s.getValue(CylinderBlock.HAS_ROD),
			() -> "The cylinder is precision with a forged piston and its rod kept");
		helper.assertBlockState(new BlockPos(2, 5, 3), s -> s.getBlock() == CEBlocks.PRECISION_CYLINDER_HEAD.get()
			&& s.getValue(CylinderHeadBlock.FINE_INJECTOR), () -> "The head is precision with a precision injector");
		helper.assertTrue(player.getInventory()
			.contains(new ItemStack(CEBlocks.CYLINDER.get())), "The standard cylinder came back");
		helper.assertTrue(player.getInventory()
			.contains(CEItems.PISTON.asStack()), "And so did the standard piston");
		helper.succeed();
	}

	/**
	 * A builder carrying spark plugs fits them in the heads, and one carrying a camshaft and cam lobes lays a cam line
	 * beside the bank; a creative builder with none of either does neither, so a diesel stays a diesel. An inline bank
	 * gets a lobe beside each cylinder block and camshaft blocks in the gaps, and a vee a lobe in its valley.
	 */
	@GameTest(template = "empty")
	public static void theBankBuilderLaysPlugsAndCamsOnlyIfItIsCarryingThem(GameTestHelper helper) {
		for (int x = 1; x <= 5; x++)
			helper.setBlock(new BlockPos(x, 3, 3), (x == 2 || x == 4 ? CEBlocks.CRANK_THROW : CEBlocks.CRANKSHAFT)
				.getDefaultState()
				.setValue(BlockStateProperties.AXIS, Axis.X));
		Direction beside = Direction.UP.getClockWise(Axis.X);
		var creative = helper.makeMockPlayer(GameType.CREATIVE);

		var plain = BankBuilderItem.build(helper.getLevel(), helper.absolutePos(new BlockPos(2, 3, 3)), Direction.UP,
			BankBuilderItem.Layout.INLINE, creative, true);
		helper.assertTrue(plain.cylinders() == 2 && plain.camBlocks() == 0, "Nothing carried, no cams laid: " + plain);
		helper.assertBlockState(new BlockPos(2, 5, 3), s -> s.getBlock() instanceof CylinderHeadBlock
			&& !s.getValue(CylinderHeadBlock.HAS_SPARK_PLUG), () -> "A diesel head has no plug");

		creative.getInventory()
			.add(new ItemStack(CEItems.SPARK_PLUG.get(), 2));
		creative.getInventory()
			.add(new ItemStack(CEBlocks.CAM_LOBE.get(), 2));
		creative.getInventory()
			.add(new ItemStack(CEBlocks.CAMSHAFT.get(), 1));
		var cammed = BankBuilderItem.build(helper.getLevel(), helper.absolutePos(new BlockPos(2, 3, 3)), Direction.UP,
			BankBuilderItem.Layout.INLINE, creative, true);
		helper.assertTrue(cammed.camBlocks() == 3, "Two lobes and the camshaft between them: " + cammed);
		for (int x : new int[] {2, 4}) {
			helper.assertBlockState(new BlockPos(x, 5, 3), s -> s.getValue(CylinderHeadBlock.HAS_SPARK_PLUG),
				() -> "The carried plugs were fitted");
			helper.assertBlockState(new BlockPos(x, 3, 3).relative(Direction.UP)
				.relative(beside), s -> s.getBlock() == CEBlocks.CAM_LOBE.get() && s.getValue(BlockStateProperties.AXIS) == Axis.X,
				() -> "A lobe beside the cylinder block at x=" + x);
		}
		helper.assertBlockState(new BlockPos(3, 3, 3).relative(Direction.UP)
			.relative(beside), s -> s.getBlock() == CEBlocks.CAMSHAFT.get(), () -> "A camshaft block joins the lobes");
		helper.succeed();
	}

	/** A vee has its one lobe in the valley, which touches the cylinder block of both banks. */
	@GameTest(template = "empty")
	public static void theBankBuilderPutsAVeesLobeInItsValley(GameTestHelper helper) {
		for (int x = 1; x <= 5; x++)
			helper.setBlock(new BlockPos(x, 3, 3), (x == 2 || x == 4 ? CEBlocks.CRANK_THROW : CEBlocks.CRANKSHAFT)
				.getDefaultState()
				.setValue(BlockStateProperties.AXIS, Axis.X));
		Direction beside = Direction.UP.getClockWise(Axis.X);
		var creative = helper.makeMockPlayer(GameType.CREATIVE);
		creative.getInventory()
			.add(new ItemStack(CEBlocks.CAM_LOBE.get(), 2));
		creative.getInventory()
			.add(new ItemStack(CEBlocks.CAMSHAFT.get(), 1));
		var vee = BankBuilderItem.build(helper.getLevel(), helper.absolutePos(new BlockPos(2, 3, 3)), Direction.UP,
			BankBuilderItem.Layout.VEE, creative, true);
		helper.assertTrue(vee.cylinders() == 4 && vee.camBlocks() == 3, "Two banks and one cam line: " + vee);
		for (int x : new int[] {2, 4}) {
			BlockPos valley = new BlockPos(x, 3, 3).relative(Direction.UP)
				.relative(beside);
			helper.assertBlockState(valley, s -> s.getBlock() == CEBlocks.CAM_LOBE.get(), () -> "A lobe in the valley at x=" + x);
			helper.assertBlockState(new BlockPos(x, 3, 3).relative(Direction.UP), s -> s.getBlock() instanceof CylinderBlock,
				() -> "It touches the first bank at x=" + x);
			helper.assertBlockState(new BlockPos(x, 3, 3).relative(beside), s -> s.getBlock() instanceof CylinderBlock,
				() -> "And the second at x=" + x);
		}
		helper.succeed();
	}

	/**
	 * Placing a cogwheel that gears two cranks together couples them at once. Create's blocks do not tell the engine
	 * scanner they were placed, so without the hook the coupling waited for the next revalidation, up to five seconds on.
	 */
	@GameTest(template = "empty", timeoutTicks = 200)
	public static void aCogPlacedBetweenTwoCranksCouplesThemAtOnce(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		TestEngine.addCoupledLine(helper.getLevel(), helper.absolutePos(BlockPos.ZERO));
		BlockPos gap = new BlockPos(0, 2, 3);
		helper.setBlock(gap, Blocks.AIR);
		helper.runAtTickTime(20, () -> {
			EngineLayout apart = EngineScanner.scan(helper.getLevel(), helper.absolutePos(PRIMARY));
			helper.assertTrue(apart != null && apart.lines() == 1, "Without the middle cog the lines are separate");
			BlockState cog = alongX(AllBlocks.COGWHEEL.getDefaultState());
			helper.setBlock(gap, cog);
			com.createengines.engine.topology.EngineTopology.gearingChanged(helper.getLevel(), helper.absolutePos(gap), cog);
		});
		// Four ticks later, far inside the revalidation interval: the engine has been rescanned and has both lines
		helper.runAtTickTime(24, () -> {
			CrankshaftBlockEntity first = helper.getBlockEntity(PRIMARY);
			helper.assertTrue(first.controller() != null && first.controller()
				.layout()
				.lines() == 2, "The engine should already have both lines: "
					+ (first.controller() == null ? null : first.controller()
						.layout()
						.lines()));
			helper.succeed();
		});
	}
}
