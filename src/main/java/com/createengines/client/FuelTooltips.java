package com.createengines.client;

import java.util.List;
import java.util.Locale;

import org.jetbrains.annotations.Nullable;

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

	private FuelTooltips() {
	}

	public static void onTooltip(ItemTooltipEvent event) {
		FluidStack contained = FluidUtil.getFluidContained(event.getItemStack())
			.orElse(FluidStack.EMPTY);
		if (contained.isEmpty())
			return;
		FuelSpec fuel = CEDataMaps.fuel(contained.getFluid());
		if (fuel != null)
			event.getToolTip()
				.addAll(lines(fuel));
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
		return lines;
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
