package com.createengines.content.display;

import java.util.List;

import com.createengines.engine.EngineSummary;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;

import net.minecraft.network.chat.MutableComponent;

/**
 * A Display Link set against any crankshaft block of an engine: one line each for what the engine is doing, its speed,
 * its load, its temperature and its oil, for a display board. The other engine sources give a single figure for a nixie
 * tube or a progress bar.
 */
public class EngineStatusDisplaySource extends DisplaySource {

	@Override
	public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
		EngineSummary engine = EngineReadout.summary(context);
		EngineReadout.noteDisplayed(context, engine);
		return EngineReadout.status(engine);
	}

	@Override
	public int getPassiveRefreshTicks() {
		return EngineReadout.REFRESH_TICKS;
	}
}
