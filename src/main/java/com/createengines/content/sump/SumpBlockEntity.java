package com.createengines.content.sump;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.createengines.content.GoggleLines;
import com.createengines.engine.topology.EngineMember;
import com.createengines.registry.CEDataMaps;
import com.createengines.registry.CEFluids;
import com.createengines.registry.CELang;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.simibubi.create.foundation.fluid.SmartFluidTank;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * The service port of an engine: one block against a crankshaft block or a cylinder head that takes everything the
 * engine needs besides air and its main fuel, from any face, so the heads keep their faces for the intake, exhaust and
 * fuel line.
 * <ul>
 * <li><b>Engine Oil</b> goes in the oil tank, which the engine draws its lubrication from.</li>
 * <li><b>Coolant</b> (or plain water) goes in the coolant tank. It is spent carrying heat out of a hot block.</li>
 * <li><b>Pilot fuel</b> (diesel, plant oil and the like) goes in the pilot tank: a compression engine injects a little to
 * light a main fuel it could not light itself.</li>
 * <li><b>Used Oil</b> comes out of the used tank, which only fills while the oil exchange is on. It has to be piped
 * away, and when it is full the exchange stops.</li>
 * </ul>
 * Fluids sort themselves into the right tank, so a single pipe into any face is enough for each of them. Only used oil
 * can be drawn back out, by pipe or by bucket.
 */
public class SumpBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation, EngineMember {

	/** Four times what a cylinder head holds: a pan is the engine's reserve. */
	public static final int CAPACITY = 1000;
	public static final int USED_CAPACITY = 500;
	public static final int COOLANT_CAPACITY = 500;
	public static final int PILOT_CAPACITY = 250;

	/** Oil exchange settings, mB of oil swapped per second while the engine turns: off, trickle, steady, flush. */
	public static final double[] EXCHANGE_RATES = {0, 0.25, 1, 4};

	private static final BehaviourType<SmartFluidTankBehaviour> USED_TYPE = new BehaviourType<>("used_oil_tank");
	private static final BehaviourType<SmartFluidTankBehaviour> COOLANT_TYPE = new BehaviourType<>("coolant_tank");
	private static final BehaviourType<SmartFluidTankBehaviour> PILOT_TYPE = new BehaviourType<>("pilot_tank");
	private static final BehaviourType<ScrollValueBehaviour> EXCHANGE_TYPE = new BehaviourType<>("oil_exchange");

	protected SmartFluidTankBehaviour tank;
	protected SmartFluidTankBehaviour used;
	protected SmartFluidTankBehaviour coolant;
	protected SmartFluidTankBehaviour pilot;
	public ScrollValueBehaviour exchange;
	private @Nullable BlockPos primaryPos;
	private boolean filtered;
	/** Oil swapped in the whole millibuckets the tanks deal in, with the fraction not yet moved carried here. */
	private double exchangeCredit;
	private IFluidHandler handler;

	public SumpBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		tank = SmartFluidTankBehaviour.single(this, CAPACITY);
		tank.getPrimaryHandler()
			.setValidator(CEFluids::isEngineOil);
		behaviours.add(tank);

		used = new SmartFluidTankBehaviour(USED_TYPE, this, 1, USED_CAPACITY, false);
		used.getPrimaryHandler()
			.setValidator(CEFluids::isUsedOil);
		behaviours.add(used);

		coolant = new SmartFluidTankBehaviour(COOLANT_TYPE, this, 1, COOLANT_CAPACITY, false);
		coolant.getPrimaryHandler()
			.setValidator(CEFluids::isCoolant);
		behaviours.add(coolant);

		pilot = new SmartFluidTankBehaviour(PILOT_TYPE, this, 1, PILOT_CAPACITY, false);
		pilot.getPrimaryHandler()
			.setValidator(CEDataMaps::isPilotFuel);
		behaviours.add(pilot);

