package com.createengines.content;

import com.createengines.content.battery.BatteryBlockEntity;
import com.createengines.content.rotor.RotorHousingBlockEntity;
import com.createengines.content.cylinder.CylinderBlock;
import com.createengines.content.cylinder.CylinderBlockEntity;
import com.createengines.content.head.CylinderHeadBlockEntity;
import com.createengines.engine.LoadedBlocks;
import com.createengines.engine.sim.Wear;
import com.createengines.registry.CEComponents;
import com.createengines.registry.CEItems;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What a player does about wear with an item: fit a gasket on a head (which replaces whatever gasket was there, a blown one too) and
 * set right light wear with a Service Kit. Heavy wear and failures are cured by replacing the part, which is the ordinary way a piston,
 * rod or gasket goes in. Nothing here needs the wear mode on; with it off the parts never wear, and saved failures do not count.
 */
public final class WearService {

	private WearService() {
	}

	/** How worn the part in a stack is, 0 for a new one (a stack with no wear data, which is every part from before the wear mode). */
	public static double wearOf(ItemStack stack) {
		Double wear = stack.get(CEComponents.WEAR.get());
		return wear == null ? 0 : Wear.clamp(wear);
	}

	/** Whether a stack is as new: no wear on it, and not a block broken while failed. Anything that does not wear is. */
	public static boolean isNew(ItemStack stack) {
		return wearOf(stack) <= 0 && !Boolean.TRUE.equals(stack.get(CEComponents.FAILED.get()));
	}

	/** Puts wear on a part; none at all leaves the stack with no wear data, so it stacks with every other new part. Returns the stack. */
	public static ItemStack withWear(ItemStack stack, double wear) {
		double w = Wear.clamp(wear);
		if (w > 0)
			stack.set(CEComponents.WEAR.get(), w);
		else
			stack.remove(CEComponents.WEAR.get());
		return stack;
	}

	/** The gasket a stack is, or null if it is not one. */
	public static Wear.Gasket gasketOf(ItemStack stack) {
		if (CEItems.STOCK_GASKET.isIn(stack))
			return Wear.Gasket.STOCK;
		if (CEItems.CARDBOARD_GASKET.isIn(stack))
			return Wear.Gasket.CARDBOARD;
		if (CEItems.RUBBER_GASKET.isIn(stack))
			return Wear.Gasket.RUBBER;
		if (CEItems.COPPER_GASKET.isIn(stack))
			return Wear.Gasket.COPPER;
		return null;
	}

	/** The item that is a gasket of this tier. */
	public static ItemStack itemFor(Wear.Gasket tier) {
		return switch (tier) {
			case CARDBOARD -> CEItems.CARDBOARD_GASKET.asStack();
			case RUBBER -> CEItems.RUBBER_GASKET.asStack();
			case COPPER -> CEItems.COPPER_GASKET.asStack();
			case STOCK -> CEItems.STOCK_GASKET.asStack();
		};
	}

	/** A spark plug went into or came out of a head or a rotor housing: the new one starts with the wear {@code carried} on its stack. */
	public static void plugChanged(Level level, BlockPos pos, boolean had, boolean has, double carried) {
		if (level.isClientSide || had == has)
			return;
		if (LoadedBlocks.blockEntity(level, pos) instanceof CylinderHeadBlockEntity head)
			head.fitPlug(has ? carried : 0);
		else if (LoadedBlocks.blockEntity(level, pos) instanceof RotorHousingBlockEntity housing)
			housing.fitPlug(has ? carried : 0);
	}

	/** A part coming out of a head or a rotor housing: the spark plug leaves with its wear, and anything else as it is. */
	public static ItemStack dressPlug(Level level, BlockPos pos, boolean plug, ItemStack part) {
		if (!plug)
			return part;
		if (LoadedBlocks.blockEntity(level, pos) instanceof CylinderHeadBlockEntity head)
			return withWear(part, head.plugWear());
		return LoadedBlocks.blockEntity(level, pos) instanceof RotorHousingBlockEntity housing ? withWear(part, housing.plugWear()) : part;
	}

