package com.createengines.gametest;

import java.util.Map;

import com.createengines.CreateEngines;
import com.createengines.content.blueprint.FramedBlueprintBlock;
import com.createengines.content.blueprint.BlueprintRecorder;
import com.createengines.content.blueprint.RecordedBlueprint;
import com.createengines.content.camshaft.CamshaftBlockEntity;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.content.head.CylinderHeadBlockEntity;
import com.createengines.engine.blueprint.Blueprint;
import com.createengines.engine.blueprint.BlueprintCodec;
import com.createengines.engine.blueprint.BlueprintException;
import com.createengines.registry.CEBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Builders and readers shared by the blueprint GameTests. */
final class BlueprintRigs {

	private BlueprintRigs() {
	}

	static Blueprint.Part part(int x, int y, int z) {
		return new Blueprint.Part(x, y, z, CreateEngines.MOD_ID + ":cylinder", Map.of(), Map.of());
	}

	// Applying

	static final BlockPos SECOND = new BlockPos(0, 0, 12);

	/** Two copies of the test engine, far enough apart to be separate engines. The first is tuned, the second left alone. */
	static void buildPair(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		TestEngine.build(level, helper.absolutePos(BlockPos.ZERO), true);
		TestEngine.build(level, helper.absolutePos(SECOND), true);
		((CylinderHeadBlockEntity) helper.getBlockEntity(TestEngine.HEAD)).metering.setValue(14);
		((CrankshaftBlockEntity) helper.getBlockEntity(TestEngine.THROW)).throwAngle.setValue(6);
		((CamshaftBlockEntity) helper.getBlockEntity(TestEngine.LOBE)).lobeAngle.setValue(9);
	}

	static RecordedBlueprint recordFirst(GameTestHelper helper) throws BlueprintException {
		BlueprintRecorder.Result result = BlueprintRecorder.record(helper.getLevel(), helper.absolutePos(TestEngine.HEAD), "Pair", "t");
		if (!result.ok())
			helper.fail("recording failed: " + result.failure());
		return new RecordedBlueprint(BlueprintCodec.encode(result.blueprint()), "Pair", result.blueprint()
			.parts()
			.size());
	}

	static BlockPos second(BlockPos inFirst) {
		return inFirst.offset(SECOND);
	}

	static int[] settingsOfSecond(GameTestHelper helper) {
		return new int[] { ((CylinderHeadBlockEntity) helper.getBlockEntity(second(TestEngine.HEAD))).metering.getValue(),
			((CrankshaftBlockEntity) helper.getBlockEntity(second(TestEngine.THROW))).throwAngle.getValue(),
			((CamshaftBlockEntity) helper.getBlockEntity(second(TestEngine.LOBE))).lobeAngle.getValue() };
	}

	static RecordedBlueprint recordedTestEngine(GameTestHelper helper) throws BlueprintException {
		ServerLevel level = helper.getLevel();
		TestEngine.build(level, helper.absolutePos(BlockPos.ZERO), true);
		((CylinderHeadBlockEntity) helper.getBlockEntity(TestEngine.HEAD)).metering.setValue(14);
		((CrankshaftBlockEntity) helper.getBlockEntity(TestEngine.THROW)).throwAngle.setValue(6);
		BlueprintRecorder.Result result = BlueprintRecorder.record(level, helper.absolutePos(TestEngine.HEAD), "Site", "t");
		if (!result.ok())
			helper.fail("recording failed: " + result.failure());
		return new RecordedBlueprint(BlueprintCodec.encode(result.blueprint()), "Site", result.blueprint()
			.parts()
			.size());
	}

	static RecordedBlueprint recordedTestEngineSmall(GameTestHelper helper) throws BlueprintException {
		TestEngine.build(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		BlueprintRecorder.Result result = BlueprintRecorder.record(helper.getLevel(), helper.absolutePos(TestEngine.HEAD), "Shared", "t");
		return new RecordedBlueprint(BlueprintCodec.encode(result.blueprint()), "Shared", result.blueprint()
			.parts()
			.size());
	}

	static BlockPos wallFrame(GameTestHelper helper) {
		BlockPos wall = new BlockPos(8, 2, 4);
		helper.setBlock(wall, Blocks.STONE);
		BlockPos at = wall.north();
		helper.setBlock(at, CEBlocks.FRAMED_BLUEPRINT.getDefaultState()
			.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, Direction.NORTH));
		return at;
	}

	static net.minecraft.world.phys.BlockHitResult hitOf(GameTestHelper helper, BlockPos at) {
		return new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(at)), Direction.NORTH,
			helper.absolutePos(at), false);
	}

	/** A wrench used on the frame at {@code at}, as a player right-clicking it with one. */
	static net.minecraft.world.InteractionResult wrench(GameTestHelper helper, BlockPos at) {
		Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
		ItemStack wrench = com.simibubi.create.AllItems.WRENCH.asStack();
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, wrench);
		var context = new net.minecraft.world.item.context.UseOnContext(helper.getLevel(), player,
			net.minecraft.world.InteractionHand.MAIN_HAND, wrench, hitOf(helper, at));
		BlockState state = helper.getBlockState(at);
		return ((FramedBlueprintBlock) state.getBlock()).onWrenched(state, context);
	}
}
