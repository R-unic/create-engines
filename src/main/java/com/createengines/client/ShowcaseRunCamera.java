package com.createengines.client;

import static com.createengines.client.ShowcaseRun.*;
import static com.createengines.client.ShowcaseRunWorks.*;
import static com.createengines.client.ShowcaseRunRover.*;
import static com.createengines.client.ShowcaseRunBuild.*;
import static com.createengines.client.ShowcaseRunHooks.*;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.createengines.client.ShowcaseRunShots.DAY;
import static com.createengines.client.ShowcaseRunShots.NO_TIME;

import com.createengines.CreateEngines;
import com.createengines.client.ShowcaseRunShots.Shot;
import com.createengines.content.tool.BankBuilderItem;
import com.createengines.content.tool.TimingToolItem;
import com.createengines.engine.topology.FiringPlanner;
import com.createengines.registry.CEBlocks;
import com.createengines.registry.CEItems;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** The trailer's camera: poses, cues, stages and the shot list as filmed. Part of {@link ShowcaseRun}, and like it kept out of the jar. */
final class ShowcaseRunCamera {

	private ShowcaseRunCamera() {
	}

	// -----------------------------------------------------------------------------------------------------------------
	// The trailer: a camera that moves while the engines run, for a screen recording
	// -----------------------------------------------------------------------------------------------------------------

	/** Seconds the camera waits at the first shot's start, so the chunks around it are in before the recording starts. */
	static final double LEAD_IN = 8;
	static final List<Shot> SHOTS = new ArrayList<>();
	static long trailerStart = -1;
	static long trailerStartMillis;
	static int trailerShot = -1;
	static boolean trailerStill;
	static boolean trailerOver;
	static volatile double[] trailerPose;
	static boolean syncPending;

	/**
	 * The sync mark: the whole screen white for a quarter of a second and an anvil's clap through the speakers, both started from
	 * the first frame this screen draws, whose wall-clock moment is written to {@code trailer_sync.json} for tools/trailer/record.py.
	 */
	static final class SyncFlash extends Screen {
		private long shownNanos;

		SyncFlash() {
			super(Component.empty());
		}

		@Override
		public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
			graphics.fill(0, 0, width, height, 0xFFFFFFFF);
			if (shownNanos != 0) {
				if (System.nanoTime() - shownNanos > 250_000_000L)
					Minecraft.getInstance()
						.setScreen(null);
				return;
			}
			shownNanos = System.nanoTime();
			long epoch = System.currentTimeMillis();
			Minecraft minecraft = Minecraft.getInstance();
			minecraft.getSoundManager()
				.play(SimpleSoundInstance.forUI(SoundEvents.ANVIL_LAND, 1.0F, 1.0F));
			try {
				Files.writeString(minecraft.gameDirectory.toPath()
					.resolve("trailer_sync.json"), String.format(Locale.ROOT, "{\"flashEpochMs\": %d}%n", epoch));
			} catch (IOException e) {
				CreateEngines.LOGGER.warn("Showcase: could not write the sync mark", e);
			}
			CreateEngines.LOGGER.info("Showcase trailer sync mark at {}", epoch);
		}

		@Override
		public boolean isPauseScreen() {
			return false;
		}

