package com.createengines.gametest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.createengines.CreateEngines;
import com.createengines.content.blueprint.BlueprintBuilder;
import com.createengines.content.blueprint.BlueprintRecorder;
import com.createengines.content.blueprint.RecordedBlueprint;
import com.createengines.content.cylinder.CylinderBlock;
import com.createengines.content.head.CylinderHeadBlock;
import com.createengines.content.tool.BankBuilderItem;
import com.createengines.content.tool.BankBuilderItem.Layout;
import com.createengines.engine.blueprint.Blueprint;
import com.createengines.engine.blueprint.BlueprintCodec;
import com.createengines.engine.blueprint.BlueprintDiff;
import com.createengines.engine.blueprint.BlueprintException;
import com.createengines.engine.topology.CylinderFault;
import com.createengines.engine.topology.EngineLayout;
import com.createengines.engine.topology.EngineScanner;
import com.createengines.registry.CEBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The Bank Builder's W (three banks) and X (four banks) layouts. */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class BankLayoutGameTests {

	/** The layouts are saved on the item by their place in the list, so the old ones must keep theirs. */
	@GameTest(template = "empty")
	public static void theNewLayoutsGoAfterTheOldOnes(GameTestHelper helper) {
		helper.assertTrue(List.of(Layout.values())
			.equals(List.of(Layout.INLINE, Layout.VEE, Layout.FLAT, Layout.W, Layout.X)), "Layout order: " + List.of(Layout.values()));
		helper.succeed();
	}

	/** Which faces each layout builds on, for every face that can be clicked on an X crank. */
	@GameTest(template = "empty")
	public static void aWBuildsThreeFacesInARowAndAnXAllFour(GameTestHelper helper) {
		for (Direction clicked : new Direction[] {Direction.UP, Direction.NORTH, Direction.DOWN, Direction.SOUTH}) {
			List<Direction> w = BankBuilderItem.faces(Layout.W, Axis.X, clicked);
			helper.assertTrue(w.size() == 3 && new HashSet<>(w).size() == 3 && w.get(0) == clicked
				&& !w.contains(clicked.getOpposite()) && w.stream()
				.noneMatch(face -> face.getAxis() == Axis.X), "W from " + clicked + ": " + w);
			List<Direction> x = BankBuilderItem.faces(Layout.X, Axis.X, clicked);
			helper.assertTrue(new HashSet<>(x).equals(Set.of(Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH))
				&& x.get(0) == clicked, "X from " + clicked + ": " + x);
		}
		// The old layouts are as they were
		helper.assertTrue(BankBuilderItem.faces(Layout.INLINE, Axis.X, Direction.UP)
			.equals(List.of(Direction.UP)), "Inline");
		helper.assertTrue(BankBuilderItem.faces(Layout.VEE, Axis.X, Direction.UP)
			.equals(List.of(Direction.UP, Direction.UP.getClockWise(Axis.X))), "Vee");
		helper.assertTrue(BankBuilderItem.faces(Layout.FLAT, Axis.X, Direction.UP)
			.equals(List.of(Direction.UP, Direction.DOWN)), "Flat");
		helper.succeed();
	}

	/** Every cam line sits in a valley: diagonal to the throw, beside the block of two of the banks the layout builds. */
	@GameTest(template = "empty")
	public static void theCamLinesOfAWAndAnXSitInTheirValleys(GameTestHelper helper) {
		for (Axis axis : Axis.values()) {
			for (Direction clicked : Direction.values()) {
				if (clicked.getAxis() == axis)
					continue;
				for (Layout layout : new Layout[] {Layout.W, Layout.X}) {
					List<Direction> faces = BankBuilderItem.faces(layout, axis, clicked);
					List<BlockPos> offsets = BankBuilderItem.camOffsets(layout, axis, clicked);
					helper.assertTrue(offsets.size() == faces.size() - (layout == Layout.W ? 1 : 0)
						&& new HashSet<>(offsets).size() == offsets.size(), layout + " " + axis + " " + clicked + ": " + offsets);
					Set<Direction> served = new HashSet<>();
					for (BlockPos offset : offsets) {
						int touching = 0;
						for (Direction face : faces)
							if (offset.distManhattan(BlockPos.ZERO.relative(face)) == 1) {
								touching++;
								served.add(face);
							}
						helper.assertTrue(touching == 2 && axis.choose(offset.getX(), offset.getY(), offset.getZ()) == 0,
							layout + " lobe " + offset + " should touch two banks across the crank, touches " + touching);
					}
					helper.assertTrue(served.containsAll(faces), layout + " leaves a bank with no lobe: " + served);
				}
			}
		}
		// The vee and flat lines have not moved
		Direction beside = Direction.UP.getClockWise(Axis.X);
		helper.assertTrue(BankBuilderItem.camOffsets(Layout.VEE, Axis.X, Direction.UP)
			.equals(List.of(BlockPos.ZERO.above()
				.relative(beside))), "Vee line");
		helper.assertTrue(BankBuilderItem.camOffsets(Layout.FLAT, Axis.X, Direction.UP)
			.equals(List.of(BlockPos.ZERO.above()
				.relative(beside), BlockPos.ZERO.below()
				.relative(beside))), "Flat lines");
		helper.succeed();
	}

	private static void crank(GameTestHelper helper) {
		for (int x = 1; x <= 5; x++)
			helper.setBlock(new BlockPos(x, 3, 3), (x % 2 == 0 ? CEBlocks.CRANK_THROW : CEBlocks.CRANKSHAFT).getDefaultState()
				.setValue(BlockStateProperties.AXIS, Axis.X));
	}

	private static void clearAroundCrank(GameTestHelper helper) {
		for (int x = 1; x <= 5; x++)
			for (int y = 1; y <= 5; y++)
				for (int z = 1; z <= 5; z++)
					if (y != 3 || z != 3)
						helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
	}

	/** A W and an X, built from each face of the crank, put a cylinder and a head on each of their faces on every throw. */
	@GameTest(template = "empty")
	public static void aWAndAnXAreBuiltFromEveryFace(GameTestHelper helper) {
		crank(helper);
		var player = helper.makeMockPlayer(GameType.CREATIVE);
		for (Layout layout : new Layout[] {Layout.W, Layout.X}) {
			for (Direction clicked : new Direction[] {Direction.UP, Direction.NORTH, Direction.DOWN, Direction.SOUTH}) {
				clearAroundCrank(helper);
				var result = BankBuilderItem.build(helper.getLevel(), helper.absolutePos(new BlockPos(2, 3, 3)), clicked, layout,
					player, true);
				int banks = layout == Layout.W ? 3 : 4;
				helper.assertTrue(result.throwsFound() == 2 && result.cylinders() == 2 * banks && result.heads() == 2 * banks
					&& result.blocked() == 0 && result.missing() == 0, layout + " from " + clicked + ": " + result);
				for (int x : new int[] {2, 4})
					for (Direction bore : BankBuilderItem.faces(layout, Axis.X, clicked)) {
						BlockPos cylinder = new BlockPos(x, 3, 3).relative(bore);
						helper.assertBlockState(cylinder, s -> s.getBlock() instanceof CylinderBlock && s.getValue(CylinderBlock.FACING) == bore,
							() -> layout + " from " + clicked + ": cylinder " + bore + " at x=" + x);
						helper.assertBlockState(cylinder.relative(bore), s -> s.getBlock() instanceof CylinderHeadBlock
							&& s.getValue(CylinderHeadBlock.FACING) == bore, () -> layout + " from " + clicked + ": head " + bore + " at x=" + x);
					}
				if (layout == Layout.W)
					helper.assertBlockState(new BlockPos(2, 3, 3).relative(clicked.getOpposite()), s -> s.isAir(),
						() -> "A W leaves the face opposite the click empty");
			}
		}
		helper.succeed();
	}

	/** With cams carried, every cylinder of a W and of an X finds a lobe, and the scanner takes them as one engine. */
	@GameTest(template = "empty")
	public static void everyCylinderOfAWAndAnXHasALobe(GameTestHelper helper) {
		crank(helper);
		for (Layout layout : new Layout[] {Layout.W, Layout.X}) {
			clearAroundCrank(helper);
			var creative = helper.makeMockPlayer(GameType.CREATIVE);
			creative.getInventory()
				.add(new ItemStack(CEBlocks.CAM_LOBE.get(), 1));
			creative.getInventory()
				.add(new ItemStack(CEBlocks.CAMSHAFT.get(), 1));
			var result = BankBuilderItem.build(helper.getLevel(), helper.absolutePos(new BlockPos(2, 3, 3)), Direction.UP, layout,
				creative, true);
			int lines = layout == Layout.W ? 2 : 4;
			// Each line runs from the first throw to the last: a lobe, a camshaft over the journal, a lobe
			helper.assertTrue(result.camBlocks() == 3 * lines, layout + " cam blocks: " + result);
			EngineLayout scanned = EngineScanner.scan(helper.getLevel(), helper.absolutePos(new BlockPos(2, 3, 3)));
			int banks = layout == Layout.W ? 3 : 4;
			helper.assertTrue(scanned != null && scanned.cylinders()
				.size() == 2 * banks, layout + " scanned cylinders: " + (scanned == null ? null : scanned.cylinders()
				.size()));
			for (EngineLayout.Cylinder cylinder : scanned.cylinders())
				helper.assertTrue((cylinder.faults() & (CylinderFault.NO_CAM.bit() | CylinderFault.BROKEN_CAM.bit())) == 0,
					layout + ": the cylinder at " + cylinder.pos() + " has no cam");
		}
		helper.succeed();
	}

	/** A survival builder short of blocks places what it can and counts the rest; something in the way is counted as blocked. */
	@GameTest(template = "empty")
	public static void aWCountsWhatIsBlockedAndWhatIsMissing(GameTestHelper helper) {
		crank(helper);
		// Stone where the second throw's middle cylinder would go
		helper.setBlock(new BlockPos(4, 4, 3), Blocks.STONE);
		var player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.getInventory()
			.add(new ItemStack(CEBlocks.CYLINDER.get(), 4));
		player.getInventory()
			.add(new ItemStack(CEBlocks.CYLINDER_HEAD.get(), 2));
		var result = BankBuilderItem.build(helper.getLevel(), helper.absolutePos(new BlockPos(2, 3, 3)), Direction.UP, Layout.W,
			player, false);
		// Six places: one blocked, four cylinders placed, the fifth has no block; two heads go on, two more are missing
		helper.assertTrue(result.cylinders() == 4 && result.heads() == 2 && result.blocked() == 1 && result.missing() == 3,
			"W short of parts: " + result);
		helper.assertBlockState(new BlockPos(4, 4, 3), s -> s.is(Blocks.STONE), () -> "The stone is left alone");
		helper.succeed();
	}

	/** A W recorded on a Blueprint builds the same W somewhere else. */
	@GameTest(template = "empty_large")
	public static void aWBlueprintBuildsTheSameW(GameTestHelper helper) throws BlueprintException {
		crank(helper);
		var creative = helper.makeMockPlayer(GameType.CREATIVE);
		creative.getInventory()
			.add(new ItemStack(CEBlocks.CAM_LOBE.get(), 1));
		creative.getInventory()
			.add(new ItemStack(CEBlocks.CAMSHAFT.get(), 1));
		BankBuilderItem.build(helper.getLevel(), helper.absolutePos(new BlockPos(2, 3, 3)), Direction.UP, Layout.W, creative, true);
		BlockPos head = new BlockPos(2, 5, 3);
		BlueprintRecorder.Result recorded = BlueprintRecorder.record(helper.getLevel(), helper.absolutePos(head), "W", "t");
		if (!recorded.ok())
			helper.fail("recording the W failed: " + recorded.failure() + " " + recorded.detail());
		RecordedBlueprint plan = new RecordedBlueprint(BlueprintCodec.encode(recorded.blueprint()), "W", recorded.blueprint()
			.parts()
			.size());
		BlockPos site = new BlockPos(20, 2, 20);
		BlueprintBuilder.Result built = BlueprintBuilder.build(creative, helper.getLevel(), helper.absolutePos(site), plan);
		if (built.status() != BlueprintBuilder.Status.BUILT || built.placed() != plan.parts())
			helper.fail("building the W gave " + built + ", expected " + plan.parts() + " parts");
		// The recorder puts the crank the head belongs to at the origin; find the copy's head the same way round
		BlueprintRecorder.Result copy = null;
		for (Blueprint.Part part : recorded.blueprint()
			.parts())
			if (part.block()
				.endsWith(":cylinder_head")) {
				copy = BlueprintRecorder.record(helper.getLevel(), helper.absolutePos(site.offset(part.x(), part.y(), part.z())), "", "");
				break;
			}
		if (copy == null || !copy.ok())
			helper.fail("the built W could not be recorded: " + (copy == null ? "no head" : copy.failure()));
		Blueprint wanted = BlueprintCodec.decode(plan.text());
		var differences = BlueprintDiff.compare(wanted, copy.blueprint(), 5);
		if (!differences.isEmpty())
			helper.fail("the built W is not the same: " + differences);
		long heads = copy.blueprint()
			.parts()
			.stream()
			.filter(part -> part.block()
				.endsWith(":cylinder_head"))
			.count();
		helper.assertTrue(heads == 6, "The copy has three banks of two: " + heads + " heads");
		helper.succeed();
	}
}
