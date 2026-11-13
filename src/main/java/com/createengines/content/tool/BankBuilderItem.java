package com.createengines.content.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.createengines.advancement.CETriggers;
import com.createengines.advancement.Milestone;
import com.createengines.config.CEConfig;
import com.createengines.content.camshaft.CamshaftBlock;
import com.createengines.content.crankshaft.CrankshaftBlock;
import com.createengines.content.cylinder.CylinderBlock;
import com.createengines.content.head.CylinderHeadBlock;
import com.createengines.registry.CEBlocks;
import com.createengines.registry.CEItems;
import com.createengines.registry.CELang;
import com.tterrag.registrate.util.entry.ItemEntry;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Lays out a bank of cylinders in one click. Using it on a crank throw sets a cylinder, its head and (from what the
 * builder has in their pockets) the piston, rod and injector on the face that was clicked, and on the same face of every
 * other throw of the crankshaft. The layout decides which faces:
 * <ul>
 *     <li><b>Inline</b>: the clicked face only.</li>
 *     <li><b>Vee</b>: the clicked face and the one a quarter turn round the crank from it, as in a 90 degree V.</li>
 *     <li><b>Flat</b>: the clicked face and the one opposite it, as in a boxer.</li>
 * </ul>
 * Sneak-using it, in the air or on a block, changes the layout, and each time the layouts come round it changes between
 * standard and precision parts. Precision builds use precision cylinders, heads, forged pistons and precision injectors,
 * and also swap those in for the standard parts already on the throws, handing the old ones back. Parts come out of the
 * player's inventory (a creative player needs none); a spot that is already built is only filled in, and a spot that is
 * blocked is left alone.
 * <p>
 * Two extras are laid only for a builder who is carrying them, creative or not, so a diesel stays as it should be:
 * <b>spark plugs</b> go into the heads, and <b>camshafts and cam lobes</b> make a cam line beside the bank, parallel to
 * the crank, with a lobe in reach of every cylinder: beside each block for an inline or a flat, in the valley for a vee,
 * where one lobe times both banks. The line still has to be geared to half the crank's speed and its lobes timed (the
 * Timing Tool does that). Pipes and fuel are still the builder's.
 */
public class BankBuilderItem extends Item {

	public enum Layout {
		INLINE, VEE, FLAT
	}

	private static final String LAYOUT_TAG = "Layout";
	private static final String PRECISION_TAG = "Precision";
	private static final Layout[] LAYOUTS = Layout.values();

	public BankBuilderItem(Properties properties) {
		super(properties);
	}