	/** The connecting rod a stack is, or null if it is not one. */
	public static Wear.Rod rodOf(ItemStack stack) {
		if (CEItems.CONNECTING_ROD.isIn(stack))
			return Wear.Rod.STANDARD;
		if (CEItems.LIGHT_CONNECTING_ROD.isIn(stack))
			return Wear.Rod.LIGHT;
		return CEItems.HEAVY_CONNECTING_ROD.isIn(stack) ? Wear.Rod.HEAVY : null;
	}

	/** The item that is a connecting rod of this kind. */
	public static ItemStack itemFor(Wear.Rod kind) {
		return switch (kind) {
			case STANDARD -> CEItems.CONNECTING_ROD.asStack();
			case LIGHT -> CEItems.LIGHT_CONNECTING_ROD.asStack();
			case HEAVY -> CEItems.HEAVY_CONNECTING_ROD.asStack();
		};
	}

	/** An item used on a cylinder head: a gasket goes in, a Service Kit sets right light wear. */
	public static ItemInteractionResult onHead(ItemStack stack, Level level, BlockPos pos, Player player, InteractionHand hand) {
		Wear.Gasket tier = gasketOf(stack);
		boolean kit = CEItems.SERVICE_KIT.isIn(stack);
		if (tier == null && !kit)
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		if (!level.isClientSide && LoadedBlocks.blockEntity(level, pos) instanceof CylinderHeadBlockEntity head) {
			if (head.showsBroken()) {
				// A head a rod went through cannot be mended or fitted: it is replaced
				player.displayClientMessage(Component.translatable("create_engines.message.wear.head_broken"), true);
				return ItemInteractionResult.sidedSuccess(false);
			}
				if (tier != null) {
				double carried = wearOf(stack);
				// A gasket that is no better than the one in is not worth swapping for it
				if (tier == head.gasket() && !head.showsBlown() && head.gasketWear() <= carried)
					return ItemInteractionResult.sidedSuccess(false);
				// The old one comes out with the wear it had (a blown one as spent), and is the player's to repair or scrap
				ItemStack old = head.fit(tier, carried);
				if (!old.isEmpty() && !player.isCreative())
					player.getInventory()
						.placeItemBackInInventory(old);
				if (!player.isCreative())
					stack.shrink(1);
				done(level, pos, player, "message.wear.gasket_fitted", tier.name().charAt(0) + tier.name().substring(1).toLowerCase(java.util.Locale.ROOT));
			} else if (player.isShiftKeyDown()) {
				// Sneaking with a kit takes the fitted gasket out, with its wear, to be repaired or replaced; it costs no use of the kit
				ItemStack out = head.removeGasket();
				if (out.isEmpty())
					player.displayClientMessage(Component.translatable("create_engines.message.wear.no_gasket_to_take"), true);
				else {
					if (!player.isCreative())
						player.getInventory()
							.placeItemBackInInventory(out);
					done(level, pos, player, "message.wear.gasket_removed");
				}
			} else
				service(stack, level, pos, player, hand, head.service());
		}
		return ItemInteractionResult.sidedSuccess(level.isClientSide);
	}

	/** A Service Kit used on a cylinder: it pulls a seized piston out, or else sets right the light wear of the piston and rod fitted. */
	public static ItemInteractionResult onCylinder(ItemStack stack, Level level, BlockPos pos, Player player, InteractionHand hand) {
		boolean part = CylinderBlock.isPiston(stack) || CEItems.PISTON_RING.isIn(stack) || rodOf(stack) != null;
		if (!CEItems.SERVICE_KIT.isIn(stack) && !part)
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		if (!level.isClientSide && LoadedBlocks.blockEntity(level, pos) instanceof CylinderBlockEntity body) {
			if (part) {
				// The wreck of a thrown rod is still in: no new part goes in until a Service Kit has cleared it
				if (!body.showsThrown())
					return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
				player.displayClientMessage(Component.translatable("create_engines.message.wear.clear_the_wreck"), true);
				return ItemInteractionResult.sidedSuccess(false);
			}
			if (body.showsSeized() && body.getBlockState()
				.getValue(CylinderBlock.HAS_PISTON))
				pullSeizedPiston(stack, level, pos, player, hand, body);
			else
				service(stack, level, pos, player, hand, body.service());
		} else if (part)
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		return ItemInteractionResult.sidedSuccess(level.isClientSide);
	}

