package com.createengines.advancement;

import java.util.Collection;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.createengines.CreateEngines;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Who gets the credit for what an engine does: the player who last placed or used one of its blocks. Stored with the
 * level, so it survives a restart, and read back through {@link Achievements}.
 */
public final class Operators extends SavedData {

	private static final String NAME = CreateEngines.MOD_ID + "_operators";
	private static final SavedData.Factory<Operators> FACTORY = new SavedData.Factory<>(Operators::new, Operators::load, null);

	private final OperatorLog log = new OperatorLog();

	private static Operators get(ServerLevel level) {
		return level.getDataStorage()
			.computeIfAbsent(FACTORY, NAME);
	}

	/** Notes that {@code player} placed or used the block at {@code pos}. */
	public static void record(Level level, BlockPos pos, Player player) {
		if (!(level instanceof ServerLevel server))
			return;
		Operators operators = get(server);
		operators.log.record(pos.asLong(), player.getUUID(), server.getGameTime());
		operators.setDirty();
	}

	/** The player who last placed or used any of {@code blocks}, if anyone has. */
	public static @Nullable UUID latest(Level level, Collection<BlockPos> blocks) {
		if (!(level instanceof ServerLevel server))
			return null;
		return get(server).log.latest(() -> blocks.stream()
			.map(BlockPos::asLong)
			.iterator());
	}

	private static boolean isOurs(BlockState state) {
		return BuiltInRegistries.BLOCK.getKey(state.getBlock())
			.getNamespace()
			.equals(CreateEngines.MOD_ID);
	}

	public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
		if (event.getEntity() instanceof Player player && isOurs(event.getPlacedBlock()))
			record((Level) event.getLevel(), event.getPos(), player);
	}

	public static void onBlockUsed(PlayerInteractEvent.RightClickBlock event) {
		Level level = event.getLevel();
		if (!level.isClientSide && isOurs(level.getBlockState(event.getPos())))
			record(level, event.getPos(), event.getEntity());
	}

	public static void onBlockBroken(BlockEvent.BreakEvent event) {
		if (event.getLevel() instanceof ServerLevel level && isOurs(event.getState())) {
			Operators operators = get(level);
			operators.log.forget(event.getPos()
				.asLong());
			operators.setDirty();
		}
	}

	private static Operators load(CompoundTag tag, HolderLookup.Provider registries) {
		Operators operators = new Operators();
		for (Tag entry : tag.getList("Entries", Tag.TAG_COMPOUND)) {
			CompoundTag compound = (CompoundTag) entry;
			operators.log.record(compound.getLong("Pos"), compound.getUUID("Player"), compound.getLong("Time"));
		}
		return operators;
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		ListTag entries = new ListTag();
		log.entries()
			.forEach((pos, entry) -> {
				CompoundTag compound = new CompoundTag();
				compound.putLong("Pos", pos);
				compound.putUUID("Player", entry.player());
				compound.putLong("Time", entry.time());
				entries.add(compound);
			});
		tag.put("Entries", entries);
		return tag;
	}
}
