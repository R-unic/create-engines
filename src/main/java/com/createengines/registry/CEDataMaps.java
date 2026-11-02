package com.createengines.registry;

import org.jetbrains.annotations.Nullable;

import com.createengines.CreateEngines;
import com.createengines.engine.sim.FuelSpec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

/**
 * Fuel properties live in a data map on fluids, so any fluid (including other mods' diesel) can be made into an
 * engine fuel with a datapack: {@code data/<namespace>/data_maps/fluid/fuel.json}.
 */
public final class CEDataMaps {

	public record FuelProperties(double energyPerMb, double autoignitionTemp, double airPerMb) {
		public static final Codec<FuelProperties> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.DOUBLE.fieldOf("energy_per_mb").forGetter(FuelProperties::energyPerMb),
				Codec.DOUBLE.fieldOf("autoignition_temperature").forGetter(FuelProperties::autoignitionTemp),
				Codec.DOUBLE.fieldOf("air_per_mb").forGetter(FuelProperties::airPerMb))
			.apply(i, FuelProperties::new));

		public FuelSpec toSpec() {
			return new FuelSpec(energyPerMb, autoignitionTemp, airPerMb);
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

	public static @Nullable FuelSpec fuel(Fluid fluid) {
		FuelProperties properties = fluid.builtInRegistryHolder()
			.getData(FUEL);
		return properties == null ? null : properties.toSpec();
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
