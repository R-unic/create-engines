package com.createengines.content.blueprint;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;

import com.createengines.engine.blueprint.Blueprint;
import com.createengines.engine.blueprint.Blueprint.Limits;
import com.createengines.engine.blueprint.BlueprintCodec;
import com.createengines.engine.blueprint.BlueprintDiff;
import com.createengines.engine.blueprint.BlueprintException;
import com.createengines.engine.blueprint.BlueprintRules;
import com.createengines.registry.CEComponents;
import com.createengines.registry.CELang;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.schematics.SchematicItem;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * A sheet that remembers an engine. Used on any block of a built engine while blank, it records the engine's layout and
 * every dial (see {@link BlueprintRecorder}); the sheet then holds it as a pasteable string. Named on an anvil first, the
 * blueprint carries that name. A recorded blueprint is not recorded over by accident: sneak and use it to record again.
 */
public class BlueprintItem extends Item {

	public BlueprintItem(Properties properties) {
		super(properties);
	}

	/** The recorded engine in this stack, or null if it is blank. */
	public static RecordedBlueprint recorded(ItemStack stack) {
		return stack.get(CEComponents.BLUEPRINT.get());
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		RecordedBlueprint held = recorded(stack);
		if (held == null) {
			// A blank sheet in the air asks for a pasted blueprint
			if (level.isClientSide)
				BlueprintViewer.importer.run();
			return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
		}
		if (hand == InteractionHand.MAIN_HAND && player.getOffhandItem()
			.is(AllItems.EMPTY_SCHEMATIC.get())) {
			if (!level.isClientSide)
				export(level, player, held);
			return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
		}
		if (level.isClientSide) {
			if (player.isShiftKeyDown())
				BlueprintViewer.copier.accept(held);
			else
				BlueprintViewer.open(held);
		}
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
	}

	/** Writes the blueprint as a Create schematic and swaps one Empty Schematic from the other hand for it. */
	private static void export(Level level, Player player, RecordedBlueprint held) {
		try {
			Blueprint blueprint = BlueprintCodec.decode(held.text());
			BlueprintRules.check(blueprint, EngineBlueprintPolicy.INSTANCE);
			String owner = player.getGameProfile()
				.getName();
			String file = BlueprintSchematic.write(blueprint, Paths.get("schematics", "uploaded")
				.resolve(owner));
			if (file == null) {
				tell(player, CELang.translate("blueprint.export.failed")
					.component(), ChatFormatting.RED);
				return;
			}
			ItemStack schematic = SchematicItem.create(level, file, owner);
			SchematicItem.writeSize(level, schematic);
			player.getOffhandItem()
				.shrink(1);
			if (!player.getInventory()
				.add(schematic))
				player.drop(schematic, false);
			tell(player, CELang.translate("blueprint.exported", file)
				.component(), ChatFormatting.GREEN);
		} catch (BlueprintException e) {
			tell(player, CELang.translate("blueprint.refused")
				.component(), ChatFormatting.RED);
		} catch (IOException e) {
			tell(player, CELang.translate("blueprint.export.failed")
				.component(), ChatFormatting.RED);
		}
	}

	/** Builds the recorded engine with its crankshaft against the face that was clicked. Used by the Bank Builder. */
	public static void buildFor(Level level, UseOnContext context, RecordedBlueprint held, Player player) {
		BlueprintBuilder.Result result = BlueprintBuilder.build(player, level, context.getClickedPos()
			.relative(context.getClickedFace()), held);
		BlueprintBuilder.report(player, result, held.name());
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		Player player = context.getPlayer();
		if (player == null)
			return InteractionResult.PASS;
		ItemStack stack = context.getItemInHand();
		RecordedBlueprint held = recorded(stack);
		if (held != null && !player.isShiftKeyDown()) {
			if (!level.isClientSide)
				apply(level, context, held, player);
			return InteractionResult.sidedSuccess(level.isClientSide);
		}
		if (level.isClientSide)
			return InteractionResult.SUCCESS;
		BlueprintRecorder.Result result = BlueprintRecorder.record(level, context.getClickedPos(), nameOf(stack),
			player.getGameProfile()
				.getName());
		if (!result.ok()) {
			tell(player, failure(result), ChatFormatting.RED);
			return InteractionResult.CONSUME;
		}
		String text;
		try {
			text = BlueprintCodec.encode(result.blueprint());
		} catch (BlueprintException e) {
			tell(player, CELang.translate("blueprint.too_big")
				.component(), ChatFormatting.RED);
			return InteractionResult.CONSUME;
		}
		Blueprint recorded = result.blueprint();
		stack.set(CEComponents.BLUEPRINT.get(), new RecordedBlueprint(text, recorded.name(), recorded.parts()
			.size()));
		tell(player, CELang.translate("blueprint.recorded", recorded.parts()
			.size())
			.component(), ChatFormatting.GREEN);
		if (result.skipped() > 0)
			player.displayClientMessage(CELang.translate("blueprint.skipped", result.skipped())
				.component()
				.copy()
				.withStyle(ChatFormatting.GRAY), false);
		level.playSound(null, context.getClickedPos(), SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.PLAYERS,
			0.8f, 1.2f);
		return InteractionResult.CONSUME;
	}

