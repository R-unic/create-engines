package com.createengines.content.display;

import com.createengines.engine.EngineSummary;
import com.createengines.registry.CELang;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.source.NumericSingleLineDisplaySource;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;

import net.minecraft.network.chat.MutableComponent;

/** The pressure the engine's turbochargers and superchargers are putting on its intake, in bar over atmosphere, as one line. */
public class EngineBoostDisplaySource extends NumericSingleLineDisplaySource {

	@Override
	protected MutableComponent provideLine(DisplayLinkContext context, DisplayTargetStats stats) {
		EngineSummary engine = EngineReadout.summary(context);
		EngineReadout.noteDisplayed(context, engine);
		return CELang.translate("display.engine.boost", EngineReadout.bar(engine == null ? 0f : engine.boost()))
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
