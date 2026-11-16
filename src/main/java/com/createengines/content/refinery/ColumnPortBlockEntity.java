package com.createengines.content.refinery;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.createengines.content.GoggleLines;
import com.createengines.engine.sim.ColumnShape;
import com.createengines.engine.sim.ColumnShape.Fault;
import com.createengines.engine.sim.ColumnShape.Part;
import com.createengines.engine.sim.Heat;
import com.createengines.engine.sim.RefiningColumn;
import com.createengines.engine.sim.RefiningColumn.Limit;
import com.createengines.engine.sim.RefiningRoute;
import com.createengines.registry.CELang;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import com.simibubi.create.foundation.fluid.SmartFluidTank;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * A Reboiler or a Condenser. Every one of them looks for the column it is part of (see {@link ColumnShape}); the
 * Reboiler at the lowest corner of the base is the column's controller, and holds its tanks, its catalyst slot and its
 * simulation. The other Reboilers feed the controller and the Condensers draw from it, so a pipe on any of them reaches
 * the same tank.
 */
public class ColumnPortBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

	/** Feed and product held for each block of the base's footprint. */
	public static final int TANK_PER_BLOCK = 1000;
	/** Stack of catalyst the Reboiler's slot holds. */
	public static final int CATALYST_SLOT = 64;

	private static final BehaviourType<SmartFluidTankBehaviour> PRODUCT_TYPE = new BehaviourType<>("column_product_tank");
	/** How often, in ticks, the column's shape is looked at again. */
	private static final int SCAN_INTERVAL = 10;
	private static final int HEAT_INTERVAL = 20;
	private static final int ROUTE_INTERVAL = 40;
	private static final int SYNC_INTERVAL = 20;

	private SmartFluidTankBehaviour feed;
	private SmartFluidTankBehaviour product;
	private final ItemStackHandler catalyst = new ItemStackHandler(1) {
		@Override
		public int getSlotLimit(int slot) {
			return CATALYST_SLOT;
		}

		@Override
		public boolean isItemValid(int slot, ItemStack stack) {
			return level == null || RefiningRecipe.takesCatalyst(level, stack);
		}

		@Override
		protected void onContentsChanged(int slot) {
			setChanged();
		}
	};

	private ColumnShape.Result shape = new ColumnShape.Result(Fault.BASE, 0, 0, 0, 0, 0);
	private final RefiningColumn column = new RefiningColumn();
	private Heat heat = Heat.NONE;
	private Limit limit = Limit.NO_FEED;
	/** Product made, mB a second, smoothed. */
	private float rate;
	private int sinceScan;
	private int sinceHeat;
	private int sinceRoute;
	private int sinceSync;
	private boolean dirty;
	private @Nullable RecipeHolder<RefiningRecipe> route;
	private @Nullable FluidStack routeFeed;
	private Heat routeHeat = Heat.NONE;
	private IFluidHandler feedPort;
	private IFluidHandler productPort;
	private IItemHandler catalystPort;

	public ColumnPortBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		// Spread the first scans out so a hundred of these placed in one go do not all look on the same tick
		sinceScan = Math.floorMod(pos.hashCode(), SCAN_INTERVAL);
	}

	public Part part() {
		return getBlockState().getBlock() instanceof ColumnPortBlock port ? port.part() : Part.OTHER;
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		feed = SmartFluidTankBehaviour.single(this, TANK_PER_BLOCK);
		feed.getPrimaryHandler()
			.setValidator(stack -> level == null || RefiningRecipe.takesFeed(level, stack));
		behaviours.add(feed);
		product = new SmartFluidTankBehaviour(PRODUCT_TYPE, this, 1, TANK_PER_BLOCK, false);
		behaviours.add(product);
	}

	public SmartFluidTank feedTank() {
		return feed.getPrimaryHandler();
	}

	public SmartFluidTank productTank() {
		return product.getPrimaryHandler();
	}

	public ColumnShape.Result shape() {
		return shape;
	}

	/** Whether this is the Reboiler that runs its column. */
	public boolean isController() {
		return shape.formed() && part() == Part.REBOILER && worldPosition.getX() == shape.x()
			&& worldPosition.getY() == shape.y() && worldPosition.getZ() == shape.z();
	}

	/** The controller of the column this part is in, itself included; null when it is in no column. */
	public @Nullable ColumnPortBlockEntity controller() {
		if (!shape.formed() || level == null)
			return null;
		if (isController())
			return this;
		BlockPos at = new BlockPos(shape.x(), shape.y(), shape.z());
		return level.isLoaded(at) && level.getBlockEntity(at) instanceof ColumnPortBlockEntity other && other.isController()
			? other : null;
	}

	public Limit limit() {
		return limit;
	}

	public Heat heat() {
		return heat;
	}

	/** Product made in a second, mB. */
	public float rate() {
		return rate;
	}

	public ItemStack catalystStack() {
		return catalyst.getStackInSlot(0);
	}

	@Override
	public void tick() {
		super.tick();
		if (level == null || level.isClientSide)
			return;
		if (++sinceScan >= SCAN_INTERVAL) {
			sinceScan = 0;
			rescan();
		}
		if (isController())
			work();
		if (dirty && ++sinceSync >= SYNC_INTERVAL) {
			sinceSync = 0;
			dirty = false;
			sendData();
		}
	}

	private void rescan() {
		ColumnShape.Result next = RefiningColumns.scan(level, worldPosition);
		if (next.equals(shape))
			return;
		boolean wasController = isController();
		if (wasController && lit)
			RefiningColumns.showWork(level, shape, false);
		lit = false;
		litSynced = false;
		shape = next;
		if (wasController && !isController())
			column.reset();
		if (isController()) {
			int cap = TANK_PER_BLOCK * shape.width() * shape.width();
			feedTank().setCapacity(cap);
			productTank().setCapacity(cap);
			sinceHeat = HEAT_INTERVAL;
		}
		dirty = true;
		setChanged();
	}

	/** One tick of the column: take the feed, make the product, use the catalyst. */
	private void work() {
		if (!litSynced) {
			// Windows may have been saved lit while the column was not running (unloaded mid-run): start them dark
			litSynced = true;
			RefiningColumns.showWork(level, shape, false);
		}
		if (++sinceHeat >= HEAT_INTERVAL) {
			sinceHeat = 0;
			Heat next = RefiningColumns.heatUnder(level, shape);
			if (next != heat) {
				heat = next;
				dirty = true;
			}
		}
		FluidStack feedFluid = feedTank().getFluid();
		if (route == null || routeFeed == null || !FluidStack.isSameFluidSameComponents(routeFeed, feedFluid) || routeHeat != heat
			|| ++sinceRoute >= ROUTE_INTERVAL) {
			sinceRoute = 0;
			Optional<RecipeHolder<RefiningRecipe>> found = feedFluid.isEmpty() ? Optional.empty()
				: RefiningRecipe.find(level, feedFluid, heat);
			RecipeHolder<RefiningRecipe> next = found.orElse(null);
			if (next == null ? route != null : route == null || !route.id().equals(next.id()))
				column.reset();
			route = next;
			routeFeed = feedFluid.copy();
			routeHeat = heat;
		}

		Limit now;
		if (route == null) {
			now = feedFluid.isEmpty() ? Limit.NO_FEED : Limit.COLD;
			rate += (0 - rate) * 0.1f;
		} else {
			RefiningRecipe recipe = route.value();
			RefiningRoute sim = recipe.route();
			SmartFluidTank productTank = productTank();
			int room = productTank.isEmpty() || FluidStack.isSameFluidSameComponents(productTank.getFluid(), recipe.product())
				? productTank.getSpace() : 0;
			ItemStack held = catalyst.getStackInSlot(0);
			int items = recipe.catalyst() != null && recipe.catalyst().ingredient().test(held) ? held.getCount() : 0;
			RefiningColumn.Step step = column.tick(sim, shape.width(), shape.height(), heat, feedFluid.getAmount(), room, items);
			if (step.feedUsed() > 0)
				feedTank().drain(step.feedUsed(), IFluidHandler.FluidAction.EXECUTE);
			if (step.productMade() > 0)
				productTank.fill(recipe.product()
					.copyWithAmount(step.productMade()), IFluidHandler.FluidAction.EXECUTE);
			if (step.catalystUsed() > 0)
				catalyst.extractItem(0, step.catalystUsed(), false);
			rate += (step.productMade() * 20f - rate) * 0.05f;
			now = step.limit();
		}
		if (rate < 0.05f)
			rate = 0;
		if (now != limit) {
			limit = now;
			dirty = true;
		}
		boolean working = limit == Limit.NONE && rate > 0;
		if (working != lit) {
			lit = working;
			RefiningColumns.showWork(level, shape, working);
		}
		if (Math.abs(rate - lastSentRate) > 0.5f) {
			lastSentRate = rate;
			dirty = true;
		}
	}

	private float lastSentRate;
	/** Whether the body windows are lit. */
	private boolean lit;
	private boolean litSynced;

	/** Puts a catalyst item in the Reboiler's slot, wherever in the column it is asked; what did not fit comes back. */
	public ItemStack insertCatalyst(ItemStack stack) {
		ColumnPortBlockEntity controller = controller();
		return (controller == null ? catalyst : controller.catalyst).insertItem(0, stack, false);
	}

	/** Spills the catalyst when the controller is broken. */
	public void dropCatalyst() {
		if (level != null && lit)
			RefiningColumns.showWork(level, shape, false);
		ItemStack held = catalyst.getStackInSlot(0);
		if (level != null && !held.isEmpty()) {
			Block.popResource(level, worldPosition, held.copy());
			catalyst.setStackInSlot(0, ItemStack.EMPTY);
		}
	}

	/** Pipes and buckets: a Reboiler takes feed, a Condenser gives product. */
	public IFluidHandler fluidCapability() {
		if (part() == Part.REBOILER) {
			if (feedPort == null)
				feedPort = new Port(true);
			return feedPort;
		}
		if (productPort == null)
			productPort = new Port(false);
		return productPort;
	}

	/** Hoppers and funnels: only a Reboiler has a slot. */
	public @Nullable IItemHandler itemCapability() {
		if (part() != Part.REBOILER)
			return null;
		if (catalystPort == null)
			catalystPort = new CatalystPort();
		return catalystPort;
	}

	@Override
	protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.write(tag, registries, clientPacket);
		tag.putInt("Fault", shape.fault()
			.ordinal());
		tag.putInt("BaseX", shape.x());
		tag.putInt("BaseY", shape.y());
		tag.putInt("BaseZ", shape.z());
		tag.putInt("Width", shape.width());
		tag.putInt("Height", shape.height());
		tag.putIntArray("Lean", shape.lean());
		tag.putInt("BlockedX", shape.blockedX());
		tag.putInt("BlockedY", shape.blockedY());
		tag.putInt("BlockedZ", shape.blockedZ());
		tag.putInt("Heat", heat.ordinal());
		tag.putInt("Limit", limit.ordinal());
		tag.putFloat("Rate", rate);
		tag.put("Catalyst", catalyst.serializeNBT(registries));
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(tag, registries, clientPacket);
		Fault[] faults = Fault.values();
		// A saved size is only a first guess until the next scan, and must never be one that a loop could not finish
		int width = Math.clamp(tag.getInt("Width"), 0, RefiningColumn.MAX_WIDTH);
		int height = Math.clamp(tag.getInt("Height"), 0, ColumnShape.MAX_HEIGHT);
		// A saved lean is a first guess like the size: no longer than the layers it can have, no step further than they could
		int[] savedLean = tag.getIntArray("Lean");
		int[] lean = new int[Math.min(savedLean.length, 2 * (ColumnShape.MAX_HEIGHT + 2)) / 2 * 2];
		for (int i = 0; i < lean.length; i++)
			lean[i] = Math.clamp(savedLean[i], -ColumnShape.MAX_HEIGHT, ColumnShape.MAX_HEIGHT);
		shape = new ColumnShape.Result(faults[Math.clamp(tag.getInt("Fault"), 0, faults.length - 1)], tag.getInt("BaseX"),
			tag.getInt("BaseY"), tag.getInt("BaseZ"), width, height, lean, tag.getInt("BlockedX"), tag.getInt("BlockedY"),
			tag.getInt("BlockedZ"));
		heat = Heat.values()[Math.clamp(tag.getInt("Heat"), 0, Heat.values().length - 1)];
		limit = Limit.values()[Math.clamp(tag.getInt("Limit"), 0, Limit.values().length - 1)];
		rate = tag.getFloat("Rate");
		if (tag.contains("Catalyst")) {
			// Only the stack in the one slot is read: a saved size from a damaged file must not size the handler
			CompoundTag saved = tag.getCompound("Catalyst")
				.copy();
			saved.putInt("Size", 1);
			catalyst.deserializeNBT(registries, saved);
		}
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		CELang.translate(part() == Part.REBOILER ? "goggles.column.reboiler" : "goggles.column.condenser")
			.forGoggles(tooltip);
		if (shape.fault() == Fault.LEAN_BLOCKED && level != null) {
			BlockPos blocked = new BlockPos(shape.blockedX(), shape.blockedY(), shape.blockedZ());
			GoggleLines.add(CELang.translate("goggles.column.fault.lean_blocked", level.getBlockState(blocked)
				.getBlock()
				.getName()
				.getString(), blocked.toShortString())
				.style(ChatFormatting.RED), tooltip, 1);
			return true;
		}
		if (!shape.formed()) {
			GoggleLines.add(CELang.translate("goggles.column.fault." + shape.fault()
				.name()
				.toLowerCase(Locale.ROOT))
				.style(ChatFormatting.RED), tooltip, 1);
			if (isPlayerSneaking)
				GoggleLines.add(CELang.translate("goggles.column.hint")
					.style(ChatFormatting.DARK_GRAY), tooltip, 2);
			return true;
		}
		GoggleLines.add(CELang.translate("goggles.column.size", shape.width(), shape.width(), shape.height())
			.style(ChatFormatting.GOLD), tooltip, 1);
		ColumnPortBlockEntity src = controller() == null ? this : controller();
		GoggleLines.add(CELang.translate("goggles.column.heat." + src.heat.serializedName())
			.style(src.heat == Heat.NONE ? ChatFormatting.GRAY : ChatFormatting.GREEN), tooltip, 1);
		if (src.limit == Limit.NONE)
			GoggleLines.add(CELang.translate("goggles.column.rate", String.format(Locale.ROOT, "%.1f", src.rate))
				.style(ChatFormatting.AQUA), tooltip, 1);
		else
			GoggleLines.add(CELang.translate("goggles.column.limit." + src.limit.name()
				.toLowerCase(Locale.ROOT))
				.style(src.limit == Limit.NO_FEED ? ChatFormatting.GRAY : ChatFormatting.RED), tooltip, 1);
		ColumnPortBlockEntity controller = controller();
		ItemStack held = controller == null ? ItemStack.EMPTY : controller.catalystStack();
		if (part() == Part.REBOILER && controller != null)
			GoggleLines.add(held.isEmpty() ? CELang.translate("goggles.column.catalyst.none")
				.style(ChatFormatting.DARK_GRAY)
				: CELang.translate("goggles.column.catalyst", held.getCount(), held.getHoverName()
					.getString())
					.style(ChatFormatting.YELLOW), tooltip, 1);
		containedFluidTooltip(tooltip, isPlayerSneaking, fluidCapability());
		return true;
	}

	/** The tanks every Reboiler and every Condenser of one column share: feed goes in at the base, product comes off the top. */
	private final class Port implements IFluidHandler {

		private final boolean feedSide;

		Port(boolean feedSide) {
			this.feedSide = feedSide;
		}

		private SmartFluidTank tank() {
			ColumnPortBlockEntity controller = controller();
			ColumnPortBlockEntity owner = controller == null ? ColumnPortBlockEntity.this : controller;
			return feedSide ? owner.feedTank() : owner.productTank();
		}

		@Override
		public int getTanks() {
			return 1;
		}

		@Override
		public FluidStack getFluidInTank(int index) {
			return tank().getFluid();
		}

		@Override
		public int getTankCapacity(int index) {
			return tank().getCapacity();
		}

		@Override
		public boolean isFluidValid(int index, FluidStack stack) {
			return feedSide && tank().isFluidValid(stack);
		}

		@Override
		public int fill(FluidStack resource, FluidAction action) {
			return feedSide ? tank().fill(resource, action) : 0;
		}

		@Override
		public FluidStack drain(FluidStack resource, FluidAction action) {
			return feedSide ? FluidStack.EMPTY : tank().drain(resource, action);
		}

		@Override
		public FluidStack drain(int maxDrain, FluidAction action) {
			return feedSide ? FluidStack.EMPTY : tank().drain(maxDrain, action);
		}
	}

	/** The catalyst slot, seen from any Reboiler: it takes catalyst and never gives it back. */
	private final class CatalystPort implements IItemHandler {

		private ColumnPortBlockEntity owner() {
			ColumnPortBlockEntity controller = controller();
			return controller == null ? ColumnPortBlockEntity.this : controller;
		}

		@Override
		public int getSlots() {
			return 1;
		}

		@Override
		public ItemStack getStackInSlot(int slot) {
			return owner().catalyst.getStackInSlot(slot);
		}

		@Override
		public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
			return owner().catalyst.insertItem(slot, stack, simulate);
		}

		@Override
		public ItemStack extractItem(int slot, int amount, boolean simulate) {
			return ItemStack.EMPTY;
		}

		@Override
		public int getSlotLimit(int slot) {
			return CATALYST_SLOT;
		}

		@Override
		public boolean isItemValid(int slot, ItemStack stack) {
			return owner().catalyst.isItemValid(slot, stack);
		}
	}
}
