package com.createengines.content.head;

import java.util.List;

import com.createengines.content.WearService;
import com.createengines.content.cylinder.CylinderBlockEntity;
import com.createengines.engine.LoadedBlocks;
import com.createengines.engine.sim.Tuning;
import com.createengines.engine.sim.Wear;
import com.createengines.engine.topology.EngineTopology;
import com.createengines.registry.CEDataMaps;
import com.createengines.registry.CEFluids;
import com.createengines.content.GoggleLines;
import com.createengines.registry.CELang;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.simibubi.create.foundation.fluid.CombinedTankWrapper;
import com.simibubi.create.foundation.fluid.SmartFluidTank;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * Holds this head's share of the engine's fuel gallery and oil gallery. Fill it with Create pipes from any face:
 * fuel goes to the fuel tank and engine oil to the oil tank, and each is shared with every other head on the same crankshaft.
 * <p>
 * Its injector is metered by hand with a value box: how much fuel each shot delivers, as a share of the usual shot.
 * Nothing matches it to the air the cylinder breathes, so a setting that suits one speed runs rich above it and lean
 * below it.
 */
public class CylinderHeadBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation, GalleryMember {

	public static final int CAPACITY = 250;
	/** Metering is set in tenths of the usual shot, from a tenth of it to twice it. */
	public static final int METERING_STEP = 10;
	public static final int MIN_METERING = 1;
	public static final int MAX_METERING = 20;
	public static final int DEFAULT_METERING = 10;
	private static final BehaviourType<SmartFluidTankBehaviour> OIL_TYPE = new BehaviourType<>("oil_tank");
	private static final BehaviourType<ScrollValueBehaviour> METERING_TYPE = new BehaviourType<>("fuel_metering");

	protected SmartFluidTankBehaviour tank;
	protected SmartFluidTankBehaviour oil;
	public ScrollValueBehaviour metering;
	private IFluidHandler combined;

	public CylinderHeadBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	// Wear (opt-in, see Wear): the gasket under the head, how worn it is, and whether it has blown. All saved only once they differ from a
	// head that never wore, so such a head saves what it always did.
	private Wear.Gasket gasket = Wear.Gasket.STOCK;
	private double gasketWear;
	private boolean blown;
	private boolean broken;
	/** Whether the stock gasket in this head was fitted as an item (any other tier always was): see {@link #hasGasketItem}. */
	private boolean stockFitted;

	public Wear.Gasket gasket() {
		return gasket;
	}

	public double gasketWear() {
		return gasketWear;
	}

	/** Whether the gasket has blown: the cylinder under this head is stopped until a new gasket goes in. */
	public boolean blown() {
		return blown;
	}

	/**
	 * Adds wear to the gasket. Returns whether it moved to a new 5% step, which is when clients are told.
	 */
	public boolean addGasketWear(double amount) {
		int before = (int) Math.round(gasketWear * 20);
		gasketWear = Wear.clamp(gasketWear + amount);
		setChanged();
		boolean visible = before != (int) Math.round(gasketWear * 20);
		if (visible)
			sendData();
		return visible;
	}

	/** How worn the spark plug in this head is, 0 new to 1 spent (see {@code PlugWear}); saved only once there is some. */
	private double plugWear;
	/** Whether heat has warped this head: it leaks a share of every burn until it is replaced. Saved only while it has. */
	private boolean warped;
	/** How long the head has soaked past critical (see {@link Wear#heatSoak}); not saved, so a reloaded world starts clean. */
	private double heatSoak;

	public double plugWear() {
		return plugWear;
	}

	/** Adds wear to the plug. Clients are told when it moves to a new 5% step. */
	public void addPlugWear(double amount) {
		int before = (int) Math.round(plugWear * 20);
		plugWear = Wear.clamp(plugWear + amount);
		setChanged();
		if (before != (int) Math.round(plugWear * 20))
			sendData();
	}

