package com.createengines.content.flywheel;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.createengines.config.CEConfig;
import com.createengines.content.GoggleLines;
import com.createengines.engine.sim.SpringPack;
import com.createengines.engine.topology.EngineTopology;
import com.createengines.registry.CELang;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;

import net.createmod.catnip.math.VecHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * The disc's shaft: it only turns, and the engine it sits on counts its mass. A Dual-Mass Flywheel also has a spring-pack
 * dial (see {@link SpringPack}) that sets where its spring pack resonates and how heavy its secondary mass is.
 */
public class CastFlywheelBlockEntity extends KineticBlockEntity {

	private static final BehaviourType<ScrollValueBehaviour> SPRING_PACK_TYPE = new BehaviourType<>("spring_pack");

	/** Client only: the crankshaft this flywheel was last found to sit on, so the drawing need not look for it each frame. */
	public @org.jetbrains.annotations.Nullable BlockPos crankPos;

	/** A Dual-Mass Flywheel's spring-pack dial; null on any other flywheel. */
	public @Nullable ScrollValueBehaviour springPack;

	public CastFlywheelBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		super.addBehaviours(behaviours);
		if (!(getBlockState().getBlock() instanceof DualMassFlywheelBlock))
			return;
		springPack = new ScrollValueBehaviour(CELang.translate("value.spring_pack")
			.component(), this, new ValueBox()) {
			@Override
			public BehaviourType<?> getType() {
				return SPRING_PACK_TYPE;
			}

			@Override
			public void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
				tag.putInt("SpringPackSetting", value);
			}

			@Override
			public void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
				// A flywheel saved before the dial has no key and keeps the middle setting; a damaged one is held to the dial's range
				value = tag.contains("SpringPackSetting") ? SpringPack.clamp(tag.getInt("SpringPackSetting")) : SpringPack.DEFAULT_SETTING;
			}
		};
		springPack.between(SpringPack.MIN_SETTING, SpringPack.MAX_SETTING);
		springPack.value = SpringPack.DEFAULT_SETTING;
		springPack.withFormatter(CastFlywheelBlockEntity::settingName);
		// The engine's masses and resonance are read from the dial when it is scanned
		springPack.withCallback(v -> EngineTopology.markDirty(level, worldPosition));
		behaviours.add(springPack);
	}

	/** The dial's setting, held to its range; the middle one on a flywheel with no dial. */
	public int springPackSetting() {
		return springPack == null ? SpringPack.DEFAULT_SETTING : SpringPack.clamp(springPack.getValue());
	}

	/** What the server's secondary mass is multiplied by for this flywheel. */
	public double massFactor() {
		return SpringPack.massFactor(springPackSetting());
	}

	/** What the server's resonance is multiplied by for this flywheel. */
	public double resonanceFactor() {
		return SpringPack.resonanceFactor(springPackSetting());
	}

	/** How a setting reads on the dial and in the goggles: the middle one is "Standard", the rest count steps from it. */
	private static String settingName(int setting) {
		int steps = SpringPack.clamp(setting) - SpringPack.DEFAULT_SETTING;
		if (steps == 0)
			return CELang.translate("value.spring_pack.standard")
				.string();
		return CELang.translate(steps < 0 ? "value.spring_pack.soft" : "value.spring_pack.stiff", Math.abs(steps))
			.string();
	}

	/** A Dual-Mass Flywheel's wheel is as wide as Create's, which runs well past its block: it is drawn while any of it is in view. */
	@Override
	protected net.minecraft.world.phys.AABB createRenderBoundingBox() {
		if (getBlockState().getBlock() instanceof DualMassFlywheelBlock)
			return new net.minecraft.world.phys.AABB(worldPosition).inflate(1);
		return super.createRenderBoundingBox();
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		if (springPack != null) {
			CELang.translate("goggles.spring_pack")
				.forGoggles(tooltip);
			GoggleLines.add(CELang.translate("goggles.spring_pack.setting", settingName(springPackSetting()))
				.style(ChatFormatting.GRAY), tooltip, 1);
			double resonance = CEConfig.DUAL_MASS_RESONANCE.get() * resonanceFactor();
			double secondary = CEConfig.DUAL_MASS_SECONDARY_INERTIA.get() * massFactor();
			GoggleLines.add(CELang.translate("goggles.spring_pack.figures", Math.round(resonance),
					String.format(java.util.Locale.ROOT, "%.2f", secondary))
				.style(ChatFormatting.DARK_GRAY), tooltip, 1);
		}
		super.addToGoggleTooltip(tooltip, isPlayerSneaking);
		return true;
	}

	/**
	 * A value box on each side face, held out past the wheel's rim. The wheel is about 44 pixels across, so its rim stands 14
	 * pixels beyond the block's side (30 against the face at 16) and a box on the face is inside it. The box floats clear of the
	 * rim instead; because a ray can only hit the block's own outline, a hit anywhere on that side face counts as a hit on the box.
	 */
	private static class ValueBox extends ValueBoxTransform.Sided {

		/** How far out from the block's centre the box sits, in pixels: the rim reaches 30, so this clears it. */
		private static final float REACH = 33;

		@Override
		protected Vec3 getSouthLocation() {
			return VecHelper.voxelSpace(8, 8, REACH);
		}

		@Override
		protected boolean isSideActive(BlockState state, Direction direction) {
			return direction.getAxis() != state.getValue(BlockStateProperties.AXIS);
		}

		@Override
		public boolean testHit(net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockState state, Vec3 localHit) {
			Direction side = getSide();
			return isSideActive(state, side) && ownsHit(side, state.getValue(BlockStateProperties.AXIS), localHit);
		}
	}

	/**
	 * Whether a hit on the block, in block-local coordinates, belongs to the box on {@code side} of a flywheel whose shaft runs along
	 * {@code shaft}. Seen from a shallow angle the ray strikes the outline's end rather than its side, so a hit is shared out by where
	 * it lies round the shaft: each side takes the quarter of the block it faces.
	 */
	public static boolean ownsHit(Direction side, Direction.Axis shaft, Vec3 localHit) {
		if (side.getAxis() == shaft)
			return false;
		double[] offset = new double[3];
		for (Direction.Axis axis : Direction.Axis.values())
			offset[axis.ordinal()] = axis == shaft ? 0 : axis.choose(localHit.x, localHit.y, localHit.z) - 0.5;
		double mine = offset[side.getAxis()
			.ordinal()] * side.getAxisDirection()
				.getStep();
		for (double other : offset)
			if (Math.abs(other) > mine + 1e-9)
				return false;
		return mine > 0;
	}
}