		@Override
		public boolean shouldCloseOnEsc() {
			return false;
		}
	}

	static void defineTrailer() {
		ShowcaseRunShots.define(SHOTS, aero());
		defineCues();
		filterShots();
	}

	/**
	 * {@code -PtrailerShots=a,b} (a comma-separated list of shot names) keeps only those shots, so a new pad can be looked at in
	 * a short run instead of the whole tour. The shots stay in the tour's order.
	 */
	static void filterShots() {
		String only = System.getProperty(PROPERTY + ".trailerShots", "");
		if (only.isBlank())
			return;
		List<String> keep = List.of(only.split(","));
		SHOTS.removeIf(shot -> !keep.contains(shot.name()));
		if (SHOTS.isEmpty())
			throw new IllegalStateException("no trailer shot is called any of " + keep);
	}

	static void scheduleTrailer() {
		defineTrailer();
		// Spectator mode, so nothing is in the camera's way and it does not fall; the tour starts at the first shot
		STEPS.add(new Step(1000, () -> server(server -> {
			command(server, "gamemode spectator @a");
			command(server, "time set " + DAY);
			teleport(server, pose(SHOTS.get(0), 0));
		})));
		// tools/trailer/record.py starts its screen recording on this line, which is LEAD_IN seconds before the first shot
		STEPS.add(new Step(1001, () -> CreateEngines.LOGGER.info("Showcase trailer ready")));
		// A white flash and an anvil's clap at one moment, in the lead-in: record.py finds both in what it recorded and so measures
		// how far the sound trails the picture, instead of assuming it does not
		STEPS.add(new Step(1001 + 80, () -> syncPending = true));
		STEPS.add(new Step(1000 + (int) (LEAD_IN * 20), () -> {
			trailerStart = System.nanoTime();
			trailerStartMillis = System.currentTimeMillis();
			writeShotList();
		}));
	}

	/** Where the camera is, as {x, eyeY, z, yaw, pitch}, {@code t} of the way (0 to 1) through the shot. */
	static double[] pose(Shot shot, double t) {
		Scene scene = SCENES.stream()
			.filter(s -> s.name().equals(shot.scene()))
			.findFirst()
			.orElseThrow();
		BlockPos pad = pad(scene.bay());
		// Half linear and half eased: the shot starts and ends gently without crawling in the middle
		double e = 0.5 * t + 0.5 * t * t * (3 - 2 * t);
		double yaw = shot.yaw0() + (shot.yaw1() - shot.yaw0()) * e;
		double pitch = shot.pitch0() + (shot.pitch1() - shot.pitch0()) * e;
		double fill = shot.fill0() + (shot.fill1() - shot.fill0()) * e;
		ShowcaseRunFraming.Box subject = shot.subject();
		ShowcaseRunShots.Move move = ShowcaseRunShots.MOVES.get(shot.name());
		if (move != null)
			subject = ShowcaseRunFraming.between(subject, move.to(), move.along(t));
		Double reach = ShowcaseRunShots.REACH.get(shot.name());
		ShowcaseRunFraming.Placing placing = reach == null ? ShowcaseRunFraming.place(subject, yaw, pitch, fill)
			: ShowcaseRunFraming.atDistance(subject, yaw, pitch, reach);
		double tx = pad.getX() + placing.aimX() + shot.travelX() * (e - 0.5);
		double ty = pad.getY() + placing.aimY();
		double tz = pad.getZ() + placing.aimZ();
		double x = pad.getX() + placing.eyeX() + shot.travelX() * (e - 0.5);
		double y = Math.max(pad.getY() + placing.eyeY(), pad.getY() + 1.8);
		double z = pad.getZ() + placing.eyeZ();
		if (scene.bay() == AERO_BAY) {
			// Open ground, no walls to stay inside: the camera follows the craft by however far it has moved since it left its pad
			double[] moved = followedMove();
			tx += moved[0];
			ty += moved[1];
			tz += moved[2];
			x += moved[0];
			y = Math.max(y + moved[1], pad.getY() + 1.8);
			z += moved[2];
		} else if (scene.bay() != ShowcaseRunSounds.V8_BAY) {
			// Stay inside the courtyard: a wall is a block thick and the camera must not come out of it or look through it
			double inX = Math.max(pad.getX() + PAD_X0 + 1.6, Math.min(pad.getX() + PAD_X1 - 0.6, x));
			double inZ = Math.max(pad.getZ() + PAD_Z0 + 1.6, Math.min(pad.getZ() + PAD_Z1 - 0.6, z));
			double inY = Math.min(y, pad.getY() + WALL - 1.5);
			// A camera held in by a wall stands nearer than the framing asked, so say so: the shot's angle wants changing
			if (Math.abs(inX - x) + Math.abs(inY - y) + Math.abs(inZ - z) > 0.75 && CLAMPED.add(shot.name()))
				CreateEngines.LOGGER.warn("Showcase trailer shot {}: held inside the walls, {} blocks from its framing", shot.name(),
					String.format(Locale.ROOT, "%.1f", Math.abs(inX - x) + Math.abs(inY - y) + Math.abs(inZ - z)));
			x = inX;
			y = inY;
			z = inZ;
		}
		// Look at the aim from where the camera ended up, so the clamps do not turn it away
		double lookPitch = Math.toDegrees(Math.atan2(y - ty, Math.hypot(x - tx, z - tz)));
		double lookYaw = Math.hypot(x - tx, z - tz) < 0.01 ? yaw : Math.toDegrees(Math.atan2(x - tx, tz - z));
		return new double[] { x, y, z, lookYaw, lookPitch };
	}

	/** The shots already reported as held in by a wall, so each is said once. */
	static final java.util.Set<String> CLAMPED = new java.util.HashSet<>();

	static double[] seenAt;
	static long seenNanos;
	static double[] seenBefore;
	static long seenBeforeNanos;
	static final double[] FOLLOW = new double[3];
	static long followNanos;

	/**
	 * How far the craft has moved since it left its pad, for the camera. The craft's place changes once a server tick (twenty times a
	 * second), which at a frame rate of sixty shows as a camera that steps, so the move is carried on from the last two places at the
	 * speed between them and then eased towards, a little behind the craft: smooth, and a fraction of a block late.
	 */
	static double[] followedMove() {
		double[] now = aeroAt;
		double[] from = aeroStart;
		long t = System.nanoTime();
		if (now == null || from == null)
			return new double[3];
		if (now != seenAt) {
			seenBefore = seenAt;
			seenBeforeNanos = seenNanos;
			seenAt = now;
			seenNanos = t;
		}
		double[] target = new double[3];
		for (int i = 0; i < 3; i++) {
			target[i] = now[i] - from[i];
			if (seenBefore != null && seenNanos > seenBeforeNanos) {
				double speed = (seenAt[i] - seenBefore[i]) / ((seenNanos - seenBeforeNanos) / 1e9);
				// Carried on at that speed for no longer than a tick or two, so a stopped craft is not overshot
				target[i] += speed * Math.min((t - seenNanos) / 1e9, 0.1);
			}
		}
		double dt = followNanos == 0 ? 1 : Math.min((t - followNanos) / 1e9, 0.5);
		followNanos = t;
		// Ease towards the target: about a tenth of a second behind a craft at a steady speed, with the steps taken out
		double ease = followNanos == 0 || dt >= 0.5 ? 1 : 1 - Math.exp(-dt / 0.12);
		for (int i = 0; i < 3; i++)
			FOLLOW[i] += (target[i] - FOLLOW[i]) * ease;
		return FOLLOW.clone();
	}

	static void teleport(MinecraftServer server, double[] pose) {
		command(server, String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", pose[0], pose[1] - 1.62, pose[2],
			pose[3], pose[4]));
	}

	static void writeShotList() {
		StringBuilder text = new StringBuilder("Trailer shots, seconds from the first frame of the recording\n");
		double at = 0;
		int index = 0;
		for (Shot shot : SHOTS) {
			text.append(String.format(Locale.ROOT, "%5.1f - %5.1f  %02d %s%s%n", at, at + shot.seconds(), ++index,
				shot.name(), shot.time() != NO_TIME && shot.time() != DAY ? "  (time of day " + shot.time() + ")" : ""));
			at += shot.seconds();
		}
		text.append(String.format(Locale.ROOT, "total %.1f seconds%n", at));
		// The same list for tools/trailer/post.py, with the wall-clock moment the first shot began
		StringBuilder json = new StringBuilder(String.format(Locale.ROOT, "{\"startEpochMs\": %d, \"shots\": [%n", trailerStartMillis));
		double from = 0;
		for (int i = 0; i < SHOTS.size(); i++) {
			Shot shot = SHOTS.get(i);
			json.append(String.format(Locale.ROOT, "  {\"name\": \"%s\", \"start\": %.2f, \"end\": %.2f}%s%n", shot.name(), from,
				from + shot.seconds(), i + 1 < SHOTS.size() ? "," : ""));
			from += shot.seconds();
		}
		json.append(String.format(Locale.ROOT, "], \"total\": %.2f}%n", from));
		Minecraft minecraft = Minecraft.getInstance();
		try {
			Files.writeString(minecraft.gameDirectory.toPath()
				.resolve("trailer_shots.txt"), text.toString());
			Files.writeString(minecraft.gameDirectory.toPath()
				.resolve("trailer_shots.json"), json.toString());
		} catch (IOException e) {
			CreateEngines.LOGGER.warn("Showcase: could not write the trailer shot list", e);
		}
		CreateEngines.LOGGER.info("Showcase trailer starts:\n{}", text);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// Gameplay clips: first person with the Bank Builder in hand, and things happening in front of the camera
	// -----------------------------------------------------------------------------------------------------------------

	/** Something that happens {@code at} seconds into a shot: a block laid, a swing of the arm. */
	record Cue(String shot, double at, Runnable action) {
	}

	static final List<Cue> CUES = new ArrayList<>();
	static final java.util.Set<Cue> FIRED = new java.util.HashSet<>();
	/** Shots seen through the player's own eyes, with the shot's item (the Bank Builder, a filter, a dye) in hand, not a spectator's camera. */
	static final java.util.Map<String, java.util.function.Supplier<ItemStack>> FIRST_PERSON = java.util.Map.of(
		"grow_engine", () -> new ItemStack(CEItems.BANK_BUILDER.get()),
		"bank_wx", () -> new ItemStack(CEItems.BANK_BUILDER.get()),
		"heat_critical", () -> ItemStack.EMPTY,
		"headphones_on", () -> new ItemStack(CEItems.HEADPHONES.get()),
		"casing_dye", () -> new ItemStack(net.minecraft.world.item.Items.RED_DYE),
		"service_sump", () -> new ItemStack(CEItems.OIL_FILTER.get()));

	/**
	 * The player uses {@code held} on a block, for real: the server runs the item's and the block's own code (a dye on a
	 * casing, a filter in the pan) as if a click had landed, and the arm swings.
	 */
	static void useOn(BlockPos pos, Direction face, ItemStack held) {
		swing();
		withPlayer((level, player) -> {
			player.setItemInHand(InteractionHand.MAIN_HAND, held);
			player.gameMode.useItemOn(player, level, held, InteractionHand.MAIN_HAND,
				new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos), face, pos, false));
		});
	}

	static void withPlayer(java.util.function.BiConsumer<ServerLevel, ServerPlayer> action) {
		server(server -> {
			var players = server.getPlayerList()
				.getPlayers();
			if (!players.isEmpty())
				action.accept(server.overworld(), players.get(0));
		});
	}

	static void swing() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null)
			player.swing(InteractionHand.MAIN_HAND);
	}

	/** The stages of the growing engine: layout, throws, whether it is precision, and what it wears. */
	record Stage(BankBuilderItem.Layout layout, int throwsCount, boolean precision, Kit kit) {
	}

	static void defineCues() {
		Kit plain = Kit.plain(Fuel.GASOLINE);
		Kit boosted = new Kit(Fuel.GASOLINE, 2, false, 0, false, true, false);
		BankBuilderItem.Layout inline = BankBuilderItem.Layout.INLINE;
		BankBuilderItem.Layout vee = BankBuilderItem.Layout.VEE;
		Stage[] stages = { new Stage(inline, 1, false, plain), new Stage(inline, 4, false, plain),
			new Stage(vee, 4, false, plain), new Stage(vee, 8, false, plain), new Stage(vee, 8, true, plain),
			new Stage(vee, 8, true, boosted) };
		// One cylinder, an inline four, a V8, a V16, the V16 in precision parts, then boosted: each is the Bank Builder's work
		// on a fresh crank line, two seconds apart, with the starter turning each over. The last gets its timing set and is
		// let go to run on its own.
		for (int i = 0; i < stages.length; i++)
			layStage("grow_engine", 0.2 + 2.0 * i, stages[i], i == stages.length - 1);
		// A W of twelve cylinders, then an X of sixteen, on the same bare pad before the growing engine takes it
		layStage("bank_wx", 0.2, new Stage(BankBuilderItem.Layout.W, 4, true, plain), false);
		layStage("bank_wx", 3.0, new Stage(BankBuilderItem.Layout.X, 4, true, plain), true);
		CUES.add(new Cue("bank_wx", 5.5, () -> withPlayer((level, player) ->
			level.removeBlock(pad(10).offset(0, CY - 1, CZ), false))));
		CUES.add(new Cue("grow_engine", 0.2 + 2.0 * (stages.length - 1) + 2.5, () -> withPlayer((level, player) ->
			level.removeBlock(pad(10).offset(0, CY - 1, CZ), false))));
		CUES.add(new Cue("grow_engine", 14.5, () -> shot("trailer_grow_end")));
		// More stills, of what a shot's middle does not show, to check it without watching
		for (var still : java.util.List.of(java.util.Map.entry("heat_critical", 5.0), java.util.Map.entry("exhaust_mouth", 0.1),
			java.util.Map.entry("far_boom", 8.8), java.util.Map.entry("rods_light_heavy", 5.0),
			java.util.Map.entry("gasket_saw", 5.0), java.util.Map.entry("radiator_fan_basin", 5.8),
			java.util.Map.entry("bank_wx", 2.5), java.util.Map.entry("bank_wx", 5.8)))
			CUES.add(new Cue(still.getKey(), still.getValue(),
				() -> shot("check_" + still.getKey() + "_" + Math.round(still.getValue() * 10))));
		// The heavy starter turns the cold V16 over (a redstone block under its input, as the other pads' starters get while they
		// are built), the engine catches and runs on, and the block is taken away again
		// (positions are worked out when a cue runs: the pads' origin is not known until the world is up)
		CUES.add(new Cue("heavy_starter", 0.5, () -> server(server -> server.overworld()
			.setBlock(pad(BIGBLOCK_BAY).offset(1, CY - 1, CZ - 1), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL))));
		CUES.add(new Cue("heavy_starter", 4.5, () -> server(server -> server.overworld()
			.removeBlock(pad(BIGBLOCK_BAY).offset(1, CY - 1, CZ - 1), false))));
		// The player puts the headphones on: the item's own use, which wears them, and the engine's note is muffled from then on
		CUES.add(new Cue("headphones_on", 2.0, () -> {
			swing();
			withPlayer((level, player) -> player.gameMode.useItem(player, level, player.getItemInHand(InteractionHand.MAIN_HAND),
				InteractionHand.MAIN_HAND));
		}));
		// Dye on the casing pillars, one after another, from the first-person view
		net.minecraft.world.item.Item[] dyes = { net.minecraft.world.item.Items.RED_DYE, net.minecraft.world.item.Items.ORANGE_DYE,
			net.minecraft.world.item.Items.LIME_DYE, net.minecraft.world.item.Items.CYAN_DYE, net.minecraft.world.item.Items.BLUE_DYE,
			net.minecraft.world.item.Items.PURPLE_DYE, net.minecraft.world.item.Items.PINK_DYE,
			net.minecraft.world.item.Items.YELLOW_DYE };
		int[] pillars = { 2, 5, 6, 9, 10, 13, 14, 17 };
		for (int i = 0; i < dyes.length; i++) {
			net.minecraft.world.item.Item dye = dyes[i];
			int x = pillars[i];
			CUES.add(new Cue("casing_dye", 0.5 + 0.65 * i, () -> useOn(casingPillar(x, 2), Direction.NORTH, new ItemStack(dye))));
		}
		// An oil filter into the pan (the item's and the pan's own code)
		CUES.add(new Cue("service_sump", 0.8, () -> useOn(pad(SERVICE_BAY).offset(1, CY, CZ - 1), Direction.NORTH,
			new ItemStack(CEItems.OIL_FILTER.get()))));
		java.util.function.BiConsumer<String, ShowcaseRunStaging.Cued> cue = (shot, cued) -> CUES.add(new Cue(shot, cued.at(),
			() -> server(server -> cued.action()
				.accept(server.overworld()))));
		ShowcaseRunFeatures.defineCues(cue);
		ShowcaseRunSounds.defineCues(cue);
		ShowcaseRunWear.defineCues(cue);
		// The wheeled engine is lifted into its structure, off the ground, as its first shot begins
		CUES.add(new Cue("rover_departs", 0.0, ShowcaseRunRover::liftOff));
	}

	/** One step of a clip on the bare pad: the Bank Builder's work on a fresh crank line, and the last one timed. */
	static void layStage(String shot, double at, Stage stage, boolean last) {
		CUES.add(new Cue(shot, at, () -> {
			if (stage.throwsCount() != 1)
				swing();
			withPlayer((level, player) -> {
				growTo(level, player, stage);
				if (last)
					TimingToolItem.apply(level, pad(10).offset(1, CY, CZ), FiringPlanner.Style.SMOOTH);
				level.playSound(null, pad(10).offset(4, CY, CZ), SoundEvents.NETHERITE_BLOCK_PLACE,
					net.minecraft.sounds.SoundSource.BLOCKS, 0.8f, 1.0f + 0.05f * stage.throwsCount());
			});
			fuelUp();
		}));
	}

	/** Takes the pad's engine down (no drops: blocks are set to air, not broken) and builds the stage's in its place. */
	static void growTo(ServerLevel level, ServerPlayer player, Stage stage) {
		BlockPos at = pad(10);
		for (int x = -1; x <= 19; x++)
			for (int y = 0; y <= CY + 7; y++)
				for (int z = CZ - 7; z <= CZ + 7; z++)
					level.setBlock(at.offset(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		TANKS.removeIf(tank -> tank.pos().getX() >= at.getX() - 1 && tank.pos().getX() <= at.getX() + 19
			&& tank.pos().getZ() >= at.getZ() && tank.pos().getZ() <= at.getZ() + 12 && tank.pos().getY() >= at.getY());
		// Whatever was dropped is gone before the camera sees it
		for (net.minecraft.world.entity.item.ItemEntity item : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
			new net.minecraft.world.phys.AABB(at.getX() - 12, at.getY() - 2, at.getZ() - 12, at.getX() + 36, at.getY() + 14, at.getZ() + 20)))
			item.discard();
		// The builder takes its plugs and cam lobes from the player, who is out of them by the fourth stage: restock
		var inventory = player.getInventory();
		inventory.setItem(9, new ItemStack(CEBlocks.CAM_LOBE.get(), 64));
		inventory.setItem(10, new ItemStack(CEBlocks.CAMSHAFT.get(), 64));
		inventory.setItem(11, new ItemStack(CEItems.SPARK_PLUG.get(), 64));
		bank(level, player, at, stage.layout(), stage.throwsCount(), stage.kit(), STAGE_ALL, stage.precision());
	}

	/**
	 * A shot starts: the camera is a spectator's, with no interface, except in the gameplay clips, where it is the player's own
	 * eyes with the Bank Builder in hand (the hotbar and crosshair are hidden by {@link #onRenderGuiLayer}).
	 */
	static void startShot(Shot shot, Minecraft minecraft) {
		boolean firstPerson = FIRST_PERSON.containsKey(shot.name());
		minecraft.options.hideGui = !firstPerson;
		withPlayer((level, player) -> {
			player.setGameMode(firstPerson ? GameType.CREATIVE : GameType.SPECTATOR);
			// The headphones come off between shots, so only the shot that puts them on is muffled
			// The first shot reads the block through Create's goggles
			player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,
				shot.name().equals("heat_critical") ? new ItemStack(com.simibubi.create.AllItems.GOGGLES.get()) : ItemStack.EMPTY);
			if (firstPerson) {
				// Only the shot's own item on the hotbar (the Bank Builder, an oil filter, a dye); the parts the Bank Builder builds
				// with move up into the main inventory
				ItemStack held = FIRST_PERSON.get(shot.name())
					.get();
				var inventory = player.getInventory();
				List<ItemStack> parts = new ArrayList<>();
				for (int slot = 0; slot < 9; slot++)
					if (!inventory.getItem(slot)
						.isEmpty() && !inventory.getItem(slot)
							.is(held.getItem())) {
						parts.add(inventory.getItem(slot)
							.copy());
						inventory.setItem(slot, ItemStack.EMPTY);
					}
				int next = 9;
				for (ItemStack part : parts)
					inventory.setItem(next++, part);
				inventory.setItem(0, held);
				inventory.selected = 0;
			}
		});
		// No tutorial toast over the footage
		minecraft.options.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
		minecraft.getTutorial()
			.setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
		if (firstPerson && minecraft.player != null) {
			minecraft.player.getAbilities().flying = true;
			minecraft.player.onUpdateAbilities();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// Sound: only the pad the camera is in is heard
	// -----------------------------------------------------------------------------------------------------------------

	/** Sounds that started in the current pad (client thread only), so they can be stopped when the camera moves on. */
	static final List<SoundInstance> HEARD = new ArrayList<>();

	static boolean inCurrentPad(SoundInstance sound) {
		if (sound.isRelative() || sound.getAttenuation() == SoundInstance.Attenuation.NONE || SHOTS.isEmpty())
			return true;
		Scene scene = shotScene();
		// The wheeled car has no courtyard, and its sounds are made where its structure is kept, not where it is seen
		return scene == null || scene.bay() == AERO_BAY || onPad(scene.bay(), sound.getX(), sound.getZ());
	}

	/**
	 * Whether an engine, by its crankshaft, belongs to the shot: the shared engine voice passes {@link #inCurrentPad}, so
	 * other engines are kept out of it here. The wheeled car's is the one on no courtyard at all.
	 */
	static boolean engineInShot(BlockPos at) {
		if (SHOTS.isEmpty())
			return true;
		Scene scene = shotScene();
		if (scene == null)
			return true;
		if (scene.bay() != AERO_BAY)
			return onPad(scene.bay(), at.getX() + 0.5, at.getZ() + 0.5);
		return SCENES.stream()
			.noneMatch(other -> other.bay() != AERO_BAY && onPad(other.bay(), at.getX() + 0.5, at.getZ() + 0.5));
	}

	static Scene shotScene() {
		String name = SHOTS.get(Math.max(trailerShot, 0))
			.scene();
		for (Scene candidate : SCENES)
			if (candidate.name().equals(name))
				return candidate;
		return null;
	}
}
