package com.createengines.client;

import static com.createengines.client.ShowcaseRun.CY;
import static com.createengines.client.ShowcaseRun.CZ;
import static com.createengines.client.ShowcaseRun.pad;
import static com.createengines.client.ShowcaseRun.set;

import java.lang.reflect.Method;
import java.util.function.BiConsumer;

import com.createengines.CreateEngines;
import com.createengines.client.ShowcaseRunStaging.Cued;
import com.createengines.content.cylinder.CylinderBlockEntity;
import com.createengines.content.head.CylinderHeadBlockEntity;
import com.createengines.content.rotor.RotorHousingBlockEntity;
import com.createengines.content.tool.BankBuilderItem;
import com.createengines.content.turbo.TurbochargerBlockEntity;
import com.createengines.engine.WearKeeper;
import com.createengines.engine.sim.Tuning;
import com.createengines.engine.sim.Wear;
import com.createengines.registry.CEBlocks;
import com.createengines.registry.CEItems;
import com.createengines.registry.CESounds;
import com.createengines.gametest.TestEngine;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.logistics.depot.DepotBehaviour;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;

/**
 * The 2.4 trailer's pads about wear and heat (see {@link ShowcaseRun}): the turbocharged four that is run critically hot
 * and later loses a gasket and its turbo, the twin whose rods are changed from light to heavy, the row of wear parts, the
 * saw that cuts belts into gaskets, the rotary's apex seals, and the fan and basin on the hot engine's radiators. The run's
 * config has the wear mode on (tools/trailer/record.py).
 * <p>
 * What is staged: a failure cannot be waited for, so no part fails by chance during the run ({@link WearKeeper#roller}, the
 * hook the GameTests use) and each shot's failure is set on its cue, with the mod's own failed state, sound and particles.
 * The hot engine's temperature is held. The rod twin has the mod's own crank inertia, so its surge can be seen. Everything
 * the parts then look, sound and do like is the mod's and Create's own.
 */
final class ShowcaseRunWear {

	static final int WEAR_BAY = 25;
	static final int ROD_BAY = 27;
	static final int PARTS_BAY = 28;
	private static final int WEAR_THROWS = 4;
	private static final int ROD_THROWS = 2;
	/** Which throws' heads lose their gaskets, as blocks along the crank: in the hot shot and in the cold one. */
	private static final int HOT_HEAD = 2;
	private static final int COLD_HEAD = 3;
	/** Where the saw stands on the parts pad, apart from the row of parts. */
	private static final int SAW_X = 12;
	/** The block temperature the heating radiators are held at: hot, short of critical, so no head warps. */
	private static final double WORKING_BLOCK = 1.4;
	/** The block temperature of the first shot: 190 degrees, well past critical. */
	private static final double CRITICAL_BLOCK = 1.7;

	private ShowcaseRunWear() {
	}

	static void build(ServerLevel level, ServerPlayer player) {
		WearKeeper.roller = () -> 1.0;
		ShowcaseRun.attempt("the wear engine", () -> ShowcaseRunBuild.bank(level, player, pad(WEAR_BAY), BankBuilderItem.Layout.INLINE,
			WEAR_THROWS, new ShowcaseRun.Kit(ShowcaseRun.Fuel.GASOLINE, 1, false, 0, false, false, false)));
		ShowcaseRun.attempt("the rod twin", () -> {
			ShowcaseRunBuild.bank(level, player, pad(ROD_BAY), BankBuilderItem.Layout.INLINE, ROD_THROWS,
				ShowcaseRun.Kit.plain(ShowcaseRun.Fuel.GASOLINE));
			// No flywheel, so that each firing shows in the crank's speed
			set(level, pad(ROD_BAY), ROD_THROWS + 5, CY, CZ, Blocks.AIR.defaultBlockState());
			rods(level, Wear.Rod.LIGHT);
		});
		ShowcaseRun.attempt("the wear parts", () -> parts(level, pad(PARTS_BAY)));
		ShowcaseRun.attempt("the gasket saw", () -> saw(level, pad(PARTS_BAY)));
	}

	private static void rods(ServerLevel level, Wear.Rod kind) {
		for (int x = 2; x <= ROD_THROWS + 1; x++)
			if (level.getBlockEntity(pad(ROD_BAY).offset(x, CY + 1, CZ)) instanceof CylinderBlockEntity cylinder)
				cylinder.setRodKind(kind);
			else
				CreateEngines.LOGGER.warn("Showcase: no cylinder for a {} rod at {} along the twin", kind, x);
	}

