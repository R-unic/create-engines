package com.createengines.content.display;

import java.util.List;

import com.createengines.content.sump.SumpBlockEntity;
import com.createengines.registry.CELang;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;

import net.minecraft.network.chat.MutableComponent;

/** A Display Link set against an oil sump: what is in each of its four tanks, in millibuckets, one tank to a line. */
public class SumpDisplaySource extends DisplaySource {

	@Override
	public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
		if (!(context.getSourceBlockEntity() instanceof SumpBlockEntity sump))
			return List.of();
		return List.of(
			CELang.translate("display.sump.oil", sump.tank()
				.getFluidAmount())
				.component(),
			CELang.translate("display.sump.used", sump.usedTank()
				.getFluidAmount())
				.component(),
			CELang.translate("display.sump.coolant", sump.coolantTank()
				.getFluidAmount())
				.component(),
			CELang.translate("display.sump.pilot", sump.pilotTank()
				.getFluidAmount())
				.component());
	}

	@Override
	public int getPassiveRefreshTicks() {
		return EngineReadout.REFRESH_TICKS;
	}
}
