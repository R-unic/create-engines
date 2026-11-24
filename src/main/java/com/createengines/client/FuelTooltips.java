package com.createengines.client;

import java.util.List;
import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import com.createengines.content.refinery.RefiningRecipe;
import com.createengines.engine.sim.FuelSpec;
import com.createengines.registry.CEDataMaps;
import com.createengines.registry.CELang;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;

/**
 * What a bucket of a fuel (or any other container with one in it) says about it when you hover: how much energy it
 * carries, whether it lights by compression or needs a spark plug, and how it behaves in a cold block. Fuels are a data
 * map, so another mod's diesel or a datapack's fuel is described the same way as ours. Client only.
 */
public final class FuelTooltips {

	/** A fuel that autoignites at or below this is a compression fuel; above it, the engine needs a spark plug. */
	private static final double COMPRESSION_LIMIT = CEDataMaps.PILOT_IGNITION_LIMIT;
	private static final double GOOD_COLD = 0.9;
	private static final double POOR_COLD = 1.2;
	private static final double THIN = 1.05;
	private static final double DIRTY = 1.2;
	private static final double CLEANER = 0.6;
	private static final double VOLATILE_SPILL = 2.0;
	private static final double FLAMMABLE_SPILL = 1.2;
	private static final double SAFE_SPILL = 0.8;

	private FuelTooltips() {
	}

	public static void onTooltip(ItemTooltipEvent event) {
		FluidStack contained = FluidUtil.getFluidContained(event.getItemStack())
			.orElse(FluidStack.EMPTY);
		if (contained.isEmpty())
			return;
		FuelSpec fuel = CEDataMaps.fuel(contained.getFluid());
		if (fuel == null)
			return;
		event.getToolTip()
			.addAll(lines(fuel));
		if (refinedInColumn(contained))
			event.getToolTip()
				.add(grey(CELang.translate("tooltip.fuel.column")
					.component()));
	}

	/** Whether a refining column makes this fluid. Fails soft: no level or an odd recipe set gives no line. */
	private static boolean refinedInColumn(FluidStack fluid) {
		try {
			net.minecraft.world.level.Level level = net.minecraft.client.Minecraft.getInstance().level;
			return level != null && RefiningRecipe.makes(level, fluid);
		} catch (RuntimeException e) {
			return false;
		}
	}

	/** The lines that describe {@code fuel}: a title, its energy, how it is lit, and how it takes a cold start. */
	public static List<Component> lines(FuelSpec fuel) {
		java.util.ArrayList<Component> lines = new java.util.ArrayList<>();
		lines.add(CELang.translate("tooltip.fuel")
			.component()
			.copy()
			.withStyle(ChatFormatting.GOLD));
		lines.add(grey(CELang.translate("tooltip.fuel.energy", number(fuel.energyPerMb()))
			.component()));
		lines.add(grey(CELang.translate(fuel.autoignitionTemp() <= COMPRESSION_LIMIT ? "tooltip.fuel.compression"
			: "tooltip.fuel.spark")
			.component()));
		Component cold = cold(fuel);
		if (cold != null)
			lines.add(grey(cold));
		if (fuel.chargeCooling() > 0)
			lines.add(grey(CELang.translate("tooltip.fuel.cooling")
				.component()));
		if (fuel.sootFactor() <= 0)
			lines.add(grey(CELang.translate("tooltip.fuel.clean")
				.component()));
		if (fuel.injectorFlow() > THIN)
			lines.add(grey(CELang.translate("tooltip.fuel.thin", number(fuel.injectorFlow()))
				.component()));
		String exhaust = exhaust(fuel);
		if (exhaust != null)
			lines.add(grey(CELang.translate(exhaust)
				.component()));
		String spill = spill(fuel);
		if (spill != null)
			lines.add(grey(CELang.translate(spill)
				.component()));
		return lines;
	}

	/** The lang key for how dirty a fuel's exhaust is next to diesel's, or null for the usual. A fuel that burns clean has its own line. */
	static String exhaust(FuelSpec fuel) {
		if (fuel.sootFactor() > DIRTY)
			return "tooltip.fuel.dirty";
		if (fuel.sootFactor() > 0 && fuel.sootFactor() <= CLEANER && fuel.sulfur() <= 0)
			return "tooltip.fuel.cleaner";
		return null;
	}

	/** The lang key for how a spill of the fuel burns (see {@link FuelSpec#flammability()}), or null for the usual. */
	static String spill(FuelSpec fuel) {
		if (fuel.flammability() >= VOLATILE_SPILL)
			return "tooltip.fuel.spill.volatile";
		if (fuel.flammability() >= FLAMMABLE_SPILL)
			return "tooltip.fuel.spill.flammable";
		if (fuel.flammability() < SAFE_SPILL)
			return "tooltip.fuel.spill.safe";
		return null;
	}

	private static @Nullable Component cold(FuelSpec fuel) {
		if (fuel.coldSensitivity() <= GOOD_COLD)
			return CELang.translate("tooltip.fuel.cold.good")
				.component();
		if (fuel.coldSensitivity() >= POOR_COLD)
			return CELang.translate("tooltip.fuel.cold.poor")
				.component();
		return null;
	}

	private static Component grey(Component line) {
		return line.copy()
			.withStyle(ChatFormatting.GRAY);
	}

	/** At most one decimal place, none for a whole number: 36, not 36.0. */
	private static String number(double value) {
		double rounded = Math.round(value * 10) / 10.0;
		return rounded == Math.rint(rounded) ? String.format(Locale.ROOT, "%d", (long) rounded)
			: String.format(Locale.ROOT, "%.1f", rounded);
	}
}
