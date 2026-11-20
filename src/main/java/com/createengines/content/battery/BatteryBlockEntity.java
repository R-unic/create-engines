package com.createengines.content.battery;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.createengines.engine.sim.BatteryChemistry;
import com.createengines.engine.sim.HeatRecovery;
import com.createengines.engine.sim.Tuning;
import com.createengines.engine.topology.EngineMember;
import com.createengines.content.GoggleLines;
import com.createengines.registry.CELang;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.QuartPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Charge is counted in sparks. An engine draws one spark's worth every time one of its plugs fires, and an alternator
 * puts it back. A flat battery gives no spark, so a spark engine needs its battery charged before it will start,
 * which is what cranking does when an alternator sits on the crank's own shaft line.
 * <p>
 * Batteries that touch are one {@link BatteryBank bank}. A cold one (a cold biome, with no heat source beside it) cannot
 * deliver all it holds ({@link BatteryChemistry}); the charge is still there, and warming it gives it back. What the goggles
 * and the lamp strip show is the charge held; what an engine can draw is the deliverable part of it.
 */
public class BatteryBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation, EngineMember {

	public static final float CAPACITY = BatteryChemistry.CAPACITY;
	private static final int SYNC_INTERVAL = 10;
	/** Ticks between looking at the weather and the blocks beside the battery. */
	private static final int COLD_INTERVAL = 40;
	/** Ticks a bank, once found, is used before it is looked for again (a change to any battery looks sooner). */
	private static final int BANK_REFRESH = 20;
	/** How quickly the charge rate follows the last tick's change: about the last second. */
	private static final float RATE_SMOOTHING = 0.05f;

	private float charge;
	private @Nullable BlockPos primaryPos;
	private float syncedCharge = -1;
	private float syncedRate;
	private float syncedCold;
	private int ticks;

	/** 0 (warm) to 1 (as cold as it gets): worked out on the server, sent to the client. */
	private float cold;
	/** Charge gained (+) or lost (-) per tick, smoothed: worked out on the server, sent to the client. */
	private float rate;
	private float lastCharge = -1;
	private int lastSignal = -1;

	private List<BatteryBlockEntity> bank = List.of();
	private int bankTopology = -1;
	private long bankTime = Long.MIN_VALUE / 2;

