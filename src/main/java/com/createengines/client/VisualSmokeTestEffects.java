package com.createengines.client;

import static com.createengines.client.VisualSmokeTest.*;

import java.util.List;

import com.createengines.CreateEngines;
import com.createengines.content.head.CylinderHeadBlockEntity;
import com.createengines.gametest.TestEngine;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

/** Sounds, effects and wear in the visual smoke test. Part of {@link VisualSmokeTest}, and like it kept out of the jar. */
final class VisualSmokeTestEffects {

	private VisualSmokeTestEffects() {
	}

	/** How many times each of this mod's sound events was started in this run, for {@link #soundReport}. */
	static final java.util.Map<net.minecraft.resources.ResourceLocation, Integer> SOUNDS_PLAYED =
		new java.util.TreeMap<>();

	static void onPlaySound(PlaySoundEvent event) {
		if (event.getSound() != null && CreateEngines.MOD_ID.equals(event.getSound()
			.getLocation()
			.getNamespace()))
			SOUNDS_PLAYED.merge(event.getSound()
				.getLocation(), 1, Integer::sum);
	}

	/**
	 * The sound smoke test's client half: every sound event this mod registers must resolve through the loaded
	 * {@code sounds.json} to a recording the resource packs actually hold. Problems are logged as errors starting
	 * "SOUND SMOKE FAIL", so a run's log can be searched for them.
	 */
	static void soundCheck() {
		Minecraft minecraft = Minecraft.getInstance();
		int problems = 0;
		int checked = 0;
		for (var id : net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.keySet()) {
			if (!CreateEngines.MOD_ID.equals(id.getNamespace()))
				continue;
			checked++;
			var weighed = minecraft.getSoundManager()
				.getSoundEvent(id);
			if (weighed == null) {
				CreateEngines.LOGGER.error("SOUND SMOKE FAIL {}: no entry in the loaded sounds.json", id);
				problems++;
				continue;
			}
			var sound = weighed.getSound(net.minecraft.util.RandomSource.create());
			if (sound == net.minecraft.client.sounds.SoundManager.EMPTY_SOUND
				|| sound == net.minecraft.client.sounds.SoundManager.INTENTIONALLY_EMPTY_SOUND) {
				CreateEngines.LOGGER.error("SOUND SMOKE FAIL {}: resolves to no sound", id);
				problems++;
				continue;
			}
			var file = sound.getPath();
			if (minecraft.getResourceManager()
				.getResource(file)
				.isEmpty()) {
				CreateEngines.LOGGER.error("SOUND SMOKE FAIL {}: recording {} is not in the resource packs", id, file);
				problems++;
			}
		}
		CreateEngines.LOGGER.info("SOUND SMOKE resolved {} sound events, {} problems", checked, problems);
	}

	/** Which of this mod's sound events actually started during the run, and which never did. */
	static void soundReport() {
		var never = new java.util.TreeSet<String>();
		for (var id : net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.keySet())
			if (CreateEngines.MOD_ID.equals(id.getNamespace()) && !SOUNDS_PLAYED.containsKey(id))
				never.add(id.getPath());
		CreateEngines.LOGGER.info("SOUND SMOKE played {}", SOUNDS_PLAYED);
		CreateEngines.LOGGER.info("SOUND SMOKE never started in this run: {}", never);
	}

	/** Where the rich, the flooded and the dry engine of {@link #effects} stand, relative to the first. */
	static final BlockPos RICH_OFFSET = new BlockPos(12, 0, 0);
	static final BlockPos FLOODED_OFFSET = new BlockPos(24, 0, 0);
	static final BlockPos DRY_OFFSET = new BlockPos(36, 0, 0);
	/** The turbocharged diesel and the supercharged spark engine of {@link #effects}, which exist to be heard. */
	static final BlockPos TURBO_OFFSET = new BlockPos(48, 0, 0);
	static final BlockPos BLOWER_OFFSET = new BlockPos(60, 0, 0);
	static final List<BlockPos> EFFECT_ENGINES =
		List.of(BlockPos.ZERO, RICH_OFFSET, FLOODED_OFFSET, DRY_OFFSET, TURBO_OFFSET, BLOWER_OFFSET);

