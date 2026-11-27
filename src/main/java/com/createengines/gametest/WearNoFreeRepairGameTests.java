package com.createengines.gametest;

import java.util.List;
import java.util.Map;

import com.createengines.CreateEngines;
import com.createengines.content.WearService;
import com.createengines.content.blueprint.BlueprintBuilder;
import com.createengines.content.cylinder.CylinderBlock;
import com.createengines.content.cylinder.CylinderBlockEntity;
import com.createengines.content.head.CylinderHeadBlock;
import com.createengines.content.head.CylinderHeadBlockEntity;
import com.createengines.content.tool.BankBuilderItem;
import com.createengines.engine.blueprint.Blueprint;
import com.createengines.engine.sim.Wear;
import com.createengines.registry.CEBlocks;
import com.createengines.registry.CEComponents;
import com.createengines.registry.CEItems;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import com.tterrag.registrate.util.entry.ItemProviderEntry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Nothing but a Service Kit or a part that really is new takes wear away: not a crafting recipe, a blueprint, a Schematicannon or the
 * Bank Builder.
 */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class WearNoFreeRepairGameTests {

	private static final String BATCH = "wear-free-repair";
	private static final BlockPos AT = new BlockPos(4, 2, 3);

	@AfterBatch(batch = BATCH)
	public static void restore(ServerLevel level) {
		GameTestConfig.wearMode(false);
	}

	private static ItemStack worn(ItemProviderEntry<?, ?> item) {
		return WearService.withWear(item.asStack(), 0.9);
	}

	private static ItemStack wrecked(ItemProviderEntry<?, ?> item) {
		ItemStack stack = item.asStack();
		stack.set(CEComponents.FAILED.get(), true);
		return stack;
	}

	/** The ingredient of a recipe that takes this part: it is there, takes a new one, and refuses it worn or wrecked. */
	private static void takesOnlyNew(GameTestHelper helper, String recipe, ItemProviderEntry<?, ?> part) {
		var found = helper.getLevel()
			.getRecipeManager()
			.byKey(CreateEngines.asResource(recipe))
			.orElse(null);
		helper.assertTrue(found != null, "the recipe loads: " + recipe);
		List<Ingredient> ingredients = found.value() instanceof SequencedAssemblyRecipe assembly ? List.of(assembly.getIngredient())
			: found.value()
				.getIngredients();
		Ingredient taking = ingredients.stream()
			.filter(ingredient -> ingredient.test(part.asStack()))
			.findFirst()
			.orElse(null);
		helper.assertTrue(taking != null, recipe + " takes a new one");
		helper.assertTrue(!taking.test(worn(part)), recipe + " refuses a worn one");
		helper.assertTrue(!taking.test(wrecked(part)), recipe + " refuses a wrecked one");
	}

	@GameTest(template = "empty")
	public static void noRecipeMakesANewPartFromAWornOne(GameTestHelper helper) {
		takesOnlyNew(helper, "forged_piston", CEItems.PISTON);
		takesOnlyNew(helper, "copper_gasket", CEItems.RUBBER_GASKET);
		takesOnlyNew(helper, "large_battery", CEBlocks.BATTERY);
		takesOnlyNew(helper, "variable_turbocharger", CEBlocks.TURBOCHARGER);
		takesOnlyNew(helper, "precision_cylinder", CEBlocks.CYLINDER);
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void aSchematicIsPaidOnlyWithNewParts(GameTestHelper helper) {
		List<BlockState> states = List.of(CEBlocks.CYLINDER.get()
			.defaultBlockState()
			.setValue(CylinderBlock.HAS_PISTON, true)
			.setValue(CylinderBlock.HAS_ROD, true),
			CEBlocks.CYLINDER_HEAD.get()
				.defaultBlockState()
				.setValue(CylinderHeadBlock.HAS_SPARK_PLUG, true),
			CEBlocks.ROTOR_HOUSING.get()
				.defaultBlockState(),
			CEBlocks.BATTERY.get()
				.defaultBlockState(),
			CEBlocks.DRY_CELL.get()
				.defaultBlockState(),
			CEBlocks.LARGE_BATTERY.get()
				.defaultBlockState(),
			CEBlocks.TURBOCHARGER.get()
				.defaultBlockState(),
			CEBlocks.VARIABLE_TURBOCHARGER.get()
				.defaultBlockState());
		for (BlockState state : states)
			for (var required : ItemRequirement.of(state, null)
				.getRequiredItems()) {
				ItemStack fresh = new ItemStack(required.stack.getItem());
				helper.assertTrue(required.matches(fresh), "a new one pays: " + fresh);
				helper.assertTrue(!required.matches(WearService.withWear(fresh.copy(), 0.5)), "a worn one does not: " + fresh);
				ItemStack failed = fresh.copy();
				failed.set(CEComponents.FAILED.get(), true);
				helper.assertTrue(!required.matches(failed), "a wrecked one does not: " + fresh);
			}
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void aBlueprintIsNotPaidWithAWornPart(GameTestHelper helper) {
		Blueprint blueprint = new Blueprint("t", "t",
			List.of(new Blueprint.Part(0, 0, 0, CreateEngines.MOD_ID + ":cylinder", Map.of("piston", "true"), Map.of())));
		Player builder = helper.makeMockPlayer(GameType.SURVIVAL);
		builder.getInventory()
			.add(CEBlocks.CYLINDER.asStack());
		builder.getInventory()
			.add(worn(CEItems.PISTON));
		var refused = BlueprintBuilder.build(builder, helper.getLevel(), helper.absolutePos(AT), blueprint);
		helper.assertTrue(refused.status() == BlueprintBuilder.Status.MISSING, "short of a new piston: " + refused);
		helper.assertTrue(helper.getBlockState(AT)
			.isAir(), "and nothing is built");
		builder.getInventory()
			.add(CEItems.PISTON.asStack());
		var built = BlueprintBuilder.build(builder, helper.getLevel(), helper.absolutePos(AT), blueprint);
		helper.assertTrue(built.status() == BlueprintBuilder.Status.BUILT, "a new one pays: " + built);
		helper.assertTrue(builder.getInventory()
			.countItem(CEItems.PISTON.get()) == 1, "the worn one is left with the player");
		helper.assertTrue(builder.getInventory().items.stream()
			.anyMatch(stack -> CEItems.PISTON.isIn(stack) && WearService.wearOf(stack) == 0.9), "as worn as it was");
		helper.setBlock(AT, Blocks.AIR);
		helper.succeed();
	}

	private static int dropped(GameTestHelper helper) {
		return helper.getLevel()
			.getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(TestEngine.CYLINDER)).inflate(3))
			.size();
	}

	private static Player precisionBuilder(GameTestHelper helper) {
		Player builder = helper.makeMockPlayer(GameType.SURVIVAL);
		builder.getInventory()
			.add(CEBlocks.PRECISION_CYLINDER.asStack());
		builder.getInventory()
			.add(CEBlocks.PRECISION_CYLINDER_HEAD.asStack());
		return builder;
	}

	private static void buildPrecision(GameTestHelper helper, Player builder) {
		BankBuilderItem.build(helper.getLevel(), helper.absolutePos(TestEngine.THROW), Direction.UP, BankBuilderItem.Layout.INLINE, true,
			builder, false);
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void thePrecisionSwapDropsNoPartAndKeepsItsWear(GameTestHelper helper) {
		GameTestConfig.wearMode(true);
		TestEngine.build(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), false);
		CylinderBlockEntity body = helper.getBlockEntity(TestEngine.CYLINDER);
		body.fitPiston(0.6);
		body.fitRod(0.4);
		CylinderHeadBlockEntity head = helper.getBlockEntity(TestEngine.HEAD);
		head.fit(Wear.Gasket.COPPER, 0.5);
		BlockState before = helper.getBlockState(TestEngine.HEAD);
		int lying = dropped(helper);
		Player builder = precisionBuilder(helper);
		buildPrecision(helper, builder);

		helper.assertTrue(CEBlocks.PRECISION_CYLINDER.has(helper.getBlockState(TestEngine.CYLINDER))
			&& CEBlocks.PRECISION_CYLINDER_HEAD.has(helper.getBlockState(TestEngine.HEAD)), "both blocks are swapped");
		helper.assertTrue(dropped(helper) == lying, "nothing drops out of a swapped block");
		CylinderBlockEntity swapped = helper.getBlockEntity(TestEngine.CYLINDER);
		helper.assertTrue(swapped.pistonWear() == 0.6 && swapped.rodWear() == 0.4,
			"the parts are as worn as they were: " + swapped.pistonWear() + " " + swapped.rodWear());
		CylinderHeadBlockEntity swappedHead = helper.getBlockEntity(TestEngine.HEAD);
		helper.assertTrue(swappedHead.gasket() == Wear.Gasket.COPPER && swappedHead.gasketWear() == 0.5,
			"so is the gasket: " + swappedHead.gasketStack());
		BlockState after = helper.getBlockState(TestEngine.HEAD);
		helper.assertTrue(after.getValue(CylinderHeadBlock.HAS_INJECTOR) == before.getValue(CylinderHeadBlock.HAS_INJECTOR)
			&& after.getValue(CylinderHeadBlock.HAS_SPARK_PLUG) == before.getValue(CylinderHeadBlock.HAS_SPARK_PLUG),
			"the head keeps what was fitted");
		helper.assertTrue(builder.getInventory()
			.countItem(CEBlocks.CYLINDER.asItem()) == 1 && builder.getInventory()
				.countItem(CEBlocks.CYLINDER_HEAD.asItem()) == 1, "the standard blocks come back");
		for (var part : List.of(CEItems.PISTON, CEItems.CONNECTING_ROD, CEItems.FUEL_INJECTOR, CEItems.SPARK_PLUG, CEItems.COPPER_GASKET))
			helper.assertTrue(builder.getInventory()
				.countItem(part.get()) == 0, "and no part with them: " + part.getId());
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void theBankBuilderLeavesAThrownRodsWreckAlone(GameTestHelper helper) {
		GameTestConfig.wearMode(true);
		TestEngine.build(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), false);
		CylinderBlockEntity body = helper.getBlockEntity(TestEngine.CYLINDER);
		body.throwRod();
		BlockState wreck = helper.getBlockState(TestEngine.CYLINDER);
		Player builder = precisionBuilder(helper);
		builder.getInventory()
			.add(CEItems.PISTON.asStack());
		builder.getInventory()
			.add(CEItems.CONNECTING_ROD.asStack());
		buildPrecision(helper, builder);

		helper.assertTrue(helper.getBlockState(TestEngine.CYLINDER) == wreck && body.showsThrown(),
			"the wreck is as it was: " + helper.getBlockState(TestEngine.CYLINDER));
		helper.assertTrue(builder.getInventory()
			.countItem(CEItems.PISTON.get()) == 1 && builder.getInventory()
				.countItem(CEBlocks.PRECISION_CYLINDER.asItem()) == 1, "and none of the builder's parts are used on it");
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void theBankBuilderDoesNotPlaceAWreckedCylinder(GameTestHelper helper) {
		TestEngine.build(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), false);
		helper.setBlock(TestEngine.CYLINDER, Blocks.AIR);
		Player builder = helper.makeMockPlayer(GameType.SURVIVAL);
		builder.getInventory()
			.add(wrecked(CEBlocks.CYLINDER));
		BankBuilderItem.build(helper.getLevel(), helper.absolutePos(TestEngine.THROW), Direction.UP, BankBuilderItem.Layout.INLINE,
			builder, false);
		helper.assertTrue(helper.getBlockState(TestEngine.CYLINDER)
			.isAir() && builder.getInventory()
				.countItem(CEBlocks.CYLINDER.asItem()) == 1, "it stays with the player");
		helper.succeed();
	}
}
