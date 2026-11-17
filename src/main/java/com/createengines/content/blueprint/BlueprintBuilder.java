package com.createengines.content.blueprint;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.createengines.engine.blueprint.Blueprint;
import com.createengines.engine.blueprint.BlueprintCodec;
import com.createengines.engine.blueprint.BlueprintException;
import com.createengines.engine.blueprint.BlueprintRules;
import com.createengines.registry.CELang;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.common.util.BlockSnapshot;

/**
 * Lays an engine out from a recorded blueprint, as the player: every block in the blueprint, in the orientation it was
 * recorded in, with its crankshaft at {@code origin}, from the parts the player carries. All or nothing: the whole site is
 * checked first (loaded, inside the world, free to build in, allowed to the player, every part in the inventory) and
 * nothing is placed or taken unless every check passes. Claims and protection mods get to refuse each block through the
 * ordinary place event, as they would for the player's own hand.
 * <p>
 * Blocks go down as they were recorded and each dial is set as it was; the engine is not started and holds no fluid. Create's
 * own blocks that the scanner counted as part of the engine (couplings, shafts and cogwheels on a crank line) are in a
 * blueprint and are built too, paid for like any other block.
 */
public final class BlueprintBuilder {

	/** How an attempt ended. */
	public enum Status {
		BUILT,
		/** The blueprint is damaged or broke the server's rules. */
		REFUSED,
		/** Part of the site is in an unloaded chunk or outside the world. */
		UNLOADED,
		/** Blocks already stand where the engine would go; {@link Result#lines()} names the first. */
		BLOCKED,
		/** The player may not build there (spawn protection, a claim, another mod). */
		PROTECTED,
		/** A block that has no item to pay for it, so it cannot be laid out by hand. */
		UNBUILDABLE,
		/** The player does not carry every part; {@link Result#lines()} lists what is short. */
		MISSING
	}

	/**
	 * @param status how it ended
	 * @param placed blocks placed
	 * @param lines  the first few things that stopped it, ready to show: positions or "3 x Cylinder"
	 */
	public record Result(Status status, int placed, List<String> lines) {

		static Result of(Status status) {
			return new Result(status, 0, List.of());
		}
	}

	/** Most things listed when a build is refused. */
	public static final int MAX_LINES = 5;

	private BlueprintBuilder() {
	}

	/** Decodes, checks and builds a recorded blueprint with its crankshaft at {@code origin}. */
	public static Result build(Player player, Level level, BlockPos origin, RecordedBlueprint recorded) {
		Blueprint blueprint;
		try {
			blueprint = BlueprintCodec.decode(recorded.text());
			BlueprintRules.check(blueprint, EngineBlueprintPolicy.INSTANCE);
		} catch (BlueprintException e) {
			return Result.of(Status.REFUSED);
		}
		return build(player, level, origin, blueprint);
	}

	/** Builds a blueprint that has already been checked. */
	public static Result build(Player player, Level level, BlockPos origin, Blueprint blueprint) {
		List<Placement> placements = new ArrayList<>();
		for (Blueprint.Part part : blueprint.parts()) {
			BlockState state = stateOf(part);
			if (state == null)
				return Result.of(Status.REFUSED);
			placements.add(new Placement(part, origin.offset(part.x(), part.y(), part.z()), state));
		}
		placements.sort((a, b) -> Integer.compare(a.pos.getY(), b.pos.getY()));

		for (Placement placement : placements)
			if (!level.isLoaded(placement.pos) || level.isOutsideBuildHeight(placement.pos) || !level.getWorldBorder()
				.isWithinBounds(placement.pos))
				return Result.of(Status.UNLOADED);

		List<String> lines = new ArrayList<>();
		for (Placement placement : placements) {
			BlockState there = level.getBlockState(placement.pos);
			if (!(there.isAir() || there.canBeReplaced()) && lines.size() < MAX_LINES)
				lines.add(at(placement.pos));
		}
		if (!lines.isEmpty())
			return new Result(Status.BLOCKED, 0, lines);

		for (Placement placement : placements)
			if (!player.mayInteract(level, placement.pos) || vetoed(player, level, placement)) {
				if (lines.size() < MAX_LINES)
					lines.add(at(placement.pos));
			}
		if (!lines.isEmpty())
			return new Result(Status.PROTECTED, 0, lines);

		Map<String, Need> needs = new LinkedHashMap<>();
		boolean free = player.isCreative();
		for (Placement placement : placements) {
			List<ItemStack> cost = cost(placement.state);
			if (cost == null) {
				if (!free) {
					lines.add(placement.part.block());
					return new Result(Status.UNBUILDABLE, 0, lines);
				}
				continue;
			}
			for (ItemStack stack : cost)
				needs.computeIfAbsent(key(stack), k -> new Need(stack.copyWithCount(1)))
					.count += stack.getCount();
		}
		if (!free) {
			for (Need need : needs.values()) {
				int carried = carried(player, need.sample);
				if (carried < need.count && lines.size() < MAX_LINES)
					lines.add((need.count - carried) + " x " + need.sample.getHoverName()
						.getString());
			}
			if (!lines.isEmpty())
				return new Result(Status.MISSING, 0, lines);
			for (Need need : needs.values())
				take(player, need.sample, need.count);
		}

		for (Placement placement : placements)
			level.setBlock(placement.pos, placement.state, Block.UPDATE_ALL);
		for (Placement placement : placements)
			setDials(level, placement);
		level.playSound(null, origin, SoundEvents.NETHERITE_BLOCK_PLACE, SoundSource.BLOCKS, 0.8f, 1.2f);
		return new Result(Status.BUILT, placements.size(), List.of());
	}