		exchange = new ScrollValueBehaviour(CELang.translate("value.oil_exchange")
			.component(), this, new SumpValueBox()) {
			@Override
			public BehaviourType<?> getType() {
				return EXCHANGE_TYPE;
			}
		};
		exchange.between(0, EXCHANGE_RATES.length - 1);
		exchange.value = 0;
		exchange.withFormatter(v -> CELang.translate("value.oil_exchange." + v)
			.string());
		behaviours.add(exchange);
	}

	/** The engine oil tank. */
	public SmartFluidTank tank() {
		return tank.getPrimaryHandler();
	}

	public SmartFluidTank usedTank() {
		return used.getPrimaryHandler();
	}

	public SmartFluidTank coolantTank() {
		return coolant.getPrimaryHandler();
	}

	public SmartFluidTank pilotTank() {
		return pilot.getPrimaryHandler();
	}

	/** What pipes and buckets see on every face. */
	public IFluidHandler fluidCapability() {
		if (handler == null)
			handler = new Ports();
		return handler;
	}

	/** Whether an oil filter is fitted in the pan. */
	public boolean filtered() {
		return filtered;
	}

	public void setFiltered(boolean filtered) {
		if (this.filtered == filtered)
			return;
		this.filtered = filtered;
		setChanged();
		sendData();
	}

	/** Oil swapped per second while the engine turns, mB: 0 with the exchange off. */
	public double exchangeRate() {
		int setting = exchange == null ? 0 : Math.clamp(exchange.getValue(), 0, EXCHANGE_RATES.length - 1);
		return EXCHANGE_RATES[setting];
	}

	/**
	 * Swaps oil for the given time: spent oil moves from the oil tank to the used tank, to be piped away, and fresh oil
	 * piped into the oil tank takes its place. It stops while the used tank is full, or the pan is empty. Called by the
	 * engine as it turns.
	 *
	 * @return mB of oil swapped, which the engine counts towards refreshing its oil
	 */
	public double exchange(double seconds) {
		double rate = exchangeRate();
		if (rate <= 0 || seconds <= 0)
			return 0;
		exchangeCredit += rate * seconds;
		int whole = (int) exchangeCredit;
		if (whole <= 0)
			return 0;
		SmartFluidTank oil = tank();
		SmartFluidTank spent = usedTank();
		int movable = Math.min(whole, Math.min(oil.getFluidAmount(), spent.getSpace()));
		if (movable <= 0) {
			// Nothing to swap with, so nothing builds up while the pan is empty or the used oil has nowhere to go
			exchangeCredit = Math.min(exchangeCredit, 1);
			return 0;
		}
		FluidStack taken = oil.drain(movable, IFluidHandler.FluidAction.EXECUTE);
		int moved = taken.getAmount();
		if (moved <= 0)
			return 0;
		spent.fill(new FluidStack(CEFluids.USED_OIL.get()
			.getSource(), moved), IFluidHandler.FluidAction.EXECUTE);
		exchangeCredit -= moved;
		return moved;
	}

	/** Called by the engine that draws on this pan. */
	public void attachToEngine(BlockPos primary) {
		if (primary.equals(primaryPos))
			return;
		primaryPos = primary;
		sendData();
	}

	@Override
	public void detachFromEngine() {
		if (primaryPos == null)
			return;
		primaryPos = null;
		sendData();
	}

	@Override
	protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.write(tag, registries, clientPacket);
		if (primaryPos != null)
			tag.put("Primary", NbtUtils.writeBlockPos(primaryPos));
		tag.putBoolean("Filtered", filtered);
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(tag, registries, clientPacket);
		primaryPos = NbtUtils.readBlockPos(tag, "Primary")
			.orElse(null);
		filtered = tag.getBoolean("Filtered");
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		CELang.translate("goggles.sump")
			.forGoggles(tooltip);
		if (primaryPos == null)
			GoggleLines.add(CELang.translate("goggles.sump.no_engine")
				.style(ChatFormatting.GOLD), tooltip, 1);
		else if (tank().isEmpty())
			GoggleLines.add(CELang.translate("goggles.sump.empty")
				.style(ChatFormatting.RED), tooltip, 1);
		if (filtered)
			GoggleLines.add(CELang.translate("goggles.sump.filter")
				.style(ChatFormatting.GREEN), tooltip, 1);
		else if (isPlayerSneaking)
			GoggleLines.add(CELang.translate("goggles.sump.no_filter")
				.style(ChatFormatting.DARK_GRAY), tooltip, 1);
		double rate = exchangeRate();
		if (rate > 0) {
			GoggleLines.add(CELang.translate("goggles.sump.exchange", rate)
				.style(ChatFormatting.AQUA), tooltip, 1);
			if (usedTank().getSpace() <= 0)
				GoggleLines.add(CELang.translate("goggles.sump.used_full")
					.style(ChatFormatting.RED), tooltip, 2);
			else if (isPlayerSneaking)
				GoggleLines.add(CELang.translate("goggles.sump.exchange.hint")
					.style(ChatFormatting.DARK_GRAY), tooltip, 2);
		}
		containedFluidTooltip(tooltip, isPlayerSneaking, fluidCapability());
		return true;
	}

	/**
	 * Every face of the pan, to pipes and buckets. A fluid goes to whichever tank takes it; only used oil comes back out,
	 * so a pump on the pan can never empty the oil or the coolant the engine is living on.
	 */
	private final class Ports implements IFluidHandler {

		@Override
		public int getTanks() {
			return 4;
		}

		private SmartFluidTank at(int index) {
			return switch (index) {
				case 0 -> tank();
				case 1 -> usedTank();
				case 2 -> coolantTank();
				default -> pilotTank();
			};
		}

		@Override
		public FluidStack getFluidInTank(int index) {
			return at(index).getFluid();
		}

		@Override
		public int getTankCapacity(int index) {
			return at(index).getCapacity();
		}

		@Override
		public boolean isFluidValid(int index, FluidStack stack) {
			// The used tank is only filled from inside
			return index != 1 && at(index).isFluidValid(stack);
		}

		@Override
		public int fill(FluidStack resource, FluidAction action) {
			if (resource.isEmpty())
				return 0;
			for (int i : new int[] {0, 2, 3})
				if (at(i).isFluidValid(resource))
					return at(i).fill(resource, action);
			return 0;
		}

		@Override
		public FluidStack drain(FluidStack resource, FluidAction action) {
			return CEFluids.isUsedOil(resource) ? usedTank().drain(resource, action) : FluidStack.EMPTY;
		}

		@Override
		public FluidStack drain(int maxDrain, FluidAction action) {
			return usedTank().drain(maxDrain, action);
		}
	}
}