	/** The four gaskets, a Service Kit and a set of Apex Seals, each on a depot of its own in a row. */
	private static void parts(ServerLevel level, BlockPos at) {
		ShowcaseRunBuild.dais(level, at, SAW_X + 4);
		Item[] shown = { CEItems.STOCK_GASKET.get(), CEItems.CARDBOARD_GASKET.get(), CEItems.RUBBER_GASKET.get(),
			CEItems.COPPER_GASKET.get(), CEItems.SERVICE_KIT.get(), CEItems.APEX_SEALS.get() };
		for (int i = 0; i < shown.length; i++) {
			BlockPos pos = at.offset(1 + i, 1, CZ);
			level.setBlock(pos, AllBlocks.DEPOT.getDefaultState(), Block.UPDATE_ALL);
			if (level.getBlockEntity(pos) instanceof DepotBlockEntity depot) {
				var held = depot.getBehaviour(DepotBehaviour.TYPE);
				if (held != null)
					held.setHeldItem(new com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack(
						new ItemStack(shown[i])));
				depot.sendData();
			}
		}
	}

	/** A Mechanical Saw lying face up, its shaft along X, turned by a motor: what is dropped on it is cut. */
	private static void saw(ServerLevel level, BlockPos at) {
		set(level, at, SAW_X, 1, CZ, AllBlocks.MECHANICAL_SAW.getDefaultState()
			.setValue(DirectionalKineticBlock.FACING, Direction.UP)
			.setValue(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE, true));
		ShowcaseRunBuild.motor(level, at.offset(SAW_X - 1, 1, CZ), Direction.EAST, 48);
	}

	private static void dropBelt(ServerLevel level) {
		BlockPos saw = pad(PARTS_BAY).offset(SAW_X, 1, CZ);
		ItemEntity belt = new ItemEntity(level, saw.getX() + 0.5, saw.getY() + 1.4, saw.getZ() + 0.5,
			new ItemStack(AllItems.BELT_CONNECTOR.get()), 0, 0, 0);
		belt.setPickUpDelay(32767);
		level.addFreshEntity(belt);
	}

