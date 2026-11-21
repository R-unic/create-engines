package com.createengines.gametest;

import java.util.ArrayList;
import java.util.List;

import com.createengines.CreateEngines;
import com.createengines.registry.CEBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * An alternator turned by a shaft charging the energy blocks of other mods: Create Crafts & Additions, Create: New Age,
 * Flux Networks and anything else that offers Forge Energy all take it the same way, through the capability, so one test covers
 * them by being given a block of each. The blocks come from the {@code create_engines.energySinks} system property (comma separated
 * block ids), which {@code runGameTestServerEnergy} sets and loads the mods for.
 * <p>
 * With no property (the normal GameTest run) the test has nothing to try and says so in the log. With the property, a block the game
 * does not have, a block that offers no energy, or one that is not charged is a failure: a row that names a mod is never green
 * because the mod was missing.
 */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class EnergyCompatGameTests {

	private static final String PROPERTY = "create_engines.energySinks";
	/** One row of the 9x7x6 template per sink: a motor, the alternator it turns, then the sink. */
	private static final int MOST_SINKS = 4;

	static List<ResourceLocation> sinks() {
		List<ResourceLocation> ids = new ArrayList<>();
		for (String name : System.getProperty(PROPERTY, "")
			.split(",")) {
			if (!name.isBlank())
				ids.add(ResourceLocation.parse(name.trim()));
		}
		return ids;
	}

	@GameTest(template = "empty", timeoutTicks = 400)
	public static void anAlternatorChargesOtherModsEnergyBlocks(GameTestHelper helper) {
		List<ResourceLocation> ids = sinks();
		if (ids.isEmpty()) {
			org.slf4j.LoggerFactory.getLogger(EnergyCompatGameTests.class)
				.info("No energy blocks named in {}: nothing to charge in this run", PROPERTY);
			helper.succeed();
			return;
		}
		helper.assertTrue(ids.size() <= MOST_SINKS, "The template has room for " + MOST_SINKS + " sinks, " + PROPERTY + " names " + ids.size());
		List<BlockPos> sinkPositions = new ArrayList<>();
		for (int row = 0; row < ids.size(); row++) {
			int z = 1 + row;
			Block sink = BuiltInRegistries.BLOCK.getOptional(ids.get(row))
				.orElse(null);
			helper.assertTrue(sink != null, "The run names " + ids.get(row) + " but the game has no such block: is the mod loaded?");
			BlockPos motor = new BlockPos(1, 1, z);
			helper.setBlock(motor, com.simibubi.create.AllBlocks.CREATIVE_MOTOR.getDefaultState()
				.setValue(CreativeMotorBlock.FACING, Direction.EAST));
			CreativeMotorBlockEntity drive = helper.getBlockEntity(motor);
			drive.generatedSpeed.setValue(64);
			helper.setBlock(new BlockPos(2, 1, z), CEBlocks.ALTERNATOR.getDefaultState()
				.setValue(BlockStateProperties.AXIS, Direction.Axis.X));
			BlockPos sinkPos = new BlockPos(3, 1, z);
			helper.setBlock(sinkPos, sink.defaultBlockState());
			sinkPositions.add(sinkPos);
		}
		helper.runAtTickTime(300, () -> {
			List<String> problems = new ArrayList<>();
			for (int row = 0; row < ids.size(); row++) {
				ResourceLocation id = ids.get(row);
				BlockPos sinkPos = helper.absolutePos(sinkPositions.get(row));
				IEnergyStorage energy = helper.getLevel()
					.getCapability(Capabilities.EnergyStorage.BLOCK, sinkPos, Direction.WEST);
				StringBuilder faces = new StringBuilder();
				for (Direction face : Direction.values()) {
					IEnergyStorage there = helper.getLevel()
						.getCapability(Capabilities.EnergyStorage.BLOCK, sinkPos, face);
					faces.append(face)
						.append(there == null ? "=none " : there.canReceive() ? "=takes " : "=gives-only ");
				}
				if (energy == null)
					problems.add(id + " offers no Forge Energy to the side the alternator is on (by face: " + faces + "; block entity "
						+ helper.getLevel()
							.getBlockEntity(sinkPos) + ")");
				else if (!energy.canReceive())
					problems.add(id + " does not take energy from that side");
				else if (energy.getEnergyStored() <= 0)
					problems.add(id + " has not been charged by the alternator beside it (" + energy.getEnergyStored() + ")");
			}
			// Every sink is judged before the test fails, so one mod's problem does not hide another's
			helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
			helper.succeed();
		});
	}
}
