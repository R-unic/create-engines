package com.createengines.content.airflow;

import java.util.List;

import com.createengines.engine.sim.Throttle;
import com.createengines.content.GoggleLines;
import com.createengines.registry.CELang;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;

import net.createmod.catnip.math.VecHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The valve of a {@link ThrottleBlock}. It holds the idle stop (how far open it sits with no signal) and works out
 * how open it is right now; the engine reads that every tick.
 */
public class ThrottleBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

	/** The idle stop is set in steps of this many percent, from one step to fully open. */
	public static final int IDLE_STEP = 5;
	public static final int MIN_IDLE = 1;
	public static final int MAX_IDLE = 20;
	public static final int DEFAULT_IDLE = 4;
	private static final BehaviourType<ScrollValueBehaviour> IDLE_TYPE = new BehaviourType<>("throttle_idle");

	public ScrollValueBehaviour idle;

	public ThrottleBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		idle = new ScrollValueBehaviour(CELang.translate("value.throttle_idle")
			.component(), this, new ValueBox()) {
			@Override
			public BehaviourType<?> getType() {
				return IDLE_TYPE;
			}
		};
		idle.between(MIN_IDLE, MAX_IDLE);
		idle.value = DEFAULT_IDLE;
		idle.withFormatter(v -> v * IDLE_STEP + "%");
		behaviours.add(idle);
	}

	/** How far open the valve rests with no signal: 0.05 (a crack) to 1 (no throttling at all). */
	public double idleFraction() {
		return Math.max(MIN_IDLE, idle.getValue()) * IDLE_STEP / 100d;
	}

	/** How far open the valve is now, from the idle stop at no signal to 1 at full redstone strength. */
	public double opening() {
		return Throttle.opening(idleFraction(), level == null ? 0 : level.getBestNeighborSignal(worldPosition));
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		CELang.translate("goggles.throttle")
			.forGoggles(tooltip);
		GoggleLines.add(CELang.translate("goggles.throttle.open", Math.round(opening() * 100))
			.style(ChatFormatting.AQUA), tooltip, 1);
		if (isPlayerSneaking)
			GoggleLines.add(CELang.translate("goggles.throttle.hint")
				.style(ChatFormatting.DARK_GRAY), tooltip, 2);
		return true;
	}

	/** A value box on any face that no pipe leaves through. */
	private static class ValueBox extends ValueBoxTransform.Sided {

		@Override
		protected Vec3 getSouthLocation() {
			return VecHelper.voxelSpace(8, 8, 16);
		}

		@Override
		protected boolean isSideActive(BlockState state, Direction direction) {
			return !state.getValue(ManifoldBlock.CONNECTIONS.get(direction));
		}
	}
}
