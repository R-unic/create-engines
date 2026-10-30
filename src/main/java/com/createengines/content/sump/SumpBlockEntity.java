package com.createengines.content.sump;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.createengines.engine.topology.EngineMember;
import com.createengines.registry.CEFluids;
import com.createengines.content.GoggleLines;
import com.createengines.registry.CELang;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import com.simibubi.create.foundation.fluid.SmartFluidTank;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** The oil held by a {@link SumpBlock}, which the engine it touches draws on as it turns. */
public class SumpBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation, EngineMember {

	/** Four times what a cylinder head holds: a pan is the engine's reserve. */
	public static final int CAPACITY = 1000;

	protected SmartFluidTankBehaviour tank;
	private @Nullable BlockPos primaryPos;

	public SumpBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		tank = SmartFluidTankBehaviour.single(this, CAPACITY);
		tank.getPrimaryHandler()
			.setValidator(CEFluids::isEngineOil);
		behaviours.add(tank);
	}

	public SmartFluidTank tank() {
		return tank.getPrimaryHandler();
	}

	public IFluidHandler fluidCapability() {
		return tank.getCapability();
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
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(tag, registries, clientPacket);
		primaryPos = NbtUtils.readBlockPos(tag, "Primary")
			.orElse(null);
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
		containedFluidTooltip(tooltip, isPlayerSneaking, fluidCapability());
		return true;
	}
}
