package com.createengines.gametest;

import com.createengines.CreateEngines;
import com.createengines.engine.sim.FuelSpec;
import com.createengines.registry.CEDataMaps;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Create: The Factory Must Grow's fuels run our engines through the common fluid tags TFMG itself fills ({@code c:diesel},
 * {@code c:gasoline}, {@code c:kerosene}, {@code c:naphtha}), with no datapack and no entry of ours naming them.
 * <p>
 * Without TFMG loaded (the normal GameTest run) the test only logs. With {@code create_engines.requireTfmg} set, which
 * {@code runGameTestServerTfmg} does and loads the jar for, a missing mod or a fluid that burns as the wrong fuel is a failure.
 */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class TfmgCompatGameTests {

	private static final boolean REQUIRED = Boolean.getBoolean("create_engines.requireTfmg");

	private static void burnsAs(GameTestHelper helper, String id, FuelSpec expected) {
		Fluid fluid = BuiltInRegistries.FLUID.getOptional(ResourceLocation.parse(id))
			.orElse(null);
		helper.assertTrue(fluid != null, "TFMG is loaded but has no fluid " + id);
		FuelSpec actual = CEDataMaps.fuel(fluid);
		helper.assertTrue(actual == expected, id + " should burn as " + expected + " but burns as " + actual);
	}

	@GameTest(template = "empty")
	public static void tfmgFuelsBurnAsTheFuelsTheyAre(GameTestHelper helper) {
		if (!ModList.get()
			.isLoaded("tfmg")) {
			helper.assertTrue(!REQUIRED, "The run requires TFMG but it is not loaded");
			org.slf4j.LoggerFactory.getLogger(TfmgCompatGameTests.class)
				.info("TFMG is not loaded: nothing to check in this run");
			helper.succeed();
			return;
		}
		burnsAs(helper, "tfmg:diesel", FuelSpec.DIESEL);
		burnsAs(helper, "tfmg:flowing_diesel", FuelSpec.DIESEL);
		burnsAs(helper, "tfmg:gasoline", FuelSpec.GASOLINE);
		burnsAs(helper, "tfmg:flowing_gasoline", FuelSpec.GASOLINE);
		burnsAs(helper, "tfmg:kerosene", FuelSpec.KEROSENE);
		burnsAs(helper, "tfmg:flowing_kerosene", FuelSpec.KEROSENE);
		burnsAs(helper, "tfmg:naphtha", FuelSpec.NAPHTHA);
		burnsAs(helper, "tfmg:flowing_naphtha", FuelSpec.NAPHTHA);
		// Fluids that are not liquid fuels for an engine stay out
		for (String id : new String[] {"tfmg:crude_oil", "tfmg:heavy_oil", "tfmg:lubrication_oil", "tfmg:lpg"}) {
			Fluid fluid = BuiltInRegistries.FLUID.getOptional(ResourceLocation.parse(id))
				.orElse(null);
			helper.assertTrue(fluid != null, "TFMG is loaded but has no fluid " + id);
			helper.assertTrue(CEDataMaps.fuel(fluid) == null, id + " is not an engine fuel but burns as " + CEDataMaps.fuel(fluid));
		}
		helper.succeed();
	}
}
