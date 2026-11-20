package com.createengines.gametest;

import java.util.HashSet;
import java.util.Set;

import com.createengines.CreateEngines;
import com.createengines.content.headphones.HeadphoneWear;
import com.createengines.content.headphones.HeadphonesItem;
import com.createengines.content.headphones.Muffle;
import com.createengines.registry.CEItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The noise-cancelling headphones, on the server side of what they are: wearable in the head slot, counted as worn only when
 * they are there, crafted, fitted to Curios' head slot by tag, and every sound category the game has decided one way or the
 * other. The muffling itself is client side and is checked against the sound card by the client audit
 * ({@code HeadphonesAudit}); {@link Muffle} is unit tested.
 */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class HeadphonesGameTests {

	@GameTest(template = "empty")
	public static void headphonesCountAsWornOnlyWhenTheyAreOnTheHead(GameTestHelper helper) {
		ArmorStand wearer = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(2, 1, 2));
		ItemStack headphones = new ItemStack(CEItems.HEADPHONES.get());
		helper.assertTrue(!HeadphoneWear.isWorn(wearer), "Nobody wears them before they are put on");

		wearer.setItemSlot(EquipmentSlot.MAINHAND, headphones.copy());
		helper.assertTrue(!HeadphoneWear.isWorn(wearer), "Held in the hand they are not worn");
		wearer.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);

		wearer.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		helper.assertTrue(!HeadphoneWear.isWorn(wearer), "A helmet in the head slot is not a pair of headphones");

		wearer.setItemSlot(EquipmentSlot.HEAD, headphones.copy());
		helper.assertTrue(HeadphoneWear.isWorn(wearer), "In the head slot they are worn");

		wearer.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
		helper.assertTrue(!HeadphoneWear.isWorn(wearer), "Taken off, they are not worn");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void theHeadphonesGoOnTheHeadLikeAnyWearable(GameTestHelper helper) {
		ItemStack headphones = new ItemStack(CEItems.HEADPHONES.get());
		Equipable equipable = Equipable.get(headphones);
		helper.assertTrue(equipable != null, "The headphones can be equipped by using them");
		helper.assertTrue(equipable.getEquipmentSlot() == EquipmentSlot.HEAD, "and they go in the head slot");
		helper.assertTrue(headphones.getMaxStackSize() == 1, "one pair to a stack");
		helper.assertTrue(CEItems.HEADPHONES.get() instanceof HeadphonesItem, "and the item is the headphones' own class");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void theHeadphonesFitCuriosHeadSlotByTag(GameTestHelper helper) {
		TagKey<net.minecraft.world.item.Item> head = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("curios", "head"));
		helper.assertTrue(new ItemStack(CEItems.HEADPHONES.get()).is(head),
			"The curios:head tag is what puts an item in Curios' head slot, and it must list the headphones");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void theHeadphonesCanBeCrafted(GameTestHelper helper) {
		var recipes = helper.getLevel()
			.getRecipeManager();
		var recipe = recipes.byKey(CreateEngines.asResource("headphones"));
		helper.assertTrue(recipe.isPresent(), "There is a recipe named headphones");
		helper.assertTrue(recipe.get()
			.value()
			.getResultItem(helper.getLevel()
				.registryAccess())
			.is(CEItems.HEADPHONES.get()), "and it makes the headphones");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void everySoundCategoryTheGameHasIsDecidedByTheHeadphones(GameTestHelper helper) {
		Set<String> game = new HashSet<>();
		for (SoundSource source : SoundSource.values())
			game.add(source.getName());
		helper.assertTrue(game.equals(new HashSet<>(Muffle.KNOWN)),
			"The game's sound categories are " + game + " but the headphones decide " + Muffle.KNOWN
				+ ". A new category needs a decision in Muffle (muffled or exempt), not a silent default");
		for (SoundSource source : SoundSource.values()) {
			boolean exempt = source == SoundSource.MASTER || source == SoundSource.MUSIC || source == SoundSource.VOICE;
			helper.assertTrue(Muffle.muffles(source.getName()) != exempt, source.getName()
				+ (exempt ? " (music, interface or voice) must not be muffled" : " (the world) must be muffled"));
		}
		helper.succeed();
	}
}
