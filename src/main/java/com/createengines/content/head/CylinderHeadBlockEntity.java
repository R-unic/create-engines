package com.createengines.content.head;

import java.util.List;

import com.createengines.registry.CEDataMaps;
import com.createengines.registry.CEFluids;
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
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * Holds this head's share of the engine's fuel gallery and oil gallery. Fill it with Create pipes from any face:
 * fuel goes to the fuel tank and engine oil to the oil tank, and each is shared with every head touching this one.
 * <p>
 * Its injector is metered by hand with a value box: how much fuel each shot delivers, as a share of the usual shot.
 * Nothing matches it to the air the cylinder breathes, so a setting that suits one speed runs rich above it and lean
 * below it.
 */
public class CylinderHeadBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

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

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		tank = SmartFluidTankBehaviour.single(this, CAPACITY);
		tank.getPrimaryHandler()
			.setValidator(CEDataMaps::isFuel);
		behaviours.add(tank);

		oil = new SmartFluidTankBehaviour(OIL_TYPE, this, 1, CAPACITY, false);
		oil.getPrimaryHandler()
			.setValidator(CEFluids::isEngineOil);
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
		behaviours.add(metering);
	}

	/** The share of the usual injector shot this head delivers, 1 being the usual shot. */
	public double meteringFraction() {
		return Math.max(MIN_METERING, metering.getValue()) * METERING_STEP / 100d;
	}

	public SmartFluidTank tank() {
		return tank.getPrimaryHandler();
	}

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
		if (!state.getValue(CylinderHeadBlock.HAS_INJECTOR))
			CELang.translate("goggles.head.no_injector")
				.style(ChatFormatting.GOLD)
				.forGoggles(tooltip, 1);
		else {
			CELang.translate("goggles.head.metering", Math.round(meteringFraction() * 100))
				.style(ChatFormatting.AQUA)
				.forGoggles(tooltip, 1);
			if (isPlayerSneaking)
				CELang.translate("goggles.head.metering.hint")
					.style(ChatFormatting.DARK_GRAY)
					.forGoggles(tooltip, 2);
		}
		if (state.getValue(CylinderHeadBlock.HAS_SPARK_PLUG))
			CELang.translate("goggles.head.spark_plug")
				.style(ChatFormatting.GRAY)
				.forGoggles(tooltip, 1);
		else if (isPlayerSneaking)
			CELang.translate("goggles.head.no_spark_plug")
				.style(ChatFormatting.DARK_GRAY)
				.forGoggles(tooltip, 1);
		if (oilTank().isEmpty())
			CELang.translate("goggles.head.oil")
				.style(ChatFormatting.GRAY)
				.forGoggles(tooltip, 1);
		containedFluidTooltip(tooltip, isPlayerSneaking, fluidCapability());
		return true;
	}
}
