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

	/**
	 * Stress capacity, in SU, that makes one horsepower. Create has no unit of power, so this is calibrated to the game:
	 * a basic naturally aspirated gasoline inline four (crude parts, rated about 445 SU per RPM) offers about 114,000 SU
	 * at Create's default 256 RPM limit, and that engine reads 100 hp.
	 */
	public static final float SU_PER_HP = 1140f;
	/** Horsepower in one SU of stress capacity. */
	public static final float HP_PER_SU_RPM = 1f / SU_PER_HP;
	/** The horsepower a Display Link has to show for the Stampede advancement: five of the basic fours' worth. */
	public static final float STAMPEDE_HP = 500f;

	/** Horsepower of a stress capacity (SU per RPM) turning at a speed. */
	public static float horsepower(float suPerRpm, float rpm) {
		return Math.max(0f, suPerRpm * rpm * HP_PER_SU_RPM);
	}

	/** Horsepower of a power in SU (stress capacity times RPM already multiplied out). */
	public static float horsepower(float su) {
		return Math.max(0f, su * HP_PER_SU_RPM);
	}

	/** What the engine is delivering to the network now, in horsepower: what is asked for, up to what it can give. */
	public static float output(EngineSummary engine) {
		return horsepower(Math.min(engine.loadTorque(), engine.availableTorque()), engine.rpm());
	}

	/** What the engine could deliver at its present speed, in horsepower. */
	public static float capacity(EngineSummary engine) {
		return horsepower(engine.availableTorque(), engine.rpm());
	}

	/** A horsepower figure as shown: whole numbers from 100 up, one decimal below. */
	public static String hp(float horsepower) {
		return horsepower >= 100 ? String.format(Locale.ROOT, "%.0f", horsepower) : String.format(Locale.ROOT, "%.1f", horsepower);
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
			CELang.translate("display.engine.power", hp(output(engine)))
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

	/** Tells the engine's operator a link is showing a hundred horsepower or more. */
	public static void noteHorsepower(DisplayLinkContext context, @Nullable EngineSummary engine) {
		if (engine != null && engine.state() == EngineState.RUNNING && output(engine) >= STAMPEDE_HP)
			Achievements.award(context.level(), List.of(context.getSourcePos()), Milestone.STAMPEDE);
	}

	/** Tells the engine's operator a link is showing their running engine, once it is. */
	public static void noteDisplayed(DisplayLinkContext context, @Nullable EngineSummary engine) {
		if (engine != null && engine.state() == EngineState.RUNNING)
			Achievements.award(context.level(), List.of(context.getSourcePos()), Milestone.ENGINE_DISPLAYED);
	}
}
