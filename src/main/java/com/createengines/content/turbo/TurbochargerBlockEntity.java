package com.createengines.content.turbo;

import java.util.List;
import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import com.createengines.config.CEClientConfig;
import com.createengines.content.EffectRate;
import com.createengines.content.EngineNoteSound;
import com.createengines.content.LoopSource;
import com.createengines.content.MachineLoopSound;
import com.createengines.content.effects.BlowOffCrowd;
import com.createengines.content.effects.EngineEffects;
import com.createengines.content.effects.GasParticleOptions;
import com.createengines.content.effects.VentJet;
import com.createengines.engine.Signals;
import com.createengines.engine.TurboStatus;
import com.createengines.engine.sim.Turbocharger;
import com.createengines.engine.topology.EngineMember;
import com.createengines.content.GoggleLines;
import com.createengines.registry.CELang;
import com.createengines.registry.CESounds;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;

import net.createmod.catnip.math.VecHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import com.createengines.engine.sim.TurboWear;
import com.createengines.engine.sim.Wear;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The wastegate setting of a {@link TurbochargerBlock}, and what the engine tells it about the wheel. The spool and
 * boost live in the engine's simulation; this holds the last figures it sent, for goggles and for the wheel to turn by.
 */
public class TurbochargerBlockEntity extends SmartBlockEntity
	implements IHaveGoggleInformation, EngineMember, LoopSource {

	/** The boost limit is set in steps of this many tenths of a bar. */
	public static final int LIMIT_STEP = 1;
	public static final int MIN_LIMIT = 1;
	public static final int MAX_LIMIT = (int) Math.round(Turbocharger.MAX_LIMIT * 10);
	public static final int DEFAULT_LIMIT = (int) Math.round(Turbocharger.DEFAULT_LIMIT * 10);
	private static final BehaviourType<ScrollValueBehaviour> LIMIT_TYPE = new BehaviourType<>("turbo_limit");
	private static final BehaviourType<ScrollValueBehaviour> ANTI_LAG_TYPE = new BehaviourType<>("turbo_anti_lag");
	private static final String ANTI_LAG_KEY = "AntiLag";
	/** The boost limit keeps Create's default of 0 for its network id. */
	private static final int ANTI_LAG_NET_ID = 1;
	/** Wheel degrees turned per tick at design speed. */
	private static final float WHEEL_DEGREES_PER_TICK = 60;

	/** Client only: the blow-offs and the wastegate chuffs heard lately, so many turbos together do not add up. */
	private static final BlowOffCrowd BLOW_OFFS = new BlowOffCrowd();
	private static final BlowOffCrowd CHUFFS = new BlowOffCrowd();

	public ScrollValueBehaviour limit;
	/** Anti-lag, 0 (off) to {@link Turbocharger#MAX_ANTI_LAG}. */
	public ScrollValueBehaviour antiLag;

	private @Nullable TurboStatus status;
	/** The primary crankshaft of the engine that sent it, so that engine's note can carry the whistle. Synced, never saved. */
	private @Nullable BlockPos engine;
	private float wheelAngle;
	private float wheelSpeed;
	/** Client only: whether the wastegate was being held open by redstone at the last tick, and the best boost lately. */
	private boolean wasVenting;
	private boolean wasRegulating;
	private final EffectRate chuffRate = new EffectRate(40);
	/** Client only: whether the engine was off the throttle at the last tick; true while there is no status to go on. */
	private boolean wasLifted = true;
	private float heldBoost;
	/** Client only: the blow-off jet still to be thrown, in ticks, out of how many it started with, and the boost it carries. */
	private int ventLeft;
	private int ventTotal;
	private float ventBoost;
	private final EffectRate popRate = new EffectRate(EffectRate.BANG_INTERVAL * 2);
	/** The valve opens at most about once a second on a throttle lift, however the driver blips it. */
	private final EffectRate liftRate = new EffectRate(20);

	public TurbochargerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		limit = new ScrollValueBehaviour(CELang.translate("value.boost_limit")
			.component(), this, new ValueBox(12)) {
			@Override
			public BehaviourType<?> getType() {
				return LIMIT_TYPE;
			}
		};
		limit.between(MIN_LIMIT, MAX_LIMIT);
		limit.value = DEFAULT_LIMIT;
		limit.withFormatter(v -> String.format(Locale.ROOT, "%.1f bar", v / 10f));
		behaviours.add(limit);

		antiLag = new ScrollValueBehaviour(CELang.translate("value.anti_lag")
			.component(), this, new ValueBox(4)) {
			@Override
			public BehaviourType<?> getType() {
				return ANTI_LAG_TYPE;
			}

			/**
			 * Create saves every scroll value under the same key, and the boost limit is one already: this one keeps
			 * its own, or the two would overwrite each other whenever the block is saved or sent to a client.
			 */
			@Override
			public void write(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
				CompoundTag scratch = new CompoundTag();
				super.write(scratch, registries, clientPacket);
				nbt.putInt(ANTI_LAG_KEY, scratch.getInt("ScrollValue"));
			}

			@Override
			public void read(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
				CompoundTag scratch = new CompoundTag();
				scratch.putInt("ScrollValue", nbt.getInt(ANTI_LAG_KEY));
				super.read(scratch, registries, clientPacket);
			}

			/**
			 * Create tells the value boxes of one block apart by this number, and a setting sent from the player lands on the
			 * first behaviour that matches it. Both scroll values here said 0, so a setting made on the anti-lag box went to
			 * the boost limit, which is first in line: the two seemed tied together.
			 */
			@Override
			public int netId() {
				return ANTI_LAG_NET_ID;
			}

			/** Create's clipboard copies a block's settings under this key, so each value needs its own. */
			@Override
			public String getClipboardKey() {
				return ANTI_LAG_KEY;
			}
		};
		antiLag.between(0, Turbocharger.MAX_ANTI_LAG);
		antiLag.value = 0;
		antiLag.withFormatter(v -> v == 0 ? "Off" : "Level " + v);
		behaviours.add(antiLag);
	}

	/** The anti-lag setting, 0 (off) to {@link Turbocharger#MAX_ANTI_LAG}. */
	public int antiLagLevel() {
		return Math.clamp(antiLag.getValue(), 0, Turbocharger.MAX_ANTI_LAG);
	}

	/** The boost the wastegate is set to hold, atmospheres. */
	public double boostLimit() {
		return Math.max(MIN_LIMIT, limit.getValue()) / 10d;
	}

	/** A redstone signal opens the wastegate fully, dumping the boost like a blow-off valve. */
	public boolean dumping() {
		return level != null && Signals.hasNeighbour(level, worldPosition);
	}

	public @Nullable TurboStatus status() {
		return status;
	}

	public void updateStatus(BlockPos primary, TurboStatus next) {
		if (!next.differsFrom(status) && primary.equals(engine))
			return;
		engine = primary.immutable();
		status = next;
		sendData();
	}

	@Override
	public void detachFromEngine() {
		if (status == null)
			return;
		status = null;
		engine = null;
		sendData();
	}

	@Override
	public void tick() {
		super.tick();
		if (level != null && level.isClientSide) {
			float target = status == null ? 0 : status.spool() * WHEEL_DEGREES_PER_TICK;
			wheelSpeed += (target - wheelSpeed) * 0.15f;
			wheelAngle = (wheelAngle + wheelSpeed) % 360;
			if (status != null && status.antiLag() && level.random.nextInt(2) == 0)
				antiLagPop();
			if (loopLevel() > 0.02f && !EngineNoteSound.carryTurbo(engine, this, worldPosition, 0.09f))
				MachineLoopSound.ensurePlaying(this, worldPosition, CESounds.TURBO_WHISTLE.get(), 0.09f, this);
			blowOff();
			ventJet();
		}
	}

	/** The whistle is as loud as the wheel is fast, and silent while it is barely turning; it eases in rather than ramping. */
	@Override
	public float loopLevel() {
		if (status == null)
			return 0;
		float x = Math.clamp((status.spool() - 0.06f) * 1.2f, 0, 1);
		return x * x * (3 - 2 * x);
	}

	/** From a low breath at a slow wheel to a whistle at design speed, climbing well over an octave on the way. */
	@Override
	public float loopPitch() {
		return com.createengines.content.effects.Pitches.turboWhistle(status == null ? 0 : status.spool());
	}

	/** The wheel is slower to come up to its whistle than the gauge is to say so, and it coasts on after the exhaust lets go. */
	@Override
	public float loopSpinDown() {
		return 0.03f;
	}

	@Override
	public boolean loopCoasts() {
		return true;
	}

	/**
	 * The blow-off valve: it opens when redstone opens the wastegate fully, and when the engine lifts off the throttle with
	 * boost still in the pipe (as a real valve does when the throttle plate shuts against a spinning compressor), and the
	 * charge goes with a thump, a hiss and a flutter. Played when the valve opens, louder for more boost. The boost it was
	 * holding is remembered for a moment, since by the time the client hears of the open valve the gauge has started to fall.
	 */
	private void blowOff() {
		float boost = status == null ? 0 : status.boost();
		heldBoost = Math.max(boost, heldBoost - 0.01f);
		boolean venting = status != null && status.wastegate() > 0.5f && dumping();
		boolean lifted = status == null || status.offThrottle();
		boolean opens = venting && !wasVenting;
		if (!opens && lifted && !wasLifted && heldBoost >= 0.25f && liftRate.allow(level))
			opens = true;
		wasLifted = lifted;
		// The wastegate swinging open to hold the limit chuffs too: a softer, higher flutter, at most every couple of seconds.
		boolean regulating = status != null && status.wastegate() > 0.5f && !dumping();
		boolean chuffs = !opens && regulating && !wasRegulating && boost >= 0.25f && chuffRate.allow(level);
		wasRegulating = regulating;
		if (chuffs && CEClientConfig.particles())
			wastegateChuff(boost);
		if (chuffs && CEClientConfig.boostVolume() > 0)
			valveSound(CHUFFS, Math.min(0.9f, 0.3f + boost * 0.5f), 1.2f);
		if (opens && heldBoost >= 0.25f) {
			if (CEClientConfig.boostVolume() > 0)
				valveSound(BLOW_OFFS, Math.min(1.2f, 0.4f + heldBoost), 1);
			if (CEClientConfig.particles())
				ventCharge(heldBoost);
		}
		wasVenting = venting;
	}

	/** The blow-off recording at about {@code pitch}, softer or not at all when others by it have only just played. */
	private void valveSound(BlowOffCrowd crowd, float volume, float pitch) {
		float share = crowd.share(level.getGameTime(), worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
		if (share > 0)
			level.playLocalSound(worldPosition, CESounds.TURBO_BLOWOFF.get(), SoundSource.BLOCKS,
				volume * share * CEClientConfig.boostVolume(),
				com.createengines.content.effects.Pitches.near(pitch, level.random.nextFloat()), false);
	}

	/**
	 * The dumped charge, seen: a burst of cold white air off the compressor end, bigger for more boost, which then goes on
	 * as a jet for a few ticks ({@link #ventJet}) so the blow-off is a rush of air and not a single puff.
	 */
	private void ventCharge(float boost) {
		Vec3 out = Vec3.atLowerCornerOf(getBlockState().getValue(TurbochargerBlock.FACING)
			.getNormal());
		Vec3 at = Vec3.atCenterOf(worldPosition)
			.add(out.scale(0.6));
		var random = level.random;
		GasParticleOptions air = new GasParticleOptions(0.92f, 0.94f, 0.97f, 0.5f, 0.55f, 0.1f, 12);
		int puffs = VentJet.burst(boost);
		for (int i = 0; i < puffs; i++)
			level.addParticle(air, at.x, at.y, at.z, out.x * 0.14 + (random.nextDouble() - 0.5) * 0.3,
				out.y * 0.14 + (random.nextDouble() - 0.5) * 0.3, out.z * 0.14 + (random.nextDouble() - 0.5) * 0.3);
		ventTotal = VentJet.duration(boost);
		ventLeft = ventTotal;
		ventBoost = boost;
	}

	/**
	 * The rest of the blow-off: a narrow jet of air out of the compressor end, quick at first and thinning to nothing over
	 * the duration {@link VentJet} gives for the boost that was dumped. Fewer puffs at a lower effect density.
	 */
	private void ventJet() {
		if (ventLeft <= 0)
			return;
		int count = VentJet.perTick(ventLeft, ventTotal);
		float strength = VentJet.strength(ventLeft, ventTotal);
		ventLeft--;
		if (!CEClientConfig.particles())
			return;
		Vec3 out = Vec3.atLowerCornerOf(getBlockState().getValue(TurbochargerBlock.FACING)
			.getNormal());
		Vec3 at = Vec3.atCenterOf(worldPosition)
			.add(out.scale(0.6));
		var random = level.random;
		GasParticleOptions air = new GasParticleOptions(0.92f, 0.94f, 0.97f, 0.35f + 0.2f * strength, 0.3f + 0.3f * strength, 0.05f, 10);
		double speed = 0.08 + 0.12 * strength * Math.min(1.5f, ventBoost);
		for (int i = 0; i < count; i++)
			if (random.nextDouble() < CEClientConfig.effectDensity())
				level.addParticle(air, at.x, at.y, at.z, out.x * speed + (random.nextDouble() - 0.5) * 0.06,
					out.y * speed + (random.nextDouble() - 0.5) * 0.06, out.z * speed + (random.nextDouble() - 0.5) * 0.06);
	}

	/**
	 * The wastegate swinging open to hold the limit, seen: a small grey puff or two off the turbine end, where it lets
	 * exhaust round the wheel. Softer and dirtier than the blow-off's white air.
	 */
	private void wastegateChuff(float boost) {
		Vec3 out = Vec3.atLowerCornerOf(getBlockState().getValue(TurbochargerBlock.FACING)
			.getOpposite()
			.getNormal());
		Vec3 at = Vec3.atCenterOf(worldPosition)
			.add(out.scale(0.6));
		var random = level.random;
		GasParticleOptions puff = new GasParticleOptions(0.62f, 0.62f, 0.64f, 0.3f, 0.4f, 0.2f, 14);
		int puffs = VentJet.chuff(boost);
		for (int i = 0; i < puffs; i++)
			if (random.nextDouble() < CEClientConfig.effectDensity())
				level.addParticle(puff, at.x, at.y, at.z, out.x * 0.05 + (random.nextDouble() - 0.5) * 0.08,
					0.03 + random.nextDouble() * 0.03, out.z * 0.05 + (random.nextDouble() - 0.5) * 0.08);
	}

	/**
	 * Anti-lag burning fuel in the exhaust to keep the wheel spinning: flame and smoke flicker at the turbine end, and
	 * every so often it goes off with a crack, higher than a cylinder's afterfire, and a burst of flame and sparks. The
	 * flames carry on down the pipe and out of the exhaust's openings.
	 */
	private void antiLagPop() {
		Vec3 out = Vec3.atLowerCornerOf(getBlockState().getValue(TurbochargerBlock.FACING)
			.getOpposite()
			.getNormal());
		Vec3 at = Vec3.atCenterOf(worldPosition)
			.add(out.scale(0.6));
		if (popRate.allow(level)) {
			List<BlockPos> outlets = status.outlets();
			EngineEffects.afterfire(level, engine, at, out, com.createengines.content.effects.ExhaustLook.ANTI_LAG_LOUDNESS, 1.25f,
				() -> EngineEffects.flamesFromOutlet(level, outlets, 4, 0.2));
			return;
		}
		if (!CEClientConfig.particles())
			return;
		var random = level.random;
		if (random.nextBoolean()) {
			com.createengines.content.effects.ExhaustFire.flames(level, at, out, 1, 0.1);
			EngineEffects.flamesFromOutlet(level, status.outlets(), 1, 0.13);
		} else
			level.addParticle(new GasParticleOptions(0.25f, 0.25f, 0.25f, 0.6f, 0.5f, 0.6f, 24), at.x, at.y, at.z,
				out.x * 0.04 + (random.nextDouble() - 0.5) * 0.04, 0.03, out.z * 0.04 + (random.nextDouble() - 0.5) * 0.04);
	}

	/** The wheel's angle, degrees, interpolated between ticks. */
	public float wheelAngle(float partialTicks) {
		return (wheelAngle + wheelSpeed * partialTicks) % 360;
	}

	// Wear (opt-in, see TurboWear): how worn the bearings and wheel are, and whether the turbo has failed. Saved only once there is some.
	private double wear;
	private boolean failed;

	public double wear() {
		return wear;
	}

	public boolean failed() {
		return failed;
	}

	/** Whether the failure counts now: with the wear mode off it stays saved, and is neither shown nor takes any boost. */
	public boolean showsFailed() {
		return com.createengines.engine.sim.Tuning.wearMode && failed;
	}

	/** The share of its boost this turbo makes now: all of it with the wear mode off. */
	public double boostShare() {
		if (!com.createengines.engine.sim.Tuning.wearMode)
			return 1;
		return failed ? 0 : TurboWear.boostShare(wear);
	}

	/** Adds wear. Clients are told when it moves to a new 5% step. */
	public void addWear(double amount) {
		int before = (int) Math.round(wear * 20);
		wear = Wear.clamp(wear + amount);
		setChanged();
		if (before != (int) Math.round(wear * 20))
			sendData();
	}

	/** The turbo fails: it makes no boost until it is replaced, and the engine is looked at again so it is reported. */
	public void fail() {
		if (failed)
			return;
		failed = true;
		setChanged();
		sendData();
		com.createengines.engine.topology.EngineTopology.wearChanged(level, worldPosition);
	}

	/** Sets right light wear (see {@link Wear#repairable}). A failed turbo is replaced instead. Returns whether anything changed. */
	public boolean service() {
		if (failed || !Wear.repairable(wear))
			return false;
		wear = Wear.repaired(wear);
		setChanged();
		sendData();
		return true;
	}

	/** The creative Service Kit: as new, a failed turbo too. Returns whether anything changed. */
	public boolean restore() {
		boolean wasFailed = failed;
		if (!wasFailed && wear == 0)
			return false;
		failed = false;
		wear = 0;
		setChanged();
		sendData();
		if (wasFailed)
			com.createengines.engine.topology.EngineTopology.wearChanged(level, worldPosition);
		return true;
	}

	// The block's item keeps the wear and the failure, so breaking a turbo and placing it again is no repair

	@Override
	protected void collectImplicitComponents(net.minecraft.core.component.DataComponentMap.Builder components) {
		super.collectImplicitComponents(components);
		if (wear > 0)
			components.set(com.createengines.registry.CEComponents.WEAR.get(), wear);
		if (failed)
			components.set(com.createengines.registry.CEComponents.FAILED.get(), true);
	}

	@Override
	protected void applyImplicitComponents(DataComponentInput components) {
		super.applyImplicitComponents(components);
		Double carried = components.get(com.createengines.registry.CEComponents.WEAR.get());
		boolean broken = Boolean.TRUE.equals(components.get(com.createengines.registry.CEComponents.FAILED.get()));
		if (carried == null && !broken)
			return;
		wear = carried == null ? 0 : Wear.clamp(carried);
		failed = broken;
		setChanged();
		sendData();
	}

	@Override
	@SuppressWarnings("deprecation")
	public void removeComponentsFromTag(CompoundTag tag) {
		super.removeComponentsFromTag(tag);
		tag.remove("Wear");
		tag.remove("WearFailed");
	}

	@Override
	protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.write(tag, registries, clientPacket);
		if (wear > 0)
			tag.putDouble("Wear", wear);
		if (failed)
			tag.putBoolean("WearFailed", true);
		if (clientPacket && status != null)
			tag.put("Status", status.write());
		if (clientPacket && engine != null)
			tag.put("Engine", NbtUtils.writeBlockPos(engine));
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(tag, registries, clientPacket);
		wear = Wear.clamp(tag.getDouble("Wear"));
		failed = tag.getBoolean("WearFailed");
		// Ponder scenes set the status through plain NBT, so a status is taken from either kind of read
		if (tag.contains("Status"))
			status = TurboStatus.read(tag.getCompound("Status"));
		else if (clientPacket)
			status = null;
		if (clientPacket)
			engine = NbtUtils.readBlockPos(tag, "Engine")
				.orElse(null);
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		CELang.translate("goggles.turbo")
			.forGoggles(tooltip);
		TurboStatus current = status;
		if (current == null)
			GoggleLines.add(CELang.translate("goggles.turbo.no_engine")
				.style(ChatFormatting.RED), tooltip, 1);
		else {
			GoggleLines.add(CELang.translate("goggles.turbo.boost", String.format(Locale.ROOT, "%.2f", current.boost()))
				.style(current.boost() > 0.05f ? ChatFormatting.AQUA : ChatFormatting.GRAY), tooltip, 1);
			if (current.compressor() && current.boost() > 0.05f) {
				int working = Turbocharger.limitPercent(current.boost(), boostLimit());
				GoggleLines.add(CELang.translate("goggles.turbo.working", working)
					.style(working >= 95 ? ChatFormatting.GOLD : ChatFormatting.DARK_AQUA), tooltip, 1);
			}
			if (current.compressor() && Math.abs(current.charge() - current.boost()) >= 0.02f)
				GoggleLines.add(CELang.translate("goggles.turbo.charge", String.format(Locale.ROOT, "%.2f", current.charge()))
					.style(ChatFormatting.DARK_AQUA), tooltip, 1);
			GoggleLines.add(CELang.translate("goggles.turbo.spool", Math.round(current.spool() * 100))
				.style(ChatFormatting.GRAY), tooltip, 1);
			if (current.vanes() > 0.05f || getBlockState().getBlock() instanceof TurbochargerBlock block && block.variableGeometry())
				GoggleLines.add(CELang.translate("goggles.turbo.vanes", Math.round(current.vanes() * 100))
					.style(ChatFormatting.DARK_AQUA), tooltip, 1);
			if (current.antiLag())
				GoggleLines.add(CELang.translate("goggles.turbo.anti_lag")
					.style(ChatFormatting.RED), tooltip, 1);
			else if (antiLagLevel() > 0)
				GoggleLines.add(CELang.translate("goggles.turbo.anti_lag.armed", antiLagLevel())
					.style(ChatFormatting.GRAY), tooltip, 1);
			if (current.wastegate() > 0.05f)
				GoggleLines.add(CELang.translate(dumping() ? "goggles.turbo.dumping" : "goggles.turbo.wastegate",
						Math.round(current.wastegate() * 100))
					.style(ChatFormatting.GOLD), tooltip, 1);
			if (!current.compressor())
				GoggleLines.add(CELang.translate("goggles.turbo.no_compressor")
					.style(ChatFormatting.RED), tooltip, 1);
			if (!current.turbine())
				GoggleLines.add(CELang.translate("goggles.turbo.no_turbine")
					.style(ChatFormatting.RED), tooltip, 1);
			if (isPlayerSneaking && current.turbine())
				GoggleLines.add(CELang.translate("goggles.turbo.back_pressure",
						String.format(Locale.ROOT, "%.2f", current.backPressure() - 1))
					.style(ChatFormatting.DARK_GRAY), tooltip, 1);
		}
		if (showsFailed())
			GoggleLines.add(CELang.translate("goggles.wear.turbo_failed")
				.style(ChatFormatting.RED), tooltip, 1);
		else if (com.createengines.engine.sim.Tuning.wearMode && wear > 0) {
			GoggleLines.add(CELang.translate("goggles.wear.turbo", Math.round(wear * 100))
				.style(wear >= Wear.FAILURE_FROM ? ChatFormatting.RED : wear >= Wear.REPAIRABLE ? ChatFormatting.GOLD : ChatFormatting.GRAY), tooltip, 1);
			if (isPlayerSneaking && (Wear.repairable(wear) || wear > Wear.REPAIRABLE))
				GoggleLines.add(CELang.translate(Wear.repairable(wear) ? "goggles.wear.light" : "goggles.wear.heavy_turbo")
					.style(ChatFormatting.DARK_GRAY), tooltip, 2);
		}
		GoggleLines.add(CELang.translate("goggles.turbo.limit", String.format(Locale.ROOT, "%.1f", boostLimit()))
			.style(ChatFormatting.DARK_AQUA), tooltip, 1);
		if (isPlayerSneaking)
			GoggleLines.add(CELang.translate("goggles.turbo.hint")
				.style(ChatFormatting.DARK_GRAY), tooltip, 2);
		return true;
	}

	/** A value box on any face the shaft does not run out of, {@code height} sixteenths up it. */
	private static class ValueBox extends ValueBoxTransform.Sided {

		private final double height;

		ValueBox(double height) {
			this.height = height;
		}

		@Override
		protected Vec3 getSouthLocation() {
			return VecHelper.voxelSpace(8, height, 16);
		}

		@Override
		protected boolean isSideActive(BlockState state, Direction direction) {
			return direction.getAxis() != state.getValue(TurbochargerBlock.FACING)
				.getAxis();
		}
	}
}
