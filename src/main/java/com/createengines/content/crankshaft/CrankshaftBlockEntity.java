package com.createengines.content.crankshaft;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.createengines.content.RenderRotation;
import com.createengines.engine.EngineController;
import com.createengines.engine.EngineState;
import com.createengines.engine.EngineSummary;
import com.createengines.engine.topology.EngineLayout;
import com.createengines.engine.topology.EngineMember;
import com.createengines.engine.topology.EngineTopology;
import com.createengines.registry.CELang;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Every crankshaft block is a Create kinetic block, so rotation propagates through the crank and anything attached
 * to its ends. One of them per engine, the primary (lowest along the axis), is the engine's generator and runs its
 * {@link EngineController}. The others just carry rotation and show the engine's figures.
 */
public class CrankshaftBlockEntity extends GeneratingKineticBlockEntity implements EngineMember {

	public static final int THROW_ANGLE_STEP = 15;

	public ScrollValueBehaviour throwAngle;

	private float publishedSpeed;
	private float publishedCapacity;
	private @Nullable BlockPos primaryPos;
	private @Nullable EngineController controller;
	private @Nullable EngineController.Motion savedMotion;
	private @Nullable EngineSummary summary;
	private final RenderRotation rotation = new RenderRotation();

	public CrankshaftBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		super.addBehaviours(behaviours);
		if (!(getBlockState().getBlock() instanceof CrankshaftBlock crank) || !crank.isThrow())
			return;
		throwAngle = new ScrollValueBehaviour(CELang.translate("value.throw_angle")
			.component(), this, new SideValueBox(14.5f));
		throwAngle.between(0, 360 / THROW_ANGLE_STEP - 1);
		throwAngle.withFormatter(v -> v * THROW_ANGLE_STEP + "°");
		throwAngle.withCallback(v -> EngineTopology.markDirty(level, worldPosition));
		behaviours.add(throwAngle);
	}

	/** Angle of this throw's crank pin, in degrees. Zero for plain journals. */
	public double throwAngle() {
		return throwAngle == null ? 0 : throwAngle.getValue() * THROW_ANGLE_STEP;
	}

	@Override
	public void initialize() {
		super.initialize();
		EngineTopology.markDirty(level, worldPosition);
	}

	@Override
	public void tick() {
		super.tick();
		if (level != null && !level.isClientSide && controller != null)
			controller.tick();
	}

	// Engine hosting

	public void adoptLayout(EngineLayout layout, @Nullable EngineController.Motion carried) {
		if (controller != null && controller.layout()
			.equals(layout))
			return;
		EngineController.Motion motion = carried != null ? carried : controller != null ? controller.motion()
			: savedMotion;
		savedMotion = null;
		controller = new EngineController(this, layout, motion);
		setPrimary(worldPosition);
	}

	public @Nullable EngineController.Motion captureMotion() {
		return controller != null ? controller.motion() : savedMotion;
	}

	public void releaseEngine() {
		if (controller != null)
			controller.shutDown();
		controller = null;
		savedMotion = null;
		summary = null;
		sendData();
	}

	@Override
	public void detachFromEngine() {
		if (controller != null)
			releaseEngine();
		if (primaryPos != null) {
			primaryPos = null;
			sendData();
		}
	}

	public void setPrimary(BlockPos primary) {
		if (primary.equals(primaryPos))
			return;
		primaryPos = primary;
		sendData();
	}

	public void updateSummary(EngineSummary next) {
		if (summary != null && !next.differsFrom(summary))
			return;
		summary = next;
		sendData();
	}

	public void publishSpeed(float speed) {
		if (speed == publishedSpeed)
			return;
		publishedSpeed = speed;
		updateGeneratedRotation();
	}

	public void publishCapacity(float capacity) {
		if (capacity == publishedCapacity)
			return;
		publishedCapacity = capacity;
		if (hasNetwork() && publishedSpeed != 0)
			notifyStressCapacityChange(calculateAddedStressCapacity());
	}

	public float publishedCapacity() {
		return publishedCapacity;
	}

	/** Total stress (SU) the kinetic network this crank is in is asking for. */
	public float networkStress() {
		return stress;
	}

	@Override
	public float getGeneratedSpeed() {
		return publishedSpeed;
	}

	@Override
	public float calculateAddedStressCapacity() {
		lastCapacityProvided = publishedCapacity;
		return publishedCapacity;
	}

	@Override
	public float calculateStressApplied() {
		lastStressApplied = 0;
		return 0;
	}

	/**
	 * How far the crank has turned for drawing, in degrees (not wrapped, and before this block's throw angle). Every
	 * block of an engine's crankshaft takes it from the primary, so that the whole crank, its pistons and its cams
	 * move as one.
	 */
	public double rotation(float renderTime) {
		float speed = getTheoreticalSpeed();
		if (primaryPos != null && !primaryPos.equals(worldPosition) && level != null
			&& level.getBlockEntity(primaryPos) instanceof CrankshaftBlockEntity primary
			&& primary.getTheoreticalSpeed() == speed)
			return rotation.follow(renderTime, primary.rotation.at(renderTime, speed));
		return rotation.at(renderTime, speed);
	}

	public @Nullable BlockPos primaryPos() {
		return primaryPos;
	}

	public @Nullable EngineSummary summary() {
		return summary;
	}

	// Sync

	@Override
	protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		tag.putFloat("EngineSpeed", publishedSpeed);
		tag.putFloat("EngineCapacity", publishedCapacity);
		if (primaryPos != null)
			tag.put("Primary", NbtUtils.writeBlockPos(primaryPos));
		EngineController.Motion motion = captureMotion();
		if (motion != null && !clientPacket) {
			CompoundTag motionTag = new CompoundTag();
			motionTag.putDouble("Angle", motion.angle());
			motionTag.putDouble("Omega", motion.omega());
			motionTag.putInt("Direction", motion.direction());
			tag.put("Motion", motionTag);
		}
		if (clientPacket && summary != null)
			tag.put("Summary", summary.write());
		super.write(tag, registries, clientPacket);
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		publishedSpeed = tag.getFloat("EngineSpeed");
		publishedCapacity = tag.getFloat("EngineCapacity");
		primaryPos = NbtUtils.readBlockPos(tag, "Primary")
			.orElse(null);
		if (tag.contains("Motion")) {
			CompoundTag motion = tag.getCompound("Motion");
			savedMotion = new EngineController.Motion(motion.getDouble("Angle"), motion.getDouble("Omega"),
				motion.getInt("Direction"), null);
		}
		if (clientPacket)
			summary = tag.contains("Summary") ? EngineSummary.read(tag.getCompound("Summary")) : null;
		super.read(tag, registries, clientPacket);
	}

	// Goggles

	private @Nullable EngineSummary engineSummary() {
		if (summary != null)
			return summary;
		if (primaryPos != null && level != null && level.getBlockEntity(primaryPos) instanceof CrankshaftBlockEntity p)
			return p.summary;
		return null;
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		EngineSummary engine = engineSummary();
		if (engine != null)
			addEngineTooltip(tooltip, engine);
		boolean kinetics = super.addToGoggleTooltip(tooltip, isPlayerSneaking);
		return engine != null || kinetics;
	}

	private static void addEngineTooltip(List<Component> tooltip, EngineSummary engine) {
		CELang.translate("goggles.engine")
			.forGoggles(tooltip);
		ChatFormatting stateColour = switch (engine.state()) {
			case RUNNING -> ChatFormatting.GREEN;
			case CRANKING -> ChatFormatting.YELLOW;
			case OVERLOADED -> ChatFormatting.GOLD;
			case STALLED, INCOMPLETE -> ChatFormatting.RED;
		};
		CELang.translate(engine.state()
				.langKey())
			.style(stateColour)
			.forGoggles(tooltip, 1);
		if (engine.cylinders() == 0) {
			CELang.translate("goggles.no_cylinders")
				.style(ChatFormatting.GRAY)
				.forGoggles(tooltip, 1);
			return;
		}
		if (engine.state() != EngineState.STALLED) {
			CELang.translate("goggles.speed")
				.style(ChatFormatting.GRAY)
				.forGoggles(tooltip, 1);
			CELang.translate("goggles.rpm", Math.round(engine.rpm()))
				.style(ChatFormatting.AQUA)
				.forGoggles(tooltip, 2);
			CELang.translate("goggles.available_torque")
				.style(ChatFormatting.GRAY)
				.forGoggles(tooltip, 1);
			CELang.translate("goggles.su_per_rpm", format(engine.availableTorque()))
				.style(ChatFormatting.AQUA)
				.forGoggles(tooltip, 2);
			CELang.translate("goggles.load")
				.style(ChatFormatting.GRAY)
				.forGoggles(tooltip, 1);
			CELang.translate("goggles.su_per_rpm", format(engine.loadTorque()))
				.style(engine.loadTorque() > engine.availableTorque() ? ChatFormatting.GOLD : ChatFormatting.AQUA)
				.forGoggles(tooltip, 2);
			CELang.translate("goggles.fuel_use")
				.style(ChatFormatting.GRAY)
				.forGoggles(tooltip, 1);
			CELang.translate("goggles.mb_per_minute", format(engine.fuelPerMinute()))
				.style(ChatFormatting.AQUA)
				.forGoggles(tooltip, 2);
		}
		CELang.translate("goggles.firing", engine.firing(), engine.cylinders())
			.style(engine.firing() == engine.cylinders() ? ChatFormatting.GREEN : ChatFormatting.GOLD)
			.forGoggles(tooltip, 1);
		float[] intervals = engine.firingIntervals();
		if (intervals.length > 0) {
			StringBuilder text = new StringBuilder();
			for (float interval : intervals) {
				if (!text.isEmpty())
					text.append(" / ");
				text.append(Math.round(interval))
					.append("°");
			}
			CELang.translate("goggles.firing_intervals", text.toString())
				.style(ChatFormatting.GRAY)
				.forGoggles(tooltip, 1);
		}
		if (engine.state() != EngineState.STALLED) {
			int ripple = Math.round(engine.ripple() * 100);
			CELang.translate("goggles.ripple", ripple)
				.style(ripple > 15 ? ChatFormatting.GOLD : ChatFormatting.GRAY)
				.forGoggles(tooltip, 1);
		}
		CELang.translate("goggles.inertia", format(engine.inertia()))
			.style(ChatFormatting.DARK_GRAY)
			.forGoggles(tooltip, 1);
		if (engine.starterEngaged())
			CELang.translate("goggles.starter_engaged")
				.style(ChatFormatting.YELLOW)
				.forGoggles(tooltip, 1);
	}

	public static String format(double value) {
		if (Math.abs(value) >= 100)
			return String.valueOf(Math.round(value));
		return String.format(java.util.Locale.ROOT, "%.1f", value);
	}
}
