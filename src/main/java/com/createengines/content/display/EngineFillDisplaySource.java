package com.createengines.content.display;

import java.util.function.ToDoubleFunction;

import com.createengines.engine.EngineSummary;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.source.PercentOrProgressBarDisplaySource;
import com.simibubi.create.foundation.gui.ModularGuiLineBuilder;
import com.simibubi.create.foundation.utility.CreateLang;

/**
 * One of the engine's figures as a percentage or a progress bar, chosen in the link's settings the way Create's own fill
 * level source is. Load, temperature and oil condition are all this with a different reading.
 */
public class EngineFillDisplaySource extends PercentOrProgressBarDisplaySource {

	private final ToDoubleFunction<EngineSummary> reading;

	public EngineFillDisplaySource(ToDoubleFunction<EngineSummary> reading) {
		this.reading = reading;
	}

	@Override
	protected Float getProgress(DisplayLinkContext context) {
		EngineSummary engine = EngineReadout.summary(context);
		EngineReadout.noteDisplayed(context, engine);
		return engine == null ? 0f : (float) reading.applyAsDouble(engine);
	}

	@Override
	protected boolean progressBarActive(DisplayLinkContext context) {
		return context.sourceConfig()
			.getInt("Mode") == 1;
	}

	@Override
	protected boolean allowsLabeling(DisplayLinkContext context) {
		return true;
	}

	@Override
	public int getPassiveRefreshTicks() {
		return EngineReadout.REFRESH_TICKS;
	}

	@Override
	public void initConfigurationWidgets(DisplayLinkContext context, ModularGuiLineBuilder builder, boolean isFirstLine) {
		super.initConfigurationWidgets(context, builder, isFirstLine);
		if (isFirstLine)
			return;
		builder.addSelectionScrollInput(0, 120, (input, label) -> input
			.forOptions(CreateLang.translatedOptions("display_source.fill_level", "percent", "progress_bar"))
			.titled(CreateLang.translateDirect("display_source.fill_level.display")), "Mode");
	}
}
