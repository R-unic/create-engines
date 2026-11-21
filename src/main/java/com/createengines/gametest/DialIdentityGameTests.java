package com.createengines.gametest;

import java.util.HashMap;
import java.util.Map;

import com.createengines.CreateEngines;
import com.createengines.registry.CEBlocks;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Create sends a player's setting to the first value box whose network id matches, and copies settings under the clipboard key,
 * so two dials on one block that share either behave as one dial (the throttle, the transmission and the rotor housing each
 * had it). Every block of this mod that carries more than one scroll dial must give each its own.
 */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class DialIdentityGameTests {

	@GameTest(template = "empty")
	public static void everyBlockWithSeveralDialsGivesEachItsOwnNetworkIdAndClipboardKey(GameTestHelper helper) {
		BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
		var level = helper.getLevel();
		int checked = 0;
		StringBuilder problems = new StringBuilder();
		for (Block block : BuiltInRegistries.BLOCK) {
			if (!BuiltInRegistries.BLOCK.getKey(block)
				.getNamespace()
				.equals(CreateEngines.MOD_ID))
				continue;
			level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
			BlockEntity be = level.getBlockEntity(pos);
			if (be instanceof SmartBlockEntity smart) {
				Map<Integer, String> ids = new HashMap<>();
				Map<String, String> keys = new HashMap<>();
				int dials = 0;
				for (BlockEntityBehaviour behaviour : smart.getAllBehaviours())
					if (behaviour instanceof ScrollValueBehaviour dial) {
						dials++;
						String name = BuiltInRegistries.BLOCK.getKey(block) + " " + dial.getType();
						String id = ids.put(dial.netId(), name);
						if (id != null)
							problems.append(name).append(" and ").append(id).append(" share network id ").append(dial.netId()).append("; ");
						String key = keys.put(dial.getClipboardKey(), name);
						if (key != null)
							problems.append(name).append(" and ").append(key).append(" share clipboard key ").append(dial.getClipboardKey()).append("; ");
					}
				if (dials > 1)
					checked++;
			}
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		}
		helper.assertTrue(problems.isEmpty(), "dials that cannot be told apart: " + problems);
		helper.assertTrue(checked >= 4, "the sweep should have found the throttle, transmission, turbocharger and rotor housing at least, found " + checked);
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void aRotorHousingsFuelMeteringAndThrowAngleAreSeparateDials(GameTestHelper helper) {
		BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
		helper.getLevel()
			.setBlock(pos, CEBlocks.ROTOR_HOUSING.getDefaultState(), Block.UPDATE_ALL);
		var rotor = (com.createengines.content.rotor.RotorHousingBlockEntity) helper.getLevel()
			.getBlockEntity(pos);
		helper.assertTrue(rotor.throwAngle != null && rotor.metering != null, "a rotor housing has both dials");
		helper.assertTrue(rotor.throwAngle.netId() != rotor.metering.netId(), "the two dials have the same network id");
		helper.assertTrue(!rotor.throwAngle.getClipboardKey()
			.equals(rotor.metering.getClipboardKey()), "the two dials copy under the same clipboard key");
		int angle = rotor.throwAngle.getValue();
		int metering = rotor.metering.getValue();
		rotor.metering.setValue(metering + 3);
		helper.assertTrue(rotor.throwAngle.getValue() == angle, "setting the metering moved the throw angle");
		rotor.throwAngle.setValue(angle + 2);
		helper.assertTrue(rotor.metering.getValue() == metering + 3, "setting the throw angle moved the metering");
		helper.succeed();
	}
}
