package com.createengines.content.headphones;

import com.createengines.compat.curios.CuriosCompat;
import com.createengines.registry.CEItems;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

/** Whether somebody is wearing the headphones: in the head slot, or in a Curios slot when Curios is installed. */
public final class HeadphoneWear {

	private HeadphoneWear() {
	}

	public static boolean isWorn(LivingEntity wearer) {
		if (wearer.getItemBySlot(EquipmentSlot.HEAD)
			.is(CEItems.HEADPHONES.get()))
			return true;
		return CuriosCompat.installed() && CuriosCompat.wears(wearer, CEItems.HEADPHONES.get());
	}
}
