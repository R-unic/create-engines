package com.createengines.content;

import java.util.List;

import com.createengines.engine.sim.Tuning;
import com.createengines.registry.CELang;

import net.minecraft.ChatFormatting;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * A part that wears (a piston, a connecting rod, a head gasket). Its wear travels on the stack as a data component, so a part taken out
 * of an engine comes out as worn as it went in, can be repaired with a Service Kit, and goes back in as worn as it was. A stack with no
 * wear data is a new part, so every part that existed before the wear mode is still valid. Worn and new parts do not stack together.
 */
public class WornPartItem extends Item {

	public WornPartItem(Properties properties) {
		super(properties);
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return Tuning.wearMode && WearService.wearOf(stack) > 0;
	}

	/** The bar is the life that is left: full for a new part, empty for a spent one. */
	@Override
	public int getBarWidth(ItemStack stack) {
		return Math.round(13 * (1 - (float) WearService.wearOf(stack)));
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return Mth.hsvToRgb(Math.max(0, 1 - (float) WearService.wearOf(stack)) / 3f, 1, 1);
	}

	/** A share as a percent, with a decimal only where it has one: 6, 4, 2.5. */
	private static String percent(double share) {
		double percent = Math.round(share * 1000) / 10.0;
		return percent == Math.rint(percent) ? Long.toString(Math.round(percent)) : Double.toString(percent);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<net.minecraft.network.chat.Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		var gasket = WearService.gasketOf(stack);
		if (gasket != null)
			tooltip.add(CELang.translate("tooltip.gasket.seals", percent(gasket.bonus))
				.component()
				.copy()
				.withStyle(ChatFormatting.AQUA));
		double wear = WearService.wearOf(stack);
		if (Tuning.wearMode && wear > 0)
			tooltip.add(CELang.translate("tooltip.wear", Math.round(wear * 100))
				.component()
				.copy()
				.withStyle(wear >= com.createengines.engine.sim.Wear.FAILURE_FROM ? ChatFormatting.RED
					: wear >= com.createengines.engine.sim.Wear.REPAIRABLE ? ChatFormatting.GOLD : ChatFormatting.GRAY));
	}
}
