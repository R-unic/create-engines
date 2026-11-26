package com.createengines.content.display;

import com.createengines.engine.EngineSummary;
import com.createengines.engine.sim.Thermal;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * The block's temperature: as a number, degrees Celsius; as a progress bar, how warm it is against a warm engine, full from there up,
 * as the bar always was.
 */
public class EngineTemperatureDisplaySource extends EngineFillDisplaySource {

	@Override
	protected Float getProgress(DisplayLinkContext context) {
		EngineSummary engine = EngineReadout.summary(context);
		EngineReadout.noteDisplayed(context, engine);
		if (engine == null)
			return 0f;
		return progressBarActive(context) ? EngineReadout.warmth(engine) : engine.temperature();
	}

	@Override
	protected MutableComponent formatNumeric(DisplayLinkContext context, Float temperature) {
		return Component.literal(Thermal.degrees(temperature) + "\u00b0C");
	}
}
