package com.createengines.content.head;

import java.util.List;

import com.createengines.registry.CEDataMaps;
import com.createengines.registry.CELang;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import com.simibubi.create.foundation.fluid.SmartFluidTank;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** Holds this head's share of the fuel gallery. Fill it with Create pipes from any face. */
public class CylinderHeadBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

	public static final int CAPACITY = 250;

	protected SmartFluidTankBehaviour tank;

	public CylinderHeadBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		tank = SmartFluidTankBehaviour.single(this, CAPACITY);
		tank.getPrimaryHandler()
			.setValidator(CEDataMaps::isFuel);
		behaviours.add(tank);
	}

	public SmartFluidTank tank() {
		return tank.getPrimaryHandler();
	}

	public IFluidHandler fluidCapability() {
		return tank.getCapability();
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		CELang.translate("goggles.head")
			.forGoggles(tooltip);
		if (!getBlockState().getValue(CylinderHeadBlock.HAS_INJECTOR))
			CELang.translate("goggles.head.no_injector")
				.style(ChatFormatting.GOLD)
				.forGoggles(tooltip, 1);
		containedFluidTooltip(tooltip, isPlayerSneaking, fluidCapability());
		return true;
	}
}
