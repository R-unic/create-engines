package com.createengines.content;

import java.util.List;

import com.createengines.registry.CELang;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * The creative Service Kit: uncraftable, never runs out, and mends any wear and any failure back to new (see {@link CreativeService}).
 * Sneaking with it on a head still takes the gasket out, as with the ordinary kit.
 */
public class CreativeServiceKitItem extends ServiceKitItem {

	public CreativeServiceKitItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack kit = player.getItemInHand(hand);
		if (!level.isClientSide && CreativeService.mendOtherHand(player, hand))
			return InteractionResultHolder.success(kit);
		return InteractionResultHolder.pass(kit);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		tooltip.add(CELang.translate("tooltip.creative_service_kit")
			.component()
			.copy()
			.withStyle(ChatFormatting.GRAY));
	}
}