	/** The plug in this head is now one worn this much (0 for a new one, or for none). */
	public void fitPlug(double wear) {
		double next = Wear.clamp(wear);
		if (next == plugWear)
			return;
		plugWear = next;
		setChanged();
		sendData();
	}

	public boolean warped() {
		return warped;
	}

	/** Whether the warp counts now (see {@link #showsBlown}). */
	public boolean showsWarped() {
		return Tuning.wearMode && warped;
	}

	public double heatSoak() {
		return heatSoak;
	}

	public void setHeatSoak(double soak) {
		heatSoak = soak;
	}

	/** Heat has warped the head: the engine is looked at again, and the cylinder under it leaks until the head is replaced. */
	public void warp() {
		if (warped)
			return;
		warped = true;
		setChanged();
		sendData();
		EngineTopology.wearChanged(level, worldPosition);
	}

	/** Whether a thrown rod broke this head: it stops its cylinder for good, and only replacing the head cures it. */
	public boolean broken() {
		return broken;
	}

	/** Whether the blown gasket counts now: with the wear mode off it stays saved, and is neither shown nor stops anything. */
	public boolean showsBlown() {
		return Tuning.wearMode && blown;
	}

	/** Whether the broken head counts now (see {@link #showsBlown}). */
	public boolean showsBroken() {
		return Tuning.wearMode && broken;
	}

	/**
	 * A gasket is going in with the wear mode off, into a head whose break nobody can see: the break goes, or the head would be broken
	 * again with the mode on, and the gasket lost with it (see {@code CylinderBlockEntity.dropHiddenWreck}).
	 */
	public void dropHiddenBreak() {
		if (Tuning.wearMode || !broken)
			return;
		broken = false;
		setChanged();
		sendData();
	}

	/** A rod went through the head: it is broken, and the gasket under it is lost with it. */
	public void breakHead() {
		if (broken)
			return;
		broken = true;
		warped = false;
		blown = false;
		gasket = Wear.Gasket.STOCK;
		gasketWear = 0;
		// Without this a stock gasket that had been fitted would still count as one, new, and come out of the wreck with the mode off
		stockFitted = false;
		setChanged();
		sendData();
		EngineTopology.wearChanged(level, worldPosition);
	}

	/**
	 * A leak or a wreck makes itself seen: a blown gasket puffs smoke, and a head with a rod through it drips oil and smokes. Client side
	 * only, nothing saved, and nothing at all from a head that is sound, so with the wear mode off no head ever does this.
	 */
	@Override
	public void tick() {
		super.tick();
		if (level == null || !level.isClientSide || !(showsBlown() || showsBroken() || showsWarped()))
			return;
		var random = level.random;
		boolean broken = showsBroken();
		if (random.nextInt(broken ? 7 : 5) != 0)
			return;
		double x = worldPosition.getX() + 0.3 + random.nextDouble() * 0.4;
		double y = worldPosition.getY() + 0.6 + random.nextDouble() * 0.3;
		double z = worldPosition.getZ() + 0.3 + random.nextDouble() * 0.4;
		// A broken head mostly smoulders, with only now and then a heavy puff, so the wreck itself stays in view
		level.addParticle(broken && random.nextInt(4) == 0 ? net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE : net.minecraft.core.particles.ParticleTypes.SMOKE, x, y, z, 0, 0.04, 0);
		if (broken && random.nextInt(4) == 0)
			level.addParticle(net.minecraft.core.particles.ParticleTypes.DRIPPING_OBSIDIAN_TEAR, x, y - 0.3, z, 0, 0, 0);
	}

	/** The gasket blows: the engine is looked at again, and the cylinder under this head stops. */
	public void blow() {
		if (blown)
			return;
		blown = true;
		setChanged();
		sendData();
		EngineTopology.wearChanged(level, worldPosition);
	}