	/**
	 * A seized piston cannot be mended where it is: a Service Kit knocks it out of the bore for one use, and it is the player's, as worn as
	 * the seizure left it. The connecting rod stays in, and the seizure and the piston's wear leave the cylinder with the piston.
	 */
	private static void pullSeizedPiston(ItemStack kit, Level level, BlockPos pos, Player player, InteractionHand hand, CylinderBlockEntity body) {
		BlockState before = body.getBlockState();
		ItemStack piston = withWear(CylinderBlock.pistonStack(before), body.pistonWear());
		int rings = before.getValue(CylinderBlock.RINGS);
		BlockState after = CylinderBlock.withoutPiston(before);
		level.setBlockAndUpdate(pos, after);
		partsChanged(level, pos, before, after);
		if (!player.isCreative()) {
			// The kit first: a Deployer's hand is its inventory too, and the piston must not land in the slot of a kit about to break
			kit.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
			player.getInventory()
				.placeItemBackInInventory(piston);
			// The rings come out on the piston, and nothing wears them
			if (rings > 0)
				player.getInventory()
					.placeItemBackInInventory(new ItemStack(CEItems.PISTON_RING.get(), rings));
		}
		done(level, pos, player, "message.wear.seized_piston_out");
	}

	/** A Service Kit used on a battery: it sets right light wear (the battery's capacity comes back to what a lightly worn one held). */
	public static ItemInteractionResult onBattery(ItemStack stack, Level level, BlockPos pos, Player player, InteractionHand hand) {
		if (!CEItems.SERVICE_KIT.isIn(stack))
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		if (!level.isClientSide && LoadedBlocks.blockEntity(level, pos) instanceof BatteryBlockEntity battery)
			service(stack, level, pos, player, hand, battery.service());
		return ItemInteractionResult.sidedSuccess(level.isClientSide);
	}

	/**
	 * An item used on a rotor housing: a set of apex seals goes in (and replaces whatever set was there, a failed one too, which is scrap),
	 * and a Service Kit sets right light wear. Anything else is the housing's ordinary parts, which are not this class's.
	 */
	/** A Service Kit used on a turbocharger: it sets right light wear. */
	public static ItemInteractionResult onTurbo(ItemStack stack, Level level, BlockPos pos, Player player, InteractionHand hand) {
		if (!CEItems.SERVICE_KIT.isIn(stack))
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		if (!level.isClientSide && LoadedBlocks.blockEntity(level, pos) instanceof com.createengines.content.turbo.TurbochargerBlockEntity turbo)
			service(stack, level, pos, player, hand, turbo.service());
		return ItemInteractionResult.sidedSuccess(level.isClientSide);
	}

	/** An item used on a rotor housing: seals or a Service Kit. */
	public static ItemInteractionResult onRotor(ItemStack stack, Level level, BlockPos pos, Player player, InteractionHand hand) {
		boolean seals = CEItems.APEX_SEALS.isIn(stack);
		boolean kit = CEItems.SERVICE_KIT.isIn(stack);
		if (!seals && !kit)
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		if (!level.isClientSide && LoadedBlocks.blockEntity(level, pos) instanceof RotorHousingBlockEntity housing) {
			if (seals) {
				// A set that is as good as new is not worth throwing away for another
				if (!housing.fitSeals())
					return ItemInteractionResult.sidedSuccess(false);
				if (!player.isCreative())
					stack.shrink(1);
				done(level, pos, player, "message.wear.seals_fitted");
			} else
				service(stack, level, pos, player, hand, housing.service());
		}
		return ItemInteractionResult.sidedSuccess(level.isClientSide);
	}

