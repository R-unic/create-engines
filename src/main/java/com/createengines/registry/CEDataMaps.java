package com.createengines.registry;

import org.jetbrains.annotations.Nullable;

import com.createengines.CreateEngines;
import com.createengines.engine.FlywheelSpec;
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
	 * The last five are optional (sulfur, for the pollution compat only, defaults to the soot factor): a fuel that leaves them out is an ordinary one in a cold block, cools its charge no
	 * more than any other fuel and fouls a plug like any other and pre-ignites under boost at its autoignition point.
	 */
	public record FuelProperties(double energyPerMb, double autoignitionTemp, double airPerMb, double coldSensitivity,
								 double chargeCooling, double sootFactor, double preignitionTemp,
								 java.util.Optional<Double> sulfur, double flammability, double heatFactor) {

		/** A fuel that heats the block as every fuel did before {@code heat_factor} existed. */
		public FuelProperties(double energyPerMb, double autoignitionTemp, double airPerMb, double coldSensitivity,
							  double chargeCooling, double sootFactor, double preignitionTemp, java.util.Optional<Double> sulfur,
							  double flammability) {
			this(energyPerMb, autoignitionTemp, airPerMb, coldSensitivity, chargeCooling, sootFactor, preignitionTemp, sulfur, flammability, 1);
		}

		/** A fuel that burns in a spill the way every fuel did before {@code flammability} existed. */
		public FuelProperties(double energyPerMb, double autoignitionTemp, double airPerMb, double coldSensitivity,
							  double chargeCooling, double sootFactor, double preignitionTemp, java.util.Optional<Double> sulfur) {
			this(energyPerMb, autoignitionTemp, airPerMb, coldSensitivity, chargeCooling, sootFactor, preignitionTemp, sulfur, 1);
		}

		public static final Codec<FuelProperties> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.DOUBLE.fieldOf("energy_per_mb").forGetter(FuelProperties::energyPerMb),
				Codec.DOUBLE.fieldOf("autoignition_temperature").forGetter(FuelProperties::autoignitionTemp),
				Codec.DOUBLE.fieldOf("air_per_mb").forGetter(FuelProperties::airPerMb),
				Codec.DOUBLE.optionalFieldOf("cold_sensitivity", 1.0).forGetter(FuelProperties::coldSensitivity),
				Codec.DOUBLE.optionalFieldOf("charge_cooling", 0.0).forGetter(FuelProperties::chargeCooling),
				Codec.DOUBLE.optionalFieldOf("soot_factor", 1.0).forGetter(FuelProperties::sootFactor),
				Codec.DOUBLE.optionalFieldOf("preignition_temperature", 0.0).forGetter(FuelProperties::preignitionTemp),
				Codec.DOUBLE.optionalFieldOf("sulfur").forGetter(FuelProperties::sulfur),
				Codec.DOUBLE.optionalFieldOf("flammability", 1.0).forGetter(FuelProperties::flammability),
				Codec.DOUBLE.optionalFieldOf("heat_factor", 1.0).forGetter(FuelProperties::heatFactor))
			.apply(i, FuelProperties::new));

		/** The sulfur a fuel that does not set one gives off: its soot factor, as before the field existed. */
		public double effectiveSulfur() {
			return sulfur.orElse(sootFactor);
		}

		public FuelSpec toSpec() {
			return new FuelSpec(energyPerMb, autoignitionTemp, airPerMb, coldSensitivity, chargeCooling, sootFactor,
				preignitionTemp > 0 ? preignitionTemp : autoignitionTemp, effectiveSulfur(), flammability, heatFactor);
		}
	}

	public static final DataMapType<Fluid, FuelProperties> FUEL =
		DataMapType.builder(CreateEngines.asResource("fuel"), Registries.FLUID, FuelProperties.CODEC)
			.synced(FuelProperties.CODEC, false)
			.build();

	/**
	 * <b>Frozen at 2.0</b> (names pinned by {@code ScriptAndDataSurfaceTest}). A retuning of the cylinders built in one block, as scales on the stats the built-in tiers give them:
	 * {@code data/<namespace>/data_maps/block/part_stats.json}, keyed by {@code create_engines:cylinder},
	 * {@code create_engines:precision_cylinder} or {@code create_engines:rotor_housing} (a rotary chamber). Every field is optional and 1 changes nothing. Parts as data
	 * ({@code part_effects}) sits beside it and this still applies on top.
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
	 * Names the part effect a block adds to the cylinder it is part of. The block is a cylinder block or a cylinder head block
	 * (a pack cannot make one, an addon registers one of this mod's block classes under its own name), and the effect is a
	 * {@code part_effects} file of the pack or addon: {@code data/<namespace>/part_effects/<name>.json}, with the same keys
	 * and ranges as the files for this mod's four precision parts. That is a third tier of cylinder, or a fifth part, with its
	 * own model and its own numbers, and nothing in this mod to change.
	 *
	 * @param effect the file's id, such as {@code mymod:racing_bore}
	 */
	public record PartEffectProperties(ResourceLocation effect) {

		public static final Codec<PartEffectProperties> CODEC = RecordCodecBuilder.create(i -> i
			.group(ResourceLocation.CODEC.fieldOf("effect").forGetter(PartEffectProperties::effect))
			.apply(i, PartEffectProperties::new));
	}

	public static final DataMapType<Block, PartEffectProperties> PART_EFFECT =
		DataMapType.builder(CreateEngines.asResource("part_effect"), Registries.BLOCK, PartEffectProperties.CODEC)
			.build();

	/**
	 * The masses of a flywheel that is a pack's or an addon's own: a block made by {@code PartBlocks.flywheel} that is named
	 * here is a flywheel on the crank line, and one that is not named adds nothing. {@code
	 * data/<namespace>/data_maps/block/flywheel.json}. Added in 2.1 (API 1.2). {@code primary_inertia} joins the crank's inertia
	 * like any flywheel; {@code secondary_inertia} (optional, 0) sits behind the spring pack and only smooths the ripple the
	 * network sees, as the Dual-Mass Flywheel's does, at the resonance the server's {@code dualMassResonance} sets. Both are
	 * between 0 and {@link FlywheelSpec#MAX_INERTIA}, like the server's own flywheel settings. No block of this mod is named here.
	 */
	public record FlywheelProperties(double primaryInertia, double secondaryInertia) {

		private static Codec<Double> mass() {
			return Codec.doubleRange(0.0, FlywheelSpec.MAX_INERTIA);
		}

		public static final Codec<FlywheelProperties> CODEC = RecordCodecBuilder.create(i -> i.group(
				mass().fieldOf("primary_inertia").forGetter(FlywheelProperties::primaryInertia),
				mass().optionalFieldOf("secondary_inertia", 0.0).forGetter(FlywheelProperties::secondaryInertia))
			.apply(i, FlywheelProperties::new));

		public FlywheelSpec toSpec() {
			return new FlywheelSpec(primaryInertia, secondaryInertia);
		}
	}

	public static final DataMapType<Block, FlywheelProperties> FLYWHEEL =
		DataMapType.builder(CreateEngines.asResource("flywheel"), Registries.BLOCK, FlywheelProperties.CODEC)
			.build();

	/** What to answer in place of the flywheel data map, for the GameTests. Null in play. */
	public static volatile @Nullable java.util.function.Function<Block, FlywheelSpec> flywheelOverride;

	/** The masses a flywheel block of a pack's or an addon's own carries, or {@link FlywheelSpec#NONE} if no entry names it. */
	public static FlywheelSpec flywheel(Block block) {
		java.util.function.Function<Block, FlywheelSpec> forced = flywheelOverride;
		if (forced != null) {
			FlywheelSpec spec = forced.apply(block);
			return spec == null ? FlywheelSpec.NONE : spec;
		}
		FlywheelProperties properties = block.builtInRegistryHolder()
			.getData(FLYWHEEL);
		return properties == null ? FlywheelSpec.NONE : properties.toSpec();
	}

	/** What to answer in place of the data map, for the GameTests, which cannot add an entry to one. Null in play. */
	public static volatile @Nullable java.util.function.Function<Block, String> partEffectOverride;

	/** The id of the part effect the block adds to its cylinder, or null for every block that names none (all of this mod's). */
	public static @Nullable String partEffect(Block block) {
		java.util.function.Function<Block, String> forced = partEffectOverride;
		if (forced != null)
			return forced.apply(block);
		PartEffectProperties properties = block.builtInRegistryHolder()
			.getData(PART_EFFECT);
		return properties == null ? null : properties.effect()
			.toString();
	}

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

	/**
	 * The retuning of a rotary chamber: the entry a pack gives {@code create_engines:rotor_housing} in the same
	 * {@code part_stats} data map ({@link PartStats#NONE} if none). The scales apply to the built-in chamber, after the
	 * injector and the server's rotary power setting.
	 */
	public static PartStats rotaryStats() {
		PartStatsProperties properties = CEBlocks.ROTOR_HOUSING.get()
			.builtInRegistryHolder()
			.getData(PART_STATS);
		return properties == null ? PartStats.NONE : properties.toStats();
	}

	private CEDataMaps() {
	}

	public static void register(RegisterDataMapTypesEvent event) {
		event.register(FUEL);
		event.register(PART_STATS);
		event.register(PART_EFFECT);
		event.register(FLYWHEEL);
	}

	private static TagKey<Fluid> common(String name) {
		return TagKey.create(Registries.FLUID, ResourceLocation.fromNamespaceAndPath("c", name));
	}

	/** The fluid tags other mods put their fuels in, and so ours: {@code c:diesel} and the rest. */
	public static final TagKey<Fluid> DIESEL = common("diesel");
	public static final TagKey<Fluid> BIODIESEL = common("biodiesel");
	public static final TagKey<Fluid> GASOLINE = common("gasoline");
	public static final TagKey<Fluid> ETHANOL = common("ethanol");
	public static final TagKey<Fluid> KEROSENE = common("kerosene");
	public static final TagKey<Fluid> METHANOL = common("methanol");
	public static final TagKey<Fluid> NAPHTHA = common("naphtha");
	public static final TagKey<Fluid> PLANT_OIL = common("plantoil");

	/**
	 * A fuel's properties: its entry in the fuel data map if it has one, and otherwise those of the common fluid tag it is
	 * in, so any mod's diesel, gasoline, ethanol, kerosene or plant oil runs an engine without a datapack. A data map entry wins, so a
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
		// Biodiesel first: Create: Diesel Generators' biodiesel is in c:diesel as well, and is the clean one
		if (inTag.test(BIODIESEL))
			return FuelSpec.BIODIESEL;
		if (inTag.test(DIESEL))
			return FuelSpec.DIESEL;
		if (inTag.test(ETHANOL))
			return FuelSpec.ETHANOL;
		if (inTag.test(KEROSENE))
			return FuelSpec.KEROSENE;
		if (inTag.test(METHANOL))
			return FuelSpec.METHANOL;
		if (inTag.test(NAPHTHA))
			return FuelSpec.NAPHTHA;
		if (inTag.test(PLANT_OIL))
			return FuelSpec.PLANT_OIL;
		return null;
	}

	public static boolean isFuel(FluidStack stack) {
		return !stack.isEmpty() && fuel(stack.getFluid()) != null;
	}

	/** Fuels that light themselves easily enough to start a flame in a charge that will not: diesel, plant oil and the like. */
	public static final double PILOT_IGNITION_LIMIT = FuelSpec.COMPRESSION_LIMIT;

	/** Whether a fluid can be injected as a pilot shot to light a main charge: a fuel that compression lights readily. */
	public static boolean isPilotFuel(FluidStack stack) {
		if (stack.isEmpty())
			return false;
		FuelSpec spec = fuel(stack.getFluid());
		return spec != null && spec.autoignitionTemp() <= PILOT_IGNITION_LIMIT;
	}
}