	/**
	 * What engines throw out: the first engine catching from cold and settling, one beside it with its injector wide
	 * open, one with its air intake underwater and one with no oil. Then the first is choked so it dies, it rains, and
	 * each engine's figures are logged so the pictures can be read against them.
	 */
	/** Where the five wear engines stand: a thrown rod and its broken head, a blown gasket, a seized piston, a sound one to compare and a rotor with failed seals. */
	static final BlockPos WRECK_OFFSET = BlockPos.ZERO;
	static final BlockPos BLOWN_OFFSET = new BlockPos(14, 0, 0);
	static final BlockPos SEIZED_OFFSET = new BlockPos(28, 0, 0);
	static final BlockPos SOUND_OFFSET = new BlockPos(42, 0, 0);
	static final BlockPos APEX_OFFSET = new BlockPos(56, 0, 0);

	/**
	 * The wear mode's wrecks, each photographed from the side that shows it, running (a wreck is drawn whatever the engine is doing), against an
	 * engine with nothing wrong; then the run ends. It shows that a thrown rod's wreck stands out of a broken head and lies in the bore, that a
	 * blown gasket hangs from the joint with its soot streak, and that a seized piston is scorched.
	 */
	static void wear(int start) {
		STEPS.add(new Step(start, () -> server(server -> {
			var level = server.overworld();
			for (BlockPos offset : List.of(WRECK_OFFSET, BLOWN_OFFSET, SEIZED_OFFSET, SOUND_OFFSET))
				TestEngine.build(level, origin.offset(offset), true);
			BlockPos wreck = origin.offset(WRECK_OFFSET);
			level.setBlock(wreck.offset(TestEngine.CYLINDER), level.getBlockState(wreck.offset(TestEngine.CYLINDER))
				.setValue(com.createengines.content.cylinder.CylinderBlock.HAS_PISTON, false)
				.setValue(com.createengines.content.cylinder.CylinderBlock.HAS_ROD, false), 3);
			if (level.getBlockEntity(wreck.offset(TestEngine.CYLINDER)) instanceof com.createengines.content.cylinder.CylinderBlockEntity cylinder)
				cylinder.throwRod();
			if (level.getBlockEntity(wreck.offset(TestEngine.HEAD)) instanceof CylinderHeadBlockEntity head)
				head.breakHead();
			if (level.getBlockEntity(origin.offset(BLOWN_OFFSET)
				.offset(TestEngine.HEAD)) instanceof CylinderHeadBlockEntity head)
				head.blow();
			if (level.getBlockEntity(origin.offset(SEIZED_OFFSET)
				.offset(TestEngine.CYLINDER)) instanceof com.createengines.content.cylinder.CylinderBlockEntity cylinder)
				cylinder.seize();
			TestEngine.buildRotary(level, origin.offset(APEX_OFFSET), true);
			if (level.getBlockEntity(origin.offset(APEX_OFFSET)
				.offset(TestEngine.HOUSING)) instanceof com.createengines.content.rotor.RotorHousingBlockEntity housing)
				housing.failApex();
		})));
		// The head from above and the side, then the cylinder through its glass, of each engine in turn
		int at = start + 60;
		for (BlockPos offset : List.of(WRECK_OFFSET, BLOWN_OFFSET, SEIZED_OFFSET, SOUND_OFFSET, APEX_OFFSET)) {
			String name = offset.equals(WRECK_OFFSET) ? "wreck" : offset.equals(BLOWN_OFFSET) ? "blown" : offset.equals(SEIZED_OFFSET) ? "seized" : offset.equals(APEX_OFFSET) ? "apex" : "sound";
			int x = offset.getX();
			frames("wear_" + name + "_head", at, x + 3.5, 6.2, 0.2, 20, 38);
			frames("wear_" + name + "_north", at + 30, x + 2.5, 4.4, -1.4, 0, 8);
			frames("wear_" + name + "_cylinder", at + 60, x + 2.5, 2.0, -0.5, 0, 2.3f);
			at += 100;
		}
		STEPS.add(new Step(at + 20, () -> {
			logEngines("wear");
			soundReport();
			Minecraft.getInstance()
				.stop();
		}));
	}