	/** Puts a new gasket of {@code tier} in, in place of whatever was there (a blown one included): new, sound and sealed. */
	public ItemStack fit(Wear.Gasket tier) {
		return fit(tier, 0);
	}

	/**
	 * Puts a gasket of {@code tier} that has {@code wear} in, in place of whatever was there (a blown one included), and returns the one that
	 * came out as an item with its wear (a blown one is spent), or an empty stack if the head had none fitted of its own.
	 */
	public ItemStack fit(Wear.Gasket tier, double wear) {
		ItemStack old = gasketStack();
		dropHiddenBreak();
		gasket = tier;
		gasketWear = Wear.clamp(wear);
		stockFitted = true;
		blown = false;
		setChanged();
		sendData();
		// A gasket seals the head whether or not the wear mode is on, so the engine is rebuilt to read it
		EngineTopology.wearChanged(level, worldPosition);
		return old;
	}

	/** Whether a gasket has been fitted to this head as an item (a head that has never had one is read as holding the stock one, which is not). */
	public boolean hasGasketItem() {
		return gasket != Wear.Gasket.STOCK || stockFitted;
	}

	/** The gasket fitted, as an item carrying its wear (a blown one is spent), or an empty stack if none was fitted as an item. */
	public ItemStack gasketStack() {
		return hasGasketItem() ? WearService.withWear(WearService.itemFor(gasket), blown ? 1 : gasketWear) : ItemStack.EMPTY;
	}

	/**
	 * Takes the fitted gasket out. The head is then without one, and stops its cylinder until a gasket is fitted (a gasket-less head is not
	 * a free new one). Returns the gasket as an item with its wear, or an empty stack if there was none fitted as an item.
	 */
	public ItemStack removeGasket() {
		ItemStack out = gasketStack();
		if (out.isEmpty())
			return out;
		gasket = Wear.Gasket.STOCK;
		gasketWear = 0;
		stockFitted = false;
		blown = true;
		setChanged();
		sendData();
		EngineTopology.wearChanged(level, worldPosition);
		return out;
	}

	/**
	 * Sets right light wear of the gasket and the spark plug (see {@link Wear#repairable}). Returns whether anything changed; a blown or
	 * heavily worn one is replaced instead, and nothing flattens a warped head.
	 */
	public boolean service() {
		boolean gasket = !blown && Wear.repairable(gasketWear);
		boolean plug = getBlockState().getValue(CylinderHeadBlock.HAS_SPARK_PLUG) && Wear.repairable(plugWear);
		if (!gasket && !plug)
			return false;
		if (gasket)
			gasketWear = Wear.repaired(gasketWear);
		if (plug)
			plugWear = Wear.repaired(plugWear);
		setChanged();
		sendData();
		return true;
	}

	/** The creative Service Kit: the gasket and plug are new again, and a blow, a break and a warp are undone. Returns whether anything changed. */
	public boolean restore() {
		boolean failure = blown || broken || warped;
		heatSoak = 0;
		if (!failure && gasketWear == 0 && plugWear == 0)
			return false;
		blown = broken = warped = false;
		gasketWear = plugWear = 0;
		setChanged();
		sendData();
		if (failure)
			EngineTopology.wearChanged(level, worldPosition);
		return true;
	}

	@Override
	protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.write(tag, registries, clientPacket);
		if (gasket != Wear.Gasket.STOCK)
			tag.putInt("Gasket", gasket.ordinal());
		if (gasketWear > 0)
			tag.putDouble("GasketWear", gasketWear);
		if (blown)
			tag.putBoolean("Blown", true);
		if (broken)
			tag.putBoolean("Broken", true);
		if (warped)
			tag.putBoolean("Warped", true);
		if (plugWear > 0)
			tag.putDouble("PlugWear", plugWear);
		if (stockFitted && gasket == Wear.Gasket.STOCK)
			tag.putBoolean("StockFitted", true);
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(tag, registries, clientPacket);
		// Absent in a world from before wear existed, where a head has the stock gasket, new
		gasket = tag.contains("Gasket") ? Wear.Gasket.byId(tag.getInt("Gasket")) : Wear.Gasket.STOCK;
		gasketWear = Wear.clamp(tag.getDouble("GasketWear"));
		blown = tag.getBoolean("Blown");
		broken = tag.getBoolean("Broken");
		warped = tag.getBoolean("Warped");
		plugWear = Wear.clamp(tag.getDouble("PlugWear"));
		stockFitted = tag.getBoolean("StockFitted");
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		tank = SmartFluidTankBehaviour.single(this, CAPACITY);
		tank.getPrimaryHandler()
			.setValidator(CEDataMaps::isFuel);
		behaviours.add(tank);

