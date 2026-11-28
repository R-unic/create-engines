package com.createengines.gametest;

import com.createengines.CreateEngines;
import com.createengines.content.blueprint.EngineDials;
import com.createengines.content.governor.GovernorBlockEntity;
import com.createengines.registry.CEBlocks;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour.ValueSettings;
import com.simibubi.create.infrastructure.config.AllConfigs;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** What the Governed Speed value panel stores: an exact speed, with steps of five above the exact row. */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class GovernorSettingGameTests {

	private static final BlockPos AT = new BlockPos(3, 2, 3);

	private static GovernorBlockEntity governor(GameTestHelper helper) {
		helper.setBlock(AT, CEBlocks.GOVERNOR.getDefaultState());
		return helper.getBlockEntity(AT);
	}

	@GameTest(template = "empty")
	public static void theGovernedSpeedCanBeAnyExactNumber(GameTestHelper helper) {
		GovernorBlockEntity governor = governor(helper);
		Player player = helper.makeMockPlayer(GameType.CREATIVE);
		governor.target.setValueSettings(player, new ValueSettings(0, 137), false);
		helper.assertTrue(governor.target.getValue() == 137, "An exact setting should be stored as it is: " + governor.target.getValue());
		helper.assertTrue(governor.target() == 137, "The governor should hold exactly 137 RPM: " + governor.target());
		governor.target.setValueSettings(player, new ValueSettings(0, 3), false);
		helper.assertTrue(governor.target() == GovernorBlockEntity.MIN_TARGET,
			"A setting below the lowest should be held at it: " + governor.target());
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void theCoarseRowGoesInFivesAndStopsAtTheTop(GameTestHelper helper) {
		GovernorBlockEntity governor = governor(helper);
		Player player = helper.makeMockPlayer(GameType.CREATIVE);
		governor.target.setValueSettings(player, new ValueSettings(1, 100), false);
		helper.assertTrue(governor.target.getValue() == 500, "Row two is in steps of 5: " + governor.target.getValue());
		governor.target.setValueSettings(player, new ValueSettings(1, 240), false);
		helper.assertTrue(governor.target.getValue() == GovernorBlockEntity.MAX_TARGET,
			"A setting past the top should be held at it: " + governor.target.getValue());
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void theValuePanelShowsTheStoredSpeedInTheRightRow(GameTestHelper helper) {
		GovernorBlockEntity governor = governor(helper);
		governor.target.setValue(137);
		helper.assertTrue(governor.target.getValueSettings()
			.equals(new ValueSettings(0, 137)), "An exact speed shows in the first row: " + governor.target.getValueSettings());
		governor.target.setValue(256);
		helper.assertTrue(governor.target.getValueSettings()
			.equals(new ValueSettings(0, 256)), "The limit of the exact row is still exact: " + governor.target.getValueSettings());
		governor.target.setValue(600);
		helper.assertTrue(governor.target.getValueSettings()
			.equals(new ValueSettings(1, 120)), "Above it the second row shows fives: " + governor.target.getValueSettings());
		helper.succeed();
	}

	/** Runs the check with Create's RPM limit at {@code limit}, all within this tick, and puts the limit back. */
	private static void withCreateLimit(int limit, Runnable check) {
		var setting = AllConfigs.server().kinetics.maxRotationSpeed;
		int original = setting.get();
		setting.set(limit);
		try {
			check.run();
		} finally {
			setting.set(original);
		}
	}

	@GameTest(template = "empty")
	public static void aRaisedCreateLimitRaisesTheTopOfTheDial(GameTestHelper helper) {
		GovernorBlockEntity governor = governor(helper);
		Player player = helper.makeMockPlayer(GameType.CREATIVE);
		withCreateLimit(1024, () -> {
			governor.target.setValueSettings(player, new ValueSettings(1, 205), false);
			helper.assertTrue(governor.target() == 1024, "The top of the panel should be Create's limit: " + governor.target());
			governor.target.setValue(1010);
			helper.assertTrue(governor.target() == 1010, "A speed past 1000 should be kept: " + governor.target());
			int listed = EngineDials.find("create_engines:governor", "target")
				.max();
			helper.assertTrue(listed == 1024, "A blueprint should be allowed the same top: " + listed);
		});
		withCreateLimit(256, () -> {
			helper.assertTrue(governor.target() == 1010, "Lowering the limit should not rewrite a set speed: " + governor.target());
			governor.target.setValue(5000);
			helper.assertTrue(governor.target() == GovernorBlockEntity.MAX_TARGET, "The dial is back to 1000: " + governor.target());
		});
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void aVeryHighLimitIsReachedInWiderSteps(GameTestHelper helper) {
		GovernorBlockEntity governor = governor(helper);
		Player player = helper.makeMockPlayer(GameType.CREATIVE);
		withCreateLimit(8192, () -> {
			governor.target.setValueSettings(player, new ValueSettings(1, 256), false);
			helper.assertTrue(governor.target() == 8192, "The last step should reach the limit: " + governor.target());
			helper.assertTrue(governor.target.getValueSettings()
				.equals(new ValueSettings(1, 256)), "And show where it was picked: " + governor.target.getValueSettings());
		});
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void aSaveFromBeforeTheSettingWasExactIsConverted(GameTestHelper helper) {
		GovernorBlockEntity governor = governor(helper);
		var registries = helper.getLevel()
			.registryAccess();
		CompoundTag saved = governor.saveWithFullMetadata(registries);
		saved.remove("ExactRpm");
		saved.putInt("ScrollValue", 16);
		governor.loadWithComponents(saved, registries);
		helper.assertTrue(governor.target.getValue() == 80, "An old setting of 16 steps is 80 RPM: " + governor.target.getValue());

		CompoundTag current = governor.saveWithFullMetadata(registries);
		helper.assertTrue(current.contains("ExactRpm"), "New saves should say the setting is in RPM");
		governor.target.setValue(137);
		governor.loadWithComponents(governor.saveWithFullMetadata(registries), registries);
		helper.assertTrue(governor.target.getValue() == 137, "A new save should load unchanged: " + governor.target.getValue());
		helper.succeed();
	}
}
