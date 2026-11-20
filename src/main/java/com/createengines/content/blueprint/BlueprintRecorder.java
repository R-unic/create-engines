package com.createengines.content.blueprint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.jetbrains.annotations.Nullable;

import com.createengines.config.CEConfig;
import com.createengines.content.plate.MakerPlateBlock;
import com.createengines.content.battery.BatteryBlock;
import com.createengines.content.plate.MakerPlateBlockEntity;
import com.createengines.engine.EngineProviders;
import com.createengines.engine.blueprint.Blueprint;
import com.createengines.engine.blueprint.Blueprint.Limits;
import com.createengines.engine.topology.EngineGearing;
import com.createengines.engine.topology.EngineLayout;
import com.createengines.engine.topology.EngineScanner;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Reads a built engine into a {@link Blueprint}: every block of the engine the scanner found, as an offset from its
 * primary crankshaft, with each block state property that is not the block's default and every dial {@link EngineDials}
 * lists for it. This mod's blocks and any Create block the scanner counts as part of the engine (the shafts, cogwheels and
 * chain drives that couple crank lines) are recorded. A block of any other mod is counted in {@link Result#skipped()} and
 * is not in the blueprint (a pasted blueprint never places another mod's blocks).
 * <p>
 * Nothing is changed in the world, and the fluids, fuel and oil in the engine are not recorded: a blueprint is the
 * engine's build and tune, not its contents.
 */
public final class BlueprintRecorder {

	/** Why an engine could not be recorded. */
	public enum Failure {
		/** The block clicked is not part of a built engine. */
		NO_ENGINE,
		/** Part of the engine is in a chunk that is not loaded, so what is there is not known. */
		INCOMPLETE,
		/** More blocks than a blueprint holds, or a block further from the crankshaft than it reaches. */
		TOO_BIG,
		/** A block of this mod that a blueprint cannot describe (it names the block). */
		UNSUPPORTED
	}

	/**
	 * What recording gave: the blueprint, or why not.
	 *
	 * @param blueprint the recorded engine, null on failure
	 * @param failure   why not, null on success
	 * @param detail    what the failure is about (a block id), may be empty
	 * @param skipped   blocks of the engine that are neither this mod's nor Create's and so were left out
	 * @param origin    where the blueprint's origin (the engine's primary crankshaft) is in the world, null on failure
	 */
	public record Result(@Nullable Blueprint blueprint, @Nullable Failure failure, String detail, int skipped,
						 @Nullable BlockPos origin) {

		public boolean ok() {
			return blueprint != null;
		}

		static Result failed(Failure failure, String detail) {
			return new Result(null, failure, detail, 0, null);
		}
	}

	/** What a blueprint is called when nobody named it and no plate does. */
	public static final String DEFAULT_NAME = "Engine";

	private BlueprintRecorder() {
	}

	/** Records the engine that the block at {@code clicked} belongs to. */
	public static Result record(Level level, BlockPos clicked, String name, String builder) {
		Set<BlockPos> seeds = EngineScanner.crankSeeds(level, clicked);
		EngineLayout layout = seeds.isEmpty() ? null : EngineScanner.scan(level, seeds.iterator()
			.next());
		if (layout == null)
			return Result.failed(Failure.NO_ENGINE, "");
		if (layout.incomplete())
			return Result.failed(Failure.INCOMPLETE, "");
		BlockPos origin = layout.primary();
		List<BlockPos> members = new ArrayList<>(layout.members());
		// The gearing that turns the camshafts is Create's, and the scanner does not follow it, so find it here
		EngineGearing.Result gearing = EngineGearing.find(level, layout, CEConfig.MAX_COUPLING_WALK.get());
		for (BlockPos pos : gearing.blocks())
			if (!layout.members()
				.contains(pos))
				members.add(pos);
		// Parts a pack or addon added beside the crankshaft, which the scanner does not know
		for (BlockPos pos : EngineProviders.claimedBlocks(level, layout))
			if (!members.contains(pos))
				members.add(pos);
		members.sort(Comparator.<BlockPos>comparingInt(p -> p.getY())
			.thenComparingInt(p -> p.getZ())
			.thenComparingInt(p -> p.getX()));
		List<Blueprint.Part> parts = new ArrayList<>();
		int skipped = gearing.unlinked();
		for (BlockPos pos : members) {
			BlockState state = level.getBlockState(pos);
			ResourceLocation key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
			String id = key.toString();
			if (!EngineBlueprintPolicy.allows(state.getBlock())) {
				skipped++;
				continue;
			}
			int dx = pos.getX() - origin.getX();
			int dy = pos.getY() - origin.getY();
			int dz = pos.getZ() - origin.getZ();
			if (Math.abs(dx) > Limits.MAX_SPAN || Math.abs(dy) > Limits.MAX_SPAN || Math.abs(dz) > Limits.MAX_SPAN
				|| parts.size() >= Limits.MAX_PARTS)
				return Result.failed(Failure.TOO_BIG, "");
			Map<String, String> properties = changedProperties(state);
			if (properties.size() > Limits.MAX_ENTRIES)
				return Result.failed(Failure.UNSUPPORTED, id);
			parts.add(new Blueprint.Part(dx, dy, dz, id, properties, dials(level, pos, id)));
		}
		if (parts.isEmpty())
			return Result.failed(Failure.NO_ENGINE, "");
		MakerPlateBlockEntity named = plateOn(level, members, false);
		MakerPlateBlockEntity stamped = plateOn(level, members, true);
		String called = !name.isEmpty() ? name : named != null && !named.name()
			.isEmpty() ? named.name() : DEFAULT_NAME;
		return new Result(new Blueprint(called, builder, parts, stamped == null ? null : stamped.figures()), null, "", skipped, origin);
	}

	/** The first Maker's Plate fixed to one of the engine's blocks (a stamped one if {@code stampedOnly}), or null. */
	private static @Nullable MakerPlateBlockEntity plateOn(Level level, List<BlockPos> members, boolean stampedOnly) {
		for (BlockPos pos : members)
			for (Direction side : Direction.values()) {
				BlockPos at = pos.relative(side);
				BlockState state = level.getBlockState(at);
				if (state.getBlock() instanceof MakerPlateBlock && state.getValue(BlockStateProperties.FACING) == side
					&& level.getBlockEntity(at) instanceof MakerPlateBlockEntity plate && (!stampedOnly || plate.figures()
						.stamped()))
					return plate;
			}
		return null;
	}

	/** The block state's properties that differ from the block's default state, by name. */
	static Map<String, String> changedProperties(BlockState state) {
		Block block = state.getBlock();
		BlockState standard = block.defaultBlockState();
		Map<String, String> changed = new TreeMap<>();
		for (Property<?> property : state.getProperties())
			// A battery's lamp strip shows what it holds, and its joins what touches it: a blueprint carries neither
			if (!BatteryBlock.isOnlyForLooks(state, property) && !state.getValue(property)
				.equals(standard.getValue(property)))
				changed.put(property.getName(), valueName(state, property));
		return changed;
	}

	private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> property) {
		return property.getName(state.getValue(property));
	}

	private static Map<String, Integer> dials(Level level, BlockPos pos, String id) {
		Map<String, Integer> values = new TreeMap<>();
		List<EngineDials.Dial> dials = EngineDials.of(id);
		if (dials.isEmpty())
			return values;
		BlockEntity entity = level.getBlockEntity(pos);
		if (entity == null)
			return values;
		for (EngineDials.Dial dial : dials) {
			ScrollValueBehaviour behaviour = dial.on(entity);
			if (behaviour != null)
				values.put(dial.name(), behaviour.getValue());
		}
		return values;
	}
}
