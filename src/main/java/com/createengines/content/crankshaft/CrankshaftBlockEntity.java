package com.createengines.content.crankshaft;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.createengines.content.RenderRotation;
import com.createengines.engine.EngineController;
import com.createengines.engine.EngineState;
import com.createengines.engine.EngineSummary;
import com.createengines.engine.sim.Thermal;
import com.createengines.engine.topology.EngineLayout;
import com.createengines.engine.topology.EngineMember;
import com.createengines.engine.topology.EngineTopology;
import com.createengines.content.GoggleLines;
import com.createengines.registry.CELang;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
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
	/** +1 if gearing turns this crank the way the engine's primary turns, -1 if the other way. */
	private int sense = 1;
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
			.component(), this, throwAngleBox());
		throwAngle.between(0, 360 / THROW_ANGLE_STEP - 1);
		throwAngle.withFormatter(v -> v * THROW_ANGLE_STEP + "°");
		throwAngle.withCallback(v -> EngineTopology.markDirty(level, worldPosition));
		behaviours.add(throwAngle);
	}

	/** Where the throw angle's value box sits on each side of the block. */
	protected ValueBoxTransform throwAngleBox() {
		return new SideValueBox(14.5f);
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

	@Override
	public void onSpeedChanged(float previousSpeed) {
		super.onSpeedChanged(previousSpeed);
		// Create's blocks never tell the engine scanner they were placed, but a crank line that gearing has just joined to
		// a turning network, or cut from one, starts or stops here: look again at once rather than at the next revalidation
		if (level != null && !level.isClientSide && (previousSpeed == 0) != (getSpeed() == 0))
			EngineTopology.markDirty(level, worldPosition);
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
		setPrimary(worldPosition, 1);
	}

	/** The engine this crankshaft hosts, if it is the primary of a built one. */
	public @Nullable EngineController controller() {
		return controller;
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

	public void setPrimary(BlockPos primary, int sense) {
		if (primary.equals(primaryPos) && sense == this.sense)
			return;
		primaryPos = primary;
		this.sense = sense;
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
	 * move as one. A crank that gearing turns the other way follows the primary's mirror image, so a coupled engine's
	 * banks stay in step with each other.
	 */
	public double rotation(float renderTime) {
		float speed = getTheoreticalSpeed();
		if (primaryPos != null && !primaryPos.equals(worldPosition) && level != null
			&& level.getBlockEntity(primaryPos) instanceof CrankshaftBlockEntity primary
			&& primary.getTheoreticalSpeed() * sense == speed)
			return rotation.follow(renderTime, sense * primary.rotation.at(renderTime, primary.getTheoreticalSpeed()));
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
		tag.putInt("Sense", sense);
		EngineController.Motion motion = captureMotion();
		if (motion != null && !clientPacket) {
			CompoundTag motionTag = new CompoundTag();
			motionTag.putDouble("Angle", motion.angle());
			motionTag.putDouble("Omega", motion.omega());
			motionTag.putInt("Direction", motion.direction());
			motionTag.putDouble("Film", motion.film());
			motionTag.putDouble("Dilution", motion.dilution());
			motionTag.putDouble("Condition", motion.condition());
			motionTag.putDouble("Temperature", motion.temperature());
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
		sense = tag.contains("Sense") && tag.getInt("Sense") < 0 ? -1 : 1;
		if (tag.contains("Motion")) {
			CompoundTag motion = tag.getCompound("Motion");
			savedMotion = new EngineController.Motion(motion.getDouble("Angle"), motion.getDouble("Omega"),
				motion.getInt("Direction"), motion.getDouble("Film"), motion.getDouble("Dilution"),
				// An engine saved before oil went off has new oil, and a block that has had time to settle
				motion.contains("Condition") ? motion.getDouble("Condition") : 1,
				motion.contains("Temperature") ? motion.getDouble("Temperature") : Thermal.NORMAL, null);
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
			addEngineTooltip(tooltip, engine, isPlayerSneaking);
		boolean kinetics = super.addToGoggleTooltip(tooltip, isPlayerSneaking);
		return engine != null || kinetics;
	}

	private static void addEngineTooltip(List<Component> tooltip, EngineSummary engine, boolean isPlayerSneaking) {
		CELang.translate("goggles.engine")
			.forGoggles(tooltip);
		ChatFormatting stateColour = switch (engine.state()) {
			case RUNNING -> ChatFormatting.GREEN;
			case CRANKING -> ChatFormatting.YELLOW;
			case COASTING -> ChatFormatting.GRAY;
			case OVERLOADED -> ChatFormatting.GOLD;
			case STALLED, INCOMPLETE, BROKEN -> ChatFormatting.RED;
		};
		GoggleLines.add(CELang.translate(engine.state()
				.langKey())
			.style(stateColour), tooltip, 1);
		if (engine.state() == EngineState.BROKEN) {
			GoggleLines.add(CELang.translate("goggles.broken.hint")
				.style(ChatFormatting.GRAY), tooltip, 1);
			return;
		}
		if (engine.lines() > 1)
			GoggleLines.add(CELang.translate("goggles.coupled", engine.lines())
				.style(ChatFormatting.GRAY), tooltip, 1);
		if (engine.state() != EngineState.INCOMPLETE) {
			int oil = Math.round(engine.lubrication() * 100);
			GoggleLines.add(CELang.translate("goggles.lubrication", oil)
				.style(oil < 30 ? ChatFormatting.RED : oil < 70 ? ChatFormatting.GOLD : ChatFormatting.GREEN), tooltip, 1);
			if (oil < 30)
				GoggleLines.add(CELang.translate("goggles.lubrication.dry")
					.style(ChatFormatting.GRAY), tooltip, 2);
			int fresh = Math.round(engine.condition() * 100);
			GoggleLines.add(CELang.translate("goggles.oil_condition", fresh)
				.style(fresh < 30 ? ChatFormatting.RED : fresh < 60 ? ChatFormatting.GOLD : ChatFormatting.GREEN), tooltip, 1);
			if (fresh < 30 && isPlayerSneaking)
				GoggleLines.add(CELang.translate("goggles.oil_condition.hint")
					.style(ChatFormatting.DARK_GRAY), tooltip, 2);
			if (engine.filtered())
				GoggleLines.add(CELang.translate("goggles.oil_filtered")
					.style(ChatFormatting.GRAY), tooltip, 2);
			addTemperature(tooltip, engine, isPlayerSneaking);
			int thinned = Math.round(engine.thinning() * 100);
			if (thinned >= 5) {
				GoggleLines.add(CELang.translate("goggles.lubrication.thinned", thinned)
					.style(ChatFormatting.GOLD), tooltip, 1);
				if (isPlayerSneaking)
					GoggleLines.add(CELang.translate("goggles.lubrication.thinned.hint")
						.style(ChatFormatting.DARK_GRAY), tooltip, 2);
			}
		}
		if (engine.cylinders() == 0) {
			GoggleLines.add(CELang.translate("goggles.no_cylinders")
				.style(ChatFormatting.GRAY), tooltip, 1);
			return;
		}
		if (engine.state() != EngineState.STALLED) {
			GoggleLines.add(CELang.translate("goggles.speed")
				.style(ChatFormatting.GRAY), tooltip, 1);
			GoggleLines.add(CELang.translate("goggles.rpm", Math.round(engine.rpm()))
				.style(ChatFormatting.AQUA), tooltip, 2);
			GoggleLines.add(CELang.translate("goggles.available_torque")
				.style(ChatFormatting.GRAY), tooltip, 1);
			GoggleLines.add(CELang.translate("goggles.su_per_rpm", format(engine.availableTorque()))
				.style(ChatFormatting.AQUA), tooltip, 2);
			GoggleLines.add(CELang.translate("goggles.load")
				.style(ChatFormatting.GRAY), tooltip, 1);
			GoggleLines.add(CELang.translate("goggles.su_per_rpm", format(engine.loadTorque()))
				.style(engine.loadTorque() > engine.availableTorque() ? ChatFormatting.GOLD : ChatFormatting.AQUA), tooltip, 2);
			GoggleLines.add(CELang.translate("goggles.fuel_use")
				.style(ChatFormatting.GRAY), tooltip, 1);
			GoggleLines.add(CELang.translate("goggles.mb_per_minute", format(engine.fuelPerMinute()))
				.style(ChatFormatting.AQUA), tooltip, 2);
		}
		GoggleLines.add(CELang.translate("goggles.firing", engine.firing(), engine.cylinders())
			.style(engine.firing() == engine.cylinders() ? ChatFormatting.GREEN : ChatFormatting.GOLD), tooltip, 1);
		float[] intervals = engine.firingIntervals();
		if (intervals.length > 0) {
			StringBuilder text = new StringBuilder();
			boolean even = true;
			for (float interval : intervals)
				even &= Math.abs(interval - intervals[0]) < 1;
			if (even && intervals.length > 2)
				text.append(Math.round(intervals[0]))
					.append("° apart, all even");
			else
				for (float interval : intervals) {
					if (!text.isEmpty())
						text.append(" / ");
					text.append(Math.round(interval))
						.append("°");
				}
			GoggleLines.add(CELang.translate("goggles.firing_intervals", text.toString())
				.style(ChatFormatting.GRAY), tooltip, 1);
		}
		if (engine.state() != EngineState.STALLED) {
			int ripple = Math.round(engine.ripple() * 100);
			GoggleLines.add(CELang.translate("goggles.ripple", ripple)
				.style(ripple > 15 ? ChatFormatting.GOLD : ChatFormatting.GRAY), tooltip, 1);
			int lost = Math.round(engine.roughness() * 100);
			if (lost >= 2)
				GoggleLines.add(CELang.translate("goggles.roughness", lost)
					.style(ChatFormatting.GOLD), tooltip, 1);
		}
		GoggleLines.add(CELang.translate("goggles.inertia", format(engine.inertia()))
			.style(ChatFormatting.DARK_GRAY), tooltip, 1);
		if (engine.starterEngaged())
			GoggleLines.add(CELang.translate("goggles.starter_engaged")
				.style(ChatFormatting.YELLOW), tooltip, 1);
	}

	/** The block's temperature in words, with the coolant it is spending and a note when a pilot shot is lighting the fuel. */
	private static void addTemperature(List<Component> tooltip, EngineSummary engine, boolean isPlayerSneaking) {
		float temperature = engine.temperature();
		String state = temperature < Thermal.COLD_BELOW ? "cold" : temperature < Thermal.COOL_NONE ? "cool" : "warm";
		ChatFormatting colour = switch (state) {
			case "cold" -> ChatFormatting.AQUA;
			case "cool" -> ChatFormatting.GREEN;
			default -> ChatFormatting.GRAY;
		};
		GoggleLines.add(CELang.translate("goggles.temperature." + state)
			.style(colour), tooltip, 1);
		if (engine.coolantRate() > 0)
			GoggleLines.add(CELang.translate("goggles.coolant_use", format(engine.coolantRate()))
				.style(ChatFormatting.GRAY), tooltip, 2);
		else if (isPlayerSneaking && temperature >= Thermal.COOL_NONE)
			GoggleLines.add(CELang.translate("goggles.temperature.hint")
				.style(ChatFormatting.DARK_GRAY), tooltip, 2);
		if (engine.pilot())
			GoggleLines.add(CELang.translate("goggles.pilot_lit")
				.style(ChatFormatting.YELLOW), tooltip, 1);
	}

	public static String format(double value) {
		if (Math.abs(value) >= 100)
			return String.valueOf(Math.round(value));
		return String.format(java.util.Locale.ROOT, "%.1f", value);
	}
}