	public static Layout layout(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null)
			return Layout.INLINE;
		return LAYOUTS[Math.floorMod(data.copyTag()
			.getInt(LAYOUT_TAG), LAYOUTS.length)];
	}

	/** Whether the builder is set to precision parts. */
	public static boolean precision(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag()
			.getBoolean(PRECISION_TAG);
	}

	private static Component gradeName(boolean precision) {
		return CELang.translate(precision ? "tool.bank_builder.grade.precision" : "tool.bank_builder.grade.standard")
			.component();
	}

	private static Component layoutName(Layout layout) {
		return CELang.translate("tool.bank_builder.layout." + layout.name()
			.toLowerCase(Locale.ROOT))
			.component();
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!player.isShiftKeyDown())
			return InteractionResultHolder.pass(stack);
		cycle(stack, level, player);
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		Player player = context.getPlayer();
		if (player == null)
			return InteractionResult.PASS;
		ItemStack stack = context.getItemInHand();
		if (player.isShiftKeyDown()) {
			cycle(stack, level, player);
			return InteractionResult.sidedSuccess(level.isClientSide);
		}
		if (!level.isClientSide)
			build(level, context.getClickedPos(), context.getClickedFace(), layout(stack), precision(stack), player);
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	private static void cycle(ItemStack stack, Level level, Player player) {
		if (level.isClientSide)
			return;
		Layout next = LAYOUTS[(layout(stack).ordinal() + 1) % LAYOUTS.length];
		// The layouts come round to inline again: that is when the grade of parts changes
		boolean grade = precision(stack) ^ next == Layout.INLINE;
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			tag.putInt(LAYOUT_TAG, next.ordinal());
			tag.putBoolean(PRECISION_TAG, grade);
		});
		player.displayClientMessage(CELang.translate("tool.bank_builder.layout", layoutName(next), gradeName(grade))
			.component(), true);
		level.playSound(null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.5f,
			1.4f);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(CELang.translate("tool.bank_builder.layout", layoutName(layout(stack)), gradeName(precision(stack)))
			.component()
			.copy()
			.withStyle(ChatFormatting.AQUA));
		tooltip.add(CELang.translate("tool.bank_builder.hint")
			.component()
			.copy()
			.withStyle(ChatFormatting.DARK_GRAY));
	}

	/**
	 * What a build did.
	 *
	 * @param throwsFound crank throws on the line
	 * @param cylinders   cylinder blocks placed
	 * @param heads       head blocks placed
	 * @param fitted      pistons, rods and injectors fitted
	 * @param blocked     places something else was in the way of
	 * @param missing     blocks that were needed and the builder did not have
	 * @param upgraded    standard parts swapped for precision ones
	 * @param camBlocks   camshaft and cam lobe blocks laid beside the bank
	 */
	public record Result(int throwsFound, int cylinders, int heads, int fitted, int blocked, int missing, int upgraded,
						 int camBlocks) {
	}

	/**
	 * Where the cam line or lines go, as offsets from a throw, for a layout built on the face {@code side}: beside the
	 * cylinder block on the side a quarter turn round the crank from the bore (the next face a vee builds on), so
	 * the lobe's axis is the crank's and does not point into the block. A vee has the one line in its valley, touching
	 * the block of both banks; a flat has a line by each block.
	 */
	public static List<BlockPos> camOffsets(Layout layout, Axis axis, Direction side) {
		Direction beside = side.getClockWise(axis);
		return switch (layout) {
			case INLINE, VEE -> List.of(BlockPos.ZERO.relative(side)
				.relative(beside));
			case FLAT -> List.of(BlockPos.ZERO.relative(side)
				.relative(beside),
				BlockPos.ZERO.relative(side.getOpposite())
					.relative(beside));
		};
	}

	/** The faces of a throw that a layout builds on, given the face that was clicked. */
	public static List<Direction> faces(Layout layout, Axis axis, Direction clicked) {
		List<Direction> faces = new ArrayList<>();
		faces.add(clicked);
		if (layout == Layout.FLAT)
			faces.add(clicked.getOpposite());
		else if (layout == Layout.VEE)
			faces.add(clicked.getClockWise(axis));
		return faces;
	}

	private static void build(Level level, BlockPos clicked, Direction face, Layout layout, boolean precision,
							  Player player) {
		BlockState state = level.getBlockState(clicked);
		if (!(state.getBlock() instanceof CrankshaftBlock)) {
			tell(player, CELang.translate("tool.bank_builder.no_crank")
				.component(), ChatFormatting.RED);
			return;
		}
		Axis axis = state.getValue(BlockStateProperties.AXIS);
		// Clicking an end of the crank has no side to build on: use the top, or the north for an upright crank
		Direction side = face.getAxis() == axis ? axis == Axis.Y ? Direction.NORTH : Direction.UP : face;
		Result result = build(level, clicked, side, layout, precision, player, player.isCreative());
		if (result.throwsFound() == 0) {
			tell(player, CELang.translate("tool.bank_builder.no_throws")
				.component(), ChatFormatting.RED);
			return;
		}
		tell(player, CELang.translate("tool.bank_builder.built", result.cylinders(), result.heads(), result.throwsFound())
			.component(), result.missing() > 0 || result.blocked() > 0 ? ChatFormatting.GOLD : ChatFormatting.GREEN);
		if (result.upgraded() > 0)
			player.displayClientMessage(CELang.translate("tool.bank_builder.upgraded", result.upgraded())
				.component()
				.copy()
				.withStyle(ChatFormatting.GRAY), false);
		if (result.camBlocks() > 0) {
			player.displayClientMessage(CELang.translate("tool.bank_builder.cams", result.camBlocks())
				.component()
				.copy()
				.withStyle(ChatFormatting.GRAY), false);
			if (player instanceof ServerPlayer server)
				CETriggers.EVENT.get()
					.trigger(server, Milestone.CAMS_LAID);
		}
		if (result.blocked() > 0)
			player.displayClientMessage(CELang.translate("tool.bank_builder.blocked", result.blocked())
				.component()
				.copy()
				.withStyle(ChatFormatting.GRAY), false);
		if (result.missing() > 0)
			player.displayClientMessage(CELang.translate("tool.bank_builder.missing", result.missing())
				.component()
				.copy()
				.withStyle(ChatFormatting.GRAY), false);
		if (result.cylinders() + result.heads() + result.upgraded() > 0) {
			level.playSound(null, clicked, SoundEvents.NETHERITE_BLOCK_PLACE, SoundSource.BLOCKS, 0.8f, 1.2f);
			if (player instanceof ServerPlayer server)
				CETriggers.EVENT.get()
					.trigger(server, Milestone.BANK_BUILT);
		}
	}

	/**
	 * Builds the layout on every throw of the crank line through {@code clicked}, taking parts from the player's
	 * inventory unless {@code free}. A cylinder needs a cylinder block and a head needs a head block; the piston, rod
	 * and injector are fitted if the player has them.
	 */
	public static Result build(Level level, BlockPos clicked, Direction side, Layout layout, Player player, boolean free) {
		return build(level, clicked, side, layout, false, player, free);
	}

	/**
	 * As above, with {@code precision} parts: new cylinders and heads are the precision ones, new pistons and injectors
	 * the forged and common-rail ones, and the standard parts already on the throws are swapped for them, as far as the
	 * builder has precision parts to swap in. The standard parts come back to the builder.
	 */
	public static Result build(Level level, BlockPos clicked, Direction side, Layout layout, boolean precision,
							   Player player, boolean free) {
		BlockState state = level.getBlockState(clicked);
		Axis axis = state.getValue(BlockStateProperties.AXIS);
		List<BlockPos> throwPositions = new ArrayList<>();
		int limit = CEConfig.MAX_CRANK_SEGMENTS.get();
		BlockPos start = clicked;
		for (int i = 0; i < limit && isCrank(level.getBlockState(start.relative(axis, -1)), axis); i++)
			start = start.relative(axis, -1);
		BlockPos cursor = start;
		for (int i = 0; i < limit && isCrank(level.getBlockState(cursor), axis); i++) {
			if (((CrankshaftBlock) level.getBlockState(cursor)
				.getBlock()).isThrow())
				throwPositions.add(cursor);
			cursor = cursor.relative(axis, 1);
		}

		Supplies supplies = new Supplies(player, free);
		int cylinders = 0;
		int heads = 0;
		int fitted = 0;
		int blocked = 0;
		int missing = 0;
		int[] counts = new int[2];
		for (BlockPos throwPos : throwPositions) {
			for (Direction bore : faces(layout, axis, side)) {
				BlockPos cylinderPos = throwPos.relative(bore);
				BlockPos headPos = cylinderPos.relative(bore);

				BlockState cylinder = level.getBlockState(cylinderPos);
				if (!(cylinder.getBlock() instanceof CylinderBlock) || cylinder.getValue(CylinderBlock.FACING) != bore) {
					if (!canBuildOn(level, cylinderPos, player)) {
						blocked++;
						continue;
					}
					Block block = precision ? supplies.takeBlock(CEBlocks.PRECISION_CYLINDER.get())
						: supplies.takeBlock(CEBlocks.CYLINDER.get(), CEBlocks.PRECISION_CYLINDER.get());
					if (block == null) {
						missing++;
						continue;
					}
					cylinder = block.defaultBlockState()
						.setValue(CylinderBlock.FACING, bore);
					level.setBlockAndUpdate(cylinderPos, cylinder);
					cylinders++;
				}
				if (precision && cylinder.getBlock() instanceof CylinderBlock existing && !existing.precision()) {
					Block better = supplies.takeBlock(CEBlocks.PRECISION_CYLINDER.get());
					if (better != null) {
						BlockState swapped = better.defaultBlockState();
						for (var property : cylinder.getProperties())
							swapped = copy(cylinder, swapped, property);
						level.setBlockAndUpdate(cylinderPos, swapped);
						supplies.giveBack(new ItemStack(CEBlocks.CYLINDER.get()));
						cylinder = swapped;
						counts[1]++;
					}
				}
				fillCylinder(level, cylinderPos, cylinder, supplies, precision, counts);

				BlockState head = level.getBlockState(headPos);
				if (!(head.getBlock() instanceof CylinderHeadBlock) || head.getValue(CylinderHeadBlock.FACING) != bore) {
					if (!canBuildOn(level, headPos, player)) {
						blocked++;
						continue;
					}
					Block block = precision ? supplies.takeBlock(CEBlocks.PRECISION_CYLINDER_HEAD.get())
						: supplies.takeBlock(CEBlocks.CYLINDER_HEAD.get(), CEBlocks.PRECISION_CYLINDER_HEAD.get());
					if (block == null) {
						missing++;
						continue;
					}
					head = block.defaultBlockState()
						.setValue(CylinderHeadBlock.FACING, bore);
					level.setBlockAndUpdate(headPos, head);
					heads++;
				}
				if (precision && head.getBlock() instanceof CylinderHeadBlock existing && !existing.precision()) {
					Block better = supplies.takeBlock(CEBlocks.PRECISION_CYLINDER_HEAD.get());
					if (better != null) {
						BlockState swapped = better.defaultBlockState();
						for (var property : head.getProperties())
							swapped = copy(head, swapped, property);
						level.setBlockAndUpdate(headPos, swapped);
						supplies.giveBack(new ItemStack(CEBlocks.CYLINDER_HEAD.get()));
						head = swapped;
						counts[1]++;
					}
				}
				if (!head.getValue(CylinderHeadBlock.HAS_INJECTOR)) {
					ItemEntry<Item> taken = precision ? supplies.takeItem(CEItems.PRECISION_INJECTOR, CEItems.FUEL_INJECTOR)
						: supplies.takeItem(CEItems.FUEL_INJECTOR, CEItems.PRECISION_INJECTOR);
					if (taken != null) {
						head = head.setValue(CylinderHeadBlock.HAS_INJECTOR, true)
							.setValue(CylinderHeadBlock.FINE_INJECTOR, taken == CEItems.PRECISION_INJECTOR);
						level.setBlockAndUpdate(headPos, head);
						counts[0]++;
					}
				} else if (precision && !head.getValue(CylinderHeadBlock.FINE_INJECTOR)
					&& supplies.takeItem(CEItems.PRECISION_INJECTOR) != null) {
					level.setBlockAndUpdate(headPos, head.setValue(CylinderHeadBlock.FINE_INJECTOR, true));
					supplies.giveBack(CEItems.FUEL_INJECTOR.asStack());
					counts[1]++;
				}
				// A plug only if the builder is carrying one: a diesel has none
				BlockState plugged = level.getBlockState(headPos);
				if (plugged.getBlock() instanceof CylinderHeadBlock && !plugged.getValue(CylinderHeadBlock.HAS_SPARK_PLUG)
					&& supplies.takeExtra(CEItems.SPARK_PLUG.get())) {
					level.setBlockAndUpdate(headPos, plugged.setValue(CylinderHeadBlock.HAS_SPARK_PLUG, true));
					counts[0]++;
				}
			}
		}
		int[] cams = layCams(level, throwPositions, axis, layout, side, supplies, player);
		return new Result(throwPositions.size(), cylinders, heads, fitted + counts[0], blocked + cams[1],
			missing + cams[2], counts[1], cams[0]);
	}

	/**
	 * Lays the cam line for the bank, if the builder is carrying a cam lobe: a lobe at each throw's offset (see
	 * {@link #camOffsets}), and camshaft blocks in the gaps between them so the line is unbroken, all along the crank's
	 * axis. A cell that already holds a camshaft block is left, and one something else is in is blocked.
	 *
	 * @return the blocks laid at 0, the places blocked at 1 and the blocks the builder lacked at 2
	 */
	private static int[] layCams(Level level, List<BlockPos> throwPositions, Axis axis, Layout layout, Direction side,
								 Supplies supplies, Player player) {
		int[] laid = new int[3];
		if (throwPositions.isEmpty() || !supplies.hasExtra(CEBlocks.CAM_LOBE.get()
			.asItem()))
			return laid;
		for (BlockPos offset : camOffsets(layout, axis, side)) {
			BlockPos first = throwPositions.get(0)
				.offset(offset);
			BlockPos last = throwPositions.get(throwPositions.size() - 1)
				.offset(offset);
			int span = axis.choose(last.getX() - first.getX(), last.getY() - first.getY(), last.getZ() - first.getZ());
			for (int step = 0; step <= span; step++) {
				BlockPos cell = first.relative(axis, step);
				BlockState there = level.getBlockState(cell);
				if (there.getBlock() instanceof CamshaftBlock && there.getValue(BlockStateProperties.AXIS) == axis)
					continue;
				boolean lobe = throwPositions.contains(cell.subtract(offset));
				if (!canBuildOn(level, cell, player)) {
					laid[1]++;
					continue;
				}
				Block block = supplies.takeExtraBlock(lobe ? CEBlocks.CAM_LOBE.get() : CEBlocks.CAMSHAFT.get());
				if (block == null) {
					laid[2]++;
					continue;
				}
				level.setBlockAndUpdate(cell, block.defaultBlockState()
					.setValue(BlockStateProperties.AXIS, axis));
				laid[0]++;
			}
		}
		return laid;
	}

	/** Copies one block state property from {@code from} onto {@code to}, if {@code to} has it. */
	private static <T extends Comparable<T>> BlockState copy(BlockState from, BlockState to,
															 net.minecraft.world.level.block.state.properties.Property<T> property) {
		return to.hasProperty(property) ? to.setValue(property, from.getValue(property)) : to;
	}

	/**
	 * Fits a piston and a rod if the cylinder lacks them and the builder has them, and in a precision build swaps a
	 * standard piston for a forged one. {@code counts} gains the parts fitted at 0 and the parts swapped at 1.
	 */
	private static void fillCylinder(Level level, BlockPos pos, BlockState state, Supplies supplies, boolean precision,
									 int[] counts) {
		boolean changed = false;
		if (!state.getValue(CylinderBlock.HAS_PISTON)) {
			ItemEntry<Item> taken = precision ? supplies.takeItem(CEItems.FORGED_PISTON, CEItems.PISTON)
				: supplies.takeItem(CEItems.PISTON, CEItems.FORGED_PISTON);
			if (taken != null) {
				state = state.setValue(CylinderBlock.HAS_PISTON, true)
					.setValue(CylinderBlock.FORGED_PISTON, taken == CEItems.FORGED_PISTON);
				counts[0]++;
				changed = true;
			}
		} else if (precision && !state.getValue(CylinderBlock.FORGED_PISTON)
			&& supplies.takeItem(CEItems.FORGED_PISTON) != null) {
			state = state.setValue(CylinderBlock.FORGED_PISTON, true);
			supplies.giveBack(CEItems.PISTON.asStack());
			counts[1]++;
			changed = true;
		}
		if (!state.getValue(CylinderBlock.HAS_ROD) && supplies.takeItem(CEItems.CONNECTING_ROD) != null) {
			state = state.setValue(CylinderBlock.HAS_ROD, true);
			counts[0]++;
			changed = true;
		}
		if (changed)
			level.setBlockAndUpdate(pos, state);
	}

	private static boolean isCrank(BlockState state, Axis axis) {
		return state.getBlock() instanceof CrankshaftBlock && state.getValue(BlockStateProperties.AXIS) == axis;
	}

	private static boolean canBuildOn(Level level, BlockPos pos, Player player) {
		return level.isInWorldBounds(pos) && level.getBlockState(pos)
			.canBeReplaced() && level.mayInteract(player, pos);
	}

	private static void tell(Player player, Component message, ChatFormatting colour) {
		player.displayClientMessage(message.copy()
			.withStyle(colour), true);
	}

	/** What the builder can draw on: a creative player has everything, anyone else what is in their pockets. */
	private static final class Supplies {

		private final Player player;
		private final boolean free;

		Supplies(Player player, boolean free) {
			this.player = player;
			this.free = free;
		}

		/** Takes one of the first of {@code items} the player has, and says which; null if they have none. */
		@SafeVarargs
		private @Nullable ItemEntry<Item> takeItem(ItemEntry<Item>... items) {
			for (ItemEntry<Item> item : items) {
				if (free)
					return item;
				if (take(item::isIn))
					return item;
			}
			return null;
		}

		/** Takes one block item of the first of {@code blocks} the player has; null if they have none. */
		private @Nullable Block takeBlock(Block... blocks) {
			for (Block block : blocks) {
				if (free)
					return block;
				if (take(stack -> stack.is(block.asItem())))
					return block;
			}
			return null;
		}

		/**
		 * Whether the player is carrying {@code item}. The extras (spark plugs, cam blocks) are only ever laid by a builder
		 * who has them, whether they are creative or not: a creative player has everything, but does not want plugs in a diesel.
		 */
		private boolean hasExtra(Item item) {
			Inventory inventory = player.getInventory();
			for (int i = 0; i < inventory.getContainerSize(); i++)
				if (inventory.getItem(i)
					.is(item))
					return true;
			return false;
		}

		/** Takes one of {@code item} if the player is carrying one; a creative player's stack is left as it was. */
		private boolean takeExtra(Item item) {
			if (!hasExtra(item))
				return false;
			return free || take(stack -> stack.is(item));
		}

		/** As {@link #takeExtra} for a block's item, answering with the block. */
		private @Nullable Block takeExtraBlock(Block block) {
			return takeExtra(block.asItem()) ? block : null;
		}

		/** Hands a part that was swapped out back to the player; a creative player has no use for it. */
		private void giveBack(ItemStack stack) {
			if (!free)
				player.getInventory()
					.placeItemBackInInventory(stack);
		}

		private boolean take(Predicate<ItemStack> wanted) {
			Inventory inventory = player.getInventory();
			for (int i = 0; i < inventory.getContainerSize(); i++) {
				ItemStack stack = inventory.getItem(i);
				if (!stack.isEmpty() && wanted.test(stack)) {
					stack.shrink(1);
					return true;
				}
			}
			return false;
		}
	}
}
