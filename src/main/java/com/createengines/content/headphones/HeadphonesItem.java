package com.createengines.content.headphones;

import java.util.List;
import java.util.function.BooleanSupplier;

import com.createengines.registry.CELang;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Noise-cancelling headphones: worn on the head like Create's goggles (and in a Curios head slot when Curios is installed),
 * they muffle every sound from the world for the wearer, never silence it. The muffling is the client's own business
 * ({@code HeadphoneMuffle}); the item only has to be wearable and say what it does.
 */
public class HeadphonesItem extends Item implements Equipable {

	/**
	 * Set by the client: whether the sound card can filter sounds. Without it the headphones only cap the loudness of
	 * world sounds, and the tooltip says so. A server, which has no sound card, never asks.
	 */
	public static BooleanSupplier filterAvailable = () -> true;

	public HeadphonesItem(Properties properties) {
		super(properties);
	}

	@Override
	public EquipmentSlot getEquipmentSlot() {
		return EquipmentSlot.HEAD;
	}

	@Override
	public Holder<SoundEvent> getEquipSound() {
		return SoundEvents.ARMOR_EQUIP_LEATHER;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		return swapWithEquipmentSlot(this, level, player, hand);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(CELang.translate("tooltip.headphones.muffles")
			.component()
			.copy()
			.withStyle(ChatFormatting.GRAY));
		tooltip.add(CELang.translate("tooltip.headphones.exempt")
			.component()
			.copy()
			.withStyle(ChatFormatting.DARK_GRAY));
		tooltip.add(CELang.translate("tooltip.headphones.worn")
			.component()
			.copy()
			.withStyle(ChatFormatting.DARK_GRAY));
		if (!filterAvailable.getAsBoolean())
			tooltip.add(CELang.translate("tooltip.headphones.no_filter")
				.component()
				.copy()
				.withStyle(ChatFormatting.GOLD));
	}
}
