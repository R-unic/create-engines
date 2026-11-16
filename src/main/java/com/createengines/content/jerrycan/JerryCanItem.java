package com.createengines.content.jerrycan;

import java.util.List;

import com.createengines.registry.CEComponents;
import com.createengines.registry.CELang;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.fluids.capability.templates.FluidHandlerItemStack;

/**
 * A jerry can: a few buckets of one fluid in one item, for carrying fuel to where there is no pipe. It takes and gives
 * fluid the way a bucket does for every tank, Create Spouts and Item Drains included, because it is an ordinary fluid
 * container item. It stacks to one.
 */
public class JerryCanItem extends Item {

	/** What a can holds, mB: four buckets. */
	public static final int CAPACITY = 4000;
	private static final int BAR = 13;
	private static final int BUCKET = 1000;

	public JerryCanItem(Properties properties) {
		super(properties.stacksTo(1));
	}

	/** The fluid handler for a can; registered as the item's fluid capability. */
	public static FluidHandlerItemStack handler(ItemStack stack) {
		return new FluidHandlerItemStack(CEComponents.CAN_FLUID, stack, CAPACITY);
	}

	/** What a can holds, capped at its capacity so a damaged or commanded stack cannot show or move more than that. */
	public static FluidStack contents(ItemStack stack) {
		SimpleFluidContent content = stack.get(CEComponents.CAN_FLUID);
		if (content == null || content.isEmpty())
			return FluidStack.EMPTY;
		FluidStack held = content.copy();
		if (held.getAmount() > CAPACITY)
			held.setAmount(CAPACITY);
		return held;
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return !contents(stack).isEmpty();
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		return Math.clamp(Math.round((float) BAR * contents(stack).getAmount() / CAPACITY), 0, BAR);
	}

	@Override
	public int getBarColor(ItemStack stack) {
		FluidStack held = contents(stack);
		return held.isEmpty() ? com.createengines.engine.sim.WindowTint.AMBER.colour() & 0xFFFFFF
			: com.createengines.content.refinery.RefiningColumns.tintOf(held.getFluid())
				.colour() & 0xFFFFFF;
	}

	/**
	 * Sneak-use pours one bucket onto the ground as a source block, the way a bucket does. It is the one way a can
	 * loses fuel on purpose, so it needs the sneak: an ordinary click never spills.
	 */
	@Override
	public net.minecraft.world.InteractionResultHolder<ItemStack> use(net.minecraft.world.level.Level level,
		net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		FluidStack held = contents(stack);
		if (!player.isShiftKeyDown() || held.getAmount() < BUCKET || held.isEmpty())
			return net.minecraft.world.InteractionResultHolder.pass(stack);
		net.minecraft.world.phys.BlockHitResult hit = getPlayerPOVHitResult(level, player,
			net.minecraft.world.level.ClipContext.Fluid.NONE);
		if (hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK)
			return net.minecraft.world.InteractionResultHolder.pass(stack);
		net.minecraft.core.BlockPos at = hit.getBlockPos()
			.relative(hit.getDirection());
		if (!level.mayInteract(player, at))
			return net.minecraft.world.InteractionResultHolder.fail(stack);
		FluidStack bucket = held.copyWithAmount(BUCKET);
		var handler = net.neoforged.neoforge.fluids.FluidUtil.getFluidHandler(stack);
		if (handler.isEmpty())
			return net.minecraft.world.InteractionResultHolder.fail(stack);
		if (!net.neoforged.neoforge.fluids.FluidUtil.tryPlaceFluid(player, level, hand, at, handler.get(), bucket))
			return net.minecraft.world.InteractionResultHolder.fail(stack);
		return net.minecraft.world.InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		FluidStack held = contents(stack);
		if (held.isEmpty())
			tooltip.add(CELang.translate("tooltip.jerry_can.empty", CAPACITY)
				.style(ChatFormatting.GRAY)
				.component());
		else {
			tooltip.add(CELang.translate("tooltip.jerry_can.holds", held.getAmount(), CAPACITY, held.getHoverName()
				.getString())
				.style(ChatFormatting.GOLD)
				.component());
			if (held.getAmount() >= BUCKET)
				tooltip.add(CELang.translate("tooltip.jerry_can.pour")
					.style(ChatFormatting.DARK_GRAY)
					.component());
		}
	}
}