	private static void logSaw(ServerLevel level) {
		BlockPos saw = pad(PARTS_BAY).offset(SAW_X, 1, CZ);
		StringBuilder seen = new StringBuilder();
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(saw).inflate(6)))
			seen.append(item.getItem())
				.append("; ");
		CreateEngines.LOGGER.info("Showcase saw: speed {}, lying about it: {}",
			level.getBlockEntity(saw) instanceof com.simibubi.create.content.kinetics.saw.SawBlockEntity be ? be.getSpeed() : 0, seen);
	}

	/** Puts sound gaskets back under the wear engine's heads that a shot blew. */
	private static void regasket(ServerLevel level) {
		for (int x : new int[] { HOT_HEAD, COLD_HEAD })
			if (level.getBlockEntity(pad(WEAR_BAY).offset(x, CY + 2, CZ)) instanceof CylinderHeadBlockEntity head && head.blown())
				head.fit(Wear.Gasket.STOCK);
	}

	private static void blowGasket(ServerLevel level, int x) {
		BlockPos pos = pad(WEAR_BAY).offset(x, CY + 2, CZ);
		checkWearMode();
		if (!(level.getBlockEntity(pos) instanceof CylinderHeadBlockEntity head)) {
			CreateEngines.LOGGER.warn("Showcase: no head to blow at {}", pos);
			return;
		}
		head.blow();
		fail(level, pos, CESounds.GASKET_BLOW.get());
		CreateEngines.LOGGER.info("Showcase: blew the gasket at {} (shows: {})", pos, head.showsBlown());
	}

	private static void failTurbo(ServerLevel level) {
		checkWearMode();
		for (var part : ShowcaseRunStaging.parts(level, WEAR_BAY, WEAR_THROWS))
			if (part instanceof TurbochargerBlockEntity turbo) {
				turbo.addWear(1);
				turbo.fail();
				fail(level, turbo.getBlockPos(), CESounds.TURBO_FAIL.get());
				return;
			}
		CreateEngines.LOGGER.warn("Showcase: no turbo to fail on the wear engine");
	}

	private static RotorHousingBlockEntity rotor(ServerLevel level) {
		return level.getBlockEntity(pad(ShowcaseRun.ROTARY_BAY).offset(TestEngine.HOUSING)) instanceof RotorHousingBlockEntity housing
			? housing : null;
	}

	/** The rotary's own shot is later in the cut, and finds it with new seals. */
	private static void newSeals(ServerLevel level) {
		RotorHousingBlockEntity housing = rotor(level);
		if (housing != null)
			housing.fitSeals();
	}

	private static void rotaryStarter(ServerLevel level, boolean on) {
		level.setBlock(pad(ShowcaseRun.ROTARY_BAY).offset(TestEngine.STARTER_POWER),
			on ? Blocks.REDSTONE_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
	}

	private static void failApex(ServerLevel level) {
		checkWearMode();
		RotorHousingBlockEntity housing = rotor(level);
		if (housing == null) {
			CreateEngines.LOGGER.warn("Showcase: no rotor housing whose seals could fail");
			return;
		}
		housing.addApexWear(1);
		housing.failApex();
		BlockPos pos = housing.getBlockPos();
		fail(level, pos, CESounds.APEX_FAIL.get());
		// The same sparks the mod throws when a set goes (WearKeeper.failApex, which takes an engine's cylinder, not a block)
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, 20, 0.35, 0.35,
			0.35, 0.2);
	}

	/** A failure's own sound, and the mod's puff of smoke. */
	private static void fail(ServerLevel level, BlockPos pos, SoundEvent sound) {
		level.playSound(null, pos, sound, SoundSource.BLOCKS, 1.2f, 1.0f);
		try {
			Method smoke = WearKeeper.class.getDeclaredMethod("smoke", Level.class, BlockPos.class);
			smoke.setAccessible(true);
			smoke.invoke(null, level, pos);
		} catch (ReflectiveOperationException e) {
			CreateEngines.LOGGER.error("Showcase: could not raise the failure's smoke", e);
		}
	}

	private static void checkWearMode() {
		if (!Tuning.wearMode)
			CreateEngines.LOGGER.warn("Showcase: the wear mode is off, so a failure will not show (tools/trailer/record.py turns it on)");
	}

	/**
	 * On the hot engine's pad: a full radiator beside its first journal with a basin on top under a Mixer, and an Encased Fan
	 * blowing at the radiator two along, which the shot before fitted.
	 */
	private static void fanAndBasin(ServerLevel level) {
		BlockPos at = pad(ShowcaseRunFeatures.HEAT_BAY);
		for (int x : new int[] { 1, 4 })
			ShowcaseRunFeatures.radiator(level, x);
		set(level, at, 1, CY + 1, CZ - 1, AllBlocks.BASIN.getDefaultState());
		set(level, at, 1, CY + 3, CZ - 1, AllBlocks.MECHANICAL_MIXER.getDefaultState());
		ShowcaseRunWorks.drive(level, at, 0, CY + 3, CZ - 1, Axis.Y, Direction.UP, 64);
		// Facing south, at the radiator; a positive speed from a motor on its south-facing shaft blows that way
		set(level, at, 4, CY, CZ - 2, AllBlocks.ENCASED_FAN.getDefaultState()
			.setValue(BlockStateProperties.FACING, Direction.SOUTH));
		ShowcaseRunBuild.motor(level, at.offset(4, CY, CZ - 3), Direction.SOUTH, 128);
	}

	/** Copper and zinc into the basin: Create's brass, a recipe that needs heat. */
	private static void stockBasin(ServerLevel level) {
		BlockPos basin = pad(ShowcaseRunFeatures.HEAT_BAY).offset(1, CY + 1, CZ - 1);
		var items = level.getCapability(Capabilities.ItemHandler.BLOCK, basin, Direction.UP);
		if (items == null) {
			CreateEngines.LOGGER.warn("Showcase: no basin to stock at {}", basin);
			return;
		}
		for (Item metal : new Item[] { Items.COPPER_INGOT, AllItems.ZINC_INGOT.get() }) {
			ItemStack rest = new ItemStack(metal, 16);
			for (int slot = 0; slot < items.getSlots() && !rest.isEmpty(); slot++)
				rest = items.insertItem(slot, rest, false);
		}
	}

	private static void logRadiators(ServerLevel level) {
		BlockPos basin = pad(ShowcaseRunFeatures.HEAT_BAY).offset(1, CY + 1, CZ - 1);
		var items = level.getCapability(Capabilities.ItemHandler.BLOCK, basin, Direction.UP);
		StringBuilder held = new StringBuilder();
		for (int slot = 0; items != null && slot < items.getSlots(); slot++)
			if (!items.getStackInSlot(slot)
				.isEmpty())
				held.append(items.getStackInSlot(slot))
					.append("; ");
		CreateEngines.LOGGER.info("Showcase basin holds: {}", held);
		for (int x : new int[] { 1, 4 }) {
			BlockPos pos = pad(ShowcaseRunFeatures.HEAT_BAY).offset(x, CY, CZ - 1);
			if (level.getBlockEntity(pos) instanceof com.createengines.content.radiator.RadiatorBlockEntity radiator)
				CreateEngines.LOGGER.info("Showcase radiator {}: air {}, heat {}, state {}", x, radiator.air(), radiator.heat(),
					radiator.getBlockState());
		}
	}

	static void defineCues(BiConsumer<String, Cued> cue) {
		// The first shot: through goggles, the block critically hot and steaming, then a gasket lets go
		ShowcaseRunStaging.hold(cue, "heat_critical", 0, 6, level -> ShowcaseRunStaging.heat(level, WEAR_BAY, CRITICAL_BLOCK));
		cue.accept("heat_critical", new Cued(3.0, level -> blowGasket(level, HOT_HEAD)));

		// Light rods, then heavy ones, in the same engine under the same camera
		ShowcaseRunStaging.hold(cue, "rods_light_heavy", 0, 6, level -> ShowcaseRunStaging.light(level, ROD_BAY));
		cue.accept("rods_light_heavy", new Cued(0, level -> rods(level, Wear.Rod.LIGHT)));
		cue.accept("rods_light_heavy", new Cued(3.0, level -> rods(level, Wear.Rod.HEAVY)));
		for (double at : new double[] { 2.75, 5.5 })
			cue.accept("rods_light_heavy", new Cued(at, level -> ShowcaseRunStaging.log(level, ROD_BAY, "the rod twin")));

		// The first shot's engine, cooled and put right, loses another gasket, seen from across the room
		cue.accept("wear_gasket_blown", new Cued(0, level -> {
			ShowcaseRunStaging.heat(level, WEAR_BAY, 0.75);
			regasket(level);
		}));
		cue.accept("wear_gasket_blown", new Cued(1.5, ShowcaseRunWear::blowGasket3));
		cue.accept("wear_apex", new Cued(0.5, ShowcaseRunWear::failApex));
		cue.accept("rotary_orbit", new Cued(2, level -> ShowcaseRunStaging.log(level, ShowcaseRun.ROTARY_BAY, "the rotary")));
		cue.accept("wear_turbo", new Cued(0, ShowcaseRunWear::regasket));
		cue.accept("wear_turbo", new Cued(0.5, ShowcaseRunWear::failTurbo));
		// A failed set stops the whole rotary: with new seals in, it is started again while the camera is elsewhere
		cue.accept("wear_turbo", new Cued(0, level -> {
			newSeals(level);
			rotaryStarter(level, true);
		}));
		cue.accept("gasket_saw", new Cued(5.5, level -> rotaryStarter(level, false)));

		for (int i = 0; i < 4; i++)
			cue.accept("gasket_saw", new Cued(0.25 + 1.25 * i, ShowcaseRunWear::dropBelt));

		for (double at : new double[] { 2.5, 5.75 })
			cue.accept("gasket_saw", new Cued(at, ShowcaseRunWear::logSaw));

		cue.accept("radiator_fan_basin", new Cued(0, ShowcaseRunWear::fanAndBasin));
		cue.accept("radiator_fan_basin", new Cued(0.5, ShowcaseRunWear::stockBasin));
		ShowcaseRunStaging.hold(cue, "radiator_fan_basin", 0, 6,
			level -> ShowcaseRunStaging.heat(level, ShowcaseRunFeatures.HEAT_BAY, WORKING_BLOCK));
		for (double at : new double[] { 2, 5.5 })
			cue.accept("radiator_fan_basin", new Cued(at, ShowcaseRunWear::logRadiators));
	}

	private static void blowGasket3(ServerLevel level) {
		blowGasket(level, COLD_HEAD);
	}
}
