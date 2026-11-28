package com.createengines.content;

import com.createengines.content.battery.BatteryBlockEntity;
import com.createengines.content.cylinder.CylinderBlockEntity;
import com.createengines.content.head.CylinderHeadBlockEntity;
import com.createengines.content.rotor.RotorHousingBlockEntity;
import com.createengines.content.turbo.TurbochargerBlockEntity;
import com.createengines.engine.LoadedBlocks;
import com.createengines.registry.CEComponents;
import com.createengines.registry.CEItems;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * What the creative Service Kit does: whatever wears is put back to new, failures and all, where it is. It brings back no part that is
 * gone (the piston and rod a thrown rod destroyed still have to be fitted).
 */
public final class CreativeService {

	private CreativeService() {
	}

	/** Whether a stack is the creative kit. */
	public static boolean isKit(ItemStack stack) {
		return CEItems.CREATIVE_SERVICE_KIT.isIn(stack);
	}

	/** The kit used on a block that wears. */
	public static ItemInteractionResult onBlock(Level level, BlockPos pos, Player player, InteractionHand hand) {
		if (level.isClientSide)
			return ItemInteractionResult.sidedSuccess(true);
		if (mend(LoadedBlocks.blockEntity(level, pos))) {
			player.displayClientMessage(Component.translatable("create_engines.message.wear.serviced"), true);
			level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.4f, 1.6f);
		} else if (!mendOtherHand(player, hand))
			player.displayClientMessage(Component.translatable("create_engines.message.wear.nothing_to_service"), true);
		return ItemInteractionResult.sidedSuccess(false);
	}

	/** Puts everything a block holds back to new. Returns whether anything changed. */
	private static boolean mend(BlockEntity entity) {
		if (entity instanceof CylinderBlockEntity body) {
			boolean changed = body.thrown() || body.seized() || body.pistonWear() > 0 || body.rodWear() > 0;
			// A thrown rod's wreck is what service() clears, and it clears nothing else while there is one
			if (body.thrown())
				body.service();
			body.unseize();
			body.resetPiston();
			body.fitRod(0);
			return changed;
		}
		if (entity instanceof RotorHousingBlockEntity housing) {
			boolean changed = housing.apexFailed() || housing.apexWear() > 0 || housing.plugWear() > 0;
			housing.fitSeals();
			housing.fitPlug(0);
			return changed;
		}
		if (entity instanceof CylinderHeadBlockEntity head)
			return head.restore();
		if (entity instanceof TurbochargerBlockEntity turbo)
			return turbo.restore();
		return entity instanceof BatteryBlockEntity battery && battery.restore();
	}

	/** Mends the stack in the other hand: all of it, since the kit costs nothing. Returns whether there was anything to mend. */
	public static boolean mendOtherHand(Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
		if (held.isEmpty() || WearService.isNew(held))
			return false;
		mend(held);
		player.displayClientMessage(Component.translatable("create_engines.message.wear.serviced"), true);
		player.level()
			.playSound(null, player.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.4f, 1.6f);
		return true;
	}

	/** Takes the wear and any failure off a stack. Returns it. */
	static ItemStack mend(ItemStack stack) {
		stack.remove(CEComponents.WEAR.get());
		stack.remove(CEComponents.FAILED.get());
		return stack;
	}
}