		oil = new SmartFluidTankBehaviour(OIL_TYPE, this, 1, CAPACITY, false);
		oil.getPrimaryHandler()
			.setValidator(CEFluids::isLubricant);
		behaviours.add(oil);

		metering = new ScrollValueBehaviour(CELang.translate("value.fuel_metering")
			.component(), this, new HeadValueBox()) {
			@Override
			public BehaviourType<?> getType() {
				return METERING_TYPE;
			}
		};
		metering.between(MIN_METERING, MAX_METERING);
		metering.value = DEFAULT_METERING;
		metering.withFormatter(v -> v * METERING_STEP + "%");
		// With features.meterAllHeads on, a player's change to this head is copied to the rest of the engine
		metering.withCallback(v -> MeteringFollow.onChanged(level, worldPosition, v));
		behaviours.add(metering);
	}

	/** The share of the usual injector shot this head delivers, 1 being the usual shot. */
	@Override
	public double meteringFraction() {
		return Math.max(MIN_METERING, metering.getValue()) * METERING_STEP / 100d;
	}

	@Override
	public SmartFluidTank tank() {
		return tank.getPrimaryHandler();
	}

	@Override
	public SmartFluidTank oilTank() {
		return oil.getPrimaryHandler();
	}

	public IFluidHandler fluidCapability() {
		if (combined == null)
			combined = new CombinedTankWrapper(tank.getCapability(), oil.getCapability());
		return combined;
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		CELang.translate("goggles.head")
			.forGoggles(tooltip);
		BlockState state = getBlockState();
		// The head has no engine data of its own: it reads the cylinder it closes
		if (LoadedBlocks.blockEntity(level, worldPosition.relative(state.getValue(CylinderHeadBlock.FACING)
			.getOpposite())) instanceof CylinderBlockEntity cylinder && cylinder.status() != null
			&& cylinder.status()
				.fuelCut())
			GoggleLines.add(CELang.translate("goggles.fuel_cut")
				.style(ChatFormatting.GOLD), tooltip, 1);
		if (!state.getValue(CylinderHeadBlock.HAS_INJECTOR))
			GoggleLines.add(CELang.translate("goggles.head.no_injector")
				.style(ChatFormatting.GOLD), tooltip, 1);
		else {
			GoggleLines.add(CELang.translate("goggles.head.metering", Math.round(meteringFraction() * 100))
				.style(ChatFormatting.AQUA), tooltip, 1);
			if (isPlayerSneaking)
				GoggleLines.add(CELang.translate("goggles.head.metering.hint")
					.style(ChatFormatting.DARK_GRAY), tooltip, 2);
			if (state.getValue(CylinderHeadBlock.HAS_ASSIST)) {
				GoggleLines.add(CELang.translate("goggles.head.assist")
					.style(ChatFormatting.AQUA), tooltip, 1);
				if (isPlayerSneaking)
					GoggleLines.add(CELang.translate("goggles.head.assist.hint")
						.style(ChatFormatting.DARK_GRAY), tooltip, 2);
			} else if (isPlayerSneaking)
				GoggleLines.add(CELang.translate("goggles.head.no_assist")
					.style(ChatFormatting.DARK_GRAY), tooltip, 1);
		}
		if (state.getValue(CylinderHeadBlock.HAS_SPARK_PLUG)) {
			GoggleLines.add(CELang.translate("goggles.head.spark_plug")
				.style(ChatFormatting.GRAY), tooltip, 1);
			if (state.getValue(CylinderHeadBlock.HAS_COIL)) {
				GoggleLines.add(CELang.translate("goggles.head.coil")
					.style(ChatFormatting.AQUA), tooltip, 1);
				if (isPlayerSneaking)
					GoggleLines.add(CELang.translate("goggles.head.coil.hint")
						.style(ChatFormatting.DARK_GRAY), tooltip, 2);
			} else if (isPlayerSneaking)
				GoggleLines.add(CELang.translate("goggles.head.no_coil")
					.style(ChatFormatting.DARK_GRAY), tooltip, 1);
		} else if (isPlayerSneaking)
			GoggleLines.add(CELang.translate("goggles.head.no_spark_plug")
				.style(ChatFormatting.DARK_GRAY), tooltip, 1);
		if (Tuning.wearMode && plugWear > 0 && state.getValue(CylinderHeadBlock.HAS_SPARK_PLUG)) {
			GoggleLines.add(CELang.translate("goggles.wear.plug", Math.round(plugWear * 100))
				.style(plugWear >= Wear.FAILURE_FROM ? ChatFormatting.RED : plugWear >= Wear.REPAIRABLE ? ChatFormatting.GOLD : ChatFormatting.GRAY), tooltip, 1);
			if (isPlayerSneaking && plugWear > Wear.REPAIRED_TO)
				GoggleLines.add(CELang.translate(Wear.repairable(plugWear) ? "goggles.wear.light" : "goggles.wear.heavy_plug")
					.style(ChatFormatting.DARK_GRAY), tooltip, 2);
		}
		if (showsWarped())
			GoggleLines.add(CELang.translate("goggles.fault.warped_head")
				.style(ChatFormatting.RED), tooltip, 1);
		if (showsBroken())
			GoggleLines.add(CELang.translate("goggles.wear.head_broken")
				.style(ChatFormatting.RED), tooltip, 1);
		else if (showsBlown())
			GoggleLines.add(CELang.translate("goggles.wear.gasket_blown")
				.style(ChatFormatting.RED), tooltip, 1);
		else if (!Tuning.wearMode) {
			// A fitted gasket still seals with the mode off, so it is named, with no wear figure
			if (hasGasketItem())
				GoggleLines.add(CELang.translate("goggles.gasket_fitted", CELang.translate("goggles.wear.gasket." + gasket.name().toLowerCase(java.util.Locale.ROOT))
						.string())
					.style(ChatFormatting.GRAY), tooltip, 1);
		} else if (gasketWear > 0 || gasket != Wear.Gasket.STOCK) {
			int percent = (int) Math.round(gasketWear * 100);
			GoggleLines.add(CELang.translate("goggles.wear.gasket", CELang.translate("goggles.wear.gasket." + gasket.name().toLowerCase(java.util.Locale.ROOT))
					.string(), percent)
				.style(gasketWear >= Wear.FAILURE_FROM ? ChatFormatting.RED : gasketWear >= Wear.REPAIRABLE ? ChatFormatting.GOLD : ChatFormatting.GRAY),
				tooltip, 1);
			if (isPlayerSneaking && percent >= 5)
				GoggleLines.add(CELang.translate(Wear.repairable(gasketWear) ? "goggles.wear.light" : "goggles.wear.heavy")
					.style(ChatFormatting.DARK_GRAY), tooltip, 2);
		}
		if (oilTank().isEmpty())
			GoggleLines.add(CELang.translate("goggles.head.oil")
				.style(ChatFormatting.GRAY), tooltip, 1);
		containedFluidTooltip(tooltip, isPlayerSneaking, fluidCapability());
		return true;
	}
}