	/** How many uses a Service Kit has. */
	public static final int KIT_USES = 24;

	/**
	 * A Service Kit with a worn part in the other hand sets that part right (light wear only, as on an engine): one part of the stack, for one use of
	 * the kit. Returns whether it did.
	 */
	public static boolean repairOtherHand(ItemStack kit, Player player, InteractionHand hand) {
		InteractionHand other = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
		ItemStack held = player.getItemInHand(other);
		if (held.isEmpty() || !(held.getItem() instanceof WornPartItem) || !Wear.repairable(wearOf(held)))
			return false;
		ItemStack one = held.copyWithCount(1);
		withWear(one, Wear.repaired(wearOf(one)));
		held.shrink(1);
		if (held.isEmpty())
			player.setItemInHand(other, one);
		else
			player.getInventory()
				.placeItemBackInInventory(one);
		if (!player.isCreative())
			kit.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
		player.displayClientMessage(Component.translatable("create_engines.message.wear.serviced"), true);
		player.level()
			.playSound(null, player.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.4f, 1.6f);
		return true;
	}

	private static void service(ItemStack stack, Level level, BlockPos pos, Player player, InteractionHand hand, boolean changed) {
		// Nothing on the block to set right: a worn part in the other hand will do
		if (!changed && repairOtherHand(stack, player, hand))
			return;
		if (!changed) {
			player.displayClientMessage(Component.translatable("create_engines.message.wear.nothing_to_service"), true);
			return;
		}
		// The kit wears with use and breaks on its last (a deployer's fake player is a player too, so a factory spends it the same way)
		if (!player.isCreative())
			stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
		done(level, pos, player, "message.wear.serviced");
	}

	private static void done(Level level, BlockPos pos, Player player, String key, Object... args) {
		player.displayClientMessage(Component.translatable("create_engines." + key, args), true);
		level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.4f, 1.6f);
	}

	/** Called after a piston or rod goes in or out of a cylinder: the wear of the old part does not carry over to a new one. */
	public static void partsChanged(Level level, BlockPos pos, BlockState before, BlockState after) {
		partsChanged(level, pos, before, after, 0);
	}

	/**
	 * As above, for a part that went in carrying {@code carried} wear (a worn piston or rod fitted keeps the wear it had; a part that came out
	 * has already left with its wear on the item, so the block starts clean).
	 */
	public static void partsChanged(Level level, BlockPos pos, BlockState before, BlockState after, double carried) {
		if (level.isClientSide || !(LoadedBlocks.blockEntity(level, pos) instanceof CylinderBlockEntity body))
			return;
		boolean piston = before.getValue(CylinderBlock.HAS_PISTON) != after.getValue(CylinderBlock.HAS_PISTON);
		boolean rod = before.getValue(CylinderBlock.HAS_ROD) != after.getValue(CylinderBlock.HAS_ROD);
		if (piston) {
			body.resetPiston();
			// A seized piston that comes out takes the seizure with it
			body.unseize();
			if (after.getValue(CylinderBlock.HAS_PISTON)) {
				body.dropHiddenWreck();
				body.fitPiston(carried);
			}
		}
		if (rod) {
			body.resetRod();
			if (after.getValue(CylinderBlock.HAS_ROD)) {
				body.dropHiddenWreck();
				body.fitRod(carried);
			}
		}
	}

	/** A part that comes out of a cylinder leaves with the wear the cylinder holds for it. */
	public static ItemStack dress(Level level, BlockPos pos, PartInstallation.Slot slot, ItemStack part) {
		if (!(LoadedBlocks.blockEntity(level, pos) instanceof CylinderBlockEntity body))
			return part;
		if (slot.property() == CylinderBlock.HAS_PISTON)
			return withWear(part, body.pistonWear());
		if (slot.property() == CylinderBlock.HAS_ROD)
			return withWear(itemFor(body.rodKind()), body.rodWear());
		return part;
	}
}
