package com.createengines.content;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The wear mode's Service Kit. Used on a cylinder or a head it sets right light wear in place (see {@link WearService}); used in the air
 * with a worn part in the other hand it sets that part right. It has durability, so it breaks after a number of uses.
 */
public class ServiceKitItem extends Item {

	public ServiceKitItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack kit = player.getItemInHand(hand);
		if (!level.isClientSide && WearService.repairOtherHand(kit, player, hand))
			return InteractionResultHolder.success(kit);
		return InteractionResultHolder.pass(kit);
	}
}
