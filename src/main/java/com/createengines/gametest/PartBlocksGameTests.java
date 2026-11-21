package com.createengines.gametest;

import com.createengines.CreateEngines;
import com.createengines.api.EngineBuilding;
import com.createengines.api.PartBlocks;
import com.createengines.content.camshaft.CamshaftBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The public surfaces an addon builds a part from (API 1.1) do what the internal route does. */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class PartBlocksGameTests {

	/** The factories themselves are exercised by the example addon (examples/racing-addon), since a block cannot be made once the registry is frozen. */
	@GameTest(template = "empty")
	public static void noBlockOfThisModNamesAPartEffectThroughTheApi(GameTestHelper helper) {
		helper.assertTrue(PartBlocks.partEffect(com.createengines.registry.CEBlocks.CYLINDER.get())
			.isEmpty(), "none of this mod's blocks name a part effect");
		helper.assertTrue(PartBlocks.partEffect(com.createengines.registry.CEBlocks.CYLINDER_HEAD.get())
			.isEmpty(), "none of this mod's blocks name a part effect");
		helper.succeed();
	}

	/** The timing is what the test engine has always set by hand, so building an engine through the API gives the same engine. */
	@GameTest(template = "empty")
	public static void timingACamLobeThroughTheApiSetsWhatTheTestEngineSets(GameTestHelper helper) {
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		TestEngine.build(helper.getLevel(), origin, false);
		BlockPos lobe = origin.offset(TestEngine.LOBE);
		CamshaftBlockEntity cam = (CamshaftBlockEntity) helper.getLevel()
			.getBlockEntity(lobe);
		int byHand = cam.lobeAngle.getValue();
		cam.lobeAngle.setValue(0);
		helper.assertTrue(EngineBuilding.timeCamLobe(helper.getLevel(), lobe, Direction.UP, Direction.Axis.X), "a lobe is there");
		helper.assertTrue(cam.lobeAngle.getValue() == byHand, "the API set " + cam.lobeAngle.getValue() + ", the test engine sets " + byHand);
		helper.assertFalse(EngineBuilding.timeCamLobe(helper.getLevel(), origin.offset(0, 0, 0), Direction.UP, Direction.Axis.X), "no lobe, no change");
		helper.succeed();
	}
}
