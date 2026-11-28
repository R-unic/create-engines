package com.createengines.gametest;

import java.util.List;

import com.createengines.CreateEngines;
import com.createengines.content.turbo.TurbochargerBlockEntity;
import com.createengines.engine.CylinderStatus;
import com.createengines.engine.TurboStatus;
import com.createengines.engine.sim.CycleResult;
import com.createengines.registry.CEBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** What the client is sent so an engine's note can carry its blowers and be heard from its exhaust. */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class SoundSyncGameTests {

	@GameTest(template = "empty")
	public static void aTurboTellsClientsWhichEngineDrivesItAndSavesNothingOfIt(GameTestHelper helper) {
		BlockPos at = new BlockPos(3, 2, 3);
		helper.setBlock(at, CEBlocks.TURBOCHARGER.getDefaultState());
		TurbochargerBlockEntity turbo = helper.getBlockEntity(at);
		var registries = helper.getLevel()
			.registryAccess();
		helper.assertTrue(!turbo.getUpdateTag(registries)
			.contains("Engine"), "A turbo on no engine names one");
		TurboStatus status = new TurboStatus(0.5f, 0.3f, 0, 1.2f, true, true);
		turbo.updateStatus(helper.absolutePos(new BlockPos(1, 2, 1)), status);
		helper.assertTrue(turbo.getUpdateTag(registries)
			.contains("Engine"), "The client is not told the engine");
		helper.assertTrue(!turbo.saveWithoutMetadata(registries)
			.contains("Engine"), "The engine is saved with the world");
		turbo.detachFromEngine();
		helper.assertTrue(!turbo.getUpdateTag(registries)
			.contains("Engine"), "A detached turbo still names its engine");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void aTurboTellsClientsWhereItsExhaustComesOut(GameTestHelper helper) {
		List<BlockPos> outlets = List.of(new BlockPos(1, 2, 3), new BlockPos(-4, 70, 5));
		TurboStatus piped = new TurboStatus(0.5f, 0.3f, 0, 1.2f, 0.3f, true, true, 0, true, 0, true, outlets);
		TurboStatus bare = new TurboStatus(0.5f, 0.3f, 0, 1.2f, 0.3f, true, true, 0, true, 0, true);
		helper.assertTrue(TurboStatus.read(piped.write())
			.outlets()
			.equals(outlets), "The openings did not come through");
		helper.assertTrue(!bare.write()
			.contains("Outlets"), "A turbo with no openings writes a new key");
		helper.assertTrue(TurboStatus.read(bare.write())
			.outlets()
			.isEmpty(), "Openings from nowhere");
		helper.assertTrue(piped.differsFrom(bare), "A new opening is not a change to send");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void onlyAMuffledExhaustSaysSoInACylindersStatus(GameTestHelper helper) {
		CylinderStatus open = status(0);
		CylinderStatus muffled = status(0.5f);
		helper.assertTrue(!open.write()
			.contains("ExhaustMuffle"), "An open exhaust writes a new key");
		CompoundTag sent = muffled.write();
		helper.assertTrue(CylinderStatus.read(sent)
			.exhaustMuffle() == 0.5f, "The muffling did not come through");
		helper.assertTrue(muffled.differsFrom(open), "A muffler fitted is not a change to send");
		sent.putFloat("ExhaustMuffle", 40);
		helper.assertTrue(CylinderStatus.read(sent)
			.exhaustMuffle() == 1, "A wild figure is not held in range");
		helper.succeed();
	}

	private static CylinderStatus status(float muffle) {
		return new CylinderStatus(BlockPos.ZERO, 0, CycleResult.FIRED, false, 0, 0, 600, 500, null, List.of(), false, 1, true,
			0, 0, List.of(), 0, false, muffle);
	}
}