	static void effects(int start) {
		STEPS.add(new Step(start - 10, VisualSmokeTestEffects::soundCheck));
		STEPS.add(new Step(start, () -> server(server -> {
			var level = server.overworld();
			BlockPos rich = origin.offset(RICH_OFFSET);
			TestEngine.build(level, rich, true);
			if (level.getBlockEntity(rich.offset(TestEngine.HEAD)) instanceof CylinderHeadBlockEntity head)
				head.metering.setValue(CylinderHeadBlockEntity.MAX_METERING);
			BlockPos flooded = origin.offset(FLOODED_OFFSET);
			TestEngine.build(level, flooded, true);
			BlockPos mouth = flooded.offset(TestEngine.AIR_INTAKE)
				.north();
			for (Direction side : new Direction[] {Direction.NORTH, Direction.EAST, Direction.WEST, Direction.DOWN})
				level.setBlock(mouth.relative(side), Blocks.GLASS.defaultBlockState(), 3);
			level.setBlock(mouth, Blocks.WATER.defaultBlockState(), 3);
			TestEngine.build(level, origin.offset(DRY_OFFSET), true, false);
			BlockPos turbo = origin.offset(TURBO_OFFSET);
			TestEngine.build(level, turbo, true);
			addTurbo(level, turbo);
			BlockPos blown = origin.offset(BLOWER_OFFSET);
			TestEngine.buildSpark(level, blown, true);
			addBlower(level, blown);
		})));
		// The first engine was built a second ago: these catch it cranking, catching and clearing its throat
		for (int i = 0; i < 4; i++)
			frames("effects_start_" + i, start + 10 + i * 40, -1.5, 4.0, -2.0, -18, 5);
		STEPS.add(new Step(start + 200, () -> server(server -> {
			for (BlockPos offset : EFFECT_ENGINES)
				server.overworld()
					.removeBlock(origin.offset(offset)
						.offset(TestEngine.STARTER_POWER), false);
		})));
		frames("effects_running", start + 360, -1.5, 4.0, -2.0, -18, 5);
		frames("effects_intake", start + 390, 5.5, 3.6, -0.5, 73, 13);
		frames("effects_rich", start + 420, RICH_OFFSET.getX() - 1.5, 4.0, -2.0, -18, 5);
		frames("effects_dry", start + 450, DRY_OFFSET.getX() + 2.5, 1.0, -0.4, 0, 0);
		frames("effects_turbo", start + 480, TURBO_OFFSET.getX() - 1.5, 4.0, -2.0, -18, 5);
		frames("effects_blower", start + 500, BLOWER_OFFSET.getX() - 1.5, 4.0, -2.0, -18, 5);
		// Dump the turbo's boost with a redstone block beneath it: the blow-off valve. Then a signal on its camshaft shuts
		// the fuel rack, so the engine is off the throttle and anti-lag has to crack fuel in the exhaust
		STEPS.add(new Step(start + 400, () -> server(server -> server.overworld()
			.setBlock(origin.offset(TURBO_OFFSET)
				.offset(TestEngine.EXHAUST)
				.below(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3))));
		STEPS.add(new Step(start + 430, () -> server(server -> server.overworld()
			.setBlock(origin.offset(TURBO_OFFSET)
				.offset(new BlockPos(4, 5, 3)), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3))));
		// Crank the flooded one again, which nothing can light, and look at its intake and its cylinder
		STEPS.add(new Step(start + 470, () -> server(server -> server.overworld()
			.setBlock(origin.offset(FLOODED_OFFSET)
				.offset(TestEngine.STARTER_POWER), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3))));
		frames("effects_flooded", start + 520, FLOODED_OFFSET.getX() - 0.5, 3.6, -1.5, -56, 10);
		STEPS.add(new Step(start + 550, () -> {
			Minecraft.getInstance().options.hideGui = false;
			server(server -> command(server, "item replace entity @a armor.head with create:goggles"));
		}));
		goggles("effects_flooded_cylinder", start + 560, FLOODED_OFFSET.getX() + 2.5, 2.0, -0.5, 0, 2.3f);
		// Starve the first engine of air so it dies hot, and let it rain on all of them
		STEPS.add(new Step(start + 590, () -> {
			Minecraft.getInstance().options.hideGui = true;
			logEngines("running");
			server(server -> command(server, "weather rain"));
		}));
		// The first engine loses its air a while before the end, so it has time to run down and stall (it coasts a long way)
		STEPS.add(new Step(start + 400, () -> server(server -> server.overworld()
			.removeBlock(origin.offset(TestEngine.AIR_INTAKE), false))));
		frames("effects_rain", start + 760, RICH_OFFSET.getX() + 0.5, 4.2, -0.8, -31, 12);
		STEPS.add(new Step(start + 1150, () -> {
			logEngines("after the stall");
			soundReport();
			Minecraft.getInstance()
				.stop();
		}));
	}
}
