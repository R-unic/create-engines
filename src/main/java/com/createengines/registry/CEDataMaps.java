package com.createengines.registry;

import org.jetbrains.annotations.Nullable;

import com.createengines.CreateEngines;
import com.createengines.engine.sim.FuelSpec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.function.Predicate;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

/**
 * Fuel properties live in a data map on fluids, so any fluid (including other mods' diesel) can be made into an
 * engine fuel with a datapack: {@code data/<namespace>/data_maps/fluid/fuel.json}.
 */
public final class CEDataMaps {

	/**
	 * The last three are optional: a fuel that leaves them out is an ordinary one in a cold block, cools its charge no
	 * more than any other fuel and fouls a plug like any other.
	 */
	public record FuelProperties(double energyPerMb, double autoignitionTemp, double airPerMb, double coldSensitivity,
								 double chargeCooling, double sootFactor) {
		public static final Codec<FuelProperties> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.DOUBLE.fieldOf("energy_per_mb").forGetter(FuelProperties::energyPerMb),
				Codec.DOUBLE.fieldOf("autoignition_temperature").forGetter(FuelProperties::autoignitionTemp),
				Codec.DOUBLE.fieldOf("air_per_mb").forGetter(FuelProperties::airPerMb),
				Codec.DOUBLE.optionalFieldOf("cold_sensitivity", 1.0).forGetter(FuelProperties::coldSensitivity),
				Codec.DOUBLE.optionalFieldOf("charge_cooling", 0.0).forGetter(FuelProperties::chargeCooling),
				Codec.DOUBLE.optionalFieldOf("soot_factor", 1.0).forGetter(FuelProperties::sootFactor))
			.apply(i, FuelProperties::new));

		public FuelSpec toSpec() {
			return new FuelSpec(energyPerMb, autoignitionTemp, airPerMb, coldSensitivity, chargeCooling, sootFactor);
		}
	}

	public static final DataMapType<Fluid, FuelProperties> FUEL =
		DataMapType.builder(CreateEngines.asResource("fuel"), Registries.FLUID, FuelProperties.CODEC)
			.synced(FuelProperties.CODEC, false)
			.build();

	private CEDataMaps() {
	}

	public static void register(RegisterDataMapTypesEvent event) {
		event.register(FUEL);
	}

	private static TagKey<Fluid> common(String name) {
		return TagKey.create(Registries.FLUID, ResourceLocation.fromNamespaceAndPath("c", name));
	}

	/** The fluid tags other mods put their fuels in, and so ours: {@code c:diesel} and the rest. */
	public static final TagKey<Fluid> DIESEL = common("diesel");
	public static final TagKey<Fluid> BIODIESEL = common("biodiesel");
	public static final TagKey<Fluid> GASOLINE = common("gasoline");
	public static final TagKey<Fluid> ETHANOL = common("ethanol");
	public static final TagKey<Fluid> PLANT_OIL = common("plantoil");

	/**
	 * A fuel's properties: its entry in the fuel data map if it has one, and otherwise those of the common fluid tag it is
	 * in, so any mod's diesel, gasoline, ethanol or plant oil runs an engine without a datapack. A data map entry wins, so a
	 * pack can still tune or replace any fluid.
	 */
	public static @Nullable FuelSpec fuel(Fluid fluid) {
		Holder<Fluid> holder = fluid.builtInRegistryHolder();
		FuelProperties properties = holder.getData(FUEL);
		return properties == null ? fuelForTags(holder::is) : properties.toSpec();
	}

	/** The fuel a fluid counts as by its tags alone, or null if it is in none of the fuel tags. */
	public static @Nullable FuelSpec fuelForTags(Predicate<TagKey<Fluid>> inTag) {
		if (inTag.test(GASOLINE))
			return FuelSpec.GASOLINE;
		if (inTag.test(DIESEL) || inTag.test(BIODIESEL))
			return FuelSpec.DIESEL;
		if (inTag.test(ETHANOL))
			return FuelSpec.ETHANOL;
		if (inTag.test(PLANT_OIL))
			return FuelSpec.PLANT_OIL;
		return null;
	}

	public static boolean isFuel(FluidStack stack) {
		return !stack.isEmpty() && fuel(stack.getFluid()) != null;
	}

	/** Fuels that light themselves easily enough to start a flame in a charge that will not: diesel, plant oil and the like. */
	public static final double PILOT_IGNITION_LIMIT = 2.0;

	/** Whether a fluid can be injected as a pilot shot to light a main charge: a fuel that compression lights readily. */
	public static boolean isPilotFuel(FluidStack stack) {
		if (stack.isEmpty())
			return false;
		FuelSpec spec = fuel(stack.getFluid());
		return spec != null && spec.autoignitionTemp() <= PILOT_IGNITION_LIMIT;
	}
}