	/** Copies the recorded tune onto the engine that was clicked and tells the player what happened. */
	private static void apply(Level level, UseOnContext context, RecordedBlueprint held, Player player) {
		BlueprintApplier.Result result = BlueprintApplier.apply(level, context.getClickedPos(), held);
		switch (result.status()) {
			case APPLIED -> {
				tell(player, CELang.translate("blueprint.applied", result.changed())
					.component(), ChatFormatting.GREEN);
				level.playSound(null, context.getClickedPos(), SoundEvents.NETHERITE_BLOCK_PLACE, SoundSource.BLOCKS, 0.7f, 1.6f);
			}
			case ALREADY_SET -> tell(player, CELang.translate("blueprint.already_set")
				.component(), ChatFormatting.AQUA);
			case NO_ENGINE -> tell(player, CELang.translate("blueprint.no_engine")
				.component(), ChatFormatting.RED);
			case INCOMPLETE -> tell(player, CELang.translate("blueprint.incomplete")
				.component(), ChatFormatting.RED);
			case REFUSED -> tell(player, CELang.translate("blueprint.refused")
				.component(), ChatFormatting.RED);
			case DIFFERENT -> {
				tell(player, CELang.translate("blueprint.different", held.name())
					.component(), ChatFormatting.RED);
				for (var mismatch : result.mismatches())
					player.displayClientMessage(mismatchLine(mismatch), false);
			}
		}
	}

	private static Component mismatchLine(BlueprintDiff.Mismatch m) {
		String where = m.x() + ", " + m.y() + ", " + m.z();
		return CELang.translate("blueprint.mismatch." + m.kind()
			.name()
			.toLowerCase(java.util.Locale.ROOT), m.block(), where, m.detail())
			.component()
			.copy()
			.withStyle(ChatFormatting.GRAY);
	}

	/** The name given on an anvil, cut to what a blueprint holds, or empty (the recorder then uses the plate's, or a default). */
	static String nameOf(ItemStack stack) {
		Component custom = stack.get(DataComponents.CUSTOM_NAME);
		if (custom == null)
			return "";
		StringBuilder clean = new StringBuilder();
		for (char c : custom.getString()
			.toCharArray())
			if (!Character.isISOControl(c) && clean.length() < Limits.MAX_NAME)
				clean.append(c);
		String name = clean.toString()
			.strip();
		return name;
	}

	private static Component failure(BlueprintRecorder.Result result) {
		return switch (result.failure()) {
			case NO_ENGINE -> CELang.translate("blueprint.no_engine")
				.component();
			case INCOMPLETE -> CELang.translate("blueprint.incomplete")
				.component();
			case TOO_BIG -> CELang.translate("blueprint.too_big")
				.component();
			case UNSUPPORTED -> CELang.translate("blueprint.unsupported", result.detail())
				.component();
		};
	}

	private static void tell(Player player, Component message, ChatFormatting colour) {
		player.displayClientMessage(message.copy()
			.withStyle(colour), true);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		RecordedBlueprint held = recorded(stack);
		if (held == null) {
			tooltip.add(CELang.translate("tooltip.blueprint.blank")
				.component()
				.copy()
				.withStyle(ChatFormatting.GRAY));
			tooltip.add(CELang.translate("tooltip.blueprint.paste")
				.component()
				.copy()
				.withStyle(ChatFormatting.GRAY));
			return;
		}
		tooltip.add(CELang.translate("tooltip.blueprint.recorded", held.name(), held.parts())
			.component()
			.copy()
			.withStyle(ChatFormatting.AQUA));
		tooltip.add(CELang.translate("tooltip.blueprint.copy")
			.component()
			.copy()
			.withStyle(ChatFormatting.GRAY));
		tooltip.add(CELang.translate("tooltip.blueprint.read")
			.component()
			.copy()
			.withStyle(ChatFormatting.GRAY));
		tooltip.add(CELang.translate("tooltip.blueprint.export")
			.component()
			.copy()
			.withStyle(ChatFormatting.GRAY));
		tooltip.add(CELang.translate("tooltip.blueprint.again")
			.component()
			.copy()
			.withStyle(ChatFormatting.DARK_GRAY));
	}
}
