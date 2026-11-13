package com.createengines.content.display;

import java.util.List;
import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import com.createengines.advancement.Achievements;
import com.createengines.advancement.Milestone;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.engine.EngineState;
import com.createengines.engine.EngineSummary;
import com.createengines.engine.sim.Thermal;
import com.createengines.registry.CELang;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;

import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * The figures a Display Link can read off an engine, worked out the same way for every source so a nixie tube, a display
 * board and a progress bar agree. Pure arithmetic on an {@link EngineSummary}, apart from finding the summary.
 */
public final class EngineReadout {

	/** How often, in ticks, a Display Link looks again: a gauge that lags by five seconds is no gauge. */
	public static final int REFRESH_TICKS = 20;

	private EngineReadout() {
	}

	/** The engine whose crankshaft block the link is set against, or null if it is not touching a built engine. */
	public static @Nullable EngineSummary summary(DisplayLinkContext context) {
		BlockEntity source = context.getSourceBlockEntity();
		return source instanceof CrankshaftBlockEntity crank ? crank.engineSummary() : null;
	}

	/** Share of what the engine can give that the network is asking for: 1 is flat out, above 1 is overloaded. */
	public static float load(EngineSummary engine) {
		if (engine.availableTorque() <= 0)
			return engine.loadTorque() > 0 ? 2f : 0f;
		return Math.clamp(engine.loadTorque() / engine.availableTorque(), 0f, 2f);
	}

	/** The block's heat against a warm engine's: 1 is up to temperature, under 0.43 is cold, over 1 is running hot. */
	public static float warmth(EngineSummary engine) {
		return Math.clamp(engine.temperature() / (float) Thermal.NORMAL, 0f, 1.5f);
	}

	/** How fresh the oil is: 1 new, 0 spent. */
	public static float oil(EngineSummary engine) {
		return Math.clamp(engine.condition(), 0f, 1f);
	}

	/** The lines of the Engine Status source: state, speed, load, temperature and oil. */
	public static List<MutableComponent> status(@Nullable EngineSummary engine) {
		if (engine == null || engine.state() == EngineState.INCOMPLETE || engine.state() == EngineState.BROKEN)
			return List.of(CELang.translate("display.engine.none")
				.component());
		return List.of(CELang.translate("display.engine.state." + engine.state()
			.name()
			.toLowerCase(Locale.ROOT))
			.component(),
			CELang.translate("display.engine.speed", Math.round(engine.rpm()))
				.component(),
			CELang.translate("display.engine.load", percent(load(engine)))
				.component(),
			CELang.translate("display.engine.temperature", percent(warmth(engine)))
				.component(),
			CELang.translate("display.engine.oil", percent(oil(engine)))
				.component());
	}

	public static int percent(float share) {
		return Math.round(share * 100);
	}

	/** Tells the engine's operator a link is showing their running engine, once it is. */
	public static void noteDisplayed(DisplayLinkContext context, @Nullable EngineSummary engine) {
		if (engine != null && engine.state() == EngineState.RUNNING)
			Achievements.award(context.level(), List.of(context.getSourcePos()), Milestone.ENGINE_DISPLAYED);
	}
}
