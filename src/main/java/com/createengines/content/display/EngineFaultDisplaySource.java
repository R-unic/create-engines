package com.createengines.content.display;

import com.createengines.engine.Diagnosis;
import com.createengines.engine.EngineState;
import com.createengines.engine.EngineSummary;
import com.createengines.registry.CELang;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.source.SingleLineDisplaySource;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;

import net.minecraft.network.chat.MutableComponent;

/**
 * The one thing most responsible for an engine running badly, as a short phrase on one line ("No spark", "Dry oil"), or
 * "No fault" when it is healthy: the figure the goggles' health line works out, for an alarm board. It reads the same
 * {@link Diagnosis} cause the goggles do, so the two always agree.
 */
public class EngineFaultDisplaySource extends SingleLineDisplaySource {

	@Override
	protected MutableComponent provideLine(DisplayLinkContext context, DisplayTargetStats stats) {
		EngineSummary engine = EngineReadout.summary(context);
		EngineReadout.noteDisplayed(context, engine);
		return CELang.translate(EngineReadout.faultKey(engine))
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