	private record Placement(Blueprint.Part part, BlockPos pos, BlockState state) {
	}

	private static final class Need {
		final ItemStack sample;
		int count;

		Need(ItemStack sample) {
			this.sample = sample;
		}
	}

	/** The block state a part describes: the block's default with the recorded properties set. Null if it is not valid. */
	public static @Nullable BlockState stateOf(Blueprint.Part part) {
		Block block = EngineBlueprintPolicy.block(part.block());
		if (block == null)
			return null;
		BlockState state = block.defaultBlockState();
		for (Map.Entry<String, String> entry : part.state()
			.entrySet()) {
			Property<?> property = block.getStateDefinition()
				.getProperty(entry.getKey());
			if (property == null)
				return null;
			state = with(state, property, entry.getValue());
			if (state == null)
				return null;
		}
		return state;
	}

	private static <T extends Comparable<T>> @Nullable BlockState with(BlockState state, Property<T> property, String value) {
		return property.getValue(value)
			.map(v -> state.setValue(property, v))
			.orElse(null);
	}

	/** What one block costs: the housing and every part fitted in it. Null if it has nothing to pay for it with. */
	public static @Nullable List<ItemStack> cost(BlockState state) {
		ItemRequirement requirement = ItemRequirement.of(state, null);
		List<ItemStack> stacks = new ArrayList<>();
		if (!requirement.isInvalid() && !requirement.isEmpty())
			for (var required : requirement.getRequiredItems())
				stacks.add(required.stack.copy());
		if (stacks.isEmpty()) {
			Item item = state.getBlock()
				.asItem();
			if (item == Items.AIR)
				return null;
			stacks.add(new ItemStack(item));
		}
		return stacks;
	}

	private static String key(ItemStack stack) {
		return stack.getItem()
			.toString();
	}

	private static int carried(Player player, ItemStack sample) {
		int total = 0;
		for (int slot = 0; slot < player.getInventory()
			.getContainerSize(); slot++) {
			ItemStack held = player.getInventory()
				.getItem(slot);
			if (ItemStack.isSameItem(held, sample))
				total += held.getCount();
		}
		return total;
	}

	private static void take(Player player, ItemStack sample, int count) {
		int left = count;
		for (int slot = 0; slot < player.getInventory()
			.getContainerSize() && left > 0; slot++) {
			ItemStack held = player.getInventory()
				.getItem(slot);
			if (!ItemStack.isSameItem(held, sample))
				continue;
			int used = Math.min(left, held.getCount());
			held.shrink(used);
			left -= used;
		}
	}

	/** Whether another mod (a claim, a protection) cancels the place event for this block. */
	private static boolean vetoed(Player player, Level level, Placement placement) {
		BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, placement.pos);
		BlockEvent.EntityPlaceEvent event = new BlockEvent.EntityPlaceEvent(snapshot, level.getBlockState(placement.pos.below()),
			player);
		NeoForge.EVENT_BUS.post(event);
		return event.isCanceled();
	}

	private static void setDials(Level level, Placement placement) {
		if (placement.part.dials()
			.isEmpty())
			return;
		BlockEntity entity = level.getBlockEntity(placement.pos);
		if (entity == null)
			return;
		for (Map.Entry<String, Integer> dial : placement.part.dials()
			.entrySet()) {
			EngineDials.Dial found = EngineDials.find(placement.part.block(), dial.getKey());
			var behaviour = found == null ? null : found.on(entity);
			if (behaviour != null)
				behaviour.setValue(dial.getValue());
		}
	}

	private static String at(BlockPos pos) {
		return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
	}

	/** Tells the player how a build went. */
	public static void report(Player player, Result result, String name) {
		switch (result.status()) {
			case BUILT -> tell(player, CELang.translate("blueprint.built", result.placed())
				.component(), ChatFormatting.GREEN, true);
			case REFUSED -> tell(player, CELang.translate("blueprint.refused")
				.component(), ChatFormatting.RED, true);
			case UNLOADED -> tell(player, CELang.translate("blueprint.build.unloaded")
				.component(), ChatFormatting.RED, true);
			case BLOCKED -> {
				tell(player, CELang.translate("blueprint.build.blocked", name)
					.component(), ChatFormatting.RED, true);
				list(player, result);
			}
			case PROTECTED -> {
				tell(player, CELang.translate("blueprint.build.protected")
					.component(), ChatFormatting.RED, true);
				list(player, result);
			}
			case UNBUILDABLE -> tell(player, CELang.translate("blueprint.build.unbuildable", result.lines()
				.get(0))
				.component(), ChatFormatting.RED, true);
			case MISSING -> {
				tell(player, CELang.translate("blueprint.build.missing", name)
					.component(), ChatFormatting.RED, true);
				list(player, result);
			}
		}
	}

	private static void list(Player player, Result result) {
		for (String line : result.lines())
			tell(player, Component.literal("  " + line), ChatFormatting.GRAY, false);
	}

	private static void tell(Player player, Component message, ChatFormatting colour, boolean overlay) {
		player.displayClientMessage(message.copy()
			.withStyle(colour), overlay);
	}
}
