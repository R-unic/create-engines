package com.createengines.client.compat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.createengines.client.FuelTooltips;
import com.createengines.engine.sim.FuelSpec;
import com.createengines.registry.CEBlocks;
import com.createengines.registry.CEDataMaps;
import com.createengines.registry.CEItems;
import com.createengines.registry.CELang;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * What the recipe viewer pages for fuels and for engine parts list, and the wording on them, kept apart from both viewers
 * so each shows exactly the same and so neither viewer is named here (a game without one must start). Client only.
 */
public final class ViewerEntries {

	/** A bucket of a fuel, and what the engine knows about it. */
	public record Fuel(FluidStack stack, FuelSpec spec) {
	}

	/**
	 * A part that is installed into a block, and the blocks it goes into.
	 *
	 * @param part   the part
	 * @param blocks every block it fits in
	 * @param grade  whether it is a precision part that takes the place of a plain one in the same slot
	 */
	public record Part(ItemStack part, List<ItemStack> blocks, boolean grade) {
	}

	/** A charge hotter than this when it lights is far beyond what an ordinary engine reaches: the fuel takes boost well. */
	private static final double TAKES_BOOST = 4.5;

	private ViewerEntries() {
	}

	/** Every fuel the engines know of: ours, and any other mod's that a tag or a data map entry makes one. */
	public static List<Fuel> fuels() {
		List<Fuel> fuels = new ArrayList<>();
		for (Fluid fluid : BuiltInRegistries.FLUID) {
			if (fluid == Fluids.EMPTY || !fluid.defaultFluidState()
				.isSource())
				continue;
			FuelSpec spec = CEDataMaps.fuel(fluid);
			if (spec != null)
				fuels.add(new Fuel(new FluidStack(fluid, 1000), spec));
		}
		fuels.sort(Comparator.comparing(fuel -> BuiltInRegistries.FLUID.getKey(fuel.stack()
			.getFluid())
			.toString()));
		return fuels;
	}

	/** Every installable part and where it goes. */
	public static List<Part> parts() {
		List<ItemStack> cylinders = stacks(CEBlocks.CYLINDER.asStack(), CEBlocks.PRECISION_CYLINDER.asStack());
		List<ItemStack> headsAndRotors = stacks(CEBlocks.CYLINDER_HEAD.asStack(), CEBlocks.PRECISION_CYLINDER_HEAD.asStack(),
			CEBlocks.ROTOR_HOUSING.asStack());
		List<Part> parts = new ArrayList<>();
		parts.add(new Part(CEItems.PISTON.asStack(), cylinders, false));
		parts.add(new Part(CEItems.FORGED_PISTON.asStack(), cylinders, true));
		parts.add(new Part(CEItems.HIGH_COMPRESSION_PISTON.asStack(), cylinders, true));
		parts.add(new Part(CEItems.LOW_COMPRESSION_PISTON.asStack(), cylinders, true));
		parts.add(new Part(CEItems.PISTON_RING.asStack(), cylinders, false));
		parts.add(new Part(CEItems.CONNECTING_ROD.asStack(), cylinders, false));
		parts.add(new Part(CEItems.FUEL_INJECTOR.asStack(), headsAndRotors, false));
		parts.add(new Part(CEItems.PRECISION_INJECTOR.asStack(), headsAndRotors, true));
		parts.add(new Part(CEItems.SPARK_PLUG.asStack(), headsAndRotors, false));
		parts.add(new Part(CEItems.INJECTOR_ASSIST.asStack(), stacks(CEBlocks.CYLINDER_HEAD.asStack(), CEBlocks.PRECISION_CYLINDER_HEAD.asStack()), false));
		parts.add(new Part(CEItems.IGNITION_COIL.asStack(), stacks(CEBlocks.CYLINDER_HEAD.asStack(), CEBlocks.PRECISION_CYLINDER_HEAD.asStack(),
			CEBlocks.ROTOR_HOUSING.asStack()), false));
		parts.add(new Part(CEItems.ROTOR.asStack(), stacks(CEBlocks.ROTOR_HOUSING.asStack()), false));
		parts.add(new Part(CEItems.HIGH_COMPRESSION_ROTOR.asStack(), stacks(CEBlocks.ROTOR_HOUSING.asStack()), false));
		parts.add(new Part(CEItems.LOW_COMPRESSION_ROTOR.asStack(), stacks(CEBlocks.ROTOR_HOUSING.asStack()), false));
		parts.add(new Part(CEItems.OIL_FILTER.asStack(), stacks(CEBlocks.SUMP.asStack()), false));
		return parts;
	}

	private static List<ItemStack> stacks(ItemStack... stacks) {
		return List.of(stacks);
	}

	/** The lines under a fuel: what {@link FuelTooltips} says of it, without the title, and how it takes boost. */
	public static List<Component> fuelLines(Fuel fuel) {
		List<Component> lines = new ArrayList<>(FuelTooltips.lines(fuel.spec()));
		lines.remove(0);
		FuelSpec spec = fuel.spec();
		// A compression fuel lights by squeezing and has no early light to speak of; a spark fuel has one, and where it is
		// decides how much boost it takes
		if (spec.autoignitionTemp() > com.createengines.registry.CEDataMaps.PILOT_IGNITION_LIMIT)
			lines.add(CELang.translate(spec.preignitionTemp() >= TAKES_BOOST ? "recipe.fuel.boost.takes" : "recipe.fuel.boost.limited",
				com.createengines.engine.sim.Thermal.chargeDegrees(spec.preignitionTemp()))
				.component()
				.copy()
				.withStyle(ChatFormatting.GRAY));
		return lines;
	}

	/** The lines under a part. */
	public static List<Component> partLines(Part part) {
		List<Component> lines = new ArrayList<>();
		lines.add(CELang.translate("recipe.part.install")
			.component()
			.copy()
			.withStyle(ChatFormatting.GRAY));
		if (part.grade())
			lines.add(CELang.translate("recipe.part.precision")
				.component()
				.copy()
				.withStyle(ChatFormatting.GRAY));
		return lines;
	}
}