	public BatteryBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
	}

	/** The charge this battery holds, in sparks. */
	public float charge() {
		return charge;
	}

	/** The share of this battery's own capacity that is charged, 0 to 1. */
	public float chargeFraction() {
		return charge / CAPACITY;
	}

	public boolean isFull() {
		return charge >= CAPACITY;
	}

	/** How cold this battery is, 0 to 1. */
	public float coldness() {
		return cold;
	}

	/** Charge gained (+) or lost (-) per tick lately, smoothed. */
	public float rate() {
		return rate;
	}

	/** The charge this battery can give now: what it holds, less what the cold keeps back. */
	public float deliverable() {
		return (float) BatteryChemistry.deliverable(charge, CAPACITY, cold, Tuning.batteryCold);
	}

	/** Takes up to {@code sparks} of charge and returns how much was actually there to give. */
	public float draw(float sparks) {
		float drawn = Math.max(0, Math.min(deliverable(), sparks));
		charge -= drawn;
		return drawn;
	}

	/** Takes in up to {@code sparks} of charge and returns how much there was room for. */
	public float charge(float sparks) {
		float taken = Math.max(0, Math.min(CAPACITY - charge, sparks));
		charge += taken;
		return taken;
	}

	/** The batteries that touch this one, and those that touch them, this one included. Looked for again about once a second. */
	public List<BatteryBlockEntity> bank() {
		if (level == null)
			return List.of(this);
		long now = level.getGameTime();
		if (bankTopology != BatteryBlock.TOPOLOGY.get() || now - bankTime >= BANK_REFRESH || now < bankTime) {
			bank = List.copyOf(BatteryBank.around(level, worldPosition));
			if (bank.isEmpty())
				bank = List.of(this);
			bankTopology = BatteryBlock.TOPOLOGY.get();
			bankTime = now;
		}
		return bank;
	}

	/** What a comparator beside this battery reads: the charge of its whole bank, 0 to 15. */
	public int bankSignal() {
		List<BatteryBlockEntity> bank = bank();
		float capacity = BatteryBank.capacity(bank);
		return BatteryChemistry.signal(capacity <= 0 ? 0 : BatteryBank.charge(bank) / capacity);
	}

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
	public void tick() {
		super.tick();
		if (level == null || level.isClientSide)
			return;
		if (lastCharge < 0)
			lastCharge = charge;
		rate += ((charge - lastCharge) - rate) * RATE_SMOOTHING;
		if (Math.abs(rate) < 1e-4f)
			rate = 0;
		lastCharge = charge;
		if (ticks % COLD_INTERVAL == 0)
			cold = (float) BatteryChemistry.coldness(biomeTemperature(), heatBeside());
		if (++ticks % SYNC_INTERVAL != 0)
			return;
		showGauge();
		int signal = bankSignal();
		if (signal != lastSignal) {
			// The first look tells them too: a comparator beside a battery that has just loaded still shows what it saw when it was saved
			level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
			lastSignal = signal;
		}
		if (Math.abs(charge - syncedCharge) >= CAPACITY * 0.01f || Math.abs(rate - syncedRate) >= 0.05f
			|| Math.abs(cold - syncedCold) >= 0.02f) {
			syncedCharge = charge;
			syncedRate = rate;
			syncedCold = cold;
			sendData();
		}
	}

	/** Lights as many lamps of the strip on the lid as this battery's own charge earns, changing the block only when the stage does. */
	private void showGauge() {
		BlockState state = getBlockState();
		if (!state.hasProperty(BatteryBlock.CHARGE))
			return;
		int shown = state.getValue(BatteryBlock.CHARGE);
		int stage = BatteryChemistry.gaugeStage(chargeFraction(), shown);
		// A battery saved before batteries joined has no joins yet, and none of its neighbours will tell it: it looks at them here
		BlockState next = BatteryBlock.joined(state.setValue(BatteryBlock.CHARGE, stage), level, worldPosition);
		if (next != state)
			// Clients only, and without the shape updates that read the block beside it: at a chunk's edge that is another chunk's block
			level.setBlock(worldPosition, next, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
	}

	/** A temperature to use in place of the biome's, for the GameTests: the test world has one biome and a cold one is wanted. Null in play. */
	public static volatile @Nullable Float biomeTemperatureOverride;

	private float biomeTemperature() {
		Float forced = biomeTemperatureOverride;
		if (forced != null)
			return forced;
		// The biome of the one cell the battery is in, from a chunk that is already loaded: level.getBiome blends with the cells
		// round it, which at a chunk's edge reads the next chunk and loads it
		// On a moving structure (Sable) the battery's own cell is in a plot far from the world, so the cold is the world's where
		// the structure is; if that chunk is not loaded the plot cell answers, as it always did
		BlockPos where = com.createengines.compat.sable.SableCompat.worldBlockPos(level, worldPosition);
		if (where != worldPosition && !level.isLoaded(where))
			where = worldPosition;
		return level.getNoiseBiome(QuartPos.fromBlock(where.getX()), QuartPos.fromBlock(where.getY()), QuartPos.fromBlock(where.getZ()))
			.value()
			.getBaseTemperature();
	}

	/** The warmest heat source touching the battery, as a Create heat level 0 (none) to 3 (seething): a blaze burner, a heat exchanger, a lit campfire. */
	private int heatBeside() {
		int best = HeatRecovery.NONE;
		for (Direction side : Direction.values()) {
			BlockPos beside = worldPosition.relative(side);
			if (!level.isLoaded(beside))
				continue;
			best = Math.max(best, switch (BlazeBurnerBlock.getHeatLevelOf(level.getBlockState(beside))) {
				case SEETHING -> HeatRecovery.SEETHE;
				case KINDLED, FADING -> HeatRecovery.KINDLE;
				case SMOULDERING -> HeatRecovery.SMOULDER;
				default -> HeatRecovery.NONE;
			});
		}
		return best;
	}

	@Override
	protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.write(tag, registries, clientPacket);
		tag.putFloat("Charge", charge);
		tag.putFloat("Cold", cold);
		if (clientPacket)
			tag.putFloat("Rate", rate);
		if (primaryPos != null)
			tag.put("Primary", NbtUtils.writeBlockPos(primaryPos));
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(tag, registries, clientPacket);
		float read = tag.getFloat("Charge");
		// Saved data may hold anything: a charge that is not a number, or is more than the battery holds, is clamped to what it can
		charge = Float.isNaN(read) ? 0 : Math.clamp(read, 0, CAPACITY);
		float coldRead = tag.getFloat("Cold");
		cold = Float.isNaN(coldRead) ? 0 : Math.clamp(coldRead, 0, 1);
		if (clientPacket) {
			float rateRead = tag.getFloat("Rate");
			rate = Float.isFinite(rateRead) ? rateRead : 0;
		}
		primaryPos = NbtUtils.readBlockPos(tag, "Primary")
			.orElse(null);
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		CELang.translate("goggles.battery")
			.forGoggles(tooltip);
		List<BatteryBlockEntity> bank = bank();
		float held = BatteryBank.charge(bank);
		float capacity = BatteryBank.capacity(bank);
		int percent = Math.round(capacity <= 0 ? 0 : held / capacity * 100);
		GoggleLines.add(CELang.translate("goggles.battery.charge", percent)
			.style(percent == 0 ? ChatFormatting.RED : percent < 20 ? ChatFormatting.GOLD : ChatFormatting.GREEN), tooltip, 1);
		if (bank.size() > 1)
			GoggleLines.add(CELang.translate("goggles.battery.bank", bank.size(), Math.round(held), Math.round(capacity))
				.style(ChatFormatting.GRAY), tooltip, 1);
		if (percent == 0)
			GoggleLines.add(CELang.translate("goggles.battery.flat")
				.style(ChatFormatting.GRAY), tooltip, 1);
		float perTick = BatteryBank.rate(bank);
		if (perTick <= -0.005f)
			GoggleLines.add(CELang.translate("goggles.battery.draining", perSecond(-perTick),
					duration(BatteryChemistry.ticksLeft(held, -perTick)))
				.style(ChatFormatting.GOLD), tooltip, 1);
		else if (perTick >= 0.005f && held < capacity)
			GoggleLines.add(CELang.translate("goggles.battery.charging", perSecond(perTick),
					duration(BatteryChemistry.ticksLeft(capacity - held, perTick)))
				.style(ChatFormatting.GREEN), tooltip, 1);
		if (Tuning.batteryCold && cold >= 0.05f) {
			int usable = Math.round((float) BatteryChemistry.deliverableLimit(100, cold, true));
			GoggleLines.add(CELang.translate("goggles.battery.cold", usable)
				.style(ChatFormatting.AQUA), tooltip, 1);
			if (isPlayerSneaking)
				GoggleLines.add(CELang.translate("goggles.battery.cold_hint")
					.style(ChatFormatting.DARK_GRAY), tooltip, 2);
		}
		if (primaryPos == null)
			GoggleLines.add(CELang.translate("goggles.battery.no_engine")
				.style(ChatFormatting.GOLD), tooltip, 1);
		return true;
	}

	private static String perSecond(float perTick) {
		return String.format(java.util.Locale.ROOT, "%.1f", perTick * 20);
	}

	/** Ticks as a short span of time: "40 s", "3 min". */
	private static String duration(double ticks) {
		double seconds = ticks / 20;
		if (seconds >= 3600)
			return ">1 h";
		if (seconds >= 120)
			return Math.round(seconds / 60) + " min";
		return Math.max(1, Math.round(seconds)) + " s";
	}
}
