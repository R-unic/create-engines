package com.createengines.content.display;

import com.createengines.engine.EngineSummary;
import com.createengines.registry.CELang;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.source.NumericSingleLineDisplaySource;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;

import net.minecraft.network.chat.MutableComponent;

/** The engine's rated torque at its present speed, in SU per RPM (the figure the goggles call rated torque), as one line. */
public class EngineTorqueDisplaySource extends NumericSingleLineDisplaySource {

	@Override
	protected MutableComponent provideLine(DisplayLinkContext context, DisplayTargetStats stats) {
		EngineSummary engine = EngineReadout.summary(context);
		EngineReadout.noteDisplayed(context, engine);
		return CELang.translate("display.engine.torque", EngineReadout.torque(engine == null ? 0f : engine.availableTorque()))
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
