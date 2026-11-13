package com.createengines.content.display;

import com.createengines.engine.EngineSummary;
import com.createengines.registry.CELang;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.source.NumericSingleLineDisplaySource;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;

import net.minecraft.network.chat.MutableComponent;

/** The engine's speed in RPM as one line, for a nixie tube or a flap display. */
public class EngineSpeedDisplaySource extends NumericSingleLineDisplaySource {

	@Override
	protected MutableComponent provideLine(DisplayLinkContext context, DisplayTargetStats stats) {
		EngineSummary engine = EngineReadout.summary(context);
		EngineReadout.noteDisplayed(context, engine);
		return CELang.translate("display.engine.speed", engine == null ? 0 : Math.round(engine.rpm()))
			.component();
	}

	@Override
	protected boolean allowsLabeling(DisplayLinkContext context) {
		return true;
	}

	@Override
	public int getPassiveRefreshTicks() {
		return EngineReadout.REFRESH_TICKS;
	}
}
