package com.createengines.registry;

import org.jetbrains.annotations.Nullable;

import com.createengines.CreateEngines;
import com.createengines.engine.sim.FuelSpec;
import com.createengines.engine.sim.PartStats;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.function.Predicate;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
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
	 * The last four are optional: a fuel that leaves them out is an ordinary one in a cold block, cools its charge no
	 * more than any other fuel and fouls a plug like any other and pre-ignites under boost at its autoignition point.
	 */
	public record FuelProperties(double energyPerMb, double autoignitionTemp, double airPerMb, double coldSensitivity,
								 double chargeCooling, double sootFactor, double preignitionTemp) {
		public static final Codec<FuelProperties> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.DOUBLE.fieldOf("energy_per_mb").forGetter(FuelProperties::energyPerMb),
				Codec.DOUBLE.fieldOf("autoignition_temperature").forGetter(FuelProperties::autoignitionTemp),
				Codec.DOUBLE.fieldOf("air_per_mb").forGetter(FuelProperties::airPerMb),
				Codec.DOUBLE.optionalFieldOf("cold_sensitivity", 1.0).forGetter(FuelProperties::coldSensitivity),
				Codec.DOUBLE.optionalFieldOf("charge_cooling", 0.0).forGetter(FuelProperties::chargeCooling),
				Codec.DOUBLE.optionalFieldOf("soot_factor", 1.0).forGetter(FuelProperties::sootFactor),
				Codec.DOUBLE.optionalFieldOf("preignition_temperature", 0.0).forGetter(FuelProperties::preignitionTemp))
			.apply(i, FuelProperties::new));

		public FuelSpec toSpec() {
			return new FuelSpec(energyPerMb, autoignitionTemp, airPerMb, coldSensitivity, chargeCooling, sootFactor,
				preignitionTemp > 0 ? preignitionTemp : autoignitionTemp);
		}
	}

	public static final DataMapType<Fluid, FuelProperties> FUEL =
		DataMapType.builder(CreateEngines.asResource("fuel"), Registries.FLUID, FuelProperties.CODEC)
			.synced(FuelProperties.CODEC, false)
			.build();

	/**
	 * <b>Preview.</b> A retuning of the cylinders built in one block, as scales on the stats the built-in tiers give them:
	 * {@code data/<namespace>/data_maps/block/part_stats.json}, keyed by {@code create_engines:cylinder} or
	 * {@code create_engines:precision_cylinder}. Every field is optional and 1 changes nothing. The format may still change
	 * before 2.0, where parts as data replaces it and keeps reading it.
	 */
	public record PartStatsProperties(double compressionRatio, double intakeFlow, double exhaustFlow, double maxFuelPerShot,
									  double ignitionDelay, double burnDuration, double frictionStatic, double frictionViscous,
									  double inertia, double displacement, double injectorFlow) {

		private static Codec<Double> scale() {
			return Codec.doubleRange(PartStats.MIN_SCALE, PartStats.MAX_SCALE);
		}

		public static final Codec<PartStatsProperties> CODEC = RecordCodecBuilder.create(i -> i.group(
				scale().optionalFieldOf("compression_ratio", 1.0).forGetter(PartStatsProperties::compressionRatio),
				scale().optionalFieldOf("intake_flow", 1.0).forGetter(PartStatsProperties::intakeFlow),
				scale().optionalFieldOf("exhaust_flow", 1.0).forGetter(PartStatsProperties::exhaustFlow),
				scale().optionalFieldOf("max_fuel_per_shot", 1.0).forGetter(PartStatsProperties::maxFuelPerShot),
				scale().optionalFieldOf("ignition_delay", 1.0).forGetter(PartStatsProperties::ignitionDelay),
				scale().optionalFieldOf("burn_duration", 1.0).forGetter(PartStatsProperties::burnDuration),
				scale().optionalFieldOf("friction_static", 1.0).forGetter(PartStatsProperties::frictionStatic),
				scale().optionalFieldOf("friction_viscous", 1.0).forGetter(PartStatsProperties::frictionViscous),
				scale().optionalFieldOf("inertia", 1.0).forGetter(PartStatsProperties::inertia),
				scale().optionalFieldOf("displacement", 1.0).forGetter(PartStatsProperties::displacement),
				scale().optionalFieldOf("injector_flow", 1.0).forGetter(PartStatsProperties::injectorFlow))
			.apply(i, PartStatsProperties::new));

		public PartStats toStats() {
			return new PartStats(compressionRatio, intakeFlow, exhaustFlow, maxFuelPerShot, ignitionDelay, burnDuration,
				frictionStatic, frictionViscous, inertia, displacement, injectorFlow);
		}
	}

	public static final DataMapType<Block, PartStatsProperties> PART_STATS =
		DataMapType.builder(CreateEngines.asResource("part_stats"), Registries.BLOCK, PartStatsProperties.CODEC)
			.build();

	/**
	 * The retuning of the cylinders built in the plain or the precision cylinder block ({@link PartStats#NONE} if no pack
	 * names it, which is the case in every world that has not used the data map).
	 */
	public static PartStats partStats(boolean precisionBore) {
		Block block = (precisionBore ? CEBlocks.PRECISION_CYLINDER : CEBlocks.CYLINDER).get();
		PartStatsProperties properties = block.builtInRegistryHolder()
			.getData(PART_STATS);
		return properties == null ? PartStats.NONE : properties.toStats();
	}

	private CEDataMaps() {
	}

	public static void register(RegisterDataMapTypesEvent event) {
		event.register(FUEL);
		event.register(PART_STATS);
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
